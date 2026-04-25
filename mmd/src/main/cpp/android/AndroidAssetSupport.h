#pragma once

#include <android/asset_manager.h>

#include <cstdint>
#include <string>
#include <vector>

namespace operit::androidbridge {

void SetAssetManager(AAssetManager* assetManager);
AAssetManager* GetAssetManager();

bool AssetExists(const std::string& assetPath);
bool ReadTextAsset(const std::string& assetPath, std::string* outText);
bool ReadBinaryAsset(const std::string& assetPath, std::vector<std::uint8_t>* outData);

std::string NormalizeAssetPath(std::string assetPath);
std::string ResolveBuiltinAssetPath(const std::string& logicalPath);

}
