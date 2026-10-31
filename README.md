<div align="center">
  <img src="src/main/resources/pack.png" alt="MTR Map Overlay logo" width="128" height="128">

  <h1>MTR Map Overlay</h1>

  <p><a href="https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/snapshot">Snapshot development builds (not manually validated)</a></p>

  <p>Minecraft Transit Railway routes, live trains and station arrivals on your map.</p>

  <p><a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="https://mtrmapoverlay.benli06.site/en/">Website &amp; docs</a> · <a href="https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3">Try v1.5.3 pre-release</a></p>

  <p>
    <a href="https://modrinth.com/project/ZU7SzyH7"><img src="docs/assets/badges/modrinth-cozy.svg" alt="Available on Modrinth" height="56"></a>
    <a href="https://www.curseforge.com/minecraft/mc-mods/mtr-map-overlay"><img src="docs/assets/badges/curseforge-cozy.svg" alt="Available on CurseForge" height="56"></a>
  </p>
</div>

MTR Map Overlay adds Minecraft Transit Railway routes, physical rails, live trains, native station arrivals and map-only landmarks to Xaero's World Map and JourneyMap. **v1.5.3 supports Minecraft 1.20.1 and 1.20.4 on Forge or Fabric, and 1.21.1 on NeoForge or Fabric.** It reads [Minecraft Transit Railway (MTR)](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) data directly and does not depend on MTR Surveyor's map.

## Features

| Map | Overlay |
| --- | --- |
| Xaero's World Map | Physical rails, route ribbons, live trains, station/platform/depot icons, and pinned station cards with native arrivals. |
| JourneyMap | Fullscreen rails, shared-route bands, live trains and native station boards; separate TRACKS and ROUTES controls, with JourneyMap 5/6 adapters. |

These are **map-only icons**, not ordinary Xaero waypoints: they do not fill the waypoint list, compass, minimap or in-world HUD. Multiple routes on one physical rail occupy adjacent colour bands rather than overwriting one another. Both map integrations draw physical rails first, coloured routes on top, and station/platform icons last. In JourneyMap, the entire layer follows the map's live drag and zoom transform; track width follows the same zoom rules as Xaero, with a minimum width for the combined shared-route bundle.

When the mod is installed on the server as well as the client, it can request a **whole-network snapshot** for each dimension. Without the server component it still works, but can show only the nearby data MTR has sent to the client. Xaero and JourneyMap are optional integrations; install either or both.

## What's new in 1.5.3

- **Pinned stations and native arrivals:** click a station, then **Expand** to see upcoming services by destination, line and platform. Predictions come directly from MTR/TSC's PIDS interface; missing predictions are shown explicitly.
- **British and European PIDS:** use the default black/amber board or switch to European/airport styling with `/mtrmap config pidsStyle british|european`. The choice is saved.
- **Navigation extensions:** a public Java 17 API exposes native network catalogs, station-board subscriptions and independent map views. Registered third-party providers enable **Navigate**; they supply route planning and journey progress. Highlight upcoming sections and separately dim completed or unused track, including high-speed white blocks and rail backing.
- **Smoother interaction:** station cards retain their classic information, stay collapsed until expanded and keep clicks/scrolling inside the panel. Shared arrivals, bounded queues and background decoding reduce repeated work.

**Release status (2026-10-31):** GitHub currently marks [v1.5.3](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3) as a **pre-release**. [v1.5.2](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.2) is the latest stable release. All six 1.5.3 builds include the features above; native arrivals and whole-map trains require matching client/server versions.

### Station boards and the navigation API

Click a station to pin the classic station/platform/route card, then **Expand** to request native arrivals. **Collapse**, closing the map or disconnecting releases the subscription. Rows show destination, line, platform and a right-aligned countdown; a missing prediction is a dash, not zero minutes. Theme changes apply immediately and persist across restarts.

**Navigate requires a compatible third-party provider.** One provider opens directly; multiple providers show a chooser; without one the action is disabled. Overlay does not bundle a planner. Independent view sessions can highlight exact native path sections without modifying the shared network data. The [public API guide](docs/api/overlay-api-v1.md) covers `MtrMapApi`, Java 17 `compileOnly` usage and lifecycle rules. The separate API artifact is built from source; it is not listed among the release's six mod JARs.

### Route styles and whole-map trains

Light rail uses 70% of standard width. Rail and high speed share standard width; high speed uses alternating route-colour/white blocks with a continuous coloured outline. Adaptive native curves, shared-route joins and zoom-responsive widths keep both map integrations aligned. These features were added in 1.5.2 and remain in 1.5.3.

### Whole-network train cars (1.5.2)

Install the matching 1.5.3 build on both server and client for all live trains in the map's viewed dimension. Opening either map subscribes to the entire dimension at a target 4 Hz; panning and zooming never restrict which trains are synchronized. The client only interpolates TSC reference points and draws abstract carriages, without banking or a second simulation. Hover shows the available model name, current route, destination and consist size; missing native metadata is shown as unavailable. Use `/mtrmap config trainCars false` to hide trains (default: enabled).

The map status distinguishes whole-network, loading, stale and nearby-only fallback data. Two XYZ points cost 24 bytes per car plus a shared double-precision origin per train; strings and dimensions are not repeated per car in position frames. A server without the new optional train protocol uses MTR's nearby data only. Xaero keeps singleplayer running while the live train layer is enabled, so the authoritative simulation can update. See [global train validation](docs/validation/global-trains.md) for evidence and remaining performance limits.

Tracks retain native Y and static protocol-v8 snapshots transfer double-precision XYZ and native curves. The independent train protocol is v1; the mod version is 1.5.3. Both maps draw fixed 2D layers: railway backing, route colours/blocks, then cars. Height does not split or sort geometry; railways never mask cars or their hover targets.

Static rail/route geometry is cached separately from car poses, with viewport prefetch, invalidation and bounded retention. See [cache validation](docs/validation/planar-cache.md) for correctness checks and the headless geometry workload; it is not an in-game FPS measurement.

## Requirements and installation

| Minecraft | Loader | Java | MTR | Optional maps |
| --- | --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.x or Fabric + Fabric API | 21 | 4.1.0-beta.2 | Xaero's World Map 1.45.0+ / JourneyMap 6.0.8+ |
| 1.20.1 | Forge 47.x or Fabric + Fabric API | 17+ | >=4.0.5, <4.1 | Xaero's World Map 1.45.0+ / JourneyMap 6.0.6+ |
| 1.20.4 | Forge 49.x or Fabric + Fabric API | 17+ | >=4.0.5, <4.1 | Xaero's World Map 1.45.0+ / JourneyMap 5.10.0 |

Every dependency must match your Minecraft version and loader. Xaero's Minimap is optional and is only used to remove old `[MTR]` waypoints created by earlier releases.

1. Download **one JAR** matching your exact Minecraft version and loader from the [v1.5.3 pre-release](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3), and place it in the client's `mods` directory. The release has six JARs: two `MC1.21.1` NeoForge/Fabric builds and four `MC1.20.1` / `MC1.20.4` Forge/Fabric builds. All filenames also identify the tested MTR version.
2. Install MTR and your chosen map mod for that same loader. Fabric additionally needs Fabric API.
3. Optionally install the matching MTR Map Overlay JAR and MTR on the server to enable the whole-network view. Client and server must use the `mtrmap` mod ID; older `mtrsurveyor` builds are not compatible with this release.
4. Open Xaero's World Map or JourneyMap's fullscreen map. Both maps have matching `ROUTES` and `TRACKS` icons (green left bar = on, red = off); JourneyMap puts them in its add-on button panel. The `/mtrmap config routeLines` and `trackLines` switches apply to both maps. Hover over a line or icon for details.

Whole-map trains require matching 1.5.3 builds on both client and server; client-only installs use nearby MTR data. All six builds passed Xaero/JourneyMap development-runtime checks; see [1.5.3 PIDS validation](docs/validation/pids-and-dimmed-1.5.3.md) for the recorded development-runtime checks and remaining limits. Real navigation-provider end-to-end integration, production modpack launch, cross-machine play and large-network performance are not covered.

## Commands and configuration

See the station-board section above for mouse controls and the [API guide](docs/api/overlay-api-v1.md) for developer integration.

**Known limit:** Forge 1.20.4 singleplayer quick-play disconnected during Xaero server-data initialization. Separate-server PIDS tests passed; singleplayer in that combination remains unverified. See [1.5.3 validation](docs/validation/pids-and-dimmed-1.5.3.md) and [release notes](docs/releases/v1.5.3.md).

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
| `/mtrmap config trainCars <true\|false>` | Show or hide the train layer on both maps. |
| `/mtrmap config pidsStyle british\|european` | Switch and save the PIDS theme (default British); omit the style to query it. |

NeoForge stores settings in `config/mtrmap.toml` and copies an existing `mtrsurveyor.toml` on first launch when the new file is absent. Fabric uses `config/mtrmap.properties` with its own defaults. The two formats are not automatically interchangeable. Important options include `networkSync.enabled` (default `true`), `networkSync.refreshIntervalSeconds` (default `300`), and the station/platform/depot visibility switches.

## Build from source

Use a Java 21 toolchain. The loader builds have separate Gradle wrappers because they use different build plugins:

The commands below build the **1.21.1 main branch**. Forge/Fabric 1.20.1 and 1.20.4 sources and build instructions are on the [MC 1.20 compatibility branch](https://github.com/teamCreating/MTR-Map-Overlay/tree/compat/mtr-4.0-mc-1.20).

| Loader | Windows | macOS / Linux | Output |
| --- | --- | --- | --- |
| NeoForge | `.\gradlew.bat build` | `./gradlew build` | `build/libs/CRTools-MTR-Map-Overlay-1.5.3.jar` |
| Fabric | `.\fabric\gradlew.bat -p fabric build` | `./fabric/gradlew -p fabric build` | `fabric/build/libs/CRTools-MTR-Map-Overlay-fabric-1.5.3.jar` |

The NeoForge build runs the shared JUnit tests. A successful build does not replace an in-game compatibility check, especially when Xaero's internal map renderer changes.

## Source guide

The NeoForge sources are under [`src/main/java/io/github/teamcreating/mtrmap`](src/main/java/io/github/teamcreating/mtrmap); [`fabric/`](fabric) contains Fabric-specific entry points and adapters and compiles the shared Java sources. NeoForge keeps common initialization in `MTRMap` and physical-client events in `MTRMapClient`; `MTRNetworkClient` holds client-only transport callbacks. The two builds share textures, the 128×128 mod/pack logo, and the same `mtrmap` identity.

| Area | Main responsibility |
| --- | --- |
| [`mapdata/`](src/main/java/io/github/teamcreating/mtrmap/mapdata) | `MapDataCache` selects server snapshots or nearby MTR client data. `TrackSampler` samples each physical rail, reused by route paths; `TrackRoutePalette` assigns stable colour bands and `MapTrack` caches bounds for viewport culling. |
| [`network/`](src/main/java/io/github/teamcreating/mtrmap/network) | Protocol-v5 payloads and `NetworkSnapshotCodec` transfer routes, tracks and landmarks. `ServerNetworkCollector` reads MTR simulators on their own threads; `ClientNetworkSync` probes and requests snapshots, while `NetworkChunkAssembler` validates and reassembles chunks. |
| [`integration/xaero/`](src/main/java/io/github/teamcreating/mtrmap/integration/xaero) | `XaeroRouteRenderer` draws tracks, route ribbons and map-only icons in world-map coordinates and handles hover tooltips. |
| [`integration/journeymap/`](src/main/java/io/github/teamcreating/mtrmap/integration/journeymap) | Optional JourneyMap v2 plugin. `JourneyMapToolbar` supplies TRACKS/ROUTES buttons; `JourneyMapScreenProjection` follows pan/drag/zoom; `JourneyMapPathManager` draws viewport-culled pixel-width track and route quads; `JourneyMapForegroundRenderer` keeps landmark icons above both layers. Fullscreen `MarkerOverlay` objects retain hover information. |
| [`mixin/`](src/main/java/io/github/teamcreating/mtrmap/mixin) | Access to MTR data and the Xaero render hook; Fabric supplies its own Xaero hook variant. |
| [`config/`](src/main/java/io/github/teamcreating/mtrmap/config) and [`fabric/src/main/java/`](fabric/src/main/java) | Loader-specific configuration, initialization, client commands and network registration. |

Data flow: MTR simulator/client data → dimension-specific `MapDataCache` → Xaero or JourneyMap fullscreen renderer. With a modded server, the client first probes dimension hashes, requests changed snapshots, validates and reassembles chunked payloads, then updates the cache. Without one, the cache falls back to MTR's radius-limited client data.

## Troubleshooting

- **Only nearby stations appear:** the server has not supplied a whole-network snapshot. Install the matching mod on the server, or use the client-only fallback as intended.
- **No Xaero lines:** check that Xaero's **World Map** is installed and that the log contains `Path layer render hook into Xaero's World Map is active`. Xaero internal changes can break the render hook.
- **No minimap waypoints:** expected. Landmarks are intentionally fullscreen-map overlays.
- **Migrating from an older build:** replace the old `mtrsurveyor` JAR rather than installing it beside this one; the mod ID and command are now `mtrmap` and `/mtrmap`.

## Support development

If MTR Map Overlay helps you explore your railway network, you can support continued development on Ko-fi or Afdian.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/Z8C424REOO)
<a href="https://afdian.com/a/benli06"><img src="docs/assets/badges/afdian.svg" alt="Support me on Afdian · 爱发电" height="30"></a>

## License and attribution

The project is MIT-licensed. The original copyright and license notice for AmberFrost's contributions remains in [`LICENSE`](LICENSE); later work is maintained by BenLi06. The existing Git commit history and attribution are preserved.
