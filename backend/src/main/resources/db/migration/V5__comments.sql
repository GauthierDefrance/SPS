-- Commentaires sous les vidéos. Supprimer la vidéo ou l'auteur supprime ses commentaires.
CREATE TABLE video_comments (
    id         UUID PRIMARY KEY,
    video_id   UUID          NOT NULL REFERENCES videos (id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    edited_at  TIMESTAMPTZ,                       -- null tant que le commentaire n'a pas été modifié
    likes      BIGINT        NOT NULL DEFAULT 0,
    dislikes   BIGINT        NOT NULL DEFAULT 0
);

-- Les commentaires d'une vidéo, du plus récent au plus ancien
CREATE INDEX idx_video_comments_video ON video_comments (video_id, created_at DESC);

-- Likes / dislikes des commentaires : une seule réaction par utilisateur et par commentaire
CREATE TABLE comment_reactions (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    comment_id UUID        NOT NULL REFERENCES video_comments (id) ON DELETE CASCADE,
    type       VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, comment_id)
);
