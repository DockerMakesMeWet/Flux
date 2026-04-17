package org.owl.flux.service;

import java.security.SecureRandom;
import org.owl.flux.data.repository.PunishmentRepository;

public final class ActionIdService {
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int MAX_RETRIES = 128;

    private final SecureRandom random = new SecureRandom();
    private final PunishmentRepository punishmentRepository;

    public ActionIdService(PunishmentRepository punishmentRepository) {
        this.punishmentRepository = punishmentRepository;
    }

    public String nextUniqueId() {
        for (int i = 0; i < MAX_RETRIES; i++) {
            String candidate = generate();
            if (!punishmentRepository.existsId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique Flux action ID.");
    }

    private String generate() {
        StringBuilder builder = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            builder.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return builder.toString();
    }
}
