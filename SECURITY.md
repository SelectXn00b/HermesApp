# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| main    | :white_check_mark: |

## Reporting a Vulnerability

如果你发现了安全漏洞，请**不要**公开提交 Issue。

请通过以下方式私下报告：

1. **GitHub Security Advisory** (推荐)
   - 访问 [Security Advisories](https://github.com/xiaomo/phoneforclaw/security/advisories)
   - 点击 "Report a vulnerability"
   - 填写漏洞详情

2. **Email**
   - 发送邮件至：[INSERT EMAIL]
   - 主题：[Security] phoneforclaw vulnerability report

## 安全最佳实践

使用 phoneforclaw 时，请遵循以下安全建议：

### 1. API Key 管理

- ✅ **推荐**: 将 API Key 存储在配置文件中（`/sdcard/.hermes/config/models.json`）
- ✅ **推荐**: 使用环境变量（`${ANTHROPIC_API_KEY}`）
- ❌ **禁止**: 将 API Key 硬编码在代码中
- ❌ **禁止**: 将包含 API Key 的配置文件提交到版本控制

### 2. 权限控制

- 只授予必要的权限（Accessibility, Display Over Apps, MediaProjection）
- 定期检查应用权限使用情况
- 不要在不可信设备上使用

### 3. 数据安全

- 配置文件可能包含敏感信息，请妥善保管
- 不要在公共网络下传输配置文件
- 定期清理日志和缓存

### 4. Gateway 模式（规划中）

当 Gateway 功能上线后：
- 使用强密码和配对机制
- 启用 allowlist 限制访问
- 使用 HTTPS/WSS 加密传输
- 定期更新密钥

## 已知限制

1. **Accessibility Service** - 可访问屏幕上的所有内容
2. **Display Over Apps** - 可覆盖其他应用
3. **MediaProjection** - 可截取屏幕内容

这些权限是核心功能所需，但也带来安全风险。请确保：
- 从可信来源安装应用
- 不要在敏感操作时使用（如网银、支付等）
- 使用前理解并接受风险

## 安全更新

安全相关更新会优先发布，请及时更新到最新版本。

---

**免责声明**: 使用本项目产生的任何后果由使用者自行承担。请遵守相关法律法规和应用服务条款。
