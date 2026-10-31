package org.sps.worker.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Lecture de l'original et écriture des fichiers HLS dans le stockage S3. */
@Component
public class S3Storage {

    private final MinioClient s3;
    private final S3Properties props;

    public S3Storage(S3Properties props) {
        this.props = props;
        this.s3 = MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey())
                .build();
    }

    /** Copie le fichier original dans un fichier local (FFmpeg travaille sur des fichiers). */
    public void downloadRaw(String key, Path target) throws IOException {
        try (InputStream in = s3.getObject(GetObjectArgs.builder().bucket(props.bucketRaw()).object(key).build())) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (MinioException e) {
            throw new IOException("Lecture de " + key + " impossible dans le stockage", e);
        }
    }

    public void uploadHls(String key, Path file, String contentType) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            s3.putObject(PutObjectArgs.builder()
                    .bucket(props.bucketHls()).object(key)
                    .stream(in, Files.size(file), -1L)
                    .contentType(contentType)
                    .build());
        } catch (MinioException e) {
            throw new IOException("Écriture de " + key + " impossible dans le stockage", e);
        }
    }
}
