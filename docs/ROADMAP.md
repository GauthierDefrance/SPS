# Roadmap SPS

Plateforme de vidéo à la demande (VOD) d'abord, puis le direct (live) dans un second temps.

**Stack :** Spring Boot (Java 21) · Vue 3 + Vuetify + Vite · PostgreSQL · SeaweedFS (S3) · FFmpeg · Docker Compose · (plus tard) MediaMTX

## Architecture cible

```
                 REST/JSON (JWT)
[Vue 3 + Vite] ─────────────────> [Spring Boot API] ──> PostgreSQL
      │                                 │                 (users, videos, renditions, watch_history)
      │                                 ├──> S3 (SeaweedFS) : bucket videos-raw  (fichiers uploadés)
      │                                 └──> file de jobs (table `transcode_jobs`, puis RabbitMQ en option)
      │                                                │
      │                                       [Worker FFmpeg]  ──> S3 (SeaweedFS) : bucket videos-hls
      │                                                              (master.m3u8 + segments)
      └──── lecture HLS (hls.js) <──── URLs pré-signées S3
```

Principe clé : **le flux vidéo ne passe jamais par le backend**. Le backend vérifie les droits et
distribue des URLs pré-signées. Le navigateur télécharge les fichiers directement depuis le stockage S3.

---

## Phase 0 : infrastructure ✅

- [x] `docker-compose.yml` avec PostgreSQL et SeaweedFS (stockage S3, remplace MinIO)
- [x] Création automatique des buckets `videos-raw` et `videos-hls` (`storage/init.sh`)
- [ ] Se connecter à l'interface d'administration SeaweedFS (http://localhost:23646) et vérifier les buckets
- [ ] Se connecter à PostgreSQL avec un client (DBeaver, psql) sur `localhost:5434`

**Notions :** volumes Docker, réseaux, variables d'environnement, healthchecks.

## Phase 1 : squelette du backend Spring Boot

- [ ] Générer le projet avec [start.spring.io](https://start.spring.io) : Web, Data JPA, PostgreSQL Driver, Flyway, Validation, Security, Actuator
- [ ] Connexion à PostgreSQL et première migration Flyway (`V1__init.sql`)
- [x] Client S3 (SDK `io.minio:minio`) configuré par `application.yaml`
- [ ] Endpoint `GET /api/health` et documentation OpenAPI (springdoc)

**Notions :** injection de dépendances, profils Spring, migrations de schéma.

## Phase 2 : authentification

- [ ] Table `users` (email unique, hash BCrypt, rôle `USER` / `ADMIN`)
- [ ] `POST /api/auth/register`, `POST /api/auth/login` qui renvoie un JWT
- [ ] Filtre Spring Security pour valider le JWT sur `/api/**`
- [ ] Tests d'intégration avec Testcontainers

**Notions :** hachage de mots de passe, JWT (access et refresh), CORS.

## Phase 3 : upload de vidéos

- [ ] Table `videos` (id, owner_id, title, description, status, raw_object_key, created_at)
- [ ] Statuts : `UPLOADING → UPLOADED → PROCESSING → READY | FAILED`
- [ ] `POST /api/videos` crée l'entrée et renvoie une **URL pré-signée PUT** vers `videos-raw`
- [ ] Le navigateur envoie le fichier directement au stockage S3, puis appelle `POST /api/videos/{id}/complete`

**Notions :** URLs pré-signées S3, upload direct, gros fichiers (multipart).

## Phase 4 : transcodage HLS

- [ ] Table `transcode_jobs` (verrouillage avec `SELECT … FOR UPDATE SKIP LOCKED`)
- [ ] Worker (module Spring Boot séparé, image Docker avec FFmpeg) qui :
  - télécharge la vidéo brute depuis le stockage S3
  - lance FFmpeg pour produire du HLS multi-qualité (360p / 720p / 1080p) et une miniature
  - envoie `master.m3u8` et les segments dans `videos-hls/{videoId}/`
  - met à jour le statut et la durée de la vidéo
- [ ] Ajouter le service `worker` au `docker-compose.yml`

Exemple de commande FFmpeg (720p seul) :

```bash
ffmpeg -i input.mp4 -c:v libx264 -preset veryfast -vf scale=-2:720 -c:a aac \
  -hls_time 6 -hls_playlist_type vod -hls_segment_filename "720p_%03d.ts" 720p.m3u8
```

**Notions :** traitement asynchrone, codecs, bitrate adaptatif (ABR), idempotence des jobs.

## Phase 5 : frontend Vue

- [x] Projet Vite + Vue 3 + TypeScript, Vue Router, composants Vuetify, icônes Font Awesome
- [x] Pages : connexion / inscription, catalogue, page vidéo, upload, "mes vidéos"
- [x] Lecteur HLS avec `hls.js` (choix de la qualité, reprise de lecture)
- [ ] Proxy Vite vers l'API en développement, puis service `frontend` (Nginx) dans le Compose

**Notions :** gestion de l'état serveur, stockage du token, protection des routes.

## Phase 6 : lecture sécurisée et fonctionnalités

- [ ] `GET /api/videos/{id}/playback` vérifie les droits et réécrit le `.m3u8` avec des URLs pré-signées
      (ou bien utilise Nginx `secure_link` / un bucket privé derrière un proxy)
- [ ] Historique et reprise (`watch_history` : user, video, position, updated_at)
- [ ] Recherche (`tsvector` PostgreSQL), pagination, catégories
- [ ] Rôle admin : modération et suppression (nettoyage des objets S3)

## Phase 7 : live avec MediaMTX (bonus)

- [ ] Ajouter `bluenviron/mediamtx` au Compose (RTMP 1935, HLS 8888, WebRTC 8889)
- [ ] Clé de stream par utilisateur, vérifiée par le backend (`authHTTPAddress` de MediaMTX)
- [ ] Diffuser depuis OBS en RTMP, lire en HLS ou WebRTC dans le frontend
- [ ] Enregistrement du live dans le stockage S3, qui devient ensuite une VOD

## Pour aller plus loin

- Observabilité : Actuator, Prometheus, Grafana
- CI GitHub Actions (build, tests, images Docker)
- CDN ou cache Nginx devant les segments HLS
- Chiffrement HLS AES-128 ou DRM (pour la culture générale)
