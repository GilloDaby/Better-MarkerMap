package com.gillodaby.bettermarkermap;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class MarkerSelectorCommand extends AbstractPlayerCommand {

    private static final String PREFIX = "[BetterMarkerMap] ";

    private final MarkerPermissionPacketFilter markerFilter;

    MarkerSelectorCommand(MarkerPermissionPacketFilter markerFilter) {
        super("marker", "Open marker icon selector");
        this.markerFilter = markerFilter;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext context,
                           Store<EntityStore> store,
                           Ref<EntityStore> ref,
                           PlayerRef playerRef,
                           World world) {
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        if (!this.markerFilter.canUseMarkerUi(playerRef.getUuid())) {
            context.sendMessage(Message.raw(PREFIX + "You do not have permission to use /marker ("
                    + BetterMarkerMapPermissions.PERM_MARKER_UI + ")."));
            return;
        }

        this.markerFilter.reloadAvailableMarkerImages();
        player.getPageManager().openCustomPage(ref, store, (CustomUIPage) new MarkerSelectorPage(playerRef, this.markerFilter));
    }
}
