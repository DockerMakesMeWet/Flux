package org.owl.flux.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Optional;
import org.owl.flux.data.model.PlayerSnapshot;
import org.owl.flux.data.repository.PlayerRepository;
import org.owl.flux.service.model.TargetProfile;
import org.owl.flux.util.NetworkUtil;

public final class TargetResolver {
    private static final int MAX_USERNAME_LENGTH = 32;

    private final ProxyServer server;
    private final PlayerRepository playerRepository;

    public TargetResolver(ProxyServer server, PlayerRepository playerRepository) {
        this.server = server;
        this.playerRepository = playerRepository;
    }

    public Optional<TargetProfile> resolvePlayer(String input) {
        Optional<Player> online = server.getPlayer(input);
        if (online.isPresent()) {
            Player player = online.get();
            return Optional.of(new TargetProfile(
                    player.getUniqueId().toString(),
                    player.getUsername(),
                    NetworkUtil.extractIp(player),
                    player
            ));
        }

        Optional<PlayerSnapshot> known = playerRepository.findByUsername(input);
        if (known.isEmpty()) {
            return Optional.empty();
        }

        PlayerSnapshot snapshot = known.get();
        return Optional.of(new TargetProfile(
                snapshot.uuid(),
                snapshot.username(),
                snapshot.lastIp(),
                null
        ));
    }

    public Optional<TargetProfile> resolvePunishmentTarget(String input) {
        String normalizedInput = input == null ? null : input.trim();
        if (normalizedInput == null || normalizedInput.isEmpty()) {
            return Optional.empty();
        }

        Optional<TargetProfile> knownTarget = resolvePlayer(normalizedInput);
        if (knownTarget.isPresent()) {
            return knownTarget;
        }

        String fallbackUsername = sanitizeFallbackUsername(normalizedInput);
        if (fallbackUsername == null) {
            return Optional.empty();
        }
        return Optional.of(new TargetProfile(null, fallbackUsername, null, null));
    }

    private static String sanitizeFallbackUsername(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_USERNAME_LENGTH || NetworkUtil.isIpLiteral(trimmed)) {
            return null;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            if (!Character.isLetterOrDigit(character) && character != '_') {
                return null;
            }
        }
        return trimmed;
    }
}
