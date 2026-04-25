/* METADATA
{
    "name": "extended_file_tools",

    "display_name": {
        "zh": "增强文件工具",
        "en": "Extended File Tools"
    },
    "description": {
        "zh": "拓展文件工具包：提供 file_exists / move_file / copy_file / file_info / unzip_files / zip_files / open_file / share_file（默认文件工具中已移除这些项）。",
        "en": "Extended file tools: file_exists / move_file / copy_file / file_info / unzip_files / zip_files / open_file / share_file (removed from default file tools)."
    },
    "category": "File",
    "enabledByDefault": true,
    "tools": [
        {
            "name": "file_exists",
            "description": { "zh": "检查文件或目录是否存在。", "en": "Check if a file or directory exists." },
            "parameters": [
                { "name": "path", "description": { "zh": "目标路径", "en": "Target path" }, "type": "string", "required": true },
                { "name": "environment", "description": { "zh": "可选：android/linux", "en": "Optional: android/linux" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "move_file",
            "description": { "zh": "移动或重命名文件/目录。", "en": "Move or rename a file/directory." },
            "parameters": [
                { "name": "source", "description": { "zh": "源路径", "en": "Source path" }, "type": "string", "required": true },
                { "name": "destination", "description": { "zh": "目标路径", "en": "Destination path" }, "type": "string", "required": true },
                { "name": "environment", "description": { "zh": "可选：android/linux", "en": "Optional: android/linux" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "copy_file",
            "description": { "zh": "复制文件/目录（支持跨环境复制）。", "en": "Copy a file/directory (supports cross-environment copy)." },
            "parameters": [
                { "name": "source", "description": { "zh": "源路径", "en": "Source path" }, "type": "string", "required": true },
                { "name": "destination", "description": { "zh": "目标路径", "en": "Destination path" }, "type": "string", "required": true },
                { "name": "recursive", "description": { "zh": "可选：是否递归，默认 false", "en": "Optional: recursive (default: false)" }, "type": "boolean", "required": false },
                { "name": "source_environment", "description": { "zh": "可选：源环境 android/linux", "en": "Optional: source environment android/linux" }, "type": "string", "required": false },
                { "name": "dest_environment", "description": { "zh": "可选：目标环境 android/linux", "en": "Optional: destination environment android/linux" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "file_info",
            "description": { "zh": "获取文件/目录信息。", "en": "Get file/directory info." },
            "parameters": [
                { "name": "path", "description": { "zh": "目标路径", "en": "Target path" }, "type": "string", "required": true },
                { "name": "environment", "description": { "zh": "可选：android/linux", "en": "Optional: android/linux" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "zip_files",
            "description": { "zh": "压缩文件/目录。", "en": "Zip files/directories." },
            "parameters": [
                { "name": "source", "description": { "zh": "源路径", "en": "Source path" }, "type": "string", "required": true },
                { "name": "destination", "description": { "zh": "输出 zip 文件路径", "en": "Destination zip path" }, "type": "string", "required": true },
                { "name": "environment", "description": { "zh": "可选：android/linux", "en": "Optional: android/linux" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "unzip_files",
            "description": { "zh": "解压 zip 文件。", "en": "Unzip an archive." },
            "parameters": [
                { "name": "source", "description": { "zh": "zip 文件路径", "en": "Zip file path" }, "type": "string", "required": true },
                { "name": "destination", "description": { "zh": "解压目录", "en": "Destination directory" }, "type": "string", "required": true },
                { "name": "environment", "description": { "zh": "可选：android/linux", "en": "Optional: android/linux" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "open_file",
            "description": { "zh": "用系统默认应用打开文件。", "en": "Open a file with system default app." },
            "parameters": [
                { "name": "path", "description": { "zh": "文件路径", "en": "File path" }, "type": "string", "required": true },
                { "name": "environment", "description": { "zh": "可选：android/linux", "en": "Optional: android/linux" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "share_file",
            "description": { "zh": "分享文件给其他应用。", "en": "Share a file with other apps." },
            "parameters": [
                { "name": "path", "description": { "zh": "文件路径", "en": "File path" }, "type": "string", "required": true },
                { "name": "title", "description": { "zh": "可选：分享标题", "en": "Optional: share title" }, "type": "string", "required": false },
                { "name": "environment", "description": { "zh": "可选：android/linux", "en": "Optional: android/linux" }, "type": "string", "required": false }
            ]
        }
    ]
}*/

const ExtendedFileTools = (function () {

    interface ToolResponse {
        success: boolean;
        message: string;
        data?: any;
    }

    type FileEnvironment = 'android' | 'linux';

    async function file_exists(params: { path: string; environment?: FileEnvironment }): Promise<ToolResponse> {
        const result = await Tools.Files.exists(params.path, params.environment);
        return { success: !!result && (result.exists ?? true), message: '检查完成', data: result };
    }

    async function move_file(params: { source: string; destination: string; environment?: FileEnvironment }): Promise<ToolResponse> {
        const result = await Tools.Files.move(params.source, params.destination, params.environment);
        return { success: !!result, message: '移动完成', data: result };
    }

    async function copy_file(params: { source: string; destination: string; recursive?: boolean; source_environment?: FileEnvironment; dest_environment?: FileEnvironment }): Promise<ToolResponse> {
        const result = await Tools.Files.copy(params.source, params.destination, params.recursive, params.source_environment, params.dest_environment);
        return { success: !!result, message: '复制完成', data: result };
    }

    async function file_info(params: { path: string; environment?: FileEnvironment }): Promise<ToolResponse> {
        const result = await Tools.Files.info(params.path, params.environment);
        return { success: !!result, message: '获取信息完成', data: result };
    }

    async function zip_files(params: { source: string; destination: string; environment?: FileEnvironment }): Promise<ToolResponse> {
        const result = await Tools.Files.zip(params.source, params.destination, params.environment);
        return { success: !!result, message: '压缩完成', data: result };
    }

    async function unzip_files(params: { source: string; destination: string; environment?: FileEnvironment }): Promise<ToolResponse> {
        const result = await Tools.Files.unzip(params.source, params.destination, params.environment);
        return { success: !!result, message: '解压完成', data: result };
    }

    async function open_file(params: { path: string; environment?: FileEnvironment }): Promise<ToolResponse> {
        const result = await Tools.Files.open(params.path, params.environment);
        return { success: !!result, message: '打开文件完成', data: result };
    }

    async function share_file(params: { path: string; title?: string; environment?: FileEnvironment }): Promise<ToolResponse> {
        const result = await Tools.Files.share(params.path, params.title, params.environment);
        return { success: !!result, message: '分享文件完成', data: result };
    }

    async function wrapToolExecution<P>(func: (params: P) => Promise<ToolResponse>, params: P) {
        try {
            const result = await func(params);
            complete(result);
        } catch (error: any) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }

    async function main() {
        const results: any[] = [];
        results.push({ tool: 'file_exists', result: { success: null, message: '未测试' } });
        results.push({ tool: 'move_file', result: { success: null, message: '未测试（会移动/重命名文件）' } });
        results.push({ tool: 'copy_file', result: { success: null, message: '未测试（会复制文件）' } });
        results.push({ tool: 'file_info', result: { success: null, message: '未测试' } });
        results.push({ tool: 'zip_files', result: { success: null, message: '未测试（会写入zip文件）' } });
        results.push({ tool: 'unzip_files', result: { success: null, message: '未测试（会写入解压文件）' } });
        results.push({ tool: 'open_file', result: { success: null, message: '未测试（会拉起系统应用）' } });
        results.push({ tool: 'share_file', result: { success: null, message: '未测试（会弹出分享面板）' } });

        complete({
            success: true,
            message: '拓展文件工具包加载完成（未执行破坏性测试）',
            data: { results }
        });
    }

    return {
        file_exists: (params: { path: string; environment?: FileEnvironment }) => wrapToolExecution(file_exists, params),
        move_file: (params: { source: string; destination: string; environment?: FileEnvironment }) => wrapToolExecution(move_file, params),
        copy_file: (params: { source: string; destination: string; recursive?: boolean; source_environment?: FileEnvironment; dest_environment?: FileEnvironment }) => wrapToolExecution(copy_file, params),
        file_info: (params: { path: string; environment?: FileEnvironment }) => wrapToolExecution(file_info, params),
        zip_files: (params: { source: string; destination: string; environment?: FileEnvironment }) => wrapToolExecution(zip_files, params),
        unzip_files: (params: { source: string; destination: string; environment?: FileEnvironment }) => wrapToolExecution(unzip_files, params),
        open_file: (params: { path: string; environment?: FileEnvironment }) => wrapToolExecution(open_file, params),
        share_file: (params: { path: string; title?: string; environment?: FileEnvironment }) => wrapToolExecution(share_file, params),
        main,
    };
})();

exports.file_exists = ExtendedFileTools.file_exists;
exports.move_file = ExtendedFileTools.move_file;
exports.copy_file = ExtendedFileTools.copy_file;
exports.file_info = ExtendedFileTools.file_info;
exports.zip_files = ExtendedFileTools.zip_files;
exports.unzip_files = ExtendedFileTools.unzip_files;
exports.open_file = ExtendedFileTools.open_file;
exports.share_file = ExtendedFileTools.share_file;
exports.main = ExtendedFileTools.main;
