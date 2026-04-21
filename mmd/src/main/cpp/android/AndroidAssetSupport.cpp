#include "android/AndroidAssetSupport.h"

#include <algorithm>
#include <cctype>
#include <mutex>

namespace operit::androidbridge {
namespace {

std::mutex gAssetManagerMutex;
AAssetManager* gAssetManager = nullptr;

std::string ToLowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return value;
}

std::string BasenameLower(const std::string& path) {
    const auto slash = path.find_last_of("/\\");
    const std::string filename = slash == std::string::npos ? path : path.substr(slash + 1);
    return ToLowerAscii(filename);
}

}  // namespace

void SetAssetManager(AAssetManager* assetManager) {
    std::lock_guard<std::mutex> lock(gAssetManagerMutex);
    gAssetManager = assetManager;
}

AAssetManager* GetAssetManager() {
    std::lock_guard<std::mutex> lock(gAssetManagerMutex);
    return gAssetManager;
}

std::string NormalizeAssetPath(std::string assetPath) {
    std::replace(assetPath.begin(), assetPath.end(), '\\', '/');
    while (!assetPath.empty() && assetPath.front() == '/') {
        assetPath.erase(assetPath.begin());
    }
    return assetPath;
}

bool AssetExists(const std::string& assetPath) {
    AAssetManager* assetManager = GetAssetManager();
    if (assetManager == nullptr) {
        return false;
    }

    const std::string normalized = NormalizeAssetPath(assetPath);
    AAsset* asset = AAssetManager_open(assetManager, normalized.c_str(), AASSET_MODE_UNKNOWN);
    if (asset == nullptr) {
        return false;
    }
    AAsset_close(asset);
    return true;
}

bool ReadTextAsset(const std::string& assetPath, std::string* outText) {
    if (outText == nullptr) {
        return false;
    }

    AAssetManager* assetManager = GetAssetManager();
    if (assetManager == nullptr) {
        return false;
    }

    const std::string normalized = NormalizeAssetPath(assetPath);
    AAsset* asset = AAssetManager_open(assetManager, normalized.c_str(), AASSET_MODE_BUFFER);
    if (asset == nullptr) {
        return false;
    }

    const auto length = static_cast<size_t>(AAsset_getLength(asset));
    outText->resize(length);
    const int readBytes = AAsset_read(asset, outText->data(), length);
    AAsset_close(asset);
    if (readBytes < 0 || static_cast<size_t>(readBytes) != length) {
        outText->clear();
        return false;
    }
    return true;
}

bool ReadBinaryAsset(const std::string& assetPath, std::vector<std::uint8_t>* outData) {
    if (outData == nullptr) {
        return false;
    }

    AAssetManager* assetManager = GetAssetManager();
    if (assetManager == nullptr) {
        return false;
    }

    const std::string normalized = NormalizeAssetPath(assetPath);
    AAsset* asset = AAssetManager_open(assetManager, normalized.c_str(), AASSET_MODE_BUFFER);
    if (asset == nullptr) {
        return false;
    }

    const auto length = static_cast<size_t>(AAsset_getLength(asset));
    outData->resize(length);
    const int readBytes = AAsset_read(asset, outData->data(), length);
    AAsset_close(asset);
    if (readBytes < 0 || static_cast<size_t>(readBytes) != length) {
        outData->clear();
        return false;
    }
    return true;
}

std::string ResolveBuiltinAssetPath(const std::string& logicalPath) {
    const std::string normalized = NormalizeAssetPath(logicalPath);
    if (normalized.rfind("@mmd_builtin/", 0) == 0) {
        return normalized.substr(std::string("@mmd_builtin/").size());
    }

    const std::string basenameLower = BasenameLower(normalized);
    if (basenameLower.size() == 10 &&
        basenameLower.rfind("toon", 0) == 0 &&
        basenameLower.substr(6) == ".bmp" &&
        std::isdigit(static_cast<unsigned char>(basenameLower[4])) &&
        std::isdigit(static_cast<unsigned char>(basenameLower[5]))) {
        return "mmd_common_toon/" + basenameLower;
    }

    return normalized;
}

}  // namespace operit::androidbridge
