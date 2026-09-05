# Shader diagnostics on 1.12.2

## Enchanted armor layer

Legacy LayerArmorBase's additive glint previously ran inside gbuffers_entities
and its MRT layout. Complementary instead supplies gbuffers_armor_glint writing
only color. The bridge now scopes the pack glint program, draw targets and blend
directives to renderEnchantedGlint, restoring the enclosing entity program in a
finally block. Vanilla animated UVs/color and equal-depth/no-depth-write behavior
remain owned by LayerArmorBase. Glint is skipped in shadow passes. World entity
shader rendering is required; GUI/no-shader rendering is unchanged.
Verify enchanted and plain armor in third person and normal skin separately;
the reported white/pink player image does not by itself prove all overexposure
comes from glint. Hand-depth correction was subsequently confirmed by the user.

## Hand depth and water reflections

With shaderpacks, the vanilla depth-only clear immediately before renderHand is
suppressed; the initial full-frame clear is unchanged. Hand projection is
pre-scaled by IrisConstants.DEPTH, matching the published MC_HAND_DEPTH macro.
Without shaderpacks both hooks preserve vanilla behavior. The previous path
cleared scene depth before composites and did not implement the advertised hand
depth convention. Verify hand motion against water reflections and near walls;
compilation/remap checks do not establish absence of visual flicker.

This diagnostic build does not change shader rendering rules and is not specific
to a shaderpack. Use the same scene and pack settings when comparing builds.

With Minecraft closed, set `enableDebugOptions=true` in
`minecraft/config/embeddium-shaders.properties`, then start the client.
Set it back to `false` and restart to disable diagnostics completely.

The existing shader printer writes transformed GLSL to `minecraft/patched_shaders/`.
This directory is overwritten on shader reload: collect it before switching packs.
The client log receives `[Shader diagnostics]` environment/pipeline records and
`[Shader GL]` driver messages. GL source, type, ID and severity are hexadecimal
OpenGL enums. All severities are requested, capped at 2000 messages per reload.
The callback does not force synchronous rendering or issue GL calls. If another
mod owns a callback it is left intact; if KHR_debug/OpenGL 4.3 is unavailable,
pipeline records and source dumps still work. A non-debug GL context may omit
driver messages, and visually incorrect rendering need not produce any GL error.

`[Shader quad]` records the first 64 fullscreen draws per loaded pack, including
program attribute locations, framebuffer, viewport and write state. For ordinary
single-sample, non-integer FBOs, `[Shader quad pixel]` records three diagonal RGBA
samples before/after each draw. Read framebuffer/buffer bindings are restored.
Readback can cause short startup stalls; it stops after 64 draws and is absent
with debug options disabled. These sparse samples are not a full GPU frame capture.

## Chocapic V9 Low: fullscreen alpha test

The 2026-09-05 12:16 trace showed varying RGB written by the penultimate
fullscreen program (79), but unchanged samples across the last program (80).
The dumped `014_final.fsh` writes only `gl_FragColor.rgb`. The legacy fullscreen
renderer did not disable Minecraft's alpha test, allowing inherited alpha testing
to reject post-processing fragments. It now saves/disables/restores that state,
just as it does depth testing and culling. Visual confirmation in Cleanroom is
still required; sparse samples alone do not establish the cause of the whole
blank scene.

## Chocapic V9 Low: displaced block entities and held light

With `RENDER_SCALE_X/Y=0.7`, Chocapic's terrain and block vertex shaders both
scale/offset clip coordinates and apply temporal jitter. The generic block-entity
bridge used only `ftransform()`, losing these transformations. The block bridge
now attempts the original pack program regardless of attachment layout, keeping
the existing generic fallback on creation failure. Confirm alignment in the client.

The previously empty 1.12 held-item uniform provider now supplies item.properties
IDs and both hand light values per frame, including the old-hand-light maximum
rule. Light values currently cover ItemBlock emission and lava buckets; they do
not implement every custom item's dynamic-light provider. A vanilla torch emits
14. This shader input fix is not a general correction for night exposure.

Vanilla fog disabled does not disable Chocapic's atmospheric pass. The observed
settings still have BASE_FOG_AMOUNT, FOG_TOD_MULTIPLIER and BLOOMY_FOG at 1.0.
Do not treat these settings as evidence that shader fog is disabled, or hide
rendering defects by silently changing them.

## Weather and remaining emissive TESR defects

### Complementary Unbound r5.9: daytime compatibility setup

Before the shadow port, forge122 `createShadowRenderer` returned null and the
client used Complementary's SHADOW_QUALITY=-1 fallback. The new candidate supplies
`VintageShadowRenderer` and restores SHADOW_QUALITY=0 for validation. It renders
terrain and legacy entity/TESR casters into pack targets, including depth copy,
mipmaps and shadow composite. GL and renderer state are restored on exit.
Implementation/checkpoint and remaining runtime verification:
`goals/2026-09-05-forge122-shadow-pass.md`. Do not equate a successful build with
verified shadow compatibility or performance.

The client pack options also disable ATMOSPHERIC_FOG, BLOOM_FOG, BORDER_FOG,
CAVE_FOG, COLORED_LIGHT_FOG and set LIGHTSHAFT_BEHAVIOUR=0. Exposure and night
light multipliers remain unchanged. Underwater/lava/status-effect fog is retained.
Verify daytime at the same location near/far and check night after reloading the
pack. A configuration change is not proof that every daytime artifact is fixed.

For the current no-fog client experiment, Chocapic's BASE_FOG_AMOUNT,
CLOUDY_FOG_AMOUNT and BLOOMY_FOG options are set to 0.0 in its `.zip.txt` file.
This is a pack configuration change, not a global fog override for other packs.
Native held terrain light replaces the standalone dynamic-light addon; see
`native-dynamic-lights.md` for its scope and verification.

The particle bridge now compiles the resolved pack vertex/geometry/fragment
sources and registers common/custom uniforms and G-buffer samplers. Chocapic's
textured particle sources write forward color to attachment 2 and transform clip
coordinates for render scale/jitter. The removed generic particle sources instead
wrote opaque black to attachment 2 and used unscaled `ftransform()`, explaining
the black, displaced torch particles. Verify flame, smoke and rain splashes in
the client. A program creation failure is logged and leaves vanilla rendering;
this change does not claim to fix global fog or terrain light intensity.

The 1.12 integration now brackets `EntityRenderer.renderRainSnow` with the pack's
weather program, its draw-buffer layout, common/custom uniforms and blend
overrides. The phase and normal pipeline framebuffer are restored on return.
Rain/snow geometry is separate from rain-impact particles. Verify falling rain,
snow, clear weather and shader reload in the client; compilation alone does not
verify injection or driver behavior.

Open cases, not fixed by the weather bridge:
- Twilight Forest firefly glow is an additive alpha-modulated subdraw; Chocapic's
  block shader writes packed material data and ignores vertex alpha. It needs a
  separate color-output path, not arbitrary blending of packed G-buffer channels.
- TConstruct fluid quads use position/color/UV/lightmap without face normals,
  while the block shader consumes normals. The rain-specific pink saturation
  still needs runtime evidence; do not equate the missing input with a proven
  explanation of that color artifact.

For each report collect:
- build commit and jar SHA-256;
- shaderpack filename/version and its saved options;
- `minecraft/logs/latest.log` and `minecraft/patched_shaders/`;
- screenshot, dimension, camera position, time/weather, and reproduction steps;
- whether the issue also occurs without Celeritas Dynamic Lights.

Build from the repository root:
`bash ./gradlew -Ptarget_versions=1.12.2 packageJar`.
The checkout does not mark `gradlew` executable.
The 1.12 build uses Unimined 1.4.1 to avoid the removed Gradle 9 `Project.javaexec`
API. RetroFuturaBootstrap 1.0.7 is fetched from its upstream GitHub release because
the configured Maven repository returns 404 for that artifact.
The jar is under `build/libs/<version>/`. Test in the Java 25 Cleanroom client;
the Gradle development run is not equivalent to that environment.
