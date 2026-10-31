-- Schéma initial. Flyway exécute ce fichier une seule fois puis le note dans flyway_schema_history :
-- pour faire évoluer le schéma, on ajoute V2__..., V3__..., on ne modifie jamais un fichier déjà appliqué.

-- Comptes clients
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(100),
    surname       VARCHAR(100)
);

-- Abonnements : follower_id suit following_id
CREATE TABLE user_subscriptions (
    follower_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    following_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (follower_id, following_id),
    CHECK (follower_id <> following_id)          -- on ne s'abonne pas à soi-même
);

-- Vidéos (le fichier lui-même est dans le stockage S3)
CREATE TABLE videos (
    id                 UUID PRIMARY KEY,
    public_id          VARCHAR(11)      NOT NULL UNIQUE,
    owner_id           UUID             NOT NULL REFERENCES users (id),
    title              VARCHAR(255)     NOT NULL,
    description        VARCHAR(5000),
    duration           DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ      NOT NULL,
    likes              BIGINT           NOT NULL DEFAULT 0,
    dislikes           BIGINT           NOT NULL DEFAULT 0,
    views              BIGINT           NOT NULL DEFAULT 0,
    original_file_size BIGINT,
    raw_file_key       VARCHAR(512),
    status             VARCHAR(20)      NOT NULL,
    policy             VARCHAR(20)      NOT NULL
);

CREATE INDEX idx_videos_owner_id ON videos (owner_id);

-- Likes / dislikes : une seule réaction par utilisateur et par vidéo
CREATE TABLE video_reactions (
    id       UUID PRIMARY KEY,
    user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    video_id UUID        NOT NULL REFERENCES videos (id) ON DELETE CASCADE,
    type     VARCHAR(10) NOT NULL,
    UNIQUE (user_id, video_id)
);
