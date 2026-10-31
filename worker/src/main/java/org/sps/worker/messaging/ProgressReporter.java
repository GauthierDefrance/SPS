package org.sps.worker.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Envoie au backend l'avancement d'UNE vidéo. FFmpeg donne sa position plusieurs fois par
 * seconde : on n'envoie un message que si le pourcentage ou l'étape a changé, et au plus une
 * fois par seconde, pour ne pas inonder RabbitMQ et la base de données.
 */
public class ProgressReporter {

    private static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

    private final RabbitTemplate rabbit;
    private final String videoId;
    private final String workerId;

    private int lastProgress = -1;
    private String lastStep;
    private Instant lastSentAt = Instant.EPOCH;

    public ProgressReporter(RabbitTemplate rabbit, String videoId, String workerId) {
        this.rabbit = rabbit;
        this.videoId = videoId;
        this.workerId = workerId;
    }

    /** Nouvelle étape : toujours envoyée. */
    public void step(String step, int progress) {
        send(step, progress, true);
    }

    /** Avancement dans l'étape en cours : envoyé seulement s'il a changé, au plus une fois par seconde. */
    public void progress(int progress) {
        send(lastStep, progress, false);
    }

    public void ready(List<Integer> renditions, double durationSeconds, int thumbnailCandidates) {
        publish(new TranscodeEvent(videoId, TranscodeEvent.Type.READY, 100, "TERMINÉ",
                renditions, durationSeconds, thumbnailCandidates, null, workerId, Instant.now()));
    }

    public void failed(String reason) {
        publish(new TranscodeEvent(videoId, TranscodeEvent.Type.FAILED, Math.max(lastProgress, 0), "ÉCHEC",
                null, null, null, reason, workerId, Instant.now()));
    }

    private void send(String step, int progress, boolean force) {
        int clamped = Math.clamp(progress, 0, 99);   // 100 % est réservé au message READY
        Instant now = Instant.now();
        boolean changed = clamped != lastProgress || !java.util.Objects.equals(step, lastStep);
        if (!force && (!changed || Duration.between(lastSentAt, now).compareTo(MIN_INTERVAL) < 0)) {
            return;
        }
        lastProgress = clamped;
        lastStep = step;
        lastSentAt = now;
        publish(new TranscodeEvent(videoId, TranscodeEvent.Type.PROGRESS, clamped, step,
                null, null, null, null, workerId, now));
    }

    private void publish(TranscodeEvent event) {
        rabbit.convertAndSend(WorkerMessaging.EXCHANGE, WorkerMessaging.EVENTS_ROUTING_KEY, event);
    }
}
