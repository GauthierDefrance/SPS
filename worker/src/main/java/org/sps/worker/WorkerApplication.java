package org.sps.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Worker de transcodage. Il prend les vidéos à traiter dans RabbitMQ, les découpe et les compresse
 * en plusieurs qualités HLS avec FFmpeg, range le résultat dans le stockage S3, et renvoie sa
 * progression au backend par RabbitMQ. Il n'a aucun accès à la base de données.
 *
 * Sans état : on peut en lancer autant qu'on veut, sur n'importe quelle machine qui joint
 * RabbitMQ et le stockage. Chacun prend la vidéo suivante dès qu'il est libre.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
