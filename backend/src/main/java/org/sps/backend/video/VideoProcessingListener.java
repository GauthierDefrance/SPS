package org.sps.backend.video;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.sps.backend.processing.ProcessingMessaging;
import org.sps.backend.processing.TranscodeEvent;
import org.sps.backend.storage.S3Properties;
import org.sps.backend.storage.S3StorageService;

import java.time.Instant;
import java.util.HashSet;

/**
 * Reçoit les messages des workers (file sps.video.events) et met à jour l'état des vidéos en base.
 * Le backend reste ainsi le seul à écrire dans la base de données : les workers n'ont besoin que
 * de RabbitMQ et du stockage S3, ce qui les rend faciles à lancer n'importe où.
 *
 * Si plusieurs backends tournent, ils se partagent cette file : chaque message est traité une fois.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoProcessingListener {

    private final VideoRepository videos;
    private final S3StorageService storage;
    private final S3Properties s3;

    @RabbitListener(queues = ProcessingMessaging.EVENTS_QUEUE)
    @Transactional
    public void onEvent(TranscodeEvent event) {
        Instant at = event.at() != null ? event.at() : Instant.now();
        switch (event.type()) {
            case PROGRESS -> videos.updateProgress(event.videoId(), event.progress(), event.step(), event.workerId(), at);
            case READY -> markReady(event, at);
            case FAILED -> markFailed(event, at);
        }
    }

    private void markReady(TranscodeEvent event, Instant at) {
        videos.findForUpdateByPublicId(event.videoId()).ifPresentOrElse(video -> {
            video.setStatus(TranscodeStatus.READY);
            video.setProcessingProgress(100);
            video.setProcessingStep("TERMINÉ");
            video.setProcessingError(null);
            video.setProcessedBy(event.workerId());
            video.setProcessingUpdatedAt(at);
            video.setRenditions(new HashSet<>(event.renditions()));
            if (event.durationSeconds() != null) {
                video.setDuration(event.durationSeconds());
            }
            video.setThumbnailCandidates(event.thumbnailCandidates() == null ? 0 : event.thumbnailCandidates());
            if (!video.thumbnailOptions().contains(video.getThumbnail())) {
                video.setThumbnail(ThumbnailChoice.AUTO);   // image choisie qui n'existe plus après un nouveau traitement
            }
            log.info("Vidéo {} prête ({}p), traitée par {}", event.videoId(), event.renditions(), event.workerId());
        }, () -> {
            // Le propriétaire a supprimé la vidéo pendant son traitement : le worker vient d'envoyer
            // des fichiers que plus rien n'utilise, on les efface
            log.warn("Vidéo {} prête mais supprimée entre-temps : nettoyage de ses fichiers", event.videoId());
            storage.deletePrefix(s3.bucketHls(), event.videoId() + "/");
        });
    }

    private void markFailed(TranscodeEvent event, Instant at) {
        videos.findForUpdateByPublicId(event.videoId())
                // Un échec tardif (ex. message redistribué après coup) n'efface pas une vidéo déjà prête
                .filter(video -> video.getStatus() != TranscodeStatus.READY)
                .ifPresent(video -> {
                    video.setStatus(TranscodeStatus.FAILED);
                    video.setProcessingStep("ÉCHEC");
                    video.setProcessingError(event.error());
                    video.setProcessedBy(event.workerId());
                    video.setProcessingUpdatedAt(at);
                    log.warn("Échec du traitement de {} sur {} : {}", event.videoId(), event.workerId(), event.error());
                });
    }
}
