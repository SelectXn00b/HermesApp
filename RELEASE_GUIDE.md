# 📦 发布指南

## 🚀 快速发布

```bash
# 1. 构建并准备 Release 包
./scripts/release.sh v1.0.0

# 2. 使用 GitHub CLI 发布 (推荐)
gh release create v1.0.0 \
  release-apks/*.apk \
  --title "AndroidForClaw v1.0.0" \
  --notes-file release-apks/RELEASE_NOTES.md

# 3. 或者手动上传
# 访问: https://github.com/xiaomochn/AndroidForClaw/releases/new
```

## 📥 下载加速方案

### 方案 1: GitHub Releases + 镜像 (推荐,免费)

**优点**:
- ✅ 完全免费
- ✅ 版本管理方便
- ✅ 国内镜像加速

**用户下载**:
```bash
# 使用 ghproxy 镜像
https://ghproxy.com/https://github.com/xiaomochn/AndroidForClaw/releases/latest/download/AndroidForClaw.apk

# 使用备用镜像
https://gh.api.99988866.xyz/https://github.com/...
```

**可用镜像列表**:
- `https://ghproxy.com/` (稳定)
- `https://gh.api.99988866.xyz/` (备用)
- `https://mirror.ghproxy.com/` (备用)
- `https://github.moeyy.xyz/` (备用)

### 方案 2: 七牛云 CDN (免费额度)

**优点**:
- ✅ 每月 10GB 免费流量
- ✅ 国内高速
- ✅ 简单易用

**配置步骤**:
```bash
# 1. 注册七牛云: https://www.qiniu.com
# 2. 创建存储空间
# 3. 配置 CDN 加速
# 4. 上传 APK

# 使用 qshell 上传
qshell fput <bucket> AndroidForClaw.apk release-apks/AndroidForClaw.apk
```

### 方案 3: 阿里云 OSS (按量付费)

**优点**:
- ✅ 稳定可靠
- ✅ 国内高速
- ✅ 企业级

**成本**:
- 约 0.5 元/GB 流量
- 约 0.12 元/GB/月 存储

**配置步骤**:
```bash
# 1. 注册阿里云: https://www.aliyun.com
# 2. 创建 OSS Bucket
# 3. 配置 CDN
# 4. 使用 ossutil 上传

ossutil cp release-apks/AndroidForClaw.apk oss://your-bucket/releases/v1.0.0/
```

### 方案 4: 应用商店 (推荐长期)

**酷安**:
- 审核周期: 1-3 天
- 免费
- 国内用户友好

**小米应用商店**:
- 审核周期: 3-7 天
- 免费
- 小米用户优先

**F-Droid**:
- 开源应用商店
- 审核严格
- 国际用户

## 📋 发布清单

- [ ] 更新版本号 (app/build.gradle)
- [ ] 更新 CHANGELOG
- [ ] 运行测试
- [ ] 构建 Release 包
- [ ] 验证签名
- [ ] 创建 Git Tag
- [ ] 发布到 GitHub Releases
- [ ] (可选) 上传到 CDN
- [ ] (可选) 提交到应用商店
- [ ] 更新文档
- [ ] 通知用户

## 🔐 签名配置

确保 `keystore.jks` 在以下位置:
```
~/file/forclaw/keystore/keystore.jks
```

配置在 `app/build.gradle`:
```gradle
signingConfigs {
    release {
        storeFile file("../../keystore/keystore.jks")
        storePassword System.getenv("KEYSTORE_PASSWORD") ?: "your-password"
        keyAlias "androidforclaw"
        keyPassword System.getenv("KEY_PASSWORD") ?: "your-password"
    }
}
```

## 📊 统计与监控

### GitHub Release 下载统计
```bash
# 查看下载次数
gh api repos/xiaomochn/AndroidForClaw/releases/latest | jq '.assets[] | {name, download_count}'
```

### 用户反馈渠道
- GitHub Issues
- 飞书群组
- Discord 服务器

## 🆘 常见问题

### Q: GitHub Release 下载很慢?
A: 提供镜像加速链接给用户,或考虑使用 CDN

### Q: APK 文件太大怎么办?
A:
1. 启用 ProGuard/R8 代码混淆和压缩
2. 使用 APK Split 分离架构
3. 移除未使用的资源

### Q: 如何自动化发布?
A: 使用 GitHub Actions (参考 `.github/workflows/release.yml`)

---

**最佳实践**: GitHub Releases + 镜像加速 + 应用商店多渠道发布
