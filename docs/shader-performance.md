# Forge122 shader performance

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
