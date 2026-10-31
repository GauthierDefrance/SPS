package org.sps.backend.video;

import java.security.SecureRandom;

/**
 * Identifiants publics de 11 caractères, comme sur YouTube : 11 × 6 bits = 66 bits de hasard.
 * Ce n'est pas un UUID (128 bits, il faudrait 22 caractères) : les collisions sont très rares
 * mais possibles, d'où la contrainte UNIQUE sur la colonne et la vérification avant insertion.
 */
public final class VideoIdGenerator {

    public static final int LENGTH = 11;

    // 64 caractères sûrs dans une URL : chaque caractère porte exactement 6 bits
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private VideoIdGenerator() { }

    public static String generateId() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString(); // Ex de retour : "X8z_K9pLm2Q"
    }
}
