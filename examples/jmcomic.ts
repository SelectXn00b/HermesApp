/*
METADATA
{
    "name": "jmcomic_downloader",

    "display_name": {
        "zh": "JMComic 下载器",
        "en": "JMComic Downloader"
    },
    "description": { "zh": "提供JMComic漫画下载功能，支持搜索、获取信息和下载，包括对新漫画的图片反爬解码。", "en": "JMComic downloader: search comics, fetch details, and download albums. Includes anti-crawling image decoding for newer comics." },
    "category": "Media",
    "tools": [
        {
            "name": "main",
            "description": { "zh": "运行一个内置的测试函数，以验证JMComic工具的基本功能（搜索和获取信息）是否正常工作。", "en": "Run a built-in test to verify basic JMComic functionality (search and info retrieval)." },
            "parameters": []
        },
        {
            "name": "search_comics",
            "description": { "zh": "搜索JMComic漫画", "en": "Search JMComic comics." },
            "parameters": [
                {
                    "name": "query",
                    "description": { "zh": "搜索关键词", "en": "Search keyword." },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "page",
                    "description": { "zh": "页码 (默认: 1)", "en": "Page number (default: 1)." },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "order_by",
                    "description": { "zh": "排序方式 (latest, view, picture, like, 默认: view)", "en": "Sort mode: latest/view/picture/like (default: view)." },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "time",
                    "description": { "zh": "时间范围 (today, week, month, all, 默认: all)", "en": "Time range: today/week/month/all (default: all)." },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "get_album_info",
            "description": { "zh": "获取漫画（本子）的详细信息", "en": "Get detailed information for a comic album." },
            "parameters": [
                {
                    "name": "album_id",
                    "description": { "zh": "漫画ID", "en": "Album ID." },
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "download_album",
            "description": { "zh": "下载指定ID的单本漫画，包含图片解码功能。", "en": "Download a single comic album by ID, including image decoding." },
            "parameters": [
                {
                    "name": "album_id",
                    "description": { "zh": "要下载的漫画ID", "en": "Album ID to download." },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "download_dir",
                    "description": { "zh": "下载目录 (可选, 默认: /sdcard/Download/OperitScripts)", "en": "Download directory (optional; default: /sdcard/Download/OperitScripts)." },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "batch_download_albums",
            "description": { "zh": "批量下载多本漫画，包含图片解码功能。", "en": "Batch download multiple comic albums, including image decoding." },
            "parameters": [
                {
                    "name": "album_ids",
                    "description": { "zh": "要下载的漫画ID列表，用逗号分隔", "en": "Comma-separated list of album IDs to download." },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "download_dir",
                    "description": { "zh": "下载目录 (可选, 默认: /sdcard/Download/OperitScripts)", "en": "Download directory (optional; default: /sdcard/Download/OperitScripts)." },
                    "type": "string",
                    "required": false
                }
            ]
        }
    ],
    "enabledByDefault": false
}*/

// region Type Definitions
interface Album {
    id: string;
    title: string;
    author: string;
    scrambleId: number;
    episodeList: { id: string; title: string }[];
    length: number;
}

interface Photo {
    id: string;
    title: string;
    albumId: string;
    scrambleId: number;
    pageArr: string[];
    length: number;
}

interface SearchResultItem {
    id: string;
    title: string;
}

interface SearchResult {
    search_params: SearchParams;
    results: SearchResultItem[];
    total_results: number;
}

interface SearchParams {
    query: string;
    page?: number;
    order_by?: 'latest' | 'view' | 'picture' | 'like';
    time?: 'today' | 'week' | 'month' | 'all';
}

interface AlbumInfo {
    id: string;
    title: string;
    author: string;
    chapterCount: number;
    success: boolean;
}

interface DownloadResult {
    success: boolean;
    albumId: string;
    title?: string;
    downloadedFiles?: {
        directory: string | undefined;
        fileCount: number;
        files: string[];
    };
    error?: string;
}

interface BatchDownloadResult {
    success: boolean;
    albumId: string;
    title?: string;
    error?: string;
}

// endregion

const jmcomic = (function () {

    // region Polyfill & Utils
    const BASE64_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

    function normalizeBase64Input(value: string): string {
        const cleaned = String(value || '')
            .replace(/\s+/g, '')
            .replace(/-/g, '+')
            .replace(/_/g, '/');
        if (!cleaned) {
            return '';
        }
        const remainder = cleaned.length % 4;
        if (remainder === 1) {
            throw new Error('Invalid base64 string');
        }
        return remainder === 0 ? cleaned : cleaned + '='.repeat(4 - remainder);
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
            const v3 = c3 === '=' ? 0 : BASE64_ALPHABET.indexOf(c3);
            const v4 = c4 === '=' ? 0 : BASE64_ALPHABET.indexOf(c4);

            if (v1 < 0 || v2 < 0 || (c3 !== '=' && v3 < 0) || (c4 !== '=' && v4 < 0)) {
                throw new Error('Invalid base64 string');
            }

            const chunk = (v1 << 18) | (v2 << 12) | (v3 << 6) | v4;
            bytes.push((chunk >> 16) & 0xff);
            if (c3 !== '=') {
                bytes.push((chunk >> 8) & 0xff);
            }
            if (c4 !== '=') {
                bytes.push(chunk & 0xff);
            }
        }

        return bytes;
    }

    function encodeBytesToBase64(bytes: number[]): string {
        let output = '';

        for (let i = 0; i < bytes.length; i += 3) {
            const b1 = bytes[i];
            const hasB2 = i + 1 < bytes.length;
            const hasB3 = i + 2 < bytes.length;
            const b2 = hasB2 ? bytes[i + 1] : 0;
            const b3 = hasB3 ? bytes[i + 2] : 0;
            const chunk = (b1 << 16) | (b2 << 8) | b3;

            output += BASE64_ALPHABET[(chunk >> 18) & 0x3f];
            output += BASE64_ALPHABET[(chunk >> 12) & 0x3f];
            output += hasB2 ? BASE64_ALPHABET[(chunk >> 6) & 0x3f] : '=';
            output += hasB3 ? BASE64_ALPHABET[chunk & 0x3f] : '=';
        }

        return output;
    }

    function decodeBase64Binary(value: string): string {
        return decodeBase64ToBytes(value)
            .map((byte) => String.fromCharCode(byte))
            .join('');
    }

    function encodeBase64Binary(value: string): string {
        const bytes: number[] = [];
        const input = String(value || '');
        for (let i = 0; i < input.length; i++) {
            bytes.push(input.charCodeAt(i) & 0xff);
        }
        return encodeBytesToBase64(bytes);
    }

    // Buffer a subset of Buffer functionality for base64 encoding/decoding
    const Buffer = {
        from: (str: string, encoding: string = 'utf8') => {
            if (encoding === 'base64') {
                return decodeBase64Binary(str);
            } else if (encoding === 'hex') {
                let s = '';
                for (let i = 0; i < str.length; i += 2) {
                    s += String.fromCharCode(parseInt(str.substr(i, 2), 16));
                }
                return s;
            }
            return str;
        },
        toString: (buf: string, encoding: string = 'utf8') => {
            if (encoding === 'base64') {
                return encodeBase64Binary(buf);
            } else if (encoding === 'hex') {
                let s = '';
                for (let i = 0; i < buf.length; i++) {
                    s += ('0' + buf.charCodeAt(i).toString(16)).slice(-2);
                }
                return s;
            }
            return buf;
        }
    };

    function joinPath(...segments: string[]): string {
        return segments.join('/').replace(/\/+/g, '/');
    }

    function dirname(filePath: string): string {
        const lastSlashPos = filePath.lastIndexOf('/');
        if (lastSlashPos === -1) {
            return ".";
        }
        if (lastSlashPos === 0) {
            return "/";
        }
        return filePath.substring(0, lastSlashPos);
    }

    async function ensureDirExists(dirPath: string): Promise<void> {
        if (!dirPath || dirPath === '/' || dirPath === '.') {
            return;
        }

        const dirExists = await Tools.Files.exists(dirPath);
        if (dirExists.exists) {
            return;
        }

        const parentDir = dirname(dirPath);
        await ensureDirExists(parentDir);

        const dirStillNotExists = await Tools.Files.exists(dirPath);
        if (!dirStillNotExists.exists) {
            await Tools.Files.mkdir(dirPath);
        }
    }

    function basename(filePath: string): string {
        return filePath.substring(filePath.lastIndexOf('/') + 1);
    }

    async function runTasksWithConcurrency<T>(tasks: (() => Promise<T>)[], limit: number): Promise<T[]> {
        const results: (T | Error)[] = new Array(tasks.length);
        let currentIndex = 0;

        async function runner(): Promise<void> {
            while (currentIndex < tasks.length) {
                const taskIndex = currentIndex++;
                if (taskIndex < tasks.length) {
                    try {
                        results[taskIndex] = await tasks[taskIndex]();
                    } catch (e: any) {
                        console.error(`并发任务 ${taskIndex} 执行失败: ${e.message}`);
                        results[taskIndex] = e;
                    }
                }
            }
        }

        const runners: Promise<void>[] = [];
        const numRunners = Math.min(limit, tasks.length);
        for (let i = 0; i < numRunners; i++) {
            runners.push(runner());
        }

        await Promise.all(runners);
        return results.filter(r => !(r instanceof Error)) as T[];
    }

    // endregion

    // region Constants and Classes from jmcomic
    const __version__ = '2.6.4-ts-adapted';

    function shuffleDomains(domains: string[]): string[] {
        const shuffled = [...domains];
        for (let i = shuffled.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
        }
        return shuffled;
    }

    const JmMagicConstants = {
        APP_TOKEN_SECRET: '18comicAPP',
        APP_TOKEN_SECRET_2: '18comicAPPContent',
        APP_DATA_SECRET: '185Hcomic3PAPP7R',
        APP_VERSION: '1.8.0',
        SCRAMBLE_220980: 220980,
        SCRAMBLE_268850: 268850,
        SCRAMBLE_421926: 421926
    };

    const JmModuleConfig = {
        PROT: 'https://',
        DOMAIN_API_LIST: shuffleDomains([
            'www.cdnmhwscc.vip',
            'www.cdnplaystation6.club',
            'www.cdnplaystation6.org',
            'www.cdnuc.vip',
            'www.cdn-mspjmapiproxy.xyz'
        ]),
        DOMAIN_IMAGE_LIST: shuffleDomains([
            'cdn-msp.jmapiproxy1.cc',
            'cdn-msp.jmapiproxy2.cc',
            'cdn-msp2.jmapiproxy2.cc',
            'cdn-msp3.jmapiproxy2.cc'
        ]),
        APP_HEADERS_TEMPLATE: {
            'Accept-Language': 'zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7',
            'X-Requested-With': 'com.jiaohua_browser',
            'user-agent': 'Mozilla/5.0 (Linux; Android 9; V1938CT Build/PQ3A.190705.11211812; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36',
        },
        APP_HEADERS_IMAGE: {
            'Accept': 'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8',
            'Accept-Language': 'zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7',
            'X-Requested-With': 'com.jiaohua_browser',
        }
    };

    class JmImageTool {
        static getNum(scrambleId: number | string, photoId: number | string, imageName: string): number {
            const scrambleIdNum = parseInt(scrambleId.toString());
            const photoIdNum = parseInt(photoId.toString());

            if (photoIdNum < scrambleIdNum) {
                return 0;
            } else if (photoIdNum < JmMagicConstants.SCRAMBLE_268850) {
                return 10;
            } else {
                const x = photoIdNum < JmMagicConstants.SCRAMBLE_421926 ? 10 : 8;

                const imageNameWithoutExt = this.getFileNameFromUrl(imageName, true);
                const s = `${photoIdNum}${imageNameWithoutExt}`;

                const hash = CryptoJS.MD5(s).toString();
                const lastChar = hash.charCodeAt(hash.length - 1);
                const num = lastChar % x;
                return (num * 2) + 2;
            }
        }

        static getFileNameFromUrl(url: string, withoutExtension: boolean = true): string {
            const queryIndex = url.indexOf('?');
            if (queryIndex !== -1) {
                url = url.substring(0, queryIndex);
            }
            const filename = basename(url);
            if (withoutExtension) {
                const lastDotIndex = filename.lastIndexOf('.');
                return lastDotIndex !== -1 ? filename.substring(0, lastDotIndex) : filename;
            }
            return filename;
        }

        static async decodeAndSave(num: number, imageBase64: string, decodedSavePath: string): Promise<void> {
            if (num === 0) {
                await Tools.Files.writeBinary(decodedSavePath, imageBase64);
                return;
            }

            let srcImage: Jimp.JimpWrapper | undefined = undefined;
            let resultImage: Jimp.JimpWrapper | undefined = undefined;
            const stripsToRelease: Jimp.JimpWrapper[] = [];

            try {
                srcImage = await Jimp.read(imageBase64);
                const w = await srcImage.getWidth();
                const h = await srcImage.getHeight();
                const over = h % num;

                resultImage = await Jimp.create(w, h);

                for (let i = 0; i < num; i++) {
                    let move = Math.floor(h / num);
                    let ySrc = h - (move * (i + 1)) - over;
                    let yDst = move * i;

                    if (i === 0) {
                        move += over;
                    } else {
                        yDst += over;
                    }

                    if (ySrc < 0 || move <= 0 || (ySrc + move > h)) continue;

                    const strip = await srcImage.crop(0, ySrc, w, move);
                    stripsToRelease.push(strip);
                    await resultImage.composite(strip, 0, yDst);
                }

                const decodedImageBase64 = await resultImage.getBase64(Jimp.MIME_JPEG);

                // 移除 "data:image/jpeg;base64," 前缀
                const pureBase64 = decodedImageBase64.substring(decodedImageBase64.indexOf(',') + 1);

                await Tools.Files.writeBinary(decodedSavePath, pureBase64);
            } catch (e: any) {
                console.error(`图片解码失败，将保存原始图片: ${e.message}`);
                await Tools.Files.writeBinary(decodedSavePath, imageBase64);
            } finally {
                if (srcImage) await srcImage.release();
                if (resultImage) await resultImage.release();
                for (const strip of stripsToRelease) {
                    await strip.release();
                }
            }
        }
    }

    class JmCryptoTool {
        static md5hex(key: string): string {
            return CryptoJS.MD5(key).toString();
        }

        static tokenAndTokenparam(ts: number, secret: string = JmMagicConstants.APP_TOKEN_SECRET): [string, string] {
            const tokenparam = `${ts},${JmMagicConstants.APP_VERSION}`;
            const token = this.md5hex(`${ts}${secret}`);
            return [token, tokenparam];
        }

        static decodeRespData(data: string, ts: number, secret: string = JmMagicConstants.APP_DATA_SECRET): string {
            try {
                // Revert to the standard CryptoJS usage pattern.
                // First, create the key from ts and secret using MD5.
                const keyHex = this.md5hex(`${ts}${secret}`);
                const key = CryptoJS.enc.Hex.parse(keyHex);

                // Then, call decrypt with the standard signature.
                const decrypted = CryptoJS.AES.decrypt(data, key, {
                    mode: CryptoJS.mode.ECB,
                    padding: CryptoJS.pad.Pkcs7
                });

                const decryptedText = decrypted.toString(CryptoJS.enc.Utf8);

                if (!decryptedText) {
                    throw new Error("AES decryption returned an empty result.");
                }
                return decryptedText;
            } catch (error: any) {
                console.error(`Underlying decryption error: ${error.message}`);
                throw new Error(`AES Decryption failed. Original error: ${error.message}`);
            }
        }
    }

    class JmApiResp {
        constructor(private resp: OkHttpResponse, private ts: number) { }

        get isSuccess(): boolean {
            return this.resp.isSuccessful();
        }

        get json(): any {
            try {
                return JSON.parse(this.resp.content);
            } catch (error: any) {
                throw new Error(`JSON解析失败: ${error.message}`);
            }
        }

        get isSuccessful(): boolean {
            return this.isSuccess && this.json.code === 200;
        }

        get encodedData(): string {
            return this.json.data;
        }

        get decodedData(): string {
            return JmCryptoTool.decodeRespData(this.encodedData, this.ts);
        }

        get resData(): any {
            if (!this.isSuccessful) {
                throw new Error(`API请求失败: code=${this.json.code}`);
            }
            const decoded = this.decodedData;
            try {
                if (typeof decoded !== 'string' || !decoded) {
                    throw new Error(`Cannot parse non-string or empty value. Type: ${typeof decoded}`);
                }
                return JSON.parse(decoded);
            } catch (error: any) {
                const preview = String(decoded || 'N/A').substring(0, 80);
                throw new Error(`Failed to parse decrypted response. Error: ${error.message}. Original data type was ${typeof decoded}.`);
            }
        }

        get modelData(): any {
            return this.resData;
        }
    }

    class DirRuleImpl {
        constructor(public baseDir: string) { }

        decideImageSaveDir(album: Album, photo: Photo): string {
            return joinPath(this.baseDir, this.sanitize(album.title));
        }

        decideAlbumRootDir(album: Pick<Album, 'title'>): string {
            return joinPath(this.baseDir, this.sanitize(album.title));
        }

        sanitize(name: string): string {
            return name.replace(/[\\?%*:|"<>]/g, '_');
        }
    }

    class JmOptionImpl {
        public dirRule: DirRuleImpl;
        constructor(baseDir: string = "/sdcard/Download/OperitScripts") {
            this.dirRule = new DirRuleImpl(baseDir);
        }

        static default(baseDir: string = "/sdcard/Download/OperitScripts"): JmOptionImpl {
            return new JmOptionImpl(baseDir);
        }

        buildJmClient(): JmApiClientImpl {
            return new JmApiClientImpl();
        }
    }

    class JmApiClientImpl {
        private domainList: string[] = JmModuleConfig.DOMAIN_API_LIST;
        private retryTimes: number = 3;
        private client: OkHttpClient = OkHttp.newClient();

        private API_ALBUM = '/album';
        private API_CHAPTER = '/chapter';
        private API_SEARCH = '/search';
        private API_CATEGORIES_FILTER = '/categories/filter';

        async getAlbumDetail(albumId: string): Promise<Album> {
            const resp = await this.reqApi(`${this.API_ALBUM}?id=${albumId}`);
            const data = resp.resData;
            if (!data || !data.name) throw new Error(`本子 ${albumId} 不存在或数据无效`);
            return this.parseAlbumData(albumId, data);
        }

        async getPhotoDetail(photoId: string): Promise<Photo> {
            const resp = await this.reqApi(`${this.API_CHAPTER}?id=${photoId}`);
            const data = resp.resData;
            if (!data || !data.name) throw new Error(`章节 ${photoId} 不存在或数据无效`);
            return this.parsePhotoData(photoId, data);
        }

        async searchComics(params: SearchParams): Promise<SearchResult> {
            const { query, page = 1, order_by = 'view', time = 'all' } = params;
            const orderMap: { [key: string]: string } = { 'latest': 'mr', 'view': 'mv', 'picture': 'mp', 'like': 'tf' };
            const timeMap: { [key: string]: string } = { 'today': 't', 'week': 'w', 'month': 'm', 'all': 'a' };
            const apiParams = {
                search_query: query,
                page,
                o: orderMap[order_by.toLowerCase()] || orderMap['view'],
                t: timeMap[time.toLowerCase()] || timeMap['all']
            };
            const resp = await this.reqApi(`${this.API_SEARCH}?${this.toUrlSearchParams(apiParams)}`);
            const data = resp.resData;
            const results = (data.content || []).map((item: any): SearchResultItem => ({
                id: String(item.id || item.album_id),
                title: item.name || item.title
            }));
            return {
                search_params: params,
                results: results,
                total_results: results.length,
            };
        }

        async reqApi(url: string, method: 'GET' | 'POST' = 'GET', data?: any): Promise<JmApiResp> {
            const ts = Math.floor(Date.now() / 1000);
            for (let i = 0; i < this.domainList.length; i++) {
                const domain = this.domainList[i];
                for (let retry = 0; retry < this.retryTimes; retry++) {
                    try {
                        const fullUrl = `${JmModuleConfig.PROT}${domain}${url}`;
                        const [token, tokenparam] = JmCryptoTool.tokenAndTokenparam(ts);
                        const headers = { ...JmModuleConfig.APP_HEADERS_TEMPLATE, token, tokenparam };

                        const requestBuilder = this.client.newRequest().url(fullUrl).headers(headers);
                        if (method === 'POST') {
                            requestBuilder.method('POST').jsonBody(data);
                        }

                        const resp = await requestBuilder.build().execute();

                        if (resp.isSuccessful()) {
                            return new JmApiResp(resp, ts);
                        }
                    } catch (error: any) {
                        console.log(`[API] 请求失败: ${error.message} 域名: ${domain}`);
                        if (retry === this.retryTimes - 1 && i === this.domainList.length - 1) {
                            throw new Error(`所有域名和重试都失败: ${error.message}`);
                        }
                    }
                }
            }
            throw new Error('请求失败');
        }

        async downloadImage(imageUrl: string, savePath: string, scrambleId: number | string, photoId: string): Promise<boolean> {
            try {
                const response = await this.client.newRequest().url(imageUrl).headers(JmModuleConfig.APP_HEADERS_IMAGE).build().execute();

                if (!response.isSuccessful()) {
                    throw new Error(`HTTP error! status: ${response.statusCode}`);
                }
                const imageBase64 = response.bodyAsBase64();
                const dir = dirname(savePath);
                await ensureDirExists(dir);

                const imageName = JmImageTool.getFileNameFromUrl(imageUrl, false);
                const num = JmImageTool.getNum(scrambleId, photoId, imageName);
                await JmImageTool.decodeAndSave(num, imageBase64, savePath);

                return true;
            } catch (error: any) {
                console.error(`[图片] 下载失败: ${imageUrl}, 错误: ${error.message}`);
                return false;
            }
        }

        private toUrlSearchParams(obj: Record<string, any>): string {
            return Object.keys(obj).map(k => `${encodeURIComponent(k)}=${encodeURIComponent(obj[k])}`).join('&');
        }

        private parseAlbumData(albumId: string, data: any): Album {
            const episodeList = data.series && data.series.length > 0 ? data.series : [{ id: albumId, title: data.name }];
            return {
                id: albumId,
                title: data.name || `本子 ${albumId}`,
                author: (data.author && data.author[0]) || '未知作者',
                episodeList: episodeList,
                scrambleId: data.scramble_id || JmMagicConstants.SCRAMBLE_220980,
                length: episodeList.length,
            };
        }

        private parsePhotoData(photoId: string, data: any): Photo {
            return {
                id: photoId,
                title: data.name || `章节 ${photoId}`,
                pageArr: data.images || [],
                albumId: data.album_id || photoId,
                scrambleId: data.scramble_id || JmMagicConstants.SCRAMBLE_220980,
                length: (data.images || []).length,
            };
        }
    }

    class JmDownloaderImpl {
        public option: JmOptionImpl;
        public client: JmApiClientImpl;

        constructor(option: JmOptionImpl) {
            this.option = option;
            this.client = option.buildJmClient();
        }

        async downloadAlbum(albumId: string): Promise<Album> {
            const album = await this.client.getAlbumDetail(albumId);
            await this.downloadByAlbumDetail(album);
            return album;
        }

        private async downloadByAlbumDetail(album: Album): Promise<void> {
            const albumDir = this.option.dirRule.decideAlbumRootDir(album);
            await ensureDirExists(albumDir);
            console.log(`[专辑: ${album.title}] 发现 ${album.episodeList.length} 个章节, 开始下载...`);

            const chapterConcurrency = 5;
            const tasks = album.episodeList.map((episode, i) => async () => {
                console.log(`  [章节 ${i + 1}/${album.episodeList.length}] 开始下载: ${episode.title} (${episode.id})`);
                try {
                    const photo = await this.client.getPhotoDetail(episode.id);
                    await this.downloadPhotoImages(photo, albumDir, album.id);
                    console.log(`  [章节 ${i + 1}/${album.episodeList.length}] 下载完成: ${episode.title}`);
                } catch (e: any) {
                    console.error(`  [章节 ${i + 1}/${album.episodeList.length}] 下载失败: ${episode.title}, 错误: ${e.message}`);
                }
            });

            await runTasksWithConcurrency(tasks, chapterConcurrency);
        }

        private async downloadPhotoImages(photo: Photo, albumDir: string, albumId: string): Promise<void> {
            if (!photo.pageArr || photo.pageArr.length === 0) return;

            console.log(`    [图片集: ${photo.title}] 发现 ${photo.pageArr.length} 张图片, 开始下载...`);
            const concurrencyLimit = 10;

            const tasks = photo.pageArr.map((imageName, i) => {
                return async () => {
                    const finalFileName = `${(i + 1).toString().padStart(5, '0')}.jpg`;
                    const filePath = joinPath(albumDir, finalFileName);

                    const fileExists = await Tools.Files.exists(filePath);
                    if (fileExists.exists) {
                        return;
                    }

                    const imageUrl = this.buildImageUrl(photo, imageName);
                    try {
                        await this.client.downloadImage(imageUrl, filePath, photo.scrambleId, photo.id);
                    } catch (e: any) {
                        console.error(`      [图片下载失败] ${finalFileName} from ${photo.title}: ${e.message}`);
                    }
                };
            });

            await runTasksWithConcurrency(tasks, concurrencyLimit);
            console.log(`    [图片集: ${photo.title}] 所有图片下载任务已处理。`);
        }

        private getFileExtension(filename: string): string {
            const dotIndex = filename.lastIndexOf('.');
            return dotIndex > 0 ? filename.substring(dotIndex + 1) : 'jpg';
        }

        private buildImageUrl(photo: Photo, imageName: string): string {
            const domain = JmModuleConfig.DOMAIN_IMAGE_LIST[Math.floor(Math.random() * JmModuleConfig.DOMAIN_IMAGE_LIST.length)];
            return `${JmModuleConfig.PROT}${domain}/media/photos/${photo.albumId}/${imageName}`;
        }
    }

    class SimpleJMDownloader {
        private downloader: JmDownloaderImpl;
        private client: JmApiClientImpl;
        private option: JmOptionImpl;

        constructor(downloadDir: string = "/sdcard/Download/OperitScripts") {
            this.option = JmOptionImpl.default(downloadDir);
            this.downloader = new JmDownloaderImpl(this.option);
            this.client = this.option.buildJmClient();
            console.log(`✅ JM下载器初始化成功, 下载目录: ${this.option.dirRule.baseDir}`);
        }

        async searchComics(params: SearchParams): Promise<SearchResult> {
            console.log(`🔍 搜索漫画: ${params.query}`);
            return await this.client.searchComics(params);
        }

        async getAlbumInfo(albumId: string): Promise<AlbumInfo> {
            const album = await this.client.getAlbumDetail(albumId);
            return {
                id: album.id,
                title: album.title,
                author: album.author,
                chapterCount: album.length,
                success: true
            };
        }

        async downloadAlbum(albumId: string): Promise<DownloadResult> {
            console.log(`📖 获取本子信息: ${albumId}`);
            try {
                const info = await this.getAlbumInfo(albumId);
                if (!info.success) return { success: false, albumId, error: "获取信息失败" };

                console.log(`📥 开始下载本子: ${info.title}`);
                await this.downloader.downloadAlbum(albumId);
                const downloadedFiles = await this._checkDownloadedFiles(info.title);

                return {
                    success: true,
                    albumId: albumId,
                    title: info.title,
                    downloadedFiles
                };
            } catch (error: any) {
                return { success: false, albumId, error: error.message };
            }
        }

        async batchDownload(albumIds: string[]): Promise<BatchDownloadResult[]> {
            const results: BatchDownloadResult[] = [];
            console.log(`📦 开始批量下载 ${albumIds.length} 个本子`);

            const concurrencyLimit = 3; // 限制并发下载的漫画数量

            const tasks = albumIds.map((albumId, i) => async (): Promise<BatchDownloadResult> => {
                console.log(`\n[${i + 1}/${albumIds.length}] 开始处理本子: ${albumId}`);
                const result = await this.downloadAlbum(albumId);
                if (result.success) {
                    console.log(`✅ [${i + 1}/${albumIds.length}] 下载成功: ${result.title}`);
                } else {
                    console.log(`❌ [${i + 1}/${albumIds.length}] 下载失败: ${albumId}, ${result.error || 'Unknown error'}`);
                }
                return result;
            });

            return await runTasksWithConcurrency(tasks, concurrencyLimit);
        }

        private async _checkDownloadedFiles(title: string): Promise<NonNullable<DownloadResult['downloadedFiles']>> {
            const albumDir = this.option.dirRule.decideAlbumRootDir({ title });
            const dirExists = await Tools.Files.exists(albumDir);
            if (dirExists.exists) {
                const listResult = await Tools.Files.list(albumDir);
                const files = listResult.entries.map(e => e.name);
                return {
                    directory: albumDir,
                    fileCount: files.length,
                    files: files.slice(0, 10)
                };
            }
            return { directory: undefined, fileCount: 0, files: [] };
        }
    }
    //endregion

    //region Tool Implementations
    async function main() {
        console.log("🚀 开始执行JMComic工具功能测试...");
        const downloader = new SimpleJMDownloader("/sdcard/Download/OperitScripts/test_downloads");
        const testQuery = "原神";

        console.log(`1. 测试搜索功能，关键词: "${testQuery}"`);
        const searchResult = await downloader.searchComics({ query: testQuery });

        if (!searchResult || !searchResult.results || searchResult.results.length === 0) {
            throw new Error(`搜索测试失败: 未能找到关于 "${testQuery}" 的任何结果。`);
        }

        console.log(`✅ 搜索成功, 找到 ${searchResult.total_results} 个结果。`);
        const firstAlbum = searchResult.results[0];
        console.log(`2. 测试获取作品信息功能, 作品ID: ${firstAlbum.id} (${firstAlbum.title})`);

        const albumInfo = await downloader.getAlbumInfo(firstAlbum.id);

        if (!albumInfo || !albumInfo.success) {
            throw new Error(`获取作品信息失败, ID: ${firstAlbum.id}`);
        }

        console.log(`✅ 作品信息获取成功:`);
        console.log(`   - 标题: ${albumInfo.title}`);
        console.log(`   - 作者: ${albumInfo.author}`);
        console.log(`   - 章节数: ${albumInfo.chapterCount}`);

        console.log(`3. 测试下载功能, 作品ID: ${firstAlbum.id} (${firstAlbum.title})`);
        const downloadResult = await downloader.downloadAlbum(firstAlbum.id);

        if (!downloadResult || !downloadResult.success) {
            throw new Error(`下载作品失败, ID: ${firstAlbum.id}`);
        }

        console.log(`✅ 下载成功:`);
        console.log(`   - 保存目录: ${downloadResult.downloadedFiles!.directory}`);
        console.log(`   - 文件数量: ${downloadResult.downloadedFiles!.fileCount}`);

        const summary = `JMComic工具测试完成。成功搜索、获取信息并下载了作品《${albumInfo.title}》。`;
        console.log(`\n${summary}`);
        return summary;
    }

    async function search_comics(params: SearchParams) {
        const downloader = new SimpleJMDownloader();
        return await downloader.searchComics(params);
    }

    async function get_album_info(params: { album_id: string }) {
        const downloader = new SimpleJMDownloader();
        return await downloader.getAlbumInfo(params.album_id);
    }

    async function download_album(params: { album_id: string, download_dir?: string }) {
        const downloader = new SimpleJMDownloader(params.download_dir);
        return await downloader.downloadAlbum(params.album_id);
    }

    async function batch_download_albums(params: { album_ids: string, download_dir?: string }) {
        const albumIds = params.album_ids.split(',').map(id => id.trim()).filter(id => id);
        if (albumIds.length === 0) throw new Error("album_ids不能为空");
        const downloader = new SimpleJMDownloader(params.download_dir);
        return await downloader.batchDownload(albumIds);
    }

    async function jmcomic_wrap(func: Function, params: any, successMessage: string, failMessage: string) {
        try {
            console.log(`开始执行: ${func.name}`);
            const result = await func(params);
            complete({ success: true, message: successMessage, data: result });
        } catch (error: any) {
            console.error(`${func.name} 执行失败: ${error.message}`);
            complete({ success: false, message: `${failMessage}: ${error.message}`, error_stack: error.stack });
        }
    }
    //endregion

    return {
        main: (p: any) => jmcomic_wrap(main, p, '功能测试完成', '功能测试失败'),
        search_comics: (p: SearchParams) => jmcomic_wrap(search_comics, p, '搜索完成', '搜索失败'),
        get_album_info: (p: { album_id: string }) => jmcomic_wrap(get_album_info, p, '信息获取完成', '信息获取失败'),
        download_album: (p: { album_id: string, download_dir?: string }) => jmcomic_wrap(download_album, p, '下载完成', '下载失败'),
        batch_download_albums: (p: { album_ids: string, download_dir?: string }) => jmcomic_wrap(batch_download_albums, p, '批量下载完成', '批量下载失败'),
    };
})();

exports.main = jmcomic.main;
exports.search_comics = jmcomic.search_comics;
exports.get_album_info = jmcomic.get_album_info;
exports.download_album = jmcomic.download_album;
exports.batch_download_albums = jmcomic.batch_download_albums; 
