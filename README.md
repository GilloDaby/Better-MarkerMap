# Better MarkerMap

Better MarkerMap lets you control world map marker actions with permission nodes:
- create personal/shared markers
- remove own markers (others only with admin permission)
- teleport to marker
- apply marker limits by permission tier
- replace marker images and enforce per-image permissions
- open /marker UI to select a global icon for all markers

## Build output

After build, plugin jar is generated at:
- build/libs/BetterMarkerMap-1.0.0.jar

## Permission nodes

Reference file:
- permissions.json

- bettermarkermap.bypass.all
- bettermarkermap.admin
- bettermarkermap.create.personal
- bettermarkermap.create.shared
- bettermarkermap.remove.own
- bettermarkermap.remove.any (legacy alias, prefer bettermarkermap.admin)
- bettermarkermap.teleport.marker
- bettermarkermap.marker.ui
- bettermarkermap.marker.use.any
- bettermarkermap.marker.use.<image_key>
- bettermarkermap.limit.unlimited
- bettermarkermap.limit.personal.unlimited
- bettermarkermap.limit.shared.unlimited
- bettermarkermap.limit.personal.<tier>
- bettermarkermap.limit.shared.<tier>

Supported tiers:
1, 3, 5, 8, 10, 12, 15, 20, 30, 40, 50, 75, 100, 150, 200

image_key format:
- lowercase file name without .png
- any non [a-z0-9] becomes _

Examples:
- UserA.png -> bettermarkermap.marker.use.usera
- Knight-Legend.png -> bettermarkermap.marker.use.knight_legend

## Marker image rules file

On startup the plugin creates marker image rules in:
- ./mods/Better MarkerMap/marker-images.json

If ./marker-images.json exists, it is used first.

Config fields:
- defaultMarkerImage: fallback icon if no icon is sent (or invalid icon)
- enforceImagePermissions: when true, every marker image requires permission
- anyImagePermission: permission node that bypasses per-image checks
- replacements: replace incoming image keys (for example UserA.png -> Knight.png)
- requiredPermissions: per-image required permission node

Example:

```json
{
	"defaultMarkerImage": "UserA.png",
	"enforceImagePermissions": true,
	"anyImagePermission": "bettermarkermap.marker.use.any",
	"replacements": {
		"UserA.png": "Knight.png",
		"UserB.png": "Mage.png"
	},
	"requiredPermissions": {
		"Knight.png": "bettermarkermap.marker.use.knight",
		"Mage.png": "bettermarkermap.marker.use.mage"
	}
}
```

## Marker selector UI

Command:
- /marker

Behavior:
- loads all PNG icons found in MapMarkers folder
- click one icon to set it as global marker icon
- existing markers are updated to this icon (best effort)
- new markers will use the selected icon automatically
- click "Use Vanilla" to disable global override for future markers

## Runtime behavior

Better MarkerMap works without changing GameplayConfigs.

When vanilla worldmap validation would block a permission-allowed action
(for example marker cap reached at 12, ownership restriction, or distance check),
the plugin applies a packet-level bypass and writes markers directly.

Optional: you can still use a custom GameplayConfig if you want server defaults
to be closer to your permission tiers, but it is no longer required.
