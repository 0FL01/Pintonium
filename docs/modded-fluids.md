# Modded fluids: iteration 1 (world blocks)

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

## Verification and next iteration

Build: `bash ./gradlew -Ptarget_versions=1.12.2 packageJar --offline --console=plain`.
Client: verify a world oil pool (top/side/flow), translucent non-emissive liquid,
molten/emissive liquid and upward/gaseous liquid, then vanilla water/lava as controls.
Use Complementary and Chocapic, clear/rain and day/night; check shadow behavior.
Build success alone does not establish visual compatibility.

Next: contained-fluid geometry. BuildCraft/TConstruct/IE use TESR helpers, whereas
Thermal Expansion tanks include baked fluid quads. Neither is identified by
classifying the host block as IFluidBlock. Preserve tank shell material and scope
fluid context to the actual fluid subdraw/quads; do not assign it to the whole tank.
