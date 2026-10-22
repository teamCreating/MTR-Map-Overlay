<div align="center">
  <img src="src/main/resources/pack.png" alt="MTR Map Overlay logo" width="128" height="128">

  <h1>MTR Map Overlay</h1>

  <p>Minecraft Transit Railway routes, rails and stations on your map.</p>

  <p><a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="https://github.com/teamCreating/MTR-Map-Overlay/tree/compat/mtr-4.0-mc-1.20">MC 1.20 compatibility branch</a></p>
</div>

MTR Map Overlay is a Minecraft 1.20.1 / 1.20.4 add-on for **Forge or Fabric**. It reads [Minecraft Transit Railway (MTR)](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) data and integrates with Xaero's World Map and JourneyMap. It does not depend on MTR Surveyor's map.

## Features

| Map | Overlay |
| --- | --- |
| Xaero's World Map | Physical rail geometry, route-coloured ribbons, and compact station, platform and depot icons. Hover to inspect routes and landmarks. |
| JourneyMap | Physical rails, shared-route colour bands, and station, platform and depot icons on the fullscreen map, with separate TRACKS and ROUTES controls. |

**1.5.2 development:** latest main features are now ported: native curves and joins, light rail at 70% width, rail/high speed at equal width, outlined colour/white high-speed blocks, and server-authoritative whole-map trains with abstract carriages and zoom detail. Both maps share train hover and viewport filtering. See [six-platform validation](docs/validation/platform-matrix-1.5.2.md). ETA/next-stop distance remains deferred.

These are **map-only icons**, not ordinary Xaero waypoints: they do not fill the waypoint list, compass, minimap or in-world HUD. Multiple routes on one physical rail occupy adjacent colour bands rather than overwriting one another. Both map integrations draw physical rails first, coloured routes on top, and station/platform icons last. In JourneyMap, the entire layer follows the map's live drag and zoom transform; track width follows the same world-scale zoom rules as Xaero, with one minimum width for the whole shared bundle.

The [v1.5.1 GitHub release](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.1) also carries four MC 1.20 compatibility JARs built as mod version **1.5.1**. Their filenames state the exact Minecraft target, MTR range and loader; the original v1.5.1 assets remain MC 1.21.1.

When the mod is installed on the server as well as the client, it can request a **whole-network snapshot** for each dimension. Without the server component it still works, but can show only the nearby data MTR has sent to the client. Xaero and JourneyMap are optional integrations; install either or both.

## Requirements and installation

| Component | Requirement |
| --- | --- |
| Minecraft | 1.20.1 or 1.20.4, Java 17 or newer; choose the exact version JAR |
| Mod loader | Forge 47.x (1.20.1) / 49.x (1.20.4), **or** Fabric Loader with the matching Fabric API |
| MTR | 4.0.5 (latest stable 4.0.x checked on 2026-09-30), built for the same loader |
| Map mod | Xaero's World Map 1.45.0+; JourneyMap 6.0.6+ (1.20.1) or 5.10.0 (1.20.4), for the same MC and loader |
| Xaero's Minimap | Optional; used only to remove old `[MTR]` waypoints created by earlier releases |

1. Build or download the **Forge** or **Fabric** JAR for your exact Minecraft version (1.20.1 / 1.20.4) for this branch. Install one matching loader build in the client's `mods` directory. This branch now ports the latest main features and uses version **1.5.2**; the published 1.5.1 assets remain unchanged. The `main` branch continues to target NeoForge/Fabric MC 1.21.1.
2. Install MTR and your chosen map mod for that same loader. Fabric additionally needs Fabric API.
3. Optionally install the matching MTR Map Overlay JAR and MTR on the server to enable the whole-network view. Client and server must use the `mtrmap` mod ID; older `mtrsurveyor` builds are not compatible with this release.
4. Open Xaero's World Map or JourneyMap's fullscreen map. Both maps have matching `ROUTES` and `TRACKS` icons (green left bar = on, red = off); JourneyMap puts them in its add-on button panel. The `/mtrmap config routeLines` and `trackLines` switches apply to both maps. Hover over a line, icon or train for details. Whole-map trains require dynamic-v1 support on both client and server; a client-only install falls back to nearby MTR data.

The server component is optional. This branch provides separate JARs for **MC 1.20.1 and 1.20.4**, each requiring its exact Minecraft version and MTR >=4.0.5, <4.1. Forge and Fabric share static-v8 snapshot and independent dynamic-v1 train encoding, with separate loader transports; cross-loader connections are not claimed. Local development-runtime checks are documented in [MC 1.20 runtime QA](docs/validation/mc120-runtime.md); production-JAR launch and cross-machine play are outside that check.

## Commands and configuration

Commands are registered on the **client**, so they are available even when the server does not run this mod.

| Command | Purpose |
| --- | --- |
| `/mtrmap syncRoutes` | Request a whole-network snapshot, if the server supports it. |
| `/mtrmap syncLandmarks` | Refresh JourneyMap landmarks. |
| `/mtrmap testMarker` | Place a JourneyMap diagnostic marker at the player. |
| `/mtrmap mode station\|platform\|both` | Select the client-data fallback landmark mode. |
| `/mtrmap config enabled <true\|false>` | Enable or disable the map overlay. |
| `/mtrmap config showStations <true\|false>` | Show or hide station icons. |
| `/mtrmap config showPlatforms <true\|false>` | Show or hide platform icons. |
| `/mtrmap config showDepots <true\|false>` | Show or hide depot icons. |
| `/mtrmap config routeLines <true\|false>` | Show or hide route ribbons on both maps. |
| `/mtrmap config trackLines <true\|false>` | Show or hide physical rails on both maps. |
| `/mtrmap config trainCars <true\|false>` | Show or hide carriages on both maps. |

Forge stores settings in `config/mtrmap.toml` and copies an existing `mtrsurveyor.toml` on first launch when the new file is absent. Fabric uses `config/mtrmap.properties` with its own defaults. The two formats are not automatically interchangeable. Important options include `networkSync.enabled` (default `true`), `networkSync.refreshIntervalSeconds` (default `300`), and the station/platform/depot visibility switches.

## Build from source

Run Gradle with JDK 21. Both builds compile mod classes for Java 17 (both Minecraft targets). The loader builds have separate Gradle wrappers because they use different build plugins:

| Loader | Windows | macOS / Linux | Output |
| --- | --- | --- | --- |
| Forge 1.20.1 | `.\gradlew.bat build` | `./gradlew build` | `build/libs/CRTools-MTR-Map-Overlay-forge-mc1.20.1-1.5.2.jar` |
| Fabric 1.20.1 | `.\fabric\gradlew.bat -p fabric build` | `./fabric/gradlew -p fabric build` | `fabric/build/libs/CRTools-MTR-Map-Overlay-fabric-mc1.20.1-1.5.2.jar` |
| Forge 1.20.4 | `.\gradlew.bat build "-Pminecraft_version=1.20.4"` | `./gradlew build -Pminecraft_version=1.20.4` | `build/mc1.20.4/libs/CRTools-MTR-Map-Overlay-forge-mc1.20.4-1.5.2.jar` |
| Fabric 1.20.4 | `.\fabric\gradlew.bat -p fabric build "-Pminecraft_version=1.20.4"` | `./fabric/gradlew -p fabric build -Pminecraft_version=1.20.4` | `fabric/build/mc1.20.4/libs/CRTools-MTR-Map-Overlay-fabric-mc1.20.4-1.5.2.jar` |

All four targets run the shared JUnit tests. Check the packaged JARs with `python tools/verify_artifacts.py` and `python tools/verify_artifacts.py --minecraft 1.20.4`. The test-world generator is opt-in and is skipped during ordinary builds. Forge uses Gradle 8.8 / ForgeGradle 6.0.54; Fabric uses Gradle 9.5.0 / Loom 1.17.21. The bundled JourneyMap API is extracted and remapped for compilation only; it is not shipped inside this mod. A successful build does not replace an in-game compatibility check, especially when Xaero's internal map renderer changes.

## Isolated runtime checks

Use `-PmtrmapRuntimeTest=true` with `runClient` / `runServer` to load matching MTR and map dependencies for local QA. Add `-PmtrmapRunDir=<absolute test directory>` to isolate saves and settings. Servers also use `-PmtrmapServerOnly=true`; clients can use `-PquickplayServer=127.0.0.1:<port>` (Forge 1.20.1 should join from the normal server list first). Select Minecraft and use the loader's own wrapper as in the build table above. See [runtime evidence and limitations](docs/validation/mc120-runtime.md).

## Source guide

The Forge sources are under [`src/main/java/com/lx862/mtrmap`](src/main/java/com/lx862/mtrmap); [`fabric/`](fabric) contains Fabric-specific entry points and adapters and compiles the shared Java sources. Forge keeps common initialization in `MTRMap` and physical-client events in `MTRMapClient`; `MTRNetworkClient` holds client-only transport callbacks. The two builds share textures, the 128×128 mod/pack logo, and the same `mtrmap` identity.

| Area | Main responsibility |
| --- | --- |
| [`mapdata/`](src/main/java/com/lx862/mtrmap/mapdata) | `MapDataCache` selects server snapshots or nearby MTR client data. `TrackSampler` samples each physical rail, reused by route paths; `TrackRoutePalette` assigns stable colour bands and `MapTrack` caches bounds for viewport culling. |
| [`network/`](src/main/java/com/lx862/mtrmap/network) | Protocol-v5 payloads and `NetworkSnapshotCodec` transfer routes, tracks and landmarks. `ServerNetworkCollector` reads MTR simulators on their own threads; `ClientNetworkSync` probes and requests snapshots, while `NetworkChunkAssembler` validates and reassembles chunks. |
| [`integration/xaero/`](src/main/java/com/lx862/mtrmap/integration/xaero) | `XaeroRouteRenderer` draws tracks, route ribbons and map-only icons in world-map coordinates and handles hover tooltips. |
| [`integration/journeymap/`](src/main/java/com/lx862/mtrmap/integration/journeymap) | Optional JourneyMap v2 plugin for 1.20.1; JourneyMap 5 adapters for 1.20.4. `JourneyMapToolbar` supplies TRACKS/ROUTES buttons; `JourneyMapScreenProjection` follows pan/drag/zoom; `JourneyMapPathManager` draws viewport-culled pixel-width track and route quads; `JourneyMapForegroundRenderer` keeps landmark icons above both layers. Fullscreen `MarkerOverlay` objects retain hover information. |
| [`mixin/`](src/main/java/com/lx862/mtrmap/mixin) | Access to MTR data and the Xaero render hook; Fabric supplies its own Xaero hook variant. |
| [`config/`](src/main/java/com/lx862/mtrmap/config) and [`fabric/src/main/java/`](fabric/src/main/java) | Loader-specific configuration, initialization, client commands and network registration. |

Data flow: MTR simulator/client data → dimension-specific `MapDataCache` → Xaero or JourneyMap fullscreen renderer. With a modded server, the client first probes dimension hashes, requests changed snapshots, validates and reassembles chunked payloads, then updates the cache. Without one, the cache falls back to MTR's radius-limited client data.

### Version-specific adapters

`compat/mc1204/` supplies Forge 49 networking, JourneyMap 5 plugin/events/toolbar, and an optional mixin after JourneyMap's `drawMap` method. The 1.20.4 foreground projection uses JourneyMap 5's grid renderer and active mouse drag; it runs before map controls are drawn. `gradle/minecraft-target.gradle` generates the two JourneyMap 5 data/presence adapters from shared source, translating only API namespaces, explicit marker IDs and UI enum sets. The common marker filters, MTR data and snapshot protocol remain shared. Fabric API is 0.92.6+1.20.1 or 0.97.3+1.20.4. Each version has a separate build directory.

## Troubleshooting

- **Only nearby stations appear:** the server has not supplied a whole-network snapshot. Install the matching mod on the server, or use the client-only fallback as intended.
- **No Xaero lines:** check that Xaero's **World Map** is installed and that the log contains `Path layer render hook into Xaero's World Map is active`. Xaero internal changes can break the render hook.
- **No minimap waypoints:** expected. Landmarks are intentionally fullscreen-map overlays.
- **Migrating from an older build:** replace the old `mtrsurveyor` JAR rather than installing it beside this one; the mod ID and command are now `mtrmap` and `/mtrmap`.

## License and attribution

The project is MIT-licensed. The original copyright and license notice for AmberFrost's contributions remains in [`LICENSE`](LICENSE); later work is maintained by BenLi06. The existing Git commit history and attribution are preserved.
