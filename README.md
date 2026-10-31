# SPS : Streaming Platform Service

Projet d'apprentissage : une plateforme de streaming vidéo (VOD, puis live).

**Stack :** Spring Boot · Vue 3 + Vuetify + Vite · PostgreSQL · SeaweedFS (S3) · RabbitMQ · FFmpeg · Docker Compose · MediaMTX (phase live)

La feuille de route complète est dans [docs/ROADMAP.md](docs/ROADMAP.md).

## Démarrer l'infrastructure

Prérequis : Docker Desktop (avec Docker Compose v2).

```bash
cp .env.example .env
docker compose up -d
```

| Service                | URL / port                  | Identifiants (`.env`)                          |
|------------------------|-----------------------------|------------------------------------------------|
| PostgreSQL             | `localhost:5434`            | `POSTGRES_USER` / `POSTGRES_PASSWORD`          |
| SeaweedFS (API S3)     | http://localhost:8333       | `S3_ACCESS_KEY` / `S3_SECRET_KEY`              |
| SeaweedFS (admin)      | http://localhost:23646      | utilisateur `admin` / `SEAWEEDFS_ADMIN_PASSWORD` |

Les buckets `videos-raw` et `videos-hls` sont créés automatiquement par le script
[storage/init.sh](storage/init.sh). Il est exécuté par le conteneur `storage-init` au démarrage, qui s'arrête ensuite
(état `Exited (0)`). Le script peut être relancé sans risque : un bucket existant est simplement ignoré.

Commandes utiles :

```bash
docker compose ps
docker compose logs -f seaweedfs
docker compose down        # arrête les conteneurs (les données sont conservées)
docker compose down -v     # arrête et SUPPRIME les données
```

## Stockage objet : SeaweedFS

Le projet utilise [SeaweedFS](https://github.com/seaweedfs/seaweedfs) (image `chrislusf/seaweedfs:4.51`) en mode
`mini` : master, volume, filer, API S3 et interface d'administration dans un seul conteneur. Il est conçu pour
stocker un très grand nombre de petits fichiers, ce qui correspond aux segments vidéo HLS.

Le backend ne dépend que du protocole S3 (SDK `io.minio:minio`, compatible avec tout stockage S3) : on peut revenir
à MinIO ou passer à Garage, Ceph... en changeant seulement le service Docker et `sps.s3.endpoint`.

> Le projet utilisait MinIO auparavant. Ses images Docker ne sont plus publiées gratuitement depuis fin 2025 et sa
> version open source n'est plus que maintenue. L'ancien volume `sps_minio-data` peut être supprimé avec
> `docker volume rm sps_minio-data` une fois la migration validée.

## Lancer le backend

Prérequis : Java 21 et FFmpeg (avec `ffprobe`) dans le `PATH`, ou indiqués par `FFMPEG_PATH` / `FFPROBE_PATH`.

```bash
cd backend
./mvnw spring-boot:run
```

- Swagger UI : http://localhost:8081/swagger-ui.html
- Lecteur de test (liste des vidéos, HLS multi-qualités) : http://localhost:8081/player.html

Le port se change avec `server.port` dans `backend/src/main/resources/application.yaml`.

## Lancer le frontend

Prérequis : Node.js 22. Le backend doit tourner (port 8081 par défaut).

```bash
cd frontend
npm install
npm run dev
```

Application : http://localhost:5173. En développement, Vite relaie `/api` vers le backend (voir
[vite.config.ts](frontend/vite.config.ts), variable `BACKEND_URL` pour changer d'adresse).

Interface en **Vue 3** avec les composants **Vuetify** et les icônes **Font Awesome** : voir
[frontend/README.md](frontend/README.md) pour l'organisation du code.

| Page | Rôle |
|---|---|
| `/` | Explorer et rechercher (titre, durée, date, tri) |
| `/watch/:id` | Lire une vidéo (choix de la qualité), j'aime / je n'aime pas, s'abonner, commentaires (écrire, modifier, supprimer, liker) ; le propriétaire peut la supprimer ; un brouillon y est signalé (visible par lui seul) |
| `/channel/:id` | Chaîne publique d'un utilisateur |
| `/subscriptions` | Dernières vidéos des chaînes suivies |
| `/me` | Mon profil : importer une vidéo, mes vidéos (brouillons, traitement, modifier, supprimer), j'aime, historique |
| `/me/videos/:id` | Détails d'une de mes vidéos : titre, description, visibilité, miniature ; « Publier » pour un brouillon une fois traité |
| `/login`, `/register` | Connexion, inscription |

## Traitement des vidéos (RabbitMQ + workers)

```
Client ──upload──> Backend ──original──> SeaweedFS (videos-raw)
                     │ vidéo en base : PENDING
                     └─ message ──> RabbitMQ : sps.video.transcode ──> un worker libre
                                                                          │ FFmpeg : 144p … 2160p en HLS
                                                                          │ segments -> SeaweedFS (videos-hls)
Backend <── sps.video.events (progression, READY, FAILED) ────────────────┘
   └─ met à jour la base : statut, %, étape, qualités
```

- Le **backend** reçoit l'upload, range l'original et publie un message. Il est le seul à écrire en base.
- Les **workers** (dossier [worker/](worker/)) n'ont besoin que de RabbitMQ et du stockage S3. Chacun traite
  une vidéo à la fois ; RabbitMQ donne chaque vidéo à un seul worker libre.
- **Monter en charge** : ajouter des workers, sur cette machine ou sur d'autres qui joignent RabbitMQ et S3.

```bash
docker compose up -d --build --scale worker=3
```

- Suivre l'avancement : `GET /api/videos/{id}` (champs `status` et `processing`).
- Interface RabbitMQ : http://localhost:15672 (`RABBITMQ_USER` / `RABBITMQ_PASSWORD`). La file
  `sps.video.transcode.dlq` contient les vidéos que les workers n'ont pas pu traiter.
- Lancer un worker hors Docker (FFmpeg dans le `PATH`) : `cd worker && ./mvnw spring-boot:run`.
