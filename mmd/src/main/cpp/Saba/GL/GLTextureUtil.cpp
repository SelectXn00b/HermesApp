#include "GLTextureUtil.h"

#include "android/AndroidAssetSupport.h"

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <unordered_map>
#include <vector>

#include <glm/glm.hpp>

#define STB_IMAGE_IMPLEMENTATION
#include <stb_image.h>

#define TINYDDSLOADER_IMPLEMENTATION
#include <tinyddsloader.h>

namespace saba {
namespace {

std::mutex gTextureAlphaMutex;
std::unordered_map<GLuint, bool> gTextureHasAlpha;

std::string ToLowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return value;
}

std::string GetExtensionLower(const std::string& path) {
    return ToLowerAscii(std::filesystem::path(path).extension().string());
}

bool ReadBinaryFile(const std::string& path, std::vector<std::uint8_t>* outData) {
    if (outData == nullptr) {
        return false;
    }

    std::ifstream stream(path, std::ios::binary);
    if (!stream.is_open()) {
        return false;
    }

    stream.seekg(0, std::ios::end);
    const std::streamsize size = stream.tellg();
    if (size < 0) {
        return false;
    }
    stream.seekg(0, std::ios::beg);

    outData->resize(static_cast<size_t>(size));
    if (size > 0) {
        stream.read(reinterpret_cast<char*>(outData->data()), size);
    }
    return stream.good() || stream.eof();
}

bool LoadBinaryResource(const std::string& path, std::vector<std::uint8_t>* outData) {
    if (operit::androidbridge::ReadBinaryAsset(path, outData)) {
        return true;
    }
    return ReadBinaryFile(path, outData);
}

void SetTextureHasAlpha(GLuint tex, bool hasAlpha) {
    std::lock_guard<std::mutex> lock(gTextureAlphaMutex);
    gTextureHasAlpha[tex] = hasAlpha;
}

bool GetTextureHasAlpha(GLuint tex) {
    std::lock_guard<std::mutex> lock(gTextureAlphaMutex);
    const auto found = gTextureHasAlpha.find(tex);
    if (found == gTextureHasAlpha.end()) {
        return false;
    }
    return found->second;
}

bool HasAnyTransparentPixel(const std::vector<std::uint8_t>& rgbaPixels) {
    for (size_t index = 3; index < rgbaPixels.size(); index += 4) {
        if (rgbaPixels[index] != 255) {
            return true;
        }
    }
    return false;
}

glm::u8vec4 DecodeRgb565(uint16_t value) {
    const uint8_t r = static_cast<uint8_t>(((value >> 11) & 0x1F) * 255 / 31);
    const uint8_t g = static_cast<uint8_t>(((value >> 5) & 0x3F) * 255 / 63);
    const uint8_t b = static_cast<uint8_t>((value & 0x1F) * 255 / 31);
    return glm::u8vec4(r, g, b, 255);
}

void WriteRgbaPixel(
    std::vector<std::uint8_t>* pixels,
    uint32_t width,
    uint32_t height,
    uint32_t x,
    uint32_t y,
    const glm::u8vec4& color
) {
    if (pixels == nullptr || x >= width || y >= height) {
        return;
    }

    const size_t base = (static_cast<size_t>(y) * width + x) * 4;
    (*pixels)[base] = color.r;
    (*pixels)[base + 1] = color.g;
    (*pixels)[base + 2] = color.b;
    (*pixels)[base + 3] = color.a;
}

glm::u8vec4 InterpolateColor(
    const glm::u8vec4& lhs,
    const glm::u8vec4& rhs,
    int lhsWeight,
    int rhsWeight,
    int divisor,
    uint8_t alpha = 255
) {
    return glm::u8vec4(
        static_cast<uint8_t>((lhs.r * lhsWeight + rhs.r * rhsWeight) / divisor),
        static_cast<uint8_t>((lhs.g * lhsWeight + rhs.g * rhsWeight) / divisor),
        static_cast<uint8_t>((lhs.b * lhsWeight + rhs.b * rhsWeight) / divisor),
        alpha
    );
}

void DecompressBc1Block(
    const tinyddsloader::DDSFile::BC1Block& block,
    uint32_t blockX,
    uint32_t blockY,
    uint32_t width,
    uint32_t height,
    std::vector<std::uint8_t>* pixels
) {
    glm::u8vec4 colors[4];
    colors[0] = DecodeRgb565(block.m_color0);
    colors[1] = DecodeRgb565(block.m_color1);
    if (block.m_color0 > block.m_color1) {
        colors[2] = InterpolateColor(colors[0], colors[1], 2, 1, 3);
        colors[3] = InterpolateColor(colors[0], colors[1], 1, 2, 3);
    } else {
        colors[2] = InterpolateColor(colors[0], colors[1], 1, 1, 2);
        colors[3] = glm::u8vec4(0, 0, 0, 0);
    }

    const uint8_t rows[4] = { block.m_row0, block.m_row1, block.m_row2, block.m_row3 };
    for (uint32_t row = 0; row < 4; ++row) {
        for (uint32_t column = 0; column < 4; ++column) {
            const uint8_t colorIndex = (rows[row] >> (column * 2)) & 0x03;
            WriteRgbaPixel(pixels, width, height, blockX + column, blockY + row, colors[colorIndex]);
        }
    }
}

void DecompressBc2Block(
    const tinyddsloader::DDSFile::BC2Block& block,
    uint32_t blockX,
    uint32_t blockY,
    uint32_t width,
    uint32_t height,
    std::vector<std::uint8_t>* pixels
) {
    tinyddsloader::DDSFile::BC1Block colorBlock { block.m_color0, block.m_color1, block.m_row0, block.m_row1, block.m_row2, block.m_row3 };
    DecompressBc1Block(colorBlock, blockX, blockY, width, height, pixels);

    const uint16_t alphaRows[4] = { block.m_alphaRow0, block.m_alphaRow1, block.m_alphaRow2, block.m_alphaRow3 };
    for (uint32_t row = 0; row < 4; ++row) {
        for (uint32_t column = 0; column < 4; ++column) {
            const uint8_t alpha4 = static_cast<uint8_t>((alphaRows[row] >> (column * 4)) & 0x0F);
            const uint8_t alpha = static_cast<uint8_t>(alpha4 * 17);
            if (blockX + column < width && blockY + row < height) {
                const size_t base = (static_cast<size_t>(blockY + row) * width + (blockX + column)) * 4;
                (*pixels)[base + 3] = alpha;
            }
        }
    }
}

void DecompressBc3Block(
    const tinyddsloader::DDSFile::BC3Block& block,
    uint32_t blockX,
    uint32_t blockY,
    uint32_t width,
    uint32_t height,
    std::vector<std::uint8_t>* pixels
) {
    tinyddsloader::DDSFile::BC1Block colorBlock { block.m_color0, block.m_color1, block.m_row0, block.m_row1, block.m_row2, block.m_row3 };
    DecompressBc1Block(colorBlock, blockX, blockY, width, height, pixels);

    uint8_t alphaPalette[8] = {};
    alphaPalette[0] = block.m_alpha0;
    alphaPalette[1] = block.m_alpha1;
    if (block.m_alpha0 > block.m_alpha1) {
        alphaPalette[2] = static_cast<uint8_t>((6 * block.m_alpha0 + 1 * block.m_alpha1) / 7);
        alphaPalette[3] = static_cast<uint8_t>((5 * block.m_alpha0 + 2 * block.m_alpha1) / 7);
        alphaPalette[4] = static_cast<uint8_t>((4 * block.m_alpha0 + 3 * block.m_alpha1) / 7);
        alphaPalette[5] = static_cast<uint8_t>((3 * block.m_alpha0 + 4 * block.m_alpha1) / 7);
        alphaPalette[6] = static_cast<uint8_t>((2 * block.m_alpha0 + 5 * block.m_alpha1) / 7);
        alphaPalette[7] = static_cast<uint8_t>((1 * block.m_alpha0 + 6 * block.m_alpha1) / 7);
    } else {
        alphaPalette[2] = static_cast<uint8_t>((4 * block.m_alpha0 + 1 * block.m_alpha1) / 5);
        alphaPalette[3] = static_cast<uint8_t>((3 * block.m_alpha0 + 2 * block.m_alpha1) / 5);
        alphaPalette[4] = static_cast<uint8_t>((2 * block.m_alpha0 + 3 * block.m_alpha1) / 5);
        alphaPalette[5] = static_cast<uint8_t>((1 * block.m_alpha0 + 4 * block.m_alpha1) / 5);
        alphaPalette[6] = 0;
        alphaPalette[7] = 255;
    }

    uint64_t alphaBits =
        static_cast<uint64_t>(block.m_alphaR0) |
        (static_cast<uint64_t>(block.m_alphaR1) << 8) |
        (static_cast<uint64_t>(block.m_alphaR2) << 16) |
        (static_cast<uint64_t>(block.m_alphaR3) << 24) |
        (static_cast<uint64_t>(block.m_alphaR4) << 32) |
        (static_cast<uint64_t>(block.m_alphaR5) << 40);

    for (uint32_t row = 0; row < 4; ++row) {
        for (uint32_t column = 0; column < 4; ++column) {
            const uint8_t alphaIndex = static_cast<uint8_t>(alphaBits & 0x07);
            alphaBits >>= 3;
            if (blockX + column < width && blockY + row < height) {
                const size_t base = (static_cast<size_t>(blockY + row) * width + (blockX + column)) * 4;
                (*pixels)[base + 3] = alphaPalette[alphaIndex];
            }
        }
    }
}

bool DecodeDdsToRgba(
    std::vector<std::uint8_t> bytes,
    int* outWidth,
    int* outHeight,
    std::vector<std::uint8_t>* outPixels
) {
    tinyddsloader::DDSFile ddsFile;
    if (ddsFile.Load(std::move(bytes)) != tinyddsloader::Result::Success) {
        return false;
    }

    const auto* imageData = ddsFile.GetImageData(0, 0);
    if (imageData == nullptr || imageData->m_mem == nullptr || imageData->m_width == 0 || imageData->m_height == 0) {
        return false;
    }

    const auto format = ddsFile.GetFormat();
    const uint32_t width = imageData->m_width;
    const uint32_t height = imageData->m_height;
    std::vector<std::uint8_t> rgbaPixels(static_cast<size_t>(width) * static_cast<size_t>(height) * 4, 0);

    if (format == tinyddsloader::DDSFile::DXGIFormat::R8G8B8A8_UNorm) {
        const auto* source = static_cast<const uint8_t*>(imageData->m_mem);
        for (uint32_t row = 0; row < height; ++row) {
            const auto* rowSource = source + static_cast<size_t>(row) * imageData->m_memPitch;
            std::copy(rowSource, rowSource + static_cast<size_t>(width) * 4, rgbaPixels.begin() + static_cast<size_t>(row) * width * 4);
        }
    } else if (format == tinyddsloader::DDSFile::DXGIFormat::B8G8R8A8_UNorm ||
               format == tinyddsloader::DDSFile::DXGIFormat::B8G8R8X8_UNorm) {
        const auto* source = static_cast<const uint8_t*>(imageData->m_mem);
        for (uint32_t row = 0; row < height; ++row) {
            const auto* rowSource = source + static_cast<size_t>(row) * imageData->m_memPitch;
            for (uint32_t column = 0; column < width; ++column) {
                const size_t sourceBase = static_cast<size_t>(column) * 4;
                const size_t destBase = (static_cast<size_t>(row) * width + column) * 4;
                rgbaPixels[destBase] = rowSource[sourceBase + 2];
                rgbaPixels[destBase + 1] = rowSource[sourceBase + 1];
                rgbaPixels[destBase + 2] = rowSource[sourceBase];
                rgbaPixels[destBase + 3] =
                    format == tinyddsloader::DDSFile::DXGIFormat::B8G8R8X8_UNorm ? 255 : rowSource[sourceBase + 3];
            }
        }
    } else if (format == tinyddsloader::DDSFile::DXGIFormat::BC1_UNorm ||
               format == tinyddsloader::DDSFile::DXGIFormat::BC2_UNorm ||
               format == tinyddsloader::DDSFile::DXGIFormat::BC3_UNorm) {
        const uint32_t blockWidth = (width + 3) / 4;
        const uint32_t blockHeight = (height + 3) / 4;
        const auto* blockBytes = static_cast<const std::uint8_t*>(imageData->m_mem);
        const size_t blockSize =
            format == tinyddsloader::DDSFile::DXGIFormat::BC1_UNorm ? sizeof(tinyddsloader::DDSFile::BC1Block) :
            format == tinyddsloader::DDSFile::DXGIFormat::BC2_UNorm ? sizeof(tinyddsloader::DDSFile::BC2Block) :
            sizeof(tinyddsloader::DDSFile::BC3Block);
        for (uint32_t blockY = 0; blockY < blockHeight; ++blockY) {
            for (uint32_t blockX = 0; blockX < blockWidth; ++blockX) {
                const size_t offset = (static_cast<size_t>(blockY) * blockWidth + blockX) * blockSize;
                if (format == tinyddsloader::DDSFile::DXGIFormat::BC1_UNorm) {
                    DecompressBc1Block(
                        *reinterpret_cast<const tinyddsloader::DDSFile::BC1Block*>(blockBytes + offset),
                        blockX * 4,
                        blockY * 4,
                        width,
                        height,
                        &rgbaPixels
                    );
                } else if (format == tinyddsloader::DDSFile::DXGIFormat::BC2_UNorm) {
                    DecompressBc2Block(
                        *reinterpret_cast<const tinyddsloader::DDSFile::BC2Block*>(blockBytes + offset),
                        blockX * 4,
                        blockY * 4,
                        width,
                        height,
                        &rgbaPixels
                    );
                } else {
                    DecompressBc3Block(
                        *reinterpret_cast<const tinyddsloader::DDSFile::BC3Block*>(blockBytes + offset),
                        blockX * 4,
                        blockY * 4,
                        width,
                        height,
                        &rgbaPixels
                    );
                }
            }
        }
    } else {
        return false;
    }

    if (outWidth != nullptr) {
        *outWidth = static_cast<int>(width);
    }
    if (outHeight != nullptr) {
        *outHeight = static_cast<int>(height);
    }
    if (outPixels != nullptr) {
        *outPixels = std::move(rgbaPixels);
    }
    return true;
}

bool UploadTexture2D(
    GLuint tex,
    int width,
    int height,
    const std::uint8_t* rgbaPixels,
    bool genMipMap
) {
    if (tex == 0 || width <= 0 || height <= 0 || rgbaPixels == nullptr) {
        return false;
    }

    glBindTexture(GL_TEXTURE_2D, tex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgbaPixels);
    if (genMipMap) {
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    } else {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    }
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);
    return true;
}

bool LoadTextureFromStbBytes(
    GLuint tex,
    const std::vector<std::uint8_t>& bytes,
    bool genMipMap,
    bool rgba
) {
    int x = 0;
    int y = 0;
    int comp = 0;
    int reqComp = rgba ? STBI_rgb_alpha : 0;
    stbi_set_flip_vertically_on_load(true);
    std::uint8_t* pixels = stbi_load_from_memory(bytes.data(), static_cast<int>(bytes.size()), &x, &y, &comp, reqComp);
    if (pixels == nullptr) {
        return false;
    }
    const bool hasAlpha = comp == STBI_rgb_alpha || comp == STBI_grey_alpha;

    if (reqComp == 0) {
        reqComp = comp == 4 ? STBI_rgb_alpha : STBI_rgb;
    }

    const bool success =
        reqComp == STBI_rgb_alpha
            ? UploadTexture2D(tex, x, y, pixels, genMipMap)
            : [&]() {
                  std::vector<std::uint8_t> rgbaPixels(static_cast<size_t>(x) * static_cast<size_t>(y) * 4, 255);
                  for (int index = 0; index < x * y; ++index) {
                      rgbaPixels[index * 4] = pixels[index * 3];
                      rgbaPixels[index * 4 + 1] = pixels[index * 3 + 1];
                      rgbaPixels[index * 4 + 2] = pixels[index * 3 + 2];
                  }
                  return UploadTexture2D(tex, x, y, rgbaPixels.data(), genMipMap);
              }();

    stbi_image_free(pixels);
    if (success) {
        SetTextureHasAlpha(tex, hasAlpha);
    }
    return success;
}

bool LoadTextureFromDdsBytes(GLuint tex, std::vector<std::uint8_t> bytes, bool genMipMap) {
    int width = 0;
    int height = 0;
    std::vector<std::uint8_t> rgbaPixels;
    if (!DecodeDdsToRgba(std::move(bytes), &width, &height, &rgbaPixels)) {
        return false;
    }
    const bool success = UploadTexture2D(tex, width, height, rgbaPixels.data(), genMipMap);
    if (success) {
        SetTextureHasAlpha(tex, HasAnyTransparentPixel(rgbaPixels));
    }
    return success;
}

bool LoadTextureBytes(
    const GLTextureObject& tex,
    const std::vector<std::uint8_t>& bytes,
    const std::string& sourcePath,
    bool genMipMap,
    bool rgba
) {
    const std::string extension = GetExtensionLower(sourcePath);
    if (extension == ".dds") {
        return LoadTextureFromDdsBytes(tex, bytes, genMipMap);
    }
    return LoadTextureFromStbBytes(tex, bytes, genMipMap, rgba);
}

bool LoadTextureFromPathImpl(const GLTextureObject& tex, const std::string& filename, bool genMipMap, bool rgba) {
    std::vector<std::uint8_t> bytes;
    if (LoadBinaryResource(filename, &bytes)) {
        return LoadTextureBytes(tex, bytes, filename, genMipMap, rgba);
    }

    const std::string builtinAssetPath = operit::androidbridge::ResolveBuiltinAssetPath(filename);
    if (builtinAssetPath != filename && operit::androidbridge::ReadBinaryAsset(builtinAssetPath, &bytes)) {
        return LoadTextureBytes(tex, bytes, builtinAssetPath, genMipMap, rgba);
    }

    return false;
}

}  // namespace

GLTextureObject CreateTextureFromFile(const char* filename, bool genMipMap, bool rgba) {
    GLTextureObject tex;
    if (!tex.Create()) {
        return GLTextureObject();
    }
    if (!LoadTextureFromFile(tex, filename, genMipMap, rgba)) {
        tex.Destroy();
        return GLTextureObject();
    }
    return tex;
}

GLTextureObject CreateTextureFromFile(const std::string& filename, bool genMipMap, bool rgba) {
    return CreateTextureFromFile(filename.c_str(), genMipMap, rgba);
}

bool LoadTextureFromFile(const GLTextureObject& tex, const char* filename, bool genMipMap, bool rgba) {
    if (filename == nullptr || tex == 0) {
        return false;
    }
    return LoadTextureFromPathImpl(tex, filename, genMipMap, rgba);
}

bool LoadTextureFromFile(const GLTextureObject& tex, const std::string& filename, bool genMipMap, bool rgba) {
    return LoadTextureFromFile(tex, filename.c_str(), genMipMap, rgba);
}

bool IsAlphaTexture(GLuint tex) {
    if (tex == 0) {
        return false;
    }
    return GetTextureHasAlpha(tex);
}

}  // namespace saba
