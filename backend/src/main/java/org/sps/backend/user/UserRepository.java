package org.sps.backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/*
 * Abonnements : la table user_subscriptions (follower_id suit following_id) est gérée par
 * le @ManyToMany "subscriptions" de User. Plutôt que de charger tout le Set en mémoire,
 * on l'interroge avec des requêtes paginées.
 *
 * @Query : quand le nom de méthode ne suffit plus, on écrit la requête en JPQL (le SQL de JPA :
 * on y parle d'entités et de champs Java, pas de tables). "select new ...UserSummary(...)"
 * construit directement le record, sans charger d'entité.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    // --- Abonnements (chaînes que userId suit) ---

    @Query(value = """
            select new org.sps.backend.user.UserSummary(s.id, s.name, s.surname, s.avatarUpdatedAt)
            from User u join u.subscriptions s
            where u.id = :userId
            order by s.name, s.surname""",
            countQuery = "select count(s) from User u join u.subscriptions s where u.id = :userId")
    Page<UserSummary> findSubscriptions(@Param("userId") UUID userId, Pageable pageable);

    @Query("select count(s) from User u join u.subscriptions s where u.id = :userId")
    long countSubscriptions(@Param("userId") UUID userId);

    // --- Abonnés (utilisateurs qui suivent userId) ---

    @Query(value = """
            select new org.sps.backend.user.UserSummary(f.id, f.name, f.surname, f.avatarUpdatedAt)
            from User f join f.subscriptions s
            where s.id = :userId
            order by f.name, f.surname""",
            countQuery = "select count(f) from User f join f.subscriptions s where s.id = :userId")
    Page<UserSummary> findSubscribers(@Param("userId") UUID userId, Pageable pageable);

    @Query("select count(f) from User f join f.subscriptions s where s.id = :userId")
    long countSubscribers(@Param("userId") UUID userId);

    // --- S'abonner / se désabonner ---
    /*
     * En SQL natif (nativeQuery = true) : une seule requête, sans charger les deux utilisateurs.
     * ON CONFLICT DO NOTHING : s'abonner deux fois n'est pas une erreur, ça ne change rien.
     */

    @Query(value = "select exists(select 1 from user_subscriptions where follower_id = :followerId and following_id = :followingId)",
            nativeQuery = true)
    boolean isSubscribed(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    @Transactional
    @Modifying
    @Query(value = "insert into user_subscriptions (follower_id, following_id) values (:followerId, :followingId) on conflict do nothing",
            nativeQuery = true)
    int subscribe(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    @Transactional
    @Modifying
    @Query(value = "delete from user_subscriptions where follower_id = :followerId and following_id = :followingId",
            nativeQuery = true)
    int unsubscribe(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);
}
