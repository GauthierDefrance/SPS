-- Date des likes / dislikes : pour afficher "Vidéos que j'aime" de la plus récente à la plus ancienne
ALTER TABLE video_reactions ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX idx_video_reactions_user ON video_reactions (user_id, created_at DESC);

-- Historique de visionnage : une ligne par (utilisateur, vidéo), mise à jour à chaque visionnage.
-- position_seconds permet de reprendre la lecture là où on s'était arrêté.
CREATE TABLE watch_history (
    id               UUID PRIMARY KEY,
    user_id          UUID             NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    video_id         UUID             NOT NULL REFERENCES videos (id) ON DELETE CASCADE,
    position_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
    watched_at       TIMESTAMPTZ      NOT NULL,
    UNIQUE (user_id, video_id)
);

CREATE INDEX idx_watch_history_user ON watch_history (user_id, watched_at DESC);
