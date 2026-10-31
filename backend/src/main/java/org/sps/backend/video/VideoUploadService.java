package org.sps.backend.video;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.sps.backend.processing.ProcessingMessaging;
import org.sps.backend.processing.TranscodeRequest;
import org.sps.backend.storage.S3Properties;
import org.sps.backend.storage.S3StorageService;
import org.sps.backend.user.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/*
 * Upload d'une vidéo. Le backend ne fait que recevoir le fichier et le ranger ; le travail lourd
 * (découpage, compression, stockage des qualités) est fait par les workers :
 *
 *   1. fichier original -> stockage S3 (bucket raw, clé {publicId}/original)
 *   2. ligne en base, statut PENDING ("en file d'attente")
 *   3. message TranscodeRequest -> RabbitMQ (file sps.video.transcode)
 *   4. réponse 202 Accepted tout de suite : le client suit l'avancement avec GET /api/videos/{id}
 *
 * La vidéo commence en brouillon (publishedAt null) : le propriétaire complète ses informations et
 * choisit sa miniature pendant le traitement, puis la publie (POST /api/videos/{id}/publish).
 *
 * Si RabbitMQ ne confirme pas avoir reçu le message, on annule les étapes 1 et 2 et on répond 503 :
 * aucune vidéo ne reste bloquée en PENDING sans message pour la traiter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoUploadService {

    private final VideoRepository videos;
    private final VideoService videoService;
    private final UserRepository users;
    private final S3StorageService storage;
    private final S3Properties s3;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transaction;

    public VideoResponse upload(UUID ownerId, VideoUploadForm form, MultipartFile file) {
        String contentType = file.getContentType();
        if (file.isEmpty() || contentType == null || !contentType.startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier doit être une vidéo non vide");
        }

        String publicId = videoService.newPublicId();
        String rawKey = publicId + "/original";

        // 1. Le fichier part directement dans le stockage, sans être chargé en mémoire
        try (InputStream data = file.getInputStream()) {
            storage.upload(s3.bucketRaw(), rawKey, data, file.getSize(), contentType);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lecture du fichier impossible");
        }

        // 2. La ligne en base, dans sa propre transaction : elle doit être visible (commitée)
        //    avant qu'un worker ne commence à envoyer des messages de progression
        Video video;
        try {
            video = transaction.execute(status -> {
                Video v = new Video();
                v.setPublicId(publicId);
                v.setOwner(users.getReferenceById(ownerId));
                v.setTitle(form.title().strip());
                v.setDescription(form.description());
                v.setPolicy(form.policy() == null ? VideoPolicy.PUBLIC : form.policy());
                v.setRawFileKey(rawKey);
                v.setOriginalFileSize(file.getSize());
                v.setStatus(TranscodeStatus.PENDING);
                v.setProcessingStep("EN ATTENTE");
                return videos.save(v);
            });
        } catch (RuntimeException e) {
            storage.delete(s3.bucketRaw(), rawKey);   // pas de fichier orphelin dans le stockage
            throw e;
        }

        // 3. Le message pour les workers
        try {
            publishTranscodeRequest(publicId, rawKey);
        } catch (AmqpException e) {
            log.error("RabbitMQ indisponible, upload de {} annulé", publicId, e);
            videos.deleteById(video.getId());
            storage.delete(s3.bucketRaw(), rawKey);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service de traitement vidéo est indisponible, réessaie dans un instant");
        }
        return videoService.get(publicId, Optional.of(ownerId));
    }

    /** Relance le traitement d'une vidéo en échec (propriétaire uniquement). */
    public VideoResponse retry(String publicId, UUID ownerId) {
        Video video = transaction.execute(status -> {
            Video v = videos.findForUpdateByPublicId(publicId)
                    .filter(found -> found.getOwner().getId().equals(ownerId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vidéo introuvable"));
            if (v.getStatus() != TranscodeStatus.FAILED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Seule une vidéo en échec peut être relancée");
            }
            v.setStatus(TranscodeStatus.PENDING);
            v.setProcessingProgress(0);
            v.setProcessingStep("EN ATTENTE");
            v.setProcessingError(null);
            return v;
        });
        try {
            publishTranscodeRequest(publicId, video.getRawFileKey());
        } catch (AmqpException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service de traitement vidéo est indisponible, réessaie dans un instant");
        }
        return videoService.get(publicId, Optional.of(ownerId));
    }

    /**
     * Publie le message et attend l'accusé de réception de RabbitMQ ("publisher confirm") :
     * une fois confirmé, le message est enregistré sur le disque de RabbitMQ et ne sera pas perdu.
     */
    private void publishTranscodeRequest(String publicId, String rawKey) {
        TranscodeRequest request = new TranscodeRequest(publicId, rawKey, Instant.now());
        rabbit.invoke(operations -> {
            operations.convertAndSend(ProcessingMessaging.EXCHANGE, ProcessingMessaging.TRANSCODE_ROUTING_KEY, request);
            operations.waitForConfirmsOrDie(5_000);
            return null;
        });
    }
}
