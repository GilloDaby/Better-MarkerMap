package com.gillodaby.bettermarkermap;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.concurrent.CompletableFuture;

final class MarkerMapPermissionsCommand extends AbstractCommand {

    MarkerMapPermissionsCommand() {
        super("markermap", "Show Better MarkerMap permission nodes");
        setAllowsExtraArguments(true);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sendMessage(Message.raw("[BetterMarkerMap] Permission nodes:"));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_BYPASS_ALL));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_ADMIN));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_CREATE_PERSONAL));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_CREATE_SHARED));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_REMOVE_OWN));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_REMOVE_ANY + " (legacy, use admin)"));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_TELEPORT_MARKER));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_MARKER_UI + " (open /marker selector UI)"));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_MARKER_USE_ANY));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_MARKER_USE_PREFIX + "<image_key>"));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_LIMIT_UNLIMITED_ALL));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_LIMIT_PERSONAL_UNLIMITED));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_LIMIT_SHARED_UNLIMITED));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_LIMIT_PERSONAL_PREFIX + "<tier>"));
        ctx.sendMessage(Message.raw(" - " + BetterMarkerMapPermissions.PERM_LIMIT_SHARED_PREFIX + "<tier>"));
        ctx.sendMessage(Message.raw("[BetterMarkerMap] Supported tiers: " + BetterMarkerMapPermissions.SUPPORTED_LIMIT_TIERS));
        ctx.sendMessage(Message.raw("[BetterMarkerMap] image_key format: lower-case file name without .png (non [a-z0-9] -> _)."));
        ctx.sendMessage(Message.raw("[BetterMarkerMap] Example image nodes:"
            + " bettermarkermap.marker.use.usera"
            + ", bettermarkermap.marker.use.knight_legend"));
        ctx.sendMessage(Message.raw("[BetterMarkerMap] /marker opens UI with all icons in MapMarkers folder."));
        ctx.sendMessage(Message.raw("[BetterMarkerMap] Example: lp user <player> permission set bettermarkermap.limit.personal.30 true"));
        ctx.sendMessage(Message.raw("[BetterMarkerMap] Marker image config file: marker-images.json"));
        return CompletableFuture.completedFuture(null);
    }
}
