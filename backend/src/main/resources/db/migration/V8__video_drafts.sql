-- Brouillons : une vidéo envoyée n'est publiée qu'une fois que son propriétaire a vérifié ses
-- informations (titre, miniature...) et cliqué "Publier". published_at NULL = brouillon.
ALTER TABLE videos ADD COLUMN published_at TIMESTAMPTZ;

-- Les vidéos existantes étaient déjà en ligne : publiées à leur date d'envoi
UPDATE videos SET published_at = created_at;

-- Catalogue et pages de chaîne : vidéos publiées, les plus récentes d'abord
CREATE INDEX idx_videos_published ON videos (published_at DESC) WHERE published_at IS NOT NULL;
