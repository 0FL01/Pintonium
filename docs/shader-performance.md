# Forge122 shader performance

## Live follow-up: 72 FPS / 13.8 ms, GPU 91%

Same coastal scene family after all four iterations (POM off, water/clouds 1,
shadow/CPU trim, Alfheim 1.6 + Red Core 0.7.1 active). Previous checkpoint was
60 FPS / 16.6 ms at GPU 99%. Time of day differs between screenshots, so the
delta cannot be fully attributed to the changes, but GPU headroom reappeared
(99% → 91%) with no reported visual defects. Next RECON below works from this
checkpoint.

## Shadow distance/smoothing + caster uniform skip

Local options: `shadowDistance=96→64`, `SHADOW_SMOOTHING=3→2`. Fewer shadow
casters per frame on CPU and GPU; the same 1024 map over a smaller area keeps
near shadows sharp. Distant shadows beyond 64 blocks are gone; edge softness
is one step harder. Revert one line each if visible.

`prepareCaster` still restores full GL/framebuffer state per caster (entity
rendering clobbers it), but the uniform-upload tail (phase/entity listeners,
blockEntityId uniform, custom uniform push, mc_Entity attrib) now runs only
when phase, entity id or block-entity value changed since the previous caster.
Key resets at each shadow pass start. No draws, filtering or restoration
removed.

## Complementary option iteration (POM off, reflections 1, clouds 1)

Local `ComplementaryUnbound_r5.9.zip.txt` (not git-tracked): `POM=true→false`,
`WATER_REFLECT_QUALITY=2→1`, `CLOUD_QUALITY=2→1`. Everything else unchanged,
notably `BLOCK_REFLECT_QUALITY=2` + `RP_MODE=3` (oil PBR reflections stay on),
`DETAIL_QUALITY=2` (TAA stays on), shadows 1024/distance 96.

- POM off is expected to be pixel-free: `GENERATED_NORMALS=false` and the load
  log reports `[Fluid PBR] NORMAL atlas ... authored=0`, so there is no height
  data for the parallax loop to displace with. Still requires a screenshot
  comparison against the pre-change baseline.
- Water SSR stays enabled at LOW-profile quality; clouds stay volumetric at
  LOW-profile quality. Both need a look at reflections/sky on the same camera,
  time and weather. One-line revert each if the regression is visible.

## Shadow/CPU iteration (no image change by construction)

## Shadow/CPU iteration (no image change by construction)

`VintageShadowRenderer`: one `BlockPos`/`IBlockState` fetch per TESR per pass
instead of up to three, a hoisted player `ResourceLocation` constant, and a
reused visible-TESR list instead of a fresh `ArrayList` every frame. Entity
submission, frustum filtering, caster programs and state restoration untouched.

`CommonIrisRenderingPipeline.onSetShaderTexture`: skip the PBR listener
round-trip when both the texture id and the resolved holder are identical to
the previous bind. Holder identity (not just id) is compared so stitch/reload
invalidation still refreshes state. Texture, AO, lightmap and labPBR channels
are not altered.

Verification: package build, `:common-shaders:test`, standalone oil/triangle
regressions. Frame-time improvement and mod-renderer compatibility require
client verification after a full restart.

## Shadow snapshot allocation iteration

`VintageShadowState` now borrows zeroed scratch buffers from LWJGL's existing
thread-local MemoryStack instead of allocating direct native buffers every frame.
This covers current color/color mask, fixed-function texture matrices and generic
vertex attributes. Heap arrays/views and the other snapshot helpers are unchanged;
this is not a zero-allocation shadow renderer.

The render method owns the stack scope. Java resource-close order restores the
snapshot first, then releases scratch memory, including exceptional exits. No GL
queries, state restoration, shadow draws, filtering or texture notifications were
removed. No settings or shader sources were changed in this iteration.

Expected benefit is lower CPU/native allocation overhead, not lower GPU shading
cost. The supplied follow-up screenshot shows 60 FPS / 16.6 ms and GPU 99%, with
different time/lighting from the original baseline; it cannot establish a causal
FPS gain. Package compilation is checked; actual frame-time improvement and
mod-renderer compatibility still require client verification after a restart.

## Fullscreen triangle iteration

User baseline: Complementary Unbound r5.9, 55 FPS / 18.1 ms, GPU 98%,
coastal water/foliage scene. This is a screenshot, not a controlled timing capture.

`VintageFullScreenQuadRenderer` now draws one oversized triangle instead of two
triangles for the exact pack name `ComplementaryUnbound_r5.9.zip`. Its audited
post-process vertex programs use full-viewport affine position/UV mappings;
other varying values are independent of vertex position. Clipping preserves
coverage and UV derivatives, avoiding duplicate fragment helper work along the
old diagonal. Pixel count, render-target sizes, effects and sampling quality
remain unchanged. This is a small optimization, not a promised FPS multiplier.

Other packs retain the original quad. In particular, Chocapic scales geometry
into subrectangles for bloom/clouds; an oversized triangle would overwrite pixels
outside those rectangles. A renamed Complementary archive also uses the fallback.
Do not broaden the fast path without auditing the pack's vertex programs.

The private fullscreen VAO now retains its attribute layout/enables, eliminating
seven repeated GL setup/teardown calls per begin/end pair. Depth/cull/alpha-test
state preservation is unchanged, including the RGB-only final-pass alpha fix.

Verification: package build and CPU check of the actual runtime vertex data:

```
java --class-path build/libs/2.4.1-dev/pintonium-forge-1.12.2-2.4.1-dev.jar forge122/src/test/java/net/irisshaders/iris/pathways/FullscreenTriangleTest.java
```

GPU image comparison and performance measurements require the client. After a
full restart, compare the same camera, time, weather and warmed-up chunks with
the baseline. Inspect screen edges, water/SSR, bloom and the hand; also check
Chocapic's fallback. No settings were lowered or diagnostics disabled.
