#!/bin/sh
# Crée les buckets s'ils n'existent pas, puis s'arrête.
# Exécuté par le service "storage-init" (image curlimages/curl) une fois le stockage prêt.
# Aucun client spécifique : on appelle directement l'API S3 (PUT /<bucket>), signée en SigV4 par curl.
# Fonctionne donc avec n'importe quel stockage compatible S3 (SeaweedFS, MinIO, Garage...).
set -eu

ENDPOINT="${S3_ENDPOINT:-http://seaweedfs:8333}"
REGION="${S3_REGION:-us-east-1}"

create_bucket() {
  bucket="$1"
  code=$(curl -s -o /tmp/response.xml -w '%{http_code}' \
    --aws-sigv4 "aws:amz:${REGION}:s3" \
    --user "${S3_ACCESS_KEY}:${S3_SECRET_KEY}" \
    -X PUT "${ENDPOINT}/${bucket}")

  case "$code" in
    200) echo "Bucket créé : ${bucket}" ;;
    409) echo "Bucket déjà existant : ${bucket}" ;;
    *)
      echo "Échec de création du bucket ${bucket} (HTTP ${code}) :" >&2
      cat /tmp/response.xml >&2
      exit 1
      ;;
  esac
}

create_bucket "${S3_BUCKET_RAW}"
create_bucket "${S3_BUCKET_HLS}"
create_bucket "${S3_BUCKET_IMAGES:-images}"
