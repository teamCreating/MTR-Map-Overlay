# 团队 Java 命名空间迁移

Codex，2026-10-22。用户指定 `io.github.teamcreating.mtrmap`；mod 版本保持 1.5.2。

## 修改

- main 与 compat/mtr-4.0-mc-1.20 的源码、测试目录及所有 package/import 迁移到 `io/github/teamcreating/mtrmap`；Maven group 改为 `io.github.teamcreating`。
- 同步 Fabric/JourneyMap entrypoints、Mixin package/plugin、JourneyMap 反射类名、Gradle 源文件排除/旧 API 生成路径、检查/预览工具及当前源码链接。
- mod ID、资源/网络通道、配置文件名、命令和协议不变。对两个原提交的全部 107/111 个 Java 文件反向替换并归一化换行后核对，只有命名空间变化，没有功能改写。
- 六个构建均将各自原始 LICENSE 按原文打包，保留 AmberFrost 版权、README 署名及 Git 历史。
- 历史发布说明、验证证据、规划与只追加的消息记录保留原始路径。`v1.5.2`、`v1.5.2-mc1.20` 标签及 GitHub 既有附件没有改写；本次新包名构建尚未发布。

代码提交：main `b7f646e`，兼容分支 `4005dd8`。

## 验证

| 目标 | build | 共用单测 | 包内命名空间/入口/Mixin/LICENSE |
| --- | --- | --- | --- |
| NeoForge 1.21.1 | 通过 | 86通过、1生成器跳过 | 通过 |
| Fabric 1.21.1 | 通过 | 复用主线86项 | 通过 |
| Forge 1.20.1 | 通过 | 89通过、1生成器跳过 | 通过 |
| Fabric 1.20.1 | 通过 | 89通过、1生成器跳过 | 通过 |
| Forge 1.20.4 | 通过 | 89通过、1生成器跳过 | 通过 |
| Fabric 1.20.4 | 通过 | 89通过、1生成器跳过 | 通过 |

运行 `python tools/verify_platform_matrix.py`（main）和 `python tools/verify_artifacts.py [--minecraft 1.20.4]`（兼容分支）。检查所有 class 的新前缀，拒绝旧包字节码/字符串，验证 Java17/21、loader 隔离、精确 MC、生产 Xaero 钩子、入口类存在、Mixin 类存在、配置/refmap 无旧包名，且 LICENSE 与对应分支原文相同。发现部分 Gradle 增量输出残留旧类后，验证器拒绝混包；将对应旧生成/编译目录移入 `.gradle/namespace-migration/` 备份后重新构建，通过最终检查。

本轮实际启动检查（开发运行环境）：

- **NeoForge 1.21.1**：新 FQN 的 JourneyMap 插件被发现；两地图成功打开，静态 3 routes/6 rails/7 landmarks，动态 1列/3节/125B，线路和车辆绘制正常。
- **Fabric 1.21.1**：新 Fabric/JourneyMap 入口和 Mixin 成功加载，两地图成功打开。玩家(2000,-59,2000)，native nearby=0，18:09:46→18:10:02 的车头X约228.63→219.92，保持全图1列/3节更新。
- **Fabric 1.20.4**：无地图模组的独立服务端启动至Done，客户端收到全网快照。玩家移至远处后 native nearby=0，18:11:58→18:12:09 的真实XYZ持续变化。JourneyMap5旧API生成类、地图钩子、路线/轨道开关及车辆悬停实际通过，显示 `Local → Charlie`、`3 carriages`。
- 三个测试客户端均正常关闭，独立服务端用RCON停止并保存。不把前次六平台完整功能回归冒充本轮六平台重跑；本轮没有重跑Forge1.20.1、Fabric1.20.1、Forge1.20.4的图形界面，也未进行production整合包或大车队性能测试。

日志：main `build/platform-matrix/namespace-*.log`；兼容分支 `.gradle/runtime-matrix-20261022/namespace-*.log`。本轮日志中仍有依赖的更新检查超时、MTR模型/loot警告和日志provider提示；没有将它们归因于本次包名变更，也没有把它们报告为已修复。

新六包单独导出到共享工作区 `test-builds/v1.5.2-teamcreating-namespace/`，文件名继续包含MC、MTR和loader，附SHA256及构建信息，不覆盖此前测试包或GitHub发布附件。
