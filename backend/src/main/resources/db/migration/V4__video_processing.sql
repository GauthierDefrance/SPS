-- Suivi du traitement d'une vidéo par les workers (mis à jour par le backend à partir des
-- messages de progression qu'ils envoient via RabbitMQ).
ALTER TABLE videos
    ADD COLUMN processing_progress   INTEGER NOT NULL DEFAULT 0,   -- 0 à 100 %
    ADD COLUMN processing_step       VARCHAR(50),                  -- étape en cours (ex. "ENCODAGE 720p")
    ADD COLUMN processing_error      VARCHAR(500),                 -- raison d'un échec
    ADD COLUMN processed_by          VARCHAR(100),                 -- worker qui a traité la vidéo
    ADD COLUMN processing_updated_at TIMESTAMPTZ;

-- Qualités HLS produites pour chaque vidéo, identifiées par leur hauteur (144, 240, ..., 2160)
CREATE TABLE video_renditions (
    video_id UUID    NOT NULL REFERENCES videos (id) ON DELETE CASCADE,
    height   INTEGER NOT NULL,
    PRIMARY KEY (video_id, height)
);
