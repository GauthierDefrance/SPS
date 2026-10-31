# Parcours SPS : construire une plateforme vidéo, étape par étape

Chaque étape est un **mini-projet** : un petit morceau qui fonctionne et se vérifie, avant de
passer au suivant. Tu écris tout le code. Claude te guide (voir [CLAUDE.md](CLAUDE.md)) : il
pose des questions, donne des pistes et des liens, relit ton travail, mais ne donne pas la
solution.

**Comment utiliser ce plan**

- Une étape à la fois. Lis l'objectif et les critères « C'est fini quand » avant de coder.
- Les **indices** sont repliés : ouvre-les seulement si tu bloques, un par un.
- Quand tous les critères sont remplis, demande à Claude de vérifier, puis coche l'étape.
- Commit à chaque étape réussie (une branche par étape si tu veux t'entraîner à git).
- La version complète existe sur la branche `reference`. Elle montre **une** façon de faire, pas
  la seule. Tes choix de design passent avant.

---

## Partie 0 : préparer le terrain

### - [ ] Étape 0 : les outils et un premier « Hello »

**Objectif** : avoir un poste prêt, et un backend Spring Boot qui répond.

**Tu construis** : un projet Spring Boot (dossier `backend/`) avec une route `GET /api/hello`
qui renvoie un message en JSON.

**C'est fini quand**
- `java -version` affiche 21, `node -v` affiche 22 ou plus, `docker version` répond.
- `./mvnw spring-boot:run` démarre le backend sans erreur.
- `curl http://localhost:8081/api/hello` renvoie ton message en JSON (ex. `{"message":"..."}`).

**Notions** : JDK, Maven et son « wrapper » (`mvnw`), projet Spring Boot, contrôleur REST, JSON.

**Ressources**
- JDK 21 : https://adoptium.net
- Générateur de projet : https://start.spring.io
- Guide officiel « Building a RESTful Web Service » : https://spring.io/guides/gs/rest-service

<details><summary>Indices</summary>

1. Sur start.spring.io, quelles « dependencies » faut-il pour une API web ? (Une seule suffit.)
2. Le port par défaut est 8080 : où se règle-t-il dans `application.properties` ou `.yaml` ?
3. Comment Spring transforme-t-il un objet Java renvoyé par ta méthode en JSON ? Essaie de
   renvoyer un `record` plutôt qu'une `String`.
</details>

---

## Partie 1 : une API REST

### - [ ] Étape 1 : des vidéos en mémoire

**Objectif** : comprendre une API REST avant d'ajouter une base de données.

**Tu construis** : un CRUD de vidéos (titre, description) gardées dans une liste en mémoire :
créer, lister, lire une vidéo, modifier, supprimer.

**C'est fini quand**
- `POST /api/videos` crée une vidéo et répond **201 Created** avec la vidéo (et son id).
- `GET /api/videos` liste les vidéos ; `GET /api/videos/{id}` en renvoie une, ou **404**.
- `PUT` (ou `PATCH`) modifie, `DELETE` supprime (réponse **204 No Content**).
- Un titre vide est refusé avec **400 Bad Request**.
- Le contrôleur ne contient pas la logique : elle est dans une classe « service ».

**Notions** : verbes HTTP, codes de statut, DTO (objet qui transporte les données de l'API),
validation, séparation contrôleur / service, injection de dépendances.

**Ressources**
- Codes HTTP : https://developer.mozilla.org/fr/docs/Web/HTTP/Status
- Annotations des contrôleurs Spring : https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods.html
- Validation : https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html

<details><summary>Indices</summary>

1. Pourquoi ne pas renvoyer directement l'objet que tu stockes ? Qu'arrivera-t-il le jour où il
   contiendra un champ secret ?
2. Comment dire à Spring « cette méthode répond 201 » ou « 204 » ? Cherche `@ResponseStatus` et
   `ResponseEntity`.
3. Pour la validation : il faut une annotation sur le champ du DTO **et** une sur le paramètre de
   la méthode. Lesquelles ?
4. Deux requêtes en même temps sur ta liste : est-ce un problème ? (Pas besoin de le régler
   ici, la base de données s'en chargera.)
</details>

### - [ ] Étape 2 : PostgreSQL, JPA et migrations

**Objectif** : stocker les vidéos dans une vraie base, qui survit au redémarrage.

**Tu construis** : un `docker-compose.yml` qui lance PostgreSQL, une entité `Video`, un
« repository » Spring Data, et une première migration Flyway qui crée la table.

**C'est fini quand**
- `docker compose up -d` lance PostgreSQL ; les identifiants viennent d'un fichier `.env`
  **non commité** (`.gitignore`), avec un `.env.example` commité à la place.
- Après un redémarrage du backend, les vidéos créées sont toujours là.
- La table est créée par une migration `V1__...sql`, pas par Hibernate (`ddl-auto` à `validate`).
- Chaque vidéo a une date de création remplie automatiquement.

**Notions** : conteneur et volume Docker, variables d'environnement, ORM, entité, repository,
migration de schéma, clé primaire (et pourquoi un UUID plutôt qu'un nombre qui se suit).

**Ressources**
- Docker Compose : https://docs.docker.com/compose/
- Image PostgreSQL : https://hub.docker.com/_/postgres
- Guide « Accessing Data with JPA » : https://spring.io/guides/gs/accessing-data-jpa
- Flyway dans Spring Boot : https://docs.spring.io/spring-boot/how-to/data-initialization.html

<details><summary>Indices</summary>

1. Sans « volume », que deviennent les données quand tu supprimes le conteneur ?
2. Comment Spring Boot lit-il une variable d'environnement dans `application.yaml` ?
   (Cherche la syntaxe `${...}`.)
3. Si PostgreSQL est déjà installé sur ta machine, quel port choisir pour celui de Docker ?
4. Pourquoi `ddl-auto: update` est-il dangereux en production, et qu'apporte une migration
   versionnée ?
</details>

### - [ ] Étape 3 : recherche, tri et pages

**Objectif** : ne jamais renvoyer « toute la table ».

**Tu construis** : `GET /api/videos?q=ski&sort=RECENT&page=0&size=20` : recherche dans le
titre, tri (récentes, anciennes, plus vues), et pagination.

**C'est fini quand**
- La réponse contient les vidéos de la page **et** le nombre total de pages et d'éléments.
- La recherche ignore les majuscules (« SKI » trouve « Vacances au ski »).
- Un `size` énorme (ex. 10000) est limité côté serveur.
- Chercher `%` ou `_` ne renvoie pas toutes les vidéos.

**Notions** : pagination, `Pageable`, requêtes dérivées du nom de méthode, `LIKE` et ses
caractères spéciaux, index SQL.

**Ressources**
- Requêtes dérivées : https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html
- Pagination et tri : https://docs.spring.io/spring-data/commons/reference/repositories/query-methods-details.html

<details><summary>Indices</summary>

1. Que se passe-t-il pour ton serveur si quelqu'un demande `size=1000000` ?
2. Renvoyer l'objet `Page` de Spring tel quel donne un JSON bavard et instable : quels champs
   veux-tu vraiment exposer ?
3. Pour les filtres optionnels (durée, date...), cherche les « Specifications » de Spring Data.
</details>

### - [ ] Étape 4 : des tests automatisés

**Objectif** : vérifier ton API en une commande, et ne plus tout retester à la main.

**Tu construis** : des tests qui appellent ton API (création, 404, 400, pagination) sur une
vraie base PostgreSQL lancée par les tests eux-mêmes.

**C'est fini quand**
- `./mvnw test` passe, Docker lancé, sans ton `docker compose` démarré.
- Au moins un test par code de réponse de l'étape 1 (201, 200, 404, 400, 204).
- Les tests ne dépendent pas de l'ordre dans lequel ils s'exécutent.

**Notions** : test d'intégration, MockMvc, Testcontainers, isolation des tests.

**Ressources**
- Tests dans Spring Boot : https://docs.spring.io/spring-boot/reference/testing/index.html
- MockMvc : https://docs.spring.io/spring-framework/reference/testing/mockmvc.html
- Guide Testcontainers + Spring Boot : https://testcontainers.com/guides/testing-spring-boot-rest-api-using-testcontainers/

<details><summary>Indices</summary>

1. Pourquoi tester sur PostgreSQL plutôt que sur une base en mémoire (H2) ?
2. Un test crée une vidéo, le suivant compte les vidéos : comment éviter qu'ils se gênent ?
3. Cherche `@ServiceConnection` : il évite de recopier l'adresse de la base de test.
</details>

### - [ ] Étape 5 : des erreurs propres

**Objectif** : des messages d'erreur utiles pour le client, sans rien révéler du serveur.

**Tu construis** : un format d'erreur unique pour toute l'API (404, 400 avec le détail des
champs invalides, 409, 500).

**C'est fini quand**
- Toutes les erreurs ont la même forme JSON (le standard « Problem Details »).
- Un 400 de validation dit quel champ est invalide et pourquoi, en français.
- Une erreur imprévue (500) ne montre **ni trace Java ni message technique** au client ; le
  détail est seulement dans les logs du serveur.

**Notions** : gestion centralisée des exceptions, RFC 9457, logs, information exposée.

**Ressources**
- Problem Details dans Spring : https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- RFC 9457 : https://datatracker.ietf.org/doc/html/rfc9457

<details><summary>Indices</summary>

1. Cherche `@RestControllerAdvice` et la classe `ProblemDetail`.
2. Quelle propriété de Spring Boot active Problem Details pour les erreurs de Spring lui-même ?
3. Pourquoi une trace Java dans une réponse est-elle une information utile à un attaquant ?
</details>

---

## Partie 2 : comptes et sécurité

### - [ ] Étape 6 : l'inscription

**Objectif** : créer des comptes sans jamais stocker un mot de passe lisible.

**Tu construis** : `POST /api/auth/register` (email, mot de passe, nom) et la table des
utilisateurs.

**C'est fini quand**
- Le mot de passe est stocké haché (BCrypt ou Argon2) : impossible de le relire en base.
- Un email déjà utilisé donne **409 Conflict**, même si deux inscriptions arrivent en même temps.
- Un mot de passe de moins de 8 caractères est refusé.
- La réponse ne contient jamais le mot de passe ni son hash.

**Notions** : hachage vs chiffrement, sel, contrainte d'unicité, Spring Security (dépendance).

**Ressources**
- Stockage des mots de passe (OWASP) : https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- `PasswordEncoder` : https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html

<details><summary>Indices</summary>

1. Vérifier « l'email existe-t-il ? » puis insérer : que se passe-t-il entre les deux si deux
   requêtes arrivent ensemble ? Qui peut garantir l'unicité à coup sûr ?
2. Ajouter Spring Security protège **toutes** les routes d'un coup : comment rouvrir
   l'inscription ?
3. `Alice@Mail.com` et `alice@mail.com` : le même compte ?
</details>

### - [ ] Étape 7 : la connexion avec un jeton (JWT)

**Objectif** : s'identifier une fois, puis prouver qui on est à chaque requête.

**Tu construis** : `POST /api/auth/login` qui renvoie un jeton JWT signé ; `GET /api/users/me`
qui renvoie l'utilisateur connecté ; les routes d'écriture deviennent réservées aux connectés.

**C'est fini quand**
- Un mauvais mot de passe et un email inconnu donnent la **même** réponse 401.
- `GET /api/users/me` sans jeton : 401 ; avec le jeton : tes informations.
- Lister et lire les vidéos reste possible sans compte ; créer, modifier, supprimer non.
- Le secret de signature vient de `.env`, fait au moins 32 caractères, et le jeton expire.

**Bonus** : un « refresh token » dans un cookie `HttpOnly` pour rester connecté sans garder
un jeton longue durée.

**Notions** : authentification sans session, JWT (en-tête, contenu, signature), en-tête
`Authorization: Bearer`, filtre de sécurité, expiration.

**Ressources**
- Introduction aux JWT : https://jwt.io/introduction
- JWT dans Spring Security (resource server) : https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Architecture de Spring Security : https://docs.spring.io/spring-security/reference/servlet/architecture.html
- Bonnes pratiques JWT (OWASP) : https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html

<details><summary>Indices</summary>

1. Pourquoi « email inconnu » et « mauvais mot de passe » doivent-ils répondre pareil ?
2. Le contenu d'un JWT est-il secret ? (Colle un jeton sur jwt.io pour voir.) Que ne faut-il
   donc jamais y mettre ?
3. Spring Security sait déjà vérifier un JWT : cherche `oauth2ResourceServer` avant d'écrire
   ton propre filtre.
4. Où stocker un jeton dans le navigateur ? Compare mémoire, `localStorage` et cookie
   `HttpOnly` face à une attaque XSS.
</details>

### - [ ] Étape 8 : propriétaire et visibilité

**Objectif** : chaque vidéo appartient à quelqu'un, et chacun ne voit que ce qu'il a le droit de voir.

**Tu construis** : le lien vidéo → propriétaire, et une visibilité par vidéo : publique (dans le
catalogue), non répertoriée (visible avec le lien), privée (propriétaire seulement).

**C'est fini quand**
- Seul le propriétaire peut modifier ou supprimer sa vidéo : **403** pour les autres.
- Une vidéo privée répond **404** à tout autre que son propriétaire (pas 403 : pourquoi ?).
- Le catalogue ne liste que les vidéos publiques.
- `GET /api/users/{id}/videos` : la page publique d'une chaîne.
- Tes tests couvrent ces cas avec deux utilisateurs différents.

**Notions** : autorisation vs authentification, relation `@ManyToOne`, IDOR (accès à la
ressource d'un autre en changeant un id), identifiants publics.

**Ressources**
- IDOR (OWASP) : https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html

<details><summary>Indices</summary>

1. Où vérifier les droits : dans le contrôleur, dans le service, ou dans les deux ? Que se
   passe-t-il si une autre route appelle le même service ?
2. Des ids qui se suivent (1, 2, 3...) dans l'URL : que peut-on deviner ? (La version
   `reference` utilise un id public court et aléatoire, façon YouTube.)
3. Charger une vidéo puis son propriétaire fait deux requêtes SQL : cherche « N+1 » et
   `@EntityGraph`.
</details>

---

## Partie 3 : les vraies vidéos

### - [ ] Étape 9 : stocker les fichiers (S3)

**Objectif** : envoyer un fichier vidéo et le ranger dans un stockage objet, pas dans la base.

**Tu construis** : SeaweedFS (compatible S3) dans Docker Compose, et `POST /api/videos` en
`multipart/form-data` qui range le fichier original dans un « bucket ».

**C'est fini quand**
- Le fichier est dans le bucket (visible dans l'interface de SeaweedFS), la base ne garde que sa
  clé et sa taille.
- Un fichier qui n'est pas une vidéo, vide, ou trop gros est refusé proprement.
- Le fichier n'est **jamais** chargé entièrement en mémoire (teste avec une vidéo de 1 Go).
- Si l'enregistrement en base échoue, le fichier ne reste pas orphelin dans le stockage.

**Notions** : stockage objet (bucket, clé), API S3, multipart, flux (stream), cohérence entre
deux systèmes.

**Ressources**
- Envoi de fichiers avec Spring : https://spring.io/guides/gs/uploading-files
- SeaweedFS et l'API S3 : https://github.com/seaweedfs/seaweedfs/wiki/Amazon-S3-API
- Client S3 Java (au choix) : AWS SDK v2 https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html
  ou MinIO https://github.com/minio/minio-java
- Envoi de fichiers (OWASP) : https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html

<details><summary>Indices</summary>

1. Pourquoi ne pas stocker la vidéo dans une colonne de PostgreSQL ?
2. Spring limite la taille des envois par défaut : quelles propriétés `spring.servlet.multipart.*` ?
3. Le `Content-Type` envoyé par le navigateur prouve-t-il que c'est une vidéo ?
4. Qui crée les buckets au premier démarrage ? (Un petit conteneur d'initialisation, ou ton backend.)
</details>

### - [ ] Étape 10 : FFmpeg à la main (sans code)

**Objectif** : comprendre ce que deviendra le traitement automatique, en ligne de commande.

**Tu construis** : à partir d'une vidéo, avec des commandes `ffprobe` et `ffmpeg` que tu écris
toi-même : ses informations, une version HLS en 3 qualités (ex. 360p, 720p, 1080p) avec une
playlist « maître », et une miniature.

**C'est fini quand**
- Tu sais dire la durée, la résolution et les codecs de ta vidéo (avec `ffprobe`).
- Le dossier produit contient `master.m3u8`, un dossier par qualité avec sa playlist et ses
  segments `.ts`.
- La vidéo se lit dans VLC en ouvrant `master.m3u8`, ou dans la démo hls.js en local.
- Tu sais expliquer : codec, conteneur, bitrate, segment, et pourquoi plusieurs qualités.

**Notions** : codecs (H.264, AAC), conteneurs, débit, HLS, lecture adaptative (ABR).

**Ressources**
- Documentation FFmpeg : https://ffmpeg.org/ffmpeg.html et https://ffmpeg.org/ffprobe.html
- Le format HLS dans FFmpeg : https://ffmpeg.org/ffmpeg-formats.html#hls-2
- HLS selon Apple : https://developer.apple.com/streaming/
- Démo de hls.js : https://hlsjs.video-dev.org/demo/

<details><summary>Indices</summary>

1. Commence par **une** qualité, puis ajoute les autres.
2. Une vidéo verticale (téléphone) : « 720p » veut-il dire 720 de haut ou 720 sur le petit côté ?
3. Pour que le lecteur change de qualité sans coupure, les segments de chaque qualité doivent
   commencer aux mêmes instants : cherche « keyframe » et « GOP ».
4. Une vidéo en 480p ne doit pas être « agrandie » en 1080p.
</details>

### - [ ] Étape 11 : un worker et une file de messages

**Objectif** : traiter les vidéos en arrière-plan, sans bloquer l'API, sur plusieurs machines si besoin.

**Tu construis** : RabbitMQ dans Docker Compose ; un second projet Spring Boot (`worker/`) qui
reçoit un message « traite la vidéo X », télécharge l'original, lance FFmpeg (étape 10),
envoie le HLS dans le stockage, et renvoie sa progression au backend.

**C'est fini quand**
- L'envoi d'une vidéo répond tout de suite (**202 Accepted**), statut « en attente ».
- `GET /api/videos/{id}` montre l'avancement (en attente, en cours avec un %, prête, échec).
- Avec 3 workers lancés (`docker compose up --scale worker=3`), 3 vidéos sont traitées en même temps.
- Si on coupe un worker en plein traitement, la vidéo est reprise par un autre.
- Une vidéo illisible passe en « échec » avec un message, et le propriétaire peut relancer.

**Notions** : traitement asynchrone, file de messages, accusé de réception (ack), message
rejoué, idempotence, `ProcessBuilder`, Dockerfile avec FFmpeg.

**Ressources**
- Tutoriels RabbitMQ (1 à 3 pour commencer) : https://www.rabbitmq.com/tutorials
- Spring AMQP : https://docs.spring.io/spring-amqp/reference/
- `ProcessBuilder` : https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ProcessBuilder.html

<details><summary>Indices</summary>

1. Un message reçu deux fois (ça arrive) : ton worker fait-il deux fois le travail, ou
   s'en rend-il compte ?
2. Un message de progression « 40 % » arrive **après** le message « terminé » : l'état doit-il
   revenir à 40 % ?
3. FFmpeg écrit sa progression sur sa sortie : comment la lire depuis Java sans bloquer ?
4. Le backend publie le message, puis la base n'est pas encore à jour quand le worker répond :
   dans quel ordre faire les choses ?
</details>

### - [ ] Étape 12 : servir la vidéo en HLS

**Objectif** : lire la vidéo depuis le navigateur, en respectant les droits d'accès.

**Tu construis** : des routes qui servent `master.m3u8`, les playlists et les segments d'une
vidéo prête, ainsi que sa miniature.

**C'est fini quand**
- La vidéo se lit dans la démo hls.js en donnant l'adresse de ton `master.m3u8`.
- Une vidéo privée n'est pas lisible par un autre utilisateur, même en devinant l'adresse d'un segment.
- Un chemin comme `../../autre-video/...` est refusé.
- Les segments sont mis en cache par le navigateur (ils ne changent jamais).

**Notions** : streaming de fichiers (sans tout charger en mémoire), types MIME, cache HTTP,
traversée de chemin (path traversal).

**Ressources**
- Cache HTTP : https://developer.mozilla.org/fr/docs/Web/HTTP/Caching
- `StreamingResponseBody` : cherche-le dans la doc de Spring MVC (réponses asynchrones).

<details><summary>Indices</summary>

1. Quel `Content-Type` pour un `.m3u8` ? pour un `.ts` ?
2. Où vérifier l'accès : à chaque segment, ou une seule fois ? Quel coût, quels risques ?
3. Une regex dans le chemin de la route peut déjà refuser beaucoup de chemins inattendus.
</details>

---

## Partie 4 : le frontend

### - [ ] Étape 13 : dessiner avant de coder

**Objectif** : décider à quoi ressemble **ton** site avant d'écrire du code.

**Tu construis** : des maquettes simples (papier, Excalidraw ou Penpot) des pages : accueil et
recherche, lecture d'une vidéo, connexion, profil et « mes vidéos », envoi d'une vidéo.

**C'est fini quand**
- Chaque page a une maquette, version ordinateur et téléphone.
- Tu as listé, pour chaque page, les appels à l'API dont elle a besoin.
- Tu as repéré les « briques » communes (carte de vidéo, avatar, bouton j'aime...).

**Ressources**
- Penpot (open source) : https://penpot.app
- Excalidraw : https://excalidraw.com
- Les composants de Vuetify, pour savoir ce qui existe déjà : https://vuetifyjs.com/en/components/all/

<details><summary>Indices</summary>

1. Qu'affiche chaque page pendant le chargement ? quand la liste est vide ? en cas d'erreur ?
2. Une brique utilisée sur 3 pages mérite d'être un composant.
</details>

### - [ ] Étape 14 : premiers pas avec Vue

**Objectif** : afficher les données de ton API dans une page Vue.

**Tu construis** : un projet Vite + Vue 3 + TypeScript (`frontend/`) dont la page d'accueil
affiche la liste des vidéos de ton API, avec une barre de recherche.

**C'est fini quand**
- `npm run dev` affiche les vidéos venant du backend.
- La recherche filtre la liste (appel à l'API, pas filtrage dans le navigateur).
- Les appels passent par le **proxy de Vite** : pas d'erreur CORS, pas d'adresse du backend
  écrite dans le code des pages.
- La carte d'une vidéo est un composant à part, qui reçoit la vidéo en « prop ».
- On voit un état « chargement » et un message en cas d'erreur.

**Notions** : composant, `<script setup>`, `ref` et réactivité, `v-for`, `v-if`, props,
`fetch`, async/await, proxy de développement, CORS.

**Ressources**
- Tutoriel interactif Vue : https://vuejs.org/tutorial/
- Les bases de Vue : https://vuejs.org/guide/essentials/application.html
- Vite : https://vite.dev/guide/ et son proxy https://vite.dev/config/server-options.html#server-proxy
- `fetch` : https://developer.mozilla.org/fr/docs/Web/API/Fetch_API/Using_Fetch

<details><summary>Indices</summary>

1. Fais le tutoriel Vue en entier avant : une heure qui en fait gagner dix.
2. Où lancer le chargement des données quand la page apparaît ? Cherche `onMounted`... et
   `watch` pour recharger quand la recherche change.
3. Que se passe-t-il si la réponse d'une ancienne recherche arrive **après** celle de la
   nouvelle ?
</details>

### - [ ] Étape 15 : pages, navigation et lecteur

**Objectif** : une vraie application à plusieurs pages, avec une bibliothèque de composants.

**Tu construis** : Vue Router (accueil, page d'une vidéo, page d'une chaîne, page 404),
Vuetify et Font Awesome, et la page d'une vidéo avec un lecteur hls.js.

**C'est fini quand**
- Chaque page a sa propre adresse ; « retour » du navigateur fonctionne ; une recherche se
  partage par son URL (`/?q=ski`).
- La page d'une vidéo lit la vidéo avec hls.js et ses contrôles natifs.
- L'interface utilise des composants Vuetify et des icônes Font Awesome, et suit tes maquettes.
- Le site reste utilisable sur un écran de téléphone.

**Notions** : routage côté client, paramètres d'URL, bibliothèque de composants, thème, icônes,
cycle de vie (détruire le lecteur en quittant la page).

**Ressources**
- Vue Router : https://router.vuejs.org/guide/
- Installer Vuetify : https://vuetifyjs.com/en/getting-started/installation/
- Font Awesome avec Vue : https://docs.fontawesome.com/web/use-with/vue/
- hls.js : https://github.com/video-dev/hls.js et son API https://github.com/video-dev/hls.js/blob/master/docs/API.md

<details><summary>Indices</summary>

1. Vuetify sait utiliser Font Awesome comme jeu d'icônes : cherche « icon fonts » dans sa doc.
2. Passer d'une vidéo à une autre garde le même composant de page : qu'est-ce qui doit se
   recharger ?
3. Safari lit le HLS sans hls.js : comment le détecter ?
</details>

### - [ ] Étape 16 : se connecter depuis le frontend

**Objectif** : inscription, connexion, et pages réservées aux connectés.

**Tu construis** : les pages de connexion et d'inscription, un état « utilisateur connecté »
partagé par toute l'application, et un module unique qui fait les appels à l'API avec le jeton.

**C'est fini quand**
- Après connexion, l'en-tête affiche ton nom ; après déconnexion, il revient à « Connexion ».
- Une page réservée (ex. `/me`) redirige vers la connexion, puis revient à la page demandée.
- Aucune page n'écrit elle-même l'en-tête `Authorization` : un seul module s'en charge.
- Le jeton n'est pas dans `localStorage` (étape 7, indice 4).
- Un jeton expiré ne laisse pas l'application dans un état bizarre.

**Notions** : état partagé (`reactive` ou Pinia), garde de navigation, client HTTP central,
redirection sécurisée (`?next=`).

**Ressources**
- Gardes de navigation : https://router.vuejs.org/guide/advanced/navigation-guards.html
- État partagé dans Vue : https://vuejs.org/guide/scaling-up/state-management.html

<details><summary>Indices</summary>

1. `?next=https://site-pirate.com` : que fait ta redirection après connexion ?
2. Le jeton est en mémoire : que se passe-t-il quand tu recharges la page ? (C'est là que le
   bonus de l'étape 7 devient utile.)
3. Plusieurs requêtes reçoivent un 401 en même temps : combien de fois faut-il renouveler le jeton ?
</details>

### - [ ] Étape 17 : envoyer une vidéo depuis le site

**Objectif** : tout le parcours d'envoi, côté utilisateur.

**Tu construis** : un formulaire d'envoi avec barre de progression, et une page « Mes vidéos »
qui suit le traitement en direct.

**C'est fini quand**
- Une barre montre l'envoi du fichier (en %), puis la vidéo apparaît dans « Mes vidéos ».
- L'état du traitement (en attente, %, prête, échec) se met à jour sans recharger la page.
- Le suivi s'arrête quand plus aucune vidéo n'est en cours, et en quittant la page.
- Une vidéo en échec peut être relancée depuis la liste.

**Notions** : `FormData`, progression d'envoi, sondage régulier (polling), nettoyage des
minuteurs.

**Ressources**
- `FormData` : https://developer.mozilla.org/fr/docs/Web/API/FormData
- Progression d'un envoi : https://developer.mozilla.org/fr/docs/Web/API/XMLHttpRequest/upload

<details><summary>Indices</summary>

1. `fetch` ne donne pas la progression d'un **envoi** : quelle autre API du navigateur le fait ?
2. Un `setInterval` jamais arrêté continue après avoir quitté la page : où l'arrêter ?
3. Bonus : au lieu de demander toutes les 2 s, le serveur pourrait prévenir le navigateur.
   Cherche « Server-Sent Events ».
</details>

---

## Partie 5 : une vraie plateforme

Chaque étape touche le backend **et** le frontend.

### - [ ] Étape 18 : j'aime / je n'aime pas

**Tu construis** : réagir à une vidéo (j'aime ou je n'aime pas, changer d'avis, retirer), avec
les compteurs, et la liste « Vidéos que j'aime ».

**C'est fini quand**
- Un utilisateur a au plus une réaction par vidéo, garanti par la base.
- 100 likes envoyés en même temps donnent exactement 100 (teste-le avec un petit script).
- Le bouton montre ta réaction, et les compteurs sont à jour sans recharger la page.

**Notions** : contrainte d'unicité, mise à jour atomique (`UPDATE ... SET n = n + 1`),
concurrence, mise à jour « optimiste » de l'interface.

<details><summary>Indices</summary>

1. Lire le compteur, ajouter 1 en Java, puis enregistrer : que se passe-t-il avec deux clics
   simultanés ?
2. Faut-il stocker le compteur, ou le recalculer (`COUNT`) à chaque affichage ? Avantages de chaque.
</details>

### - [ ] Étape 19 : abonnements et fil

**Tu construis** : s'abonner à une chaîne, nombre d'abonnés, et un fil « Abonnements » avec
les dernières vidéos des chaînes suivies.

**C'est fini quand**
- On ne peut pas s'abonner à soi-même ni deux fois à la même chaîne.
- Le fil est paginé et ne contient que des vidéos publiques des chaînes suivies.
- Le bouton S'abonner fonctionne aussi sans compte (il mène à la connexion, puis revient).

**Notions** : relation plusieurs-à-plusieurs, sous-requêtes, index composés.

<details><summary>Indices</summary>

1. Table de liaison simple, ou entité à part entière ? Que faire si un jour tu veux la date
   d'abonnement ?
</details>

### - [ ] Étape 20 : commentaires et réponses

**Tu construis** : commenter sous une vidéo, répondre à un commentaire (un seul niveau, comme
YouTube), modifier et supprimer, liker un commentaire, une marque à côté du nom de l'auteur de
la vidéo.

**C'est fini quand**
- L'auteur modifie son commentaire ; l'auteur **ou** le propriétaire de la vidéo peut le supprimer.
- Supprimer un commentaire supprime ses réponses.
- Les commentaires sont paginés (« Afficher plus ») ; les réponses se chargent à la demande.
- Le texte est affiché tel quel : `<script>` dans un commentaire ne s'exécute pas.
- On ne peut pas commenter une vidéo qu'on n'a pas le droit de voir.

**Notions** : relation d'un objet vers lui-même (`parent`), suppression en cascade, XSS, modération.

**Ressources**
- XSS (OWASP) : https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html

<details><summary>Indices</summary>

1. Vue protège déjà du XSS avec `{{ }}` : quelle directive ne protège pas, et pourquoi l'éviter ?
2. Répondre à une réponse : à quel commentaire la rattacher pour rester sur un seul niveau ?
</details>

### - [ ] Étape 21 : historique et reprise de lecture

**Tu construis** : l'historique des vidéos regardées, et la reprise là où on s'était arrêté.

**C'est fini quand**
- Une vue est comptée au début de la lecture, pas à chaque rechargement de la page.
- La position est enregistrée régulièrement et à la pause, sans envoyer une requête par seconde.
- En revenant sur la vidéo, la lecture reprend à la bonne position.
- L'historique peut être effacé.

**Notions** : événements du lecteur (`play`, `timeupdate`, `pause`), limiter le nombre de
requêtes, « upsert » en SQL.

<details><summary>Indices</summary>

1. `INSERT ... ON CONFLICT ... DO UPDATE` en PostgreSQL : à quoi ça sert ici ?
</details>

### - [ ] Étape 22 : miniatures au choix et photos de profil

**Tu construis** : plusieurs images proposées par le worker pour chaque vidéo, le choix de la
miniature, l'envoi d'une miniature personnalisée, et une photo de profil.

**C'est fini quand**
- Le propriétaire choisit parmi 3 ou 4 images extraites, ou envoie la sienne (recadrée en 16:9).
- La photo de profil est recadrée en carré et apparaît partout où l'utilisateur est cité.
- Une image envoyée est **redessinée** côté serveur (pas stockée telle quelle) : ses métadonnées
  (position GPS...) disparaissent.
- Une image annonçant 50 000 × 50 000 pixels est refusée sans faire planter le serveur.
- Changer de miniature s'affiche aussitôt, malgré le cache du navigateur.

**Notions** : traitement d'image (`ImageIO`), métadonnées EXIF, « bombe de décompression »,
ETag et cache, adresse versionnée (`?v=`).

**Ressources**
- `ImageIO` : https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/imageio/ImageIO.html
- Envoi de fichiers (OWASP), à relire : https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html

<details><summary>Indices</summary>

1. Peut-on connaître les dimensions d'une image **avant** de la décoder entièrement ? Cherche
   `ImageReader`.
2. Une balise `<img>` n'envoie pas le jeton de connexion : comment afficher la miniature d'une
   vidéo privée à son propriétaire ?
</details>

### - [ ] Étape 23 : brouillons et publication

**Tu construis** : une vidéo envoyée reste un brouillon (visible par son propriétaire seul) ; il
complète titre, description, visibilité et miniature pendant le traitement, puis clique « Publier ».

**C'est fini quand**
- Un brouillon est invisible des autres partout : page, recherche, chaîne, fil, miniature, HLS.
- « Publier » n'est possible qu'une fois la vidéo traitée.
- Les vidéos sont triées par date de **publication**, pas d'envoi.
- Le formulaire garde ce qui est saisi pendant que l'état du traitement se met à jour.

**Notions** : cycle de vie d'un objet (états et transitions), règle de visibilité centralisée,
migration qui modifie des données existantes.

<details><summary>Indices</summary>

1. Combien d'endroits du code décident « qui peut voir cette vidéo » ? Et s'il n'y en avait qu'un ?
2. Les vidéos déjà en ligne avant cette étape : brouillons ou publiées ? Ta migration doit en décider.
</details>

### - [ ] Étape 24 (bonus) : un lecteur personnalisé

**Tu construis** : tes propres contrôles par-dessus la balise `<video>` : lecture, ±15 s,
volume, barre de progression avec aperçu du temps, choix de la qualité et de la vitesse,
plein écran, raccourcis clavier.

**C'est fini quand**
- Tous les contrôles fonctionnent à la souris, au clavier et au doigt (double toucher = ±15 s).
- Les contrôles se masquent pendant la lecture quand la souris ne bouge plus.
- Le lecteur démarre dans la meilleure qualité adaptée à sa taille et à la connexion, puis s'adapte.

**Ressources**
- `HTMLMediaElement` : https://developer.mozilla.org/fr/docs/Web/API/HTMLMediaElement
- Plein écran : https://developer.mozilla.org/fr/docs/Web/API/Fullscreen_API
- Options de hls.js (`startLevel`, `capLevelToPlayerSize`) : https://github.com/video-dev/hls.js/blob/master/docs/API.md

---

## Partie 6 : mise en production (optionnel)

### - [ ] Étape 25 : tout dans Docker

**Tu construis** : des Dockerfiles pour le backend, le worker et le frontend (servi par Nginx),
pour lancer toute la plateforme avec un seul `docker compose up`, en HTTPS.

**C'est fini quand**
- Une machine avec seulement Docker lance tout le site.
- Les images ne contiennent ni secret, ni outils de compilation (« multi-stage build »).
- Aucune erreur ne renvoie de détail technique au client (étape 5), les logs sont lisibles.

**Ressources**
- Construire une image : https://docs.docker.com/build/
- Spring Boot et Docker : https://docs.spring.io/spring-boot/reference/packaging/container-images/index.html

### - [ ] Étape 26 (bonus) : le direct

**Tu construis** : diffuser en direct depuis OBS (RTMP) et regarder le direct dans le site
(HLS), avec MediaMTX.

**Ressources**
- MediaMTX : https://github.com/bluenviron/mediamtx
