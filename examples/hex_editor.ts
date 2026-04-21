/* METADATA
{
    "name": "hex_editor",
    "display_name": {
        "zh": "十六进制编辑器",
        "en": "Hex Editor"
    },
    "description": {
        "zh": "一个面向二进制文件的轻量十六进制查看与修改工具包，支持查看指定偏移的 hex 窗口、按偏移写入字节序列，以及搜索指定 hex 模式。",
        "en": "A lightweight binary hex inspection and patching toolkit. It can read hex windows at offsets, write byte sequences at offsets, and search for hex patterns."
    },
    "enabledByDefault": false,
    "category": "File",
    "tools": [
        {
            "name": "read_hex_window",
            "description": {
                "zh": "读取二进制文件在指定偏移附近的十六进制窗口，并附带 ASCII 预览，便于快速定位和人工核对。",
                "en": "Read a hex window from a binary file at a specific offset, including an ASCII preview for quick inspection."
            },
            "parameters": [
                { "name": "file_path", "description": { "zh": "目标文件路径。", "en": "Target file path." }, "type": "string", "required": true },
                { "name": "offset", "description": { "zh": "起始偏移，可写十进制或 0x 开头的十六进制。", "en": "Start offset in decimal or 0x-prefixed hexadecimal." }, "type": "string", "required": true },
                { "name": "length", "description": { "zh": "读取字节数，默认 128，最大 4096。", "en": "Number of bytes to read. Default 128, max 4096." }, "type": "number", "required": false },
                { "name": "environment", "description": { "zh": "文件环境：android 或 linux，默认 android。", "en": "File environment: android or linux. Defaults to android." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "write_hex_bytes",
            "description": {
                "zh": "把给定的 hex 字节序列直接写入文件指定偏移。该工具会原地修改文件，不会自动生成回退文件。",
                "en": "Write a hex byte sequence directly into a file at the specified offset. This patches the file in place and does not create rollback files."
            },
            "parameters": [
                { "name": "file_path", "description": { "zh": "目标文件路径。", "en": "Target file path." }, "type": "string", "required": true },
                { "name": "offset", "description": { "zh": "写入起始偏移，可写十进制或 0x 开头的十六进制。", "en": "Write offset in decimal or 0x-prefixed hexadecimal." }, "type": "string", "required": true },
                { "name": "hex_bytes", "description": { "zh": "要写入的 hex 字节序列，例如 'DE AD BE EF' 或 'deadbeef'。", "en": "Hex bytes to write, for example 'DE AD BE EF' or 'deadbeef'." }, "type": "string", "required": true },
                { "name": "environment", "description": { "zh": "文件环境：android 或 linux，默认 android。", "en": "File environment: android or linux. Defaults to android." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "find_hex_pattern",
            "description": {
                "zh": "在二进制文件中搜索指定的 hex 模式，返回命中偏移以及每个命中点附近的十六进制窗口。",
                "en": "Search a binary file for a hex pattern and return matched offsets with nearby hex windows."
            },
            "parameters": [
                { "name": "file_path", "description": { "zh": "目标文件路径。", "en": "Target file path." }, "type": "string", "required": true },
                { "name": "hex_bytes", "description": { "zh": "要搜索的 hex 模式，例如 '50 4B 03 04'。", "en": "Hex pattern to search for, for example '50 4B 03 04'." }, "type": "string", "required": true },
                { "name": "max_results", "description": { "zh": "最多返回多少个命中，默认 20，最大 100。", "en": "Maximum number of matches to return. Default 20, max 100." }, "type": "number", "required": false },
                { "name": "environment", "description": { "zh": "文件环境：android 或 linux，默认 android。", "en": "File environment: android or linux. Defaults to android." }, "type": "string", "required": false }
            ]
        }
    ]
}*/

type FileEnvironment = "android" | "linux";

interface ReadHexWindowParams {
    file_path: string;
    offset: string;
    length?: number;
    environment?: string;
}

interface WriteHexBytesParams {
    file_path: string;
    offset: string;
    hex_bytes: string;
    environment?: string;
}

interface FindHexPatternParams {
    file_path: string;
    hex_bytes: string;
    max_results?: number;
    environment?: string;
}

const hexEditorPackage = (function () {
    const BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    const DEFAULT_WINDOW_LENGTH = 128;
    const MAX_WINDOW_LENGTH = 4096;
    const DEFAULT_MAX_RESULTS = 20;
    const MAX_RESULTS_LIMIT = 100;
    const CONTEXT_BEFORE = 16;
    const CONTEXT_TOTAL = 64;
    const BYTES_PER_LINE = 16;

    function normalizeEnvironment(environment?: string): FileEnvironment {
        const value = String(environment || "android").trim().toLowerCase();
        if (value !== "android" && value !== "linux") {
            throw new Error("environment 只能是 android 或 linux。");
        }
        return value as FileEnvironment;
    }

    function normalizeBase64Input(value: string): string {
        const cleaned = String(value || "")
            .replace(/\s+/g, "")
            .replace(/-/g, "+")
            .replace(/_/g, "/");
        if (!cleaned) {
            return "";
        }
        const remainder = cleaned.length % 4;
        if (remainder === 1) {
            throw new Error("Invalid base64 string");
        }
        return remainder === 0 ? cleaned : cleaned + "=".repeat(4 - remainder);
    }

    function decodeBase64ToBytes(value: string): number[] {
        const normalized = normalizeBase64Input(value);
        const bytes: number[] = [];

        for (let i = 0; i < normalized.length; i += 4) {
            const c1 = normalized[i];
            const c2 = normalized[i + 1];
            const c3 = normalized[i + 2];
            const c4 = normalized[i + 3];

            const v1 = BASE64_ALPHABET.indexOf(c1);
            const v2 = BASE64_ALPHABET.indexOf(c2);
            const v3 = c3 === "=" ? 0 : BASE64_ALPHABET.indexOf(c3);
            const v4 = c4 === "=" ? 0 : BASE64_ALPHABET.indexOf(c4);

            if (v1 < 0 || v2 < 0 || (c3 !== "=" && v3 < 0) || (c4 !== "=" && v4 < 0)) {
                throw new Error("Invalid base64 string");
            }

            const chunk = (v1 << 18) | (v2 << 12) | (v3 << 6) | v4;
            bytes.push((chunk >> 16) & 0xff);
            if (c3 !== "=") {
                bytes.push((chunk >> 8) & 0xff);
            }
            if (c4 !== "=") {
                bytes.push(chunk & 0xff);
            }
        }

        return bytes;
    }

    function encodeBytesToBase64(bytes: number[]): string {
        let output = "";

        for (let i = 0; i < bytes.length; i += 3) {
            const b1 = bytes[i] ?? 0;
            const hasB2 = i + 1 < bytes.length;
            const hasB3 = i + 2 < bytes.length;
            const b2 = hasB2 ? (bytes[i + 1] ?? 0) : 0;
            const b3 = hasB3 ? (bytes[i + 2] ?? 0) : 0;
            const chunk = (b1 << 16) | (b2 << 8) | b3;

            output += BASE64_ALPHABET[(chunk >> 18) & 0x3f];
            output += BASE64_ALPHABET[(chunk >> 12) & 0x3f];
            output += hasB2 ? BASE64_ALPHABET[(chunk >> 6) & 0x3f] : "=";
            output += hasB3 ? BASE64_ALPHABET[chunk & 0x3f] : "=";
        }

        return output;
    }

    function parseOffset(value: string): number {
        const trimmed = String(value || "").trim().toLowerCase();
        if (!trimmed) {
            throw new Error("offset 不能为空。");
        }

        let parsed = NaN;
        if (/^0x[0-9a-f]+$/.test(trimmed)) {
            parsed = parseInt(trimmed.slice(2), 16);
        } else if (/^\d+$/.test(trimmed)) {
            parsed = parseInt(trimmed, 10);
        } else {
            throw new Error("offset 必须是十进制数字或 0x 开头的十六进制。");
        }

        if (!Number.isFinite(parsed) || parsed < 0) {
            throw new Error("offset 必须是大于等于 0 的整数。");
        }
        return parsed;
    }

    function clampPositiveInteger(value: number | undefined, defaultValue: number, maxValue: number, fieldName: string): number {
        const actual = value == null ? defaultValue : Math.floor(value);
        if (!Number.isFinite(actual) || actual <= 0) {
            throw new Error(`${fieldName} 必须是大于 0 的整数。`);
        }
        return Math.min(actual, maxValue);
    }

    function normalizeHexBytesInput(value: string): string {
        const trimmed = String(value || "").trim();
        if (!trimmed) {
            throw new Error("hex_bytes 不能为空。");
        }
        const withoutPrefix = trimmed.replace(/^0x/i, "");
        const normalized = withoutPrefix.replace(/[^0-9a-fA-F]/g, "").toLowerCase();
        if (!normalized) {
            throw new Error("hex_bytes 中没有可用的十六进制字符。");
        }
        if (normalized.length % 2 !== 0) {
            throw new Error("hex_bytes 必须包含偶数个十六进制字符。");
        }
        return normalized;
    }

    function parseHexBytes(value: string): number[] {
        const normalized = normalizeHexBytesInput(value);
        const bytes: number[] = [];
        for (let i = 0; i < normalized.length; i += 2) {
            bytes.push(parseInt(normalized.slice(i, i + 2), 16));
        }
        return bytes;
    }

    function toHex(value: number, width: number): string {
        let hex = Math.max(0, Number(value) || 0).toString(16).toUpperCase();
        while (hex.length < width) {
            hex = `0${hex}`;
        }
        return hex;
    }

    function bytesToHexString(bytes: number[]): string {
        return bytes.map((byte) => toHex(byte, 2)).join(" ");
    }

    function bytesToAscii(bytes: number[]): string {
        return bytes
            .map((byte) => {
                if (byte >= 32 && byte <= 126) {
                    return String.fromCharCode(byte);
                }
                return ".";
            })
            .join("");
    }

    function buildHexDump(bytes: number[], startOffset: number): string {
        if (bytes.length === 0) {
            return "";
        }

        const lines: string[] = [];
        const offsetWidth = Math.max(8, (startOffset + bytes.length).toString(16).length);

        for (let index = 0; index < bytes.length; index += BYTES_PER_LINE) {
            const slice = bytes.slice(index, index + BYTES_PER_LINE);
            const hexCells = slice.map((byte) => toHex(byte, 2));
            while (hexCells.length < BYTES_PER_LINE) {
                hexCells.push("  ");
            }
            const left = hexCells.slice(0, 8).join(" ");
            const right = hexCells.slice(8).join(" ");
            const ascii = bytesToAscii(slice).padEnd(BYTES_PER_LINE, " ");
            lines.push(`${toHex(startOffset + index, offsetWidth)}  ${left}  ${right}  |${ascii}|`);
        }

        return lines.join("\n");
    }

    async function ensureFileExists(path: string, environment: FileEnvironment): Promise<void> {
        const exists = await Tools.Files.exists(path, environment);
        if (!exists.exists) {
            throw new Error(`文件不存在: ${path}`);
        }
        if (exists.isDirectory) {
            throw new Error(`目标路径是目录，不是文件: ${path}`);
        }
    }

    async function readFileBytes(path: string, environment: FileEnvironment): Promise<{ base64: string; bytes: number[]; size: number }> {
        await ensureFileExists(path, environment);
        const result = await Tools.Files.readBinary(path, environment);
        const base64 = String(result.contentBase64 || "");
        return {
            base64,
            bytes: decodeBase64ToBytes(base64),
            size: Number(result.size || 0)
        };
    }

    async function writeFileBytes(path: string, bytes: number[], environment: FileEnvironment) {
        return await Tools.Files.writeBinary(path, encodeBytesToBase64(bytes), environment);
    }

    function collectWindow(bytes: number[], offset: number, length: number): { window: number[]; actualLength: number } {
        if (offset > bytes.length) {
            throw new Error(`offset 超出文件范围。文件大小为 ${bytes.length} 字节。`);
        }
        const end = Math.min(bytes.length, offset + length);
        const window = bytes.slice(offset, end);
        return {
            window,
            actualLength: window.length
        };
    }

    async function read_hex_window(params: ReadHexWindowParams) {
        const environment = normalizeEnvironment(params.environment);
        const offset = parseOffset(params.offset);
        const length = clampPositiveInteger(params.length, DEFAULT_WINDOW_LENGTH, MAX_WINDOW_LENGTH, "length");
        const file = await readFileBytes(params.file_path, environment);
        const { window, actualLength } = collectWindow(file.bytes, offset, length);

        return {
            file_path: params.file_path,
            environment,
            file_size: file.size,
            offset_decimal: offset,
            offset_hex: `0x${toHex(offset, 8)}`,
            requested_length: length,
            actual_length: actualLength,
            end_offset_exclusive_decimal: offset + actualLength,
            end_offset_exclusive_hex: `0x${toHex(offset + actualLength, 8)}`,
            hex: bytesToHexString(window),
            ascii_preview: bytesToAscii(window),
            hex_dump: buildHexDump(window, offset)
        };
    }

    async function write_hex_bytes(params: WriteHexBytesParams) {
        const environment = normalizeEnvironment(params.environment);
        const offset = parseOffset(params.offset);
        const patchBytes = parseHexBytes(params.hex_bytes);
        const file = await readFileBytes(params.file_path, environment);

        if (offset + patchBytes.length > file.bytes.length) {
            throw new Error(`写入范围超出文件大小。文件大小 ${file.bytes.length} 字节，尝试写入到 ${offset + patchBytes.length}。`);
        }

        const beforeBytes = file.bytes.slice(offset, offset + patchBytes.length);
        for (let i = 0; i < patchBytes.length; i += 1) {
            file.bytes[offset + i] = patchBytes[i];
        }

        const writeResult = await writeFileBytes(params.file_path, file.bytes, environment);
        const afterBytes = file.bytes.slice(offset, offset + patchBytes.length);

        return {
            file_path: params.file_path,
            environment,
            offset_decimal: offset,
            offset_hex: `0x${toHex(offset, 8)}`,
            bytes_written: patchBytes.length,
            before_hex: bytesToHexString(beforeBytes),
            after_hex: bytesToHexString(afterBytes),
            write_result: writeResult
        };
    }

    async function find_hex_pattern(params: FindHexPatternParams) {
        const environment = normalizeEnvironment(params.environment);
        const patternBytes = parseHexBytes(params.hex_bytes);
        const maxResults = clampPositiveInteger(params.max_results, DEFAULT_MAX_RESULTS, MAX_RESULTS_LIMIT, "max_results");
        const file = await readFileBytes(params.file_path, environment);
        const matches: Array<{
            offset_decimal: number;
            offset_hex: string;
            matched_hex: string;
            context_hex_dump: string;
        }> = [];

        if (patternBytes.length > file.bytes.length) {
            return {
                file_path: params.file_path,
                environment,
                file_size: file.size,
                pattern_hex: bytesToHexString(patternBytes),
                match_count: 0,
                matches
            };
        }

        for (let offset = 0; offset <= file.bytes.length - patternBytes.length; offset += 1) {
            let matched = true;
            for (let index = 0; index < patternBytes.length; index += 1) {
                if (file.bytes[offset + index] !== patternBytes[index]) {
                    matched = false;
                    break;
                }
            }
            if (!matched) {
                continue;
            }

            const contextStart = Math.max(0, offset - CONTEXT_BEFORE);
            const contextEnd = Math.min(file.bytes.length, contextStart + CONTEXT_TOTAL);
            const contextBytes = file.bytes.slice(contextStart, contextEnd);

            matches.push({
                offset_decimal: offset,
                offset_hex: `0x${toHex(offset, 8)}`,
                matched_hex: bytesToHexString(patternBytes),
                context_hex_dump: buildHexDump(contextBytes, contextStart)
            });

            if (matches.length >= maxResults) {
                break;
            }
        }

        return {
            file_path: params.file_path,
            environment,
            file_size: file.size,
            pattern_hex: bytesToHexString(patternBytes),
            match_count: matches.length,
            matches
        };
    }

    async function wrap(fn: (params: any) => Promise<any>, params: any, successMessage: string, failureMessage: string) {
        try {
            const data = await fn(params);
            complete({
                success: true,
                message: successMessage,
                data
            });
        } catch (error: any) {
            console.error(`[hex_editor] ${fn.name} failed:`, error);
            complete({
                success: false,
                message: `${failureMessage}: ${error.message}`,
                error_stack: error.stack
            });
        }
    }

    return {
        read_hex_window: (params: ReadHexWindowParams) => wrap(read_hex_window, params, "Hex 窗口读取成功。", "Hex 窗口读取失败"),
        write_hex_bytes: (params: WriteHexBytesParams) => wrap(write_hex_bytes, params, "Hex 字节写入成功。", "Hex 字节写入失败"),
        find_hex_pattern: (params: FindHexPatternParams) => wrap(find_hex_pattern, params, "Hex 模式搜索成功。", "Hex 模式搜索失败")
    };
})();

exports.read_hex_window = hexEditorPackage.read_hex_window;
exports.write_hex_bytes = hexEditorPackage.write_hex_bytes;
exports.find_hex_pattern = hexEditorPackage.find_hex_pattern;
