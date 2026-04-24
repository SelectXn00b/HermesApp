# AndroidForClaw 签名信息

## 统一签名配置

所有 AndroidForClaw 相关应用使用同一个签名:

### 签名文件
- **路径**: `/Users/qiao/file/forclaw/keystore.jks`
- **类型**: PKCS12
- **创建日期**: 2026年3月9日
- **有效期**: 2026-2053 (27年)

### 签名密钥
```
keyAlias: android
keyPassword: android
storePassword: android
```

### 证书信息
- **所有者**: CN=AndroidForClaw, OU=Development, O=AndroidForClaw, L=Beijing, ST=Beijing, C=CN
- **SHA1**: D1:DD:BC:72:84:8E:FF:99:10:FA:EB:C8:4D:94:A9:FE:A5:24:5E:94
- **SHA256**: 95:7E:0A:25:92:85:07:B9:24:88:F2:73:65:51:78:1B:D3:78:E1:82:60:72:04:6E:0B:2C:95:DF:8C:3E:F2:1F

## 应用列表

使用此签名的应用:

1. **AndroidForClaw** (主应用)
   - 包名: `com.xiaomo.androidforclaw`
   - APK: `androidforclaw-v2.4.3-release.apk`
   - 配置: `phoneforclaw/app/build.gradle`

2. **Screen4Claw** (无障碍服务)
   - 包名: `com.xiaomo.androidforclaw.observer`
   - APK: `Screen4Claw-v2.4.3-release.apk`
   - 配置: `phoneforclaw/extensions/observer/build.gradle`

3. **BClaw** (浏览器)
   - 包名: `info.plateaukao.einkbro`
   - APK: `BClaw-universal-release.apk`
   - 配置: `phoneforclaw/extensions/BrowserForClaw/android-project/app/build.gradle.kts`

## Gradle 配置示例

### Groovy (app/build.gradle)
```groovy
signingConfigs {
    debug {
        keyAlias 'android'
        keyPassword 'android'
        storeFile project.rootProject.file('../keystore.jks')
        storePassword 'android'
        enableV1Signing true
        enableV2Signing true
        enableV3Signing true
        enableV4Signing true
    }
    release {
        keyAlias 'android'
        keyPassword 'android'
        storeFile project.rootProject.file('../keystore.jks')
        storePassword 'android'
        enableV1Signing true
        enableV2Signing true
        enableV3Signing true
        enableV4Signing true
    }
}

buildTypes {
    release {
        signingConfig signingConfigs.release
        // ...
    }
    debug {
        signingConfig signingConfigs.debug
        // ...
    }
}
```

### Kotlin DSL (build.gradle.kts)
```kotlin
signingConfigs {
    create("release") {
        keyAlias = "android"
        keyPassword = "android"
        storeFile = project.rootProject.file("../../../../keystore.jks")
        storePassword = "android"
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = true
    }
    getByName("debug") {
        keyAlias = "android"
        keyPassword = "android"
        storeFile = project.rootProject.file("../../../../keystore.jks")
        storePassword = "android"
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = true
    }
}

buildTypes {
    getByName("release") {
        signingConfig = signingConfigs.getByName("release")
        // ...
    }
}
```

## 注意事项

1. **签名文件位置**: 签名文件在 Git 仓库外 (`/Users/qiao/file/forclaw/`)，不会被提交
2. **密码安全**: 密码已硬编码在 build.gradle 中，仅用于个人开发
3. **统一签名**: 所有应用使用同一签名，确保应用间可以共享数据和权限
4. **路径引用**: 各模块使用相对路径引用签名文件 (`../keystore.jks` 或 `../../../../keystore.jks`)

## 验证签名

查看 APK 签名信息:
```bash
# 方法1: 使用 apksigner
apksigner verify --print-certs app-release.apk

# 方法2: 使用 keytool
keytool -printcert -jarfile app-release.apk

# 方法3: 查看签名文件
keytool -list -v -keystore keystore.jks -storepass android
```

## 重新生成签名 (如需)

```bash
keytool -genkey -v \
  -keystore keystore.jks \
  -alias android \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass android \
  -keypass android \
  -dname "CN=AndroidForClaw, OU=Development, O=AndroidForClaw, L=Beijing, ST=Beijing, C=CN"
```

---

**最后更新**: 2026-03-09
