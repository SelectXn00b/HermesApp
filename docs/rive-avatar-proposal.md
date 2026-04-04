# AndroidForClaw Rive 角色动画系统 — 综合方案

## 一、目标定位

做一个**纯屏幕 AI 伴侣角色**，对标 AIBI（Living.AI），核心体验：
- 角色通过表情传递情绪，对话驱动 + 触摸互动 + 环境感知
- 永远不静止 — 呼吸、眨眼、微动构成"活着"的感觉
- 表情是连续混合而非离散切换，情绪之间平滑过渡

> AIBI 是最佳对标物：它没有身体运动，所有情感表达 100% 靠屏幕动画完成，与手机端场景完全一致。

---

## 二、技术选型

| 层面 | 方案 | 理由 |
|------|------|------|
| 角色动画引擎 | **Rive** (`app.rive:rive-android:11.3.1`) | GPU 原生渲染器 60FPS，State Machine 内置混合，文件 10-50KB，Compose API 成熟 |
| 场景特效叠加 | **Lottie** (`com.airbnb.android:lottie-compose`) | 雪花/烟花/音符等粒子特效，与 Rive 角色叠加渲染 |
| 唇形同步 | **Rive Number Input + TTS Viseme** | 参考 Duolingo 方案，TTS 引擎输出时间戳 Viseme → 映射到嘴型参数 |
| 角色资产制作 | **Rive 编辑器** (rive.app/editor) | 免费使用，State Machine 可视化编辑 |

---

## 三、Rive 角色架构设计

### 3.1 参数化眼睛系统（核心）

参考 Cozmo/Vector + AIBI 的设计哲学：**"简单即高效，两只大圆眼就够了"**

```
眼睛参数（Number Input, 0.0-1.0）:
├── eyeWidth        眼睛宽度（惊讶=大，不悦=小）
├── eyeHeight       眼睛高度（开心=弯，生气=窄）
├── eyeRoundness    圆角程度
├── pupilX          瞳孔水平偏移（左看/右看/跟随触摸）
├── pupilY          瞳孔垂直偏移
├── lidTop          上眼睑位置（困=下垂，惊讶=抬高）
├── lidBottom       下眼睑位置（笑=上抬）
├── eyeSlope        眼睛倾斜角度（生气=内八，悲伤=外八）
└── squash          挤压拉伸（弹跳反馈）
```

每种情绪 = 一组参数预设，混合过渡而非硬切：

| 情绪 | eyeHeight | eyeWidth | lidTop | lidBottom | eyeSlope | 补充 |
|------|-----------|----------|--------|-----------|----------|------|
| neutral | 0.5 | 0.5 | 0.3 | 0.0 | 0.0 | 基准态 |
| happy | 0.3 | 0.6 | 0.2 | 0.4 | 0.0 | 弯眼笑 |
| excited | 0.8 | 0.7 | 0.0 | 0.0 | 0.0 | 大圆眼 |
| sad | 0.4 | 0.4 | 0.5 | 0.0 | -0.3 | 耷拉 |
| angry | 0.3 | 0.5 | 0.4 | 0.0 | 0.4 | 内八 |
| sleepy | 0.2 | 0.4 | 0.7 | 0.0 | 0.0 | 半闭 |
| surprised | 0.9 | 0.8 | 0.0 | 0.0 | 0.0 | 全开 |
| curious | 0.6 | 0.55 | 0.1 | 0.0 | 0.1 | 微歪 |

### 3.2 多 Layer State Machine 架构

```
Rive Artboard: "CompanionCharacter"
│
├── Layer 1: Base Expression（基础表情）
│   └── Blend State: 通过 emotion_* Number Input 混合
│       neutral ←→ happy ←→ sad ←→ angry ←→ ...
│
├── Layer 2: Blink（眨眼）
│   └── 独立循环，随机间隔 2-6 秒
│   └── Trigger: forceBlink
│
├── Layer 3: Breath（呼吸）
│   └── 永续微弱缩放循环（0.98-1.02），永不停止
│
├── Layer 4: Eye Saccade（微动）
│   └── 随机小幅瞳孔位移，模拟真实眼球微跳
│
├── Layer 5: Mouth / Lip Sync（嘴部）
│   └── Number Input: mouthOpen (0-1), viseme_* (多个口型)
│   └── 非说话时：微笑/抿嘴等随表情变化
│
├── Layer 6: Reactive Overlay（反应动画）
│   └── Trigger: onTouch, onShake, onLaugh, onCry
│   └── 播放后自动回退到基础层
│
└── Layer 7: Scene Animation（场景动画）
    └── Trigger: dance, sleep, eat, exercise, celebrate
    └── 全身参与的完整动画序列
```

### 3.3 嵌套 Artboard（Nested Artboard / Component）

```
主 Artboard
├── 背景 Component（天气/时间主题背景）
├── 角色 Component（上述 7 层结构）
├── 前景特效 Component（Lottie 叠加或 Rive 粒子）
└── UI 覆盖 Component（状态指示器等）
```

---

## 四、AI 情绪驱动系统

### 4.1 Agent 侧：情绪标签输出

在 Agent 系统提示中注入：

```
在回复末尾附加情绪标签：[emotion:happy,0.8] [emotion:curious,0.3]
支持的情绪: happy, sad, angry, surprised, excited, sleepy, curious, neutral
数值 0.0-1.0 表示强度
可叠加多个情绪标签（混合表情）
```

### 4.2 Client 侧：标签解析 → Rive 输入

```kotlin
// 解析情绪标签
data class EmotionTag(val emotion: String, val intensity: Float)

fun parseEmotionTags(text: String): List<EmotionTag> {
    val regex = """\[emotion:(\w+),?([\d.]*)]""".toRegex()
    return regex.findAll(text).map {
        EmotionTag(
            emotion = it.groupValues[1],
            intensity = it.groupValues[2].toFloatOrNull() ?: 1.0f
        )
    }.toList()
}

// 映射到 Rive State Machine
fun applyEmotions(stateMachine: StateMachineInstance, emotions: List<EmotionTag>) {
    // 先重置所有情绪为 0
    EMOTION_NAMES.forEach { stateMachine.setNumberInput("emotion_$it", 0f) }
    // 应用新情绪
    emotions.forEach { tag ->
        stateMachine.setNumberInput("emotion_${tag.emotion}", tag.intensity)
    }
}
```

### 4.3 情绪衰减模型

```kotlin
class EmotionState {
    private val emotions = mutableMapOf<String, Float>()  // 当前情绪值
    private val baselineMood = mutableMapOf<String, Float>()  // 长期基线

    fun applyReactiveEmotion(emotion: String, intensity: Float) {
        emotions[emotion] = intensity
    }

    fun tick(deltaSeconds: Float) {
        // 反应情绪以指数衰减向基线回归
        emotions.forEach { (key, value) ->
            val baseline = baselineMood[key] ?: 0f
            val decayRate = 0.3f  // 每秒衰减 30%
            emotions[key] = baseline + (value - baseline) * exp(-decayRate * deltaSeconds)
        }
    }

    fun updateBaseline(emotion: String, delta: Float) {
        // 长期交互模式缓慢调整基线（模拟 AIBI 的性格演化）
        val current = baselineMood[emotion] ?: 0f
        baselineMood[emotion] = (current + delta * 0.01f).coerceIn(0f, 0.3f)
    }
}
```

---

## 五、Idle 行为系统（"活着"的关键）

### 5.1 设计原则

> **AIBI 和 EMO 永远不会完全静止。** 即使无交互，也通过微动保持"存在感"。

参考 AIBI 的 idle 行为：

| 微行为 | 频率 | 幅度 | 实现 |
|--------|------|------|------|
| 眨眼 | 2-6 秒随机 | 全闭 200ms | Rive Layer 2 自动循环 |
| 呼吸 | 持续 | 缩放 ±2% | Rive Layer 3 永续 |
| 眼球微跳 | 3-8 秒随机 | pupilX/Y ±5% | Rive Layer 4 |
| 左右看 | 15-30 秒随机 | pupilX 大幅偏移 | Kotlin 定时器 → Rive 输入 |
| 好奇歪头 | 30-60 秒随机 | 整体倾斜 ±5° | Trigger: idleCurious |
| 打哈欠 | 3-5 分钟 | 嘴大开 + 眼眯 | Trigger: yawn（仅 sleepy 基线高时） |
| 发呆 | 2-3 分钟无交互 | 表情渐变到 bored | EmotionState 自动衰减 |

### 5.2 加权随机选择（参考 ESP32-Eyes / Cozmo）

```kotlin
class IdleBehaviorScheduler(private val emotionState: EmotionState) {
    data class IdleBehavior(
        val trigger: String,
        val baseWeight: Float,
        val moodBias: Map<String, Float> = emptyMap(),  // 某情绪高时权重增加
        val cooldownMs: Long = 30_000L
    )

    private val behaviors = listOf(
        IdleBehavior("idleLookAround", 0.3f, cooldownMs = 15_000),
        IdleBehavior("idleCurious",    0.2f, moodBias = mapOf("curious" to 0.5f)),
        IdleBehavior("idleYawn",       0.1f, moodBias = mapOf("sleepy" to 0.8f)),
        IdleBehavior("idleBounce",     0.15f, moodBias = mapOf("happy" to 0.4f)),
        IdleBehavior("idleSigh",       0.1f, moodBias = mapOf("sad" to 0.5f)),
        IdleBehavior("idleStretch",    0.1f, cooldownMs = 60_000),
    )

    fun selectNext(): String {
        // 加权轮盘选择，当前情绪影响权重
        val weighted = behaviors.map { b ->
            val moodBonus = b.moodBias.entries.sumOf { (mood, bias) ->
                (emotionState.get(mood) * bias).toDouble()
            }.toFloat()
            b to (b.baseWeight + moodBonus)
        }
        return weightedRandom(weighted).trigger
    }
}
```

---

## 六、触摸互动

```kotlin
// Compose 手势 → Rive 输入
Rive(
    riveFile,
    modifier = Modifier
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { offset ->
                    val zone = detectZone(offset, size)  // head / body / feet
                    when (zone) {
                        "head" -> stateMachine.fireInput("onPetHead")   // 开心眯眼
                        "body" -> stateMachine.fireInput("onPoke")      // 惊讶弹跳
                    }
                },
                onDoubleTap = {
                    stateMachine.fireInput("onDoubleTap")  // 逗弄 → 略带不悦
                },
                onLongPress = {
                    stateMachine.fireInput("onHug")  // 拥抱 → 满足
                }
            )
        }
        .pointerInput(Unit) {
            detectDragGestures { _, dragAmount ->
                // 瞳孔跟随手指
                stateMachine.setNumberInput("pupilX", (dragAmount.x / size.width).coerceIn(-1f, 1f))
                stateMachine.setNumberInput("pupilY", (dragAmount.y / size.height).coerceIn(-1f, 1f))
            }
        }
)
```

---

## 七、唇形同步（Lip Sync）

参考 Duolingo 的 Viseme 方案：

```
TTS Engine (Android TTS / Edge TTS)
    ↓ UtteranceProgressListener.onRangeStart()
    ↓ 或 自定义 Viseme 时间戳
    ↓
Viseme 映射表:
    silence → mouthOpen: 0.0
    AA/AH  → mouthOpen: 0.9,  mouthWidth: 0.6
    EE     → mouthOpen: 0.3,  mouthWidth: 0.8
    OO     → mouthOpen: 0.7,  mouthWidth: 0.3
    MM/PP  → mouthOpen: 0.0,  mouthWidth: 0.4
    ...
    ↓
Rive Number Inputs: mouthOpen, mouthWidth, mouthRound
```

**简化方案（V1）：** 不做精确 Viseme，只做"说话时嘴巴动"：
- 检测 TTS 播放中 → mouthOpen 在 0.2-0.8 之间随音频振幅波动
- 停止说话 → mouthOpen 平滑回归 0

---

## 八、场景动画

全身参与的特殊动画序列，通过 Trigger 触发：

| 场景 | 触发条件 | 动画描述 | 时长 |
|------|---------|---------|------|
| dance | 音乐检测 / 开心对话 | 左右摇摆 + 眼睛闪亮 | 5-15 秒 |
| sleep | 夜间 + 长时间无交互 | 渐入闭眼 + ZZZ 气泡 | 持续 |
| eat | 用户提到美食 | 咀嚼动画 + 满足表情 | 3 秒 |
| exercise | 用户提到运动 | 蹦跳 + 冒汗 | 5 秒 |
| celebrate | 生日 / 节日 / 任务完成 | 放烟花 + 跳跃 | 5 秒 |
| think | 复杂问题处理中 | 眼珠转动 + 冒泡 | 持续至完成 |
| greet | 每日首次打开 | 挥手 + 开心跳跃 | 2 秒 |

---

## 九、环境感知层

| 上下文 | 数据源 | 角色反应 |
|--------|--------|---------|
| 时间 | System.currentTimeMillis | 早:精神 → 午:正常 → 晚:困 → 深夜:打盹 |
| 天气 | WeatherAPI | 雨:忧郁+打伞, 晴:开心, 雪:兴奋+围巾 |
| 节日 | 日历 | 圣诞:圣诞帽, 春节:舞龙, 万圣:南瓜 |
| 电量 | BatteryManager | <20%:疲惫, <5%:濒死挣扎 |
| 长时间无交互 | Timer | 发呆 → 无聊 → 睡着 |

---

## 十、Rive 角色资产方案

### 方案 A：基于社区资产改造（快速启动）

从 Rive 社区获取基础资产，二次定制：

| 资产 | 用途 | 改造点 |
|------|------|--------|
| **Cute Robot (JcToon)** | 主角色骨架 | 简化为圆眼风格，添加嘴部 |
| **Facial Expression Demo (JcToon)** | 表情混合架构 | 直接复用 BlendAdditive 结构 |
| **Ghost (JcToon)** | Blend State 参考 | 学习多层混合技术 |
| **Android Rive Piggy** | 多 Artboard 叠加参考 | 学习 Android 端多层渲染 |

### 方案 B：全定制（推荐，体验最佳）

请 Rive 设计师按需求定制角色：
- 圆眼萌系风格（参考 AIBI 的 LED 大眼睛）
- 参数化骨骼绑定（上述 9 个眼睛参数 + 嘴部参数）
- 7 Layer State Machine 完整搭建
- 8+ 基础表情预设 + 6+ 场景动画 + 5+ 触摸反应

### 方案 C：混合方案（推荐起步）

1. **V0（1 周）**: 用 JcToon Facial Expression Demo 的架构，搭建 Android 端集成 + 情绪驱动管道
2. **V1（2-3 周）**: 在 Rive 编辑器中自制简化角色（圆眼 + 简单身体），接入全部 7 层
3. **V2（持续）**: 迭代角色设计，增加场景动画和环境感知

---

## 十一、Android 集成架构

```
┌─────────────────────────────────────────┐
│              RiveAvatarScreen            │  ← Jetpack Compose
│  ┌────────────────────────────────────┐  │
│  │  Lottie 场景特效层 (雪花/烟花)     │  │
│  │  ┌──────────────────────────────┐  │  │
│  │  │   Rive() Composable          │  │  │
│  │  │   ├── artboard: Companion    │  │  │
│  │  │   ├── stateMachine: Main     │  │  │
│  │  │   └── Touch Gesture Handler  │  │  │
│  │  └──────────────────────────────┘  │  │
│  └────────────────────────────────────┘  │
│  ┌────────────────────────────────────┐  │
│  │  对话输入区 (可选)                   │  │
│  └────────────────────────────────────┘  │
└─────────────────────────────────────────┘

┌──────────────────────┐  ┌──────────────────────┐
│  RiveAvatarController │  │   EmotionEngine      │
│  - loadRiveFile()     │  │   - parseEmotionTag()│
│  - setEmotion()       │  │   - decayToBaseline()│
│  - fireTrigger()      │  │   - updateBaseline() │
│  - setMouthOpen()     │  │   - getEmotionMap()  │
│  - setPupilPosition() │  │   - tickEveryFrame() │
└──────────┬───────────┘  └──────────┬───────────┘
           │                          │
┌──────────┴──────────────────────────┴───────────┐
│              IdleBehaviorScheduler               │
│  - weighted random idle selection                │
│  - mood-biased behavior probabilities            │
│  - cooldown management                           │
└──────────────────────────────────────────────────┘
           │
┌──────────┴───────────┐  ┌──────────────────────┐
│   EnvironmentSensor   │  │   LipSyncController  │
│   - time of day       │  │   - TTS viseme map   │
│   - weather API       │  │   - audio amplitude  │
│   - battery level     │  │   - mouth params     │
│   - calendar events   │  │                      │
└───────────────────────┘  └──────────────────────┘
```

---

## 十二、与现有 AvatarTool 的关系

**完全独立的新功能**，不修改现有 Live2D 系统。

| | Live2D（现有） | Rive（新建） |
|---|---|---|
| 入口 | AvatarTab → FloatingAvatarService | 新 Tab "Rive Avatar" |
| 渲染 | OpenGL ES 2.0 GLSurfaceView 30FPS | Rive GPU Renderer Compose 60FPS |
| 模型 | Hiyori (.moc3 + .model3.json) 4.7MB | 自制角色 (.riv) ~50KB |
| 控制 | AvatarTool 6 actions + 17 params | RiveAvatarTool N actions + 参数化输入 |
| 状态 | AvatarStateHolder (StateFlow) | EmotionEngine (StateFlow) |

用户可在设置中选择使用哪套角色系统。

---

## 十三、关键参考资源

### 必读
1. **[Duolingo Viseme Lip Sync](https://blog.duolingo.com/world-character-visemes/)** — 唇形同步工业级方案
2. **[Rive Teddy Bear Login](https://rive-app.github.io/rive-use-cases/?path=/story/example-loginformcomponent--primary)** — 表情随输入变化的经典案例
3. **[Android Rive Piggy](https://github.com/mjtalbot/rive_piggy)** — Android 多 Artboard 层叠
4. **[ESP32-Eyes](https://github.com/intellar/esp32_eyes)** — 参数化眼睛 + 加权随机情绪系统

### 社区资产
5. **[Cute Robot - JcToon](https://rive.app/marketplace/3364-7075-cute-robot/)** — 最接近 AIBI 的机器人角色
6. **[Facial Expression Demo - JcToon](https://rive.app/community/files/669-1300-facial-expression-demo/)** — 表情混合架构参考
7. **[Ghost - JcToon](https://rive.app/community/files/3814-7990-gost/)** — Blend State 多层混合
8. **[Cloud Mascot 6 Expressions](https://rive.app/community/files/26964-50676)** — 生产级 6 表情 State Machine

### AI + Rive
9. **[Choose Your Own Adventure (ChatGPT + Rive)](https://www.youtube.com/watch?v=szSzawXa918)** — AI 驱动 Rive 动画案例
10. **[Don't Look at Me](https://github.com/zplata/dont-look-at-me)** — 检测驱动表情 State Machine

### 技术文档
11. **[Rive Android Runtime](https://rive.app/docs/runtimes/android/android)** — 官方 Android 集成文档
12. **[Rive State Machines](https://help.rive.app/editor/state-machine/)** — State Machine 编辑器指南
13. **[Rive Blend States](https://help.rive.app/editor/state-machine/blend-states)** — 混合状态文档

---

## 十四、推荐实施路线

```
V0 — 管道验证（1 周）
├── Rive SDK 集成 + Compose API
├── 加载社区 .riv 文件（Facial Expression Demo）
├── Kotlin 代码控制 Number Input 切换表情
└── 验证: AI 回复 → 解析 emotion tag → Rive 表情变化

V1 — 核心体验（2-3 周）
├── 自制简化角色（Rive 编辑器）
├── 7 层 State Machine 搭建
├── EmotionEngine + IdleBehaviorScheduler
├── 触摸互动（摸头/戳/长按）
├── 基础 lip sync（振幅驱动）
└── 验证: 角色"活着"— 呼吸、眨眼、微动、情绪响应

V2 — 丰富内容（2-3 周）
├── 场景动画（dance / sleep / eat / celebrate）
├── 环境感知（时间/天气/电量）
├── Lottie 特效叠加
├── 精确 Viseme lip sync
└── RiveAvatarTool 接入 Agent 系统

V3 — 打磨迭代（持续）
├── 更多角色皮肤（Skinning）
├── 角色换装系统
├── 性格演化（长期交互影响基线情绪）
├── 节日/活动限定动画
└── 用户自定义表情偏好
```
