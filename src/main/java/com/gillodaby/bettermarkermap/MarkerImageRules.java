package com.gillodaby.bettermarkermap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class MarkerImageRules {

    static final String CONFIG_FILE_NAME = "marker-images.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String defaultMarkerImage;
    private final boolean enforceImagePermissions;
    private final String anyImagePermission;
    private final Map<String, String> replacementsByRequestedLower;
    private final Map<String, String> requiredPermissionByImageLower;

    private MarkerImageRules(String defaultMarkerImage,
                             boolean enforceImagePermissions,
                             String anyImagePermission,
                             Map<String, String> replacementsByRequestedLower,
                             Map<String, String> requiredPermissionByImageLower) {
        this.defaultMarkerImage = defaultMarkerImage;
        this.enforceImagePermissions = enforceImagePermissions;
        this.anyImagePermission = anyImagePermission;
        this.replacementsByRequestedLower = replacementsByRequestedLower;
        this.requiredPermissionByImageLower = requiredPermissionByImageLower;
    }

    static MarkerImageRules defaultRules() {
        return fromModel(defaultModel());
    }

    static MarkerImageRules load(Path configPath) {
        if (configPath == null) {
            return defaultRules();
        }

        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            if (Files.notExists(configPath)) {
                ConfigModel defaults = defaultModel();
                Files.writeString(
                        configPath,
                        GSON.toJson(defaults),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE
                );
                return fromModel(defaults);
            }

            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) {
                return defaultRules();
            }

            ConfigModel model = GSON.fromJson(json, ConfigModel.class);
            if (model == null) {
                return defaultRules();
            }

            return fromModel(model);
        } catch (Exception e) {
            System.out.println("[BetterMarkerMap] Failed to load marker image rules: " + e.getMessage());
            return defaultRules();
        }
    }

    ImageDecision resolveForCreate(boolean isAdmin,
                                   String requestedImage,
                                   PermissionLookup permissions) {
        return resolveInternal(isAdmin, requestedImage, permissions, true);
    }

    ImageDecision resolveForSelection(boolean isAdmin,
                                      String requestedImage,
                                      PermissionLookup permissions) {
        return resolveInternal(isAdmin, requestedImage, permissions, false);
    }

    private ImageDecision resolveInternal(boolean isAdmin,
                                          String requestedImage,
                                          PermissionLookup permissions,
                                          boolean applyReplacements) {
        String normalizedRequested = normalizeImageName(requestedImage);
        if (normalizedRequested == null) {
            normalizedRequested = this.defaultMarkerImage;
        }

        String resolvedImage = normalizedRequested;
        if (applyReplacements) {
            String replacement = this.replacementsByRequestedLower.get(normalizedRequested.toLowerCase(Locale.ROOT));
            resolvedImage = normalizeImageName(replacement != null ? replacement : normalizedRequested);
        }

        if (resolvedImage == null) {
            resolvedImage = this.defaultMarkerImage;
        }

        String resolvedLower = resolvedImage.toLowerCase(Locale.ROOT);
        String explicitPermission = this.requiredPermissionByImageLower.get(resolvedLower);
        boolean needsPermission = this.enforceImagePermissions || explicitPermission != null;

        if (!needsPermission || isAdmin) {
            return ImageDecision.allow(resolvedImage);
        }

        if (permissions != null
                && this.anyImagePermission != null
                && !this.anyImagePermission.isBlank()
                && permissions.hasPermission(this.anyImagePermission)) {
            return ImageDecision.allow(resolvedImage);
        }

        String specificPermission = explicitPermission;
        if (specificPermission == null || specificPermission.isBlank()) {
            specificPermission = BetterMarkerMapPermissions.PERM_MARKER_USE_PREFIX
                    + toPermissionSuffix(resolvedImage);
        }

        if (permissions != null && permissions.hasPermission(specificPermission)) {
            return ImageDecision.allow(resolvedImage);
        }

        return ImageDecision.deny(
                "You do not have permission to use marker image '"
                        + resolvedImage
                        + "' ("
                        + specificPermission
                        + ")."
        );
    }

    static String toPermissionSuffix(String imageName) {
        String normalized = normalizeImageName(imageName);
        if (normalized == null) {
            return "unknown";
        }

        String base = normalized.substring(0, normalized.length() - 4).toLowerCase(Locale.ROOT);
        base = base.replaceAll("[^a-z0-9]+", "_");
        base = base.replaceAll("^_+", "");
        base = base.replaceAll("_+$", "");

        return base.isBlank() ? "unknown" : base;
    }

    static String normalizeImageName(String rawImageName) {
        if (rawImageName == null) {
            return null;
        }

        String imageName = rawImageName.trim();
        if (imageName.isEmpty()) {
            return null;
        }

        imageName = imageName.replace('\\', '/');
        int lastSlash = imageName.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < imageName.length() - 1) {
            imageName = imageName.substring(lastSlash + 1);
        }

        if (!imageName.toLowerCase(Locale.ROOT).endsWith(".png")) {
            imageName += ".png";
        }

        if (!imageName.matches("[A-Za-z0-9_-]+\\.png")) {
            return null;
        }

        return imageName;
    }

    private static MarkerImageRules fromModel(ConfigModel model) {
        String defaultImage = normalizeImageName(model.defaultMarkerImage);
        if (defaultImage == null) {
            defaultImage = "UserA.png";
        }

        boolean enforcePermissions = model.enforceImagePermissions != null
                && model.enforceImagePermissions;

        String anyPermission = model.anyImagePermission;
        if (anyPermission == null || anyPermission.isBlank()) {
            anyPermission = BetterMarkerMapPermissions.PERM_MARKER_USE_ANY;
        }

        Map<String, String> replacements = new HashMap<>();
        if (model.replacements != null) {
            for (Map.Entry<String, String> entry : model.replacements.entrySet()) {
                String from = normalizeImageName(entry.getKey());
                String to = normalizeImageName(entry.getValue());
                if (from != null && to != null) {
                    replacements.put(from.toLowerCase(Locale.ROOT), to);
                }
            }
        }

        Map<String, String> requiredPermissions = new HashMap<>();
        if (model.requiredPermissions != null) {
            for (Map.Entry<String, String> entry : model.requiredPermissions.entrySet()) {
                String image = normalizeImageName(entry.getKey());
                String permission = entry.getValue();
                if (image != null && permission != null && !permission.isBlank()) {
                    requiredPermissions.put(image.toLowerCase(Locale.ROOT), permission.trim());
                }
            }
        }

        return new MarkerImageRules(
                defaultImage,
                enforcePermissions,
                anyPermission,
                replacements,
                requiredPermissions
        );
    }

    private static ConfigModel defaultModel() {
        ConfigModel model = new ConfigModel();
        model.defaultMarkerImage = "UserA.png";
        model.enforceImagePermissions = false;
        model.anyImagePermission = BetterMarkerMapPermissions.PERM_MARKER_USE_ANY;
        model.replacements = new HashMap<>();
        model.requiredPermissions = new HashMap<>();
        return model;
    }

    @FunctionalInterface
    interface PermissionLookup {
        boolean hasPermission(String permission);
    }

    static final class ImageDecision {
        private final boolean allowed;
        private final String image;
        private final String deniedReason;

        private ImageDecision(boolean allowed, String image, String deniedReason) {
            this.allowed = allowed;
            this.image = image;
            this.deniedReason = deniedReason;
        }

        static ImageDecision allow(String image) {
            return new ImageDecision(true, image, null);
        }

        static ImageDecision deny(String deniedReason) {
            return new ImageDecision(false, null, deniedReason);
        }

        boolean allowed() {
            return this.allowed;
        }

        String image() {
            return this.image;
        }

        String deniedReason() {
            return this.deniedReason;
        }
    }

    private static final class ConfigModel {
        String defaultMarkerImage;
        Boolean enforceImagePermissions;
        String anyImagePermission;
        Map<String, String> replacements;
        Map<String, String> requiredPermissions;
    }
}
