-- Réponses aux commentaires, sur un seul niveau comme sur YouTube : une réponse pointe toujours
-- vers un commentaire principal (parent_id), jamais vers une autre réponse.
-- Supprimer un commentaire supprime ses réponses.
ALTER TABLE video_comments
    ADD COLUMN parent_id   UUID REFERENCES video_comments (id) ON DELETE CASCADE,
    ADD COLUMN reply_count BIGINT NOT NULL DEFAULT 0;   -- nombre de réponses, pour "Voir les 3 réponses"

-- Les réponses d'un commentaire, de la plus ancienne à la plus récente
CREATE INDEX idx_video_comments_parent ON video_comments (parent_id, created_at);
