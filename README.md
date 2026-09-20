# Auto Fill Chest（Minecraft 26.2 / Fabric）

按 **H** 键，把背包里的物品自动塞进准星指向的箱子，直到箱子被占满。
填充方向固定为 **玩家背包 → 箱子**（不是把箱子搬进背包）。

- Mod ID：`autofillchest`
- 环境：**纯客户端**（服务端 / 联机服务器无需安装本模组）
- 无任何 Mixin
- 按键位置：选项 → 控制 → 按键绑定 → **Auto Fill Chest**

## 一、用法

1. 准星对准一个箱子（距离 ≤ 4.5 格），按 **H**。
2. 模组自动打开箱子界面，再逐格把背包物品快速移入，完成后提示"已占用 X / Y 格"。
3. 没对准箱子 → 提示"没有对准箱子"。
4. 手里拿着方块时按 H → 提示"请先空出一只手"（可放置方块会让右键变成"放方块"而不是"开箱子"）。

已经手动打开箱子界面时也可以直接按 H，模组会先核对"当前界面是不是你正在看的那个箱子"。

## 二、箱子容量

- 普通箱子 / 陷阱箱：**27 格**
- 两个相连的大箱子：合成一个 **54 格**容器

代码不写死 27，界面打开后按 `menu.slots.size() - 36` 计算容器格数，两种都支持
（木桶、潜影盒同理也能用）。

## 三、实现要点（为什么没有自定义网络包）

原需求写的是"客户端发自定义包 → 服务端搬运物品"，但既然定位**纯客户端模组**，
自定义 payload 要求服务端也装同一个模组，否则通道注册不上。所以改用：

> **QUICK_MOVE**（vanilla 的 shift + 左键）→ `MultiPlayerGameMode.handleContainerInput(...)`

好处：

- 服务端不需要安装本模组，原版服务器 / 任何服务器都能用；
- 物品移动由服务端权威执行，零 desync；
- QUICK_MOVE 的服务端规则本身就是需求要的顺序：
  1. 先补满箱子中**同种物品、未满堆叠**的格子；
  2. 没有同种物品才占用**第一个空格子**；
  3. 放不下的留在背包；
  4. 箱子占满 / 背包空 → 结束。

## 四、版本矩阵

| 组件 | 值 |
|---|---|
| Minecraft | `26.2`（Chaos Cubed，年份.序号 编号） |
| Java | `25`（class 主版本 69） |
| Fabric Loader | `0.19.5`（满足 `>=0.19.3`） |
| Fabric API | `0.160.0+26.2`（最低可用 `0.155.2+26.2`） |
| Fabric Loom | 构建时自动解析最新（实测 1.18.2） |
| Gradle | `9.7.0`（CI 用；wrapper 里是 9.5.1） |
| 映射 | Mojang 官方映射（26.1 起游戏不再混淆，**不写 `mappings` / `yarn_mappings`**） |

## 五、26.x 已核实的 API（相对 1.21.1 的坑）

| 场景 | 1.21.1 老写法 | 26.x 正确写法（本仓库实测编译通过） |
|---|---|---|
| 标识符 | `net.minecraft.util.Identifier` | `net.minecraft.resources.Identifier` |
| 方块实体常量 | `BlockEntityType.CHEST` | `BlockEntityTypes.CHEST` |
| 按键注册 | `KeyBindingHelper` | `KeyMappingHelper`（`client.keymapping.v1`） |
| 按键类别 | 直接传字符串 | `KeyMapping.Category.register(Identifier)` |
| 槽位点击 | `handleInventoryMouseClick(..., ClickType, player)` | `handleContainerInput(containerId, slot, button, ContainerInput, player)` |
| 点击类型 | `ClickType.QUICK_MOVE` | `ContainerInput.QUICK_MOVE` |
| 聊天栏提示 | `player.displayClientMessage(msg, bool)` | `sendSystemMessage(msg)` / `sendOverlayMessage(msg)` |
| 判断当前界面 | `mc.screen` | 无 public 字段，改用 `containerMenu.slots.size() > 46` |
| 构建 | `remapJar` / `mappings` | `jar` 任务，不写 mappings，`noIntermediateMappings()` |

## 六、目录结构

```
src/main/java/com/kongbai/autofillchest/
├─ AutoFillChest.java              公共常量（MOD_ID / Logger / id()）
└─ client/
   ├─ AutoFillChestClient.java     客户端入口（注册按键 + tick 事件）
   ├─ ModKeyBindings.java          H 键注册
   ├─ ChestTargetFinder.java       准星射线找箱子 + 4.5 格距离校验
   ├─ ContainerClickHelper.java    发 vanilla 的 QUICK_MOVE / 右键 / 关闭界面包
   ├─ ChestAutoFillTask.java       填充状态机（核心）
   ├─ Feedback.java                提示（overlay / 聊天栏）
   └─ AutoFillConfig.java          可调参数（速度、距离、上限等）
```

状态机：`IDLE → (找箱子) → WAITING_OPEN → FILLING → IDLE`。
FILLING 每 tick 最多发 3 个点击包，最多扫 3 轮、200 tick，避免卡死或一帧狂发包。

## 七、构建

CI（`.github/workflows/build.yml`，push 到 main 或手动触发）会自动跑，
产物提交到仓库 `build-out/` 目录，同时作为 Actions artifact 保留 7 天。

本地构建需要 JDK 25 与 Gradle 9.7.0：

```bash
gradle build          # 产物：build/libs/autofillchest-mc26.2-1.0.0.jar
```

## 八、许可

MIT
