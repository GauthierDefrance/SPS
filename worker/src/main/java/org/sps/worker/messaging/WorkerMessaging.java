package org.sps.worker.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * Topologie RabbitMQ, déclarée À L'IDENTIQUE dans le backend (ProcessingMessaging) : le premier
 * qui démarre la crée, l'autre la retrouve. Si les arguments d'une file différaient,
 * RabbitMQ refuserait la déclaration.
 *
 *   exchange "sps.video"
 *     ├─ "transcode.request" ─> sps.video.transcode   (backend -> workers)  [écoutée ici]
 *     └─ "transcode.event"   ─> sps.video.events      (workers -> backend)  [publiée ici]
 *   exchange "sps.video.dlx" ─> sps.video.transcode.dlq (vidéos que le worker n'a pas pu traiter)
 */
@Configuration
public class WorkerMessaging {

    public static final String EXCHANGE = "sps.video";
    public static final String TRANSCODE_QUEUE = "sps.video.transcode";
    public static final String TRANSCODE_ROUTING_KEY = "transcode.request";
    public static final String EVENTS_QUEUE = "sps.video.events";
    public static final String EVENTS_ROUTING_KEY = "transcode.event";
    public static final String DEAD_LETTER_EXCHANGE = "sps.video.dlx";
    public static final String TRANSCODE_DEAD_LETTER_QUEUE = "sps.video.transcode.dlq";

    @Bean
    public Declarables videoProcessingTopology() {
        DirectExchange exchange = new DirectExchange(EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);

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

    /** JSON, converti dans le type du paramètre du listener (voir le backend pour le détail). */
    @Bean
    public MessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
