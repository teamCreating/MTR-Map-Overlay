# Create-style planar geometry caching — 2026-10-21

Codex; main stays 1.5.2, MC 1.21.1 NeoForge/Fabric. Implements the static/dynamic separation identified in the Create source study. Rendering remains pure 2D.

## Implementation

- PlanarMapScene now builds immutable static geometry separately from car bodies/markers. Its combined list is a read-only view over both layers, so a new car revision does not copy all static quad references into another scene.
- Each map host owns a PlanarMapCache. Published DimensionData object identity is the source revision key; scale, track/route flags and viewport coverage are also checked. A new snapshot invalidates even if a numeric version/hash collides. Input snapshots and pose lists must be replaced on update, not mutated in place.
- Static cache bounds extend each axis by the larger of 15% of the viewport or 64 screen pixels in world units. Small pans reuse geometry; leaving this guard rebuilds. Exact scale changes rebuild, preserving line widths and high-speed LOD/phase. Invalid transforms return an empty scene instead of stale picking.
- The native collector already publishes immutable nearby poses with a 50ms capture cache. The scene cache reuses dynamic geometry while that pose-list identity is unchanged; a new pose list rebuilds cars and hover targets while retaining static geometry.
- Default retention limit is 100000 total quads per host, including car geometry. Dense over-budget views still render correctly without retention; this limit is not a peak-allocation or per-frame rendering cap. Static and dynamic sources use weak identity references, avoiding retention of an obsolete whole-network snapshot or every offscreen car solely for cache-key comparison.
- MapDataCache.clearClientData clears both host caches on existing login/logout paths, without resolving optional map classes. Changing map data/dimension also switches snapshot identity. Hidden maps do not call the cache or build geometry; bounded caches may remain for reopening until invalidation/connection cleanup.
- Car bounds checks no longer construct a temporary four-corner list.

This is a CPU geometry cache, not a port of Create's DynamicTexture renderer or a persistent GPU mesh. Vertex submission and GPU drawing still happen each rendered frame. Xaero route/stop hover keeps its existing search path; the car hit list remains separate. No global dynamic train stream, new interpolation algorithm, ETA, protocol or version change is included.

## Correctness checks

48 NeoForge tests passed; NeoForge and Fabric builds passed on JDK 21. New tests verify repeated frames/small pans reuse the same scene, moving cars update drawing/hover without changing static quad identity, data/scale/settings/guard invalidation, high-speed pattern consistency after panning, static and dynamic retention limits with correct uncached output, read-only layer composition, invalid-transform handling, and shared connection cleanup.

Existing pure-2D layers, route styles, XYZ snapshots, nearby car geometry and projection tests pass. Fabric still reports the existing optional Xaero method-remapping warnings. No Minecraft instance was started; compilation does not verify runtime hooks.

## Headless geometry workload

Tool: `tools/PlanarCacheProbe.java`. CPU: Intel Core Ultra 5 225H, Windows, JDK 21.0.11, max heap 768MiB. One recorded run after 12 warmup iterations of both paths; not a JMH benchmark or statistically sampled game performance result.

Inputs: 10000 visible rail segments, four ordinary route lanes, 500 visible cars, 52000 quads per scene, 120 simulated render frames. Forty immutable pose revisions are each reused for three frames (20Hz poses / 60 render frames). Map source/scale/viewport remain stable. Cached measurement includes its first static build. Cold uncached and cached paths use the same current geometry code.

| Mode | Static builds | Car builds | Total geometry time for 120 frames | Current-thread allocated bytes |
| --- | ---: | ---: | ---: | ---: |
| Uncached | 120 | 120 | 231.827ms | 673.835MiB |
| Cached | 1 | 40 | 3.207ms | 13.665MiB |

Allocations were read using the JDK ThreadMXBean counter, not inferred from object layout; they are allocations over the workload, not live heap occupancy. Roughly 98% lower allocation in this geometry workload does not imply a similar FPS increase. It excludes Core pose collection, network, vertex submission, GPU, map UI, text and hover. Frequent source/zoom changes or oversized views reduce the benefit; a stable server-synced snapshot is the favourable case. Timings vary with JIT/GC/machine load.

```powershell
New-Item -ItemType Directory -Force build/cache-probe
javac -cp build/classes/java/main -d build/cache-probe tools/PlanarCacheProbe.java
java '-Xmx768m' -cp 'build/classes/java/main;build/cache-probe' PlanarCacheProbe
```

Runtime checks still needed: continuous pan/zoom, map/data/dimension switches, toggles, crossing styles, moving train hover and frame/GC profiles in an actual populated world. No total-frame or GPU performance claim is made.
