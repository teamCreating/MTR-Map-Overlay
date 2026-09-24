<div align="center">
  <img src="src/main/resources/pack.png" alt="MTR Map Overlay 标志" width="128" height="128">

  <h1>MTR Map Overlay</h1>

  <p>在地图上查看 Minecraft Transit Railway 的路线、轨道和车站。</p>

  <p><a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="https://github.com/teamCreating/MTR-Xareo-Mapper/releases/tag/v1.4.6">下载 v1.4.6</a></p>
</div>

MTR Map Overlay 是适用于 **Minecraft 1.21.1 NeoForge 或 Fabric** 的地图扩展。它读取 [Minecraft Transit Railway（MTR）](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) 的数据，绘制到 Xaero's World Map 和 JourneyMap；不依赖 MTR Surveyor 的地图。

## 功能

| 地图 | 显示内容 |
| --- | --- |
| Xaero's World Map | 真实轨道几何、路线色带，以及小型车站、站台、车辆段图标；悬停查看详情。 |
| JourneyMap | 仅在全屏地图显示真实轨道、共线路线色带，以及车站、站台和车辆段标记。 |

这些是**地图内图标**，不是普通 Xaero 路标，不会挤满路标列表、指南针、小地图或游戏内 HUD。同一条物理轨道有多条路线时，颜色并排显示，不再互相覆盖。拖动和缩放 Xaero 地图时，站点与站台图标和轨道使用同一地图坐标变换。

客户端和服务端都安装本 mod 时，可按维度获取**全网快照**。如果服务端没有安装，本 mod 仍可工作，但只能显示 MTR 已同步到客户端的附近数据。Xaero 与 JourneyMap 都是可选集成，可以只装其中一个，也可以同时安装。

## 依赖与安装

| 组件 | 要求 |
| --- | --- |
| Minecraft | 1.21.1、Java 21 |
| 加载器 | NeoForge 21.1.x **或** Fabric Loader + Fabric API |
| MTR | 与所选加载器对应的 4.1.0-beta.2 |
| 地图 mod | 与加载器对应的 Xaero's World Map 1.45.0+ 和/或 JourneyMap 6.0.8+ |
| Xaero's Minimap | 可选；只用于清理旧版本创建的 `[MTR]` 路标 |

1. 从 [Releases](https://github.com/teamCreating/MTR-Xareo-Mapper/releases/tag/v1.4.6) 下载与你的加载器对应的 **NeoForge 或 Fabric JAR**，放入客户端 `mods` 目录。**不要同时安装两个版本。**
2. 安装同一加载器的 MTR 和所需地图 mod；Fabric 还必须安装 Fabric API。
3. 如需全网地图，可选地在服务器安装对应加载器版本的 MTR Map Overlay 和 MTR。客户端与服务端都必须使用新的 `mtrmap` mod ID；旧的 `mtrsurveyor` 版本与本版不兼容。
4. 打开 Xaero's World Map 或 JourneyMap 全屏地图。Xaero 左上角有 `ROUTES`、`TRACKS` 按钮；JourneyMap 的附加按钮栏有 `ROUTE`、`TRACK` 开关。`/mtrmap config routeLines` 和 `trackLines` 也对两种地图生效。悬停在线路或图标上可查看详情。

纯客户端使用不要求服务端安装。v1.4.6 的 Fabric 构建和 JAR 静态检查已通过，但 Xaero 渲染钩子与跨机器联机尚未手动实机验证，详见[发布说明](RELEASE_NOTES.md)。

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

| 加载器 | Windows | macOS / Linux | 产物 |
| --- | --- | --- | --- |
| NeoForge | `.\gradlew.bat build` | `./gradlew build` | `build/libs/CRTools-MTR-Map-Overlay-1.4.6.jar` |
| Fabric | `.\fabric\gradlew.bat -p fabric build` | `./fabric/gradlew -p fabric build` | `fabric/build/libs/CRTools-MTR-Map-Overlay-fabric-1.4.6.jar` |

NeoForge 构建会运行共用的 JUnit 测试。构建成功不能替代游戏内兼容性验证，尤其是 Xaero 更新内部地图渲染实现之后。

## 源码结构与数据流

NeoForge 源码在 [`src/main/java/com/lx862/mtrmap`](src/main/java/com/lx862/mtrmap)；[`fabric/`](fabric) 放置 Fabric 专用入口和适配代码，并编译共用的 Java 源码。两种构建共用纹理、128×128 模组/资源包 Logo 与 `mtrmap` 标识。

| 模块 | 主要职责 |
| --- | --- |
| [`mapdata/`](src/main/java/com/lx862/mtrmap/mapdata) | `MapDataCache` 优先使用服务器快照，否则回退到 MTR 客户端附近数据；`TrackSampler` 采样真实轨道，`TrackRoutePalette` 为共线轨道分配稳定色带。 |
| [`network/`](src/main/java/com/lx862/mtrmap/network) | v5 协议和 `NetworkSnapshotCodec` 传输路线、轨道与地标；`ServerNetworkCollector` 在 MTR 模拟器线程读数据，`ClientNetworkSync` 探测变化、请求并重组分块快照。 |
| [`integration/xaero/`](src/main/java/com/lx862/mtrmap/integration/xaero) | `XaeroRouteRenderer` 按世界坐标绘制轨道、路线色带和地图内图标，处理悬停提示。 |
| [`integration/journeymap/`](src/main/java/com/lx862/mtrmap/integration/journeymap) | 可选的 JourneyMap v2 插件、全屏 `MarkerOverlay` 地标，以及 `PolygonOverlay` 轨道/路线色带。 |
| [`mixin/`](src/main/java/com/lx862/mtrmap/mixin) | 读取 MTR 数据并接入 Xaero 绘制流程；Fabric 有专用的 Xaero mixin。 |
| [`config/`](src/main/java/com/lx862/mtrmap/config) 与 [`fabric/src/main/java/`](fabric/src/main/java) | 两种加载器各自的配置、初始化、客户端命令及网络注册。 |

数据流：MTR 模拟器或客户端数据 → 按维度管理的 `MapDataCache` → Xaero 绘制器或 JourneyMap 覆盖层。有服务端组件时，客户端先探测各维度内容哈希，只请求变化的快照，再重组分块数据并更新缓存；否则使用 MTR 客户端半径范围内的数据。

## 常见问题

- **只能看到附近站点：**服务端尚未提供全网快照。可在服务端安装匹配的 mod，或继续使用纯客户端回退模式。
- **Xaero 没有线路：**确认安装的是 Xaero's **World Map**，并检查日志中是否出现 `Path layer render hook into Xaero's World Map is active`。Xaero 内部实现变化可能导致绘制钩子失效。
- **小地图没有路标：**这是预期行为，地标只在全屏地图显示。
- **从旧版升级：**请替换旧的 `mtrsurveyor` JAR，不要同时安装；新版 mod ID 和命令分别为 `mtrmap`、`/mtrmap`。

## 许可证与署名

本项目使用 MIT 许可证。原始贡献的 AmberFrost 版权及许可声明保留在 [`LICENSE`](LICENSE)，后续版本由 BenLi06 维护。保留 Git 提交历史与原有署名。
