# DragonBones 模块

这个模块提供了在 Android 应用中使用 DragonBones 骨骼动画的功能。它使用 WebView 加载 DragonBones.js 和 Three.js 来渲染动画。

## 特性

- 支持 DragonBones 骨骼动画的加载和播放
- 提供 Compose 组件和传统 View 组件两种使用方式
- 支持动画控制（播放、停止、设置速度等）
- 支持模型变换（位置、缩放）
- 支持获取动画列表和其他模型信息

## 使用方法

### 1. 准备模型文件

将 DragonBones 模型文件放在 `assets/dragonbones/models/` 目录下，每个模型需要包含以下文件：

- `xxx.json` - 骨骼数据文件
- `xxx_tex.json` - 纹理数据文件
- `xxx_tex.png` - 纹理图片文件

### 2. 在 Compose 中使用

```kotlin
// 创建模型对象
val model = DragonBonesModel(
    skeletonPath = "file:///android_asset/dragonbones/models/loong/loongbones-web.json",
    texturePath = "file:///android_asset/dragonbones/models/loong/loongbones-web_tex.json",
    textureImagePath = "file:///android_asset/dragonbones/models/loong/loongbones-web_tex.png"
)

// 创建配置对象
val config = DragonBonesConfig(
    scale = 0.5f,
    translateX = 0f,
    translateY = 0f,
    speed = 1.0f
)

// 使用 Compose 组件
DragonBonesViewCompose(
    modifier = Modifier.fillMaxSize(),
    model = model,
    config = config,
    animationToPlay = "stand",
    onModelLoaded = {
        // 模型加载完成后的回调
    }
)
```

### 3. 在传统 View 中使用

```kotlin
// 创建 DragonBonesView
val dragonBonesView = DragonBonesView(context)

// 设置加载完成回调
dragonBonesView.setOnReadyCallback {
    // 模型加载完成后的回调
}

// 加载模型
dragonBonesView.loadModel(
    "file:///android_asset/dragonbones/models/loong/loongbones-web.json",
    "file:///android_asset/dragonbones/models/loong/loongbones-web_tex.json",
    "file:///android_asset/dragonbones/models/loong/loongbones-web_tex.png"
)

// 播放动画
dragonBonesView.play("stand")

// 设置速度
dragonBonesView.setSpeed(1.0f)

// 设置位置
dragonBonesView.setPosition(0f, 0f)

// 设置缩放
dragonBonesView.setScale(0.5f)
```

## 示例

查看 `DragonBonesLoongExample.kt` 文件，了解如何使用龙模型的完整示例。

## 注意事项

1. 确保在 AndroidManifest.xml 中添加了 INTERNET 权限
2. 确保模型文件路径正确
3. 模型加载是异步的，请使用回调函数处理加载完成后的逻辑 