#include <jni.h>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <limits>
#include <mutex>
#include <sstream>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include <sys/stat.h>

#include "ufbx.h"

namespace {

constexpr size_t kVertexStrideFloats = 8;

std::mutex g_error_mutex;
std::string g_last_error;

void SetLastError(const std::string &message)
{
    std::lock_guard<std::mutex> lock(g_error_mutex);
    g_last_error = message;
}

std::string GetLastError()
{
    std::lock_guard<std::mutex> lock(g_error_mutex);
    return g_last_error;
}

void ClearLastError()
{
    SetLastError("");
}

std::string StringFromUfbx(ufbx_string value)
{
    if (!value.data || value.length == 0) {
        return "";
    }
    return std::string(value.data, value.length);
}

std::string TrimCopy(const std::string &value)
{
    size_t begin = 0;
    while (begin < value.size() && std::isspace(static_cast<unsigned char>(value[begin])) != 0) {
        begin += 1;
    }

    size_t end = value.size();
    while (end > begin && std::isspace(static_cast<unsigned char>(value[end - 1])) != 0) {
        end -= 1;
    }

    return value.substr(begin, end - begin);
}

std::string NormalizePath(std::string value)
{
    std::replace(value.begin(), value.end(), '\\', '/');
    return value;
}

bool IsAbsolutePath(const std::string &path)
{
    if (path.empty()) {
        return false;
    }
    if (path.size() >= 2 && std::isalpha(static_cast<unsigned char>(path[0])) != 0 && path[1] == ':') {
        return true;
    }
    return path[0] == '/';
}

std::string DirectoryName(const std::string &path)
{
    const std::string normalized = NormalizePath(path);
    const size_t slash = normalized.find_last_of('/');
    if (slash == std::string::npos) {
        return "";
    }
    return normalized.substr(0, slash);
}

std::string BaseNameNoExtension(const std::string &path)
{
    std::string normalized = NormalizePath(path);
    const size_t slash = normalized.find_last_of('/');
    if (slash != std::string::npos) {
        normalized = normalized.substr(slash + 1);
    }

    const size_t dot = normalized.find_last_of('.');
    if (dot == std::string::npos) {
        return normalized;
    }
    return normalized.substr(0, dot);
}

std::string JoinPath(const std::string &left, const std::string &right)
{
    if (right.empty()) {
        return NormalizePath(left);
    }
    if (left.empty() || IsAbsolutePath(right)) {
        return NormalizePath(right);
    }
    if (left.back() == '/' || left.back() == '\\') {
        return NormalizePath(left + right);
    }
    return NormalizePath(left + "/" + right);
}

bool FileExists(const std::string &path)
{
    if (path.empty()) {
        return false;
    }

    struct stat info;
    return stat(path.c_str(), &info) == 0 && (info.st_mode & S_IFREG) != 0;
}

float Clamp01(double value)
{
    return static_cast<float>(std::clamp(value, 0.0, 1.0));
}

void AppendJsonEscapedString(std::string &dst, const std::string &value)
{
    dst.push_back('"');
    for (const unsigned char ch : value) {
        switch (ch) {
            case '\\': dst += "\\\\"; break;
            case '"': dst += "\\\""; break;
            case '\b': dst += "\\b"; break;
            case '\f': dst += "\\f"; break;
            case '\n': dst += "\\n"; break;
            case '\r': dst += "\\r"; break;
            case '\t': dst += "\\t"; break;
            default:
                if (ch < 0x20) {
                    char buffer[7];
                    std::snprintf(buffer, sizeof(buffer), "\\u%04x", static_cast<unsigned int>(ch));
                    dst += buffer;
                } else {
                    dst.push_back(static_cast<char>(ch));
                }
                break;
        }
    }
    dst.push_back('"');
}

std::string FormatUfbxError(const ufbx_error &error)
{
    char buffer[512];
    ufbx_format_error(buffer, sizeof(buffer), &error);
    return std::string(buffer);
}

struct ExternalFileInfo {
    std::string display_path;
    std::string resolved_path;
};

ExternalFileInfo ResolveExternalFileInfo(
    const std::string &model_path,
    const std::string &filename,
    const std::string &absolute_filename,
    const std::string &relative_filename)
{
    const std::string model_directory = DirectoryName(model_path);

    const std::string relative = NormalizePath(TrimCopy(relative_filename));
    const std::string resolved = NormalizePath(TrimCopy(filename));
    const std::string absolute = NormalizePath(TrimCopy(absolute_filename));

    if (!relative.empty() && !IsAbsolutePath(relative)) {
        return ExternalFileInfo{
            relative,
            JoinPath(model_directory, relative),
        };
    }

    if (!resolved.empty()) {
        return ExternalFileInfo{
            resolved,
            IsAbsolutePath(resolved) ? resolved : JoinPath(model_directory, resolved),
        };
    }

    if (!absolute.empty()) {
        return ExternalFileInfo{
            absolute,
            absolute,
        };
    }

    if (!relative.empty()) {
        return ExternalFileInfo{
            relative,
            relative,
        };
    }

    return ExternalFileInfo{};
}

bool TryCopyEmbeddedBytes(const ufbx_blob &blob, std::vector<uint8_t> *dst)
{
    if (!dst || !blob.data || blob.size == 0) {
        return false;
    }

    const auto *begin = static_cast<const uint8_t *>(blob.data);
    dst->assign(begin, begin + blob.size);
    return true;
}

std::string NormalizeAnimationName(const ufbx_scene *scene, size_t index)
{
    if (!scene || index >= scene->anim_stacks.count) {
        return "";
    }

    std::string raw_name = TrimCopy(StringFromUfbx(scene->anim_stacks.data[index]->name));
    if (!raw_name.empty()) {
        return raw_name;
    }
    return "Animation " + std::to_string(index + 1);
}

struct InspectSceneData {
    std::string model_name;
    std::vector<std::string> animation_names;
    std::vector<int64_t> animation_durations_millis;
    std::vector<std::string> required_external_files;
    std::vector<std::string> missing_external_files;
};

InspectSceneData BuildInspectSceneData(const std::string &model_path, const ufbx_scene *scene)
{
    InspectSceneData data;

    for (size_t index = 0; index < scene->anim_stacks.count; ++index) {
        const ufbx_anim_stack *anim_stack = scene->anim_stacks.data[index];
        std::string animation_name = NormalizeAnimationName(scene, index);

        size_t suffix = 2;
        const std::string base_name = animation_name;
        while (std::find(data.animation_names.begin(), data.animation_names.end(), animation_name) != data.animation_names.end()) {
            animation_name = base_name + " (" + std::to_string(suffix) + ")";
            suffix += 1;
        }

        data.animation_names.push_back(animation_name);
        const double duration_seconds = std::max(anim_stack->time_end - anim_stack->time_begin, 0.0);
        data.animation_durations_millis.push_back(static_cast<int64_t>(std::llround(duration_seconds * 1000.0)));
    }

    std::unordered_set<std::string> required_seen;
    std::unordered_set<std::string> missing_seen;

    for (size_t index = 0; index < scene->texture_files.count; ++index) {
        const ufbx_texture_file *texture_file = &scene->texture_files.data[index];
        if (texture_file->content.size > 0) {
            continue;
        }

        const ExternalFileInfo external = ResolveExternalFileInfo(
            model_path,
            StringFromUfbx(texture_file->filename),
            StringFromUfbx(texture_file->absolute_filename),
            StringFromUfbx(texture_file->relative_filename));
        if (external.display_path.empty()) {
            continue;
        }

        const std::string required_key = NormalizePath(external.display_path);
        if (required_seen.insert(required_key).second) {
            data.required_external_files.push_back(external.display_path);
        }

        if (!FileExists(external.resolved_path)) {
            const std::string missing_key = NormalizePath(external.display_path);
            if (missing_seen.insert(missing_key).second) {
                data.missing_external_files.push_back(external.display_path);
            }
        }
    }

    for (size_t index = 0; index < scene->nodes.count; ++index) {
        const ufbx_node *node = scene->nodes.data[index];
        if (node && !node->is_root) {
            const std::string node_name = TrimCopy(StringFromUfbx(node->name));
            if (!node_name.empty()) {
                data.model_name = node_name;
                break;
            }
        }
    }

    if (data.model_name.empty()) {
        data.model_name = BaseNameNoExtension(model_path);
    }

    return data;
}

bool LoadScene(const std::string &model_path, ufbx_scene **out_scene)
{
    if (!out_scene) {
        SetLastError("FBX internal error: output scene pointer is null.");
        return false;
    }

    ufbx_load_opts opts = {};
    opts.target_axes = ufbx_axes_right_handed_y_up;
    opts.target_unit_meters = 1.0f;
    opts.generate_missing_normals = true;
    opts.evaluate_skinning = true;
    opts.evaluate_caches = true;
    opts.load_external_files = true;
    opts.ignore_missing_external_files = true;

    ufbx_error error;
    ufbx_scene *scene = ufbx_load_file(model_path.c_str(), &opts, &error);
    if (!scene) {
        SetLastError(FormatUfbxError(error));
        return false;
    }

    *out_scene = scene;
    return true;
}

struct TextureSlotData {
    std::string label;
    std::string resolved_path;
    std::vector<uint8_t> embedded_bytes;
};

struct MaterialData {
    int texture_index = -1;
    float base_color[4] = { 1.0f, 1.0f, 1.0f, 1.0f };
    bool alpha_blend = false;
};

struct SegmentData {
    int vertex_offset = 0;
    int vertex_count = 0;
    int material_index = -1;
};

struct VertexReference {
    uint32_t node_element_id = 0;
    uint32_t mesh_vertex_index = 0;
    float u = 0.0f;
    float v = 0.0f;
};

struct PreviewSession {
    std::string model_path;
    ufbx_scene *scene = nullptr;
    std::vector<std::string> animation_names;
    std::vector<int64_t> animation_durations_millis;
    std::unordered_map<std::string, size_t> animation_name_to_index;
    std::vector<TextureSlotData> textures;
    std::vector<MaterialData> materials;
    std::vector<SegmentData> segments;
    std::vector<VertexReference> vertex_references;
    std::vector<float> current_vertices;
    float center[3] = { 0.0f, 0.0f, 0.0f };
    float radius = 1.0f;

    std::unordered_map<const ufbx_material *, int> material_cache;
    std::unordered_map<std::string, int> texture_cache;

    ~PreviewSession()
    {
        if (scene) {
            ufbx_free_scene(scene);
            scene = nullptr;
        }
    }
};

bool BuildSessionGeometry(PreviewSession *session);
bool UpdatePreviewFrame(PreviewSession *session, const std::string *animation_name, double time_seconds);

const ufbx_texture *SelectFileTexture(const ufbx_texture *texture)
{
    if (!texture) {
        return nullptr;
    }
    if (texture->type == UFBX_TEXTURE_FILE) {
        return texture;
    }
    if (texture->file_textures.count > 0) {
        return texture->file_textures.data[0];
    }
    return texture;
}

int EnsureTextureSlot(PreviewSession *session, const ufbx_texture *texture)
{
    if (!session || !texture) {
        return -1;
    }

    const ufbx_texture *file_texture = SelectFileTexture(texture);
    if (!file_texture) {
        return -1;
    }

    std::vector<uint8_t> embedded_bytes;
    if (!TryCopyEmbeddedBytes(file_texture->content, &embedded_bytes) && file_texture->video) {
        TryCopyEmbeddedBytes(file_texture->video->content, &embedded_bytes);
    }

    const ExternalFileInfo external = ResolveExternalFileInfo(
        session->model_path,
        StringFromUfbx(file_texture->filename),
        StringFromUfbx(file_texture->absolute_filename),
        StringFromUfbx(file_texture->relative_filename));

    std::string key;
    if (!embedded_bytes.empty()) {
        key = "embedded:" + std::to_string(file_texture->element_id);
    } else if (!external.resolved_path.empty()) {
        key = "path:" + NormalizePath(external.resolved_path);
    } else {
        key = "texture:" + std::to_string(file_texture->element_id);
    }

    const auto existing = session->texture_cache.find(key);
    if (existing != session->texture_cache.end()) {
        return existing->second;
    }

    TextureSlotData slot;
    slot.label = !external.display_path.empty() ? external.display_path : TrimCopy(StringFromUfbx(file_texture->name));
    if (slot.label.empty()) {
        slot.label = "Texture " + std::to_string(session->textures.size() + 1);
    }
    slot.resolved_path = external.resolved_path;
    slot.embedded_bytes = std::move(embedded_bytes);

    const int index = static_cast<int>(session->textures.size());
    session->textures.push_back(std::move(slot));
    session->texture_cache.emplace(key, index);
    return index;
}

int EnsureMaterial(PreviewSession *session, const ufbx_material *material)
{
    if (!session) {
        return -1;
    }

    if (!material) {
        const auto existing = session->material_cache.find(nullptr);
        if (existing != session->material_cache.end()) {
            return existing->second;
        }

        const int index = static_cast<int>(session->materials.size());
        session->materials.push_back(MaterialData{});
        session->material_cache.emplace(nullptr, index);
        return index;
    }

    const auto existing = session->material_cache.find(material);
    if (existing != session->material_cache.end()) {
        return existing->second;
    }

    MaterialData material_data;

    const ufbx_material_map &pbr_base_color = material->pbr.base_color;
    const ufbx_material_map &pbr_base_factor = material->pbr.base_factor;
    const ufbx_material_map &pbr_opacity = material->pbr.opacity;
    const ufbx_material_map &fbx_diffuse_color = material->fbx.diffuse_color;
    const ufbx_material_map &fbx_transparency_factor = material->fbx.transparency_factor;
    const ufbx_material_map &fbx_transparency_color = material->fbx.transparency_color;

    if (pbr_base_color.has_value && pbr_base_color.value_components >= 3) {
        const ufbx_vec4 color = pbr_base_color.value_vec4;
        material_data.base_color[0] = static_cast<float>(color.x);
        material_data.base_color[1] = static_cast<float>(color.y);
        material_data.base_color[2] = static_cast<float>(color.z);
        if (pbr_base_color.value_components >= 4) {
            material_data.base_color[3] = static_cast<float>(color.w);
        }
    } else if (fbx_diffuse_color.has_value && fbx_diffuse_color.value_components >= 3) {
        const ufbx_vec4 color = fbx_diffuse_color.value_vec4;
        material_data.base_color[0] = static_cast<float>(color.x);
        material_data.base_color[1] = static_cast<float>(color.y);
        material_data.base_color[2] = static_cast<float>(color.z);
        if (fbx_diffuse_color.value_components >= 4) {
            material_data.base_color[3] = static_cast<float>(color.w);
        }
    }

    if (pbr_base_factor.has_value) {
        const float factor = static_cast<float>(pbr_base_factor.value_real);
        material_data.base_color[0] *= factor;
        material_data.base_color[1] *= factor;
        material_data.base_color[2] *= factor;
    }

    float opacity = material_data.base_color[3];
    if (pbr_opacity.has_value) {
        opacity *= Clamp01(pbr_opacity.value_real);
    } else {
        if (fbx_transparency_factor.has_value) {
            opacity *= 1.0f - Clamp01(fbx_transparency_factor.value_real);
        }
        if (fbx_transparency_color.has_value && fbx_transparency_color.value_components >= 3) {
            const ufbx_vec3 transparent = fbx_transparency_color.value_vec3;
            opacity *= 1.0f - Clamp01(std::max({ transparent.x, transparent.y, transparent.z }));
        }
    }
    material_data.base_color[3] = Clamp01(opacity);

    const ufbx_texture *texture = nullptr;
    if (pbr_base_color.texture && pbr_base_color.texture_enabled) {
        texture = pbr_base_color.texture;
    } else if (fbx_diffuse_color.texture && fbx_diffuse_color.texture_enabled) {
        texture = fbx_diffuse_color.texture;
    }
    material_data.texture_index = EnsureTextureSlot(session, texture);

    material_data.alpha_blend =
        material_data.base_color[3] < 0.999f ||
        pbr_opacity.texture_enabled ||
        fbx_transparency_factor.texture_enabled ||
        fbx_transparency_color.texture_enabled ||
        material->features.opacity.enabled;

    const int index = static_cast<int>(session->materials.size());
    session->materials.push_back(material_data);
    session->material_cache.emplace(material, index);
    return index;
}

bool BuildSessionGeometry(PreviewSession *session)
{
    if (!session || !session->scene) {
        SetLastError("FBX internal error: preview session scene is null.");
        return false;
    }

    session->segments.clear();
    session->vertex_references.clear();

    for (size_t node_index = 0; node_index < session->scene->nodes.count; ++node_index) {
        const ufbx_node *node = session->scene->nodes.data[node_index];
        if (!node || !node->mesh) {
            continue;
        }

        const ufbx_mesh *mesh = node->mesh;
        if (mesh->num_faces == 0 || mesh->num_triangles == 0) {
            continue;
        }

        std::vector<uint32_t> triangulated_indices(mesh->max_face_triangles * 3);
        auto append_part = [&](const ufbx_mesh_part *part, int material_slot_index) {
            const int vertex_offset = static_cast<int>(session->vertex_references.size());

            for (size_t face_list_index = 0; face_list_index < part->face_indices.count; ++face_list_index) {
                const uint32_t face_index = part->face_indices.data[face_list_index];
                if (face_index >= mesh->faces.count) {
                    continue;
                }

                const ufbx_face face = mesh->faces.data[face_index];
                const size_t triangle_count = ufbx_triangulate_face(
                    triangulated_indices.data(),
                    triangulated_indices.size(),
                    mesh,
                    face);
                for (size_t triangle_index = 0; triangle_index < triangle_count * 3; ++triangle_index) {
                    const uint32_t mesh_vertex_index = triangulated_indices[triangle_index];
                    ufbx_vec2 uv = {};
                    if (mesh_vertex_index < mesh->vertex_uv.indices.count) {
                        uv = ufbx_get_vertex_vec2(&mesh->vertex_uv, mesh_vertex_index);
                    }
                    session->vertex_references.push_back(VertexReference{
                        node->element_id,
                        mesh_vertex_index,
                        static_cast<float>(uv.x),
                        static_cast<float>(uv.y),
                    });
                }
            }

            const int vertex_count = static_cast<int>(session->vertex_references.size()) - vertex_offset;
            if (vertex_count > 0) {
                session->segments.push_back(SegmentData{
                    vertex_offset,
                    vertex_count,
                    material_slot_index,
                });
            }
        };

        if (mesh->material_parts.count > 0) {
            std::vector<uint32_t> ordered_part_indices;
            ordered_part_indices.reserve(mesh->material_parts.count);
            if (mesh->material_part_usage_order.count == mesh->material_parts.count) {
                for (size_t usage_index = 0; usage_index < mesh->material_part_usage_order.count; ++usage_index) {
                    ordered_part_indices.push_back(mesh->material_part_usage_order.data[usage_index]);
                }
            } else {
                for (size_t part_index = 0; part_index < mesh->material_parts.count; ++part_index) {
                    ordered_part_indices.push_back(static_cast<uint32_t>(part_index));
                }
            }

            for (const uint32_t part_index : ordered_part_indices) {
                if (part_index >= mesh->material_parts.count) {
                    continue;
                }
                const ufbx_material *material = nullptr;
                if (part_index < node->materials.count) {
                    material = node->materials.data[part_index];
                } else if (part_index < mesh->materials.count) {
                    material = mesh->materials.data[part_index];
                }
                const int material_index = EnsureMaterial(session, material);
                append_part(&mesh->material_parts.data[part_index], material_index);
            }
        } else {
            ufbx_mesh_part whole_mesh_part = {};
            whole_mesh_part.face_indices.data = mesh->face_material.data;
            whole_mesh_part.face_indices.count = 0;
            std::vector<uint32_t> synthetic_face_indices(mesh->faces.count);
            for (size_t face_index = 0; face_index < mesh->faces.count; ++face_index) {
                synthetic_face_indices[face_index] = static_cast<uint32_t>(face_index);
            }
            whole_mesh_part.face_indices.data = synthetic_face_indices.data();
            whole_mesh_part.face_indices.count = synthetic_face_indices.size();
            const int material_index = EnsureMaterial(session, node->materials.count > 0 ? node->materials.data[0] : nullptr);
            append_part(&whole_mesh_part, material_index);
        }
    }

    if (session->vertex_references.empty()) {
        SetLastError("FBX preview did not find any renderable mesh triangles.");
        return false;
    }

    session->current_vertices.resize(session->vertex_references.size() * kVertexStrideFloats);
    return true;
}

bool UpdatePreviewFrame(PreviewSession *session, const std::string *animation_name, double time_seconds)
{
    if (!session || !session->scene) {
        SetLastError("FBX internal error: preview session is not initialized.");
        return false;
    }

    const ufbx_scene *active_scene = session->scene;
    ufbx_scene *evaluated_scene = nullptr;
    if (animation_name && !animation_name->empty()) {
        const auto animation_it = session->animation_name_to_index.find(*animation_name);
        if (animation_it != session->animation_name_to_index.end() && animation_it->second < session->scene->anim_stacks.count) {
            ufbx_error error;
            ufbx_evaluate_opts opts = {};
            opts.evaluate_skinning = true;
            opts.evaluate_caches = true;
            opts.load_external_files = true;
            const ufbx_anim_stack *anim_stack = session->scene->anim_stacks.data[animation_it->second];
            evaluated_scene = ufbx_evaluate_scene(session->scene, anim_stack->anim, time_seconds, &opts, &error);
            if (!evaluated_scene) {
                SetLastError(FormatUfbxError(error));
                return false;
            }
            active_scene = evaluated_scene;
        }
    }

    std::unordered_map<uint32_t, const ufbx_node *> nodes_by_element_id;
    nodes_by_element_id.reserve(active_scene->nodes.count);
    for (size_t node_index = 0; node_index < active_scene->nodes.count; ++node_index) {
        const ufbx_node *node = active_scene->nodes.data[node_index];
        if (node) {
            nodes_by_element_id.emplace(node->element_id, node);
        }
    }

    float min_x = std::numeric_limits<float>::infinity();
    float min_y = std::numeric_limits<float>::infinity();
    float min_z = std::numeric_limits<float>::infinity();
    float max_x = -std::numeric_limits<float>::infinity();
    float max_y = -std::numeric_limits<float>::infinity();
    float max_z = -std::numeric_limits<float>::infinity();

    for (size_t index = 0; index < session->vertex_references.size(); ++index) {
        const VertexReference &reference = session->vertex_references[index];
        const auto node_it = nodes_by_element_id.find(reference.node_element_id);
        if (node_it == nodes_by_element_id.end() || !node_it->second || !node_it->second->mesh) {
            continue;
        }

        const ufbx_node *node = node_it->second;
        const ufbx_mesh *mesh = node->mesh;
        if (reference.mesh_vertex_index >= mesh->skinned_position.indices.count) {
            continue;
        }

        ufbx_vec3 position = ufbx_get_vertex_vec3(&mesh->skinned_position, reference.mesh_vertex_index);
        ufbx_vec3 normal = { 0.0, 1.0, 0.0 };
        if (reference.mesh_vertex_index < mesh->skinned_normal.indices.count) {
            normal = ufbx_get_vertex_vec3(&mesh->skinned_normal, reference.mesh_vertex_index);
        }

        if (mesh->skinned_is_local) {
            position = ufbx_transform_position(&node->geometry_to_world, position);
            const ufbx_matrix normal_matrix = ufbx_matrix_for_normals(&node->geometry_to_world);
            normal = ufbx_transform_direction(&normal_matrix, normal);
        }

        const float normal_length = std::sqrt(
            static_cast<float>(normal.x * normal.x + normal.y * normal.y + normal.z * normal.z));
        if (normal_length > 1e-6f) {
            normal.x /= normal_length;
            normal.y /= normal_length;
            normal.z /= normal_length;
        }

        const size_t base = index * kVertexStrideFloats;
        session->current_vertices[base + 0] = static_cast<float>(position.x);
        session->current_vertices[base + 1] = static_cast<float>(position.y);
        session->current_vertices[base + 2] = static_cast<float>(position.z);
        session->current_vertices[base + 3] = static_cast<float>(normal.x);
        session->current_vertices[base + 4] = static_cast<float>(normal.y);
        session->current_vertices[base + 5] = static_cast<float>(normal.z);
        session->current_vertices[base + 6] = reference.u;
        session->current_vertices[base + 7] = reference.v;

        min_x = std::min(min_x, session->current_vertices[base + 0]);
        min_y = std::min(min_y, session->current_vertices[base + 1]);
        min_z = std::min(min_z, session->current_vertices[base + 2]);
        max_x = std::max(max_x, session->current_vertices[base + 0]);
        max_y = std::max(max_y, session->current_vertices[base + 1]);
        max_z = std::max(max_z, session->current_vertices[base + 2]);
    }

    if (std::isfinite(min_x) && std::isfinite(max_x)) {
        session->center[0] = (min_x + max_x) * 0.5f;
        session->center[1] = (min_y + max_y) * 0.5f;
        session->center[2] = (min_z + max_z) * 0.5f;
        const float extent_x = max_x - min_x;
        const float extent_y = max_y - min_y;
        const float extent_z = max_z - min_z;
        session->radius = std::max({ extent_x, extent_y, extent_z }) * 0.5f;
        session->radius = std::max(session->radius, 0.1f);
    }

    if (evaluated_scene) {
        ufbx_free_scene(evaluated_scene);
    }
    return true;
}

std::string BuildInspectJson(const InspectSceneData &data)
{
    std::string json = "{";
    json += "\"modelName\":";
    AppendJsonEscapedString(json, data.model_name);
    json += ",\"animationNames\":[";
    for (size_t index = 0; index < data.animation_names.size(); ++index) {
        if (index > 0) json += ",";
        AppendJsonEscapedString(json, data.animation_names[index]);
    }
    json += "],\"animationDurationsMillis\":[";
    for (size_t index = 0; index < data.animation_durations_millis.size(); ++index) {
        if (index > 0) json += ",";
        json += std::to_string(data.animation_durations_millis[index]);
    }
    json += "],\"requiredExternalFiles\":[";
    for (size_t index = 0; index < data.required_external_files.size(); ++index) {
        if (index > 0) json += ",";
        AppendJsonEscapedString(json, data.required_external_files[index]);
    }
    json += "],\"missingExternalFiles\":[";
    for (size_t index = 0; index < data.missing_external_files.size(); ++index) {
        if (index > 0) json += ",";
        AppendJsonEscapedString(json, data.missing_external_files[index]);
    }
    json += "]}";
    return json;
}

std::string BuildPreviewInfoJson(const PreviewSession &session)
{
    std::string json = "{";
    json += "\"center\":[";
    json += std::to_string(session.center[0]) + "," + std::to_string(session.center[1]) + "," + std::to_string(session.center[2]);
    json += "],\"radius\":";
    json += std::to_string(session.radius);
    json += ",\"vertexCount\":";
    json += std::to_string(session.vertex_references.size());
    json += ",\"animationNames\":[";
    for (size_t index = 0; index < session.animation_names.size(); ++index) {
        if (index > 0) json += ",";
        AppendJsonEscapedString(json, session.animation_names[index]);
    }
    json += "],\"animationDurationsMillis\":[";
    for (size_t index = 0; index < session.animation_durations_millis.size(); ++index) {
        if (index > 0) json += ",";
        json += std::to_string(session.animation_durations_millis[index]);
    }
    json += "],\"textures\":[";
    for (size_t index = 0; index < session.textures.size(); ++index) {
        const TextureSlotData &texture = session.textures[index];
        if (index > 0) json += ",";
        json += "{";
        json += "\"label\":";
        AppendJsonEscapedString(json, texture.label);
        json += ",\"path\":";
        if (texture.resolved_path.empty()) {
            json += "null";
        } else {
            AppendJsonEscapedString(json, texture.resolved_path);
        }
        json += ",\"embedded\":";
        json += texture.embedded_bytes.empty() ? "false" : "true";
        json += "}";
    }
    json += "],\"materials\":[";
    for (size_t index = 0; index < session.materials.size(); ++index) {
        const MaterialData &material = session.materials[index];
        if (index > 0) json += ",";
        json += "{";
        json += "\"textureIndex\":" + std::to_string(material.texture_index);
        json += ",\"baseColor\":[";
        json += std::to_string(material.base_color[0]) + ",";
        json += std::to_string(material.base_color[1]) + ",";
        json += std::to_string(material.base_color[2]) + ",";
        json += std::to_string(material.base_color[3]);
        json += "],\"alphaBlend\":";
        json += material.alpha_blend ? "true" : "false";
        json += "}";
    }
    json += "],\"segments\":[";
    for (size_t index = 0; index < session.segments.size(); ++index) {
        const SegmentData &segment = session.segments[index];
        if (index > 0) json += ",";
        json += "{";
        json += "\"vertexOffset\":" + std::to_string(segment.vertex_offset);
        json += ",\"vertexCount\":" + std::to_string(segment.vertex_count);
        json += ",\"materialIndex\":" + std::to_string(segment.material_index);
        json += "}";
    }
    json += "]}";
    return json;
}

jstring NewJavaString(JNIEnv *env, const std::string &value)
{
    return env->NewStringUTF(value.c_str());
}

std::string JStringToStdString(JNIEnv *env, jstring value)
{
    if (!value) {
        return "";
    }
    const char *raw = env->GetStringUTFChars(value, nullptr);
    std::string result = raw ? raw : "";
    if (raw) {
        env->ReleaseStringUTFChars(value, raw);
    }
    return result;
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_fbx_FbxNative_nativeIsAvailable(JNIEnv *, jobject)
{
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_fbx_FbxNative_nativeGetUnavailableReason(JNIEnv *env, jobject)
{
    return NewJavaString(env, "");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_fbx_FbxNative_nativeGetLastError(JNIEnv *env, jobject)
{
    return NewJavaString(env, GetLastError());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_fbx_FbxNative_nativeInspectModel(JNIEnv *env, jobject, jstring path_model)
{
    ClearLastError();
    const std::string model_path = JStringToStdString(env, path_model);
    if (model_path.empty()) {
        SetLastError("FBX model path is empty.");
        return nullptr;
    }

    ufbx_scene *scene = nullptr;
    if (!LoadScene(model_path, &scene)) {
        return nullptr;
    }

    const InspectSceneData inspect_data = BuildInspectSceneData(model_path, scene);
    ufbx_free_scene(scene);
    return NewJavaString(env, BuildInspectJson(inspect_data));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_fbx_FbxNative_nativeCreatePreviewSession(JNIEnv *env, jobject, jstring path_model)
{
    (void)env;
    ClearLastError();
    const std::string model_path = JStringToStdString(env, path_model);
    if (model_path.empty()) {
        SetLastError("FBX model path is empty.");
        return 0L;
    }

    auto *session = new PreviewSession();
    session->model_path = model_path;
    if (!LoadScene(model_path, &session->scene)) {
        delete session;
        return 0L;
    }

    const InspectSceneData inspect_data = BuildInspectSceneData(model_path, session->scene);
    session->animation_names = inspect_data.animation_names;
    session->animation_durations_millis = inspect_data.animation_durations_millis;
    for (size_t index = 0; index < session->animation_names.size(); ++index) {
        session->animation_name_to_index.emplace(session->animation_names[index], index);
    }

    if (!BuildSessionGeometry(session) || !UpdatePreviewFrame(session, nullptr, 0.0)) {
        delete session;
        return 0L;
    }

    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_fbx_FbxNative_nativeDestroyPreviewSession(JNIEnv *, jobject, jlong session_handle)
{
    auto *session = reinterpret_cast<PreviewSession *>(session_handle);
    delete session;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_fbx_FbxNative_nativeReadPreviewInfo(JNIEnv *env, jobject, jlong session_handle)
{
    ClearLastError();
    auto *session = reinterpret_cast<PreviewSession *>(session_handle);
    if (!session) {
        SetLastError("FBX preview session is not available.");
        return nullptr;
    }
    return NewJavaString(env, BuildPreviewInfoJson(*session));
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_ai_assistance_fbx_FbxNative_nativeBuildPreviewFrame(
    JNIEnv *env,
    jobject,
    jlong session_handle,
    jstring animation_name,
    jdouble time_seconds)
{
    ClearLastError();
    auto *session = reinterpret_cast<PreviewSession *>(session_handle);
    if (!session) {
        SetLastError("FBX preview session is not available.");
        return nullptr;
    }

    const std::string animation = JStringToStdString(env, animation_name);
    const std::string *animation_ptr = animation.empty() ? nullptr : &animation;
    if (!UpdatePreviewFrame(session, animation_ptr, time_seconds)) {
        return nullptr;
    }

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(session->current_vertices.size()));
    if (!result) {
        SetLastError("Failed to allocate FBX preview vertex array.");
        return nullptr;
    }
    env->SetFloatArrayRegion(
        result,
        0,
        static_cast<jsize>(session->current_vertices.size()),
        session->current_vertices.data());
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ai_assistance_fbx_FbxNative_nativeReadEmbeddedTextureBytes(
    JNIEnv *env,
    jobject,
    jlong session_handle,
    jint texture_index)
{
    auto *session = reinterpret_cast<PreviewSession *>(session_handle);
    if (!session) {
        SetLastError("FBX preview session is not available.");
        return nullptr;
    }

    if (texture_index < 0 || static_cast<size_t>(texture_index) >= session->textures.size()) {
        return nullptr;
    }

    const TextureSlotData &texture = session->textures[texture_index];
    if (texture.embedded_bytes.empty()) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(texture.embedded_bytes.size()));
    if (!result) {
        SetLastError("Failed to allocate embedded FBX texture byte array.");
        return nullptr;
    }
    env->SetByteArrayRegion(
        result,
        0,
        static_cast<jsize>(texture.embedded_bytes.size()),
        reinterpret_cast<const jbyte *>(texture.embedded_bytes.data()));
    return result;
}
