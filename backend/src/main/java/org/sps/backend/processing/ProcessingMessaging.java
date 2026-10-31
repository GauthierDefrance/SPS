package org.sps.backend.processing;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * Topologie RabbitMQ du traitement vidéo (déclarée à l'identique par le backend et le worker :
 * le premier qui démarre la crée, l'autre la retrouve) :
 *
 *   exchange "sps.video" (direct)
 *     ├─ clé "transcode.request" ─> file sps.video.transcode   (backend -> workers)
 *     └─ clé "transcode.event"   ─> file sps.video.events      (workers -> backend)
 *
 *   exchange "sps.video.dlx" : les vidéos que le worker n'a pas pu traiter atterrissent dans
 *   sps.video.transcode.dlq ("dead letter queue"), où on peut les inspecter depuis l'interface web.
 *
 * Plusieurs workers écoutent la même file : RabbitMQ donne chaque message à UN seul d'entre eux.
 * C'est ce qui permet d'ajouter des workers pour traiter plus de vidéos en parallèle.
 */
@Configuration
public class ProcessingMessaging {

    public static final String EXCHANGE = "sps.video";
    public static final String TRANSCODE_QUEUE = "sps.video.transcode";
    public static final String TRANSCODE_ROUTING_KEY = "transcode.request";
    public static final String EVENTS_QUEUE = "sps.video.events";
    public static final String EVENTS_ROUTING_KEY = "transcode.event";
    public static final String DEAD_LETTER_EXCHANGE = "sps.video.dlx";
    public static final String TRANSCODE_DEAD_LETTER_QUEUE = "sps.video.transcode.dlq";

    /** Déclarés automatiquement dans RabbitMQ au démarrage (s'ils n'existent pas déjà). */
    @Bean
    public Declarables videoProcessingTopology() {
        DirectExchange exchange = new DirectExchange(EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);

        // durable : la file et ses messages survivent à un redémarrage de RabbitMQ
        Queue transcode = QueueBuilder.durable(TRANSCODE_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(TRANSCODE_ROUTING_KEY)
                .build();
        Queue deadLetters = QueueBuilder.durable(TRANSCODE_DEAD_LETTER_QUEUE).build();
        Queue events = QueueBuilder.durable(EVENTS_QUEUE).build();

        return new Declarables(exchange, deadLetterExchange, transcode, deadLetters, events,
                BindingBuilder.bind(transcode).to(exchange).with(TRANSCODE_ROUTING_KEY),
                BindingBuilder.bind(events).to(exchange).with(EVENTS_ROUTING_KEY),
                BindingBuilder.bind(deadLetters).to(deadLetterExchange).with(TRANSCODE_ROUTING_KEY));
    }

    /**
     * Messages en JSON. "InferredType" : le JSON est converti dans le type du paramètre de la
     * méthode @RabbitListener, sans se fier au nom de classe Java envoyé par l'autre application
     * (qui n'est pas le même dans le backend et le worker).
     */
    @Bean
    public MessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
