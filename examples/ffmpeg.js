/* METADATA
{
    "name": "ffmpeg",

    "display_name": {
        "zh": "FFmpeg 工具集",
        "en": "FFmpeg Toolkit"
    },
    "description": {
        "zh": "提供FFmpeg工具，用于处理多媒体内容。",
        "en": "FFmpeg utilities for processing multimedia content."
    },
    "enabledByDefault": true,
    "category": "Media",
    "tools": [
        {
            "name": "ffmpeg_execute",
            "description": { "zh": "执行自定义FFmpeg命令（仅填写参数，不要包含前缀 ffmpeg）。", "en": "Execute a custom FFmpeg command (arguments only; do not include the leading ffmpeg)." },
            "parameters": [
                { "name": "command", "description": { "zh": "要执行的FFmpeg命令参数（不要包含前缀 ffmpeg）", "en": "FFmpeg command arguments to execute (do not include the leading ffmpeg)" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "ffmpeg_info",
            "description": { "zh": "获取FFmpeg系统信息，包括版本、构建配置和支持的编解码器。", "en": "Get FFmpeg system info, including version, build config, and supported codecs." },
            "parameters": []
        },
        {
            "name": "ffmpeg_convert",
            "description": { "zh": "使用简化参数转换视频文件。", "en": "Convert a video file using simplified parameters." },
            "parameters": [
                { "name": "input_path", "description": { "zh": "源视频文件路径", "en": "Input video file path" }, "type": "string", "required": true },
                { "name": "output_path", "description": { "zh": "目标视频文件路径", "en": "Output video file path" }, "type": "string", "required": true },
                { "name": "video_codec", "description": { "zh": "可选，要使用的视频编解码器。推荐使用 'h264'。支持的值: 'h264', 'hevc', 'vp8', 'vp9', 'av1', 'libx265', 'libvpx', 'libaom', 'mpeg4', 'mjpeg', 'prores'。", "en": "Optional. Video codec to use. Prefer 'h264'. Supported values: 'h264', 'hevc', 'vp8', 'vp9', 'av1', 'libx265', 'libvpx', 'libaom', 'mpeg4', 'mjpeg', 'prores'." }, "type": "string", "required": false },
                { "name": "audio_codec", "description": { "zh": "可选，要使用的音频编解码器。支持的值: 'aac', 'mp3', 'opus', 'vorbis', 'flac', 'pcm', 'wav', 'ac3', 'eac3'。", "en": "Optional. Audio codec to use. Supported values: 'aac', 'mp3', 'opus', 'vorbis', 'flac', 'pcm', 'wav', 'ac3', 'eac3'." }, "type": "string", "required": false },
                { "name": "resolution", "description": { "zh": "可选，输出分辨率，例如 '1280x720'。", "en": "Optional. Output resolution, e.g. '1280x720'." }, "type": "string", "required": false },
                { "name": "bitrate", "description": { "zh": "可选，视频比特率，例如 '1000k'。", "en": "Optional. Video bitrate, e.g. '1000k'." }, "type": "string", "required": false }
            ]
        }
    ]
}*/
const FFmpegTools = (function () {
    async function ffmpeg_execute(params) {
        const result = await Tools.FFmpeg.execute(params.command);
        return {
            success: result.returnCode === 0,
            message: result.returnCode === 0 ? "FFmpeg command executed successfully." : `FFmpeg command failed with return code ${result.returnCode}.`,
            data: result.output
        };
    }
    async function ffmpeg_info() {
        const result = await Tools.FFmpeg.info();
        return {
            success: result.returnCode === 0,
            message: result.returnCode === 0 ? "FFmpeg info retrieved successfully." : "Failed to retrieve FFmpeg info.",
            data: result.output
        };
    }
    async function ffmpeg_convert(params) {
        const result = await Tools.FFmpeg.convert(params.input_path, params.output_path, {
            video_codec: params.video_codec,
            audio_codec: params.audio_codec,
            resolution: params.resolution,
            bitrate: params.bitrate,
        });
        return {
            success: result.returnCode === 0,
            message: result.returnCode === 0 ? "FFmpeg conversion completed successfully." : `FFmpeg conversion failed with return code ${result.returnCode}.`,
            data: result.output
        };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }
    async function main() {
        console.log("--- FFmpeg Tools Test ---");
        console.log("\n[1/3] Testing ffmpeg_info...");
        const infoResult = await ffmpeg_info();
        console.log(JSON.stringify(infoResult, null, 2));
        console.log("\n[2/3] Testing ffmpeg_execute (example: getting help for a decoder)...");
        const executeResult = await ffmpeg_execute({ command: '-h decoder=h264' });
        console.log(JSON.stringify(executeResult, null, 2));
        console.log("\n[3/3] Testing ffmpeg_convert (example)...");
        console.log("Skipping ffmpeg_convert test as it requires input files.");
        complete({ success: true, message: "Test finished." });
    }
    return {
        ffmpeg_execute: (params) => wrapToolExecution(ffmpeg_execute, params),
        ffmpeg_info: (params) => wrapToolExecution(ffmpeg_info, params),
        ffmpeg_convert: (params) => wrapToolExecution(ffmpeg_convert, params),
        main,
    };
})();
exports.ffmpeg_execute = FFmpegTools.ffmpeg_execute;
exports.ffmpeg_info = FFmpegTools.ffmpeg_info;
exports.ffmpeg_convert = FFmpegTools.ffmpeg_convert;
exports.main = FFmpegTools.main;
