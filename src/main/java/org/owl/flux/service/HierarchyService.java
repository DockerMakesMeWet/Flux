package org.owl.flux.service;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.owl.flux.service.model.TargetProfile;

public final class HierarchyService {
    private final MastersService mastersService;
    private final PermissionService permissionService;
    private final LuckPerms luckPerms;

    public HierarchyService(MastersService mastersService, PermissionService permissionService) {
        this.mastersService = mastersService;
        this.permissionService = permissionService;
        this.luckPerms = lookupLuckPerms();
    }

    public boolean canTarget(CommandSource source, TargetProfile target) {
        String targetName = target.username() == null ? "" : target.username();
        if (mastersService.isMaster(targetName)) {
            return false;
        }

        if (!(source instanceof Player actor)) {
            return true;
        }

        if (mastersService.isMaster(actor.getUsername())) {
            return true;
        }

        if (permissionService.has(actor, "flux.hierarchy.bypass")) {
            return true;
        }

        int actorWeight = getWeight(actor.getUniqueId().toString());
        int targetWeight = target.uuid() == null ? 0 : getWeight(target.uuid());
        return actorWeight > targetWeight;
    }

    public int getWeight(String uuid) {
        if (luckPerms == null || uuid == null) {
            return 0;
        }

        User user = luckPerms.getUserManager().getUser(java.util.UUID.fromString(uuid));
        if (user == null) {
            return 0;
        }
        CachedMetaData meta = user.getCachedData().getMetaData();
        String weightValue = meta.getMetaValue("weight");
        if (weightValue == null || weightValue.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(weightValue.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static LuckPerms lookupLuckPerms() {
        try {
            return LuckPermsProvider.get();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }
}
