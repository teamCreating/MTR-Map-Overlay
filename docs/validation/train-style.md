# Carriage visibility — 2026-10-21

Codex, 14:08. MTR Map Overlay remains **1.5.2**, pure 2D, static protocol v8 unchanged.

## Change and reference

Create's `TrainMapManager.drawTrains` at pinned commit `7ff2760e4924d35d4bc2e22c4b19923f0178cd16` draws carriage sprites with distinct body/end pieces and orientation; see [source](https://github.com/Creators-of-Create/Create/blob/7ff2760e4924d35d4bc2e22c4b19923f0178cd16/src/main/java/com/simibubi/create/compat/trainmap/TrainMapManager.java#L312). We use that visual separation, with original procedural geometry rather than copied textures or quantized sprite headings.

- Previous cars used their projected physical width, a same-route-colour fill and thin dark caps. Narrow cars disappeared into their route. The new silhouette has a seven-GUI-pixel minimum width, chamfered white halo, dark border, route-colour sides, cream roof and dark end windows.
- Native front/back centres determine continuous orientation. Measured XYZ, car length and tooltips remain data; only symbol width, border and details are exaggerated. Ends are inset 0.7 GUI pixels to maintain carriage separation.
- A gold arrow marks the leading car's correct end. `MapCar.reversed` comes directly from MTR `Vehicle.getReversed`, including single-car reverse operation. It is client data, not a new wire field.
- Below nine GUI pixels of projected car length, only the leading car emits a 12×7 GUI-pixel oriented train symbol. Its centre remains at that car's measured centre; it does not represent the exact whole-train footprint.
- Each symbol has one enclosing rectangular hover target, including the halo. This includes the tiny empty chamfer corners. Viewport culling allows eight GUI pixels of extra symbol extent.

## Cost

Full-size trailing cars emit eight quads, leading cars nine, versus four previously. A three-car train emits 25 quads / 100 vertices versus 12 / 48. A distant train emits nine quads versus the previous three-quad square. This is a bounded constant increase in dynamic geometry, not an FPS measurement.

Collection and scene generation remain linear in input cars; drawing is linear in visible quads, hover linear in visible symbols. No per-car image loading, route tessellation, height sorting or network traffic is added. Existing static geometry sharing and the total retained-quad budget remain active. Actual game frame-time scaling on large fleets has not been measured.

## Validation

- NeoForge and Fabric MC 1.21.1 builds pass. 65 ordinary NeoForge tests pass; the opt-in world generator is skipped (66 total). Existing Fabric optional Xaero method-remapping warnings remain.
- Tests cover minimum visible width, contrasting roof, measured coordinates retained, reverse head placement, distant heading/one-symbol LOD, edge culling, end gap and dynamic cache budget. Previous crossing/layer/native-curve/protocol tests continue to pass.
- Actual NeoForge TestWorld, Xaero: at displayed 3.0×, all three cars appear on the green curve with distinct outlines/roofs and gold leading end. At 1.403×, one oriented train marker remains readable. Actual JourneyMap: marker visible on the same green route, hover shows Local → Charlie, car 3/3 and measured XYZ. Both hosts use the same geometry.
- Runtime collection logs report one train / three cars, zero path failures and changing positions. An existing MTR transparent-block model error occurs during startup; no train renderer exception was observed. Fabric runtime has not been exercised.
- Artifacts: `../test-builds/v1.5.2-train-style/` relative to repository root: both JARs, SHA-256 list, production-geometry synthetic preview, actual screenshots and runtime log. Preview tool: `tools/TrainStylePreview.java`.

Global train streaming and PIDS ETA remain outstanding work; this change only updates the current train rendering style.
