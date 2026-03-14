#!/bin/bash
# AndroidForClaw Release Script
# 自动构建并发布到 GitHub Releases

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 版本号 (从 git tag 获取或手动指定)
VERSION=${1:-$(git describe --tags --abbrev=0 2>/dev/null || echo "v1.0.0")}
RELEASE_DIR="release-apks"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}AndroidForClaw Release Builder${NC}"
echo -e "${GREEN}Version: $VERSION${NC}"
echo -e "${GREEN}========================================${NC}"

# 1. Clean build
echo -e "\n${YELLOW}[1/6] Cleaning previous build...${NC}"
./gradlew clean

# 2. Build Release APKs
echo -e "\n${YELLOW}[2/6] Building Release APKs...${NC}"
./gradlew assembleRelease

# 3. Build BrowserForClaw
echo -e "\n${YELLOW}[3/6] Building BrowserForClaw...${NC}"
(cd extensions/BrowserForClaw/android-project && ./gradlew assembleRelease)

# 4. Prepare release directory
echo -e "\n${YELLOW}[4/6] Preparing release directory...${NC}"
mkdir -p $RELEASE_DIR

# 5. Copy APKs with fixed names
echo -e "\n${YELLOW}[5/6] Copying APKs...${NC}"
cp app/build/outputs/apk/release/app-release.apk $RELEASE_DIR/AndroidForClaw.apk
cp extensions/observer/build/outputs/apk/release/observer-release.apk $RELEASE_DIR/ObserverService.apk
cp extensions/BrowserForClaw/android-project/app/build/outputs/apk/release/app-universal-release.apk $RELEASE_DIR/BrowserForClaw.apk

# 6. Show results
echo -e "\n${YELLOW}[6/6] Build complete!${NC}"
echo -e "\n${GREEN}APK files:${NC}"
ls -lh $RELEASE_DIR/*.apk

# Calculate checksums
echo -e "\n${GREEN}SHA256 Checksums:${NC}"
(cd $RELEASE_DIR && sha256sum *.apk > SHA256SUMS.txt)
cat $RELEASE_DIR/SHA256SUMS.txt

# Create release notes
cat > $RELEASE_DIR/RELEASE_NOTES.md << EOF
# AndroidForClaw $VERSION

发布日期: $(date +%Y-%m-%d)

## 📦 下载

### 国内镜像加速 (推荐)
\`\`\`bash
# 主应用
curl -LO "https://ghproxy.com/https://github.com/xiaomochn/AndroidForClaw/releases/download/$VERSION/AndroidForClaw.apk"

# 无障碍服务
curl -LO "https://ghproxy.com/https://github.com/xiaomochn/AndroidForClaw/releases/download/$VERSION/ObserverService.apk"

# 浏览器
curl -LO "https://ghproxy.com/https://github.com/xiaomochn/AndroidForClaw/releases/download/$VERSION/BrowserForClaw.apk"
\`\`\`

## ✨ 更新内容

<!-- 在这里填写更新内容 -->

## 📊 文件信息

\`\`\`
$(cat $RELEASE_DIR/SHA256SUMS.txt)
\`\`\`

## 🚀 安装说明

1. 下载 APK 文件
2. 安装主应用: \`adb install AndroidForClaw.apk\`
3. 安装无障碍服务: \`adb install ObserverService.apk\`
4. (可选) 安装浏览器: \`adb install BrowserForClaw.apk\`

## 📋 要求

- Android 5.0+ (API 21+)
- 约 50MB 存储空间

## ⚙️ 配置

首次使用需要配置 \`/sdcard/.androidforclaw/openclaw.json\`

详见: https://github.com/xiaomochn/AndroidForClaw#configuration

---

**完整文档**: https://github.com/xiaomochn/AndroidForClaw
EOF

echo -e "\n${GREEN}✅ Release package ready in $RELEASE_DIR/${NC}"
echo -e "${GREEN}✅ Release notes created${NC}"
echo -e "\n${YELLOW}Next steps:${NC}"
echo -e "1. Review release notes: ${GREEN}$RELEASE_DIR/RELEASE_NOTES.md${NC}"
echo -e "2. Create GitHub release: ${GREEN}gh release create $VERSION $RELEASE_DIR/*.apk --notes-file $RELEASE_DIR/RELEASE_NOTES.md${NC}"
echo -e "3. Or manually upload to: ${GREEN}https://github.com/xiaomochn/AndroidForClaw/releases/new${NC}"
