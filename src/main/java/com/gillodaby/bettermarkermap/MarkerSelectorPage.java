package com.gillodaby.bettermarkermap;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

final class MarkerSelectorPage extends InteractiveCustomUIPage<MarkerSelectorPage.MarkerSelectorEventData> {

    private static final String PREFIX = "[BetterMarkerMap] ";
    private static final String LAYOUT = "BetterMarkerMap/MarkerSelector.ui";
    private static final String MARKER_TEXTURE_PREFIX = "Textures/BetterMarkerMap/MapMarkers/";

    private final MarkerPermissionPacketFilter markerFilter;

    MarkerSelectorPage(PlayerRef playerRef, MarkerPermissionPacketFilter markerFilter) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, MarkerSelectorEventData.CODEC);
        this.markerFilter = markerFilter;
    }

    @Override
    public void build(Ref<EntityStore> ref,
                      UICommandBuilder cmd,
                      UIEventBuilder evt,
                      Store<EntityStore> store) {
        cmd.append(LAYOUT);
        buildContent(cmd, evt);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref,
                                Store<EntityStore> store,
                                MarkerSelectorEventData data) {
        if (data == null || data.action == null) {
            return;
        }

        switch (data.action) {
            case "close" -> close();
            case "reload" -> {
                this.markerFilter.reloadAvailableMarkerImages();
                notifyPlayer("Marker icon list reloaded.");
                refresh();
            }
            case "vanilla" -> {
                MarkerPermissionPacketFilter.MarkerSelectionResult result =
                        this.markerFilter.clearGlobalMarkerImage(this.playerRef);
                notifyPlayer(result.message());
                refresh();
            }
            case "select" -> {
                MarkerPermissionPacketFilter.MarkerSelectionResult result =
                        this.markerFilter.setGlobalMarkerImage(this.playerRef, data.icon);
                notifyPlayer(result.message());
                refresh();
            }
            default -> {
            }
        }
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        buildContent(cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    private void buildContent(UICommandBuilder cmd, UIEventBuilder evt) {
        String selectedGlobalIcon = this.markerFilter.getSelectedMarkerImage(this.playerRef.getUuid());
        List<String> icons = this.markerFilter.getAvailableMarkerImages();
        String topPreviewIcon = selectedGlobalIcon;

        cmd.set("#TitleText.Text", "Marker Icon Selector");
        cmd.set("#CurrentIconLabel.Text", "Your Selected Icon");
        cmd.set("#CurrentPreviewHint.Text", "Your marker icon preview");
        cmd.set("#CurrentIconValue.Text", selectedGlobalIcon == null
                ? "Vanilla (packet-selected)"
                : selectedGlobalIcon);

        if (topPreviewIcon != null && !topPreviewIcon.isBlank()) {
            String texturePath = toUiTexturePath(topPreviewIcon);
            if (texturePath != null) {
                cmd.set("#CurrentIconPreview.Visible", true);
                cmd.setObject("#CurrentIconPreview.Background", new PatchStyle(Value.of(texturePath)));
            } else {
                cmd.set("#CurrentIconPreview.Visible", false);
                cmd.setNull("#CurrentIconPreview.Background");
            }
        } else {
            cmd.set("#CurrentIconPreview.Visible", false);
            cmd.setNull("#CurrentIconPreview.Background");
        }

        cmd.set("#CloseButton.Text", "Close");
        cmd.set("#ReloadButton.Text", "Reload Icons");
        cmd.set("#UseVanillaButton.Text", "Use Vanilla");

        cmd.clear("#MarkerList");
        cmd.set("#EmptyLabel.Text", icons.isEmpty() ? "No marker icons found." : "");

        int index = 0;
        for (String icon : icons) {
            cmd.append("#MarkerList", "BetterMarkerMap/MarkerSelectorItem.ui");
            String selector = "#MarkerList[" + index + "]";

            cmd.set(selector + " #IconName.Text", icon);
            cmd.set(selector + " #SelectButton.Text", "Select");

            boolean selected = selectedGlobalIcon != null && selectedGlobalIcon.equalsIgnoreCase(icon);
            cmd.set(selector + " #SelectedTag.Visible", selected);

            String texturePath = toUiTexturePath(icon);
            if (texturePath != null) {
                cmd.set(selector + " #IconPreview.Visible", true);
                cmd.setObject(selector + " #IconPreview.Background", new PatchStyle(Value.of(texturePath)));
            } else {
                cmd.set(selector + " #IconPreview.Visible", false);
                cmd.setNull(selector + " #IconPreview.Background");
            }

            EventData select = new EventData()
                    .append("Action", "select")
                    .append("Icon", icon);
            evt.addEventBinding(CustomUIEventBindingType.Activating, selector + " #SelectButton", select, false);
            index++;
        }

        evt.addEventBinding(CustomUIEventBindingType.Activating,
                "#CloseButton",
                new EventData().append("Action", "close"),
                false);

        evt.addEventBinding(CustomUIEventBindingType.Activating,
                "#ReloadButton",
                new EventData().append("Action", "reload"),
                false);

        evt.addEventBinding(CustomUIEventBindingType.Activating,
                "#UseVanillaButton",
                new EventData().append("Action", "vanilla"),
                false);
    }

    private void notifyPlayer(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        this.playerRef.sendMessage(Message.raw(PREFIX + message));
    }

    private String toUiTexturePath(String rawIconName) {
        if (rawIconName == null || rawIconName.isBlank()) {
            return null;
        }

        String normalizedIcon = MarkerImageRules.normalizeImageName(rawIconName);
        if (normalizedIcon == null) {
            return null;
        }

        return MARKER_TEXTURE_PREFIX + normalizedIcon;
    }

    static final class MarkerSelectorEventData {
        static final BuilderCodec<MarkerSelectorEventData> CODEC = BuilderCodec.builder(
                        MarkerSelectorEventData.class,
                        MarkerSelectorEventData::new
                )
                .append(new KeyedCodec<>("Action", Codec.STRING),
                        (data, value) -> data.action = value,
                        data -> data.action)
                .add()
                .append(new KeyedCodec<>("Icon", Codec.STRING),
                        (data, value) -> data.icon = value,
                        data -> data.icon)
                .add()
                .build();

        String action;
        String icon;

        MarkerSelectorEventData() {
        }
    }
}
