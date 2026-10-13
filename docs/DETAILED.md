# 课表 App · 从零到戴上 OPPO Watch X3

> 目标：手表上抬腕就能看到「现在第几节 / 下一节是什么」，
> 手机上随时改课表，改完一键推到手表。

---

## 0. 先搞清楚这台设备的约束（决定了整个架构）

查证过的 OPPO Watch X3 规格：

| 项目 | 值 | 对我们的影响 |
|---|---|---|
| 系统 | Wear OS 6 + ColorOS Watch 8（国行 ColorOS Watch 16.0） | 标准 Wear OS 开发路线可行 |
| 芯片 | 骁龙 W5 Gen1 + BES2800BP 双擎 | 性能没问题 |
| 屏幕 | 1.5 英寸 LTPO AMOLED，**466×466 圆形** | UI 必须按圆形设计（内边距要留够） |
| 表冠 | 旋转 + 按压，支持自定义快捷方式 | 旋钮滚动是我们的核心交互 |
| 内存 | 2GB + 32GB | 绰绰有余 |
| **国行** | **无 GMS（Google 移动服务）** | **⚠️ 官方 Data Layer 同步 API 不可用** |

最后一行是整个方案里最关键的一条。绝大多数 Wear OS 教程都会教你用
`Wearable.getDataClient()` / `MessageClient` 做手机↔手表同步 —— **在国行
OPPO 上这些 API 全部失效**，因为底层依赖 Google Play services。

所以本项目自建了一条同步通道：

```
手机 App ──Wi-Fi TCP:8899──▶ 手表 App（ServerSocket）
     [4字节长度] + [UTF-8 JSON]  ──▶  回 1 字节 ACK
```

好处：零第三方依赖、不联网也能用、延迟 < 100ms。
代价：手表 App 至少要被打开过一次，进程要在。

---

## 1. 目录结构

```
android/
├── settings.gradle.kts          # 三个模块：shared / watch / phone
├── gradle/libs.versions.toml    # ★ 所有版本号集中在这里，装报错先改这里
├── shared/                      # 手表 + 手机共用的数据模型与计算引擎（纯 Kotlin）
├── watch/                       # Wear OS 手表 App
└── phone/                       # 手机配套 App

prototype/index.html             # 高保真网页原型，双击就能打开
```

### 为什么要有 shared 模块

「还有几分钟下课」「今天第几周」「这节课是否在有效周次内」
这些逻辑，**手表主界面、Tile 小组件、手机今日页都要用**。
各写一份必然会出现「手机显示上课中、手表显示已下课」的鬼故事。
所以统一放 `shared`，两端 `implementation(project(":shared"))`。

核心入口是 `ScheduleEngine.snapshot(timetable, now)`：
输入一个时刻，输出一个完整的 `Snapshot`（当前/下一节/进度/全部条目），
纯函数、无副作用、不依赖 Android，非常好测。

---

## 2. 第一步：把原型跑起来（不需要装任何东西）

```bash
open prototype/index.html
```

左边是 1:1 尺寸的 466×466 手表预览，右边是手机端。

你可以：
- **拖动表冠**（右侧那个金属旋钮）、或者在屏幕上**滚鼠标滚轮 / 按住拖拽** → 列表滚动，右侧会出现弧形滚动条
- 调「模拟时间」滑块 → 看 UI 在 上课中 / 课间 / 课前 / 放学 / 没课 五种状态之间怎么切换
- 调「模拟周次」→ 看单双周课程（比如双周才有的数据库实验）如何自动显隐
- 在手机端点课程卡片 → 改课程信息 → 手表端**立刻**跟着变

先把这一步玩明白，再去碰 Android Studio 会省很多时间 ——
UI 的细节（间距、字号、配色）在网页上调，比在模拟器上改一次编译两分钟快得多。

---

## 3. 第二步：安装 Android Studio

1. 下载 <https://developer.android.com/studio>（国内建议走官方镜像）
2. 安装时勾选 **Android SDK Platform 35**（或更高）、**Android SDK Build-Tools**、**Android SDK Command-line Tools**
3. 装完后打开 Android Studio → `More Actions` → `SDK Manager` → `SDK Tools` 页：
   - 勾选 **Android SDK Command-line Tools (latest)**
   - 勾选 **Google USB Driver**（Windows 才需要）
4. 终端验证：
   ```bash
   sdkmanager --version
   adb version
   ```

### 3.1 只想跑命令行的话，不装 Android Studio 也行

Android Studio 主要是给了一个图形化的 SDK 管理器。只用命令行也完全够：

```bash
# 1) JDK 17（AGP 8.x 硬性要求 17，用 8 或 21 都会报错）
#    macOS 可从 Azul 拿：https://www.azul.com/downloads/?package=jdk
export JAVA_HOME=<JDK 17 路径>/Contents/Home

# 2) Android 命令行工具
mkdir -p <SDK 路径>/cmdline-tools
curl -L -o cmdtools.zip \
  https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip
unzip cmdtools.zip && mv cmdline-tools <SDK 路径>/cmdline-tools/latest

# 3) 装 SDK 组件
export ANDROID_HOME=<SDK 路径>
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
      --sdk_root=$ANDROID_HOME --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME \
      "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

> 我验证这套工程时就是这么装的（JDK 用 Zulu 17，Gradle 从腾讯云镜像拉的）。
> 官方文档的 `commandlinetools-mac-XXXX_latest.zip` 链接里 `XXXX` 会变，
> 如果 404 了就换一个版本号，或者直接从 Android Studio 官网下。

---

## 4. 第三步：打开工程

```
File → Open → 选中本仓库里的 android 目录 → Trust Project
```

第一次会自动下载 Gradle，可能要 5~15 分钟，看网络。
看到右下角 Gradle Sync 成功即可。

> ⚠️ 如果 Sync 报错说某个依赖找不到，十有八九是版本号问题。
> **不要去逐个依赖里改**，直接打开 `gradle/libs.versions.toml`，
> 把 `composeBom` 或 `wearCompose` 换成 Android Studio 提示的那个版本。
>
> 不过下面 9. 里那张版本表是**实测编译通过**的组合，正常情况下不用动。

> 如果你的 Gradle 版本和 `gradle-wrapper.properties` 里的不一致，
> Android Studio 会提示 "Update Gradle"，点它就行。

---

## 5. 第四步：连上手表

### 5.1 打开手表的开发者选项

表盘上依次进：
`设置 → 关于本机/版本信息 → 连续点「版本号」7 次` → 提示「您已处于开发者模式」。

回到 `设置 → 系统 → 开发者选项`，打开：
- **ADB 调试** ✅
- **通过 Wi-Fi 调试** ✅（推荐，省一根线）

### 5.2 用 Wi-Fi 连（推荐）

在手表上点进「通过 Wi-Fi 调试」，会显示一行 `192.168.x.x:5555`。

```bash
adb connect 192.168.x.x:5555
adb devices
# 看到 192.168.x.x:5555   device 就说明连上了
```

> 如果 `adb devices` 显示 `unauthorized`，手表屏幕上会弹「允许 USB 调试吗」，点允许。

> OPPO 手表底座通常**不支持数据传输**，只能充电，这种情况下
> **Wi-Fi ADB 是唯一可行的走法**。如果连不上，先检查电脑和手表是否在同一
> Wi-Fi 子网、路由器有没有开 AP 隔离。

### 5.3 拿到手表的 IP（同步要用）

后面手机推课表要靠这一步的 IP：

```bash
adb shell ip addr show wlan0 | grep "inet "
# 形如：inet 192.168.1.87/24 ...
```

记下来，比如 `192.168.1.87`。

---

## 6. 第五步：装到手表上

Android Studio 顶部设备栏选 **OPPO Watch X3**（会显示 `Google OPPO Watch X3` 或类似），
然后点 ▶ Run。

想更明确一点：

```bash
./gradlew :watch:installDebug
# 或分别在 UI 里 run 两个 target
```

手表 App 装好后：
1. 打开「课表」→ 会先显示样例课表
2. 往上（或转表冠）滚列表，看今天全部课程
3. 转表冠确认滚动跟手
4. Tile：在表盘上**向左/右滑**到「添加小组件」，找到「今日课表」加进去

---

## 7. 第六步：手机端同步

1. 装 `:phone` 到你的安卓手机：选择手机设备点 ▶ Run
2. 手表 App **先打开一次**（同步服务要在进程里）
3. 手机和手表连**同一个 Wi-Fi**
4. 手机端进「同步」页 → 点 **扫描局域网** → 会自动填上手表的 IP
5. 点 **推送课表** → 显示「推送成功」，手表立刻刷新

失败排查：
- 扫描不到 → 确认手机连的是 Wi-Fi 不是移动数据；确认手表 App 打开过
- 推送超时 → 有些家用路由器开了「AP 隔离」，把手机和手表隔开了，换手机热点试试
- 手表一直显示样例课表 → 手机端推之前先进「今日」页确认课表本身是对的；
  推送状态显示成功后手表会立即刷新，若没有就退掉手表 App 再重开一次

---

## 8. 自定义你的课表

手机端三个 Tab：

- **今日**：今天一整天的课，写着「进行中 / 已结束」，点卡片可编辑
- **本周**：周一到周日七天全部，自动按单双周过滤
- **同步**：推到手表 + 学期设置 + 节次时间表

添加课程时几个字段的意思：

| 字段 | 说明 |
|---|---|
| 星期 | 1=周一 … 7=周日 |
| 起始/结束节次 | 连堂就设成 1→2；三节连堂 1→3 |
| 有效周次 | 留空 = 全学期。`1-8,10-16` = 前八周加后七周；`1-16单` = 只在单周；`2,4,6,8` = 枚举 |
| 颜色 | 8 档，手表上每门课一个色，扫一眼就能区分 |

**重要**：`学期设置 → 第 1 周的周一` 一定要填对，它决定了所有周次的计算基准。
必须是**周一**（填错 App 会提示）。

---

## 9. 现在的状态：已经实测编译通过

这套代码不是"理论上能跑"，是我在一台没有 Android 开发环境的 Mac 上
现装了 JDK 17 + Android SDK + Gradle，然后**实际编译、打 APK、跑测试**过的。

```
./gradlew build
BUILD SUCCESSFUL

:shared:test        16 个测试全部 PASSED
:watch:assembleDebug  → watch-debug.apk  (24 MB)
:phone:assembleDebug  → phone-debug.apk  (16 MB)
lint                 0 Error / 0 Warning
```

验证过的版本组合（都在 `gradle/libs.versions.toml`）：

| 依赖 | 版本 |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.10.01（实际解析出 foundation 1.7.5） |
| Wear Compose | 1.4.0 |
| Wear Tiles | 1.4.0 |
| compileSdk / targetSdk | 35 |
| Gradle | 8.9 |

### 不用 Android Studio 也能打包

工程里已经带好了 `gradlew`，装好 JDK 17 和 Android SDK 之后：

```bash
export JAVA_HOME=<你的 JDK 17 路径>/Contents/Home
export ANDROID_HOME=<你的 SDK 路径>
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :watch:assembleDebug     # 手表 APK
./gradlew :phone:assembleDebug     # 手机 APK
./gradlew :shared:test             # 跑引擎单元测试
```

> `gradle/wrapper/gradle-wrapper.properties` 里的 Gradle 下载源是腾讯云镜像
> （`mirrors.cloud.tencent.com`），国内会快很多。想换回官方就把那行改回
> `https://services.gradle.org/distributions/gradle-8.9-bin.zip`。

### 单元测试覆盖了什么

`shared/src/test/kotlin/.../ScheduleEngineTest.kt` 里有 16 个用例，
专门盯着最容易错、也最影响体感的地方：周次推算、单双周过滤、
上课中 / 课间 / 课前 / 放学 / 没课五种状态、连堂课时间跨度、
JSON 序列化往返。

**它已经帮我们抓到两个真 bug**：

1. `weekOf()` 里整数除法是向零截断的，导致开学前 1~6 天会被算成「第 1 周」——
   等于把假期算成了上课周，课表会莫名其妙出现不该有的课。已改成 `Math.floorDiv`。
2. （运行期抓到）`courseColor()` 原来写的是 `Color(CoursePalette.of(index).toULong())`。
   Compose 的 `Color(ULong)` 期望的是「色彩空间打包格式」，而我们色板里存的是
   `0xAARRGGBB` 的 Long，直接塞进去会解析出一个非法 colorSpace id（实测崩在
   `drawCircle` 的 `ArrayIndexOutOfBoundsException: length=18; index=63`）。
   改成 `Color(value.toInt())`（走 ARGB 打包构造）后正常。
   **记住规则：ARGB Long/Int 一律用 `Color(Int)`，永远别用 `Color(ULong)`。**

---

## 9.5 已经在模拟器上真实跑起来了

创建了一台对标 OPPO Watch X3 的 Wear OS 模拟器（466×466 圆形、320dpi、
Wear OS 5.1 API 35），`adb install` 手表 APK 后实测：

- ✅ App 正常启动，无崩溃
- ✅ Hero 卡正确显示状态（截图时是放学状态：「2 节课，辛苦了 /
  下一次：明天 09:55 大学英语」）
- ✅ 顶部 TimeText 显示「周一 · 第6周 双周 · 时间」，单双周判定正常
- ✅ 课程列表滑动滚动正常（圆点颜色、时间、教室齐全）
- ✅ 列表用的就是表冠那套 `scrollableState`，真机上旋钮驱动同一状态

模拟器复现命令（SDK 统一在 `~/Library/Android`）：

```bash
# 创建（仅一次；镜像 android-35-ext15 是 Wear OS 5.1，avdmanager 太旧认不出时
# 可以像我们一样手写 ~/.android/avd/<名字>.avd/config.ini）
~/Library/Android/emulator/emulator -avd WatchX3_Wear51 &   # 启动（本机 86 秒开机）

# 装 + 启动 App
~/Library/Android/platform-tools/adb install -r watch/build/outputs/apk/debug/watch-debug.apk
~/Library/Android/platform-tools/adb shell am start -n com.classwatch.watch/.ui.MainActivity

# 首次开机若被 setupwizard 挡住：
~/Library/Android/platform-tools/adb shell pm disable-user --user 0 com.google.android.wearable.setupwizard
```

> 注意 Activity 全路径是 `com.classwatch.watch/.ui.MainActivity`
> （MainActivity 在 `.ui` 包下），用 `/.MainActivity` 会报 Activity 不存在。

## 9.7 已经在 OPPO Watch X3 真机上跑起来了（重要修正）

连上真机（`adb devices` 显示 `OWW261`）后，发现**之前的系统判断要推翻**：

```
ro.build.version.sdk = 30        ← Android 11，不是 Wear OS 6！
ro.build.display.id  = OWW261_11_A.215
ROM                  = V16.0.0   ← ColorOS Watch，OPPO 自研
abilist              = armeabi-v7a,armeabi   ← 只有 32 位！
pm list libraries    ← 没有 com.google.android.wearable
```

**OPPO Watch X3 国行不是 Wear OS 手表**，是 ColorOS Watch（Android 11 底子）。
第一次安装直接被拒：
`INSTALL_FAILED_MISSING_SHARED_LIBRARY: requires unavailable shared library com.google.android.wearable`

于是做了这些改造（全部已实测）：

1. **manifest**：删掉 `<uses-library com.google.android.wearable>`；
   `uses-feature` 全部 `required="false"`（否则缺 `android.hardware.type.watch` 也会拒装）
2. **Tile 小组件**：彻底移除（没有 Tile renderer）。源码备份在
   `android/legacy-wearos-tile/`，build.gradle 里的 tiles 依赖也注释了
3. **Wear Compose 保留**：它是纯 Jetpack 库，只依赖 androidx.*，实测在 ColorOS Watch 上正常
4. **主题**：`Theme.DeviceDefault.NoActionBar`（省出顶部标题栏）

### 表冠协议（真机抓包结果）

用 `InputProbeActivity`（临时诊断页）抓到了 OPPO 表冠的真实事件：

```
[DISPATCH-SCROLL] act=SCROLL src=8194 dev=oplus_crown axes=[ VSCROLL=+10 ]   ← 正转
[DISPATCH-SCROLL] act=SCROLL src=8194 dev=oplus_crown axes=[ VSCROLL=-8 ]    ← 反转
```

关键：事件是 `MotionEvent.ACTION_SCROLL` + `AXIS_VSCROLL`，但 source 是
**SOURCE_MOUSE(8194)**、设备名 `oplus_crown` —— **不是** Wear OS 的
`SOURCE_ROTARY_ENCODER`，所以 `Modifier.rotaryScrollable` 完全收不到。

适配方案（已实现，见 `RotaryBus.kt` + `MainActivity.dispatchGenericMotionEvent`）：

```kotlin
override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
    if (RotaryBus.isRotary(event)) {                    // 认 SOURCE_ROTARY_ENCODER 或 oplus_crown
        val value = RotaryBus.rotaryValue(event)        // AXIS_SCROLL 优先，退回 AXIS_VSCROLL
        RotaryBus.dispatch(-value * ROTARY_PX_PER_UNIT) // 转发给 ScheduleScreen 的列表
        return true
    }
    return super.dispatchGenericMotionEvent(event)
}
```

### 性能：debug 包在这块表上是真的不能用

实测 `dumpsys gfxinfo`（两次滑动手势）：

| 构建 | 中位帧耗时 | 两次滑动渲染帧数 |
|---|---|---|
| debug（JIT + debuggable） | **150ms**（6fps！） | 7 帧 |
| debug + `cmd package compile -m speed` | 250ms（无改善） | 6 帧 |
| **release（R8 + 非 debuggable）** | **16ms**（≈60fps 达标线） | 87 帧 |

结论：**32 位 A53 上必须用 release 包**，debug 包慢 10 倍是 `debuggable` 的
调试开销 + 未优化 dex 叠加出来的。release 还把 APK 从 25MB 压到 1.6MB。

另外做了三处 UI 优化（静止时零重绘）：
1. 刷新从「每秒」改成「对齐分钟边界」（界面文案都是分钟粒度，秒刷纯浪费）
2. 呼吸点动画 `PulsingDot` 改静态（无限动画 = 界面永不休息，软渲染下最卡元凶）
3. `rememberScalingLazyListState(initialCenterItemIndex = 0)` ——
   默认锚在第 1 项，会把 Hero 卡顶出屏幕让 TimeText 压在课程名上，
   这是个非常隐蔽的坑：`autoCentering` 只管滚动吸附、不管初始位置

### 真机安装命令

```bash
ADB=~/Library/Android/platform-tools/adb
$ADB install -r watch/build/outputs/apk/release/watch-release.apk   # release，别用 debug
$ADB shell am start -n com.classwatch.watch/.ui.MainActivity
```

---

## 9.8 单双周总开关 & 手表 IP 会变的问题

### 单双周总开关

有些学校不分单双周，或者前半学期分、后半学期不分。这时候把每门课的周次
重写一遍太痛苦，所以加了一个**全局开关**：

```kotlin
// shared/Model.kt
Timetable(..., oddEvenEnabled = true)   // 默认开
```

- **开**（默认）：按每门课自己的 `weeks` 过滤，1、3、5 周上的课只在单周出现
- **关**：忽略周次，这天排了课就显示（相当于每周都上），单双周标签也会隐藏

手机端「同步」页有对应的开关 UI（`TimetableStore.setOddEvenEnabled`）。
注意：某门课的 `weeks` 清空表示「从不排课」，**关掉开关也不会出现**——
这点有单元测试盯着。

### 手表 IP 是固定的吗？不固定，所以做了三层自动发现

DHCP 不保证每次都给同一个地址（路由器重启、换了 Wi-Fi、甚至手表随机 MAC
都会变）。手机端「自动找手表」按快→慢三层试，任一层成功就停：

| 顺序 | 方式 | 耗时 | 适用 |
|---|---|---|---|
| ① | 上次成功过的 IP（`SharedPrefs` 记住） | <1s | 绝大多数情况，路由器一般给同一 MAC 同一地址 |
| ② | **UDP 广播发现**（新增） | ≈1s | IP 变了也能找到，与网段无关 |
| ③ | 扫整个网段 | ≈10s | 兜底 |

广播发现协议（手表端 `DiscoveryResponder`，与 8899 同步通道分开用 8898）：

```
手机 → 广播  CLASSWATCH_WHO                （发 255.255.255.255 和 x.x.x.255 各一份）
手表 → 单播  CLASSWATCH_HERE|192.168.1.107|8899
```

手机端要拿 `MulticastLock`（很多手机默认丢广播包），权限是
`CHANGE_WIFI_MULTICAST_STATE`。

手表主界面列表底部也会显示当前 IP，需要手动填的时候滚到底就能看到。

### 建议：开一次无线调试，以后不用插线

USB 插着时执行 `adb tcpip 5555`，之后 `adb connect 手表IP:5555` 就能无线
装机、无线抓 log，不用每次都把表摘下来：

```bash
adb tcpip 5555
adb connect 192.168.1.107:5555
# 之后所有 adb 命令都走 Wi-Fi，拔线也没关系
```

---

## 10. 常见问题

### Q1. 表冠滚动是怎么实现的（两套并存）

**OPPO 真机**（ColorOS Watch）：走 `RotaryBus`（见 9.7），Activity 层拦
`ACTION_SCROLL` + `AXIS_VSCROLL` 转发给列表 —— `rotaryScrollable` 认不出它。

**真正的 Wear OS 手表 / 模拟器**：用 Wear Compose 官方的
`Modifier.rotaryScrollable`，不是网上流传的
`Modifier.onRotaryScrollEvent`（foundation 1.7.5 里根本没有这个函数）。

```kotlin
Modifier
    .focusRequester(focusRequester)
    .focusable()
    .rotaryScrollable(
        behavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
        focusRequester = focusRequester
    )
```

配套还要 `LaunchedEffect(Unit) { focusRequester.requestFocus() }` ——
表冠事件只会发给**拿到焦点**的组件，少了这行旋钮就完全不动。

### Q2. Tile 用的是哪套 API

`androidx.wear.tiles:tiles:1.4.0` 里**没有** `SuspendingTileService`，
只有 `TileService` + `ListenableFuture` 这一套（protobuf 时代的 API）。
所以 `ScheduleTileService` 继承的是 `TileService`，重写 `onTileRequest()`
返回一个手写的 `ImmediateFuture`（就 20 行，省掉一个 guava 依赖）。

顺带一提：`TimeText` 的参数名是 `startLinearContent`，不是
`leadingLinearContent` —— 后者在 1.4.0 里不存在。

### Q2.5 真要去掉 Tile 的话

主 App 完全不依赖 Tile，删这三处即可：
1. `watch/.../tile/ScheduleTileService.kt`
2. `watch/src/main/AndroidManifest.xml` 里的 `<service>` 段
3. `watch/build.gradle.kts` 里 `wear-tiles` / `wear-tiles-material` 两行

### Q3. 手表 App 退出后台后同步收不到

当前实现：同步服务跑在 App 进程里（`App.onCreate()` 启动）。进程被系统回收就收不到。
个人自用基本够（Wear OS 一般不会激进杀进程），但要做到 7×24 可靠，需要升级成
**前台服务 + 常驻通知**：加一个 `foregroundService` 权限、`startForeground()` 并显示
「课表同步中 · IP 192.168.1.87:8899」，然后系统就不会轻易杀了。

### Q4. 想让「下一节课」直接显示在表盘上

那是 Complication（复杂功能），需要写一个 `ComplicationDataSourceService`，
再让用户选一个支持复杂功能的表盘。如果你想加，我可以补一段 ——
它跟 Tile 的区别是：Complication 嵌在第三方表盘里，Tile 是独立的一屏。

### Q5. 电池影响

主 App 每秒刷一次 UI，但只在**前台**跑；Tile 是 60 秒刷新一次。
同步服务只是一个阻塞的 socket，几乎不耗电。

---

## 11. 接下来可以做的

按性价比排序：

1. **上课前 10 分钟震动提醒** —— 手表最有价值的场景，用一个
   `AlarmManager` + `BroadcastReceiver` 就能做
2. **前台服务化同步**，让推送随时可用（见 Q4）
3. **导入教务系统课表** —— 很多学校能导出 Excel/ICS，写个解析器直接灌进去
4. **AOD 常亮界面** —— 息屏状态下只显示课程名和剩余时间，省电且够用
5. **节假日调课** —— 数据模型里加一个 `overrides: Map<LocalDate, List<Course>>`，
   优先于周课表匹配
