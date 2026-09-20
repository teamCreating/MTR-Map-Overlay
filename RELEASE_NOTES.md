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
