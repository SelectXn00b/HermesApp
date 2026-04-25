/* METADATA
{
  "name": "openai_draw",

  "display_name": {
      "zh": "OpenAI 绘图",
      "en": "OpenAI Draw"
  },
  "description": {
    "zh": "使用 OpenAI 格式的图像生成 API (/v1/images/generations) 根据提示词画图，将图片保存到本地 /sdcard/Download/Operit/draws/ 目录，并返回 Markdown 图片提示。",
    "en": "Generate images via an OpenAI-compatible image generation API (/v1/images/generations) from a prompt, save to /sdcard/Download/Operit/draws/, and return a Markdown image reference."
  },
  "category": "Draw",
  "env": [
    {
      "name": "OPENAI_API_KEY",
      "description": {
        "zh": "OpenAI API Key（必填）",
        "en": "OpenAI API key (required)"
      },
      "required": true
    },
    {
      "name": "OPENAI_API_BASE_URL",
      "description": {
        "zh": "OpenAI API Base URL（可选，不填则默认 https://api.openai.com ）",
        "en": "OpenAI API base URL (optional; defaults to https://api.openai.com)"
      },
      "required": false
    },
    {
      "name": "OPENAI_IMAGE_MODEL",
      "description": {
        "zh": "默认绘图模型（可选；当 draw_image 未传 model 时使用）",
        "en": "Default image model (optional; used when draw_image doesn't pass model)"
      },
      "required": false
    }
  ],
  "tools": [
    {
      "name": "draw_image",
      "description": {
        "zh": "根据提示词调用 OpenAI 格式图像生成接口生成图片，保存到本地并返回 Markdown 图片提示。",
        "en": "Generate an image via an OpenAI-compatible image generation endpoint using a prompt, save it locally, and return a Markdown image reference."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "绘图提示词（英文或中文皆可）", "en": "Prompt for image generation (Chinese or English)" }, "type": "string", "required": true },
        { "name": "model", "description": { "zh": "模型名称（可选；不传则使用环境变量 OPENAI_IMAGE_MODEL，再不行使用默认值）", "en": "Model name (optional; falls back to env OPENAI_IMAGE_MODEL, then default)" }, "type": "string", "required": false },
        { "name": "size", "description": { "zh": "图片尺寸，例如 '1024x1024'，可选", "en": "Image size, e.g. '1024x1024' (optional)" }, "type": "string", "required": false },
        { "name": "file_name", "description": { "zh": "自定义保存到本地的文件名（不含路径和扩展名）", "en": "Custom output file name (without path or extension)" }, "type": "string", "required": false },
        { "name": "api_base_url", "description": { "zh": "OpenAI API Base URL（不传则取环境变量 OPENAI_API_BASE_URL 或默认 https://api.openai.com ）", "en": "OpenAI API base URL (optional; falls back to env OPENAI_API_BASE_URL or https://api.openai.com)" }, "type": "string", "required": false }
      ]
    }
  ]
}*/

/// <reference path="./types/index.d.ts" />

const openaiDraw = (function () {
    const HTTP_TIMEOUT_MS = 600000;
    const client = OkHttp.newBuilder()
        .connectTimeout(HTTP_TIMEOUT_MS)
        .readTimeout(HTTP_TIMEOUT_MS)
        .writeTimeout(HTTP_TIMEOUT_MS)
        .build();
    const DEFAULT_API_BASE_URL = "https://api.openai.com";
    const DEFAULT_MODEL = "gpt-image-1";

    // Android 实际路径为 /sdcard/Download，对应系统中文名“下载”
    const DOWNLOAD_ROOT = "/sdcard/Download";
    const OPERIT_DIR = `${DOWNLOAD_ROOT}/Operit`;
    const DRAWS_DIR = `${OPERIT_DIR}/draws`;

    function getApiKey(): string {
        const apiKey = getEnv("OPENAI_API_KEY");
        if (!apiKey) {
            throw new Error("OPENAI_API_KEY 未配置，请在环境变量中设置 OpenAI 的 API Key。");
        }
        return apiKey;
    }

    function joinUrl(baseUrl: string, path: string): string {
        const normalizedBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
        const normalizedPath = path.startsWith('/') ? path.slice(1) : path;
        return `${normalizedBase}${normalizedPath}`;
    }

    function getApiBaseUrl(customBaseUrl?: string | null): string {
        const fromParam = (customBaseUrl || "").trim();
        if (fromParam) return fromParam;

        const fromEnv = (getEnv("OPENAI_API_BASE_URL") || "").trim();
        if (fromEnv) return fromEnv;

        return DEFAULT_API_BASE_URL;
    }

    function getImageEndpoint(baseUrl: string): string {
        const trimmed = baseUrl.trim();
        if (!trimmed) return joinUrl(DEFAULT_API_BASE_URL, "v1/images/generations");

        if (trimmed.includes("/v1/images/generations")) return trimmed;

        // If user passes .../v1, we should append images/generations
        if (trimmed.endsWith("/v1")) return joinUrl(trimmed, "images/generations");
        if (trimmed.endsWith("/v1/")) return joinUrl(trimmed, "images/generations");

        // Otherwise append v1/images/generations
        return joinUrl(trimmed, "v1/images/generations");
    }

    function sanitizeFileName(name: string): string {
        const safe = name.replace(/[\\/:*?"<>|]/g, "_").trim();
        if (!safe) {
            return `openai_draw_${Date.now()}`;
        }
        return safe.substring(0, 80);
    }

    function buildFileName(prompt: string, customName?: string | null): string {
        if (customName && customName.trim().length > 0) {
            return sanitizeFileName(customName);
        }
        const shortPrompt = prompt.length > 40 ? `${prompt.substring(0, 40)}...` : prompt;
        const base = sanitizeFileName(shortPrompt || "image");
        const timestamp = Date.now();
        return `${base}_${timestamp}`;
    }

    async function ensureDirectories() {
        const dirs = [DOWNLOAD_ROOT, OPERIT_DIR, DRAWS_DIR];
        for (const dir of dirs) {
            try {
                const result = await Tools.Files.mkdir(dir);
                if (!result.successful) {
                    console.warn(`创建目录失败(可能已存在): ${dir} -> ${result.details}`);
                }
            } catch (e: any) {
                console.warn(`创建目录异常: ${dir} -> ${e?.message || e}`);
            }
        }
    }

    function normalizeBase64(base64: string): string {
        const raw = String(base64 || "").trim();
        if (!raw) return raw;

        // Some providers may return: data:image/png;base64,xxxx
        const prefixIndex = raw.indexOf("base64,");
        if (raw.startsWith("data:") && prefixIndex >= 0) {
            return raw.substring(prefixIndex + "base64,".length).trim();
        }

        return raw;
    }

    type OpenAIImageApiResult = {
        b64_json: string;
        revised_prompt?: string;
        effective_model: string;
    };

    async function callOpenAIImageApi(params: {
        prompt: string;
        model?: string;
        size?: string;
        api_base_url?: string;
    }): Promise<OpenAIImageApiResult> {
        const apiKey = getApiKey();
        const apiBaseUrl = getApiBaseUrl(params.api_base_url);
        const endpoint = getImageEndpoint(apiBaseUrl);

        const modelFromParam = (params.model || "").trim();
        const modelFromEnv = (getEnv("OPENAI_IMAGE_MODEL") || "").trim();
        const effectiveModel = modelFromParam || modelFromEnv || DEFAULT_MODEL;

        const body: any = {
            model: effectiveModel,
            prompt: params.prompt,
            response_format: "b64_json"
        };

        if (params.size && params.size.trim().length > 0) {
            body.size = params.size.trim();
        }

        const headers = {
            "accept": "application/json",
            "content-type": "application/json",
            "Authorization": `Bearer ${apiKey}`
        };

        const request = client
            .newRequest()
            .url(endpoint)
            .method("POST")
            .headers(headers)
            .body(JSON.stringify(body), "json");

        const response = await request.build().execute();

        if (!response.isSuccessful()) {
            throw new Error(`OpenAI 图片 API 调用失败: ${response.statusCode} - ${response.content}`);
        }

        let parsed: any;
        try {
            parsed = JSON.parse(response.content);
        } catch (e: any) {
            throw new Error(`解析 OpenAI 响应失败: ${e?.message || e}`);
        }

        const item = parsed?.data?.[0];
        if (!item) {
            throw new Error("OpenAI 响应中未找到 data[0]，请检查接口返回格式。");
        }

        if (item.b64_json && String(item.b64_json).trim().length > 0) {
            return {
                b64_json: String(item.b64_json),
                revised_prompt: item.revised_prompt ? String(item.revised_prompt) : undefined,
                effective_model: effectiveModel
            };
        }

        if (item.url && String(item.url).trim().length > 0) {
            // Some OpenAI-compatible APIs may return url only. We download it, then read as base64.
            const tmpName = `openai_tmp_${Date.now()}`;
            const tmpPath = `${DRAWS_DIR}/${tmpName}.png`;
            const downloadResult = await Tools.Files.download(String(item.url), tmpPath);
            if (!downloadResult.successful) {
                throw new Error(`下载图片失败: ${downloadResult.details}`);
            }

            const readBinary = await Tools.Files.readBinary(tmpPath);
            const contentBase64 = (readBinary as any)?.contentBase64;
            if (!contentBase64 || String(contentBase64).trim().length === 0) {
                throw new Error("读取下载图片失败: contentBase64 为空");
            }

            try {
                await Tools.Files.deleteFile(tmpPath);
            } catch {
                // ignore
            }

            return {
                b64_json: String(contentBase64),
                revised_prompt: item.revised_prompt ? String(item.revised_prompt) : undefined,
                effective_model: effectiveModel
            };
        }

        throw new Error("OpenAI 响应中未找到 b64_json 或 url，请检查模型/参数以及接口兼容性。");
    }

    async function draw_image(params: {
        prompt: string;
        model?: string;
        size?: string;
        file_name?: string;
        api_base_url?: string;
    }) {
        if (!params || !params.prompt || params.prompt.trim().length === 0) {
            throw new Error("参数 prompt 不能为空。");
        }

        const prompt = params.prompt.trim();

        await ensureDirectories();

        const apiResult = await callOpenAIImageApi({
            prompt,
            model: params.model,
            size: params.size,
            api_base_url: params.api_base_url
        });

        const baseName = buildFileName(prompt, params.file_name);
        const filePath = `${DRAWS_DIR}/${baseName}.png`;

        const writeResult = await Tools.Files.writeBinary(filePath, normalizeBase64(apiResult.b64_json));
        if (!writeResult.successful) {
            throw new Error(`保存图片失败: ${writeResult.details}`);
        }

        const fileUri = `file://${filePath}`;
        const markdown = `![AI生成的图片](${fileUri})`;

        const hintLines: string[] = [];
        hintLines.push("图片已生成并保存在本地 /sdcard/Download/Operit/draws/ 目录。");
        hintLines.push(`本地路径: ${filePath}`);
        hintLines.push("");
        hintLines.push("在后续回答中，请直接输出下面这一行 Markdown 来展示这张图片：");
        hintLines.push("");
        hintLines.push(markdown);

        return {
            file_path: filePath,
            file_uri: fileUri,
            markdown,
            prompt,
            revised_prompt: apiResult.revised_prompt || null,
            model: apiResult.effective_model,
            hint: hintLines.join("\n")
        };
    }

    async function draw_image_wrapper(params: {
        prompt: string;
        model?: string;
        size?: string;
        file_name?: string;
        api_base_url?: string;
    }) {
        try {
            const result = await draw_image(params);
            complete({
                success: true,
                message: "图片生成成功，已保存到 /sdcard/Download/Operit/draws/，并返回 Markdown 图片提示。",
                data: result
            });
        } catch (error: any) {
            console.error("draw_image 执行失败:", error);
            complete({
                success: false,
                message: `图片生成失败: ${error?.message || error}`,
                error_stack: error?.stack
            });
        }
    }

    return {
        draw_image: draw_image_wrapper
    };
})();

exports.draw_image = openaiDraw.draw_image;
