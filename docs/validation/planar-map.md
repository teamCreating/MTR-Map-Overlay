# Pure 2D map rendering — 2026-10-21

Additional optimization: static/dynamic geometry is now cached independently; current 48-test/build results and the measured headless workload are documented in [planar-cache.md](planar-cache.md). The 39-test results below record the initial planar change.

Codex; main development version remains 1.5.2, MC 1.21.1 NeoForge/Fabric. User explicitly replaced underpass/height ordering with a planar map. Earlier train-height.md and underpass-complexity.md describe the superseded implementation.

## Behaviour

- Both maps share PlanarMapScene: all railway backings first, all route base colours next, route white details next, then each car body/fill/end caps. Car overlap follows paint/input order, independent of Y.
- RouteRibbonGeometry no longer reads Y or divides rails into height fragments. A car body uses four quads at any slope. High-speed pattern phase, clipping, shared-lane widths, style LOD and projected X/Z footprints are retained.
- Scene quads hold primitive X/Z coordinates, without MapVertex objects or height sort keys. Rendering emits vertices directly without per-quad List.of wrappers. The scene never sorts.
- Hover checks one body/marker target per visible car, back to front. Rail geometry is not considered an occluder. Zoomed-out marker hit areas match their rendered footprint; degenerate projected bodies are rejected.
- Native track/car XYZ, pitch/roll source transforms, tooltip coordinates, endpoint connectivity and snapshot protocol v7 remain intact. Y is data, not a layer priority. No network change or version increment.
- This does not implement global train streaming or ETA. Current nearby data coverage remains explicit.

## Verification

JDK 21: NeoForge `gradlew.bat build --no-daemon` and Fabric `fabric/gradlew.bat -p fabric build --no-daemon` passed. All 39 NeoForge tests passed, including revised planar car overlap/crossing tests, slope-independent geometry, route layers across later backings, marker picking, retained native XYZ/endpoint semantics, and the existing route-style/protocol/projection suites. Fabric retains the existing optional Xaero method-remapping warnings; runtime hook compatibility is not proven by compilation.

The production-geometry probe uses 10000 visible segments, four ordinary route lanes, and 500 visible cars. All three flat/rail-slope/car-slope cases now produce 50000 rail quads + 2000 car quads = 52000. The previous maximum was 209000. This confirms removal of height-driven multiplication, not a frame-time or heap benchmark.

```powershell
New-Item -ItemType Directory -Force build/planar-preview
javac -cp build/classes/java/main -d build/planar-preview tools/TrainPlanarPreview.java tools/HeightComplexityProbe.java
java '-Djava.awt.headless=true' -cp 'build/classes/java/main;build/planar-preview' TrainPlanarPreview build/planar-preview/train-planar.png
java -cp 'build/classes/java/main;build/planar-preview' HeightComplexityProbe
```

The shared-geometry preview was visually inspected: lower/higher/inclined cars all remain above crossing railway lines. This is a synthetic fixture, not an in-game screenshot. No Minecraft instance was started. Actual integration, streaming poses, map navigation and performance remain manual runtime checks.
