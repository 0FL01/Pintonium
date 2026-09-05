# Forge122 shader performance

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
