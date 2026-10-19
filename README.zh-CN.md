<div align="center">
  <img src="src/main/resources/pack.png" alt="MTR Map Overlay 标志" width="128" height="128">

  <h1>MTR Map Overlay</h1>

  <p>在地图上查看 Minecraft Transit Railway 的路线、轨道和车站。</p>

  <p><a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="https://mtrmapoverlay.benli06.site/zh/">官网与文档</a> · <a href="https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.1">下载 v1.5.1</a></p>

  <p>
    <a href="https://modrinth.com/project/ZU7SzyH7"><img src="docs/assets/badges/modrinth-cozy.svg" alt="Modrinth 下载" height="56"></a>
    <a href="https://www.curseforge.com/minecraft/mc-mods/mtr-map-overlay"><img src="docs/assets/badges/curseforge-cozy.svg" alt="CurseForge 下载" height="56"></a>
  </p>
</div>

MTR Map Overlay 将 Minecraft Transit Railway 的路线、真实轨道与地图专属地标显示在 Xaero's World Map 和 JourneyMap 中。**v1.5.1 支持 Minecraft 1.20.1、1.20.4 的 Forge/Fabric，以及 1.21.1 的 NeoForge/Fabric。**它直接读取 [Minecraft Transit Railway（MTR）](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) 数据，不依赖 MTR Surveyor 的地图。

## 功能

| 地图 | 显示内容 |
| --- | --- |
| Xaero's World Map | 真实轨道几何、路线色带，以及小型车站、站台、车辆段图标；悬停查看详情。 |
| JourneyMap | 仅在全屏地图显示真实轨道、共线路线色带，以及车站、站台和车辆段图标；提供独立的 TRACKS 与 ROUTES 开关。 |

这些是**地图内图标**，不是普通 Xaero 路标，不会挤满路标列表、指南针、小地图或游戏内 HUD。同一条物理轨道有多条路线时，颜色并排显示，不再互相覆盖。两种地图都先绘制轨道，再叠加路线，最后绘制站点与站台图标。JourneyMap 整层跟随地图实时拖动和缩放；轨道宽度与 Xaero 的屏幕像素样式一致。

客户端和服务端都安装本 mod 时，可按维度获取**全网快照**。如果服务端没有安装，本 mod 仍可工作，但只能显示 MTR 已同步到客户端的附近数据。Xaero 与 JourneyMap 都是可选集成，可以只装其中一个，也可以同时安装。

## 开发路线图

以下按优先级记录开发进度，未完成目标的具体范围与时间可能调整。

- [x] **TSC 线路类型（main / 1.5.2 开发版）** — 轻铁为标准宽度的 70%，普通铁路为标准实线，高铁为 140% 宽彩白交替块并保留连续线路色描边。Xaero 与 JourneyMap 共用绘制几何；密集共线和低缩放下简化块状纹理。全网同步需客户端、服务端匹配开发版本（当前 1.5.2 / 协议 v7）；现有 1.5.1 发布下载不变。待游戏内视觉验证。
- [ ] **列车实时位置与预计到站时间（部分实现）** — main 1.5.2 已按真实 XYZ 绘制附近车厢，轨道/线路/车厢按高度叠放，悬停显示运行线路和坐标；全网列车动态同步及预计到站时间尚未实现。
- [ ] **后续：显示效果优化** — 优化线路样式、列车与站点图标、文字标注和缩放表现，提升地图的可读性与视觉一致性。

### 附近车厢（main 1.5.2 开发版）

车厢使用 MTR 真实车体/转向架变换，保留坡度和侧倾；悬停可查看线路、目的地、车型 ID、车厢序号与真实 XYZ。默认显示，可用 `/mtrmap config trainCars false` 关闭。当前仅显示玩家所在维度、MTR 已同步到附近的车辆；静态全网地图不代表全网动态列车。

轨道采样保留真实 Y，协议 v7 用 double 精度传输 XYZ。Xaero 与 JourneyMap 共用轨道、线路和车厢的高度排序，上层铁路可遮住下穿车厢。这里实现的是叠加层内部排序，不是地形/建筑/隧道顶板遮挡或完整三维深度渲染。待游戏内视觉验证，见[验证记录](docs/validation/train-height.md)。

## 依赖与安装

| Minecraft | 加载器 | Java | MTR | 可选地图 |
| --- | --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.x 或 Fabric + Fabric API | 21 | 4.1.0-beta.2 | Xaero's World Map 1.45.0+ / JourneyMap 6.0.8+ |
| 1.20.1 | Forge 47.x 或 Fabric + Fabric API | 17+ | >=4.0.5, <4.1 | Xaero's World Map 1.45.0+ / JourneyMap 6.0.6+ |
| 1.20.4 | Forge 49.x 或 Fabric + Fabric API | 17+ | >=4.0.5, <4.1 | Xaero's World Map 1.45.0+ / JourneyMap 5.10.0 |

所有依赖都必须匹配 Minecraft 版本及加载器。Xaero's Minimap 为可选依赖，只用于清理旧版本创建的 `[MTR]` 路标。

1. 从最新发布版 [v1.5.1](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.1) 下载与 Minecraft 版本及加载器完全匹配的 **一个 JAR**，放入客户端 `mods` 目录。发布包含六个 JAR：原有两个 1.21.1 NeoForge/Fabric 构建，以及文件名带 `MC1.20.1` / `MC1.20.4` 的四个 Forge/Fabric 兼容构建。
2. 安装同一加载器的 MTR 和所需地图 mod；Fabric 还必须安装 Fabric API。
3. 如需全网地图，可选地在服务器安装对应加载器版本的 MTR Map Overlay 和 MTR。客户端与服务端都必须使用新的 `mtrmap` mod ID；旧的 `mtrsurveyor` 版本与本版不兼容。
4. 打开 Xaero's World Map 或 JourneyMap 全屏地图。两端使用配套的 `ROUTES`、`TRACKS` 图标：左侧亮条绿色表示开、红色表示关；JourneyMap 的按钮位于附加按钮栏。`/mtrmap config routeLines` 和 `trackLines` 也对两种地图生效。悬停在线路或图标上可查看详情。

服务端组件为可选。v1.5.1 通过隔离客户端初始化修复 NeoForge 专用服务器启动崩溃；1.21.1 构建、共用测试与 NeoForge 专用服务器启动已通过，全屏地图视觉与跨机器联机仍待进一步验证。四个 MC 1.20 构建均通过本地开发运行环境的地图与专用服务器检查；生产 JAR 启动及跨机器联机尚未验证。详见[发布说明](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.1)和 [MC 1.20 实机验证记录](https://github.com/teamCreating/MTR-Map-Overlay/blob/compat/mtr-4.0-mc-1.20/docs/validation/mc120-runtime.md)。

## 命令与配置

命令在**客户端**注册，连接没有安装本 mod 的服务器时也能使用。

| 命令 | 作用 |
| --- | --- |
| `/mtrmap syncRoutes` | 在服务器支持时请求全网快照。 |
| `/mtrmap syncLandmarks` | 刷新 JourneyMap 地标。 |
| `/mtrmap testMarker` | 在玩家当前位置放置 JourneyMap 测试标记。 |
| `/mtrmap mode station\|platform\|both` | 选择纯客户端回退时显示的地标类型。 |
| `/mtrmap config enabled <true\|false>` | 启用或关闭地图覆盖层。 |
| `/mtrmap config showStations <true\|false>` | 显示或隐藏车站图标。 |
| `/mtrmap config showPlatforms <true\|false>` | 显示或隐藏站台图标。 |
| `/mtrmap config showDepots <true\|false>` | 显示或隐藏车辆段图标。 |
| `/mtrmap config routeLines <true\|false>` | 显示或隐藏两种地图的路线色带。 |
| `/mtrmap config trackLines <true\|false>` | 显示或隐藏两种地图的轨道层。 |

NeoForge 配置位于 `config/mtrmap.toml`；如果新配置不存在，首次启动会从旧 `mtrsurveyor.toml` 复制设置。Fabric 使用 `config/mtrmap.properties`，从自身默认值开始；两种配置格式不会自动互转。主要配置包括 `networkSync.enabled`（默认 `true`）、`networkSync.refreshIntervalSeconds`（默认 `300`）及车站、站台、车辆段可见性开关。

## 从源码构建

需要 Java 21 工具链。两种加载器使用独立的 Gradle wrapper：

下方命令构建的是 **1.21.1 主线**。Forge/Fabric 1.20.1 和 1.20.4 的源码及构建步骤位于 [MC 1.20 兼容分支](https://github.com/teamCreating/MTR-Map-Overlay/tree/compat/mtr-4.0-mc-1.20)。

| 加载器 | Windows | macOS / Linux | 产物 |
| --- | --- | --- | --- |
| NeoForge | `.\gradlew.bat build` | `./gradlew build` | `build/libs/CRTools-MTR-Map-Overlay-1.5.1.jar` |
| Fabric | `.\fabric\gradlew.bat -p fabric build` | `./fabric/gradlew -p fabric build` | `fabric/build/libs/CRTools-MTR-Map-Overlay-fabric-1.5.1.jar` |

NeoForge 构建会运行共用的 JUnit 测试。构建成功不能替代游戏内兼容性验证，尤其是 Xaero 更新内部地图渲染实现之后。

## 源码结构与数据流

NeoForge 源码在 [`src/main/java/com/lx862/mtrmap`](src/main/java/com/lx862/mtrmap)；[`fabric/`](fabric) 放置 Fabric 专用入口和适配代码，并编译共用的 Java 源码。NeoForge 的 `MTRMap` 只负责通用初始化，`MTRMapClient` 注册纯客户端事件，`MTRNetworkClient` 承接客户端网络回调。两种构建共用纹理、128×128 模组/资源包 Logo 与 `mtrmap` 标识。

| 模块 | 主要职责 |
| --- | --- |
| [`mapdata/`](src/main/java/com/lx862/mtrmap/mapdata) | `MapDataCache` 优先使用服务器快照，否则回退到 MTR 客户端附近数据；`TrackSampler` 为每根物理轨道采样并供路线复用，`TrackRoutePalette` 为共线轨道分配稳定色带，`MapTrack` 缓存包围盒以剔除视窗外轨道。 |
| [`network/`](src/main/java/com/lx862/mtrmap/network) | v5 协议和 `NetworkSnapshotCodec` 传输路线、轨道与地标；`ServerNetworkCollector` 在 MTR 模拟器线程读数据，`ClientNetworkSync` 探测并请求快照，`NetworkChunkAssembler` 校验和重组分块。 |
| [`integration/xaero/`](src/main/java/com/lx862/mtrmap/integration/xaero) | `XaeroRouteRenderer` 按世界坐标绘制轨道、路线色带和地图内图标，处理悬停提示。 |
| [`integration/journeymap/`](src/main/java/com/lx862/mtrmap/integration/journeymap) | 可选的 JourneyMap v2 插件；`JourneyMapToolbar` 提供 TRACKS/ROUTES 按钮，`JourneyMapScreenProjection` 同步平移、拖动与缩放，`JourneyMapPathManager` 用屏幕像素宽度绘制经过视窗剔除的轨道和路线，`JourneyMapForegroundRenderer` 将地标图标固定在最上层；全屏 `MarkerOverlay` 保留悬停信息。 |
| [`mixin/`](src/main/java/com/lx862/mtrmap/mixin) | 读取 MTR 数据并接入 Xaero 绘制流程；Fabric 有专用的 Xaero mixin。 |
| [`config/`](src/main/java/com/lx862/mtrmap/config) 与 [`fabric/src/main/java/`](fabric/src/main/java) | 两种加载器各自的配置、初始化、客户端命令及网络注册。 |

数据流：MTR 模拟器或客户端数据 → 按维度管理的 `MapDataCache` → Xaero 或 JourneyMap 全屏绘制器。有服务端组件时，客户端先探测各维度内容哈希，只请求变化的快照，校验并重组分块数据后更新缓存；否则使用 MTR 客户端半径范围内的数据。

## 常见问题

- **只能看到附近站点：**服务端尚未提供全网快照。可在服务端安装匹配的 mod，或继续使用纯客户端回退模式。
- **Xaero 没有线路：**确认安装的是 Xaero's **World Map**，并检查日志中是否出现 `Path layer render hook into Xaero's World Map is active`。Xaero 内部实现变化可能导致绘制钩子失效。
- **小地图没有路标：**这是预期行为，地标只在全屏地图显示。
- **从旧版升级：**请替换旧的 `mtrsurveyor` JAR，不要同时安装；新版 mod ID 和命令分别为 `mtrmap`、`/mtrmap`。

## 支持开发

如果 MTR Map Overlay 帮助你探索铁路网络，欢迎通过 Ko-fi 或爱发电支持持续开发。

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/Z8C424REOO)
<a href="https://afdian.com/a/benli06"><img src="docs/assets/badges/afdian.svg" alt="通过爱发电支持我" height="30"></a>

## 许可证与署名

本项目使用 MIT 许可证。原始贡献的 AmberFrost 版权及许可声明保留在 [`LICENSE`](LICENSE)，后续版本由 BenLi06 维护。保留 Git 提交历史与原有署名。
