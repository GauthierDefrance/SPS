-- Miniature choisie par le propriétaire :
--   AUTO          thumbnail.jpg, image prise au tiers de la vidéo (par défaut)
--   AUTO_1..AUTO_3 autres images proposées par le worker (début, milieu, fin)
--   CUSTOM        image envoyée par le propriétaire
ALTER TABLE videos
    ADD COLUMN thumbnail            VARCHAR(10) NOT NULL DEFAULT 'AUTO',
    ADD COLUMN thumbnail_candidates INTEGER     NOT NULL DEFAULT 0,   -- nombre d'images AUTO_n disponibles
    ADD COLUMN custom_thumbnail_at  TIMESTAMPTZ,                      -- date d'envoi de l'image perso (null : aucune)
    ADD COLUMN thumbnail_updated_at TIMESTAMPTZ;                      -- dernier changement de miniature (cache)

-- Photo de profil : date du dernier envoi, null si l'utilisateur n'en a pas.
-- Elle sert aussi de "version" dans l'URL de l'image (?v=...) pour que le navigateur recharge la nouvelle.
ALTER TABLE users ADD COLUMN avatar_updated_at TIMESTAMPTZ;
