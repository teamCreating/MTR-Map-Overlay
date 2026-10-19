# True XYZ and nearby car drawing — 2026-10-20

Scope: main 1.5.2 development, MC 1.21.1, MTR 4.1.0-beta.2, NeoForge/Fabric. No ETA, no global dynamic train feed, no change to the MC 1.20 worktree.

## Data and geometry

- Read-only nearby-car collector runs only when a fullscreen map requests it, with a shared 50ms capture cache. World/connection changes clear it. Empty paths, invalid bogies, non-positive dimensions and degenerate bodies are rejected.
- Read `Vehicle.getVehicleCarsAndPositions()` from MTR's client vehicle set. Use MTR `PositionAndRotation` for bogie orientation, exact car-body centre/bogie-offset math and rigid-body corner transforms. Preserve world XYZ, pitch and roll; do not infer Y from a route line or mutate VehicleExtension's smoothing state.
- `MapCar` stores measured corners and service metadata. Tooltips show the actual transformed body centre XYZ, route, destination, model ID and car index, labelled as nearby data. No ETA is fabricated.
- TrackSampler retains native railMath X/Y/Z. Internally legacy planar indices remain [x,z,y]; v7 wire order is conventional XYZ with doubles. Missing legacy Y stays NaN, not an invented zero. Infinite coordinates are rejected. Native endpoints differing in Y are not joined by the static stripe-layout builder.
- Versioned `_v7` snapshot channels and NeoForge registrar 7 prevent v6 decoders from misreading the larger XYZ point records. Full-network static geometry needs matching versions; nearby cars do not require this server component.
- Xaero converts camera-relative coordinates in doubles before writing screen-space floats, preserving distant-world geometry precision.

## Height ordering and limits

Both maps use the same HeightMapScene, ordered by measured world height before feature type. Railway backing is opaque so a higher crossing masks lower cars; picking follows the visible primitive order. Car detail is subdivided on slopes, and route colour/white blocks retain their parent fragment's height key to avoid their own backing covering them.

This is a bounded painter algorithm, not per-pixel 3D depth: longitudinal height spans are split at approximately 0.5 block (capped at 256 pieces per source segment/body). Very close/intersecting tilted surfaces and non-planar model geometry still need in-game evaluation. Body roof height is not a full 3D model envelope. No terrain, buildings, bridge decks or tunnel ceilings are sampled as occluders, and no cave-layer slice filtering is claimed. Icons remain map UI on top.

Zoomed-out cars collapse to one small marker per train; large-scale car bodies retain real footprint with pixel-sized side/end detail. The player dimension and MTR's normal nearby streaming bound coverage.

## Verification

JDK 21 commands:

```powershell
.\gradlew.bat build --no-daemon
.\fabric\gradlew.bat -p fabric build --no-daemon
```

37 NeoForge tests passed. New checks cover native XYZ sampling on a below-zero slope, double-precision distant XYZ snapshot round-trip, upper/lower car order independent of input order, railway masking of lower cars and hover, local height on an inclined body, vertically separated but coincident X/Z endpoints, high-speed white block/backing order on slopes, unknown height handling and invalid-car rejection. Existing route style, pathfinding, palette, bounds, projection and chunk checks pass.

Fabric compiles and remaps. The existing optional Xaero method-remapping warnings remain; compilation does not verify the runtime hook.

Headless preview uses the actual shared geometry and synthetic XYZ fixtures, not screenshots or recorded live vehicle data:

```powershell
New-Item -ItemType Directory -Force build/train-height-preview
javac -cp build/classes/java/main -d build/train-height-preview tools/TrainHeightPreview.java
java '-Djava.awt.headless=true' -cp 'build/classes/java/main;build/train-height-preview' TrainHeightPreview build/train-height-preview/train-height.png
```

Preview checked for underpass, overpass and two local crossings of an inclined body. Java2D polygon antialiasing is disabled to match typical non-MSAA Minecraft GUI colour-quad rasterization; text remains antialiased.

## Manual checks remaining

No Minecraft instance was started. Verify actual MTR body alignment, pitched/tilted and single-bogie cars, reverse running, dynamic streaming/connection cleanup, visible and hidden hover targets, both map projections, map switches, dimension changes and cave modes. Check that unsupported/v6 servers leave normal client fallback usable. No performance benchmark or exact terrain occlusion was performed.
