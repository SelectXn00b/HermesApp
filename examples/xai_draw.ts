/* METADATA
{
  "name": "xai_draw",
  "display_name": {
    "zh": "xAI 图片与视频",
    "en": "xAI Images and Video"
  },
  "description": {
    "zh": "使用 xAI 官方接口生成图片和视频，并保存到本地。",
    "en": "Generate images and videos with the official xAI APIs and save them locally."
  },
  "env": [
    {
      "name": "XAI_API_KEY",
      "description": {
        "zh": "xAI API Key（必填）",
        "en": "xAI API key (required)"
      },
      "required": true
    },
    {
      "name": "XAI_IMAGE_MODEL",
      "description": {
        "zh": "默认图片模型（可选；未传 model 时使用，默认 grok-2-image-1212）",
        "en": "Default image model (optional; used when model is omitted, default grok-2-image-1212)"
      },
      "required": false
    },
    {
      "name": "XAI_VIDEO_MODEL",
      "description": {
        "zh": "默认视频模型（可选；未传 model 时使用，默认 grok-imagine-video）",
        "en": "Default video model (optional; used when model is omitted, default grok-imagine-video)"
      },
      "required": false
    }
  ],
  "category": "Draw",
  "tools": [
    {
      "name": "draw_image",
      "description": {
        "zh": "根据提示词调用 xAI 图像生成 API 生成图片，保存到本地并返回 Markdown 图片提示。",
        "en": "Generate an image via the xAI image generation API using a prompt, save it locally, and return a Markdown image reference."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "绘图提示词（英文或中文皆可）", "en": "Prompt for image generation (Chinese or English)" }, "type": "string", "required": true },
        { "name": "model", "description": { "zh": "xAI 图像模型名称；不传则优先取 XAI_IMAGE_MODEL，再用默认值 grok-2-image-1212", "en": "xAI image model name; falls back to XAI_IMAGE_MODEL, then grok-2-image-1212" }, "type": "string", "required": false },
        { "name": "size", "description": { "zh": "图片尺寸，例如 1024x1024（可选）", "en": "Image size, e.g. 1024x1024 (optional)" }, "type": "string", "required": false },
        { "name": "file_name", "description": { "zh": "自定义保存到本地的文件名（不含路径和扩展名）", "en": "Custom output file name (without path or extension)" }, "type": "string", "required": false }
      ]
    },
    {
      "name": "draw_video",
      "description": {
        "zh": "根据提示词调用 xAI 官方视频生成 API 生成视频，支持文生视频、图生视频和视频编辑，轮询完成后下载到本地并返回本地视频链接提示。",
        "en": "Generate a video with the official xAI video API. Supports text-to-video, image-to-video, and video editing. Polls until completion, downloads locally, and returns local video link hints."
      },
      "parameters": [
        { "name": "prompt", "description": { "zh": "视频提示词", "en": "Video prompt" }, "type": "string", "required": true },
        { "name": "model", "description": { "zh": "视频模型；不传则优先取 XAI_VIDEO_MODEL，再用默认值 grok-imagine-video", "en": "Video model; falls back to XAI_VIDEO_MODEL, then grok-imagine-video" }, "type": "string", "required": false },
        { "name": "aspect_ratio", "description": { "zh": "输出比例，可选 1:1、16:9、9:16、4:3、3:4、3:2、2:3；默认 16:9；视频编辑模式不支持", "en": "Output aspect ratio. Supported: 1:1, 16:9, 9:16, 4:3, 3:4, 3:2, 2:3. Defaults to 16:9; not supported for video editing." }, "type": "string", "required": false },
        { "name": "resolution", "description": { "zh": "输出分辨率，仅支持 480p 或 720p；默认 480p；视频编辑模式不支持", "en": "Output resolution, only 480p or 720p. Defaults to 480p; not supported for video editing." }, "type": "string", "required": false },
        { "name": "duration", "description": { "zh": "输出时长，支持 1-15 秒；默认 5；视频编辑模式不支持", "en": "Output duration from 1 to 15 seconds. Defaults to 5; not supported for video editing." }, "type": "number", "required": false },
        { "name": "image_url", "description": { "zh": "图生视频输入图 URL（可选）", "en": "Input image URL for image-to-video (optional)" }, "type": "string", "required": false },
        { "name": "image_path", "description": { "zh": "图生视频输入图本地路径（可选，会转成 data URL）", "en": "Local input image path for image-to-video (optional; converted to a data URL)" }, "type": "string", "required": false },
        { "name": "video_url", "description": { "zh": "视频编辑输入视频 URL（可选）", "en": "Input video URL for video editing (optional)" }, "type": "string", "required": false },
        { "name": "file_name", "description": { "zh": "自定义保存到本地的文件名（不含路径和扩展名）", "en": "Custom output file name (without path or extension)" }, "type": "string", "required": false },
        { "name": "poll_interval_ms", "description": { "zh": "轮询间隔毫秒数，默认 5000", "en": "Polling interval in milliseconds, default 5000" }, "type": "number", "required": false },
        { "name": "max_wait_time_ms", "description": { "zh": "最大等待毫秒数，默认 600000", "en": "Maximum wait time in milliseconds, default 600000" }, "type": "number", "required": false }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />

const xaiDraw = (function () {
    const HTTP_TIMEOUT_MS = 600000;
    const client = OkHttp.newBuilder()
        .connectTimeout(HTTP_TIMEOUT_MS)
        .readTimeout(HTTP_TIMEOUT_MS)
        .writeTimeout(HTTP_TIMEOUT_MS)
        .build();

    const DEFAULT_IMAGE_MODEL = "grok-2-image-1212";
    const DEFAULT_VIDEO_MODEL = "grok-imagine-video";
    const DEFAULT_POLL_INTERVAL_MS = 5000;
    const DEFAULT_MAX_WAIT_TIME_MS = 600000;
    const DEFAULT_VIDEO_ASPECT_RATIO = "16:9";
    const DEFAULT_VIDEO_RESOLUTION = "480p";
    const DEFAULT_VIDEO_DURATION = 5;
    const VIDEO_ASPECT_RATIOS = ["1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3"];
    const VIDEO_RESOLUTIONS = ["480p", "720p"];
    const MIN_VIDEO_DURATION = 1;
    const MAX_VIDEO_DURATION = 15;

    const API_BASE_URL = "https://api.x.ai/v1";
    const IMAGE_API_ENDPOINT = `${API_BASE_URL}/images/generations`;
    const VIDEO_GENERATION_ENDPOINT = `${API_BASE_URL}/videos/generations`;

    const DOWNLOAD_ROOT = "/sdcard/Download";
    const OPERIT_DIR = `${DOWNLOAD_ROOT}/Operit`;
    const DRAWS_DIR = `${OPERIT_DIR}/draws`;
    const VIDEOS_DIR = `${OPERIT_DIR}/videos`;

    type JsonMap = Record<string, any>;

    type VideoParams = {
        prompt: string;
        model?: string;
        aspect_ratio?: string;
        resolution?: string;
        duration?: number | string;
        image_url?: string;
        image_path?: string;
        video_url?: string;
        file_name?: string;
        poll_interval_ms?: number | string;
        max_wait_time_ms?: number | string;
    };

    function getErrorMessage(error: unknown): string {
        if (error instanceof Error) return error.message;
        return String(error);
    }

    function getErrorStack(error: unknown): string | undefined {
        if (error instanceof Error) return error.stack;
        return undefined;
    }

    function isRecord(value: unknown): value is Record<string, unknown> {
        return typeof value === "object" && value !== null && !Array.isArray(value);
    }

    function getApiKey(): string {
        const apiKey = getEnv("XAI_API_KEY");
        if (!apiKey) {
            throw new Error("XAI_API_KEY 未配置，请在环境变量中设置 xAI 的 API Key。");
        }
        return apiKey;
    }

    function getDefaultImageModel(): string {
        const fromEnv = String(getEnv("XAI_IMAGE_MODEL") || "").trim();
        return fromEnv || DEFAULT_IMAGE_MODEL;
    }

    function getDefaultVideoModel(): string {
        const fromEnv = String(getEnv("XAI_VIDEO_MODEL") || "").trim();
        return fromEnv || DEFAULT_VIDEO_MODEL;
    }

    function sanitizeFileName(name: string, fallbackPrefix: string): string {
        const safe = String(name || "").replace(/[\\/:*?"<>|]/g, "_").trim();
        if (!safe) {
            return `${fallbackPrefix}_${Date.now()}`;
        }
        return safe.substring(0, 80);
    }

    function buildFileName(prompt: string, customName: string | null | undefined, fallbackPrefix: string): string {
        if (customName && customName.trim().length > 0) {
            return sanitizeFileName(customName, fallbackPrefix);
        }
        const shortPrompt = prompt.length > 40 ? `${prompt.substring(0, 40)}...` : prompt;
        const base = sanitizeFileName(shortPrompt || fallbackPrefix, fallbackPrefix);
        return `${base}_${Date.now()}`;
    }

    function guessExtensionFromUrl(url: string, fallback: string): string {
        const match = String(url || "").match(/\.(png|jpg|jpeg|webp|gif|mp4|mov|webm|mkv)(?:\?|#|$)/i);
        if (match && match[1]) {
            return match[1].toLowerCase();
        }
        return fallback;
    }

    function guessMimeTypeFromPath(path: string): string {
        const normalized = String(path || "").trim().toLowerCase();
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".webp")) return "image/webp";
        if (normalized.endsWith(".gif")) return "image/gif";
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    function isProbablyUrl(value: string): boolean {
        return /^https?:\/\//i.test(String(value || "").trim());
    }

    function normalizePositiveInteger(value: unknown, fallback: number): number {
        if (value === undefined || value === null || value === "") return fallback;
        const parsed = typeof value === "number" ? value : parseInt(String(value), 10);
        if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
        return Math.floor(parsed);
    }

    function normalizeVideoAspectRatio(value: unknown): string {
        const raw = String(value || "").trim();
        if (!raw) return DEFAULT_VIDEO_ASPECT_RATIO;
        if (!VIDEO_ASPECT_RATIOS.includes(raw)) {
            throw new Error(`aspect_ratio 仅支持 ${VIDEO_ASPECT_RATIOS.join(" 或 ")}。`);
        }
        return raw;
    }

    function normalizeVideoResolution(value: unknown): string {
        const raw = String(value || "").trim().toLowerCase();
        if (!raw) return DEFAULT_VIDEO_RESOLUTION;
        if (!VIDEO_RESOLUTIONS.includes(raw)) {
            throw new Error(`resolution 仅支持 ${VIDEO_RESOLUTIONS.join(" 或 ")}。`);
        }
        return raw;
    }

    function normalizeVideoDuration(value: unknown): number {
        if (value === undefined || value === null || value === "") return DEFAULT_VIDEO_DURATION;
        const parsed = typeof value === "number" ? value : parseInt(String(value), 10);
        if (!Number.isFinite(parsed)) {
            throw new Error("duration 必须是数字。");
        }
        const normalized = Math.floor(parsed);
        if (normalized < MIN_VIDEO_DURATION || normalized > MAX_VIDEO_DURATION) {
            throw new Error(`duration 仅支持 ${MIN_VIDEO_DURATION}-${MAX_VIDEO_DURATION} 秒。`);
        }
        return normalized;
    }

    async function ensureDirectories(): Promise<void> {
        const dirs = [DOWNLOAD_ROOT, OPERIT_DIR, DRAWS_DIR, VIDEOS_DIR];
        for (const dir of dirs) {
            try {
                const result = await Tools.Files.mkdir(dir);
                if (!result.successful) {
                    console.warn(`创建目录失败(可能已存在): ${dir} -> ${result.details}`);
                }
            } catch (error) {
                console.warn(`创建目录异常: ${dir} -> ${getErrorMessage(error)}`);
            }
        }
    }

    async function parseJsonResponse(response: { content: string }, label: string): Promise<JsonMap> {
        try {
            const parsed = JSON.parse(response.content) as unknown;
            if (!isRecord(parsed)) {
                throw new Error("响应不是对象");
            }
            return parsed as JsonMap;
        } catch (error) {
            throw new Error(`解析 ${label} 响应失败: ${getErrorMessage(error)}`);
        }
    }

    function extractApiErrorMessage(payload: JsonMap): string {
        const directError = payload.error;
        if (typeof directError === "string" && directError.trim()) {
            return directError.trim();
        }
        if (isRecord(directError)) {
            if (typeof directError.message === "string" && directError.message.trim()) {
                return directError.message.trim();
            }
            if (typeof directError.code === "string" && directError.code.trim()) {
                return directError.code.trim();
            }
        }
        return "";
    }

    async function readLocalImageAsDataUrl(filePath: string): Promise<string> {
        const trimmedPath = String(filePath || "").trim();
        if (!trimmedPath) {
            throw new Error("image_path 不能为空。");
        }

        const existsResult = await Tools.Files.exists(trimmedPath);
        if (!existsResult.exists) {
            throw new Error(`本地图片不存在: ${trimmedPath}`);
        }

        const binaryResult = await Tools.Files.readBinary(trimmedPath);
        const base64Content = binaryResult && binaryResult.contentBase64
            ? String(binaryResult.contentBase64).trim()
            : "";
        if (!base64Content) {
            throw new Error(`读取本地图片失败: ${trimmedPath}`);
        }

        return `data:${guessMimeTypeFromPath(trimmedPath)};base64,${base64Content}`;
    }

    async function resolveVideoInputs(
        imageUrl: string | undefined,
        imagePath: string | undefined,
        videoUrl: string | undefined
    ): Promise<{ image?: string; video_url?: string; input_type: string }> {
        const trimmedImageUrl = String(imageUrl || "").trim();
        const trimmedImagePath = String(imagePath || "").trim();
        const trimmedVideoUrl = String(videoUrl || "").trim();

        const imageInputCount = (trimmedImageUrl ? 1 : 0) + (trimmedImagePath ? 1 : 0);
        if (imageInputCount > 1) {
            throw new Error("image_url 和 image_path 只能二选一，请不要同时传。");
        }
        if (trimmedVideoUrl && imageInputCount > 0) {
            throw new Error("视频生成一次只能使用一种输入源：纯文本、图片或视频。请不要同时传 image_* 和 video_url。");
        }

        if (trimmedImageUrl) {
            if (!isProbablyUrl(trimmedImageUrl)) {
                throw new Error("image_url 必须是 http 或 https 链接。");
            }
            return {
                image: trimmedImageUrl,
                input_type: "image_url"
            };
        }

        if (trimmedImagePath) {
            return {
                image: await readLocalImageAsDataUrl(trimmedImagePath),
                input_type: "image_path"
            };
        }

        if (trimmedVideoUrl) {
            if (!isProbablyUrl(trimmedVideoUrl)) {
                throw new Error("video_url 必须是 http 或 https 链接。");
            }
            return {
                video_url: trimmedVideoUrl,
                input_type: "video_url"
            };
        }

        return {
            input_type: "text"
        };
    }

    async function callXaiImageApi(params: {
        prompt: string;
        model?: string;
        size?: string;
    }): Promise<{ image_url: string; effective_model: string }> {
        const apiKey = getApiKey();
        const modelFromParam = String(params.model || "").trim();
        const effectiveModel = modelFromParam || getDefaultImageModel();

        const body: JsonMap = {
            model: effectiveModel,
            prompt: params.prompt
        };

        const size = String(params.size || "").trim();
        if (size) {
            body.size = size;
        }

        const request = client
            .newRequest()
            .url(IMAGE_API_ENDPOINT)
            .method("POST")
            .headers({
                "accept": "application/json",
                "content-type": "application/json",
                "Authorization": `Bearer ${apiKey}`
            })
            .body(JSON.stringify(body), "json");

        const response = await request.build().execute();
        if (!response.isSuccessful()) {
            throw new Error(`xAI 图片 API 调用失败: ${response.statusCode} - ${response.content}`);
        }

        const parsed = await parseJsonResponse(response, "xAI 图片生成");
        const data = Array.isArray(parsed.data) ? parsed.data : [];
        const first = data.length > 0 && isRecord(data[0]) ? data[0] as JsonMap : null;
        const imageUrl = first && typeof first.url === "string" ? String(first.url).trim() : "";

        if (!imageUrl) {
            throw new Error("xAI 响应中未找到图片 URL，请检查模型和参数是否正确。");
        }

        return {
            image_url: imageUrl,
            effective_model: effectiveModel
        };
    }

    async function createVideoTask(params: VideoParams & { image?: string; video_url?: string }) {
        const apiKey = getApiKey();
        const modelFromParam = String(params.model || "").trim();
        const effectiveModel = modelFromParam || getDefaultVideoModel();

        const body: JsonMap = {
            model: effectiveModel,
            prompt: String(params.prompt || "").trim()
        };

        const isVideoEdit = Boolean(params.video_url);
        if (!isVideoEdit) {
            body.aspect_ratio = normalizeVideoAspectRatio(params.aspect_ratio);
            body.resolution = normalizeVideoResolution(params.resolution);
            body.duration = normalizeVideoDuration(params.duration);
        }

        if (params.image) {
            body.image = {
                url: params.image
            };
        }
        if (params.video_url) {
            body.video_url = params.video_url;
        }

        const request = client
            .newRequest()
            .url(VIDEO_GENERATION_ENDPOINT)
            .method("POST")
            .headers({
                "accept": "application/json",
                "content-type": "application/json",
                "Authorization": `Bearer ${apiKey}`
            })
            .body(JSON.stringify(body), "json");

        const response = await request.build().execute();
        if (!response.isSuccessful()) {
            throw new Error(`xAI 视频创建任务失败: ${response.statusCode} - ${response.content}`);
        }

        const parsed = await parseJsonResponse(response, "xAI 视频创建");
        const requestId = typeof parsed.request_id === "string" ? parsed.request_id.trim() : "";
        const status = typeof parsed.status === "string" ? parsed.status.trim() : "";

        if (!requestId) {
            throw new Error(`xAI 视频创建响应中未找到 request_id: ${response.content}`);
        }

        return {
            request_id: requestId,
            status,
            effective_model: effectiveModel,
            aspect_ratio: typeof body.aspect_ratio === "string" ? body.aspect_ratio : null,
            resolution: typeof body.resolution === "string" ? body.resolution : null,
            duration: typeof body.duration === "number" ? body.duration : null
        };
    }

    async function queryVideoTaskStatus(requestId: string): Promise<{
        status: string;
        video_url: string;
        content_type: string;
        expires_at: string;
        duration: number | null;
        respect_moderation: string;
        error_message: string;
    }> {
        const apiKey = getApiKey();
        const endpoint = `${API_BASE_URL}/videos/${encodeURIComponent(requestId)}`;
        const request = client
            .newRequest()
            .url(endpoint)
            .method("GET")
            .headers({
                "accept": "application/json",
                "Authorization": `Bearer ${apiKey}`
            });

        const response = await request.build().execute();
        if (!response.isSuccessful()) {
            throw new Error(`xAI 视频查询失败: ${response.statusCode} - ${response.content}`);
        }

        const parsed = await parseJsonResponse(response, "xAI 视频查询");
        const status = typeof parsed.status === "string" ? parsed.status.trim() : "";
        const video = isRecord(parsed.video) ? parsed.video as JsonMap : null;
        const videoUrl = video && typeof video.url === "string" ? video.url.trim() : "";
        const contentType = video && typeof video.content_type === "string" ? video.content_type.trim() : "";
        const expiresAt = video && typeof video.expires_at === "string" ? video.expires_at.trim() : "";
        const duration = video && typeof video.duration === "number" ? video.duration : null;
        const respectModeration = video && typeof video.respect_moderation === "string"
            ? video.respect_moderation.trim()
            : "";
        const errorMessage = extractApiErrorMessage(parsed);

        return {
            status,
            video_url: videoUrl,
            content_type: contentType,
            expires_at: expiresAt,
            duration,
            respect_moderation: respectModeration,
            error_message: errorMessage
        };
    }

    function normalizeVideoTaskStatus(status: string): "processing" | "completed" | "failed" {
        const normalized = String(status || "").trim().toLowerCase();
        if (normalized === "done") return "completed";
        if (normalized === "expired") return "failed";
        return "processing";
    }

    async function draw_image(params: {
        prompt: string;
        model?: string;
        size?: string;
        file_name?: string;
    }) {
        if (!params || !params.prompt || params.prompt.trim().length === 0) {
            throw new Error("参数 prompt 不能为空。");
        }

        const prompt = params.prompt.trim();
        await ensureDirectories();

        const apiResult = await callXaiImageApi({
            prompt,
            model: params.model,
            size: params.size
        });

        const ext = guessExtensionFromUrl(apiResult.image_url, "png");
        const baseName = buildFileName(prompt, params.file_name, "xai_draw");
        const filePath = `${DRAWS_DIR}/${baseName}.${ext}`;
        const downloadResult = await Tools.Files.download(apiResult.image_url, filePath);
        if (!downloadResult.successful) {
            throw new Error(`下载图片失败: ${downloadResult.details}`);
        }

        const fileUri = `file://${filePath}`;
        const markdown = `![AI生成的图片](${fileUri})`;

        const hintLines: string[] = [];
        hintLines.push(`图片已生成并保存在本地 ${DRAWS_DIR}。`);
        hintLines.push(`本地路径: ${filePath}`);
        hintLines.push("");
        hintLines.push("在后续回答中，请直接输出下面这一行 Markdown 来展示这张图片：");
        hintLines.push("");
        hintLines.push(markdown);

        return {
            prompt,
            model: apiResult.effective_model,
            remote_image_url: apiResult.image_url,
            file_path: filePath,
            file_uri: fileUri,
            markdown,
            hint: hintLines.join("\n")
        };
    }

    async function draw_video(params: VideoParams) {
        const prompt = String(params && params.prompt ? params.prompt : "").trim();
        if (!prompt) {
            throw new Error("prompt 不能为空。");
        }

        await ensureDirectories();

        const resolvedInputs = await resolveVideoInputs(params.image_url, params.image_path, params.video_url);
        const createdTask = await createVideoTask({
            ...params,
            prompt,
            image: resolvedInputs.image,
            video_url: resolvedInputs.video_url
        });

        const pollIntervalMs = normalizePositiveInteger(params.poll_interval_ms, DEFAULT_POLL_INTERVAL_MS);
        const maxWaitTimeMs = normalizePositiveInteger(params.max_wait_time_ms, DEFAULT_MAX_WAIT_TIME_MS);
        const deadline = Date.now() + maxWaitTimeMs;

        let latestStatus = createdTask.status;
        let latestErrorMessage = "";
        let remoteVideoUrl = "";
        let remoteContentType = "";
        let expiresAt = "";
        let remoteDuration = createdTask.duration;
        let remoteRespectModeration = "";

        while (Date.now() <= deadline) {
            const statusResult = await queryVideoTaskStatus(createdTask.request_id);
            latestStatus = statusResult.status;
            latestErrorMessage = statusResult.error_message;
            remoteContentType = statusResult.content_type;
            expiresAt = statusResult.expires_at;
            remoteDuration = statusResult.duration;
            remoteRespectModeration = statusResult.respect_moderation;

            const normalizedStatus = normalizeVideoTaskStatus(statusResult.status);
            if (normalizedStatus === "completed") {
                remoteVideoUrl = statusResult.video_url;
                if (!remoteVideoUrl) {
                    throw new Error("视频任务已完成，但响应中未找到 video.url。");
                }
                break;
            }

            if (normalizedStatus === "failed") {
                throw new Error(`视频生成失败或过期: ${latestErrorMessage || latestStatus || "接口未返回失败原因"}`);
            }

            await Tools.System.sleep(pollIntervalMs);
        }

        if (!remoteVideoUrl) {
            throw new Error(
                `视频生成超时，最后状态为 ${latestStatus || "未知"}${latestErrorMessage ? `，原因：${latestErrorMessage}` : ""}`
            );
        }

        const extension = guessExtensionFromUrl(remoteVideoUrl, "mp4");
        const baseName = buildFileName(prompt, params.file_name, "xai_video");
        const filePath = `${VIDEOS_DIR}/${baseName}.${extension}`;
        const downloadResult = await Tools.Files.download(remoteVideoUrl, filePath);
        if (!downloadResult.successful) {
            throw new Error(`下载视频失败: ${downloadResult.details}`);
        }

        const fileUri = `file://${filePath}`;
        const markdownLink = `[点击查看生成的视频](${fileUri})`;
        const htmlVideo = `<video controls src="${fileUri}"></video>`;

        const hintLines: string[] = [];
        hintLines.push(`视频已生成并保存在本地 ${VIDEOS_DIR}。`);
        hintLines.push(`本地路径: ${filePath}`);
        hintLines.push("");
        hintLines.push("后续回答如果要给出视频，请优先返回这个本地链接：");
        hintLines.push(markdownLink);
        hintLines.push("");
        hintLines.push("如果当前渲染环境支持 HTML 视频标签，也可以使用：");
        hintLines.push(htmlVideo);

        return {
            prompt,
            model: createdTask.effective_model,
            request_id: createdTask.request_id,
            status: latestStatus,
            input_type: resolvedInputs.input_type,
            aspect_ratio: createdTask.aspect_ratio,
            resolution: createdTask.resolution,
            duration: remoteDuration,
            remote_video_url: remoteVideoUrl,
            remote_content_type: remoteContentType,
            remote_expires_at: expiresAt,
            remote_respect_moderation: remoteRespectModeration,
            file_path: filePath,
            file_uri: fileUri,
            markdown_link: markdownLink,
            html_video: htmlVideo,
            hint: hintLines.join("\n")
        };
    }

    async function draw_image_wrapper(params: {
        prompt: string;
        model?: string;
        size?: string;
        file_name?: string;
    }) {
        try {
            const result = await draw_image(params);
            complete({
                success: true,
                message: "xAI 图片生成成功，已下载到本地。",
                data: result
            });
        } catch (error) {
            console.error("draw_image 执行失败:", error);
            complete({
                success: false,
                message: `xAI 图片生成失败: ${getErrorMessage(error)}`,
                error_stack: getErrorStack(error)
            });
        }
    }

    async function draw_video_wrapper(params: VideoParams) {
        try {
            const result = await draw_video((params || {}) as VideoParams);
            complete({
                success: true,
                message: "xAI 视频生成成功，已下载到本地。",
                data: result
            });
        } catch (error) {
            console.error("draw_video 执行失败:", error);
            complete({
                success: false,
                message: `xAI 视频生成失败: ${getErrorMessage(error)}`,
                error_stack: getErrorStack(error)
            });
        }
    }

    return {
        draw_image: draw_image_wrapper,
        draw_video: draw_video_wrapper
    };
})();

exports.draw_image = xaiDraw.draw_image;
exports.draw_video = xaiDraw.draw_video;
