package org.sps.backend.video;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * Spring Data écrit le SQL à partir du nom des méthodes : find + By + champ(s) + mot-clé.
 *   findByPolicyAndStatus        -> WHERE policy = ? AND status = ?
 *   TitleContainingIgnoreCase    -> WHERE lower(title) LIKE lower('%' || ? || '%')
 *   Owner_Id                     -> le champ id de la relation owner (colonne owner_id, sans jointure)
 * Le texte entre "find" et "By" est libre (findSummaryBy, findWithOwnerBy...) : il sert à nommer
 * des variantes ; c'est le type de retour qui décide des colonnes lues.
 *
 * Pageable / Page : on ne charge jamais une liste entière, mais une page (ex. 20 vidéos)
 * triée, avec le nombre total d'éléments. Ex. : PageRequest.of(0, 20, Sort.by("createdAt").descending())
 */
public interface VideoRepository extends JpaRepository<Video, UUID>, JpaSpecificationExecutor<Video> {

    // Recherche avec filtres (voir VideoSearch). Redéclarée pour charger aussi le propriétaire
    // (JOIN) : la carte affiche le nom de la chaîne, sans une requête de plus par vidéo.
    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Video> findAll(Specification<Video> spec, Pageable pageable);

    // --- Une vidéo ---

    Optional<Video> findByPublicId(String publicId);

    // Même chose, mais le propriétaire est chargé dans la même requête (JOIN) :
    // utile quand on a besoin de son nom ou pour vérifier les droits, sans 2e requête
    @EntityGraph(attributePaths = "owner")
    Optional<Video> findWithOwnerByPublicId(String publicId);

    Optional<VideoSummary> findSummaryByPublicId(String publicId);

    boolean existsByPublicId(String publicId);

    // Vérification des droits : cette vidéo appartient-elle à cet utilisateur ?
    boolean existsByPublicIdAndOwner_Id(String publicId, UUID ownerId);

    // --- Listes (paginées) ---

    // Catalogue : vidéos publiques et prêtes à être lues (policy = PUBLIC, status = READY)
    Page<VideoSummary> findByPolicyAndStatus(VideoPolicy policy, TranscodeStatus status, Pageable pageable);

    // Recherche simple dans le titre, parmi les vidéos visibles du catalogue
    Page<VideoSummary> findByPolicyAndStatusAndTitleContainingIgnoreCase(
            VideoPolicy policy, TranscodeStatus status, String title, Pageable pageable);

    // Page d'une chaîne, vue par les visiteurs : seulement ses vidéos publiées, publiques et prêtes
    Page<VideoSummary> findByOwner_IdAndPolicyAndStatusAndPublishedAtIsNotNull(
            UUID ownerId, VideoPolicy policy, TranscodeStatus status, Pageable pageable);

    // "Mes vidéos", vue par le propriétaire : toutes, quel que soit leur statut ou leur visibilité
    Page<VideoSummary> findByOwner_Id(UUID ownerId, Pageable pageable);

    // "Mes vidéos" avec leur état de traitement (entités complètes, pas une projection)
    Page<Video> findAllByOwner_Id(UUID ownerId, Pageable pageable);

    long countByOwner_Id(UUID ownerId);

    long countByOwner_IdAndPolicyAndStatusAndPublishedAtIsNotNull(UUID ownerId, VideoPolicy policy, TranscodeStatus status);

    // --- Transcodage ---

    // Vidéos à (re)traiter, par ex. au démarrage : status = PENDING ou PROCESSING
    List<Video> findByStatusIn(List<TranscodeStatus> statuses);

    /*
     * Progression envoyée par un worker. Écrite en une seule requête conditionnelle : une vidéo déjà
     * READY ou FAILED n'est jamais ramenée en PROCESSING, même si un vieux message de progression
     * arrive après le message final (possible avec plusieurs backends qui lisent la file en parallèle).
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Video v
            SET v.status = org.sps.backend.video.TranscodeStatus.PROCESSING,
                v.processingProgress = :progress, v.processingStep = :step,
                v.processedBy = :worker, v.processingUpdatedAt = :at
            WHERE v.publicId = :publicId
              AND v.status IN (org.sps.backend.video.TranscodeStatus.PENDING, org.sps.backend.video.TranscodeStatus.PROCESSING)""")
    int updateProgress(@Param("publicId") String publicId, @Param("progress") int progress,
                       @Param("step") String step, @Param("worker") String worker, @Param("at") Instant at);

    // Verrouille la ligne pendant la transaction (SELECT ... FOR UPDATE) : deux messages
    // finaux pour la même vidéo sont traités l'un après l'autre, jamais en même temps
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Video> findForUpdateByPublicId(String publicId);

    // --- Compteurs ---
    /*
     * Mise à jour directement en SQL : "views = views + 1" est atomique. Lire la vidéo,
     * faire setViews(getViews() + 1) puis save() perdrait des vues si deux personnes
     * regardent en même temps (les deux lisent 10, les deux écrivent 11).
     * @Modifying : la requête modifie des données. @Transactional : obligatoire pour écrire.
     * Le nombre renvoyé est le nombre de lignes modifiées (0 si la vidéo n'existe pas).
     *
     * Piège : un UPDATE en JPQL modifie la base, pas les entités déjà chargées en mémoire dans la
     * transaction (le "contexte de persistance" d'Hibernate). Une relecture renverrait l'ancienne valeur.
     *   flushAutomatically : enregistre d'abord les changements en attente (ex. une réaction créée)
     *   clearAutomatically : vide ensuite la mémoire, la prochaine lecture repart de la base
     */

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Video v SET v.views = v.views + 1 WHERE v.id = :id")
    int incrementViews(@Param("id") UUID id);

    // likes / dislikes = +1, -1 ou 0 : ajouter un like, le retirer, passer d'un like à un dislike...
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Video v SET v.likes = v.likes + :likes, v.dislikes = v.dislikes + :dislikes WHERE v.id = :id")
    int addReactions(@Param("id") UUID id, @Param("likes") long likes, @Param("dislikes") long dislikes);
}
