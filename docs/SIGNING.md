# HermesApp Signing

HermesApp (applicationId `com.xiaomo.androidforclaw`) 的签名配置、证书指纹、打包和验证流程。

## Keystore

**路径**：`/Users/qiao/file/HermesApp/release.jks`（**不进 git**，在 `.gitignore` 的 `*.jks` 里）

**被 Gradle 引用的方式**：`HermesApp/local.properties`（也不进 git）

```properties
RELEASE_STORE_FILE=/Users/qiao/file/HermesApp/release.jks
RELEASE_STORE_PASSWORD=<store password>
RELEASE_KEY_ALIAS=xiaomo
RELEASE_KEY_PASSWORD=<key password>
```

`app/build.gradle.kts` 的 `signingConfigs` 读这四个属性，`release` 和 `debug`（以及 `nightly`，用 debug cert）都会用同一份 release 签名。

## 证书指纹

Keystore alias `xiaomo`，DN `CN=xiaomo, OU=forclaw, O=forclaw, L=Beijing, ST=Beijing, C=CN`：

| 算法 | 指纹 |
|---|---|
| SHA-1 | `14:2A:AF:59:6A:98:26:68:C0:BF:2D:D2:A2:51:5C:0F:85:1B:92:FA` |
| SHA-256 | `5C:96:77:D0:79:D3:E4:D3:9D:57:E7:56:E7:84:52:6F:C3:AB:2A:BC:F3:AF:48:E2:A5:90:30:0F:58:BD:2F:E7` |

Google Play Console 的 App Signing 证书与此 keystore 一致（即 release.jks 就是 Play 签名 key）。

## 常用命令

### 查看 keystore 内容
```bash
keytool -list -v -keystore /Users/qiao/file/HermesApp/release.jks
```

### 验证已打包 APK 的签名
```bash
apksigner verify --print-certs app-release.apk
# 或
keytool -printcert -jarfile app-release.apk
```

### 打 release APK
```bash
export JAVA_HOME="/Users/qiao/Library/Java/JavaVirtualMachines/semeru-21.0.3/Contents/Home"
cd HermesApp
./gradlew :app:assembleRelease
# 产物: app/build/outputs/apk/release/app-release.apk
```

### 打 debug APK（签同一个 release cert）
```bash
./gradlew :app:assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

> debug 和 release 都会用 release signing config（见 `app/build.gradle.kts` L99-101），
> 这样 E2E 跑 debug APK 和上架 release APK 的签名一致，避免"本地绿/线上红"。

### nightly 构建（文件名固定 `app-nightly.apk`）
```bash
./gradlew :app:assembleNightly
```

## 重新生成 keystore（丢失时的应急流程）

⚠️ **警告**：重新生成等于**新应用**——Play Store 无法用新 key 更新已发布的 APK，
旧版本用户收不到更新。只有从未上架或可接受全量迁移时才用。

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias xiaomo \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=xiaomo, OU=forclaw, O=forclaw, L=Beijing, ST=Beijing, C=CN"
# 按提示输入 store / key password, 同步更新 local.properties
```

## 历史背景（仅备查）

HermesApp 沿用 forclaw 体系的 applicationId `com.xiaomo.androidforclaw`，但签名 keystore
从 forclaw 早期的 `keystore.jks`（CN=AndroidForClaw，已弃用）迁到了当前
`release.jks`（CN=xiaomo）。Play Console 上的 App Signing cert 已切到 CN=xiaomo 这份，
旧 CN=AndroidForClaw cert 不再有效。旧 keystore 的 store/key password 见私有备份，
不在本仓库任何文件或历史里留存（此前误写于本节，已于 2026-04-25 redact）。
