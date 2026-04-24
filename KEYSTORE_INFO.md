# BrowserForClaw 签名密钥信息

## 密钥库文件
**文件名**: `browserforclaw-release.keystore`  
**位置**: `/Users/qiao/file/forclaw/browserforclaw-release.keystore`

## 密钥信息

### Keystore 密码
```
browserforclaw123
```

### Key 别名
```
browserforclaw
```

### Key 密码
```
browserforclaw123
```

## 密钥详细信息

- **算法**: RSA
- **密钥长度**: 2048 位
- **有效期**: 10,000 天
- **签名算法**: SHA256withRSA
- **Distinguished Name**:
  - CN: BrowserForClaw
  - OU: Development
  - O: ForClaw
  - L: Unknown
  - ST: Unknown
  - C: CN

## 使用方法

### 方法 1: Gradle 命令行签名
```bash
cd /Users/qiao/file/forclaw/einkbro

./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/Users/qiao/file/forclaw/browserforclaw-release.keystore \
  -Pandroid.injected.signing.store.password=browserforclaw123 \
  -Pandroid.injected.signing.key.alias=browserforclaw \
  -Pandroid.injected.signing.key.password=browserforclaw123
```

### 方法 2: 手动签名已有 APK
```bash
# 对齐 APK
zipalign -v -p 4 app-release-unsigned.apk app-release-aligned.apk

# 签名
apksigner sign \
  --ks /Users/qiao/file/forclaw/browserforclaw-release.keystore \
  --ks-pass pass:browserforclaw123 \
  --key-pass pass:browserforclaw123 \
  --out app-release-signed.apk \
  app-release-aligned.apk

# 验证签名
apksigner verify app-release-signed.apk
```

### 方法 3: 配置 build.gradle
```kotlin
// einkbro/app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file("/Users/qiao/file/forclaw/browserforclaw-release.keystore")
            storePassword = "browserforclaw123"
            keyAlias = "browserforclaw"
            keyPassword = "browserforclaw123"
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... other config
        }
    }
}
```

## 密钥管理建议

### ⚠️ 安全注意事项
1. **不要提交到 Git**
   - keystore 文件已在 .gitignore 中
   - 密码信息不要提交到公开仓库
   
2. **备份密钥**
   - 定期备份 keystore 文件到安全位置
   - 丢失密钥将无法更新已发布的 APK

3. **密码管理**
   - 生产环境建议使用更强密码
   - 使用密码管理器存储

### 查看密钥信息
```bash
keytool -list -v -keystore /Users/qiao/file/forclaw/browserforclaw-release.keystore \
  -storepass browserforclaw123
```

### 导出证书
```bash
keytool -exportcert \
  -alias browserforclaw \
  -keystore /Users/qiao/file/forclaw/browserforclaw-release.keystore \
  -storepass browserforclaw123 \
  -file browserforclaw.cer
```

## 当前已签名版本

- **v0.5.1**: 使用此密钥签名
- **发布日期**: 2026-03-06
- **APK 位置**: `browserforclaw/releases/BrowserForClaw-v0.5.1.apk`

## 重新生成密钥（如果需要）

⚠️ **警告**: 重新生成密钥将导致无法更新已发布的应用！

```bash
keytool -genkeypair -v \
  -keystore browserforclaw-release.keystore \
  -alias browserforclaw \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass browserforclaw123 \
  -keypass browserforclaw123 \
  -dname "CN=BrowserForClaw, OU=Development, O=ForClaw, L=Unknown, ST=Unknown, C=CN"
```

---

**创建日期**: 2026-03-06  
**最后更新**: 2026-03-06  
**维护人**: qiao
