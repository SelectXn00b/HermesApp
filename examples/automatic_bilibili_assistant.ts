/*
METADATA
{
    // Bilibili 智能助手包
    name: Automatic_bilibili_assistant

    display_name: {
      zh: "Bilibili自动化助手"
      en: "Bilibili Automation Assistant"
    }description: { zh: "高级B站智能助手，通过UI自动化技术实现B站应用交互，支持视频搜索播放、评论互动、用户关注等功能，为AI赋予B站社交和内容消费能力。适用于自动追番、视频推荐、社交互动等场景。", en: "Advanced Bilibili assistant powered by UI automation. Supports video search/playback, commenting, and following uploaders, enabling AI-driven content consumption and social interaction on Bilibili." }

    // Tools in this package
    category: "Automatic"
    tools: [
        {
            name: workflow_guide
            description: { zh: "B站助手工具使用流程指南。要完成复杂任务，请按以下顺序组合使用工具：\n1. **搜索视频**: 使用 `search_video` 查找视频，这是所有操作的起点。\n2. **播放视频**: 从搜索结果中，使用 `play_video` 播放指定视频。\n3. **视频内互动**: 视频播放后，可执行 `get_video_info`, `like_video`, `collect_video`, `follow_uploader`, `send_comment`, `browse_comments` 等操作。\n4. **返回列表**: 要播放另一个视频，必须先用 `return_to_video_list` 返回搜索列表，再调用 `play_video`。\n- **随时导航**: `navigate_to_home` 等导航工具可在多数情况下使用。", en: "Workflow guide for the Bilibili assistant:\n1. **Search videos**: use `search_video` as the entry point for most tasks.\n2. **Play a video**: use `play_video` to open a specific result.\n3. **Interact**: while on the video page you can run `get_video_info`, `like_video`, `collect_video`, `follow_uploader`, `send_comment`, `browse_comments`, etc.\n4. **Back to list**: to play another video, call `return_to_video_list` first, then `play_video`.\n- **Navigation**: tools like `navigate_to_home` can often be used at any time." }
            parameters: []
            advice: true
        },
        {
            name: search_video
            description: { zh: "在B站搜索视频内容", en: "Search videos on Bilibili." }
            parameters: [
                {
                    name: keyword
                    description: { zh: "搜索关键词", en: "Search keyword." }
                    type: string
                    required: true
                },
                {
                    name: filter_type
                    description: { zh: "搜索结果过滤类型：comprehensive(综合)、new(最新)、hot(最多播放)、danmaku(最多弹幕)", en: "Result filter: comprehensive, new, hot (most played), danmaku (most danmaku)." }
                    type: string
                    required: false
                }
            ]
        },
        {
            name: play_video
            description: { zh: "播放指定的视频，可以通过标题或位置选择", en: "Play a specific video by title keyword or by index." }
            parameters: [
                {
                    name: video_title
                    description: { zh: "要播放的视频标题关键词", en: "Keyword to match the video title." }
                    type: string
                    required: false
                },
                {
                    name: video_index
                    description: { zh: "要播放的视频在搜索结果中的索引位置（从1开始）", en: "Index of the video in the search results (1-based)." }
                    type: number
                    required: false
                }
            ]
        },
        {
            name: return_to_video_list
            description: { zh: "从视频播放界面返回到视频列表界面", en: "Return from the video player page back to the video list." }
            parameters: []
        },
        {
            name: send_comment
            description: { zh: "在当前视频下发送评论", en: "Post a comment under the current video." }
            parameters: [
                {
                    name: comment_text
                    description: { zh: "要发送的评论内容", en: "Comment text to send." }
                    type: string
                    required: true
                }
            ]
        },
        {
            name: like_video
            description: { zh: "给当前视频点赞", en: "Like the current video." }
            parameters: []
        },
        {
            name: collect_video
            description: { zh: "收藏当前视频", en: "Save (favorite) the current video." }
            parameters: [
                {
                    name: folder_name
                    description: { zh: "收藏夹名称，留空则使用默认收藏夹", en: "Favorite folder name (leave empty to use the default folder)." }
                    type: string
                    required: false
                }
            ]
        },
        {
            name: follow_uploader
            description: { zh: "关注当前视频的UP主", en: "Follow the uploader (UP) of the current video." }
            parameters: []
        },
        {
            name: get_video_info
            description: { zh: "获取当前视频的详细信息，包括标题、UP主、播放量、评论数等", en: "Get detailed info of the current video (title, uploader, views, comments, etc.)." }
            parameters: []
        },
        {
            name: browse_comments
            description: { zh: "浏览当前视频的评论，获取热门评论内容", en: "Browse comments of the current video and retrieve popular comments." }
            parameters: [
                {
                    name: comment_count
                    description: { zh: "获取的评论数量，默认为5条", en: "Number of comments to fetch (default: 5)." }
                    type: number
                    required: false
                }
            ]
        },
        {
            name: navigate_to_home
            description: { zh: "导航到B站首页", en: "Navigate to the Bilibili home page." }
            parameters: []
        },
        {
            name: navigate_to_following
            description: { zh: "导航到关注页面，查看关注的UP主动态", en: "Navigate to the Following page to view updates from followed uploaders." }
            parameters: []
        },
        {
            name: navigate_to_history
            description: { zh: "导航到观看历史页面", en: "Navigate to the watch history page." }
            parameters: []
        },
        {
            name: toggle_fullscreen
            description: { zh: "切换视频全屏/非全屏状态", en: "Toggle fullscreen / windowed mode." }
            parameters: []
        },
        {
            name: adjust_playback_speed
            description: { zh: "调整视频播放速度", en: "Adjust video playback speed." }
            parameters: [
                {
                    name: speed
                    description: { zh: "播放速度：0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x", en: "Playback speed: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x." }
                    type: string
                    required: true
                }
            ]
        }
    ]
}*/

interface Array<T> {
    at(index: number): T | undefined;
}

const BilibiliAssistant = (function () {

    // 添加 Array.prototype.at 支持
    Array.prototype.at = function (index: number) {
        if (index < 0) {
            index = this.length + index;
        }
        return this[index];
    }

    // B站应用包名和主要Activity
    const BILIBILI_PACKAGE = "tv.danmaku.bili";
    const MAIN_ACTIVITY = "tv.danmaku.bili.MainActivityV2";
    const VIDEO_ACTIVITY = "com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity";

    // Helper to create response objects
    function createResponse(success: boolean, message: string, data: object | string = {}) {
        if (typeof data === 'string') {
            return { success: true, message: message, data: data };
        }
        return { success, message, ...data };
    }

    // Helper to find a UI element and click it
    async function findAndClick(finder: () => Promise<any>): Promise<boolean> {
        const element = await finder();
        if (element) {
            await element.click();
            await Tools.System.sleep(1000);
            return true;
        }
        return false;
    }

    // Helper to find a UI element by text and click it
    async function findAndClickByText(text: string): Promise<boolean> {
        return findAndClick(async () => (await UINode.getCurrentPage()).findByText(text));
    }

    async function ensureMain(packageName: string = BILIBILI_PACKAGE): Promise<boolean> {
        let pageInfo = await Tools.UI.getPageInfo();

        // 1. Check if we are already on the main activity.
        if (pageInfo.packageName === packageName && pageInfo.activityName?.includes(MAIN_ACTIVITY)) {
            console.log("Already on the main activity.");
            return true;
        }

        // 2. If not in the correct app, start it.
        if (pageInfo.packageName !== packageName) {
            console.log(`Not in the correct app. Current: ${pageInfo.packageName}. Starting ${packageName}.`);
            await Tools.System.startApp(packageName);
            await Tools.System.sleep(200); // Wait for app to load
        }

        // 3. Try pressing back up to 4 times to return to main.
        console.log("Attempting to go back to main activity.");
        for (let i = 0; i < 4; i++) {
            pageInfo = await Tools.UI.getPageInfo();
            if (pageInfo.packageName === packageName && pageInfo.activityName?.includes(MAIN_ACTIVITY)) {
                console.log(`Successfully returned to main activity on attempt ${i + 1}.`);
                return true;
            }
            // If we are not in the app anymore, stop trying to go back.
            if (pageInfo.packageName !== packageName) {
                console.log("Exited the app while trying to go back.");
                break;
            }
            await Tools.UI.pressKey("KEYCODE_BACK");
            await Tools.System.sleep(500);
        }

        // 4. If pressing back failed, perform a full restart.
        console.log("Pressing back failed or exited app. Performing a full app restart.");
        await Tools.System.stopApp(packageName);
        await Tools.System.sleep(1000);
        await Tools.System.startApp(packageName);
        await Tools.System.sleep(4000);

        pageInfo = await Tools.UI.getPageInfo();
        if (pageInfo.packageName === packageName && pageInfo.activityName?.includes(MAIN_ACTIVITY)) {
            console.log("Successfully reached main activity after restart.");
            return true;
        }

        console.error(`Failed to navigate to the main activity of ${packageName}.`);
        return false;
    }

    async function navigateToSearch(): Promise<boolean> {
        console.log("导航到搜索页面");
        // 确保在主页面
        if (!await ensureMain()) {
            return false;
        }

        // 点击搜索框容器（正确的搜索区域）
        const page = await UINode.getCurrentPage();
        const searchContainer = page.findById('expand_search');
        if (searchContainer) {
            await searchContainer.click();
            await Tools.System.sleep(1000);
            return true;
        }

        return false;
    }


    // --- Bilibili Automation Logic ---
    let lastSearchResults: any[] = [];

    /**
     * Searches for videos on Bilibili.
     * @param params - The search parameters.
     * @param {string} params.keyword - The keyword to search for.
     * @param {string} [params.filter_type] - The filter to apply (e.g., 'new', 'hot', 'danmaku').
     * @param {number} [params.count=20] - The desired number of video results to return.
     * @returns {Promise<any>} A promise that resolves with the search results.
     */
    async function search_video(params: { keyword: string; filter_type?: string; count?: number }): Promise<any> {
        const { keyword, filter_type, count = 20 } = params; // Default count to 20

        console.log(`搜索视频: ${keyword}, 过滤类型: ${filter_type}`);

        // 导航到搜索页面
        if (!await navigateToSearch()) {
            return createResponse(false, "无法进入搜索页面", { keyword: keyword });
        }

        await Tools.System.sleep(1000);

        // 输入搜索关键词
        const searchInput = (await UINode.getCurrentPage()).findById('search_plate');
        if (searchInput) {
            await Tools.UI.setText(keyword);
            await Tools.System.sleep(500);
        } else {
            // 如果没找到输入框，直接输入文本
            await Tools.UI.setText(keyword);
            await Tools.System.sleep(500);
        }

        // 点击搜索按钮
        const searchButton = (await UINode.getCurrentPage()).findById('action_search');
        if (searchButton) {
            await searchButton.click();
        } else {
            // 尝试按回车键搜索
            await Tools.UI.pressKey("KEYCODE_ENTER");
        }

        await Tools.System.sleep(4000); // 等待搜索结果加载

        // Apply filter if specified
        if (filter_type) {
            await applySearchFilter(filter_type);
        }

        // 获取搜索结果
        const results = await getSearchResults(count);
        lastSearchResults = results; // Cache the results

        return createResponse(true, `搜索到 ${results.length} 条有效视频结果`, {
            keyword: keyword,
            filter_type: filter_type,
            results: results.map(({ element, ...rest }) => rest), // Exclude element from response
            result_count: results.length
        });
    }

    async function applySearchFilter(filterType: string): Promise<void> {
        const page = await UINode.getCurrentPage();
        const filterButton = page.findByText("综合"); // Default to comprehensive
        if (filterButton) {
            await filterButton.click();
            await Tools.System.sleep(1000);
        }

        if (filterType === "new") {
            await findAndClickByText("最新");
            await Tools.System.sleep(1000);
        } else if (filterType === "hot") {
            await findAndClickByText("最多播放");
            await Tools.System.sleep(1000);
        } else if (filterType === "danmaku") {
            await findAndClickByText("最多弹幕");
            await Tools.System.sleep(1000);
        }
    }

    async function getSearchResults(desiredCount: number = 20): Promise<any[]> {
        let page = await UINode.getCurrentPage();
        const results: any[] = [];
        const seenTitles = new Set<string>();

        const resultsContainer = page.findById('tv.danmaku.bili:id/recycler_view');
        if (!resultsContainer) {
            console.log("Could not find search results container.");
            return results;
        }

        // 不再滚动到顶部，直接从当前位置开始处理，避免触发下拉刷新
        let lastResultCount = -1;
        const maxScrolls = 10; // Safety break to prevent infinite loops
        let scrollCount = 0;

        for (let i = 0; i < maxScrolls; i++) {
            page = await UINode.getCurrentPage();
            const currentContainer = page.findById('tv.danmaku.bili:id/recycler_view');
            if (!currentContainer) break;

            for (const item of currentContainer.children) {
                const titleNode = item.findById('tv.danmaku.bili:id/title');
                const title = titleNode ? titleNode.text : null;

                if (!title || seenTitles.has(title)) {
                    continue; // Skip if no title or already seen
                }

                // Filtering logic
                const isAd = item.findById('tv.danmaku.bili:id/ad_tint_frame') || item.findById('tv.danmaku.bili:id/ad_tag');
                const isUser = item.findById('tv.danmaku.bili:id/user_info');
                const isGame = item.findById('tv.danmaku.bili:id/iv_background');
                const isLive = item.findByText("直播");

                if (isAd || isUser || isGame || isLive) {
                    continue;
                }

                seenTitles.add(title);

                const uploaderNode = item.findById('tv.danmaku.bili:id/upper') || item.findById('tv.danmaku.bili:id/upuser');
                const playCountNode = item.findById('tv.danmaku.bili:id/play_num');
                const durationNode = item.findById('tv.danmaku.bili:id/duration');
                const postTimeNode = item.findById('tv.danmaku.bili:id/post_time') || item.findById('tv.danmaku.bili:id/danmakus_num');

                results.push({
                    index: results.length + 1,
                    title: title,
                    uploader: uploaderNode ? uploaderNode.text : "N/A",
                    play_count: playCountNode ? playCountNode.text : "N/A",
                    duration: durationNode ? durationNode.text : "N/A",
                    post_time: postTimeNode ? postTimeNode.text : "N/A",
                    element: item
                });

                if (results.length >= desiredCount) {
                    break;
                }
            }

            if (results.length >= desiredCount) {
                console.log(`Reached desired count of ${desiredCount}.`);
                break;
            }

            // Check if scrolling is producing new results
            if (results.length === lastResultCount) {
                console.log("Scrolling is not yielding new results. Stopping.");
                break;
            }
            lastResultCount = results.length;

            // Scroll down to load more
            await Tools.UI.swipe(540, 1500, 540, 800); // Swipe up to scroll down
            await Tools.System.sleep(1500); // Wait for new items to load
            scrollCount++;
        }

        console.log(`Scrolling back up ${scrollCount} times.`);
        for (let i = 0; i < scrollCount; i++) {
            await Tools.UI.swipe(540, 800, 540, 1550);
            await Tools.System.sleep(1000);
        }

        return results.slice(0, desiredCount);
    }

    async function play_video(params: { video_title?: string, video_index?: number }): Promise<any> {
        const { video_title, video_index } = params;

        if (!video_title && !video_index) {
            return createResponse(false, "请提供视频标题或索引。");
        }

        console.log(`尝试播放视频 - 标题: "${video_title}", 索引: ${video_index}`);

        let page = await UINode.getCurrentPage();
        const seenTitles = new Set<string>();
        let currentVideoCount = 0;
        const maxScrolls = 10;
        let videoFoundAndClicked = false;
        let foundVideoTitle: string | undefined = undefined;

        // Ensure we are on search results page by checking for the recycler view
        const initialContainer = page.findById('tv.danmaku.bili:id/recycler_view');
        if (!initialContainer) {
            return createResponse(false, "似乎不在搜索结果页面，无法播放视频。");
        }

        // 不再滚动到顶部，直接从当前位置开始查找，避免触发下拉刷新
        let lastSeenCount = -1;
        let scrollCount = 0; // 记录向下滚动的次数

        for (let i = 0; i < maxScrolls; i++) {
            page = await UINode.getCurrentPage();
            const currentContainer = page.findById('tv.danmaku.bili:id/recycler_view');
            if (!currentContainer) {
                console.log("Could not find search results container during scroll.");
                break;
            }

            const visibleItems = currentContainer.children;

            for (const item of visibleItems) {
                const titleNode = item.findById('tv.danmaku.bili:id/title');
                const title = titleNode ? titleNode.text : null;

                if (!title || seenTitles.has(title)) {
                    continue;
                }

                // Filtering logic
                const isAd = item.findById('tv.danmaku.bili:id/ad_tint_frame') || item.findById('tv.danmaku.bili:id/ad_tag');
                const isUser = item.findById('tv.danmaku.bili:id/user_info');
                const isGame = item.findById('tv.danmaku.bili:id/iv_background');
                const isLive = item.findByText("直播");

                if (isAd || isUser || isGame || isLive) {
                    continue;
                }

                // This is a valid video, add to seen and increment count
                seenTitles.add(title);
                currentVideoCount++;

                let shouldClick = false;
                if (video_index) {
                    if (currentVideoCount === video_index) {
                        shouldClick = true;
                    }
                } else if (video_title) {
                    if (title.includes(video_title)) {
                        shouldClick = true;
                    }
                }

                if (shouldClick) {
                    // Try clicking the cover image first, as it's a more reliable target
                    const coverImage = item.findById('tv.danmaku.bili:id/cover');
                    if (coverImage) {
                        console.log("Clicking on video cover image.");
                        await coverImage.click();
                    } else {
                        console.log("Cover image not found, clicking on the whole item.");
                        await item.click();
                    }
                    await Tools.System.sleep(4000); // Increased wait time for video to load
                    videoFoundAndClicked = true;
                    foundVideoTitle = title;
                    break;
                }
            }

            if (videoFoundAndClicked) {
                break;
            }

            // Check if we should stop scrolling
            if (seenTitles.size === lastSeenCount) {
                console.log("Scrolling is not yielding new results. Stopping.");
                break;
            }
            lastSeenCount = seenTitles.size;

            // If we are looking for an index and have already passed it, stop.
            if (video_index && currentVideoCount >= video_index) {
                break;
            }

            // Scroll down to load more
            await Tools.UI.swipe(540, 1500, 540, 800);
            await Tools.System.sleep(1500);
            scrollCount++; // 记录滚动次数
        }

        // 如果没找到视频，滚动回去
        if (!videoFoundAndClicked && scrollCount > 0) {
            console.log(`未找到视频，滚动回去 ${scrollCount} 次`);
            for (let i = 0; i < scrollCount; i++) {
                await Tools.UI.swipe(540, 800, 540, 1550);
                await Tools.System.sleep(1000);
            }
        }

        if (videoFoundAndClicked) {
            return createResponse(true, `成功开始播放视频: "${foundVideoTitle}"`, { video_title, video_index });
        } else {
            return createResponse(false, "未找到指定的视频", { video_title, video_index });
        }
    }

    async function send_comment(params: { comment_text: string }): Promise<any> {
        const commentText = params.comment_text || "";

        if (!await switch_to_comment_activity()) {
            return createResponse(false, "不在视频页面");
        }

        console.log(`发送评论: ${commentText}`);

        const page = await UINode.getCurrentPage();
        // 查找评论区域
        let commentArea = page.findById("input");
        if (commentArea) {
            await commentArea.click();
            await Tools.System.sleep(1000);

            // 输入评论内容
            await Tools.UI.setText(commentText);
            await Tools.System.sleep(500);

            // 查找发送按钮
            const sendButton = page.findByText("发送");
            if (sendButton) {
                await sendButton.click();
                await Tools.System.sleep(1000);

                return createResponse(true, "评论发送成功", { comment_text: commentText });
            }
        } else {
            return createResponse(false, "未找到评论输入框", { comment_text: commentText });
        }
    }
    async function is_in_video_activity(): Promise<boolean> {
        const pageInfo = await Tools.UI.getPageInfo();
        return pageInfo.activityName?.includes(VIDEO_ACTIVITY);
    }

    async function is_on_search_results_page(): Promise<boolean> {
        // A reliable indicator of the search results page is the presence of filter buttons.
        const page = await UINode.getCurrentPage();
        const filterButton = page.findByText("综合") && page.findByText("搜索"); // "Comprehensive" filter
        return !!filterButton;
    }

    async function return_to_video_list(params: {}): Promise<any> {
        console.log("Attempting to return to video list...");
        if (await is_in_video_activity()) {
            await Tools.UI.pressKey("KEYCODE_BACK");
            await Tools.System.sleep(2000); // Wait for transition
            // Verify we are back on the search results page
            if (await is_on_search_results_page()) {
                return createResponse(true, "Successfully returned to video list.");
            } else {
                return createResponse(false, "Failed to return to video list, did not land on search results page.");
            }
        } else {
            // If we are already on the search results page, consider it a success.
            if (await is_on_search_results_page()) {
                return createResponse(true, "Already on the video list page.");
            }
            return createResponse(false, "Not in a video activity, no action taken.");
        }
    }

    async function switch_to_comment_activity() {
        if (!await is_in_video_activity()) {
            return false;
        }
        console.log("切换到评论页面");
        await findAndClickByText("评论");
        await Tools.System.sleep(1000);
        return true;
    }

    async function like_video(params: {}): Promise<any> {
        if (!await is_in_video_activity()) {
            return createResponse(false, "不在视频页面");
        }
        console.log("给视频点赞");

        if (await findAndClick(async () => (await UINode.getCurrentPage()).findById("frame_like"))) {
            return createResponse(true, "点赞成功");
        } else {
            return createResponse(false, "未找到点赞按钮");
        }
    }

    async function collect_video(params: { folder_name?: string }): Promise<any> {
        if (!await is_in_video_activity()) {
            return createResponse(false, "不在视频页面");
        }
        const { folder_name: folderName } = params;
        console.log(`开始收藏视频, 文件夹: ${folderName}`);

        if (await findAndClick(async () => (await UINode.getCurrentPage()).findById("frame_fav"))) {
            return createResponse(true, "收藏成功", { folder_name: folderName });
        } else {
            return createResponse(false, "未找到收藏按钮", { folder_name: folderName });
        }
    }

    async function follow_uploader(params: {}): Promise<any> {
        if (!await is_in_video_activity()) {
            return createResponse(false, "不在视频页面");
        }
        console.log("开始关注UP主");

        if (await findAndClick(async () => (await UINode.getCurrentPage()).findAllById("follow"))) {
            return createResponse(true, "关注成功");
        } else {
            return createResponse(false, "未找到关注按钮或已经关注");
        }
    }

    async function get_video_info(params: {}): Promise<any> {
        if (!await is_in_video_activity()) {
            return createResponse(false, "不在视频页面");
        }

        console.log("获取视频信息");

        const page = await UINode.getCurrentPage();
        const videoInfo = {
            title: "",
            uploader: "",
            play_count: "",
            comment_count: "",
            like_count: "",
            coin_count: "",
            collect_count: "",
            forward_count: ""
        };

        // 获取视频标题
        const titleElement = page.findById('title');
        if (titleElement && titleElement.text) {
            videoInfo.title = titleElement.text;
        }
        // 获取视频点赞数量
        const likeElement = page.findById('frame_like');
        if (likeElement && likeElement.contentDesc) {
            videoInfo.like_count = likeElement.contentDesc;
        }
        // 获取视频收藏数量
        const collectElement = page.findById('frame_fav');
        if (collectElement && collectElement.contentDesc) {
            videoInfo.collect_count = collectElement.contentDesc;
        }
        // 获取视频投币数量
        const coinElement = page.findById('frame_coin');
        if (coinElement && coinElement.contentDesc) {
            videoInfo.coin_count = coinElement.contentDesc;
        }

        //获取视频转发数量
        const forwardElement = page.findById('frame_share');
        if (forwardElement && forwardElement.contentDesc) {
            videoInfo.forward_count = forwardElement.contentDesc;
        }

        // 获取UP主名称
        const uploaderElements = page.findAllById("author_name");
        if (uploaderElements.length > 0) {
            videoInfo.uploader = uploaderElements.map(u => u.text).join("/");
        }


        return createResponse(true, "获取视频信息成功", { video_info: videoInfo });
    }

    async function browse_comments(params: { comment_count?: number }): Promise<any> {
        const commentCount = params.comment_count || 5;
        if (!await switch_to_comment_activity()) {
            return createResponse(false, "不在视频页面");
        }

        console.log(`浏览评论，获取${commentCount}条`);

        const page = await UINode.getCurrentPage();

        // 滚动到评论区域
        await Tools.UI.swipe(540, 1500, 540, 800);
        await Tools.System.sleep(2000);

        // 查找评论列表
        const commentList = page.allTexts();

        return createResponse(true, "获取评论成功", { "all_texts": commentList });
    }

    async function navigate_to_home(params: {}): Promise<any> {
        console.log("导航到首页");

        if (await ensureMain()) {
            return createResponse(true, "已导航到首页");
        } else {
            return createResponse(false, "无法导航到首页");
        }
    }

    async function navigate_to_following(params: {}): Promise<any> {
        console.log("导航到关注页面");

        const page = await UINode.getCurrentPage();

        const followingTab = page.findByText("关注");
        if (followingTab) {
            await followingTab.click();
            await Tools.System.sleep(2000);

            return createResponse(true, "已导航到关注页面");
        } else {
            return createResponse(false, "未找到关注标签");
        }
    }

    async function navigate_to_history(params: {}): Promise<any> {
        console.log("导航到历史页面");

        const page = await UINode.getCurrentPage();

        // 查找历史按钮（通常在个人中心或侧边栏）
        let historyButton = page.findByText("历史");
        if (!historyButton) {
            historyButton = page.findByText("观看历史");
        }

        if (historyButton) {
            await historyButton.click();
            await Tools.System.sleep(2000);

            return createResponse(true, "已导航到历史页面");
        } else {
            return createResponse(false, "未找到历史按钮");
        }
    }

    async function toggle_fullscreen(params: {}): Promise<any> {
        console.log("切换全屏状态");

        // 点击视频中央来显示控制栏
        await Tools.UI.tap(540, 960);
        await Tools.System.sleep(1000);

        const page = await UINode.getCurrentPage();

        // 查找全屏按钮
        let fullscreenButton = page.findByContentDesc("全屏");
        if (!fullscreenButton) {
            fullscreenButton = page.findByText("全屏");
        }
        if (!fullscreenButton) {
            fullscreenButton = page.findById('fullscreen_button');
        }

        if (fullscreenButton) {
            await fullscreenButton.click();
            await Tools.System.sleep(1000);

            return createResponse(true, "全屏状态切换成功");
        } else {
            return createResponse(false, "未找到全屏按钮");
        }
    }

    async function adjust_playback_speed(params: { speed: string }): Promise<any> {
        const speed = params.speed || "1.0x";

        console.log(`调整播放速度为: ${speed}`);

        // 点击视频中央来显示控制栏
        await Tools.UI.tap(540, 960);
        await Tools.System.sleep(1000);

        const page = await UINode.getCurrentPage();

        // 查找速度设置按钮
        let speedButton = page.findByText("倍速");
        if (!speedButton) {
            speedButton = page.findByText("1.0x");
        }
        if (!speedButton) {
            speedButton = page.findById('speed_button');
        }

        if (speedButton) {
            await speedButton.click();
            await Tools.System.sleep(1000);

            // 选择目标速度
            const targetSpeedButton = page.findByText(speed);
            if (targetSpeedButton) {
                await targetSpeedButton.click();
                await Tools.System.sleep(1000);

                return createResponse(true, `播放速度已调整为 ${speed}`, { speed: speed });
            } else {
                return createResponse(false, `未找到 ${speed} 速度选项`, { speed: speed });
            }
        } else {
            return createResponse(false, "未找到速度设置按钮", { speed: speed });
        }
    }

    async function main() {
        console.log("=== B站智能助手测试 ===");
        console.log(await search_video({ "keyword": "原神" }));
        await play_video({ "video_index": 4 });
        console.log(await get_video_info({}));
        console.log(await browse_comments({}));

    }

    async function wrapToolExecution(func: (params: any) => Promise<any>, params: any) {
        try {
            const result = await func(params);
            complete({
                ...result, // contains success, message, and other data
            });
        } catch (error) {
            // This catch is for unexpected errors in tool logic, not for "soft" failures.
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }

    return {
        main: main,
        search_video: async (params) => await wrapToolExecution(search_video, params),
        play_video: async (params) => await wrapToolExecution(play_video, params),
        return_to_video_list: async (params) => await wrapToolExecution(return_to_video_list, params),
        send_comment: async (params) => await wrapToolExecution(send_comment, params),
        like_video: async (params) => await wrapToolExecution(like_video, params),
        collect_video: async (params) => await wrapToolExecution(collect_video, params),
        follow_uploader: async (params) => await wrapToolExecution(follow_uploader, params),
        get_video_info: async (params) => await wrapToolExecution(get_video_info, params),
        browse_comments: async (params) => await wrapToolExecution(browse_comments, params),
        navigate_to_home: async (params) => await wrapToolExecution(navigate_to_home, params),
        navigate_to_following: async (params) => await wrapToolExecution(navigate_to_following, params),
        navigate_to_history: async (params) => await wrapToolExecution(navigate_to_history, params),
        toggle_fullscreen: async (params) => await wrapToolExecution(toggle_fullscreen, params),
        adjust_playback_speed: async (params) => await wrapToolExecution(adjust_playback_speed, params)
    };
})();

// 逐个导出
exports.search_video = BilibiliAssistant.search_video;
exports.play_video = BilibiliAssistant.play_video;
exports.return_to_video_list = BilibiliAssistant.return_to_video_list;
exports.send_comment = BilibiliAssistant.send_comment;
exports.like_video = BilibiliAssistant.like_video;
exports.collect_video = BilibiliAssistant.collect_video;
exports.follow_uploader = BilibiliAssistant.follow_uploader;
exports.get_video_info = BilibiliAssistant.get_video_info;
exports.browse_comments = BilibiliAssistant.browse_comments;
exports.navigate_to_home = BilibiliAssistant.navigate_to_home;
exports.navigate_to_following = BilibiliAssistant.navigate_to_following;
exports.navigate_to_history = BilibiliAssistant.navigate_to_history;
exports.toggle_fullscreen = BilibiliAssistant.toggle_fullscreen;
exports.adjust_playback_speed = BilibiliAssistant.adjust_playback_speed;
exports.main = BilibiliAssistant.main;