# 1.5.2 六平台移植及回归

Codex，2026-10-22。用户要求先把最新功能移植到 MC 1.20，再测试所有支持平台。主线源基准为 `df9df96`，兼容分支原基准为 `af537e5`。版本固定 1.5.2；本次没有创建 release/tag。

## 结果

| Minecraft | 加载器 | MTR | 构建/测试 | Xaero 全屏 | JourneyMap 全屏 |
| --- | --- | --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.249 | 4.1.0-beta.2 | 成功 / 86 通过、1 跳过 | 通过 | 6.0.8，通过 |
| 1.21.1 | Fabric 0.16.14 | 4.1.0-beta.2 | 成功 / 复用主线 86 项测试 | 通过 | 6.0.8，通过 |
| 1.20.1 | Forge 47.4.0 | 4.0.5 | 成功 / 89 通过、1 跳过 | 通过 | 6.0.6，通过 |
| 1.20.1 | Fabric 0.16.14 | 4.0.5 | 成功 / 89 通过、1 跳过 | 通过 | 6.0.6，通过 |
| 1.20.4 | Forge 49.2.0 | 4.0.5 | 成功 / 89 通过、1 跳过 | 重试通过，见下文 | 5.10.0，通过 |
| 1.20.4 | Fabric 0.16.14 | 4.0.5 | 成功 / 89 通过、1 跳过 | 通过 | 5.10.0，通过 |

测试生成器默认跳过，未把写入存档的生成用例算作通过。1.21.1 Fabric 不重复运行共用源测试；兼容分支两个构建均显式运行共用测试。构建用 JDK 21；1.20 游戏使用 Temurin 17.0.19，产物 Java 17；1.21.1 产物 Java 21。

## 已移植行为

- 静态 v8：真实 XYZ / 原生曲线、屏幕误差细分、轨道接头与彩虹共线样式。轻铁宽度 0.7，Rail 和 High Speed 均 1.0，高铁保留彩白矩形块及描边。
- 纯 2D 底衬→线路→车厢，不恢复下穿或高度排序。
- 动态 v1：TSC Simulator 线程采集全网车厢的两个参考点、共享采集/编码、定义分离、完整帧分块/校验、租约/过期/清理；客户端只缓存、插值和绘制。
- Create 参考的车厢轮廓、线路色、方向标记、三级缩放及双帧扫掠视口索引；两地图共用悬停。未实施距离/ETA，不恢复暂停的草稿。

兼容分支保留 Forge 47/49 各自网络接口和 Fabric 原始通道；静态 v8、动态 v1 使用独立通道。Core 4.0 没有公开 siding 车辆遍历方法，新增只读 `SidingVehiclesAccessor`，在 Simulator 线程读取其已有集合；不创建/推进车辆、不扫描 Minecraft 实体。原生 bogie 的 Vector 对取中点；真实 XYZ 与 Core 数据一致。旧 RailMath 没有侧倾参数，v8 保留槽位写零。Java 17 兼容辅助不改变客户端计算责任。

## 实机覆盖与观察

使用隔离测试目录及存档副本；没有修改用户普通世界。网格为 3 routes / 6 rails / 7 landmarks，1 列 3 节车厢。四个 1.20 目标均使用无地图模组的本地独立服务端，启动至 Done 并成功与对应客户端连接。1.21.1 两平台本轮使用集成服。

检查两地图的车辆移动/过弯、缩远单列图标→放大完整三节、线路/车辆悬停、路线和轨道开关；JourneyMap 5 额外检查平移投影，车厢和轨道同步对齐。悬停可见 `Local → Charlie` / `Local Return → Alpha` 及 `3 carriages`。仅原生元数据可用时显示名称；不猜测车型。

四个兼容目标及 Fabric 1.21.1 将玩家移至 (2000, -59, 2000)，原生附近车辆为 0 时仍显示全图 1 列/3 节，位置连续变化。位置正文为 125 bytes/frame（不含定义、传输头及分块头）。示例日志：

```text
Forge 1.20.4 09:52:02: native nearby=0, first XYZ=73.95859744436109/-59.0/0.5
Fabric 1.20.4 09:55:25: native nearby=0, first XYZ=295.37052955817205/-59.0/78.28498728012147
Fabric 1.20.4 09:55:51: native nearby=0, first XYZ=281.1831566577925/-59.0/80.48617916572424
Fabric 1.21.1 09:33:45: native nearby=0, first XYZ=335.5578516802119/-59.0/27.13768740438409
Fabric 1.21.1 09:34:21: native nearby=0, first XYZ=341.34001179003053/-59.0/18.703699900031236
```

NeoForge 本轮复查了两地图移动、缩放和悬停；附近为 0 的独立证据沿用同一列车实现此前的[全图验证](global-trains.md)和[显示优化验证](train-display-optimization.md)，不将本轮附近为 1 的检查写成新的远距离实验。Forge 1.20.1 首轮有错误箭头编码，修复 UTF-8 源后四目标重新构建；Fabric 1.20.1 与两个 1.20.4 目标实机核对箭头正常。

## 本次发现及修复

1. **Fabric 1.21.1 缺少 JourneyMap 插件入口**：仅有 Forge 注解不能触发 Fabric 插件发现。补上 `fabric.mod.json` 的 `journeymap` entrypoint，重启后 JourneyMap 初始化插件、按钮和车辆均正常。
2. **Fabric 开发环境缺少地图/MTR 嵌套依赖**：增加显式 opt-in 的运行脚本，从校验 SHA-1 的原发布包提取嵌套模块。XaeroLib 同时作为 compile-only 层级依赖提供给 Loom，避免缺父类信息产生错误开发重映射。正式 JAR 不嵌入这些依赖。最后脚本整理后重新通过普通 build 和 `configureClientLaunch`；实际游戏验证使用同一组依赖坐标。
3. **列车配置命令遗漏**：最终源码核对补齐兼容分支的 `/mtrmap config trainCars` 入口，并将提示文字统一为 Map train cars；相关六包重新构建。
4. **移植时文本编码**：修复 `→`/`—` 等 UTF-8 源文本，避免 Windows 默认编码破坏悬停。
5. **Forge 1.20.4 原生崩溃**：首轮客户端在激活窗口时出现 `EXCEPTION_ACCESS_VIOLATION`，位置为 `glfw.dll+0xff5b`，不是通过记录。关闭其他游戏客户端后单独重启，09:51–09:53 完成两地图全图车辆回归并正常退出。根因未确定，不能据此断言由多窗口或模组引起；保留 `forge-1.20.4-client/hs_err_pid10132.log`。

## 复现与证据

主线：NeoForge `gradlew build`；Fabric 在 `fabric` 下使用其 wrapper `gradlew build`。Fabric 运行测试可加 `-PmtrmapRuntimeTest -PmtrmapRunDir=../build/platform-matrix/fabric-1.21.1 -Pquickplay=GlobalTrainSyncTest`；此选项不用于正式依赖捆绑。

兼容分支：根目录 wrapper 构建 Forge，`fabric` wrapper 构建 Fabric；两者加 `-Pminecraft_version=1.20.4` 选择 1.20.4，默认 1.20.1。PowerShell 参数整体加引号。运行脚本使用 `mtrmapRuntimeTest`、`mtrmapServerOnly`、`mtrmapRunDir` 和 `quickplayServer` 选择隔离客户端/服务端。

包内检查：主线 `python tools/verify_platform_matrix.py --compat ../mtrmap-mc120` 覆盖六包的 1.5.2、精确 MC 范围、Java 字节码、加载器隔离、原生曲线/全图列车/视口类、Mixin 和 Fabric JourneyMap 入口，确保没有捆绑第三方类或依赖 JAR。兼容分支另运行 `python tools/verify_artifacts.py` 和 `--minecraft 1.20.4`，检查生产 Xaero 钩子、JourneyMap 5/6 区别及各 89 项测试。

本地原始证据（均忽略提交）：

- 主线 `build/platform-matrix/*final*.log`、`build/platform-matrix/fabric-1.21.1/logs/latest.log` 和 `run/logs/latest.log`。
- 兼容分支 `.gradle/runtime-matrix-20261022/*final-build.log`、`*-client-launch.log`、`forge-1204-client-retry.log`、`*-server-launch.log`；同名客户端/服务端目录中的完整日志及首次原生崩溃报告。
- 正式构建测试报告位于各项目 `build/test-results/test` 或 `build/mc1.20.4/test-results/test`。

所有测试客户端及四个独立服务端均正常关闭，服务端存档完成。最终六包及 SHA256 位于共享工作区 `test-builds/v1.5.2-platform-matrix/`。

## 验证边界

这是开发运行环境下的功能回归，另对正式产物做包结构/元数据/映射检查；没有用这六份 production JAR 启动独立整合包，也没有跨机器/跨加载器联机。小网格低速列车不能证明千列车队 FPS、多人带宽、高速小半径、复杂延迟或长时间稳定性。Forge 1.20.4 首次原生崩溃仍需后续观察。全图能力需要客户端和服务端都更新到动态 v1；仅更新客户端仍是附近回退。
