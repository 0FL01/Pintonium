# Native Held-Item Light (1.12.2)

Pintonium lights terrain around the current client's player using the brighter of
their two held items. Block items use their metadata-derived block light value;
lava buckets emit 15. No standalone dynamic-light mod is needed or should be used
alongside this feature.

With shaderpacks disabled, the source follows the player's interpolated eye
position every frame. A camera-relative uniform raises the native terrain shader's
block-light coordinate before lightmap sampling, preserving skylight and brighter
existing block light. AO and tint remain intact. Lighting is evaluated per vertex
and interpolated across the surface; it does not add shadow/wall occlusion.
Movement, item changes, and teleports do not request held-light chunk rebuilds in
this mode. Empty hands disable the contribution on the next frame, without waiting
for old chunks to rebuild. World changes clear the source; dead/spectator players
do not emit light.

Shaderpacks retain the previous baked-light path: the source is snapped to the
player's eye block and chunk build contexts capture an immutable snapshot for
`WorldSlice.getCombinedLight`. Moving between blocks, changing brightness, or
emptying hands rebuilds old/new neighborhoods of radius `light level + 2` (at most
64 sections each, clipped to world height). Teleports do not rebuild the space
between them. Switching back to native rendering invalidates the previous baked
neighborhood. Shader held-item uniforms share the same item-emission mapping;
shaderpack programs are not transformed by the native lighting feature.

`CeleritasWorldRenderer.getHeldItemLight(World)` returns the current-frame source
as `HeldItemLight(double x, double y, double z, int level)`. Its integer-coordinate
`apply(int, int, int, int)` API is retained, with a double-coordinate overload.
`getTerrainHeldItemLight(World)` is the separate shaderpack-only worker snapshot
and returns `NONE` during native rendering.

With shaderpacks disabled, RenderManager's ordinary and multipass lightmap setup
also samples this source at the rendered entity's interpolated eye position.
This lights nearby players/mobs without making them new sources. First-person
hands sample at the player's source position. Existing brighter light and vanilla
burning-entity fullbright remain intact. These hooks do not run with shaderpacks.

The only source is the local player's held items, not dropped items or other
players' equipment. Custom renderers that override lightmap coordinates and
tile-entity renderers are not covered by the entity hook. Light has radial falloff without
wall occlusion and does not affect gameplay/server light. Shaderpack terrain may
still step or lag as queued chunk rebuilds complete. Shader packs may add their own hand
light or interpret/ignore terrain lightmaps differently.

Manual check with shaders off: in a dark area, hold a torch in each hand separately,
walk slowly within a single block and across section boundaries, rotate in place,
then empty both hands and teleport. The terrain light must follow continuously
and old locations must dim immediately, without held-light rebuild requests.
Repeat in third person and far from the world origin; skylight/existing lamps
must stay unchanged. Approach a mob, inspect your player in third person and your
hands in first person, then remove the torch: their brightness must rise and fall
without dimming existing stronger light. Repeat with shaders on (queued updates are expected), toggle
back off while holding a torch, and change dimensions. This needs in-client
verification; a successful build alone does not establish visual correctness.
