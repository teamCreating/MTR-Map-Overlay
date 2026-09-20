# STATE.md — 项目权威状态

> 每次改变项目状态（版本、依赖、架构、验证结果、重大决策）后**必须**更新本文件。
> 就地编辑本文件（它反映"当前状态"，历史去看 git log 和 RELEASE_NOTES.md）。

## 当前版本

- **v1.4.2**（MTR 真实路径染色 + 严格贴轨回退 + 哈希探测热更）
- 最后更新：2026-09-20 18:35，操作者：Codex（接管 ZCode 中断工作）

## 平台与依赖

| 项 | 值 |
|---|---|
| 目标平台 | NeoForge 21.1.249 / Minecraft 1.21.1 |
| MTR | 4.1.0-beta.2（NEOFORGE-4.1.0-beta.2+1.21.1） |
| Xaero's World Map | 1.45.0+（neoforge-1.21.1） |
| Xaero's Minimap | 26.4.2+（neoforge-1.21.1） |
| JourneyMap | 1.21.1-6.0.8+（可选；v2 API 2.0.0-1.21.1） |
| 构建 | ModDevGradle 2.0.146 / Gradle 8.8 / JDK 21 |

## 架构现状

- 航点同步（Xaero Minimap 内部类直调）——稳定
- 路径层（GuiMap mixin + XaeroRouteRenderer：路线层/轨道层/开关/悬停）——稳定
- 全网同步（C2S RequestNetworkSync → Simulator 线程采集 → 200KB 分块 S2C → MapDataCache）——稳定
- 纯客户端回退（无服务端组件时用 MTR 半径数据）——稳定
- JourneyMap 地标（v2 API MarkerOverlay，station/platform/depot，自动检测）——稳定（实机验证）
- 路线颜色染色（服务端 depot 真实路径 + 协议 v3 哈希探测热更）——稳定（实机验证）
- 无 Depot 路径回退（铁路网最短路 + 与 TrackSampler 完全相同的几何采样）——无头测试通过，待 Ben 实机验证

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

- 2026-09-20：v1.4.2 修复无 Depot 路径时的站点直线回退；回退路径必须来自铁路网且逐点复用
  轨道层采样，断路时不绘制伪造直线。无头测试通过，未由 agent 启动 Minecraft。

- 2026-09-20：v1.4.1 路线颜色改用 MTR 真实寻路结果（Depot.getPath）+ 哈希探测热更（协议 v3）；
  Dijkstra 自研寻路移除。

- 2026-09-20：v1.4.0 路线寻路吸附上线（RoutePathfinder + 协议 v2 + 车道偏移），实机截图验证三线并行。

- 2026-09-16：v1.3.0 统一版——JourneyMap 集成并入主 mod（v2 API），实机验证通过。

- 2026-09-05：v1.2.0 完成 Forge 1.20.1 → NeoForge 1.21.1 整体移植并实机验证（commit fa8ff07）。
- 2026-09-16：回退事故（某 agent 按过时认知暂存了旧 Forge 架构，后自行 reset 丢弃；HEAD 无损）。
  详见 AGENTS.md §4。
