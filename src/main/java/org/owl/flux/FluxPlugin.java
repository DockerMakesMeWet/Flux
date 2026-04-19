package org.owl.flux;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import org.slf4j.Logger;

@Plugin(
        id = "flux",
        name = "Flux",
    version = "1.5.0",
        description = "A modern, configurable moderation system for Velocity.",
        authors = {"Flux Team"}
)
public final class FluxPlugin {
    private final FluxBootstrap bootstrap;

    @Inject
    public FluxPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.bootstrap = new FluxBootstrap(this, server, logger, dataDirectory);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        bootstrap.start();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        bootstrap.stop();
    }
}
