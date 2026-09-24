# STATE.md — 项目权威状态

> 每次改变项目状态（版本、依赖、架构、验证结果、重大决策）后**必须**更新本文件。
> 就地编辑本文件（它反映"当前状态"，历史去看 git log 和 RELEASE_NOTES.md）。

## 当前版本

- **v1.4.6**（新增 Fabric 1.21.1 并行构建，保留 NeoForge）
- 项目标识：MTR Map Overlay；mod ID `mtrmap`；Java 包 `com.lx862.mtrmap`；客户端命令 `/mtrmap`
- 最后更新：2026-09-25 00:32，操作者：Codex

## 平台与依赖

| 项 | 值 |
|---|---|
| 目标平台 | NeoForge 21.1.249 或 Fabric Loader 0.16.14 + Fabric API 0.116.17 / Minecraft 1.21.1 |
| MTR | 4.1.0-beta.2（NEOFORGE-4.1.0-beta.2+1.21.1） |
| Xaero's World Map | 1.45.0+（neoforge-1.21.1） |
| Xaero's Minimap | 26.4.2+（neoforge-1.21.1） |
| JourneyMap | 1.21.1-6.0.8+（可选；v2 API 2.0.0-1.21.1） |
| 构建 | NeoForge：ModDevGradle 2.0.146 / Gradle 8.8；Fabric：Loom 1.16.3 / Gradle 9.4.1；JDK 21 |

## 架构现状

- 独立 MTR 地图叠加项目，直接读取 MTR 数据并绘制到 Xaero / JourneyMap；不集成 MTR Surveyor 地图。
- NeoForge 与 Fabric 使用各自入口、配置、客户端命令及网络适配，共用地图数据、渲染与协议 v5。双平台 v1.4.6 已正式发布，说明基于上一正式版 v1.4.4 的差异；误发的 v1.4.7 预发布和 tag 已删除。两平台构建及 NeoForge 单元测试通过，待 Ben 实机验证。
- 旧 `mtrsurveyor.toml` 在新配置不存在时复制为 `mtrmap.toml`；全网同步需客户端和服务端都运行 `mtrmap` ID 版本。
- Xaero 地标图标（World Map overlay 直接绘制；不进入 waypoint/Minimap/HUD）——构建通过，待 Ben 实机验证
- Xaero 地标与轨道共用世界坐标变换，站点/站台拖动缩放不再按屏幕像素取整；Xaero/JourneyMap 站点纹理按实际 32×32 完整采样——构建/测试通过，待 Ben 实机验证
- 路径层（GuiMap mixin + XaeroRouteRenderer：路线层/轨道层/开关/悬停）——稳定
- 全网同步（C2S RequestNetworkSync → Simulator 线程采集 → 200KB 分块 S2C → MapDataCache）——稳定
- 纯客户端回退（无服务端组件时用 MTR 半径数据）——稳定
- JourneyMap 地标（v2 API MarkerOverlay，station/platform/depot，自动检测）——稳定（实机验证）
- 路线颜色染色（服务端 depot 真实路径 + 协议 v4；ROUTE/TRACK 共用 MapTrack 与 drawPolyline）——无头测试通过，待 Ben 实机验证
- 无 Depot 路径回退（铁路网最短路，直接返回 TRACK 层 MapTrack）——无头测试通过，待 Ben 实机验证
- 全图地图地标（协议 v5 服务端地标快照；Xaero 直接绘制，JourneyMap 仅 Fullscreen）——构建/测试通过，待 Ben 实机验证
- 共线彩虹带（唯一物理轨道一次绘制；route ID 稳定排序的横向色带；真实轨道悬停）——构建/测试通过，待 Ben 实机验证
- 轨道采样复用（服务端每个快照按物理轨道采样一次，客户端车辆路径复用 TRACK 几何；仅有回退路线时构建寻路图）——NeoForge/Fabric 构建及单测通过，待实机验证

## 已验证功能（最近一次实机测试）

- 2026-09-05，runClient + quickplay TestWorld + MTR/Xaero 实装：
  - mixin 应用、渲染钩子激活（日志 `Path layer render hook`）、ROUTES/TRACKS 开关可见（截图确认）
  - 全网同步全链路（三个维度快照 请求→发送→应用）
  - 会话无 mod 相关报错
- 回归测试清单：见 `walkthrough.md` §7

## 已知限制

- 只渲染 TRAIN 模式轨道（路径层；JourneyMap 地标不受限）
- 列车实时位置未上地图（数据已具备：`MinecraftClientData.vehicles`）
- 洞穴图层下路径线悬浮（与 Create 行为一致）

## 历史要点

- 2026-09-23：v1.4.5 撤销将全量地标写入 Xaero waypoint 的方案。Xaero 改为在 `GuiMap`
  直接绘制固定像素小图标；JourneyMap `MarkerOverlay` 限定 `Fullscreen`，不在 Minimap 显示。
  旧 `[MTR]` Xaero waypoint 首次进入世界时自动清理。

- 2026-09-22：v1.4.4 协议升级 v5，完整快照新增站点/站台/车辆段；路线改为引用唯一 rail ID。
  Xaero 对全维度地标做增量对账，附近客户端回退不再清除远处持久航点。共享物理轨道沿 TRACK
  原几何只绘制一次，并按稳定路线顺序横向分成多色 ribbon。

- 2026-09-20：v1.4.3 删除独立路线折线绘制模型；ROUTE 改为物理轨道 `MapTrack` 列表，
  与 TRACK 共用同一 `drawPolyline`、线宽、透明度和视口裁剪。快照协议升级 v4。

- 2026-09-20：v1.4.2 修复无 Depot 路径时的站点直线回退；回退路径必须来自铁路网且逐点复用
  轨道层采样，断路时不绘制伪造直线。无头测试通过，未由 agent 启动 Minecraft。

- 2026-09-20：v1.4.1 路线颜色改用 MTR 真实寻路结果（Depot.getPath）+ 哈希探测热更（协议 v3）；
  Dijkstra 自研寻路移除。

- 2026-09-20：v1.4.0 路线寻路吸附上线（RoutePathfinder + 协议 v2 + 车道偏移），实机截图验证三线并行。

- 2026-09-16：v1.3.0 统一版——JourneyMap 集成并入主 mod（v2 API），实机验证通过。

- 2026-09-05：v1.2.0 完成 Forge 1.20.1 → NeoForge 1.21.1 整体移植并实机验证（commit fa8ff07）。
- 2026-09-16：回退事故（某 agent 按过时认知暂存了旧 Forge 架构，后自行 reset 丢弃；HEAD 无损）。
  详见 AGENTS.md §4。
