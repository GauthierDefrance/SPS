package org.sps.backend.common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

/**
 * Une page de résultats renvoyée par l'API. On n'expose pas directement Page de Spring Data :
 * son JSON contient beaucoup de détails internes et peut changer d'une version à l'autre.
 */
public record PageResponse<T>(
        List<T> items,
        int page,           // numéro de la page, à partir de 0
        int size,           // nombre d'éléments demandés par page
        long totalItems,
        int totalPages
) {
    public static final int MAX_SIZE = 100;

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** Page demandée par le client, bornée : page >= 0 et 1 <= size <= MAX_SIZE. */
    public static PageRequest request(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_SIZE), sort);
    }

    public static PageRequest request(int page, int size) {
        return request(page, size, Sort.unsorted());
    }
}
