package org.owl.flux.service;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

public final class PermissionService {
    private final MastersService mastersService;

    public PermissionService(MastersService mastersService) {
        this.mastersService = mastersService;
    }

    public boolean has(CommandSource source, String permission) {
        if (source instanceof Player player && permission.startsWith("flux.") && mastersService.isMaster(player.getUsername())) {
            return true;
        }
        return source.hasPermission(permission);
    }

    public boolean canSeeSilentNotifications(Player player) {
        return mastersService.isMaster(player.getUsername()) || player.hasPermission("flux.notify");
    }
}
