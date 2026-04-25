/* METADATA
{
  "name": "qwen_draw",

  "display_name": {
      "zh": "Qwen 绘图",
      "en": "Qwen Draw"
  },
  "description": {
    "zh": "使用阿里云百炼/DashScope 文生图接口（通义万相/通义千问图像）根据提示词画图（异步任务轮询），将图片保存到本地 /sdcard/Download/Operit/draws/ 目录，并返回 Markdown 图片提示。",
    "en": "Generate images via Alibaba Cloud Model Studio (DashScope) text-to-image API (async task polling), save to /sdcard/Download/Operit/draws/, and return a Markdown image reference."
  },
  "env": [
    {
      "name": "DASHSCOPE_API_KEY",
      "description": {
        "zh": "DashScope API Key（必填）",
        "en": "DashScope API key (required)"
      },
      "required": true
    },
    {
      "name": "DASHSCOPE_API_BASE_URL",
      "description": {
        "zh": "DashScope API Base URL（可选，不填则默认 https://dashscope.aliyuncs.com；国际站可用 https://dashscope-intl.aliyuncs.com）",
        "en": "DashScope API base URL (optional; defaults to https://dashscope.aliyuncs.com; intl: https://dashscope-intl.aliyuncs.com)"
      },
      "required": false
    },
    {
      "name": "QWEN_IMAGE_MODEL",
      "description": {
        "zh": "默认文生图模型（可选；当 draw_image 未传 model 时使用，例如 qwen-image-plus 或 wan2.2-t2i-flash）",
        "en": "Default image model (optional; used when draw_image doesn't pass model), e.g. qwen-image-plus or wan2.2-t2i-flash"
      },
      "required": false
    }
  ],
  "category": "Draw",
  "tools": [
    {
      "name": "draw_image",
      "description": {
        "zh": "根据提示词调用 DashScope 文生图接口生成图片，保存到本地并返回 Markdown 图片提示。",
        "en": "Generate an image via DashScope text-to-image API using a prompt, save it locally, and return a Markdown image reference."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "绘图提示词（英文或中文皆可）", "en": "Prompt for image generation (Chinese or English)" }, "type": "string", "required": true },
        { "name": "model", "description": { "zh": "模型名称（可选；不传则使用环境变量 QWEN_IMAGE_MODEL，再不行使用默认值）", "en": "Model name (optional; falls back to env QWEN_IMAGE_MODEL, then default)" }, "type": "string", "required": false },
        { "name": "size", "description": { "zh": "输出图像分辨率，如 '1664*928' 或 '1024x1024'（可选）", "en": "Output image resolution, e.g. '1664*928' or '1024x1024' (optional)" }, "type": "string", "required": false },
        { "name": "n", "description": { "zh": "生成图片数量（可选，默认 1）", "en": "Number of images (optional; default 1)" }, "type": "number", "required": false },
        { "name": "negative_prompt", "description": { "zh": "负面提示词（可选）", "en": "Negative prompt (optional)" }, "type": "string", "required": false },
        { "name": "prompt_extend", "description": { "zh": "是否开启 prompt 智能改写（可选，默认 true）", "en": "Enable prompt extension (optional; default true)" }, "type": "boolean", "required": false },
        { "name": "watermark", "description": { "zh": "是否加水印（可选，默认 false）", "en": "Enable watermark (optional; default false)" }, "type": "boolean", "required": false },
        { "name": "file_name", "description": { "zh": "自定义保存到本地的文件名（不含路径和扩展名）", "en": "Custom output file name (without path or extension)" }, "type": "string", "required": false },
        { "name": "api_base_url", "description": { "zh": "DashScope API Base URL（不传则取环境变量 DASHSCOPE_API_BASE_URL 或默认 https://dashscope.aliyuncs.com ）", "en": "DashScope API base URL (optional; falls back to env DASHSCOPE_API_BASE_URL or https://dashscope.aliyuncs.com)" }, "type": "string", "required": false },
        { "name": "poll_interval_ms", "description": { "zh": "轮询间隔（毫秒），默认 2000", "en": "Polling interval (milliseconds), default 2000" }, "type": "number", "required": false },
        { "name": "max_wait_time_ms", "description": { "zh": "最长等待时间（毫秒），默认 10 分钟", "en": "Max wait time (milliseconds), default 10 minutes" }, "type": "number", "required": false }
      ]
    }
  ]
}*/

/// <reference path="./types/index.d.ts" />

const qwenDraw = (function () {
    const HTTP_TIMEOUT_MS = 600000;
    const client = OkHttp.newBuilder()
        .connectTimeout(HTTP_TIMEOUT_MS)
        .readTimeout(HTTP_TIMEOUT_MS)
        .writeTimeout(HTTP_TIMEOUT_MS)
        .build();

    const DEFAULT_API_BASE_URL = "https://dashscope.aliyuncs.com";
    const DEFAULT_MODEL = "qwen-image-plus";

    const DOWNLOAD_ROOT = "/sdcard/Download";
    const OPERIT_DIR = `${DOWNLOAD_ROOT}/Operit`;
    const DRAWS_DIR = `${OPERIT_DIR}/draws`;

    const POLL_INTERVAL_MS = 2000;
    const MAX_WAIT_TIME_MS = 600000;

    function isRecord(value: unknown): value is Record<string, unknown> {
        return typeof value === "object" && value !== null;
    }

    function getErrorMessage(error: unknown): string {
        if (error instanceof Error) return error.message;
        return String(error);
    }

    function getErrorStack(error: unknown): string | undefined {
        if (error instanceof Error) return error.stack;
        return undefined;
    }

    function normalizePositiveInt(value: unknown, fallback: number): number {
        if (value === undefined || value === null) return fallback;
        const n = typeof value === "number" ? value : parseInt(String(value), 10);
        if (!Number.isFinite(n) || n <= 0) return fallback;
        return Math.floor(n);
    }

    function joinUrl(baseUrl: string, path: string): string {
        const normalizedBase = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
        const normalizedPath = path.startsWith("/") ? path.slice(1) : path;
        return `${normalizedBase}${normalizedPath}`;
    }

    function getApiKey(): string {
        const apiKey = getEnv("DASHSCOPE_API_KEY");
        if (!apiKey) {
            throw new Error("DASHSCOPE_API_KEY 未配置，请在环境变量中设置 DashScope 的 API Key。");
        }
        return apiKey;
    }

    function getApiBaseUrl(customBaseUrl?: string | null): string {
        const fromParam = (customBaseUrl || "").trim();
        if (fromParam) return fromParam;

        const fromEnv = (getEnv("DASHSCOPE_API_BASE_URL") || "").trim();
        if (fromEnv) return fromEnv;

        return DEFAULT_API_BASE_URL;
    }

    function getTextToImageEndpoint(baseUrl: string): string {
        const trimmed = baseUrl.trim();
        if (!trimmed) return joinUrl(DEFAULT_API_BASE_URL, "api/v1/services/aigc/text2image/image-synthesis");

        if (trimmed.includes("/api/v1/services/aigc/text2image/image-synthesis")) return trimmed;

        if (trimmed.endsWith("/api/v1")) return joinUrl(trimmed, "services/aigc/text2image/image-synthesis");
        if (trimmed.endsWith("/api/v1/")) return joinUrl(trimmed, "services/aigc/text2image/image-synthesis");

        return joinUrl(trimmed, "api/v1/services/aigc/text2image/image-synthesis");
    }

    function getTaskEndpoint(baseUrl: string, taskId: string): string {
        const trimmed = baseUrl.trim();
        const safeBase = trimmed || DEFAULT_API_BASE_URL;
        if (safeBase.includes("/api/v1/tasks/")) {
            const idx = safeBase.indexOf("/api/v1/tasks/");
            return safeBase.substring(0, idx) + `/api/v1/tasks/${taskId}`;
        }
        if (safeBase.endsWith("/api/v1")) return joinUrl(safeBase, `tasks/${taskId}`);
        if (safeBase.endsWith("/api/v1/")) return joinUrl(safeBase, `tasks/${taskId}`);
        return joinUrl(safeBase, `api/v1/tasks/${taskId}`);
    }

    function sanitizeFileName(name: string): string {
        const safe = name.replace(/[\\/:*?"<>|]/g, "_").trim();
        if (!safe) return `qwen_draw_${Date.now()}`;
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

    function normalizeSize(size?: string | null): string | undefined {
        const raw = String(size || "").trim();
        if (!raw) return undefined;

        const cleaned = raw.replace(/×/g, "*").replace(/x/gi, "*").replace(/\s+/g, "");
        if (!/^\d+\*\d+$/.test(cleaned)) return undefined;
        return cleaned;
    }

    function guessExtensionFromUrl(url: string): string {
        const match = url.match(/\.(png|jpg|jpeg|webp|gif)(?:\?|#|$)/i);
        if (match && match[1]) return match[1].toLowerCase();
        return "png";
    }

    async function ensureDirectories(): Promise<void> {
        const dirs = [DOWNLOAD_ROOT, OPERIT_DIR, DRAWS_DIR];
        for (const dir of dirs) {
            try {
                const result = await Tools.Files.mkdir(dir);
                if (!result.successful) {
                    console.warn(`创建目录失败(可能已存在): ${dir} -> ${result.details}`);
                }
            } catch (e: unknown) {
                console.warn(`创建目录异常: ${dir} -> ${getErrorMessage(e)}`);
            }
        }
    }

    async function createTask(params: {
        prompt: string;
        model?: string;
        size?: string;
        n?: number;
        negative_prompt?: string;
        prompt_extend?: boolean;
        watermark?: boolean;
        api_base_url?: string;
    }): Promise<{ task_id: string; effective_model: string }> {
        const apiKey = getApiKey();
        const apiBaseUrl = getApiBaseUrl(params.api_base_url);
        const endpoint = getTextToImageEndpoint(apiBaseUrl);

        const modelFromParam = (params.model || "").trim();
        const modelFromEnv = (getEnv("QWEN_IMAGE_MODEL") || "").trim();
        const effectiveModel = modelFromParam || modelFromEnv || DEFAULT_MODEL;

        const body: Record<string, unknown> = {
            model: effectiveModel,
            input: {
                prompt: params.prompt
            },
            parameters: {
                n: typeof params.n === "number" && Number.isFinite(params.n) && params.n > 0 ? Math.floor(params.n) : 1,
                prompt_extend: params.prompt_extend === undefined ? true : !!params.prompt_extend,
                watermark: params.watermark === undefined ? false : !!params.watermark
            }
        };

        const normalizedSize = normalizeSize(params.size);
        if (normalizedSize) {
            (body.parameters as Record<string, unknown>)["size"] = normalizedSize;
        }

        const negativePrompt = (params.negative_prompt || "").trim();
        if (negativePrompt) {
            (body.parameters as Record<string, unknown>)["negative_prompt"] = negativePrompt;
        }

        const headers = {
            "accept": "application/json",
            "content-type": "application/json",
            "Authorization": `Bearer ${apiKey}`,
            "X-DashScope-Async": "enable"
        };

        const request = client
            .newRequest()
            .url(endpoint)
            .method("POST")
            .headers(headers)
            .body(JSON.stringify(body), "json");

        const response = await request.build().execute();

        if (!response.isSuccessful()) {
            throw new Error(`DashScope 文生图创建任务失败: ${response.statusCode} - ${response.content}`);
        }

        let parsed: unknown;
        try {
            parsed = JSON.parse(response.content) as unknown;
        } catch (e: unknown) {
            throw new Error(`解析 DashScope 创建任务响应失败: ${getErrorMessage(e)}`);
        }

        const output = isRecord(parsed) && isRecord(parsed["output"]) ? (parsed["output"] as Record<string, unknown>) : null;
        const taskId = output && typeof output["task_id"] === "string" ? (output["task_id"] as string) : "";
        if (!taskId) {
            throw new Error(`DashScope 创建任务响应中未找到 output.task_id: ${response.content}`);
        }

        return { task_id: taskId, effective_model: effectiveModel };
    }

    async function pollTask(params: {
        task_id: string;
        api_base_url?: string;
        poll_interval_ms?: number;
        max_wait_time_ms?: number;
    }): Promise<{ image_url: string; task_status: string }> {
        const apiKey = getApiKey();
        const apiBaseUrl = getApiBaseUrl(params.api_base_url);

        const pollIntervalMs = normalizePositiveInt(params.poll_interval_ms, POLL_INTERVAL_MS);
        const maxWaitTimeMs = normalizePositiveInt(params.max_wait_time_ms, MAX_WAIT_TIME_MS);

        const startTime = Date.now();
        let attempts = 0;

        while (Date.now() - startTime < maxWaitTimeMs) {
            attempts++;

            const endpoint = getTaskEndpoint(apiBaseUrl, params.task_id);
            const headers = {
                "accept": "application/json",
                "Authorization": `Bearer ${apiKey}`
            };

            const request = client
                .newRequest()
                .url(endpoint)
                .method("GET")
                .headers(headers);

            const response = await request.build().execute();

            if (!response.isSuccessful()) {
                throw new Error(`DashScope 查询任务失败: ${response.statusCode} - ${response.content}`);
            }

            let parsed: unknown;
            try {
                parsed = JSON.parse(response.content) as unknown;
            } catch (e: unknown) {
                throw new Error(`解析 DashScope 查询任务响应失败: ${getErrorMessage(e)}`);
            }

            const output = isRecord(parsed) && isRecord(parsed["output"]) ? (parsed["output"] as Record<string, unknown>) : null;
            const taskStatus = output && typeof output["task_status"] === "string" ? String(output["task_status"]) : "";

            if (taskStatus === "SUCCEEDED") {
                const results = output ? output["results"] : null;
                const first = Array.isArray(results) && results.length > 0 ? results[0] : null;
                const url = isRecord(first) ? first["url"] : undefined;
                if ((typeof url !== "string" && typeof url !== "number") || String(url).trim().length === 0) {
                    throw new Error(`任务已完成但未找到图片 URL: ${response.content}`);
                }
                return { image_url: String(url), task_status: taskStatus };
            }

            if (taskStatus === "FAILED") {
                throw new Error(`任务失败: ${response.content}`);
            }

            if (attempts % 5 === 0) {
                console.log(`任务状态: ${taskStatus || "UNKNOWN"}，继续等待...`);
            }

            await Tools.System.sleep(pollIntervalMs);
        }

        throw new Error(`任务超时: 等待超过${Math.ceil(maxWaitTimeMs / 60000)}分钟仍未完成`);
    }

    async function draw_image(params: {
        prompt: string;
        model?: string;
        size?: string;
        n?: number;
        negative_prompt?: string;
        prompt_extend?: boolean;
        watermark?: boolean;
        file_name?: string;
        api_base_url?: string;
        poll_interval_ms?: number;
        max_wait_time_ms?: number;
    }) {
        if (!params || !params.prompt || params.prompt.trim().length === 0) {
            throw new Error("参数 prompt 不能为空。");
        }

        const prompt = params.prompt.trim();

        await ensureDirectories();

        const createResult = await createTask({
            prompt,
            model: params.model,
            size: params.size,
            n: params.n,
            negative_prompt: params.negative_prompt,
            prompt_extend: params.prompt_extend,
            watermark: params.watermark,
            api_base_url: params.api_base_url
        });

        const pollResult = await pollTask({
            task_id: createResult.task_id,
            api_base_url: params.api_base_url,
            poll_interval_ms: params.poll_interval_ms,
            max_wait_time_ms: params.max_wait_time_ms
        });

        const ext = guessExtensionFromUrl(pollResult.image_url);
        const baseName = buildFileName(prompt, params.file_name);
        const filePath = `${DRAWS_DIR}/${baseName}.${ext}`;

        const downloadResult = await Tools.Files.download(pollResult.image_url, filePath);
        if (!downloadResult.successful) {
            throw new Error(`下载图片失败: ${downloadResult.details}`);
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
            model: createResult.effective_model,
            task_id: createResult.task_id,
            task_status: pollResult.task_status,
            image_url: pollResult.image_url,
            hint: hintLines.join("\n")
        };
    }

    async function draw_image_wrapper(params: {
        prompt: string;
        model?: string;
        size?: string;
        n?: number;
        negative_prompt?: string;
        prompt_extend?: boolean;
        watermark?: boolean;
        file_name?: string;
        api_base_url?: string;
        poll_interval_ms?: number;
        max_wait_time_ms?: number;
    }) {
        try {
            const result = await draw_image(params);
            complete({
                success: true,
                message: "图片生成成功，已保存到 /sdcard/Download/Operit/draws/，并返回 Markdown 图片提示。",
                data: result
            });
        } catch (error: unknown) {
            console.error("draw_image 执行失败:", error);
            complete({
                success: false,
                message: `图片生成失败: ${getErrorMessage(error)}`,
                error_stack: getErrorStack(error)
            });
        }
    }

    return {
        draw_image: draw_image_wrapper
    };
})();

exports.draw_image = qwenDraw.draw_image;
