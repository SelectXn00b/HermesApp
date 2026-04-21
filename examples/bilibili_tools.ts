/* METADATA
{
    "name": "bilibili_tools",
    "display_name": {
        "zh": "Bilibili 工具集",
        "en": "Bilibili Toolkit"
    },
    "description": { "zh": "提供B站视频信息分析功能，包括获取字幕、弹幕、评论和搜索视频。", "en": "Analyze Bilibili video information, including subtitles, danmaku, comments, and video search." },
    "enabledByDefault": true,
    "category": "Media",
    "tools": [
        {
            "name": "get_subtitles",
            "description": { "zh": "从Bilibili视频中获取字幕。", "en": "Get subtitles from a Bilibili video." },
            "parameters": [
                { "name": "url", "description": { "zh": "Bilibili视频URL，例如：https://www.bilibili.com/video/BV1x341177NN", "en": "Bilibili video URL, e.g. https://www.bilibili.com/video/BV1x341177NN" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "get_danmaku",
            "description": { "zh": "从Bilibili视频中获取弹幕。", "en": "Get danmaku (bullet comments) from a Bilibili video." },
            "parameters": [
                { "name": "url", "description": { "zh": "Bilibili视频URL，例如：https://www.bilibili.com/video/BV1x341177NN", "en": "Bilibili video URL, e.g. https://www.bilibili.com/video/BV1x341177NN" }, "type": "string", "required": true },
                { "name": "count", "description": { "zh": "要获取的弹幕数量，默认500，最多1000", "en": "Number of danmaku items to fetch (default: 500, max: 1000)." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "get_comments",
            "description": { "zh": "从Bilibili视频中获取热门评论。", "en": "Get hot comments from a Bilibili video." },
            "parameters": [
                { "name": "url", "description": { "zh": "Bilibili视频URL，例如：https://www.bilibili.com/video/BV1x341177NN", "en": "Bilibili video URL, e.g. https://www.bilibili.com/video/BV1x341177NN" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "search_videos",
            "description": { "zh": "在Bilibili上搜索视频。", "en": "Search videos on Bilibili." },
            "parameters": [
                { "name": "keyword", "description": { "zh": "要搜索的关键词", "en": "Keyword to search for." }, "type": "string", "required": true },
                { "name": "page", "description": { "zh": "页码，默认为1", "en": "Page number (default: 1)." }, "type": "number", "required": false },
                { "name": "count", "description": { "zh": "返回结果的数量，默认10，最多20", "en": "Number of results to return (default: 10, max: 20)." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "get_user_info",
            "description": { "zh": "获取B站用户的基本信息。", "en": "Get basic information for a Bilibili user." },
            "parameters": [
                { "name": "mid", "description": { "zh": "用户的数字ID (UID)", "en": "User numeric ID (UID)." }, "type": "number", "required": true }
            ]
        }
    ]
}*/

//参考了https://github.com/vruses/bili-api-interceptor/blob/master/dist/bili-api-interceptor.user.js 特此感谢

const BilibiliVideoAnalysis = (function () {

    interface ToolResponse {
        success: boolean;
        message: string;
        data?: any;
    }

    interface Subtitle {
        lan: string;
        content: string[];
    }

    interface Comment {
        user: string;
        content: string;
        likes: number;
        time: string; // 添加评论时间
        sub_comments: Comment[]; // 添加子评论列表
    }

    interface SearchResultVideo {
        title: string;
        author: string;
        bvid: string;
        play: number;
        danmaku: number;
        like: number;
        duration: string;
        pubdate: number;
        description: string;
    }

    interface UserInfo {
        name: string;
        sex: string;
        face: string;
        sign: string;
        level: number;
        birthday: string;
        follower: number;
        following: number;
        mid: number;
    }

    // Bilibili API endpoints
    const API_GET_VIEW_INFO = "https://api.bilibili.com/x/web-interface/view";
    const API_GET_SUBTITLE_LIST = "https://api.bilibili.com/x/player/v2";
    const API_GET_DANMAKU = "https://api.bilibili.com/x/v1/dm/list.so";
    const API_GET_COMMENTS = "https://api.bilibili.com/x/v2/reply/wbi/main";
    const API_SEARCH = "https://api.bilibili.com/x/web-interface/search/all/v2";
    const API_NAV = "https://api.bilibili.com/x/web-interface/nav";
    const API_GET_USER_INFO = "https://api.bilibili.com/x/space/wbi/acc/info";
    const API_GET_RELATION_STAT = "https://api.bilibili.com/x/relation/stat";

    const client = OkHttp.newClient();
    let SESSDATA: string | undefined;
    let wbiKeys: { img_key: string; sub_key: string; } | null = null;
    let buvid3: string | null = null;

    function generateBuvid3(): string {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
            const r = Math.random() * 16 | 0;
            const v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }

    async function init() {
        if (SESSDATA === undefined) {
            // SESSDATA 是B站的Cookie，用于访问一些需要登录的API。
            // 为了简单起见，这里我们不作强制要求。
            // 如果遇到请求失败的情况，可以尝试手动在这里设置你的SESSDATA值。
            // 例如: SESSDATA = "your_sessdata_cookie_value";
            SESSDATA = "";
            if (!SESSDATA) {
                console.log("SESSDATA 未设置，部分需要登录的请求可能会失败。");
            }
        }
        // Pre-fetch WBI keys if not already available.
        if (!wbiKeys) {
            wbiKeys = await get_wbi_keys();
        }
    }

    function getHeaders(): Record<string, string> {
        const headers: Record<string, string> = {
            'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36',
            'Referer': 'https://www.bilibili.com/'
        };

        if (!buvid3) {
            buvid3 = generateBuvid3();
        }

        const cookies = [`buvid3=${buvid3}`];
        if (SESSDATA) {
            cookies.push(`SESSDATA=${SESSDATA}`);
        }
        headers['Cookie'] = cookies.join('; ');

        return headers;
    }

    function unescapeXml(text: string): string {
        return text.replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>')
            .replace(/&amp;/g, '&')
            .replace(/&quot;/g, '"')
            .replace(/&apos;/g, "'");
    }

    const mixinKeyEncTab = [
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    ];

    function getMixinKey(orig: string): string {
        return mixinKeyEncTab.map(n => orig[n]).join('').slice(0, 32);
    }

    function encWbi(params: Record<string, any>, img_key: string, sub_key: string): string {
        const mixin_key = getMixinKey(img_key + sub_key);
        const curr_time = Math.round(Date.now() / 1000);
        const chr_filter = /[!'()*]/g;

        const new_params = { ...params, wts: curr_time };

        const query = Object.keys(new_params).sort().map(key => {
            const value = String(new_params[key]).replace(chr_filter, "");
            return `${encodeURIComponent(key)}=${encodeURIComponent(value)}`;
        }).join('&');

        const w_rid = CryptoJS.MD5(query + mixin_key).toString();
        return `${query}&w_rid=${w_rid}`;
    }

    async function get_wbi_keys(): Promise<{ img_key: string, sub_key: string } | null> {
        try {
            const response = await client.get(API_NAV, getHeaders());
            if (!response.isSuccessful()) {
                console.error("Failed to get WBI keys, status: " + response.statusCode);
                return null;
            }
            const navData = response.json();

            // The API may return a non-zero code for non-logged-in users, but still provide the WBI keys.
            if (!navData.data?.wbi_img?.img_url || !navData.data?.wbi_img?.sub_url) {
                console.error("Failed to get WBI keys: " + (navData.message || "No wbi_img in response"));
                return null;
            }

            const imgUrl = navData.data.wbi_img.img_url;
            const subUrl = navData.data.wbi_img.sub_url;

            const img_key = imgUrl.substring(imgUrl.lastIndexOf('/') + 1, imgUrl.lastIndexOf('.'));
            const sub_key = subUrl.substring(subUrl.lastIndexOf('/') + 1, subUrl.lastIndexOf('.'));

            return { img_key, sub_key };
        } catch (e: any) {
            console.error(`Error fetching WBI keys: ${e.message}`);
            return null;
        }
    }

    async function extract_bvid(url: string): Promise<string | null> {
        let match = url.match(/BV[a-zA-Z0-9_]+/);
        if (match) {
            return match[0];
        }

        if (url.includes('b23.tv')) {
            try {
                const request = client.newRequest().url(url).method("HEAD").build();
                const response = await request.execute();
                const finalUrl = response.raw.url;
                if (finalUrl) {
                    match = finalUrl.match(/BV[a-zA-Z0-9_]+/);
                    if (match) {
                        return match[0];
                    }
                }
            } catch (e) {
                console.error(`Error resolving short URL: ${e}`);
            }
        }
        return null;
    }

    async function get_video_basic_info(bvid: string): Promise<{ aid: number | null, cid: number | null, error: string | null }> {
        try {
            const url = `${API_GET_VIEW_INFO}?bvid=${bvid}`;
            const response = await client.get(url, getHeaders());
            if (!response.isSuccessful()) {
                return { aid: null, cid: null, error: `Failed to get video info, status: ${response.statusCode}` };
            }
            const data = response.json();
            if (data.code !== 0) {
                return { aid: null, cid: null, error: `Failed to get video info: ${data.message}` };
            }
            const videoData = data.data;
            return { aid: videoData.aid, cid: videoData.cid, error: null };
        } catch (e: any) {
            return { aid: null, cid: null, error: `Failed to fetch video details: ${e.message}` };
        }
    }

    async function get_subtitles_from_api(aid: number, cid: number): Promise<{ subtitles: Subtitle[], error: string | null }> {
        const subtitles: Subtitle[] = [];
        try {
            const url = `${API_GET_SUBTITLE_LIST}?aid=${aid}&cid=${cid}`;
            const response = await client.get(url, getHeaders());
            if (!response.isSuccessful()) {
                return { subtitles: [], error: `Could not fetch subtitles list, status: ${response.statusCode}` };
            }
            const subtitleListData = response.json();
            if (subtitleListData.code === 0 && subtitleListData.data?.subtitle?.subtitles) {
                for (const sub_meta of subtitleListData.data.subtitle.subtitles) {
                    if (sub_meta.subtitle_url) {
                        try {
                            const subtitle_json_url = `https:${sub_meta.subtitle_url}`;
                            const sub_content_response = await client.get(subtitle_json_url, getHeaders());
                            if (sub_content_response.isSuccessful()) {
                                const sub_content = await sub_content_response.json();
                                const subtitle_body = sub_content.body || [];
                                const content_list = subtitle_body.map((item: any) => item.content || '');
                                subtitles.push({
                                    lan: sub_meta.lan,
                                    content: content_list
                                });
                            }
                        } catch (e: any) {
                            console.error(`Could not fetch or parse subtitle content from ${sub_meta.subtitle_url}: ${e.message}`);
                        }
                    }
                }
            }
            return { subtitles, error: null };
        } catch (e: any) {
            return { subtitles: [], error: `Could not fetch subtitles: ${e.message}` };
        }
    }

    async function get_danmaku_from_api(cid: number, count: number): Promise<{ danmaku: string[], error: string | null }> {
        const danmaku_list: string[] = [];
        try {
            const url = `${API_GET_DANMAKU}?oid=${cid}`;
            const response = await client.get(url, getHeaders());

            if (!response.isSuccessful()) {
                return { danmaku: [], error: `Failed to get danmaku, status: ${response.statusCode}` };
            }

            const compressedData = response.bodyAsBase64();
            console.log("base64" + compressedData);
            if (!compressedData) {
                return { danmaku: [], error: null };
            }

            const danmaku_content = pako.inflate(compressedData, { to: 'string' });

            const regex = /<d p=".*?">(.*?)<\/d>/g;
            let match;
            while ((match = regex.exec(danmaku_content)) !== null) {
                if (danmaku_list.length >= count) {
                    break;
                }
                danmaku_list.push(unescapeXml(match[1]));
            }

            return { danmaku: danmaku_list, error: null };
        } catch (e: any) {
            return { danmaku: [], error: `Failed to get or parse danmaku: ${e.message}` };
        }
    }

    async function get_comments_from_api(aid: number): Promise<{ comments: string, count: number, error: string | null }> {
        const all_comments: Comment[] = [];

        if (!wbiKeys) {
            return { comments: "", count: 0, error: "获取 WBI keys 失败，无法请求评论 API" };
        }

        let page_num = 1;
        let total_pages = 1;

        try {
            while (page_num <= total_pages) {
                const params = {
                    type: 1,
                    oid: aid,
                    sort: 2, // sort=2 for hot
                    pn: page_num
                };

                const signed_query = encWbi(params, wbiKeys.img_key, wbiKeys.sub_key);
                const url = `${API_GET_COMMENTS}?${signed_query}`;

                const response = await client.get(url, getHeaders());

                if (!response.isSuccessful()) {
                    console.error(`Failed to get comments page ${page_num}, status: ${response.statusCode}`);
                    page_num++;
                    continue;
                }

                const comments_data = response.json();

                if (comments_data.code !== 0) {
                    console.error(`API error on page ${page_num}: ${comments_data.message}`);
                    page_num++;
                    continue;
                }

                if (page_num === 1 && comments_data.data?.page) {
                    total_pages = Math.ceil(comments_data.data.page.count / comments_data.data.page.size);
                }

                if (comments_data.data?.replies) {
                    for (const comment of comments_data.data.replies) {
                        const formatted_comment = format_comment(comment);
                        if (formatted_comment) {
                            all_comments.push(formatted_comment);
                        }
                    }
                }
                page_num++;
            }
            const comment_summary = format_comments_to_string(all_comments);
            return { comments: comment_summary, count: all_comments.length, error: null };
        } catch (e: any) {
            const comment_summary = format_comments_to_string(all_comments);
            return { comments: comment_summary, count: all_comments.length, error: `Failed to get comments: ${e.message}` }; // 返回已获取的部分数据
        }
    }

    function format_comment(comment_item: any): Comment {
        const sub_comments = (comment_item.replies || []).map(format_comment).filter(Boolean);
        return {
            user: comment_item.member.uname,
            content: comment_item.content.message,
            likes: comment_item.like || 0,
            time: new Date(comment_item.ctime * 1000).toLocaleDateString(),
            sub_comments: sub_comments
        };
    }

    function format_comments_to_string(comments: Comment[]): string {
        const result: string[] = [];
        function format_single(comment: Comment, indent: string) {
            result.push(`${indent}- ${comment.user} (👍${comment.likes}) [${comment.time}]: ${comment.content}`);
            if (comment.sub_comments && comment.sub_comments.length > 0) {
                for (const sub of comment.sub_comments) {
                    format_single(sub, indent + "  ");
                }
            }
        }
        for (const comment of comments) {
            format_single(comment, "");
        }
        return result.join('\n');
    }


    async function get_subtitles(params: { url: string }): Promise<ToolResponse> {
        await init();
        const bvid = await extract_bvid(params.url);
        if (!bvid) {
            return { success: false, message: `错误: 无法从 URL 提取 BV 号: ${params.url}` };
        }

        const { aid, cid, error: infoError } = await get_video_basic_info(bvid);
        if (infoError) {
            return { success: false, message: `获取视频信息失败: ${infoError}` };
        }
        if (!aid || !cid) {
            return { success: false, message: '获取视频 aid 或 cid 失败' };
        }

        const { subtitles, error: subtitlesError } = await get_subtitles_from_api(aid, cid);
        if (subtitlesError) {
            return { success: false, message: `获取字幕失败: ${subtitlesError}` };
        }

        if (subtitles.length === 0) {
            return { success: true, message: "该视频没有字幕", data: [] };
        }

        return { success: true, message: `成功获取 ${subtitles.length} 门语言的字幕`, data: subtitles };
    }

    async function get_danmaku(params: { url: string, count?: number }): Promise<ToolResponse> {
        await init();
        const { url, count = 500 } = params;
        const bvid = await extract_bvid(url);
        if (!bvid) {
            return { success: false, message: `错误: 无法从 URL 提取 BV 号: ${url}` };
        }

        if (count > 1000) {
            return { success: false, message: "参数 'count' 不能超过 1000" };
        }

        const { aid, cid, error: infoError } = await get_video_basic_info(bvid);
        if (infoError) {
            return { success: false, message: `获取视频信息失败: ${infoError}` };
        }
        if (!cid) {
            return { success: false, message: '获取视频 cid 失败' };
        }

        const { danmaku, error: danmakuError } = await get_danmaku_from_api(cid, count);
        if (danmakuError) {
            return { success: false, message: `获取弹幕失败: ${danmakuError}` };
        }

        if (danmaku.length === 0) {
            return { success: true, message: "该视频没有弹幕", data: [] };
        }

        return { success: true, message: `成功获取 ${danmaku.length} 条弹幕`, data: danmaku };
    }

    async function get_comments(params: { url: string }): Promise<ToolResponse> {
        await init();
        const bvid = await extract_bvid(params.url);
        if (!bvid) {
            return { success: false, message: `错误: 无法从 URL 提取 BV 号: ${params.url}` };
        }

        const { aid, error: infoError } = await get_video_basic_info(bvid);
        if (infoError) {
            return { success: false, message: `获取视频信息失败: ${infoError}` };
        }
        if (!aid) {
            return { success: false, message: '获取视频 aid 失败' };
        }

        const { comments, count, error: commentsError } = await get_comments_from_api(aid);

        if (commentsError && count === 0) {
            return { success: false, message: `获取评论失败: ${commentsError}` };
        }

        if (count === 0) {
            return { success: true, message: "该视频没有热门评论", data: "" };
        }

        const message = commentsError
            ? `成功获取 ${count} 条热门评论，但过程中发生错误: ${commentsError}`
            : `成功获取 ${count} 条热门评论`;

        return { success: true, message: message, data: comments };
    }

    async function search_videos_from_api(keyword: string, page: number): Promise<{ results: any, error: string | null }> {
        if (!wbiKeys) {
            return { results: null, error: "获取 WBI keys 失败，无法请求搜索 API" };
        }

        try {
            const params = {
                keyword: keyword,
                page: page,
                search_type: "video"
            };

            const signed_query = encWbi(params, wbiKeys.img_key, wbiKeys.sub_key);
            const url = `${API_SEARCH}?${signed_query}`;

            const response = await client.get(url, getHeaders());

            if (!response.isSuccessful()) {
                return { results: null, error: `搜索失败, status: ${response.statusCode}` };
            }

            const search_data = response.json();
            if (search_data.code !== 0) {
                return { results: null, error: `搜索 API 错误: ${search_data.message}` };
            }

            return { results: search_data.data, error: null };
        } catch (e: any) {
            return { results: null, error: `搜索时发生异常: ${e.message}` };
        }
    }

    function format_search_results_to_string(data: any, count: number): { formatted: string, result_count: number, total_count: number } {
        if (!data.result) {
            return { formatted: "没有找到相关结果。", result_count: 0, total_count: 0 };
        }

        const videoResults = data.result
            .find((item: any) => item.result_type === "video")
            ?.data;

        if (!videoResults || videoResults.length === 0) {
            return { formatted: "没有找到相关视频结果。", result_count: 0, total_count: data.numResults || 0 };
        }

        const slicedResults = videoResults.slice(0, count);

        const formattedResults = slicedResults
            .map((video: SearchResultVideo, index: number) => {
                const cleanTitle = video.title.replace(/<em class="keyword">|<\/em>/g, "");
                const cleanDescription = video.description.replace(/<em class="keyword">|<\/em>/g, "");
                return [
                    `${index + 1}. "${cleanTitle}" - ${video.author}`,
                    `   BV ID: ${video.bvid}`,
                    `   播放: ${video.play?.toLocaleString()}`,
                    `   弹幕: ${video.danmaku?.toLocaleString()}`,
                    `   点赞: ${video.like?.toLocaleString()}`,
                    `   时长: ${video.duration}`,
                    `   发布于: ${new Date(video.pubdate * 1000).toLocaleDateString()}`,
                    `   简介: ${cleanDescription?.substring(0, 100)}${cleanDescription?.length > 100 ? "..." : ""}`,
                ].join("\n");
            })
            .join("\n\n");

        return { formatted: formattedResults, result_count: slicedResults.length, total_count: data.numResults || 0 };
    }


    async function search_videos(params: { keyword: string, page?: number, count?: number }): Promise<ToolResponse> {
        await init();
        const { keyword, page = 1, count = 10 } = params;

        if (count > 20) {
            return { success: false, message: "参数 'count' 不能超过 20" };
        }

        const { results, error } = await search_videos_from_api(keyword, page);
        if (error) {
            return { success: false, message: `搜索失败: ${error}` };
        }

        const { formatted, result_count, total_count } = format_search_results_to_string(results, count);

        if (result_count === 0) {
            return { success: true, message: "没有找到相关视频。", data: "" };
        }

        const message = `成功为 "${keyword}" 找到 ${total_count} 个相关视频，当前显示第 ${page} 页的 ${result_count} 个结果。`;
        return { success: true, message, data: formatted };
    }

    function format_user_info_to_string(user: UserInfo): string {
        return [
            `昵称: ${user.name} (UID: ${user.mid})`,
            `性别: ${user.sex}`,
            `等级: LV${user.level}`,
            `生日: ${user.birthday || '未设置'}`,
            `粉丝数: ${user.follower.toLocaleString()}`,
            `关注数: ${user.following.toLocaleString()}`,
            `个人简介: ${user.sign || '这个UP主很懒，什么都没有写...'}`,
            `头像链接: ${user.face}`
        ].join('\n');
    }

    async function get_user_info_from_api(mid: number): Promise<{ user_info: UserInfo | null, error: string | null }> {
        if (!wbiKeys) {
            return { user_info: null, error: "获取 WBI keys 失败，无法请求用户信息 API" };
        }

        try {
            // Get user account info (WBI signed)
            const params = { mid };
            const signed_query = encWbi(params, wbiKeys.img_key, wbiKeys.sub_key);
            const url = `${API_GET_USER_INFO}?${signed_query}`;
            const response = await client.get(url, getHeaders());

            if (!response.isSuccessful()) {
                return { user_info: null, error: `获取用户信息失败, status: ${response.statusCode}` };
            }

            const userData = response.json();
            if (userData.code !== 0) {
                return { user_info: null, error: `用户信息 API 错误: ${userData.message}` };
            }

            // Get user relation stat (not WBI signed)
            const statUrl = `${API_GET_RELATION_STAT}?vmid=${mid}`;
            const statResponse = await client.get(statUrl, getHeaders());
            let follower = -1, following = -1;
            if (statResponse.isSuccessful()) {
                const statData = statResponse.json();
                if (statData.code === 0) {
                    follower = statData.data.follower;
                    following = statData.data.following;
                }
            }

            const userInfo: UserInfo = {
                mid: userData.data.mid,
                name: userData.data.name,
                sex: userData.data.sex,
                face: userData.data.face,
                sign: userData.data.sign,
                level: userData.data.level,
                birthday: userData.data.birthday,
                follower: follower,
                following: following
            };

            return { user_info: userInfo, error: null };
        } catch (e: any) {
            return { user_info: null, error: `获取用户信息时发生异常: ${e.message}` };
        }
    }

    async function get_user_info(params: { mid: number }): Promise<ToolResponse> {
        await init();
        const { mid } = params;

        if (!mid || typeof mid !== 'number' || mid <= 0) {
            return { success: false, message: "参数 'mid' 必须是一个正整数" };
        }

        const { user_info, error } = await get_user_info_from_api(mid);
        if (error) {
            return { success: false, message: `获取用户信息失败: ${error}` };
        }

        if (!user_info) {
            return { success: false, message: "未能获取到用户信息" };
        }

        const formatted_info = format_user_info_to_string(user_info);
        const message = `成功获取 UID:${mid} 的用户信息。`;

        return { success: true, message, data: formatted_info };
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
        console.log("--- Bilibili Video Analysis Tool Test ---");
        const testUrl = "https://www.bilibili.com/video/BV1NRE5zmE3W";
        const testKeyword = "OpenAI";

        console.log("\n[1/4] Testing search_videos...");
        const searchResult = await search_videos({ keyword: testKeyword, count: 10 });
        if (searchResult.success) {
            console.log(`Success: ${searchResult.message}`);
            console.log(`---------- Search Results for "${testKeyword}" ----------`);
            console.log(searchResult.data);
            console.log("------------------------------------------");
        } else {
            console.error(`Failure: ${searchResult.message}`);
        }

        console.log("\n[2/4] Testing get_subtitles...");
        const subtitlesResult = await get_subtitles({ url: testUrl });
        console.log(JSON.stringify(subtitlesResult, null, 2));

        console.log("\n[3/4] Testing get_danmaku with count limit...");
        const danmakuResult = await get_danmaku({ url: testUrl, count: 20 });
        console.log(JSON.stringify(danmakuResult, null, 2));

        console.log("\n[4/4] Testing get_comments...");
        const commentsResult = await get_comments({ url: testUrl });
        // For compact string format, we don't need JSON.stringify and can print directly
        if (commentsResult.success) {
            console.log(`Success: ${commentsResult.message}`);
            console.log("---------- Comments ----------");
            console.log(commentsResult.data);
            console.log("----------------------------");
        } else {
            console.error(`Failure: ${commentsResult.message}`);
        }

        console.log("\n[5/5] Testing get_user_info...");
        const userInfoResult = await get_user_info({ mid: 2 }); // 2 is B站创始人之一的UID
        if (userInfoResult.success) {
            console.log(`Success: ${userInfoResult.message}`);
            console.log("---------- User Info ----------");
            console.log(userInfoResult.data);
            console.log("-----------------------------");
        } else {
            console.error(`Failure: ${userInfoResult.message}`);
        }

        complete({ success: true, message: "Test finished." });
    }

    return {
        get_subtitles: (params: { url: string }) => wrapToolExecution(get_subtitles, params),
        get_danmaku: (params: { url: string, count?: number }) => wrapToolExecution(get_danmaku, params),
        get_comments: (params: { url: string }) => wrapToolExecution(get_comments, params),
        search_videos: (params: { keyword: string, page?: number, count?: number }) => wrapToolExecution(search_videos, params),
        get_user_info: (params: { mid: number }) => wrapToolExecution(get_user_info, params),
        main,
    };
})();

exports.get_subtitles = BilibiliVideoAnalysis.get_subtitles;
exports.get_danmaku = BilibiliVideoAnalysis.get_danmaku;
exports.get_comments = BilibiliVideoAnalysis.get_comments;
exports.search_videos = BilibiliVideoAnalysis.search_videos;
exports.get_user_info = BilibiliVideoAnalysis.get_user_info;
exports.main = BilibiliVideoAnalysis.main; 