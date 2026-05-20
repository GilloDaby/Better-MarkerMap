package com.gillodaby.bettermarkermap;

import java.util.Arrays;
import java.util.List;

public final class BetterMarkerMapPermissions {

    private BetterMarkerMapPermissions() {
    }

    public static final String PERM_BYPASS_ALL = "bettermarkermap.bypass.all";
    public static final String PERM_ADMIN = "bettermarkermap.admin";

    public static final String PERM_CREATE_PERSONAL = "bettermarkermap.create.personal";
    public static final String PERM_CREATE_SHARED = "bettermarkermap.create.shared";

    public static final String PERM_REMOVE_OWN = "bettermarkermap.remove.own";
    public static final String PERM_REMOVE_ANY = "bettermarkermap.remove.any";

    public static final String PERM_TELEPORT_MARKER = "bettermarkermap.teleport.marker";
    public static final String PERM_MARKER_UI = "bettermarkermap.marker.ui";

    public static final String PERM_MARKER_USE_ANY = "bettermarkermap.marker.use.any";
    public static final String PERM_MARKER_USE_PREFIX = "bettermarkermap.marker.use.";

    public static final String PERM_LIMIT_UNLIMITED_ALL = "bettermarkermap.limit.unlimited";
    public static final String PERM_LIMIT_PERSONAL_PREFIX = "bettermarkermap.limit.personal.";
    public static final String PERM_LIMIT_SHARED_PREFIX = "bettermarkermap.limit.shared.";
    public static final String PERM_LIMIT_PERSONAL_UNLIMITED = "bettermarkermap.limit.personal.unlimited";
    public static final String PERM_LIMIT_SHARED_UNLIMITED = "bettermarkermap.limit.shared.unlimited";

    public static final List<Integer> SUPPORTED_LIMIT_TIERS = Arrays.asList(
            1, 3, 5, 8, 10, 12, 15, 20, 30, 40, 50, 75, 100, 150, 200
    );
}
