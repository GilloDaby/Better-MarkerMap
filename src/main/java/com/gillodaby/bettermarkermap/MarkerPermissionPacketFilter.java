package com.gillodaby.bettermarkermap;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.player.RemoveMapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.CreateUserMarker;
import com.hypixel.hytale.protocol.packets.worldmap.TeleportToWorldMapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerDeathPositionData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.worldstore.WorldMarkersResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class MarkerPermissionPacketFilter implements PlayerPacketFilter {

    private static final String PREFIX = "[BetterMarkerMap] ";
    private static final String MAP_MARKERS_RESOURCE_PREFIX = "Common/UI/WorldMap/MapMarkers/";
    private final MarkerImageRules markerImageRules;
    private final ConcurrentHashMap<UUID, String> selectedMarkerImageByPlayer;
    private volatile List<String> availableMarkerImages;

    MarkerPermissionPacketFilter(MarkerImageRules markerImageRules) {
        this.markerImageRules = markerImageRules == null
                ? MarkerImageRules.defaultRules()
                : markerImageRules;
        this.selectedMarkerImageByPlayer = new ConcurrentHashMap<>();
        this.availableMarkerImages = List.of();
        reloadAvailableMarkerImages();
    }

    @Override
    public boolean test(PlayerRef playerRef, Packet packet) {
        if (playerRef == null || packet == null) {
            return false;
        }

        if (packet instanceof CreateUserMarker createPacket) {
            return onCreateMarker(playerRef, createPacket);
        }
        if (packet instanceof RemoveMapMarker removePacket) {
            return onRemoveMarker(playerRef, removePacket);
        }
        if (packet instanceof TeleportToWorldMapMarker teleportPacket) {
            return onTeleportToMarker(playerRef, teleportPacket);
        }

        return false;
    }

    private boolean onCreateMarker(PlayerRef playerRef, CreateUserMarker packet) {
        UUID playerUuid = playerRef.getUuid();
        boolean shared = packet.shared;
        String createPermission = shared
                ? BetterMarkerMapPermissions.PERM_CREATE_SHARED
                : BetterMarkerMapPermissions.PERM_CREATE_PERSONAL;

        boolean bypassAll = hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_BYPASS_ALL, false);
        boolean isAdmin = bypassAll
                || hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_ADMIN, false)
                || hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_REMOVE_ANY, false);

        if (!isAdmin && !hasPermission(playerUuid, createPermission, false)) {
            deny(playerRef, "You do not have permission to create "
                    + (shared ? "shared" : "personal")
                    + " markers (" + createPermission + ").");
            return true;
        }

        World world = resolveWorld(playerRef);
        if (world == null) {
            return false;
        }

        CreateUserMarker packetCopy = new CreateUserMarker(packet);
        world.execute(() -> handleCreateOnWorldThread(world, playerRef, playerUuid, packetCopy, isAdmin));
        // We always handle create ourselves so vanilla validation never runs async-side.
        return true;
    }

    private boolean onRemoveMarker(PlayerRef playerRef, RemoveMapMarker packet) {
        UUID playerUuid = playerRef.getUuid();

        boolean bypassAll = hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_BYPASS_ALL, false);
        boolean isAdmin = bypassAll
                || hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_ADMIN, false)
                || hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_REMOVE_ANY, false);
        boolean canRemoveOwn = isAdmin || hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_REMOVE_OWN, false);

        if (!canRemoveOwn) {
            deny(playerRef, "You do not have permission to remove markers ("
                    + BetterMarkerMapPermissions.PERM_REMOVE_OWN + ").");
            return true;
        }

        if (packet.markerId == null || packet.markerId.isBlank()) {
            deny(playerRef, "Invalid marker id.");
            return true;
        }

        World world = resolveWorld(playerRef);
        if (world == null) {
            return false;
        }

        String markerId = packet.markerId;
        world.execute(() -> handleRemoveOnWorldThread(world, playerRef, playerUuid, markerId, isAdmin));
        return true;
    }

    private boolean onTeleportToMarker(PlayerRef playerRef, TeleportToWorldMapMarker packet) {
        UUID playerUuid = playerRef.getUuid();
        if (hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_BYPASS_ALL, false)) {
            return false;
        }

        if (hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_ADMIN, false)) {
            return false;
        }

        if (hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_TELEPORT_MARKER, false)) {
            return false;
        }

        deny(playerRef, "You do not have permission to teleport to map markers ("
                + BetterMarkerMapPermissions.PERM_TELEPORT_MARKER + ").");
        return true;
    }

    private void handleCreateOnWorldThread(World world,
                                           PlayerRef playerRef,
                                           UUID playerUuid,
                                           CreateUserMarker packet,
                                           boolean isAdmin) {
        Player player = resolvePlayerOnWorldThread(playerRef);
        if (player == null) {
            return;
        }

        String selectedImage = playerUuid == null ? null : this.selectedMarkerImageByPlayer.get(playerUuid);
        if (selectedImage != null && !selectedImage.isBlank()) {
            packet.markerImage = selectedImage;
        }

        MarkerImageRules.ImageDecision imageDecision = this.markerImageRules.resolveForCreate(
                isAdmin,
                packet.markerImage,
                permission -> hasPermission(playerUuid, permission, false)
        );
        if (!imageDecision.allowed()) {
            deny(playerRef, imageDecision.deniedReason());
            return;
        }
        packet.markerImage = imageDecision.image();

        int defaultLimit = resolveDefaultLimit(world, packet.shared);
        int effectiveLimit = isAdmin
                ? Integer.MAX_VALUE
                : resolveEffectiveLimit(playerUuid, packet.shared, defaultLimit);

        int currentCount = packet.shared
                ? countSharedMarkers(world, playerUuid)
                : countPersonalMarkers(player, world, playerUuid);

        if (effectiveLimit != Integer.MAX_VALUE && currentCount >= effectiveLimit) {
            deny(playerRef, "Marker limit reached: " + currentCount + "/" + effectiveLimit
                    + " for " + (packet.shared ? "shared" : "personal") + " markers.");
            return;
        }

        if (!forceCreateMarkerNow(world, player, playerRef, packet)) {
            deny(playerRef, "Failed to create marker via Better MarkerMap bypass.");
        }
    }

    private void handleRemoveOnWorldThread(World world,
                                           PlayerRef playerRef,
                                           UUID playerUuid,
                                           String markerId,
                                           boolean isAdmin) {
        Player player = resolvePlayerOnWorldThread(playerRef);
        if (player == null) {
            return;
        }

        PlayerWorldData worldData = getPlayerWorldData(player, world);
        boolean nativeDeathMarker = isNativeDeathMarker(worldData, markerId);
        UUID owner = resolveOwner(world, player, playerUuid, markerId);
        if (!isAdmin && !nativeDeathMarker && owner != null && !owner.equals(playerUuid)) {
            deny(playerRef, "You may only remove your own markers."
                    + " Grant " + BetterMarkerMapPermissions.PERM_ADMIN + " to remove others.");
            return;
        }

        // If owner cannot be resolved, keep removal admin-only to avoid bypassing
        // ownership checks by unknown marker state.
        if (!isAdmin && !nativeDeathMarker && owner == null) {
            deny(playerRef, "Unable to verify marker owner."
                    + " Admin permission required: " + BetterMarkerMapPermissions.PERM_ADMIN + ".");
            return;
        }

        if (!forceRemoveMarkerNow(world, player, markerId)) {
            deny(playerRef, "Marker not found: " + markerId);
        }
    }

    private int resolveEffectiveLimit(UUID playerUuid, boolean shared, int defaultLimit) {
        if (hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_LIMIT_UNLIMITED_ALL, false)) {
            return Integer.MAX_VALUE;
        }

        if (shared && hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_LIMIT_SHARED_UNLIMITED, false)) {
            return Integer.MAX_VALUE;
        }

        if (!shared && hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_LIMIT_PERSONAL_UNLIMITED, false)) {
            return Integer.MAX_VALUE;
        }

        String prefix = shared
                ? BetterMarkerMapPermissions.PERM_LIMIT_SHARED_PREFIX
                : BetterMarkerMapPermissions.PERM_LIMIT_PERSONAL_PREFIX;

        int chosenTier = -1;
        for (int tier : BetterMarkerMapPermissions.SUPPORTED_LIMIT_TIERS) {
            if (hasPermission(playerUuid, prefix + tier, false) && tier > chosenTier) {
                chosenTier = tier;
            }
        }

        return chosenTier > 0 ? chosenTier : defaultLimit;
    }

    private int resolveDefaultLimit(World world, boolean shared) {
        try {
            if (shared) {
                return world.getGameplayConfig()
                        .getWorldMapConfig()
                        .getUserMapMarkerConfig()
                        .getMaxSharedMarkersPerPlayer();
            }
            return world.getGameplayConfig()
                    .getWorldMapConfig()
                    .getUserMapMarkerConfig()
                    .getMaxPersonalMarkersPerPlayer();
        } catch (Exception ignored) {
            return 12;
        }
    }

    private int countSharedMarkers(World world, UUID playerUuid) {
        WorldMarkersResource resource = getWorldMarkersResource(world);
        if (resource == null) {
            return 0;
        }

        Collection<? extends UserMapMarker> markers = resource.getUserMapMarkers(playerUuid);
        return markers == null ? 0 : markers.size();
    }

    private int countPersonalMarkers(Player player, World world, UUID playerUuid) {
        PlayerWorldData worldData = getPlayerWorldData(player, world);
        if (worldData == null) {
            return 0;
        }

        Collection<? extends UserMapMarker> markers = worldData.getUserMapMarkers(playerUuid);
        if (markers == null) {
            markers = worldData.getUserMapMarkers();
        }
        return markers == null ? 0 : markers.size();
    }

    private UUID resolveOwner(World world, Player player, UUID playerUuid, String markerId) {
        WorldMarkersResource sharedResource = getWorldMarkersResource(world);
        if (sharedResource != null) {
            UserMapMarker sharedMarker = sharedResource.getUserMapMarker(markerId);
            if (sharedMarker != null) {
                return sharedMarker.getCreatedByUuid();
            }
        }

        PlayerWorldData worldData = getPlayerWorldData(player, world);
        if (worldData != null) {
            UserMapMarker personalMarker = worldData.getUserMapMarker(markerId);
            if (personalMarker != null) {
                UUID owner = personalMarker.getCreatedByUuid();
                return owner != null ? owner : playerUuid;
            }
        }

        return null;
    }

    private boolean forceCreateMarkerNow(World world, Player player, PlayerRef playerRef, CreateUserMarker packet) {
        if (world == null || player == null || playerRef == null || packet == null) {
            return false;
        }

        String markerId = (packet.shared ? "user_shared_" : "user_personal_") + UUID.randomUUID();
        UserMapMarker marker = new UserMapMarker();
        marker.setId(markerId);
        marker.setPosition(packet.x, packet.z);
        marker.setName(packet.name == null ? "Marker" : packet.name);
        marker.setIcon(packet.markerImage == null ? "UserA.png" : packet.markerImage);
        marker.setColorTint(packet.tintColor);
        marker.withCreatedByUuid(playerRef.getUuid());
        marker.withCreatedByName(playerRef.getUsername());

        if (packet.shared) {
            WorldMarkersResource sharedStore = getWorldMarkersResource(world);
            if (sharedStore == null) {
                return false;
            }
            sharedStore.addUserMapMarker(marker);
            return true;
        }

        PlayerWorldData worldData = getPlayerWorldData(player, world);
        if (worldData == null) {
            return false;
        }
        worldData.addUserMapMarker(marker);
        return true;
    }

    private boolean forceRemoveMarkerNow(World world, Player player, String markerId) {
        if (world == null || markerId == null || markerId.isBlank()) {
            return false;
        }

        boolean removed = false;
        WorldMarkersResource sharedStore = getWorldMarkersResource(world);
        if (sharedStore != null && sharedStore.getUserMapMarker(markerId) != null) {
            sharedStore.removeUserMapMarker(markerId);
            removed = true;
        }

        PlayerWorldData worldData = getPlayerWorldData(player, world);
        if (worldData != null && worldData.getUserMapMarker(markerId) != null) {
            worldData.removeUserMapMarker(markerId);
            removed = true;
        }

        // Native death markers can also be tracked in deathPositions.
        if (worldData != null && worldData.removeLastDeath(markerId)) {
            removed = true;
        }

        return removed;
    }

    private boolean isNativeDeathMarker(PlayerWorldData worldData, String markerId) {
        if (worldData == null || markerId == null || markerId.isBlank()) {
            return false;
        }

        try {
            List<PlayerDeathPositionData> deaths = worldData.getDeathPositions();
            if (deaths == null || deaths.isEmpty()) {
                return false;
            }

            for (PlayerDeathPositionData death : deaths) {
                if (death != null && markerId.equals(death.getMarkerId())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }

        return false;
    }

    private PlayerWorldData getPlayerWorldData(Player player, World world) {
        if (player == null || world == null) {
            return null;
        }

        PlayerConfigData data = player.getPlayerConfigData();
        if (data == null) {
            return null;
        }

        return data.getPerWorldData(world.getName());
    }

    private WorldMarkersResource getWorldMarkersResource(World world) {
        if (world == null || world.getChunkStore() == null || world.getChunkStore().getStore() == null) {
            return null;
        }

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        return chunkStore.getResource(WorldMarkersResource.getResourceType());
    }

    private World resolveWorld(PlayerRef playerRef) {
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid() || entityRef.getStore() == null) {
            return null;
        }

        Store<EntityStore> store = entityRef.getStore();
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null) {
            return null;
        }

        return entityStore.getWorld();
    }

    private Player resolvePlayerOnWorldThread(PlayerRef playerRef) {
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid() || entityRef.getStore() == null) {
            return null;
        }

        try {
            return entityRef.getStore().getComponent(entityRef, Player.getComponentType());
        } catch (Exception ignored) {
            return null;
        }
    }

    List<String> getAvailableMarkerImages() {
        return this.availableMarkerImages;
    }

    String getSelectedMarkerImage(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        return this.selectedMarkerImageByPlayer.get(playerUuid);
    }

    void reloadAvailableMarkerImages() {
        this.availableMarkerImages = scanAvailableMarkerImages();
    }

    boolean canUseMarkerUi(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }

        return hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_BYPASS_ALL, false)
                || hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_ADMIN, false)
                || hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_MARKER_UI, false);
    }

    MarkerSelectionResult clearGlobalMarkerImage(PlayerRef actor) {
        if (actor == null || actor.getUuid() == null) {
            return MarkerSelectionResult.failure("Invalid player context.");
        }

        if (!canUseMarkerUi(actor.getUuid())) {
            return MarkerSelectionResult.failure("You do not have permission to use /marker ("
                    + BetterMarkerMapPermissions.PERM_MARKER_UI + ").");
        }

        this.selectedMarkerImageByPlayer.remove(actor.getUuid());
        return MarkerSelectionResult.success("Your marker icon reset. New markers use your packet-selected icon.");
    }

    MarkerSelectionResult setGlobalMarkerImage(PlayerRef actor, String requestedImage) {
        if (actor == null || actor.getUuid() == null) {
            return MarkerSelectionResult.failure("Invalid player context.");
        }

        UUID playerUuid = actor.getUuid();
        if (!canUseMarkerUi(playerUuid)) {
            return MarkerSelectionResult.failure("You do not have permission to use /marker ("
                    + BetterMarkerMapPermissions.PERM_MARKER_UI + ").");
        }

        boolean isAdmin = hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_BYPASS_ALL, false)
                || hasPermission(playerUuid, BetterMarkerMapPermissions.PERM_ADMIN, false);

        MarkerImageRules.ImageDecision imageDecision = this.markerImageRules.resolveForSelection(
                isAdmin,
                requestedImage,
                permission -> hasPermission(playerUuid, permission, false)
        );

        if (!imageDecision.allowed()) {
            return MarkerSelectionResult.failure(imageDecision.deniedReason());
        }

        String resolvedIcon = imageDecision.image();
        boolean knownIcon = this.availableMarkerImages.stream().anyMatch(icon -> icon.equalsIgnoreCase(resolvedIcon));
        if (!knownIcon) {
            return MarkerSelectionResult.failure("Unknown marker icon: " + resolvedIcon + ".");
        }

        this.selectedMarkerImageByPlayer.put(playerUuid, resolvedIcon);
        return MarkerSelectionResult.success("Your marker icon set to " + resolvedIcon
            + ". Only your newly created markers are affected.");
    }

    private List<String> scanAvailableMarkerImages() {
        Set<String> iconNames = new HashSet<>();

        Path root = Paths.get(".").toAbsolutePath().normalize();
        collectMarkerIconsFromDirectory(root.resolve("src/main/resources/Common/UI/WorldMap/MapMarkers"), iconNames);
        collectMarkerIconsFromDirectory(root.resolve("Common/UI/WorldMap/MapMarkers"), iconNames);

        collectMarkerIconsFromCodeSource(iconNames);

        if (iconNames.isEmpty()) {
            iconNames.addAll(List.of(
                    "UserA.png",
                    "UserB.png",
                    "UserC.png",
                    "UserD.png",
                    "UserE.png",
                    "UserF.png",
                    "Death.png",
                    "Home.png",
                    "Coordinate.png",
                    "Spawn.png",
                    "Campfire.png",
                    "Portal.png",
                    "Warp.png",
                    "Prefab.png"
            ));
        }

        List<String> sorted = new ArrayList<>(iconNames);
        sorted.sort(Comparator.comparing(String::toLowerCase));
        return List.copyOf(sorted);
    }

    private void collectMarkerIconsFromDirectory(Path directory, Set<String> sink) {
        try {
            if (directory == null || !Files.isDirectory(directory)) {
                return;
            }

            Files.list(directory)
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .map(MarkerImageRules::normalizeImageName)
                    .filter(icon -> icon != null && !icon.isBlank())
                    .forEach(sink::add);
        } catch (Exception ignored) {
            // Best effort icon discovery.
        }
    }

    private void collectMarkerIconsFromCodeSource(Set<String> sink) {
        try {
            Path codePath = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(codePath)) {
                collectMarkerIconsFromDirectory(codePath.resolve("Common/UI/WorldMap/MapMarkers"), sink);
                return;
            }

            if (!Files.isRegularFile(codePath)) {
                return;
            }

            try (ZipFile zipFile = new ZipFile(codePath.toFile())) {
                zipFile.stream()
                        .map(ZipEntry::getName)
                        .filter(name -> name.startsWith(MAP_MARKERS_RESOURCE_PREFIX) && !name.endsWith("/"))
                        .map(name -> name.substring(MAP_MARKERS_RESOURCE_PREFIX.length()))
                        .map(MarkerImageRules::normalizeImageName)
                        .filter(icon -> icon != null && !icon.isBlank())
                        .forEach(sink::add);
            }
        } catch (Exception ignored) {
            // Best effort icon discovery.
        }
    }

    private boolean hasPermission(UUID playerUuid, String permission, boolean defaultValue) {
        if (permission == null || permission.isBlank()) {
            return defaultValue;
        }

        try {
            PermissionsModule perms = PermissionsModule.get();
            return perms.hasPermission(playerUuid, permission, defaultValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private void deny(PlayerRef playerRef, String reason) {
        try {
            playerRef.sendMessage(Message.raw(PREFIX + reason));
        } catch (Exception ignored) {
            // Best effort.
        }
    }

    static final class MarkerSelectionResult {
        private final boolean success;
        private final String message;

        private MarkerSelectionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        static MarkerSelectionResult success(String message) {
            return new MarkerSelectionResult(true, message);
        }

        static MarkerSelectionResult failure(String message) {
            return new MarkerSelectionResult(false, message);
        }

        boolean success() {
            return this.success;
        }

        String message() {
            return this.message;
        }
    }
}
