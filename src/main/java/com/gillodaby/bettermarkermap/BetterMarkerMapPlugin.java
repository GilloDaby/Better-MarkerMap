package com.gillodaby.bettermarkermap;

import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BetterMarkerMapPlugin extends JavaPlugin {

    private MarkerPermissionPacketFilter markerFilter;
    private PacketFilter inboundFilterHandle;
    private MarkerImageRules markerImageRules;

    public BetterMarkerMapPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        // No setup required.
    }

    @Override
    public void start() {
        Path imageRulesPath = resolveImageRulesPath();
        this.markerImageRules = MarkerImageRules.load(imageRulesPath);

        this.markerFilter = new MarkerPermissionPacketFilter(this.markerImageRules);
        this.inboundFilterHandle = PacketAdapters.registerInbound(this.markerFilter);

        CommandManager commandManager = CommandManager.get();
        if (commandManager != null) {
            commandManager.register(new MarkerMapPermissionsCommand());
            commandManager.register(new MarkerSelectorCommand(this.markerFilter));
        }

        System.out.println("[BetterMarkerMap] Loaded. Use /markermap to view permission nodes.");
        System.out.println("[BetterMarkerMap] Use /marker to open marker icon selector UI.");
        System.out.println("[BetterMarkerMap] Marker image rules: " + imageRulesPath);
    }

    @Override
    protected void shutdown() {
        if (this.inboundFilterHandle != null) {
            try {
                PacketAdapters.deregisterInbound(this.inboundFilterHandle);
            } catch (Exception ignored) {
                // Ignore on shutdown.
            }
            this.inboundFilterHandle = null;
        }
    }

    private Path resolveImageRulesPath() {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        Path localConfig = root.resolve(MarkerImageRules.CONFIG_FILE_NAME);
        if (Files.exists(localConfig)) {
            return localConfig;
        }

        return root
                .resolve("mods")
                .resolve("Better MarkerMap")
                .resolve(MarkerImageRules.CONFIG_FILE_NAME);
    }
}
