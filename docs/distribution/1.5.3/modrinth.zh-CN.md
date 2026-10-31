# MTR Map Overlay

在 Xaero 世界地图与 JourneyMap 中查看你的 Minecraft Transit Railway 路网：彩色路线、真实轨道、运行列车和原生站点到站预测。

体验 [v1.5.3 预发布版](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3)，或选择 [v1.5.2 稳定版](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.2)。

## 看清整个铁路网络

- 真实轨道几何与线路色带跟随 MTR 路网；共线轨道并排显示颜色，轻铁更纤细，高铁采用彩白交替块。
- 全图实时列车显示当前查看维度中远离玩家的运行车辆。缩远显示紧凑图标，悬停可读取已有线路、终点与编组信息。
- 车站、站台和车辆段图标仅显示在全屏地图内，不挤占普通 Xaero 路标列表、指南针、小地图或游戏内 HUD。
- 两种地图均提供独立的 TRACKS／ROUTES 控件，按需查看图层。

## 1.5.3 新增：站点预测与 PIDS

点击车站固定原信息框，选择 **Expand**，按目的地、线路与站台查看 MTR/TSC 原生预测。默认折叠，预测缺失时明确提示。

可选英式黑底琥珀字或欧式／机场风格。`/mtrmap config pidsStyle british|european` 即时切换并保存；折叠到站板或关闭地图后释放预测订阅。

## 支持导航扩展

公共 API 允许第三方模组读取原生路网、订阅到站板和控制独立地图视图。注册服务后启用站点 **Navigate** 入口。导航模组负责规划和行程进度，Overlay 可高亮待乘区段，分别淡化已走及未用轨道。

当前不捆绑导航服务。[开发者 API 指南](https://github.com/teamCreating/MTR-Map-Overlay/blob/main/docs/api/overlay-api-v1.md)。

## 兼容平台

| Minecraft | Loader | Java | MTR | Map versions |
| --- | --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.x / Fabric + Fabric API | 21 | 4.1.0-beta.2 | Xaero's World Map 1.45.0+ / JourneyMap 6.0.8+ |
| 1.20.1 | Forge 47.x / Fabric + Fabric API | 17+ | 4.0.5 (>=4.0.5, <4.1) | Xaero's World Map 1.45.0+ / JourneyMap 6.0.6+ |
| 1.20.4 | Forge 49.x / Fabric + Fabric API | 17+ | 4.0.5 (>=4.0.5, <4.1) | Xaero's World Map 1.45.0+ / JourneyMap 5.10.0 |

请按精确 Minecraft 版本和加载器选择附件，六个 JAR 文件名包含已测试 MTR 版本。Fabric 还需对应 Fabric API。两种地图可选其一或同时安装，必须匹配游戏版本及加载器。支持的是上述三个具体版本，不代表 1.20.1 到 1.21.1 间每个版本都可用。

## 客户端与服务端安装

- 客户端安装一个匹配的 Overlay JAR、MTR，以及支持的全屏地图模组。
- 如需完整路网、全图实时列车及原生站点预测，服务端也要安装匹配的 Overlay 与 MTR；服务器不需要地图模组。
- 纯客户端使用 MTR 已下发的附近数据；原生站点预测需要匹配的 1.5.3 服务端支持。
- `/mtrmap config trainCars false` 隐藏实时列车。列车悬停暂不含下一站距离或 ETA，站点到站板是独立功能。

## 链接与支持

[官网与文档](https://mtrmapoverlay.benli06.site/zh/) · [源码与问题反馈](https://github.com/teamCreating/MTR-Map-Overlay) · [发布说明](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3)

欢迎通过 [Ko-fi](https://ko-fi.com/Z8C424REOO) 或[爱发电](https://afdian.com/a/benli06)支持开发。

本项目采用 MIT 许可证，保留原作者版权声明，为 Minecraft Transit Railway 独立开发。
