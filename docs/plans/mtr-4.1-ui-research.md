# MTR 4.1内部UI源码研究与1.5.3取舍

Codex，2026-10-25。仅公共源码研究，没有反编译、修改MTR或实现Overlay功能。结论补入[游戏内UI设计](v1.5.3-ingame-ui.md)。

## 1. 研究版本：先区分开发源码和发布包

本次通过上游`git ls-remote --heads/--tags`重新核对：4.1开发分支`stonecutter`的远端HEAD为`f05d99efab6f27b3868913438fce2da381e5798f`，与本地研究副本一致；其[gradle.properties](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/gradle.properties)仍标`4.1.0-beta.2`，依赖Elementa 743、UniversalCraft 497。不能将其表述为已核实的“4.1.0正式版”。GitHub Releases页没有发布条目，不能据此确认Modrinth/CurseForge的最新发布包或二进制与源码完全一致。

本报告的“最新”限定为**调查时4.1公开开发分支HEAD**。版本目录提供1.21.1/1.21.4的Fabric/NeoForge，不证明这套新UI在本项目1.20.1/1.20.4 + MTR4.0.5中存在。研究固定上述SHA，不随远端分支移动；源码副本位于ignored `build/source-research/mtr`。

## 2. 实际架构：两代UI并存

新页面采用Elementa的组件树与约束布局，UniversalCraft桥接Minecraft版本；旧的Minecraft `Screen`/`AbstractWidget`体系仍保留。

- [WindowBase](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/screen/WindowBase.java)继承Elementa `WindowScreen`，新构造用Elementa V11，旧Screen父页兼容构造仍用V10；Esc释放时标记关闭，下一tick返回父页；`isPauseScreen()`固定false。
- [ScreenBase](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/screen/ScreenBase.java)仍继承Minecraft Screen，注册原生widgets并在onClose返回新/旧父页，也不暂停游戏。
- [ConfigScreen](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/screen/ConfigScreen.java)、DashboardScreen、PIDSConfigScreen等已采用新组件；`ScrollableListWidget`、BetterButtonWidget等旧控件仍在仓库。

因此不能只看到一个旧控件就认定4.1仍完全手写坐标，也不能认定所有旧控件已经被替代。公开class不等于承诺稳定的扩展API。

## 3. 最适合借鉴的六类写法

| 写法 | MTR源码与行为 | Overlay落点 |
| --- | --- | --- |
| 约束式组合布局 | [SingleTabBackgroundScreenBase](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/screen/SingleTabBackgroundScreenBase.java)使用父容器相对宽度、Sibling间距、Fill剩余高度和可换行标题；[BackgroundComponent](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/widget/BackgroundComponent.java)限制最大320×240并预留外边距 | 用“标题固定+剩余区域滚动+宽高钳制”描述站点卡片；避开继续散落绝对坐标。该320×240/40外边距是MTR独立页面参数，不直接套地图侧栏 |
| 分组与展开状态保留 | [ListItem](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/widget/ListItem.java)分parent/child，默认expanded=false，overwriteList按parentKey恢复展开状态，筛选可自动展开匹配组 | ETA更新后保留用户展开的线路、选中站和滚动位置；以原生route/platform/服务组身份作键，不能以ETA文字或行号作键 |
| 释放时决定点击/拖动与重叠消歧 | [MapComponent](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/widget/MapComponent.java)记录按下位置，释放时比较位移；重叠站点/站台进入候选列表，单项单动作可直接触发；弹窗位置钳制 | 保留“单击固定站点，重叠才选列表”；先由宿主桥证明按下/释放顺序。采用整个手势最大位移/拖动标志，避免拖远后回到原处误判点击；不照搬其小于1px端点阈值 |
| 控件按职责拆分 | [ButtonComponent](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/widget/ButtonComponent.java)处理可换行/省略和禁用/悬停，[ScrollPanelComponent](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/widget/ScrollPanelComponent.java)独立处理滚动内容和滚动条 | 绘制/布局、数据状态、动作分别建模；图层菜单按钮、站点标题、ETA行不各写一套输入逻辑。保留宽度/文字变化才重算省略的思想 |
| 设置页有统一关闭提交点 | ConfigScreen由通用单页背景承载标签、checkbox和数字输入；onScreenClose提交数字值、保存配置，并刷新相关资源 | 把图层动作统一通知、按用户操作批量保存；必要的详情页保存父页。Overlay不应照搬“所有关闭都刷新动态纹理”，也不能把返回子页错误当最终退出 |
| 名称/时间格式化与业务数据分离 | [FontRenderOptions](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/font/FontRenderOptions.java)及[FontRenderHelper](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/font/FontRenderHelper.java)区分字体、CJK、对齐、`|`分行、溢出；[RenderPIDS](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/render/RenderPIDS.java)使用原生到站时间、时钟偏移、实时/计划标记 | 名称展示通过版本适配器，不能解析显示字符串建线路关系。ETA原始时间与本地展示分开，计划班次明确标注；时间格式化不参与预测 |

### 视觉语言值得参考，但不能整套照搬

[GuiHelper](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/src/main/java/org/mtr/tool/GuiHelper.java)定义深色列表底`#111111`、强调底`#333333`、hover `#444444`、透明背景`#CC111111`，以及8px字高/4px间距/16px标准行的基准。配合线路色块、chevron、图形按钮，适合减少Overlay菜单与MTR本体的视觉割裂。

MTR独立设置页使用Minecraft风格背景并有深色标题文字，列表/地图则是另一套深色面板；不能把标题`#404040`直接放到我们的深色站点卡片。Overlay建议采用其色块/间距层级，保留20–24 GUIpx的主要点击目标、清晰文字与键盘焦点，不把16px内部列表行当所有控件尺寸。

## 4. 不适合直接采用的部分

1. **移开即关闭的弹窗**：MapComponent.checkPopupHover在指针离开扩展范围时隐藏弹窗。可用于临时目标选择，不能用于用户要求的固定站点到站板；卡片必须与hover生命周期分离。
2. **用颜色/名字归并服务身份**：ListComponent.setRoutes可按颜色+`||`前名称分组，另有flattenRoutesByColor。这是管理列表展示方法，不是原生route ID；到站板不能合并同色同名的不同路线/方向。
3. **把滚动裁剪当列表虚拟化**：新ListComponent.draw调用iterateData后为每个未折叠/未过滤行创建绘制任务，consumer始终返回false；没有按可视行范围跳过。即使外层裁剪，CPU侧仍遍历M行。DashboardScreen.onTick还会重建当前tab的数据。大站点UI应在结构revision变化时重建分组，仅对可见V行更新文字/绘制，不能把MTR代码当O(V)的证据。
4. **每帧排版、资源查找与flush**：ListComponent.draw创建builder/数组/list/lambda并endBatch；作为独立界面可以工作，嵌入宿主地图会增加分配和合批干预。保留本项目静态/动态分离，不将这些调用复制进每帧地图层。性能是源码风险分析，未做对比实测。
5. **悬停缓存决定点击**：ListComponent在draw中设clickAction，点击时直接执行。Overlay必须在输入事件发生时核对当前布局/世界/数据revision及有效目标，不执行上一帧已经过期的行回调。
6. **继承WindowBase解决暂停**：它始终不暂停，且拥有完整WindowScreen的输入根节点。我们的地图由Xaero/JM持有，不能再嵌一套全屏根窗口抢事件；只查ETA的单机运行和普通暂停菜单须由地图上下文判定。
7. **把文本压扁当通用长名方案**：MTR列表用COMPRESS，部分按钮做省略；站点卡片仍以换行/省略+完整提示为主，防止中文终点和状态压到难读。FontRenderOptions.SCROLL仍注释未实现，ALTERNATE当前也按分行处理，不能承诺现成跑马灯/语言轮播。
8. **只凭控件外观推断无障碍支持**：旧ClickableWidgetBase的updateWidgetNarration为空；新组件的示例鼠标回调也不能证明已有Tab/键盘/叙述支持。此前UI设计的可达性验收保留。
9. **把PIDS方块渲染器当GUI到站板**：RenderPIDS.getArrivalString在>=60秒显示整数分钟、0秒及以下返回空串，计划班次加星号。这些呈现适合站内设备，地图面板仍需已到站/终到/覆盖不全/过期等状态；不实例化方块renderer只为格式化秒数。

## 5. 直接复用内部UI类的实际成本

[NeoForge构建脚本](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/build-neoforge.gradle.kts)把Elementa/UniversalCraft及Kotlin shadow并重定位到`org.mtr.libraries.*`；[Fabric构建脚本](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/f05d99efab6f27b3868913438fce2da381e5798f/build-fabric.gradle.kts)采用include，未做相同的gg.essential重定位。

因此源码里的`gg.essential.*`类型，不能未经实际依赖编译就假定在两个loader运行时都有同一全名。派生WindowBase、接受/返回Elementa对象、加载其Kotlin类型都会穿过这个边界。MTR4.0兼容目标还需要另一套实现，不能仅改import宣布六平台共用。Elementa带跨版本抽象，也没有自动解决Xaero/JM事件取消与面板占区。

建议分层取舍：

| 层 | 本次建议 | 复用条件 |
| --- | --- | --- |
| 地图内浮层 | 复用交互/布局设计；继续本项目共享controller/layout/model和轻量Minecraft绘制 | 可控宿主输入、裁剪、缓存和三语言，保持6目标；不额外引入Elementa整套窗口 |
| 独立设置详情页 | 可评估MTR组件，但不作为本版前提 | 先用实际发布依赖完成NF/Fabric/旧4.0编译与加载原型；证明资源就绪、返回/保存/暂停正确后再选 |
| 名称与只读资源元数据 | 在内部版本适配器中优先调用已经存在且核实的方法 | 支持资源/语言重载；无法调用则可见回退，不能拖累公共API加载 |
| 对外API | 仍为Java17纯JDK DTO及声明式请求 | 不泄漏Elementa、WindowBase、MTR控件、Kotlin或地图宿主类 |

若未来实际移植源码，保留MIT作者和许可证，并独立核对素材/字体许可；本次未复制实现或素材。

## 6. 对1.5.3设计的具体补充

- 持久选择/展开键采用`WorldRef + stationId + routeId + direction/destination + platformId`，结构与倒计时分开；同世界同站刷新不折叠、不同ID不误继承。同一服务重复停站若缺原生消歧仍按主计划标不确定。
- 借鉴MTR的parent/child与chevron，站点整体仍默认折叠，点击一次固定展开；**展开站点后默认可见各线路ETA**。多方向/站台行可在内容过多时由用户主动收起，不能又把每条线路ETA全藏在第二层点击后。
- 分组状态与排版按revision更新，当前可见行按秒刷新ETA；保留滚动锚点和键盘焦点，不用新ETA排序使指针下内容跳动。
- 候选弹窗使用钳制位置/有界高度/滚动，单一明确目标直接打开卡片。拖动状态一旦越过阈值便不再恢复为点击，即使释放点返回原坐标。
- 设置保存与返回使用明确的“一次关闭/提交”动作；Esc由最上层消费，避免同一事件关闭卡片和宿主地图。只借鉴WindowBase延后执行导航的思想，不强行改成所有界面都不暂停。
- 测试新增：数据刷新后展开/滚动/焦点保持；同名同色不同ID分开；拖出再拖回不是点击；长中文/多语言换行；一份数据更新不引发每帧全列表重建；资源重载后样式/文本可用。

验证边界：已核对远端分支HEAD/版本、上述公共源码及构建打包规则；文档引用检查另记录于协作文件。本次未尝试继承内部组件、未修改依赖、未构建UI原型或游戏实测；1.5.3实施仍先经过原生数据与宿主输入桥验收。
