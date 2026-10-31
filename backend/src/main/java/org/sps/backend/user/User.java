package org.sps.backend.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/*
 * Pas de @Data sur une entité JPA : il génère equals/hashCode/toString sur TOUS les champs.
 * Avec "subscriptions" (des User qui pointent vers d'autres User), ces méthodes tourneraient
 * en boucle (StackOverflowError) et chargeraient les relations LAZY hors transaction.
 * On garde donc l'égalité par défaut de Java (même instance), ce que Hibernate gère très bien
 * au sein d'une session.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;            // Hash BCrypt, jamais le mot de passe en clair

    @Column(length = 100)
    private String name;

    @Column(length = 100)
    private String surname;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;        // envoi de la photo de profil (null : pas de photo)

    // Liste des utilisateurs auxquels cet utilisateur est abonné (Following).
    // LAZY par défaut pour un @ManyToMany : chargée seulement quand on y accède.
    @ManyToMany
    @JoinTable(
            name = "user_subscriptions",
            joinColumns = @JoinColumn(name = "follower_id"),
            inverseJoinColumns = @JoinColumn(name = "following_id")
    )
    private Set<User> subscriptions = new HashSet<>();

}
