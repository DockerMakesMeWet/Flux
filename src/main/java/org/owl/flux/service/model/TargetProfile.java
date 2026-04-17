package org.owl.flux.service.model;

import com.velocitypowered.api.proxy.Player;
import java.util.Optional;

public record TargetProfile(
        String uuid,
        String username,
        String ip,
        Player onlinePlayer
) {
    public Optional<Player> optionalOnlinePlayer() {
        return Optional.ofNullable(onlinePlayer);
    }
}
