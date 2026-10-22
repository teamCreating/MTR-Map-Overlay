# MTR Map Overlay — MC 1.20 兼容分支

分支：`compat/mtr-4.0-mc-1.20`，版本：**1.5.2**。已移植主线最新功能，主线仍支持 NeoForge/Fabric 1.21.1。

## 支持版本

| 组件 | 版本 |
| --- | --- |
| Minecraft | **1.20.1 / 1.20.4**；游戏运行需要 Java 17+，两版本使用独立 JAR |
| MTR | **4.0.5**（2026-09-30 核实的最新稳定 4.0.x）；必须匹配加载器 |
| 加载器 | Forge 47.x（1.20.1）/ 49.x（1.20.4），或 Fabric Loader 0.16.14 + 对应版本 Fabric API |
| 地图 | Xaero's World Map 1.45.0+；JourneyMap **6.0.6+（1.20.1）/ 5.10.0（1.20.4）**，均需匹配 MC 和加载器 |
| Xaero's Minimap | 可选；仅用于清理早期版本的旧航点 |

四个目标分别覆盖两种 MC 版本和两种加载器。每份 JAR 元数据严格匹配对应 MC 版本，要求 MTR >=4.0.5 且 <4.1；安装时需同时匹配 MC 版本和加载器。

## 功能

- 最新1.5.2包含原生曲线/接头、轻铁0.7宽、Rail与高铁等宽、高铁彩白描边块及随缩放收细的共线带。
- 全图列车由TSC采集/下发，客户端只缓存、插值、按视口及三级缩放绘制抽象车厢；两地图共用悬停。全图能力需两端更新，无动态服务端能力仅附近回退。纯2D，距离/ETA仍暂停。
- 保留主线的真实轨道、共线路线彩虹带、地图内车站/站台/车厂图标及悬停信息。
- Xaero / JourneyMap 全屏地图均支持轨道和路线开关，图标不进入普通航点列表。
- 服务端也安装同加载器的本模组时可同步全维度网络；仅客户端安装时使用 MTR 下发的附近数据。
- `/mtrmap config trainCars <true|false>` 控制车厢显示。
- `/mtrmap syncRoutes`、`syncLandmarks`、`mode station|platform|both` 和 `config` 命令保留。
- Forge 配置为 `config/mtrmap.toml`；Fabric 配置为 `config/mtrmap.properties`。

## 构建

构建 Gradle 使用 **JDK 21**，产物编译为 Java 17。Windows 命令：

```powershell
.\gradlew.bat build
.\fabric\gradlew.bat -p fabric build
.\gradlew.bat build "-Pminecraft_version=1.20.4"
.\fabric\gradlew.bat -p fabric build "-Pminecraft_version=1.20.4"
```

产物：

- `build/libs/CRTools-MTR-Map-Overlay-forge-mc1.20.1-1.5.2.jar`
- `fabric/build/libs/CRTools-MTR-Map-Overlay-fabric-mc1.20.1-1.5.2.jar`
- `build/mc1.20.4/libs/CRTools-MTR-Map-Overlay-forge-mc1.20.4-1.5.2.jar`
- `fabric/build/mc1.20.4/libs/CRTools-MTR-Map-Overlay-fabric-mc1.20.4-1.5.2.jar`

只安装对应加载器的一份 JAR。Forge 使用 Gradle 8.8 / ForgeGradle 6.0.54；Fabric 使用 Gradle 9.5.0 / Loom 1.17.21。JourneyMap 发布包内的 v2 API 会按原包校验、提取并重映射作为编译依赖，不打包进本模组。

四个构建都会运行共用测试。产物检查使用 `python tools/verify_artifacts.py` 及 `python tools/verify_artifacts.py --minecraft 1.20.4`。写入测试存档的生成器默认跳过；在明确需要生成存档时用 `-PmtrmapGenerateTestWorld=true` 启用。最新四目标各89项测试、两地图实机功能回归已完成，详见[1.5.2六平台验证](docs/validation/platform-matrix-1.5.2.md)；旧1.5.1记录见[MC 1.20运行验证报告](docs/validation/mc120-runtime.md)；production JAR 启动及跨机器联机仍未验证。

1.20.4 的专用适配位于 `compat/mc1204/`，包含 Forge 49 网络、JourneyMap 5 插件及两平台地图按钮事件。在 JourneyMap 地图绘制后、工具栏绘制前叠加轨道与地标；复用共用数据、筛选、寻路及静态v8/独立动态v1协议。1.20.4 Fabric API 使用 0.97.3+1.20.4。JourneyMap 地标/存在性适配由 `gradle/minecraft-target.gradle` 从共用源码生成，避免复制数据处理逻辑。

完整功能、命令与源码说明见 [English README](README.md)。许可证和原作者署名保留于 [LICENSE](LICENSE)。
