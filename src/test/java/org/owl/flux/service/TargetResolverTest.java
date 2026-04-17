package org.owl.flux.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.owl.flux.data.model.PlayerSnapshot;
import org.owl.flux.data.repository.PlayerRepository;
import org.owl.flux.service.model.TargetProfile;

class TargetResolverTest {

    @Test
    void resolvePunishmentTargetFallsBackToUsernameForNeverSeenPlayer() {
        ProxyServer server = mock(ProxyServer.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(server.getPlayer("NeverSeen")).thenReturn(Optional.empty());
        when(playerRepository.findByUsername("NeverSeen")).thenReturn(Optional.empty());

        TargetResolver resolver = new TargetResolver(server, playerRepository);

        Optional<TargetProfile> fallback = resolver.resolvePunishmentTarget("NeverSeen");
        assertTrue(fallback.isPresent());
        assertNull(fallback.get().uuid());
        assertEquals("NeverSeen", fallback.get().username());
        assertNull(fallback.get().ip());
        assertTrue(fallback.get().optionalOnlinePlayer().isEmpty());
        assertTrue(resolver.resolvePlayer("NeverSeen").isEmpty());
    }

    @Test
    void resolvePunishmentTargetRejectsIpLiteralsWhenNoPlayerMatches() {
        ProxyServer server = mock(ProxyServer.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(server.getPlayer("203.0.113.10")).thenReturn(Optional.empty());
        when(playerRepository.findByUsername("203.0.113.10")).thenReturn(Optional.empty());

        TargetResolver resolver = new TargetResolver(server, playerRepository);

        assertTrue(resolver.resolvePunishmentTarget("203.0.113.10").isEmpty());
    }

    @Test
    void resolvePunishmentTargetReturnsKnownProfileWhenAvailable() {
        ProxyServer server = mock(ProxyServer.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(server.getPlayer("KnownUser")).thenReturn(Optional.empty());
        when(playerRepository.findByUsername("KnownUser"))
                .thenReturn(Optional.of(new PlayerSnapshot("uuid-1", "KnownUser", "203.0.113.10")));

        TargetResolver resolver = new TargetResolver(server, playerRepository);

        Optional<TargetProfile> resolved = resolver.resolvePunishmentTarget("KnownUser");
        assertTrue(resolved.isPresent());
        assertEquals("uuid-1", resolved.get().uuid());
        assertEquals("KnownUser", resolved.get().username());
        assertEquals("203.0.113.10", resolved.get().ip());
    }
}
