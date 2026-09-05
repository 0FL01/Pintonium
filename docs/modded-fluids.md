# Modded fluids: world blocks and PBR atlas

## Iteration 3: oil transparency

With shaderpacks enabled, BuildCraft crude oil (`fluid_block_oil_heat_0/1/2`)
and Thermal Foundation `fluid_crude_oil` now enter the existing sorted translucent
terrain pass. Their sampled albedo alpha is multiplied by 0.8. This is a visual
preset, not measured absorption or depth-dependent refraction. Other fluids,
tank geometry and the no-shader render path are unchanged.

`OilRendering` identifies these explicit registry entries, guarded by IFluidBlock;
neither a water material nor a substring in a fluid name implies this opacity.
An internal render-type marker (257) accompanies the existing quad metadata.
`VintageOilShader` decodes it back to fluid type 1 for the pack and carries opacity
separately. Block IDs, vertex color/AO, lightmap and labPBR channels are untouched.
This matters for Complementary, whose water program ignores vertex alpha for
coverage and uses it for lighting instead. The atlas itself is not modified.

The Forge122 compiler adapter wraps direct albedo samples in fragment `main`
(including `textureAF`), leaving generic sampler helpers and other samplers alone.
It runs after the normal `patched_shaders` dump. Geometry/tessellation pipelines
do not receive the opacity varying; packs with those stages, helper-only albedo
reads, or their own alpha replacement are not guaranteed transparent oil.

Verification: package build and standalone `VintageOilShaderTest` (classpath:
packaged jar, fastutil 8.5.9 and commons-lang3). The test checks marker decoding,
AO/PBR isolation and optionally transforms a supplied `patched_shaders` directory
into `build/oil-shader-check`. Transformation of the captured Complementary
terrain/water/shadow sources passed. Headless EGL initialization was unavailable,
so GPU compilation and the actual image still require a full client restart.
Check oil top/side/flow over visible terrain, camera movement, water boundaries
and shadows; compare vanilla water/lava and then disable shaders as controls.

## Iteration 2: generated labPBR materials

Forge122 now supplies the existing PBR manager with a parallel normal/specular
atlas, tracked and bound with the Minecraft block atlas. The loader matches
registered Forge fluids' still/flow sprites against actually uploaded sprites.
Unauthored mod-fluid sprites receive RGBA (240,10,0,0) specular data: high
smoothness, dielectric F0, no added SSS or emission. This is a common synthetic
surface preset, not a measured physical property of each fluid. Albedo, UVs,
animation and render layers remain unchanged. Vanilla water/lava are excluded.

Static resource-pack `_s` and `_n` maps take priority, copied as raw channels even
at alpha zero. Other unauthored atlas regions remain neutral. Authored animated
maps or incompatible frame-sheet dimensions warn and use fallback/neutral data;
animated authored PBR is not implemented. Constant generated maps need no frame
updates. Resource reload/restitch/deletion invalidates the owned PBR textures.
Unit-zero tracking covers skins/armor/hands so the atlas material is not retained
when their separate textures are bound.

Complementary client settings now use RP_MODE=3 (labPBR) and
BLOCK_REFLECT_QUALITY=2. This is a pack-wide mode change, not an oil-only option.
Exposure/fog/shadows are unchanged. Shaders that do not consume labPBR specular
maps will not gain reflections from this implementation.

Limitations: only registered atlas still/flow sprites; no automatic discovery of
stack-dependent textures/overlays or non-atlas tank textures. Shared sprites share
material parameters. A fluid needs reflected surroundings/light to show highlights;
the preset neither makes black oil white nor simulates waves or an oil film.
Existing packed block light is preserved; luminosity is not guessed as PBR emission.

Verification: package build and existing common shader tests; standalone regression
for NativeImage channel order and PNG data at alpha zero:

```
java -Djava.awt.headless=true --class-path build/libs/2.4.1-dev/pintonium-forge-1.12.2-2.4.1-dev.jar forge122/src/test/java/org/embeddedt/embeddium/compat/mc/NativeImageMaterialChannelsTest.java
```

Client verification remains required. Inspect `[Fluid PBR]` for generated sprites
and authored counts, compare world oil at grazing angles, then reload resources
and inspect hands/armor and ordinary blocks for material leakage. Also check a
translucent and emissive fluid. Full tank/TESR material attribution remains pending.

## Iteration 1: classification and layers

The world-meshing path recognizes Forge `IFluidBlock` in addition to liquid
materials. `VintageChunkBuildContext` preserves the original render layer for
these quads instead of reclassifying them from atlas texture opacity. It emits
the fluid render-type marker without assuming water appearance.

Explicit shaderpack block-state mappings retain priority. Unmapped modded fluids
use neutral material 0, bypassing foliage/name heuristics. IDs such as Chocapic's
water 8 or Complementary's water 32000 are not interchangeable or universal.
No fluid is remapped to water/lava based on its name, temperature or luminosity.

Forge/mod model geometry, tint/alpha, UVs, flow/surface heights and packed light
remain on the existing path. This iteration does not introduce oil-specific PBR,
water-style reflections/refraction or a new fluid simulation/renderer.

## Confirmed installed world-fluid paths

- BuildCraft 8.0.0: BCFluidBlock extends BlockFluidClassic and explicitly uses
  SOLID; oil naming includes `buildcraftenergy:fluid_block_oil_heat_0`.
- Thermal Foundation: CoFH BlockFluidCore extends BlockFluidClassic, with
  forge:fluid models; includes crude oil and emissive energetic fluids.
- TConstruct: BlockTinkerFluid/BlockMolten use Forge fluids; molten luminosity 10.
- Immersive Engineering: BlockIEFluid extends BlockFluidClassic.
- Forestry: BlockForestryFluid extends BlockFluidClassic; water/lava material
  selection does not identify the actual fluid.

Evidence is local jar/bytecode inspection, not runtime registry or visual coverage.

## Iteration 1 result and continuing scope

User reported BuildCraft oil appearance unchanged after iteration 1. That iteration
preserves classification/layers; it does not assign a reflective oil material.
The inspected Complementary configuration had RP_MODE=0 and
BLOCK_REFLECT_QUALITY=1; opaque PBR reflections require RP_MODE>=1 and quality>=2.
Complementary contains no dedicated oil material. Its obsidian material preserves
albedo but is not an oil substitute. Accurate reflectivity requires pack material
support or PBR texture maps. Iteration 2 above implements the atlas path; it does
not introduce a global water/obsidian material-ID mapping.

Build: `bash ./gradlew -Ptarget_versions=1.12.2 packageJar --offline --console=plain`.
Client: verify a world oil pool (top/side/flow), translucent non-emissive liquid,
molten/emissive liquid and upward/gaseous liquid, then vanilla water/lava as controls.
Use Complementary and Chocapic, clear/rain and day/night; check shadow behavior.
Build success alone does not establish visual compatibility.

Next: contained-fluid geometry. BuildCraft/TConstruct/IE use TESR helpers, whereas
Thermal Expansion tanks include baked fluid quads. Neither is identified by
classifying the host block as IFluidBlock. Preserve tank shell material and scope
fluid context to the actual fluid subdraw/quads; do not assign it to the whole tank.
