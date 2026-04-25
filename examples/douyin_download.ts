/*
METADATA
{
    "name": "douyin_download",

    "display_name": {
        "zh": "抖音下载工具",
        "en": "Douyin Download Tool"
    },
    "description": { "zh": "抖音工具包，提供从分享链接或分享口令中提取并下载无水印视频的功能。", "en": "Douyin toolkit for extracting and downloading watermark-free videos from share links or share codes." },
    "enabledByDefault": true,
    "category": "Media",
    "tools": [
        {
            "name": "get_douyin_download_link",
            "description": { "zh": "解析抖音分享链接或口令，下载无水印视频到本地", "en": "Parse a Douyin share link/code and download a watermark-free video to local storage." },
            "parameters": [
                {
                    "name": "input",
                    "description": { "zh": "抖音分享链接或包含链接的分享口令文本", "en": "Douyin share link, or share-code text that contains a link." },
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "get_douyin_video_info",
            "description": { "zh": "解析抖音分享链接或口令，仅获取视频信息和无水印下载链接，不下载视频", "en": "Parse a Douyin share link/code and return video info + watermark-free download URL (without downloading)." },
            "parameters": [
                {
                    "name": "input",
                    "description": { "zh": "抖音分享链接或包含链接的分享口令文本", "en": "Douyin share link, or share-code text that contains a link." },
                    "type": "string",
                    "required": true
                }
            ]
        }
    ]
}*/
const douyin = (function () {
    /**
     * 包装函数调用，提供标准化的成功/错误处理
     */
    async function douyin_wrap<T, U>(
        func: (params: T) => Promise<U>,
        params: T,
        successMessage: string,
        failMessage: string
    ): Promise<void> {
        try {
            console.log(`开始执行函数: ${func.name || '匿名函数'}`);
            const result = await func(params);
            complete({ success: true, message: successMessage, data: result });
        } catch (error: unknown) {
            console.error(`函数 ${func.name || '匿名函数'} 执行失败! 错误: ${error instanceof Error ? error.message : String(error)}`);
            complete({ success: false, message: `${failMessage}: ${error instanceof Error ? error.message : String(error)}`, error_stack: error instanceof Error ? error.stack : undefined });
        }
    }

    const client = OkHttp.newClient();

    // 抖音相关URL模式
    const DOUYIN_URL_PATTERNS = [
        /https?:\/\/v\.douyin\.com\/[^\s]+/,
        /https?:\/\/www\.douyin\.com\/video\/[0-9]+/,
        /https?:\/\/www\.douyin\.com\/share\/video\/[0-9]+/
    ];

    /**
     * 从文本中提取抖音链接
     */
    function extractDouyinUrl(text: string): string | null {
        for (const pattern of DOUYIN_URL_PATTERNS) {
            const match = text.match(pattern);
            if (match) {
                return match[0];
            }
        }
        return null;
    }

    /**
     * 解析抖音分享链接，获取视频信息
     */
    async function resolveDouyinUrl(shareUrl: string): Promise<{ videoId: string, downloadUrl: string, videoTitle: string }> {
        try {
            console.log(`正在解析抖音链接: ${shareUrl}`);

            const request = client.newRequest()
                .url(shareUrl)
                .method('GET')
                .headers({
                    "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) EdgiOS/121.0.2277.107 Version/17.0 Mobile/15E148 Safari/604.1",
                    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                    "Connection": "keep-alive"
                });

            const response = await request.build().execute();

            if (!response.isSuccessful()) {
                throw new Error(`链接解析失败 (${response.statusCode})`);
            }

            const html = response.content;

            // 尝试从ROUTER_DATA中提取视频信息
            const routerDataMatch = html.match(/window\._ROUTER_DATA\s*=\s*({[\s\S]*?})<\/script>/);
            if (routerDataMatch && routerDataMatch[1]) {
                try {
                    const routerData = JSON.parse(routerDataMatch[1]);
                    // The actual key might vary, let's find it dynamically
                    const pageDataKey = Object.keys(routerData.loaderData).find(k => k.includes('/page'));
                    if (!pageDataKey) {
                        throw new Error("无法在 _ROUTER_DATA 中找到页面数据");
                    }
                    const videoData = routerData.loaderData[pageDataKey]?.videoInfoRes?.item_list?.[0];

                    if (videoData) {
                        const videoId = videoData.aweme_id;
                        const videoTitle = videoData.desc;
                        const watermarkedUrl = videoData.video?.play_addr?.url_list?.[0];

                        if (videoId && videoTitle && watermarkedUrl) {
                            console.log(`通过 _ROUTER_DATA 找到视频信息: ID=${videoId}`);
                            const downloadUrl = watermarkedUrl.replace("playwm", "play");
                            return { videoId, downloadUrl, videoTitle };
                        }
                    }
                } catch (e: unknown) {
                    console.error(`解析 _ROUTER_DATA JSON 失败: ${e instanceof Error ? e.message : String(e)}`);
                }
            }

            throw new Error("无法从HTML中解析出 _ROUTER_DATA");

        } catch (error) {
            console.error(`解析抖音链接时出错: ${error instanceof Error ? error.message : String(error)}`);
            throw error;
        }
    }

    /**
     * 获取抖音视频的无水印下载链接并下载视频
     */
    async function get_douyin_download_link(params: { input: string }): Promise<string> {
        const { input } = params;

        if (!input) {
            throw new Error("输入内容不能为空");
        }

        console.log("开始获取并下载抖音无水印视频...");

        try {
            // 1. 提取和解析URL
            const douyinUrl = extractDouyinUrl(input);
            if (!douyinUrl) {
                throw new Error("未找到有效的抖音链接");
            }
            console.log(`找到抖音链接: ${douyinUrl}`);

            // 2. 解析链接并获取所有需要的视频信息
            console.log("正在解析分享链接...");
            const { videoId, downloadUrl, videoTitle: rawVideoTitle } = await resolveDouyinUrl(douyinUrl);
            console.log(`解析到视频ID: ${videoId}`);
            console.log(`获取到无水印下载链接: ${downloadUrl}`);

            // 获取视频标题（用于文件命名）
            let videoTitle = (rawVideoTitle || `douyin_${videoId}`).trim();
            // 替换文件名中的非法字符
            videoTitle = videoTitle.replace(/[\\/:*?"<>|]/g, '_');

            // 逐级创建目录并下载视频
            const baseDir = "/sdcard/Download/OperitScripts";
            const destinationDir = `${baseDir}/douyin`;
            const destinationPath = `${destinationDir}/${videoTitle}_${videoId}.mp4`;

            console.log(`确保目录存在: ${destinationDir}`);
            // 逐级创建目录以确保路径存在
            const dirsToCreate = ["/sdcard/Download/", baseDir, destinationDir];
            for (const dir of dirsToCreate) {
                const mkdirResult = await Tools.Files.mkdir(dir);
                if (!mkdirResult.successful) {
                    // 忽略"目录已存在"的错误，但记录其他可能的错误
                    console.warn(`创建目录 '${dir}' 失败 (可能已存在): ${mkdirResult.details}`);
                }
            }

            console.log(`开始下载视频到: ${destinationPath}`);
            const downloadResult = await Tools.Files.download(downloadUrl, destinationPath);

            if (!downloadResult.successful) {
                throw new Error(`视频下载失败: ${downloadResult.details}`);
            }

            const successMessage = `视频"${videoTitle}"成功下载到: ${destinationPath}`;
            console.log(successMessage);
            return successMessage;

        } catch (error) {
            const message = error instanceof Error ? error.message : String(error);
            console.error(`处理失败: ${message}`);
            // 重新抛出错误，以便包装器可以捕获它
            throw new Error(message);
        }
    }

    /**
     * 获取抖音视频信息和无水印下载链接（不下载视频）
     */
    async function get_douyin_video_info(params: { input: string }): Promise<{ videoId: string; videoTitle: string; downloadUrl: string; originalUrl: string }> {
        const { input } = params;

        if (!input) {
            throw new Error("输入内容不能为空");
        }

        console.log("开始解析抖音视频信息...");

        try {
            // 1. 提取URL
            const douyinUrl = extractDouyinUrl(input);
            if (!douyinUrl) {
                throw new Error("未找到有效的抖音链接");
            }
            console.log(`找到抖音链接: ${douyinUrl}`);

            // 2. 解析链接并获取视频信息
            console.log("正在解析分享链接...");
            const { videoId, downloadUrl, videoTitle } = await resolveDouyinUrl(douyinUrl);
            console.log(`解析到视频ID: ${videoId}`);
            console.log(`获取到无水印下载链接: ${downloadUrl}`);
            console.log(`视频标题: ${videoTitle}`);

            const result = {
                videoId,
                videoTitle,
                downloadUrl,
                originalUrl: douyinUrl
            };

            console.log("视频信息解析成功");
            return result;

        } catch (error) {
            const message = error instanceof Error ? error.message : String(error);
            console.error(`解析失败: ${message}`);
            throw new Error(message);
        }
    }

    //region 测试函数
    /**
     * 抖音工具功能测试主函数
     */
    async function main() {
        console.log("🚀 开始执行抖音工具功能测试...");
        await test_download_link();
        const summary = `抖音下载工具测试完成！\n` +
            `✅ 下载功能已测试`;
        console.log(`\n${summary}`);
        return summary;
    }

    /**
     * 测试下载功能
     */
    async function test_download_link() {
        const testUrl = "3.00 05/24 Z@M.JI TLW:/ 这里有几首melodic dubstep，看你认识几个 # 电子音乐 # 音乐分享 # 顶级旋律 # 热门音乐🔥百听不厌 # 戴上耳机  https://v.douyin.com/AT8AfEbuP_k/ 复制此链接，打开Dou音搜索，直接观看视频！"; // 使用真实分享文本进行测试
        console.log(`1. 测试视频下载功能 (使用真实分享文本)`);
        // 我们预期这个调用会成功提取URL并尝试下载
        try {
            const result = await get_douyin_download_link({ input: testUrl });
            console.log(`✅ 下载功能测试成功, 结果: ${result}`);
            return result;
        } catch (e) {
            const errorMessage = e instanceof Error ? e.message : String(e);
            console.error(`❌ 下载功能测试失败: ${errorMessage}`);
            // 在测试中，即使是预期的失败，也应该被视为一个需要注意的问题
            throw e;
        }
    }
    //endregion

    return {
        get_douyin_download_link: (params: { input: string; }) => douyin_wrap(get_douyin_download_link, params, '抖音视频下载完成', '抖音视频下载失败'),
        get_douyin_video_info: (params: { input: string; }) => douyin_wrap(get_douyin_video_info, params, '抖音视频信息获取成功', '抖音视频信息获取失败'),
        main: (params: Record<string, never>) => douyin_wrap(main, params, '抖音工具测试完成', '抖音工具测试失败'),
        test_download_link: (params: Record<string, never>) => douyin_wrap(test_download_link, params, '下载链接获取测试成功', '下载链接获取测试失败'),
    };
})();

// 导出所有功能
exports.get_douyin_download_link = douyin.get_douyin_download_link;
exports.get_douyin_video_info = douyin.get_douyin_video_info;
exports.main = douyin.main;
exports.test_download_link = douyin.test_download_link;