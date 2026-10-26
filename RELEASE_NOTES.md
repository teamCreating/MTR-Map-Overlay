# v1.5.3 (release preparation)

Compared with v1.5.2, this release adds native station arrival boards, two PIDS themes and a public API for third-party navigation views.

## Added

- **Pinned station information and native arrivals.** Click a station to keep its original station/platform/route information open. The card starts collapsed; **Expand** shows the next available services by destination, line and platform, using the same MTR/TSC arrival-data interface used for PIDS. Closing or collapsing the board releases its subscription. Missing predictions are displayed explicitly rather than as zero-minute arrivals.
- **British and European/airport PIDS styles.** British black/amber is the default. Use `/mtrmap config pidsStyle british` or `/mtrmap config pidsStyle european` to switch immediately and save the choice. Omit the style argument to query the current setting.
- **A unified navigation/view API.** Third-party mods can read native station/route/stop/path identifiers, resolve route sections, open owned view sessions and subscribe to station boards. They can highlight upcoming sections while independently dimming completed sections and unused lines, including different sections of the same physical rail. The **Navigate** entry supports registered providers and a chooser when several are available. It is disabled when no provider is installed; this release does not include an MTR-Navigation/howtogo-specific adapter.

## Optimized and fixed

- Reused native MTR/TSC arrival predictions without adding a client-side transit simulation. Station interests share server collection/encoding, with bounded leases, queues, paced chunks and fair transmission across players and interests. Clients decode in the background and reject stale snapshots.
- Preserved the classic station card and Xaero ROUTES/TRACKS controls. Improved narrow-window summaries, long-text hover details, aligned PIDS columns and ETA readability. Panel scrolling and clicks are intercepted before map zoom/pan actions.
- Fixed rail layers and the player arrow appearing over station panels. Retained separate Forge 47/49 input/network adapters and JourneyMap 5/6 rendering bridges.
- Navigation dimming applies consistently to route colours, high-speed white blocks, feathered edges and physical backing. Overlapping upcoming sections remain bright even when an earlier navigation leg uses the same track.
- All six builds include these features. Whole-map live trains, native XYZ storage and pure 2D rendering remain available; vehicle-hover ETA/next-stop distance is still deferred.

## 中文更新说明

相较 v1.5.2，新增原生站点到站板、英式/欧式两套 PIDS，以及供第三方导航模组接入的统一地图视图 API。

### 新增功能

- **站点点击固定与原生到站预测**：保留原站点、站台和线路信息，默认折叠；点击 **Expand** 后按目的地、线路和站台显示下一班可用服务。直接使用 MTR/TSC 给 PIDS 的到站数据接口；无预测明确显示缺失，折叠或关闭后释放订阅。
- **两种 PIDS 样式**：默认英式黑底琥珀字，可用 `/mtrmap config pidsStyle british|european` 即时切换并保存；不带样式参数可查询当前设置。
- **统一导航/显示接口**：第三方可读取原生站点、线路、停站序号和路径目录，解析区段并控制独立视图。待乘区段保持明亮，已走区段和未用线路可分别变暗，同一物理轨道也能按区段控制。预留 **Navigate** 入口及多 provider 选择；没有 provider 时禁用。本版未内置特定导航模组的适配器。

### 优化与修复

- 服务端共享原生预测采集和编码，增加租约、容量限制、后台队列、分片节流与玩家/订阅轮转；客户端后台解码并拒绝旧快照，不维护第二套行车模拟。
- 恢复旧式站点信息框与 Xaero ROUTES/TRACKS 控件；改善小窗口摘要、长文本悬停、表格列对齐和 ETA 显示，防止面板操作误触地图缩放/拖动。
- 修复轨道和玩家箭头覆盖站点面板的问题，保留 Forge 47/49 和 JourneyMap 5/6 的独立兼容处理。
- 导航 dimmed 同时作用于线路色、高铁白块、边缘及物理底衬；后续乘车区段重复使用同一轨道时保持明亮。
- 六个平台同步提供本次功能；继续保留全图列车、真实 XYZ 和纯 2D 绘制。列车悬停 ETA/下一站距离仍暂缓。

## Choose the matching file / 下载环境

Install **one** mod JAR matching the exact Minecraft version, MTR version and loader. Native station arrivals and whole-map trains require matching client/server Overlay versions; client-only installations cannot provide the server snapshot service.

按游戏版本、MTR 版本和加载器选择**一个** JAR。原生站点预测及全图列车需客户端和服务端安装匹配版本；仅客户端安装无法提供服务端快照服务。

| File | Minecraft | Loader | MTR | Java |
| --- | --- | --- | --- | --- |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.21.1-MTR4.1.0-beta.2-neoforge.jar` | 1.21.1 | NeoForge 21.1.x | 4.1.0-beta.2 | 21 |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.21.1-MTR4.1.0-beta.2-fabric.jar` | 1.21.1 | Fabric | 4.1.0-beta.2 | 21 |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.20.1-MTR4.0.5-forge.jar` | 1.20.1 | Forge 47.x | 4.0.5 | 17 |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.20.1-MTR4.0.5-fabric.jar` | 1.20.1 | Fabric | 4.0.5 | 17 |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.20.4-MTR4.0.5-forge.jar` | 1.20.4 | Forge 49.x | 4.0.5 | 17 |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.20.4-MTR4.0.5-fabric.jar` | 1.20.4 | Fabric | 4.0.5 | 17 |

## Validation and known limits / 验证与已知限制

Six-target builds, JAR checks, shared unit tests and public Java 17 API consumer checks passed. Development-runtime PIDS checks covered Xaero and JourneyMap on all six targets. This is distinct from a production-JAR/large-modpack acceptance claim; see the manual candidate record for the exact release-binary validation performed.

**Forge 1.20.4 singleplayer quick-play failed during testing:** Xaero Minimap/WorldMap read a null `serverData` during player join and disconnected before the Overlay snapshot was applied. The root cause remains unconfirmed. The same target passed both-map PIDS checks when connected to a separate local server. Singleplayer quick-play is **not verified as working** in that tested combination.

六目标构建、产物检查、共享单测和 Java17 API 消费者检查通过；六平台两地图均完成开发运行环境中的基础 PIDS 回归。此结论不等同 production JAR/大型整合包验收，具体发布二进制的验证范围以候选验收记录为准。

**Forge 1.20.4 单人 quick-play 存在已知启动问题**：测试时 Xaero 在玩家加入阶段读取空 `serverData`，于 Overlay 快照应用前断开连接，根因尚未确认。该目标连接独立本地服务器后的双地图 PIDS 测试通过；不能据此声称单人启动已通过。

Real third-party navigation-provider integration, large-network/multi-viewer performance and cross-machine play have not been validated. No FPS/bandwidth benchmark is claimed. 原生预测覆盖不足会明确显示缺失，不应理解为没有列车或零 ETA；真实第三方导航联动、大网/多观看者性能和跨机器联机仍未实测。

[Detailed validation / 完整验证记录](https://github.com/teamCreating/MTR-Map-Overlay/blob/v1.5.3/docs/validation/pids-and-dimmed-1.5.3.md) · [Public API / 公共接口](https://github.com/teamCreating/MTR-Map-Overlay/blob/v1.5.3/docs/api/overlay-api-v1.md)

## Changes from the previous release / 与上一次 release 的 diff

[Full changelog: v1.5.2 → v1.5.3](https://github.com/teamCreating/MTR-Map-Overlay/compare/v1.5.2...v1.5.3)

[Minecraft 1.20 compatibility diff: v1.5.2-mc1.20 → v1.5.3-mc1.20](https://github.com/teamCreating/MTR-Map-Overlay/compare/v1.5.2-mc1.20...v1.5.3-mc1.20)

The refreshed 1.5.2 namespace binaries were built from main `959b7e4da470bf8a1315877a972189cce7c8e776` / compat `54a0acd`; the original release tags were retained. [Diff from the actual refreshed main source / 与1.5.2更新附件实际源码比较](https://github.com/teamCreating/MTR-Map-Overlay/compare/959b7e4da470bf8a1315877a972189cce7c8e776...v1.5.3).


---

# v1.5.2 (2026-10-22)

Compared with v1.5.1, this release adds whole-map live trains and improves rail curves, shared-route joins and map zoom behaviour across all six supported builds.

## Refreshed 1.5.2 files / 1.5.2 附件更新

The six JARs were refreshed on 2026-10-22 with the independent Java namespace `io.github.teamcreating.mtrmap` and the original MIT LICENSE included in every JAR. These files replace the earlier 1.5.2 binaries; mod version, mod ID, configuration filenames and network protocols remain unchanged. SHA256SUMS.txt has been updated.

六个 JAR 已于 2026-10-22 更新为独立包名 `io.github.teamcreating.mtrmap`，并随包附带保留原作者版权声明的 MIT LICENSE，替换此前的 1.5.2 文件。模组版本、mod ID、配置文件名及网络协议保持不变；校验文件已同步更新。此前已下载 1.5.2 的用户请重新下载对应附件。

## Added

- **Whole-map live trains in Xaero and JourneyMap.** View moving trains throughout the map's selected dimension, including trains far outside the player's nearby area. Position data comes from TSC; the client interpolates received positions and draws abstract carriages without running another train simulation.
- **Clearer carriage symbols and hover information.** Outlined bodies, route-coloured edges and direction indicators make trains easier to see. Zooming out switches to a compact train marker; zooming in reveals individual carriages. Hover shows available vehicle/model information, route, destination and consist size. `/mtrmap config trainCars false` hides the train layer.
- **Distinct route styles.** Light rail uses 70% of the standard width. Rail and high speed use the same width, with high speed shown as alternating route-colour/white blocks inside a continuous coloured outline.
- **The same latest features on MC 1.20.1 and 1.20.4.** Forge/Fabric compatibility builds now include the current rendering and whole-map train features, alongside NeoForge/Fabric 1.21.1.

## Optimized and fixed

- Smoother native rail curves, adaptive drawing detail and softer thin-line edges reduce visible straight segments on bends.
- Shared cross-sections and continuous stripe direction improve rail joins and shared-route transitions. Combined line width follows map zoom, so shared sections no longer remain excessively thick when zoomed out.
- Static rail geometry and moving carriages use separate bounded caches. Offscreen train filtering and three levels of carriage detail reduce repeated geometry work and unnecessary drawing.
- Global train collection and encoding are shared on the server. Compact per-car reference points and separately synchronized definitions avoid repeating full poses and text in every position update; subscriptions end when no map needs them.
- Rendering uses fixed **2D layers**: rail backing, route colours/blocks, then carriages. Native XYZ is retained, without height sorting or underpass masking.
- Fixed missing train visibility cases, singleplayer Xaero updates being stopped by map pause, and Fabric 1.21.1 JourneyMap plugin discovery. MC 1.20 adapters retain their loader-specific networking and JourneyMap 5/6 support.

## 中文更新说明

相较 v1.5.1，本版新增全图实时列车，并统一改善六个平台的曲线、共线拼接和地图缩放表现。

**新增功能**

- **全图实时列车**：Xaero 与 JourneyMap 可显示当前查看维度中远离玩家的运行车辆。位置由 TSC 下发，客户端只插值和绘制抽象车厢，不维护第二套行车模拟。
- **更清楚的车厢与悬停信息**：增加车体描边、线路色侧边和方向标记；缩远显示紧凑列车图标，放大显示每节车厢。悬停显示可用的车辆/车型信息、所属线路、终点和编组节数。可用 `/mtrmap config trainCars false` 关闭列车层。
- **三种线路样式**：轻铁为标准宽度的 70%；普通 Rail 与高铁等宽，高铁使用连续线路色描边的彩白交替矩形块。
- **兼容分支功能同步**：MC 1.20.1／1.20.4 的 Forge、Fabric 包现已包含最新绘制及全图列车功能，与 1.21.1 的 NeoForge、Fabric 一同提供。

**优化与修复**

- 采用原生轨道曲线、自适应绘制精度及细线边缘过渡，改善弯道折线感。
- 改善轨道接头、共线色带与白块的连续性；总线宽随地图缩放收细，避免缩远后共线区域过粗。
- 分离静态轨道缓存与动态车厢，增加视口剔除和三级车厢细节，减少逐帧重建与屏幕外绘制。
- 服务端共享全图车辆采集和编码，压缩车厢参考点、独立同步定义，减少高频位置包中重复的姿态与文字；不再需要地图时结束订阅。
- 使用轨道底衬→线路→车厢的**纯 2D 绘制**；保留真实 XYZ，不进行高度排序或下穿遮挡。
- 修复部分车辆不可见、Xaero 单机地图暂停导致车辆停止更新，以及 Fabric 1.21.1 无法发现 JourneyMap 插件的问题；保留旧版加载器网络和 JourneyMap 5／6 适配。

## Choose the correct file / 选择对应附件

Install **one** JAR matching your exact Minecraft version and loader. Every filename identifies the tested MTR version. Fabric also requires the matching Fabric API. / 请只安装符合游戏版本和加载器的一个 JAR；文件名标明已测试的 MTR 版本。Fabric 还需要对应的 Fabric API。

| File / 文件名 | Minecraft | Loader / 加载器 | Tested MTR / 已测试 MTR | Java |
| --- | --- | --- | --- | --- |
| `CRTools-MTR-Map-Overlay-1.5.2-MC1.21.1-MTR4.1.0-beta.2-neoforge.jar` | 1.21.1 | NeoForge 21.1.x | 4.1.0-beta.2 | 21 |
| `CRTools-MTR-Map-Overlay-1.5.2-MC1.21.1-MTR4.1.0-beta.2-fabric.jar` | 1.21.1 | Fabric | 4.1.0-beta.2 | 21 |
| `CRTools-MTR-Map-Overlay-1.5.2-MC1.20.1-MTR4.0.5-forge.jar` | 1.20.1 | Forge 47.x | 4.0.5 | 17+ |
| `CRTools-MTR-Map-Overlay-1.5.2-MC1.20.1-MTR4.0.5-fabric.jar` | 1.20.1 | Fabric | 4.0.5 | 17+ |
| `CRTools-MTR-Map-Overlay-1.5.2-MC1.20.4-MTR4.0.5-forge.jar` | 1.20.4 | Forge 49.x | 4.0.5 | 17+ |
| `CRTools-MTR-Map-Overlay-1.5.2-MC1.20.4-MTR4.0.5-fabric.jar` | 1.20.4 | Fabric | 4.0.5 | 17+ |

The MC 1.20 builds declare MTR >=4.0.5 and <4.1 and were tested with 4.0.5. All dependencies must match the same Minecraft version and loader. Optional maps: Xaero's World Map 1.45.0; JourneyMap 6.0.6 for MC 1.20.1, 5.10.0 for MC 1.20.4, or 6.0.8 for MC 1.21.1. / MC 1.20 包声明 MTR >=4.0.5 且 <4.1，本轮测试版本为 4.0.5；所有依赖必须匹配游戏版本和加载器。

**Upgrade both client and server for whole-map trains.** Static snapshots now use v8 and live trains use an independent v1 protocol. Without matching server support, only nearby MTR data is available. Servers need MTR and this mod, but no map mod. Next-stop distance and ETA are not included. / **需要全图列车时，请同时更新客户端和服务端。**无对应服务端能力时仅显示附近回退数据；服务端不需要地图模组。本版不含下一站距离或 ETA。

## Validation / 验证

All six builds passed artifact checks and Xaero/JourneyMap development-runtime checks. The 1.21.1 builds share 86 passing tests; each of the four MC 1.20 builds passed 89 tests. One world-writing generator is skipped by default. Forge 1.20.4 had one GLFW native crash on its first attempt; an isolated retry passed, and the root cause remains unknown. Production modpack launch, cross-machine multiplayer and large-fleet performance have not been verified. / 六包构建、产物检查和两地图开发环境实机回归通过；首次 Forge 1.20.4 原生崩溃重试通过，原因未定，未将其记作已修复。

[Full validation record / 完整验证记录](https://github.com/teamCreating/MTR-Map-Overlay/blob/v1.5.2/docs/validation/platform-matrix-1.5.2.md)

The namespace refresh passed all six builds, the same 86/89-test suites and artifact checks. NeoForge/Fabric 1.21.1 and Fabric 1.20.4 were also rechecked in game; the other three targets retain the earlier runtime results. / 新包名六目标构建、原有86/89项测试及包结构检查通过；本次另复查NeoForge/Fabric 1.21.1与Fabric 1.20.4实机，其余三个目标沿用此前实机结果。

[Namespace migration checks / 包名迁移验证](https://github.com/teamCreating/MTR-Map-Overlay/blob/959b7e4da470bf8a1315877a972189cce7c8e776/docs/validation/namespace-migration.md)

**Source for the refreshed binaries / 当前附件对应源码:** [MC 1.21.1: 959b7e4](https://github.com/teamCreating/MTR-Map-Overlay/tree/959b7e4da470bf8a1315877a972189cce7c8e776), [MC 1.20.1 / 1.20.4: 54a0acd](https://github.com/teamCreating/MTR-Map-Overlay/tree/54a0acd89a5c8c101caf092594812fc1070a8636). The original publication tags are retained; use these source links for the refreshed JARs. / 初次发布标签保留，当前新附件的源码请使用以上链接。

[MC 1.20 compatibility changes since the previous release / 兼容分支改动](https://github.com/teamCreating/MTR-Map-Overlay/compare/76ec108...54a0acd89a5c8c101caf092594812fc1070a8636).

[Full diff from the previous release / 与上一次发布的完整 diff: v1.5.1 → refreshed v1.5.2](https://github.com/teamCreating/MTR-Map-Overlay/compare/v1.5.1...959b7e4da470bf8a1315877a972189cce7c8e776)


---

# v1.5.1 Pre-release Notes (2026-09-28)

Compared with [v1.5.0](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.0), this pre-release fixes NeoForge Dedicated Server startup:

- **Dedicated Server crash fixed.** Common Setup no longer loads JourneyMap or other client-only classes. Client ticks, login/logout events and commands are registered from a physical-client-only mod entry point.
- **Network side isolation.** Client payload handlers and transport checks moved out of the common NeoForge network registrar. Fabric retains its loader-specific client transport adapter; the snapshot protocol remains v5.
- **Verification.** Both loader builds and NeoForge tests passed. A NeoForge Dedicated Server with MTR and Xaero reached `Done`, while a NeoForge client in TestWorld initialized JourneyMap and applied a snapshot containing 2 routes, 5 rails and 6 landmarks; 4 JourneyMap markers synced without errors. Fullscreen map visuals and cross-machine play still require manual verification.

Minecraft 1.21.1 and MTR 4.1.0-beta.2 requirements are unchanged. Install only the JAR for your loader. For a whole-network view, install the matching JAR on both client and server.

[Full diff: v1.5.0...v1.5.1](https://github.com/teamCreating/MTR-Map-Overlay/compare/v1.5.0...v1.5.1)

## 中文摘要

相较 [v1.5.0](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.0)，此预发布版修复 NeoForge Dedicated Server 启动时加载 `ClientLevel` 导致的崩溃。通用入口不再加载 JourneyMap 等纯客户端类；客户端事件、命令和网络回调移至客户端专用入口。Fabric 保留对应的平台适配，网络快照协议仍为 v5。

NeoForge、Fabric 构建及 NeoForge 测试通过；NeoForge Dedicated Server 已启动至 `Done`。TestWorld 客户端已初始化 JourneyMap、应用含 2 条路线/5 段轨道/6 个地标的快照，并同步 4 个标记且无失败。全屏地图视觉效果和跨机器联机仍需手动验证。Minecraft 1.21.1 与 MTR 4.1.0-beta.2 要求不变；全网视图需客户端和服务端都安装对应加载器的 JAR。

---

# v1.5.0 Release Notes (2026-09-25)

Compared with the previous official release, [v1.4.6](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.4.6):

- **JourneyMap now has the full rail and route layer.** Its fullscreen map draws sampled physical rails and adjacent colour bands for routes sharing a rail. Independent TRACKS and ROUTES buttons use the same red/off and green/on icon convention as Xaero. With both enabled, routes sit over tracks; station and platform icons remain on top in every switch combination.
- **Map-anchored rendering.** JourneyMap uses Xaero-matched screen-pixel track width and the map's live pan, drag and zoom projection. The path and landmark layer stays aligned while dragging instead of floating or tearing.
- **Lighter rendering and data handling.** Route paths reuse sampled track geometry; rail bounds cull offscreen work on both maps; JourneyMap batches compatible icons. Snapshot chunk assembly validates order, size and metadata, and client fallback caches are cleared when changing servers.
- **Project presentation and builds.** The new 128×128 logo is used for the mod pack and both READMEs. GitHub Actions builds NeoForge and Fabric with Java 21; the English and Chinese guides now describe JourneyMap's actual renderer and controls.

Minecraft 1.21.1, MTR 4.1.0-beta.2, and the v5 network snapshot protocol remain unchanged. Install only the JAR matching your loader. For whole-network data, the matching mod must run on the server as well as the client; client-only use is limited to MTR's nearby data. Both loader builds and NeoForge unit tests passed; Fabric in-game rendering and cross-machine networking have not been independently verified for this release.

[Full diff: v1.4.6...v1.5.0](https://github.com/teamCreating/MTR-Map-Overlay/compare/v1.4.6...v1.5.0)

## 中文摘要

相较 [v1.4.6](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.4.6)，JourneyMap 全屏地图补齐 TRACK/ROUTE 轨道与共线路线色带，提供与 Xaero 一致的红/绿开关图标；双层开启时路线叠在轨道上，站点与站台图标始终位于最上层。整层跟随地图实时拖动与缩放，轨道线宽与 Xaero 一致。此版还复用轨道采样、剔除视窗外轨道、合批图标，并加强快照分块校验及跨服务器缓存清理。两端 README 已更新并使用新 Logo。全网视图仍需服务端安装对应版本；纯客户端只能显示 MTR 同步到附近的数据。协议保持 v5，版本号为 1.5.0。

---

# v1.4.6 Release Notes (2026-09-23)

相较上一正式发布版 [v1.4.4](https://github.com/teamCreating/MTR-Xareo-Mapper/releases/tag/v1.4.4)：

- **新增 Fabric 1.21.1 构建**：与 NeoForge 版分别出包；两者共用地图数据、绘图逻辑和 v5 快照协议，Fabric 单独实现入口、客户端命令、配置及网络适配。Fabric 需要 Fabric API 与同平台的 MTR 4.1.0-beta.2。
- **地标不再是普通 Xaero 路标**：站点、站台、车辆段改为仅在全屏地图内绘制的小图标，不进入路标列表、指南针、Minimap 或世界 HUD；旧版留下的 `[MTR]` 路标会自动清理。JourneyMap 地标也限定在全屏地图显示。
- **地图图标稳定性与完整性**：Xaero 图标与轨道共用地图坐标变换，拖动和缩放时不再按屏幕整数像素跳动；Xaero/JourneyMap 的 32×32 站点贴图改为完整采样，修复截断。
- **项目标识更名**：mod ID、Java 包、资源命名空间与客户端命令统一为 `mtrmap` / `/mtrmap`，不再使用 `mtrsurveyor` 标识。NeoForge 首次启动可从旧的 `mtrsurveyor.toml` 复制配置；全网同步需客户端和服务端安装使用新 mod ID 的版本。

请按加载器只安装一个 JAR。NeoForge 使用 `config/mtrmap.toml`，Fabric 使用 `config/mtrmap.properties`。两平台构建及 NeoForge 单元测试通过；未自动启动 Minecraft，Fabric 地图显示与联机同步仍待手动验证。

[查看 v1.4.4 到 v1.4.6 的完整差异](https://github.com/teamCreating/MTR-Xareo-Mapper/compare/v1.4.4...v1.4.6)

---

# v1.4.5 Release Notes (2026-09-23)

## 独立项目标识

- 统一使用 `mtrmap` mod ID、Java 包、配置名、资源命名空间和 `/mtrmap` 客户端命令；项目直接读取 MTR 数据并绘制 Xaero / JourneyMap 地图覆盖层，不接入 MTR Surveyor 的地图。
- 首次启动时，如果新配置不存在，会从旧的 `mtrsurveyor.toml` 复制设置。全网同步需要客户端与服务端都安装使用 `mtrmap` ID 的版本。

## 地标改为地图图层小图标

- 移除 v1.4.4 的 Xaero 普通 waypoint 创建逻辑；站点、站台和车辆段现在直接由 Xaero World Map
  overlay 绘制，不进入路标列表、指南针、Minimap 或世界内 HUD。
- Xaero 站点图标固定为 12px，车辆段 10px；站台使用 5px 小点并只在放大后显示，悬停才展开名称和路线。
- JourneyMap 的自定义 `MarkerOverlay` 限定为 `Fullscreen`，清空常驻标签并缩小图标；不会显示在 Minimap。
- Xaero 与 JourneyMap 都优先使用协议 v5 的全量 `MapLandmark`，无服务端组件时退回附近客户端数据。
- 首次进入各 Xaero 世界时自动删除旧版本遗留的 `[MTR]` waypoint，但此后不再创建或维护正常路标。
- 验证：`gradlew test --rerun-tasks --no-daemon` 通过；Minecraft 实机验证留给 Ben 手动执行。

---

# v1.4.4 Release Notes (2026-09-22)

## 全图站点/站台航点与共线彩虹带

- 协议升级为 v5：服务端完整快照新增站点、站台、车辆段地标，并把路线几何改为引用唯一物理轨道 ID。
- Xaero 收到全量快照后增量维护当前维度的站点和站台航点；远处航点不再依赖 MTR 客户端半径数据，重登前的已保存远处航点也不会被附近数据误删。
- 站点和站台支持同时显示，新增 `showPlatformLandmarks`、`mode both` 与 `config showPlatforms`。
- 同一物理轨道只绘制一次：单路线保持 TRACK 同形单色，多路线按稳定 route ID 横向分成彩虹色带，不再被最后绘制的路线覆盖。
- 路线悬停改为检测真实轨道折线，并列出该轨道上的所有路线。
- 验证：`gradlew test --no-daemon` 通过；Minecraft 实机验证留给 Ben 手动执行。

---

# v1.4.3 Release Notes (2026-09-20)

## ROUTE 直接复用 TRACK 轨道折线与绘制管线

- 路线几何由单条拼接 path 改为 `List<MapTrack>`：每根属于路线的物理轨道直接复用 TRACK 层的
  `TrackSampler.sample()` 结果，不再对 Depot 路径另行采样或跨轨拼接。
- ROUTE 与 TRACK 统一调用同一个 `drawPolyline()`：相同的逐段视口裁剪、quad 生成、线宽和透明度；
  两层现在只有颜色不同。
- 纯客户端车辆路径和无 Depot 路径的服务器回退也统一输出 `MapTrack`，不再存在另一套路线折线格式。
- 网络快照协议升级为 v4，按路线传输独立的轨道折线，避免客户端重新拼接产生伪连接。
- 验证：`gradlew test --rerun-tasks --no-daemon` 通过；按 Ben 要求未启动 Minecraft。

---

# v1.4.2 Release Notes (2026-09-20)

## 无车辆段路径时仍严格贴轨

- MTR `Depot.getPath()` 仍是首选路线来源；尚未生成车辆段路径的路线改用服务器现有
  `positionsToRail` 铁路网做最短路回退。
- 回退路线逐轨调用与灰色轨道层相同的 `TrackSampler.sample()`，顶点与轨道层采样结果完全一致，
  并固定在线路中心，不再绘制站点间直线或偏离轨道的车道线。
- 任意区间无法在铁路网上连通时，整条回退路线不绘制，并记录警告；不会伪造跨空白区域的直线。
- 环线闭合改为对末站到首站单独寻路，不再反向重走整条既有路径。
- 新增无头回归测试，覆盖轨道采样逐点一致、反向索引补全及断路不画直线。
- 验证：`gradlew test --rerun-tasks --no-daemon` 通过；Minecraft 实机验证留给 Ben 手动执行。

---

# v1.4.1 Release Notes (2026-09-20)

## 路线颜色改用 MTR 真实寻路结果（轨道染色）+ 快照热更新探测

- **路线颜色来源更换**：放弃本 mod 自己的 Dijkstra 寻路，改用 **MTR 车辆段生成的真实驾驶路径**
  （`Depot.getPath()`，即 MTR 路线生成后列车实际行驶的轨道序列）。路线颜色直接叠加在轨道几何上，
  呈现「轨道被染上路线颜色」的效果——与轨道层共享同一几何来源，所见即列车实际行驶路线。
- **多路线车辆段**：路线颜色按列车途经站台解析（`platform.routes ∩ depot.routes`），
  同一车辆段的多条路线自动按段着色；跨路线的过渡段延续前色。
- **快照热更新探测（协议 v3）**：
  - 新增 `NetworkSyncProbe`（C2S）/ `NetworkProbeResponse`（S2C）：客户端定期探测，
    服务端计算每个维度的轻量内容哈希（O(轨道+路线+车辆段)，毫秒级、零传输）；
  - 哈希未变化的维度不传输；变化的维度按维度过滤请求全量快照；
  - 刷新探测很便宜，可以把刷新间隔设得更短（默认仍为 `networkSync.refreshIntervalSeconds=300`）。
  - 参照 Create 的机制（客户端轨道图持续增量同步 + `version` 计数驱动的重绘）。
- **Dijkstra 寻路移除**：v1.4.0 的自研寻路及车道偏移逻辑删除（`RoutePathfinder`），
  未生成车辆段路径的路线回退为站点连线（淡化透明度）。
- 车辆段路径生成需要 MTR 的生成管线完成；测试世界可用 `TestWorldGeneratorTest`
  （含 `instantDeployDepots` 部署）生成带车辆段的存档。

---

# v1.4.0 Release Notes (2026-09-20)

## 路线层沿真实轨道吸附（寻路 + 平行走线偏移）

- **路线吸附轨道**：服务端生成全网快照时，对每条路线的相邻站台在铁路网（`positionsToRail` 节点图）上做
  **Dijkstra 寻路**，以站台自身的平台轨道为锚点，输出沿真实轨道几何（含弧线/弯道）的路径点，
  替代原先"站台间直线"的画法。
- **共线车道偏移**：多条路线共用同一轨道段时，按遍历方向分组、按路线序号排序，自动分配 ±1、±2 …
  车道偏移；单路线独占的轨道保持中线。渲染时车道间距 = 一条路线线宽，平行走线不再互相覆盖。
- **寻路失败回退**：轨道不连通（或未装服务端组件）的路线段自动回退为直线插值（车道 0），不影响其余段。
- **协议 v2**：快照中每条路线新增 path 点列（x/z/车道索引）；`MapRoute` 新增 `path` 字段；
  纯客户端回退模式（无服务端组件）仍为直线画法。
- **弯道闭合**：环形路线自动用反向重走前段路径闭合；同路线不与自身偏移。
- 新增 `TestWorldGeneratorTest`：可在无头 JVM 上生成带完整 MTR 网络（车站/站台/路线/轨道）的测试世界存档，
  用于实机验证；`RoutePathfinderTest` 覆盖吸附、车道、断连回退、反向遍历（5/5 通过）。

## 实机验证（TestWorld 生成网络：Alpha/Bravo/Charlie 三站 + Express/Local/Local Return 三线）

- 快照同步：`Sent full-network snapshot (3 routes, 5 rails)` → `applied`。
- 打开 Xaero 世界地图：Express（红）与 Local/Local Return（绿）在共享段三线并行、互不重叠，
  线形完全贴合轨道走向；下方暗色轨道层正常。

---

# v1.3.0 Release Notes (2026-09-16)

## 统一版本：一个 jar 同时支持 Xaero 与 JourneyMap

- 将原独立发行的 JourneyMap 版本（journeymap 分支）并入主 mod，与 Xaero 集成共存：
  - **装了 JourneyMap** → 自动启用车站/车辆段地标（JourneyMap v2 API `MarkerOverlay`，按交通方式着色图标，
    station/platform 两种模式，悬浮显示收费区与「路线 → 终点站」信息），行为与旧 JourneyMap 版一致；
  - **装了 Xaero** → 航点同步 + 世界地图路径层 + 全网同步，行为不变；
  - **两者都装** → 两套集成同时工作，互不干扰。
- JourneyMap 检测为可选依赖（`ModList` 探测 + 类加载隔离），未安装时零开销、零报错。
- 移植到 JourneyMap **v2 API**（`journeymap.api.v2`，依赖 `info.journeymap:journeymap-api-neoforge`，
  官方 maven `jm.gserv.me`）；旧 `journeymap.client.api`（v1）在 JourneyMap 6.x 已被移除。
- 标记纹理统一迁入 `assets/mtrsurveyor/` 命名空间（同时修正了旧分支命名空间不一致的隐患）。
- 新增命令：`/mtrsurveyor syncLandmarks`（强制刷新地标）、`/mtrsurveyor testMarker`
  （在玩家位置放一个诊断标记，验证集成是否生效）。
- `neoforge.mods.toml` 将 JourneyMap 声明为可选依赖（`[1.21.1-6.0.8,)`）。

## 实机自测（runClient：MTR + Xaero 双图 + JourneyMap 6.0.8 同装）

- JourneyMap 启动时发现并初始化本 mod 的 v2 插件（`JourneyMap v2 API initialized!`）。
- 进入世界后地标同步自动执行（`Landmark sync (scheduled sync): 0 markers active, 0 failed`）。
- `/mtrsurveyor testMarker` 放置的标记在 JourneyMap 全屏地图上实际渲染（截图确认）。
- 同会话中 Xaero 航点同步、路径层、全网同步均正常，无冲突、无报错。

---

# v1.2.0 Release Notes (2026-09-05)

## 平台迁移: Forge 1.20.1 → NeoForge 1.21.1

- 目标平台变更为 **NeoForge 1.21.1**(NeoForge 21.1.249),依赖 **MTR 4.1.0-beta.2**(NEOFORGE-4.1.0-beta.2+1.21.1)、Xaero's World Map 1.45.0+、Xaero's Minimap 26.4.2+。
- 构建系统由 ForgeGradle 6 迁移为 **ModDevGradle 2.0.146**;NeoForge 运行时即 Mojang 官方映射,mixin 不再需要 refmap/SRG 名。
- MTR 4.1 的包名迁移已适配:`org.mtr.mod.client.MinecraftClientData` → `org.mtr.client.MinecraftClientData`、`org.mtr.mod.Init` → `org.mtr.MTR`;mtr-core 数据层(Station/Platform/SimplifiedRoute/Rail/RailMath/Simulator)保持兼容。
- 网络层从 Forge `SimpleChannel` 重写为 NeoForge `CustomPacketPayload` + `PayloadRegistrar`,两侧均以 `optional()` 注册,并对非 NeoForge 连接做了类型探测,在不支持的服务器上自动回退纯客户端模式。
- 1.21.1 渲染 API 适配:`VertexConsumer.addVertex()/setColor()`、`ResourceLocation.fromNamespaceAndPath`、pack_format 34。
- 新增 `/mtrsurveyor syncRoutes` 命令与 `networkSync.enabled` / `networkSync.refreshIntervalSeconds` 配置(全网快照同步)。

## 实机自测记录 (runClient + MTR/Xaero 实装环境)

- mtrsurveyor 1.2.0 加载成功;Xaero 双图检测、`XaeroWorldMapMixin`/`XaeroWorldMapAccessor` 成功应用到 `GuiMap`。
- 打开世界地图后渲染钩子日志 `Path layer render hook into Xaero's World Map is active` 出现,ROUTES/TRACKS 开关在地图左上角正常渲染。
- 单机进入世界后全网同步全链路自动完成:`Requested → Sent full-network snapshot for minecraft/overworld|the_nether|the_end → Full-network snapshot applied`,分块传输与客户端缓存工作正常。
- 会话期间无 mod 相关报错。

---

# v1.0.1 Release Notes(2026-09-05)

## 下载说明

本 Release 附带两个独立构建产物(均为客户端 mod,可单独安装,也可同时安装):

| 文件 | 版本 | 说明 |
|------|------|------|
| `CRTools-MTR-Xaero-Mapper-1.0.1.jar` | Xaero 版(main 分支) | 将 MTR 车站 / 站台 / 车辆段同步为 Xaero's Minimap 航点 |
| `mtrjourneymap-1.0.1.jar` | JourneyMap 版(journeymap 分支) | 将 MTR 车站 / 站台 / 车辆段显示为 JourneyMap marker |

## 本次更新内容

### 移除:Xaero 世界地图路线视图(main 分支)

- 按计划移除了将 MTR 路线网络直接呈现至 Xaero's World Map 的功能(`XaeroRouteRenderer` 及配套的 `XaeroWorldMapMixin` / `XaeroWorldMapAccessor` / `XaeroMixinPlugin` 已一并删除)。
- Xaero 版本自此不再依赖 Xaero's World Map——航点同步仅需要 Xaero's Minimap。
- 两个版本的功能特性自此完全对齐,路线视图在两个版本中均不再提供。

### 统一:JourneyMap 版本(journeymap 分支)功能对齐 Xaero 版本

- **站台(Platform)模式**:新增 `waypointMode` 配置(`station` / `platform`)。
  - platform 模式下,每个站台一个 marker,标签为站台编号;
  - 悬停显示车站名,以及每条经过该站台路线的「路线名 → 终点站」信息(支持自定义终点站、按路线名去重);
  - marker 坐标取站台精确位置。
- **Depot 显示**:车辆段 marker 在两种模式下均可通过 `showDepotLandmarks` 开关显示,并按交通方式(火车 / 船 / 缆车 / 飞机)使用对应图标,按站点颜色染色。
- **配置调整命令**(客户端命令,任何服务器可用,与 Xaero 版命令结构一致):
  - `/mtrjourneymap syncLandmarks` — 手动触发同步;
  - `/mtrjourneymap mode [station|platform]` — 查看 / 切换显示模式;
  - `/mtrjourneymap config enabled|showStations|showDepots|showEmptyStation <true/false>` — 调整配置;
  - 每次修改自动保存并立即触发重新同步。
- **同步机制对齐**:新增 `ClientSyncHandler`,同步请求进入客户端 tick 队列并自动重试(JourneyMap API 未就绪、MTR 数据未同步时不会丢失请求);玩家切换维度时自动重建 marker。
- **服务端命令移除**:原需权限等级 4 的服务端 `/mtrjourneymap syncLandmarks [dimension]` 已由上述客户端命令取代;`MTRSimulatorMixin` 改为空操作(marker 属客户端内容,专用服务器上不存在 JourneyMap API,原写法在服务器端有崩溃风险)。

### 保留:Xaero 版本既有特性(纯客户端运行)

- 全面解耦服务端数据依赖:调取本地 Dashboard 与活动 Client 实例节点直接映射渲染,可顺畅连接至未安装本 Mod 的外部或第三方多人服务器。

## 修复与底层优化

- **JourneyMap marker 重复 ID 修复**:JourneyMap API 拒绝对已显示的 ID 再次调用 `api.show()`,原逻辑每次同步会持续报错且无法更新。现改为每次同步先移除旧 marker 集再整体重建。
- **marker ID 生成修复**:Station / Depot / Platform 的共同基类是 `NameColorDataBase` 而非 `AreaBase`(Platform 属于 SavedRailBase 体系),原实现对站台生成 marker 时会失败。
- **构建环境修复**:两个分支的 `gradle.properties` 改为指向本机 JDK 21;journeymap 分支 gradle wrapper 由 8.12 调整为 8.8。

## 部署及兼容说明

- **目标环境架构**: Forge 1.20.1
- **依赖运行基准版本**:
  - `MTR v4.0.2-hotfix-1` 或以上版本
  - Xaero 版本(main 分支):`Xaero's Minimap v25.3.10` 或以上版本(不再需要 Xaero's World Map)
  - JourneyMap 版本(journeymap 分支):`JourneyMap 1.20.1`(可选,提供 marker 显示)
