# STATE.md — 项目权威状态

> 每次改变项目状态（版本、依赖、架构、验证结果、重大决策）后**必须**更新本文件。
> 就地编辑本文件（它反映"当前状态"，历史去看 git log 和 RELEASE_NOTES.md）。

## 当前版本

- **v1.5.2 开发版（main）**：真实 XYZ 附近车厢投影为纯 2D，固定轨道→线路→车厢层次；GitHub 已发布版本仍为 v1.5.1。
- 项目标识：MTR Map Overlay；mod ID `mtrmap`；Java 包 `com.lx862.mtrmap`；客户端命令 `/mtrmap`
- 最后更新：2026-10-21 19:25，操作者：Codex
- 版本管理：按用户要求固定 1.5.2，只有用户明确指示时才更改版本号；禁止自动递增。
- main 保留 NeoForge/Fabric MC 1.21.1；`compat/mtr-4.0-mc-1.20` 另提供 MC 1.20.1/1.20.4 Forge/Fabric + MTR 4.0.5 构建。v1.5.1 发布包含六个 JAR，按精确 Minecraft 版本及加载器选择。
- 中英文 README 已同步六个附件的依赖矩阵、官网文档及 Modrinth/CurseForge、Ko-fi/爱发电入口；仅修改文档及 badge 资产。

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

- 2026-10-21 17:30，Codex：按用户要求高铁宽度权重 1.4→1.0，与普通 Rail 等宽；轻铁仍 0.7，高铁彩白块保留。现有共线几何测试检查等宽及白块边界，68 普通测试与双构建通过；本次参数调整未重新启动游戏。版本 1.5.2 / 协议 v8 保持，测试包 ../test-builds/v1.5.2-highspeed-standard-width。

- 2026-10-21 14:39，Codex：修复共线→单线结束色带方形台阶及彩色→灰轨底衬叠加宽度楔形；两侧共享实际截面，结束色带在本轨道内 smoothstep 收束，白块 inset 随窄带钳制。参考 Create 世界空间缩放，轨道和总色带随地图缩小，仅整束保留 0.3 GUIpx 半宽下限，肩部/边缘同步收细；轻铁/普通/高铁权重和四线路宽度上限保持。68 普通测试、双构建和 NeoForge Xaero 3×/1.403×、JourneyMap 实机复查通过。详见 `docs/validation/route-joins-zoom.md`；1.5.2 / 静态 v8 / 纯 2D 保持，无网络改动。

- 2026-10-21 14:08，Codex：参考 Create 车厢图标改为最小 7 GUIpx 车宽、白色倒角轮廓/深色框、线路色侧边、浅色车顶及端窗；金色车头箭头读取实际 reversed，缩远为 12×7 定向图标。真实 XYZ/朝向保持，单符号单 hover target，视口含额外显示边距。每节 8/9 quads、三节 25（旧 12），增加为常数绘制成本，静态缓存/网络不增加；未测大车队 FPS。65 普通测试与双平台构建通过，NeoForge Xaero 三节弯道/缩远及 JourneyMap 悬停实测通过；详见 `docs/validation/train-style.md`。版本仍 1.5.2、协议 v8、纯 2D；全图动态流/ETA 仍待实现。

- 2026-10-21 13:34，Codex：修复细线曲线仍为 8 格折线的问题，新增 NativeRailCurve 原生参数导出/解析求值、按 GUI 屏幕误差细分及原生切向，底衬两侧逐顶点 alpha 过渡。Native 曲线 LOD 在每 host 缓存，仅当前视口、最多 maxQuads 个点，车辆更新复用；Core 初始化包围盒扫描有界。**静态协议现为 v8 / `_v8`，两端需一起更新，mod 仍 1.5.2、纯 2D。** 61 普通测试通过，生成用例跳过，双平台构建通过；NeoForge 已在约 34× 地图缩放查看绿色轻铁弯道。数值/成本/限制见 `docs/validation/native-curves.md`。以下 v7 记录为历史状态。

- 2026-10-21 12:53，Codex：实际启动 NeoForge 1.21.1，两个地图均验证 3 节待发车厢及悬停真实 XYZ；移除误用的 getIsOnRoute 过滤、加入限频采集诊断/中性色及缩远标记对比轮廓。曲线端点归一/共享截面、岔口主走廊延续、共线宽度渐变、同色往返去重、Depot 未实际染色线路恢复回退；53 普通测试通过（生成用例显式跳过），双平台构建通过。生成测试存档改为 -PgenerateTestWorld 显式触发。纯 2D / 1.5.2 / 静态 v7 保持。**全网动态流和 ETA 仍未实现，实机车辆为停放状态，未验证长时间营运移动。** 详见 `docs/validation/runtime-trains-routes.md`。

- 2026-10-21，Codex：落实 Create 式静态/动态分离缓存。两个地图各自使用 PlanarMapCache，snapshot identity/scale/开关/视口 guard 失效；静态 quad list 共享，姿态不变整场景复用，pose 更新仅重建车厢。每 host 最多保留 100000 总 quads，超限正确无缓存绘制，源 identity 弱引用，连接清理释放。48 测试和双平台构建通过；120 帧合成几何工作负载 static builds 120→1、线程分配约 674→14MiB，非游戏 FPS。详见 `docs/validation/planar-cache.md`。纯 2D、协议 v7、版本 1.5.2 保持；全网动态通信未实现。

- 2026-10-21，Codex：按用户要求改为纯 2D，PlanarMapScene 使用 primitive X/Z 四边形，移除所有高度细分/排序和轨道遮车判断；hover 只检查每个可见车厢/标记一个目标。原生 XYZ、协议 v7、样式、实际端点连接语义保持。39 测试和双平台构建通过，三种坡度数量场景均为 52000 quads，预览已检查，未游戏内实测。见 `docs/validation/planar-map.md`；以下历史高度记录已经被此项取代。

- 独立 MTR 地图叠加项目，直接读取 MTR 数据并绘制到 Xaero / JourneyMap；不集成 MTR Surveyor 地图。
- NeoForge 与 Fabric 使用各自入口、配置、客户端命令及网络适配，共用地图数据、渲染与协议 v8（轨道 double XYZ + 原生曲线，独立 `_v8` 通道，需匹配客户端和服务端）。双平台 v1.4.6 已正式发布，说明基于上一正式版 v1.4.4 的差异；误发的 v1.4.7 预发布和 tag 已删除。两平台构建及 NeoForge 单元测试通过。
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
- 渲染与同步边界检查（轨道包围盒用于 Xaero/JourneyMap 视口剔除；JourneyMap 图标按纹理/颜色/透明度合批；分块重组限制大小并校验元数据；切服清理客户端回退缓存）——NeoForge/Fabric 构建及单测通过，待实机验证

- 2026-10-18 三种路线样式：Core RouteType 进入服务端、客户端车辆回退、路线 palette 和协议 v6；更改类型会触发哈希更新。轻铁 70%、普通 100%、高铁 140% 且彩白交替、连续色边。共线权重分配，四条以内保留单独宽度，更多路线限制总宽；物理连续链统一方向及累计相位，环线适配完整块对，低缩放/窄色带简化。
- 2026-10-18 验证：NeoForge 与 Fabric 构建通过，NeoForge 29 项测试通过；共用几何生成无头 PNG 预览并检查。未启动 Minecraft，尚未做游戏内视觉及新旧客户端混连验证。详见 `docs/validation/route-styles.md`。MC 1.20 兼容分支本次未改动，仍为 v1.5.1。

- 2026-10-19，Codex：MTR 原生车体/转向架姿态 → MapCar XYZ，两个地图共用 HeightMapScene，坡道片段按高度排序，上层轨道遮挡下穿车厢及其悬停；配置/命令 trainCars，默认开启。仅附近玩家维度数据，不含 ETA。轨道采样与静态快照保留原生 Y，协议 v7 double XYZ；负高度及远坐标精度已验证。
- 验证：37 项测试、NeoForge/Fabric 构建通过，实际共用几何的合成高度预览已检查；未启动 Minecraft，未做性能基准。完整限制和待验项目见 `docs/validation/train-height.md`。MC 1.20 兼容分支未改动。

## 已验证功能（最近一次实机测试）

- 2026-09-05，runClient + quickplay TestWorld + MTR/Xaero 实装：
  - mixin 应用、渲染钩子激活（日志 `Path layer render hook`）、ROUTES/TRACKS 开关可见（截图确认）
  - 全网同步全链路（三个维度快照 请求→发送→应用）
  - 会话无 mod 相关报错
- 回归测试清单：见 `walkthrough.md` §7

## 后续设计（部分实现）

- 2026-10-21 19:25，Codex：用户接受 Create 式抽象车厢过弯/加减速，不需侧倾。`docs/plans/global-train-sync.md` 现为 **TSC 原生位置→每节两个 XYZ 锚点→全网下发→客户端插值/2D 符号**，取代 full-bogie/tilt 和客户端 MTR 3D 变换。两点 6 个局部 float=24B/节，另每列 double XYZ 原点=24B；1000列×8节×4Hz 仅参考点约768kB/s，含原点约864kB/s（不含其他字段/压缩，估算）。全网覆盖、共享4Hz待测、客户端无模拟/沿轨求解保持；不依赖光照，Core原生查询成本尚未减少。G0两点提取/精度→G1完整全网流→G2显示/成本→G3异常/平台。距离/ETA暂停；仅规划，无新功能/测试包/性能实测，1.5.2/静态v8保持。

- 下列 2026-10-19/20/21 早期设计记录为历史；其中路径进度客户端求解、视口细节订阅、下穿和 PIDS 实施顺序已被最新要求取代。

- 2026-10-21，Codex：下穿时空复杂度见 `docs/plans/underpass-complexity.md`。当前细分画家每帧 O(R+A+P+H+C+Q log Q)、工作空间 O(Q)，不做车厢/轨道全对全相交。数量 probe 确认 10000 段/4 线路/500 车厢的合成场景平地 52000、坡道最高 209000 quads；未做帧时间或实际内存测量。优先静态缓存/对象复用、段级索引，严格局部遮挡另评估独立深度；保持 1.5.2。

- 2026-10-21，Codex：Create 源码研究见 `docs/plans/create-train-map-research.md`，固定 `mc1.21.1/dev` 的 `7ff2760e4924d35d4bc2e22c4b19923f0178cd16`。确认独立地图订阅、全网车厢两点 float 流、定义独立同步、到包插值与静态纹理缓存；light codec 仍含文本，高度为近似。调整 G0 先做全网原生姿态端到端原型，进度优化按实测决定，静态缓存前移。仅研究/文档，当前功能与 1.5.2 不变。

- 2026-10-20，Codex：用户明确要求全图列车。详细通信方案见 `docs/plans/global-train-sync.md`：服务端全网概要 2Hz＋任意地图视口完整车厢进度 4Hz、共享定义/采集、能力/租约、原子分片、修订/删除/过期与预算；当前仅规划，尚未实现，频率和性能均为待测目标。正式验收必须包含远离所有玩家/未加载区块中的实际列车及其完整车厢。版本保持 1.5.2。

- 2026-10-19，Codex：列车车厢/位置/所属线路/ETA 规划见 `docs/plans/train-map-design.md`，按实际 Core 4.1 API 核验，分 P0–P4 验收。后续按用户要求先实现附近车厢 XYZ 和高度绘制；全网动态流及 ETA 仍待实现。

## 已知限制

- 只渲染 TRAIN 模式轨道（路径层；JourneyMap 地标不受限）
- 附近车厢已有真实 XYZ 的纯 2D 投影；全网动态列车同步与 ETA 未实现。按用户要求不再做高度/下穿遮挡；游戏内姿态与地图集成待验证。
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
