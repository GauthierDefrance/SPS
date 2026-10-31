package org.sps.backend.storage;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.messages.DeleteRequest;
import io.minio.messages.DeleteResult;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Petite façade au-dessus du stockage objet : écrire, lire, supprimer. */
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final MinioClient s3;

    /** Envoie un flux. La taille connue permet au SDK de découper l'envoi en plusieurs parties. */
    public void upload(String bucket, String key, InputStream data, long size, String contentType) {
        try {
            s3.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(data, size, -1L)
                    .contentType(contentType)
                    .build());
        } catch (MinioException e) {
            throw new StorageException("Échec de l'envoi de " + key, e);
        }
    }

    /** Ouvre un flux sur l'objet entier (l'appelant le ferme). 404 si l'objet n'existe pas. */
    public InputStream read(String bucket, String key) {
        try {
            return s3.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable");
            }
            throw new StorageException("Échec de la lecture de " + key, e);
        } catch (MinioException e) {
            throw new StorageException("Échec de la lecture de " + key, e);
        }
    }

    public void delete(String bucket, String key) {
        try {
            s3.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (MinioException e) {
            throw new StorageException("Échec de la suppression de " + key, e);
        }
    }

    /**
     * Supprime tous les objets dont la clé commence par prefix (ex. "X8z_K9pLm2Q/" : toutes les
     * qualités HLS d'une vidéo). Le stockage objet n'a pas de vrais dossiers : on liste puis on
     * supprime par lots. Les deux résultats sont "paresseux" : rien ne se passe tant qu'on ne les parcourt pas.
     */
    public void deletePrefix(String bucket, String prefix) {
        try {
            List<DeleteRequest.Object> keys = new ArrayList<>();
            for (Result<Item> item : s3.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build())) {
                keys.add(new DeleteRequest.Object(item.get().objectName()));
            }
            if (keys.isEmpty()) {
                return;
            }
            for (Result<DeleteResult.Error> error : s3.removeObjects(RemoveObjectsArgs.builder().bucket(bucket).objects(keys).build())) {
                throw new StorageException("Échec de la suppression de " + prefix + " : " + error.get(), null);
            }
        } catch (MinioException e) {
            throw new StorageException("Échec de la suppression de " + prefix, e);
        }
    }

    /** Erreur de communication avec le stockage (indisponible, refus...). */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
