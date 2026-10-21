# Route type styling validation — 2026-10-18

Update, Codex, 2026-10-21 17:30: high speed now uses standard rail width (1.0), retaining the block pattern; light rail remains 0.7. Current zoom/join behaviour and validation are in [route-joins-zoom.md](route-joins-zoom.md). The original results below are historical.

Scope: main, MC 1.21.1, MTR 4.1.0-beta.2, NeoForge and Fabric. Version 1.5.2 development; no release/tag created. MC 1.20 compatibility branch unchanged.

## Behaviour

- Core `NORMAL`, `LIGHT_RAIL`, `HIGH_SPEED` survive collection, snapshot v6 and per-rail palette creation. Live-client fallback uses the vehicle's route ID/type; unavailable types default to normal.
- Width weights: normal 1.0, light rail 0.7, high speed 1.4. At normal map scales, up to four routes retain individually readable lanes; more routes share a capped total width.
- High-speed lanes contain white blocks with continuous coloured sides. Nominal block length is 16 world blocks; draw only when blocks occupy at least 3 pixels and a lane at least 2.8 pixels. This prevents subpixel flicker in dense bundles or at low zoom.
- Build direction and cumulative phase once per snapshot along degree-two physical rail chains. Branch junctions start new chains; closed chains adjust block length to fit whole pairs. Mitered sample joins keep neighbouring lanes aligned. Both map integrations consume identical world-space quads.
- Versioned `_v6` channel names prevent old Fabric payload decoders from receiving a new snapshot layout. NeoForge protocol registration is also v6. Old/new mixed-version full-network sync is not supported.

## Automated checks

Commands (JDK 21):

```powershell
.\gradlew.bat build --no-daemon
.\fabric\gradlew.bat -p fabric build --no-daemon
```

NeoForge: 29 tests passed. Includes route style round-trip into the decoded palette, 70/100/140 mixed lane widths and boundaries, white block containment, split/reversed rail phase, deterministic closed loops, sample joins, zoom/density simplification and viewport-bounded work on a 20-million-block rail. Existing route, track, projection, chunk and pathfinding checks passed.

Fabric: build/remapJar passed. Loom retains the existing optional Xaero method-remapping warnings; Minecraft runtime mapping is not verified by compilation.

## Headless visual check

This uses the same `RouteRibbonGeometry` as the two renderers, not a separately drawn mockup:

```powershell
javac -cp build/classes/java/main -d build/style-preview tools/RouteStylePreview.java
java '-Djava.awt.headless=true' -cp 'build/classes/java/main;build/style-preview' RouteStylePreview build/style-preview/route-styles.png
```

Create `build/style-preview` before compiling. The preview covers individual types, green/red high-speed blocks, mixed shared lanes, connected curved rails stored in opposite directions and zoom simplification. Preview checked visually; the white blocks stay within the coloured high-speed lane.

## Remaining manual verification

No Minecraft client/server was started. Check Xaero and JourneyMap fullscreen maps in a world with all three route types, shared rails and junctions; pan and zoom on light/dark terrain; change a route type and wait for a hash refresh; check matching v6 client/server sync and unsupported-server fallback.
