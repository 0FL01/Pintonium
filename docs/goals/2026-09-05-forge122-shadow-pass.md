# Goal: shaderpack shadows on 1.12.2

Status: blocked
Source: user request, 2026-09-05: implement full Pintonium shadow support compatible with shaderpacks.

## Objective and frozen contract

Render shaderpack shadow maps on Cleanroom 1.12.2, including terrain, entities,
the player and block entities, instead of relying on a no-shadow pack setting.
Respect pack shadow directives; preserve the main scene and no-shader rendering.

- R1: Complete shadow rendering in forge122.
  - Source: full shadow support request.
  - Acceptance: opaque/cutout terrain, entities/player and TESRs cast shadows;
    translucent handling and pre-translucent depth follow pack directives.
  - Primary evidence: build plus client scenes showing these casters and motion.
  - Status: blocked on client/GL validation; runtime implementation and build complete.
- R2: Shaderpack-compatible integration.
  - Source: compatibility with shaderpacks request; current Complementary workflow.
  - Acceptance: Complementary Unbound r5.9 and Chocapic V9 Low work with their
    shadow-enabled settings, through reload, dimension/time changes and camera
    movement, without corrupting the main pass. This is not a claim for all packs.
  - Primary evidence: client tests with pack options and logs recorded.
  - Status: blocked on paired-pack client validation.

## Constraints / non-goals

- Work in this fork; preserve unrelated working changes and Java 25 client.
- Do not change server, world, dependencies or shaderpack source to fake shadows.
- No global brightness boost, no fog restoration as part of this objective.
- Do not deploy incomplete prerequisites as completed shadow support.

## Change envelope

- `forge122/src/shaders/java/net/irisshaders/iris/shadows/`: legacy shadow renderer,
  camera/frustum and exception-safe state ownership.
- `VintageIrisRenderingPipeline`: factory, shadow entity/TESR programs and targets.
- Vintage section manager, renderer and shader mixins: separate shadow visibility,
  CPU culling, main-pass hook isolation, entity/TESR submission.
- Reuse common shadow targets, samplers, directives and matrix utilities where
  correct. Shared changes only for a demonstrated blocker to R1/R2.
- Existing nearby shader tests; bounded matrix checks; build and client validation.

## Execution directive

Complete R1/R2 inside this envelope. Work on the smallest unresolved prerequisite;
do not turn adjacent visual artifacts into new requirements. Completion requires
runtime evidence, not only compilation. Keep status active while in-scope work
remains; never describe a prerequisite-only build as functional shadows.

## Current checkpoint

User accepted the corrected client build on 2026-09-05: "Теперь всё окей" and
requested commit/push. This confirms the reported crash no longer blocks use.
It does not separately document every caster, both packs or all reload/dimension
checks; those remaining compatibility checks are not claimed as completed.

For further compatibility verification with Complementary SHADOW_QUALITY=0:
Check a solid wall, cutout leaves, player/mob, chest and translucent surface;
move/turn, change time, reload and switch dimension. Repeat with Chocapic's own
shadow-enabled configuration. Collect latest.log/patched_shaders before switching.
No available agent tool can verify the actual in-game image; user observation is
the smallest unlock for R1/R2. Do not declare completion from build/test results.

## Completed checkpoint

2026-09-05: wired the vintage section manager's shadow-list selector to the
existing matrix shadow flag; disabled player-camera CPU face culling during
shadows; guarded the main translucent/deferred hook. The flag is not yet driven
by a real shadow renderer, so this is prerequisite isolation, not working shadows.
`bash ./gradlew -Ptarget_versions=1.12.2 packageJar --offline --console=plain`
passed. No client deployment; R1/R2 remain unresolved.

## Runtime checkpoint

2026-09-05: implemented `VintageShadowRenderer` and `VintageShadowState`, factory,
shadow sampler initialization, shadow matrix uniform provider, visibility selection,
terrain/entity/player/TESR submission, pre-translucent depth copy, mipmaps/composite,
main-pass isolation and finally-based state restoration. Raw Program caster bridges
avoid the unused MCShaderInstance shadow factory API. The latter is not implemented.

`bash ./gradlew -Ptarget_versions=1.12.2 packageJar :common-shaders:test --console=plain`
passed; 8 existing shader transformer tests passed. Offline attempt was blocked by
uncached declared JUnit dependencies, resolved by the online run. These tests do
not exercise legacy GL draw/state restoration. Candidate deployment re-enables
Complementary shadows while keeping fog disabled. No server or dependency changes.

Known validation risks: extended entity vertex attributes are not universally
available, mod renderers may override GL state/programs, snapshot/TESR flushing
cost is unmeasured. Do not silently disable casters to pass visual checks.

## Client crash checkpoint

2026-09-05 16:33:07: first client entry failed in `updateShadowVisibility` because
the shadow render-list manager was null. Its allocation was incorrectly tied to
shader-enabled state at terrain-manager construction. The constructor now declares
shadow capability unconditionally, preserving section registration before shader
initialization/toggling. The same crash exposed a suppressed exception restoring
texture slot 8 through Minecraft's eight-entry texture cache. Snapshot inspection
now uses raw GL slot selection; restoration uses Minecraft caching only for fixed
function units and raw GL for shader-only slots. `packageJar --offline` passed;
the user subsequently reported the corrected build working. Complete paired-pack
R1/R2 coverage remains undocumented.

## Baseline evidence

- Before this implementation, `VintageIrisRenderingPipeline.createShadowRenderer()` returned null.
- `forge1710` shadow renderer is also a stub; modern implementation is a reference,
  not a drop-in legacy renderer.
- Base section manager already has separate shadow render-list selection, but
  forge122 currently inherits `isInShadowPass() == false`.
- Previous client used no-shadow Complementary compatibility settings.
