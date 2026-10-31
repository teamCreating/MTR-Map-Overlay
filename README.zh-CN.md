<div align="center">
  <img src="src/main/resources/pack.png" alt="MTR Map Overlay logo" width="128" height="128">

  <h1>MTR Map Overlay</h1>

  <p>在全屏地图上直接绘制 Minecraft Transit Railway (MTR) 的轨道、彩色线路、实时列车与车站到站显示屏。</p>

  <p>
    <a href="README.md">English</a> · 
    <a href="README.zh-CN.md">简体中文</a> · 
    <a href="https://mtrmapoverlay.benli06.site/zh/">官网与文档</a> · 
    <a href="https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3">v1.5.3 预览版</a> · 
    <a href="https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/snapshot">快照构建</a>
  </p>

  <p>
    <a href="https://modrinth.com/project/ZU7SzyH7"><img src="docs/assets/badges/modrinth-cozy.svg" alt="Available on Modrinth" height="56"></a>
    <a href="https://www.curseforge.com/minecraft/mc-mods/mtr-map-overlay"><img src="docs/assets/badges/curseforge-cozy.svg" alt="Available on CurseForge" height="56"></a>
  </p>
</div>

**MTR Map Overlay** 可将 [Minecraft Transit Railway (MTR)](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) 的交通网络直接渲染至 **Xaero's World Map（Xaero 的世界地图）** 与 **JourneyMap（旅行地图）**。本模组直接读取 MTR 原生数据（不依赖 MTR Surveyor），支持显示物理铁轨、彩色运营线路、实时运行的列车位置以及交互式车站 PIDS 到站看板。

**当前支持版本（`v1.5.3`）：** Minecraft **1.20.1 / 1.20.4**（Forge & Fabric）以及 **1.21.1**（NeoForge & Fabric）。

## 核心特性

| 地图模组 | 支持的叠加层功能 |
| :--- | :--- |
| **Xaero's World Map** | 物理铁轨、彩色线路带、实时列车、车站/站台/车厂图标，以及可固定的车站实时到站信息卡（PIDS）。 |
| **JourneyMap** | 全屏物理铁轨、共线彩色线路带、实时列车与车站信息板；在工具栏提供独立的 `TRACKS` 与 `ROUTES` 图层开关（兼容 JourneyMap 5 & 6）。 |

- **纯净的全屏地图图标**：车站、站台与车厂仅作为地图图层绘制，**不会**往 Xaero 路径点列表、罗盘、小地图或游戏画面 HUD 里塞满坐标点。
- **共线并排与多制式区分**：多条线路共用同一段铁轨时，会以并排色带绘制而不会互相遮盖。轻轨采用 70% 标准宽度渲染；高铁则采用“线路色+白块”交替样式并带有连续外边框。
- **全维度实时列车**：当服务端与客户端同时安装本模组时，打开地图即可按约 4 Hz 频率同步当前维度的全部运行列车。鼠标悬停在车厢上可查看车型名称、所属线路、终点站及编组节数。
- **交互式车站 PIDS 看板**：点击任意车站可固定信息卡片，点击 **Expand（展开）** 即可实时查看各站台、各线路的列车到站倒计时。支持通过 `/mtrmap config pidsStyle british|european` 切换英式（黑底琥珀字）与欧式（机场风格）显示屏主题。
- **纯客户端兼容**：若服务端未安装本模组，客户端依然可以利用 MTR 同步的周围区块数据正常渲染附近路网；在服务端安装本模组后即可解锁全图路网快照、全图实时列车与实时到站预测。

## v1.5.3 更新亮点

> **版本状态说明：** [v1.5.3](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3) 目前标记为 **Pre-release（预览版）**，最新稳定版为 [v1.5.2](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.2)。全图实时列车与车站到站看板需要客户端与服务端同时安装相同版本的 `1.5.3` 构建。

- **固定车站卡片与实时到站（PIDS）**：直接接入 MTR/TSC 的 PIDS 接口获取预计到站时间；无预测数据时明确显示为 `-` 而非 `0 分钟`。收起卡片或关闭地图时会自动释放订阅。
- **PIDS 主题切换**：新增 `british`（默认）与 `european` 两种车站看板样式，修改后自动保存。
- **导航扩展 API（Java 17）**：面向第三方寻路/导航模组开放 `MtrMapApi`，支持读取路网目录、订阅车站看板及创建独立的地图高亮视图。当安装了兼容的第三方导航插件时，点击 **Navigate** 即可高亮前方乘车区间并压暗已驶过或无关的轨道（详见 [API 接入指南](docs/api/overlay-api-v1.md)）。
- **交互与解码优化**：固定后的车站卡片支持独立内部滚动与点击，不会误触底层地图拖拽；网络包采用后台解码与有界队列以减少主线程开销。

## 环境要求与安装

| Minecraft | 加载器 (Loader) | Java | MTR 版本要求 | 兼容的地图模组（任选其一或同时安装） |
| :--- | :--- | :--- | :--- | :--- |
| **1.21.1** | NeoForge 21.1.x / Fabric (+ Fabric API) | 21 | `4.1.0-beta.2` | Xaero's World Map `1.45.0+` / JourneyMap `6.0.8+` |
| **1.20.1** | Forge 47.x / Fabric (+ Fabric API) | 17+ | `>=4.0.5, <4.1` | Xaero's World Map `1.45.0+` / JourneyMap `6.0.6+` |
| **1.20.4** | Forge 49.x / Fabric (+ Fabric API) | 17+ | `>=4.0.5, <4.1` | Xaero's World Map `1.45.0+` / JourneyMap `5.10.0` |

*注：无需安装 Xaero's Minimap（小地图）；若已安装，本模组仅会用它来清理早期版本遗留的 `[MTR]` 旧路径点。*

1. 前往 [Releases](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3) 下载与你的 **MC 版本 + 加载器** 完全匹配的单个 JAR 文件，放入客户端 `.minecraft/mods` 目录（文件名中已标明对应的加载器与 MTR 版本）。
2. 安装对应版本的 **MTR** 与任意一款支持的地图模组（Fabric 环境需额外安装 **Fabric API**）。
3. *（可选但推荐）* 在服务端同样放入匹配的 **MTR Map Overlay** JAR 文件，以启用全维度路网同步、全图实时列车与车站到站看板。*（注意：新版 Mod ID 已统一为 `mtrmap`，升级前请删除旧版 `mtrsurveyor` 文件。）*
4. 进入游戏并打开全屏地图，可通过地图界面上的 `ROUTES`（线路）和 `TRACKS`（轨道）图标按钮（绿条为开启，红条为关闭）或客户端指令控制图层显示。

## 指令与配置

所有 `/mtrmap` 指令均在**客户端**注册，即使服务端未安装本模组也可正常使用：

| 指令 | 功能说明 |
| :--- | :--- |
| `/mtrmap syncRoutes` | 向服务端请求一次全维度路网快照（需服务端支持）。 |
| `/mtrmap syncLandmarks` | 刷新 JourneyMap 地标图层。 |
| `/mtrmap testMarker` | 在 JourneyMap 当前玩家位置生成一个测试标记。 |
| `/mtrmap mode station\|platform\|both` | 设置纯客户端模式下的地标回退显示粒度。 |
| `/mtrmap config enabled <true\|false>` | 启用或禁用整个地图叠加层。 |
| `/mtrmap config routeLines <true\|false>` | 显示或隐藏彩色运营线路。 |
| `/mtrmap config trackLines <true\|false>` | 显示或隐藏物理铁轨。 |
| `/mtrmap config trainCars <true\|false>` | 显示或隐藏实时列车图层（默认：`true`）。 |
| `/mtrmap config showStations <true\|false>` | 显示或隐藏车站图标。 |
| `/mtrmap config showPlatforms <true\|false>` | 显示或隐藏站台图标。 |
| `/mtrmap config showDepots <true\|false>` | 显示或隐藏车厂图标。 |
| `/mtrmap config pidsStyle [british\|european]` | 切换或查询车站 PIDS 显示屏主题（默认：`british`）。 |

**配置文件路径：**
- **NeoForge / Forge**：位于 `config/mtrmap.toml`（首次启动若检测到旧版 `mtrsurveyor.toml` 会自动迁移配置）。
- **Fabric**：位于 `config/mtrmap.properties`。

## 从源码构建

需要 **Java 21** 工具链。`main` 主分支对应 **Minecraft 1.21.1**；如需构建 `1.20.1` 或 `1.20.4` 版本，请切换至 [`compat/mtr-4.0-mc-1.20`](https://github.com/teamCreating/MTR-Map-Overlay/tree/compat/mtr-4.0-mc-1.20) 分支。

| 加载器 | Windows | macOS / Linux | 构建产物路径 |
| :--- | :--- | :--- | :--- |
| **NeoForge** | `.\gradlew.bat build` | `./gradlew build` | `build/libs/CRTools-MTR-Map-Overlay-1.5.3.jar` |
| **Fabric** | `.\fabric\gradlew.bat -p fabric build` | `./fabric/gradlew -p fabric build` | `fabric/build/libs/CRTools-MTR-Map-Overlay-fabric-1.5.3.jar` |

## 常见问题排查 (Troubleshooting)

- **地图上只显示附近的车站和轨道**：服务端未安装本模组（或双端版本不一致），此时客户端会自动回退为仅显示 MTR 默认同步半径内的数据。
- **Xaero 地图上没有任何线条**：请确认安装的是 **Xaero's World Map（世界地图）** 而非仅有小地图，并检查 `latest.log` 中是否输出了 `Path layer render hook into Xaero's World Map is active`。
- **小地图或游戏画面里没有路径点**：这是正常设计。所有图标仅绘制在全屏大地图上，避免遮挡日常游戏视野。
- **从旧版 `mtrsurveyor` 升级失败**：请先删掉 `mods` 文件夹里的旧版 `mtrsurveyor` JAR 包，切勿新旧混装。

## 支持开发

如果 MTR Map Overlay 对你的铁路建设有所帮助，欢迎通过 Ko-fi 或爱发电支持后续开发：

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/Z8C424REOO)
<a href="https://afdian.com/a/benli06"><img src="docs/assets/badges/afdian.svg" alt="Support me on Afdian · 爱发电" height="30"></a>

## 开源协议与致谢

本项目采用 [MIT 协议](LICENSE) 开源。AmberFrost 的早期贡献版权声明保留于 [`LICENSE`](LICENSE) 及 Git 提交历史中，后续版本由 BenLi06 维护开发。
