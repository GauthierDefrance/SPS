package org.sps.worker.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.sps.worker.transcode.Transcoder;
import org.sps.worker.transcode.VideoProcessingException;

import java.time.Duration;
import java.time.Instant;

/*
 * Écoute la file des vidéos à traiter. Cycle de vie d'un message :
 *  - RabbitMQ le donne à ce worker (et à aucun autre) ; il reste "réservé" tant qu'on le traite
 *  - méthode terminée normalement      -> accusé de réception (ack) : le message est supprimé
 *  - échec (AmqpRejectAndDontRequeue)  -> rejeté vers la file d'erreurs sps.video.transcode.dlq
 *  - worker arrêté ou planté en cours  -> pas d'ack : RabbitMQ redonne la vidéo à un autre worker
 */
@Component
public class TranscodeJobListener {

    private static final Logger log = LoggerFactory.getLogger(TranscodeJobListener.class);

    private final Transcoder transcoder;
    private final RabbitTemplate rabbit;
    private final String workerId;

    public TranscodeJobListener(Transcoder transcoder, RabbitTemplate rabbit, @Value("${sps.worker.id}") String workerId) {
        this.transcoder = transcoder;
        this.rabbit = rabbit;
        this.workerId = workerId;
    }

    @RabbitListener(queues = WorkerMessaging.TRANSCODE_QUEUE, concurrency = "${sps.worker.concurrency}")
    public void onJob(TranscodeRequest job) {
        Instant start = Instant.now();
        log.info("Vidéo {} prise en charge par {}", job.videoId(), workerId);
        ProgressReporter progress = new ProgressReporter(rabbit, job.videoId(), workerId);

        try {
            Transcoder.Result result = transcoder.transcode(job.videoId(), job.rawKey(), progress);
            progress.ready(result.renditions(), result.durationSeconds(), result.thumbnailCandidates());
            log.info("Vidéo {} prête en {} s", job.videoId(), Duration.between(start, Instant.now()).toSeconds());
        } catch (InterruptedException e) {
            // Arrêt du worker pendant le traitement : la vidéo retourne dans la file pour un autre worker
            Thread.currentThread().interrupt();
            throw new ImmediateRequeueAmqpException("Worker arrêté pendant le traitement de " + job.videoId(), e);
        } catch (VideoProcessingException e) {
            // Erreur liée au fichier lui-même : réessayer ne changerait rien
            log.warn("Vidéo {} refusée : {}", job.videoId(), e.getMessage());
            progress.failed(e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        } catch (Exception e) {
            log.error("Échec du traitement de {}", job.videoId(), e);
            progress.failed("Erreur interne pendant le traitement, réessaie plus tard");
            throw new AmqpRejectAndDontRequeueException("Échec du traitement de " + job.videoId(), e);
        }
    }
}
