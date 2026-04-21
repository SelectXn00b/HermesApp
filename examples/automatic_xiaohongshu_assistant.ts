/*
METADATA
{
    // 小红书智能助手包
    name: "Automatic_xiaohongshu_assistant",

    display_name: {
      zh: "小红书自动化助手"
      en: "Xiaohongshu Automation Assistant"
    }description: { zh: "高级小红书智能助手，通过UI自动化技术实现小红书应用交互，支持内容浏览、搜索查看、评论互动、内容发布等功能，为AI赋予小红书社交和内容创作能力。适用于内容营销、社交互动、生活分享等场景。", en: "Advanced Xiaohongshu (RED) assistant powered by UI automation. Supports browsing feeds, searching and viewing content, commenting and interactions, and publishing posts, enabling AI-driven social interaction and content creation. Useful for content marketing and social engagement." },
    
    // Tools in this package
    category: "Automatic"
    tools: [
        {
            name: "workflow_guide",
            description: { zh: "小红书助手工具使用流程指南。工具主要分为几类：\n1. **内容发现**: 使用 `browse_home_feed` 浏览主页，或用 `search_content` 搜索特定内容。\n2. **查看与互动**: 发现帖子后，用 `view_post` 查看详情。在详情页内可执行 `like_post`, `collect_post`, `comment_post`, `follow_user`, `unfollow_user` 等并列互动操作。\n3. **内容发布**: `publish_post` 是一个独立的发布流程。\n4. **导航**: `navigate_to_home`, `back_to_feed` 等工具用于在各页面间跳转。", en: "Workflow guide for the Xiaohongshu assistant:\n1. **Discover**: browse the home feed via `browse_home_feed`, or search via `search_content`.\n2. **View & interact**: after finding a post, open it with `view_post`. On the detail page you can run `like_post`, `collect_post`, `comment_post`, `follow_user`, `unfollow_user`, etc.\n3. **Publish**: `publish_post` is a dedicated publishing flow.\n4. **Navigation**: use tools like `navigate_to_home` and `back_to_feed` to switch between pages." },
            parameters: []
        },
        {
            name: "browse_home_feed",
            description: { zh: "浏览小红书主页信息流，获取推荐内容", en: "Browse the Xiaohongshu home feed and collect recommended content." },
            parameters: [
                {
                    name: "scroll_count",
                    description: { zh: "滚动次数，控制浏览的内容量，默认为3次", en: "Number of scrolls (controls how much content to browse; default: 3)." },
                    type: "number",
                    required: false
                },
                {
                    name: "collect_posts",
                    description: { zh: "是否收集帖子信息，默认为true", en: "Whether to collect post info (default: true)." },
                    type: "boolean",
                    required: false
                }
            ]
        },
        {
            name: "search_content",
            description: { zh: "在小红书中搜索内容、用户或话题", en: "Search content, users, or topics in Xiaohongshu." },
            parameters: [
                {
                    name: "keyword",
                    description: { zh: "搜索关键词", en: "Search keyword." },
                    type: "string",
                    required: true
                },
                {
                    name: "search_type",
                    description: { zh: "搜索类型：comprehensive(综合)、note(笔记)、user(用户)、topic(话题)", en: "Search type: comprehensive, note, user, topic." },
                    type: "string",
                    required: false
                }
            ]
        },
        {
            name: "view_post",
            description: { zh: "查看指定的帖子详情", en: "View details of a specific post." },
            parameters: [
                {
                    name: "post_title",
                    description: { zh: "要查看的帖子标题关键词", en: "Keyword to match the post title (optional)." },
                    type: "string",
                    required: false
                },
                {
                    name: "post_index",
                    description: { zh: "要查看的帖子在列表中的索引位置（从1开始）", en: "Index of the post in the list (1-based, optional)." },
                    type: "number",
                    required: false
                }
            ]
        },
        {
            name: "like_post",
            description: { zh: "给当前帖子点赞", en: "Like the current post." },
            parameters: []
        },
        {
            name: "collect_post",
            description: { zh: "收藏当前帖子", en: "Save (collect) the current post." },
            parameters: [
                {
                    name: "collection_name",
                    description: { zh: "收藏夹名称，留空则使用默认收藏夹", en: "Collection name (leave empty to use the default collection)." },
                    type: "string",
                    required: false
                }
            ]
        },
        {
            name: "follow_user",
            description: { zh: "关注当前帖子的作者", en: "Follow the author of the current post." },
            parameters: []
        },
        {
            name: "unfollow_user",
            description: { zh: "取消关注当前帖子的作者", en: "Unfollow the author of the current post." },
            parameters: []
        },
        {
            name: "comment_post",
            description: { zh: "在当前帖子下发表评论", en: "Post a comment under the current post." },
            parameters: [
                {
                    name: "comment_text",
                    description: { zh: "要发表的评论内容", en: "Comment text to post." },
                    type: "string",
                    required: true
                }
            ]
        },
        {
            name: "get_post_info",
            description: { zh: "获取当前帖子的详细信息，包括标题、作者、点赞数、评论数、发布日期和热门评论等", en: "Get detailed info of the current post: title, author, likes, comments, publish date, hot comments, etc." },
            parameters: []
        },
        {
            name: "publish_post",
            description: { zh: "发布新的帖子内容", en: "Publish a new post." },
            parameters: [
                {
                    name: "content_text",
                    description: { zh: "帖子文字内容", en: "Post text content." },
                    type: "string",
                    required: true
                },
                {
                    name: "image_paths",
                    description: { zh: "图片文件路径列表，用逗号分隔", en: "Comma-separated list of image file paths (optional)." },
                    type: "string",
                    required: false
                },
                {
                    name: "tags",
                    description: { zh: "话题标签，用逗号分隔", en: "Comma-separated topic tags (optional)." },
                    type: "string",
                    required: false
                },
                {
                    name: "location",
                    description: { zh: "地理位置信息", en: "Location information (optional)." },
                    type: "string",
                    required: false
                }
            ]
        },
        {
            name: "navigate_to_home",
            description: { zh: "导航到小红书首页", en: "Navigate to the Xiaohongshu home page." },
            parameters: []
        },
        {
            name: "navigate_to_profile",
            description: { zh: "导航到个人主页", en: "Navigate to the profile page." },
            parameters: []
        },
        {
            name: "navigate_to_search",
            description: { zh: "导航到搜索页面", en: "Navigate to the search page." },
            parameters: []
        },
        {
            name: "navigate_to_publish",
            description: { zh: "导航到发布页面", en: "Navigate to the publish page." },
            parameters: []
        },
        {
            name: "back_to_feed",
            description: { zh: "从帖子详情页返回到信息流列表", en: "Return from the post detail page back to the feed list." },
            parameters: []
        }
    ]
}*/

interface Array<T> {
    at(index: number): T | undefined;
}

const XiaohongshuAssistant = (function () {

    // 添加 Array.prototype.at 支持
    Array.prototype.at = function (index: number) {
        if (index < 0) {
            index = this.length + index;
        }
        return this[index];
    }

    interface Post {
        title: string;
        author: string;
        likes: string;
    }

    interface PostInfo {
        index: number;
        title: string;
        author: string;
        likes: string;
        element: UINode;
    }

    interface Comment {
        author: string;
        content: string;
        likes: string;
        date: string;
    }

    interface ToolResponse {
        success: boolean;
        message: string;
        [key: string]: unknown;
    }

    // 小红书应用包名和主要Activity
    const XIAOHONGSHU_PACKAGE = "com.xingin.xhs";
    const MAIN_ACTIVITY = "com.xingin.xhs.activity.SplashActivity";
    const HOME_ACTIVITY = "com.xingin.xhs.index.v2.IndexActivityV2";

    // Helper to create response objects
    function createResponse(success: boolean, message: string, data: object | string = {}) {
        if (typeof data === 'string') {
            return { success: true, message: message, data: data };
        }
        return { success, message, ...data };
    }

    // Helper to find a UI element and click it
    async function findAndClick(finder: () => Promise<UINode | null | undefined>): Promise<boolean> {
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

    async function ensureMain(packageName: string = XIAOHONGSHU_PACKAGE): Promise<boolean> {
        let pageInfo = await Tools.UI.getPageInfo();

        // 1. 如果不在小红书app，则启动
        if (pageInfo.packageName !== packageName) {
            await Tools.System.startApp(packageName);
            await Tools.System.sleep(3000);
            pageInfo = await Tools.UI.getPageInfo();
        }

        // 2. 检查是否在首页
        const isAlreadyOnHome = async () => {
            const page = await UINode.getCurrentPage();
            const followButton = page.findByContentDesc("关注");
            const discoverButton = page.findByContentDesc("发现");
            const searchButton = page.findByContentDesc("搜索");
            return followButton && discoverButton && searchButton;
        };

        if (await isAlreadyOnHome()) {
            console.log("已经在首页。");
            return true;
        }

        // 3. 尝试通过返回回到首页
        console.log("尝试返回到首页。");
        for (let i = 0; i < 4; i++) {
            await Tools.UI.pressKey("KEYCODE_BACK");
            await Tools.System.sleep(1000);
            if (await isAlreadyOnHome()) {
                console.log("成功回到首页。");
                return true;
            }
            pageInfo = await Tools.UI.getPageInfo();
            if (pageInfo.packageName !== packageName) {
                console.log("返回时退出了app。");
                break; // Exit loop if we've exited the app
            }
        }

        // 4. 如果返回失败，重启app
        console.log("返回失败，重启app。");
        await Tools.System.stopApp(packageName);
        await Tools.System.sleep(1000);
        await Tools.System.startApp(packageName);
        await Tools.System.sleep(5000);

        pageInfo = await Tools.UI.getPageInfo();
        if (pageInfo.packageName === packageName) {
            console.log("App重启成功。");
            return true;
        }

        console.error(`无法导航到 ${packageName} 的首页。`);
        return false;
    }

    // --- 小红书自动化逻辑 ---
    let lastBrowseResults: Post[] = [];

    async function browse_home_feed(params: { scroll_count?: number, collect_posts?: boolean }) {
        const { scroll_count = 5, collect_posts = false } = params;
        const posts: Post[] = [];
        console.log(`开始浏览, 滑动次数: ${scroll_count}, 是否收集帖子: ${collect_posts}`);

        for (let i = 0; i < scroll_count; i++) {
            console.log(`第 ${i + 1} 次滑动...`);

            if (collect_posts) {
                const currentPage = await UINode.getCurrentPage();

                const searchScopeNode = currentPage;
                console.log("将在整个页面范围内查找帖子。");

                // 为了调试，我们先找到所有 FrameLayout 并打印它们的 contentDesc
                const allFrameLayouts = searchScopeNode.findAll(n => n.className === 'FrameLayout');
                console.log(`[调试] 在查找范围内找到了 ${allFrameLayouts.length} 个 FrameLayout 节点。`);
                for (const frame of allFrameLayouts) {
                    if (frame.contentDesc) {
                        console.log(`[调试] FrameLayout contentDesc: "${frame.contentDesc}"`);
                    }
                }

                // 正式筛选帖子节点
                const postNodes = allFrameLayouts.filter(n =>
                    n.contentDesc && n.contentDesc.includes(' 来自')
                );

                console.log(`本次查找共发现 ${postNodes.length} 个帖子。`);

                for (const postNode of postNodes) {
                    const desc = postNode.contentDesc;
                    if (desc) {
                        try {
                            const parts = desc.split(' 来自');
                            if (parts.length > 1) {
                                const authorAndLikesStr = parts.pop()!.trim();
                                let title = parts.join(' 来自').trim();

                                // 移除 "笔记" or "视频" 前缀, 包括中间有空格或换行的情况
                                title = title.replace(/^(?:笔\s*记|视\s*频)\s*/, '');

                                const words = authorAndLikesStr.split(/\s+/);
                                if (words.length > 0) {
                                    const likesWithSuffix = words.pop();
                                    const author = words.join(' ');

                                    if (title && likesWithSuffix && likesWithSuffix.endsWith('赞')) {
                                        const post = {
                                            title,
                                            author: author || "未知作者",
                                            likes: likesWithSuffix.replace('赞', '').trim()
                                        };

                                        if (!posts.some(p => p.title === post.title && p.author === post.author)) {
                                            console.log(` -> 收集到新帖子: ${post.title} | 作者: ${post.author} | 赞: ${post.likes}`);
                                            posts.push(post);
                                        }
                                    }
                                }
                            }
                        } catch (e) {
                            console.log(`解析时出现异常: "${desc}"`, e);
                        }
                    }
                }
            }

            await Tools.UI.swipe(500, 1800, 500, 500); // 从下往上滑动
            await Tools.System.sleep(2500); // 等待新内容加载
        }

        console.log(`浏览结束, 共收集到 ${posts.length} 个独立帖子。`);
        return createResponse(true, "成功浏览主页信息流", { posts: posts });
    }

    async function search_content(params: { keyword: string; search_type?: string }): Promise<ToolResponse> {
        const { keyword, search_type = "comprehensive" } = params;
        console.log(`搜索内容: ${keyword}, 类型: ${search_type}`);

        // 确保小红书已在前台运行
        if (!await ensureMain()) {
            return createResponse(false, "无法启动或切换到小红书");
        }

        // 1. 点击主页右上角的搜索图标
        let page = await UINode.getCurrentPage();
        const searchIcon = page.findByContentDesc("搜索");

        if (!searchIcon) {
            return createResponse(false, "在主页上找不到带 '搜索' contentDesc 的图标");
        }
        await searchIcon.click();
        await Tools.System.sleep(2000); // 等待搜索页面加载

        // 2. 在搜索页面，找到输入框并输入文字
        page = await UINode.getCurrentPage();
        const searchInput = page.findByClass("EditText");

        if (!searchInput) {
            return createResponse(false, "在搜索页面找不到输入框");
        }

        await searchInput.click();
        await Tools.System.sleep(1000);
        await Tools.UI.setText(keyword);
        await Tools.System.sleep(500);

        // 3. 点击“搜索”按钮
        page = await UINode.getCurrentPage();
        const searchButtons = page.findAllByText("搜索");
        const searchButton = searchButtons.at(-1); // 通常，实际的搜索按钮是最后一个

        if (!searchButton) {
            console.log("找不到文本为'搜索'的按钮，尝试按回车键");
            await Tools.UI.pressKey("KEYCODE_ENTER");
        } else {
            await searchButton.click();
        }
        await Tools.System.sleep(3000); // 等待搜索结果加载

        // 应用搜索类型过滤
        if (search_type !== "comprehensive") {
            await applySearchFilter(search_type);
        }

        // 获取搜索结果
        const results = await getSearchResults();

        return createResponse(true, `搜索到 ${results.length} 条相关内容`, {
            keyword: keyword,
            search_type: search_type,
            results: results.map(({ element, ...rest }) => rest), // 移除element属性
            result_count: results.length
        });
    }

    async function applySearchFilter(filterType: string): Promise<void> {
        console.log(`应用搜索过滤器: ${filterType}`);

        const page = await UINode.getCurrentPage();
        let filterButton;

        switch (filterType) {
            case "note":
                filterButton = page.findByText("笔记");
                break;
            case "user":
                filterButton = page.findByText("用户");
                break;
            case "topic":
                filterButton = page.findByText("话题");
                break;
        }

        if (filterButton) {
            await filterButton.click();
            await Tools.System.sleep(2000);
        }
    }

    function extractPostInfoFromNode(node: UINode, index: number): PostInfo | null {
        // 改进视频帖子过滤逻辑
        const relativeLayout = node.findByClass("RelativeLayout");
        if (relativeLayout) {
            const directImageViews = relativeLayout.children.filter(child => child.className === "ImageView");
            if (directImageViews.length > 1) {
                // console.log("检测到视频帖子（多个直接ImageView子节点），已跳过。");
                return null;
            }
        }

        const allTextViews = node.findAllByClass("TextView");

        // 过滤广告
        if (allTextViews.some(tv => tv.text === "广告")) {
            // console.log("检测到广告，已跳过。")

            return null;
        }

        if (allTextViews.length < 2) return null; // 需要标题和其他信息

        // 1. 寻找标题：通常是当中最长的文本
        const sortedByLength = [...allTextViews].sort((a, b) => (b.text?.length || 0) - (a.text?.length || 0));
        const titleNode = sortedByLength[0];
        if (!titleNode || !titleNode.text || titleNode.text.length < 5) return null;
        const title = titleNode.text;

        const otherTextViews = allTextViews.filter(tv => tv !== titleNode);

        // 2. 寻找点赞数：包含数字，可能带“万”
        const likesRegex = /^\s*\d+(\.\d+)?\s*万?\s*$/;
        const likesNode = otherTextViews.find(tv => tv.text && likesRegex.test(tv.text.trim()));
        const likes = likesNode?.text?.trim() || "未知";

        // 3. 寻找作者：不是点赞数，也不是日期
        const dateRegex = /^\d{2,4}-\d{2}(-\d{2})?$/;
        const authorNode = otherTextViews.find(tv =>
            tv.text &&
            tv !== likesNode &&
            !dateRegex.test(tv.text.trim())
        );
        const author = authorNode?.text?.trim() || "未知作者";

        if (title === author) {
            return null;
        }

        return {
            index,
            title,
            author,
            likes,
            element: node,
        };
    }


    async function getSearchResults(desiredCount: number = 15): Promise<PostInfo[]> {
        const results: PostInfo[] = [];
        const seenTitles = new Set<string>();

        const maxScrolls = 5;
        let lastResultCount = -1;
        let scrollCount = 0;

        for (let i = 0; i < maxScrolls && results.length < desiredCount; i++) {
            const page = await UINode.getCurrentPage();
            const resultsContainer = page.findByClass("RecyclerView");

            if (!resultsContainer) {
                console.log("未找到搜索结果容器 (RecyclerView)");
                if (i === 0) return [];
                else break;
            }

            for (const item of resultsContainer.children) {
                const postInfo = extractPostInfoFromNode(item, results.length + 1);
                if (postInfo && !seenTitles.has(postInfo.title)) {
                    seenTitles.add(postInfo.title);
                    results.push(postInfo);
                    if (results.length >= desiredCount) break;
                }
            }

            if (results.length >= desiredCount) break;

            if (results.length === lastResultCount) {
                console.log("滚动未产生新结果，停止滚动");
                break;
            }
            lastResultCount = results.length;

            await Tools.UI.swipe(540, 1500, 540, 800);
            await Tools.System.sleep(2000);
            scrollCount++;
        }

        console.log(`Scrolling back up ${scrollCount} times.`);
        for (let i = 0; i < scrollCount; i++) {
            await Tools.UI.swipe(540, 800, 540, 1550);
            await Tools.System.sleep(1000);
        }
        return results;
    }

    async function view_post(params: { post_title?: string; post_index?: number }): Promise<ToolResponse> {
        const { post_title, post_index } = params;

        if (!post_title && !post_index) {
            return createResponse(false, "请提供帖子标题或索引");
        }

        console.log(`尝试查看帖子 - 标题: "${post_title}", 索引: ${post_index}`);

        let targetPost: { title: string; item: UINode } | null = null;
        let currentIndex = 0;
        const seenTitles = new Set<string>();
        const maxScrolls = 5;

        for (let i = 0; i < maxScrolls; i++) {
            const page = await UINode.getCurrentPage();
            const resultsContainer = page.findByClass("RecyclerView");

            if (!resultsContainer) {
                if (i === 0) return createResponse(false, "未找到帖子列表容器 (RecyclerView)");
                else break;
            }

            for (const item of resultsContainer.children) {
                const postInfo = extractPostInfoFromNode(item, 0); // index doesn't matter here
                if (postInfo && !seenTitles.has(postInfo.title)) {
                    seenTitles.add(postInfo.title);
                    currentIndex++;

                    let shouldClick = false;
                    if (post_index && currentIndex === post_index) {
                        shouldClick = true;
                    } else if (post_title && postInfo.title.includes(post_title)) {
                        shouldClick = true;
                    }

                    if (shouldClick) {
                        targetPost = { title: postInfo.title, item: postInfo.element };
                        break;
                    }
                }
            }

            if (targetPost) break;

            await Tools.UI.swipe(540, 1500, 540, 800);
            await Tools.System.sleep(2000);
        }


        if (targetPost) {
            await targetPost.item.click();
            await Tools.System.sleep(3000); // 等待帖子详情页加载
            let res = await get_post_info({});

            return createResponse(true, `成功打开帖子并完成内容加载: "${targetPost.title}"`, res);
        } else {
            return createResponse(false, "未找到指定的帖子", { post_title, post_index });
        }
    }

    async function isInPostDetail(): Promise<boolean> {
        const page = await UINode.getCurrentPage();
        // 检测是否在帖子详情页的特征元素
        const likeButton = page.findByContentDesc("点赞") ||
            page.findByText("赞") ||
            page.find(n => n.contentDesc && n.contentDesc.includes("点赞"));
        const commentButton = page.findByContentDesc("评论") ||
            page.findByText("评论") ||
            page.find(n => n.contentDesc && n.contentDesc.includes("评论"));

        return !!(likeButton || commentButton);
    }

    async function findLikeButton(): Promise<UINode | null> {
        const page = await UINode.getCurrentPage();
        const button = page.findByContentDesc("点赞") ||
            page.findByText("赞") ||
            page.find(n => n.contentDesc && n.contentDesc.includes("点赞"));
        return button || null;
    }

    async function findCollectButton(): Promise<UINode | null> {
        const page = await UINode.getCurrentPage();
        const button = page.findByContentDesc("收藏") ||
            page.findByText("收藏") ||
            page.find(n => n.contentDesc && n.contentDesc.includes("收藏"));
        return button || null;
    }

    async function findCommentButton(): Promise<UINode | null> {
        const page = await UINode.getCurrentPage();
        const button = page.findByContentDesc("评论") ||
            page.findByText("评论") ||
            page.find(n => n.contentDesc && n.contentDesc.includes("评论"));
        return button || null;
    }

    async function like_post(params: {}): Promise<ToolResponse> {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }

        console.log("给帖子点赞");

        const likeButton = await findLikeButton();

        if (likeButton) {
            await likeButton.click();
            await Tools.System.sleep(1000);
            return createResponse(true, "点赞成功");
        } else {
            return createResponse(false, "未找到点赞按钮");
        }
    }

    async function collect_post(params: { collection_name?: string }): Promise<ToolResponse> {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }

        const { collection_name } = params;
        console.log(`收藏帖子到: ${collection_name || "默认收藏夹"}`);

        const collectButton = await findCollectButton();

        if (collectButton) {
            await collectButton.click();
            await Tools.System.sleep(2000);

            // 如果指定了收藏夹名称，尝试选择
            if (collection_name) {
                const collectionPage = await UINode.getCurrentPage();
                const targetCollection = collectionPage.findByText(collection_name);
                if (targetCollection) {
                    await targetCollection.click();
                    await Tools.System.sleep(1000);
                }
            }

            return createResponse(true, "收藏成功", { collection_name });
        } else {
            return createResponse(false, "未找到收藏按钮", { collection_name });
        }
    }

    async function follow_user(params: {}): Promise<ToolResponse> {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }

        console.log("关注用户");

        const page = await UINode.getCurrentPage();
        const followButton = page.findByText("关注");

        if (followButton) {
            await followButton.click();
            await Tools.System.sleep(1000);
            return createResponse(true, "关注成功");
        } else {
            return createResponse(false, "未找到关注按钮或已经关注");
        }
    }
    async function unfollow_user(params: {}): Promise<ToolResponse> {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }

        console.log("不关注用户");

        const page = await UINode.getCurrentPage();
        const followButton = page.findByText("已关注");

        if (followButton) {
            await followButton.click();
            await Tools.System.sleep(1000);
            (await UINode.getCurrentPage()).findByText("不再关注")?.click();

            return createResponse(true, "取消关注成功");
        } else {
            return createResponse(false, "未找到不关注按钮或已经不关注");
        }
    }

    async function comment_post(params: { comment_text: string }): Promise<ToolResponse> {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }

        const { comment_text } = params;
        console.log(`发表评论: ${comment_text}`);

        const page = await UINode.getCurrentPage();
        const commentBox = page.findByContentDesc("评论框")

        if (commentBox) {
            await commentBox.click();
            await Tools.System.sleep(1000);

            // 输入评论内容
            await Tools.UI.setText(comment_text);
            await Tools.System.sleep(500);

            // 查找发送按钮
            const sendButton = (await UINode.getCurrentPage()).findByText("发送")

            if (sendButton) {
                await sendButton.click();
                await Tools.System.sleep(2000);
                return createResponse(true, "评论发表成功", { comment_text });
            } else {
                return createResponse(false, "未找到发送按钮", { comment_text });
            }
        } else {
            return createResponse(false, "未找到评论输入框", { comment_text });
        }
    }

    async function get_post_info(params: GetPostInfoParams): Promise<ToolResponse> {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }
        console.log("获取帖子信息...");

        // Initial scroll to load content below the media
        await Tools.UI.swipe(540, 1600, 540, 1000);
        await Tools.System.sleep(1500);

        const page = await UINode.getCurrentPage();

        const postInfo: {
            title: string;
            author: string;
            content: string;
            like_count: string;
            comment_count: string;
            collect_count: string;
            publish_date: string;
            comments: Comment[];
        } = {
            title: "未知",
            author: "未知",
            content: "",
            like_count: "未知",
            comment_count: "未知",
            collect_count: "未知",
            publish_date: "未知",
            comments: [],
        };

        // 1. Parse static information first
        const authorButton = page.find(n => n.contentDesc && n.contentDesc.startsWith("作者,"));
        let authorTextView: UINode | undefined;
        if (authorButton) {
            authorTextView = authorButton.findByClass("TextView") ?? undefined;
            if (authorTextView && authorTextView.text) {
                postInfo.author = authorTextView.text;
            } else if (authorButton.contentDesc) {
                const parts = authorButton.contentDesc.split(',');
                if (parts.length > 1) {
                    postInfo.author = parts[1].trim();
                }
            }
        }

        const allNodesWithDesc = page.findAll(n => !!n.contentDesc);
        const dateNode = allNodesWithDesc.find(n => n.contentDesc && /^\d{2}-\d{2}$/.test(n.contentDesc.trim()));
        if (dateNode && dateNode.contentDesc) {
            postInfo.publish_date = dateNode.contentDesc.trim();
        }

        const findCountFromButton = (button: UINode | null): string => {
            if (!button) return "未知";
            if (button.contentDesc) {
                const match = button.contentDesc.match(/(\d+(\.\d+)?[万wW]?)/);
                if (match && match[1]) return match[1];
            }
            const countNode = button.findByClass("TextView");
            if (countNode && countNode.text && /^\d+(\.\d+)?[万wW]?$/.test(countNode.text.trim())) {
                return countNode.text.trim();
            }
            return "未知";
        };

        postInfo.like_count = findCountFromButton(await findLikeButton());
        postInfo.comment_count = findCountFromButton(await findCommentButton());
        postInfo.collect_count = findCountFromButton(await findCollectButton());

        // 2. Identify comment section to isolate title/content
        const allRecyclerViews = page.findAllByClass("RecyclerView");
        const commentListInitial = allRecyclerViews.find(rv => {
            const parent = rv.parent;
            return !(parent && parent.className === 'FrameLayout' && parent.contentDesc && (parent.contentDesc.includes("图片") || parent.contentDesc.includes("视频")));
        });

        // 3. Extract title and content
        const commentTextViews = new Set(commentListInitial ? commentListInitial.findAllByClass("TextView") : []);
        const allTextViews = page.findAllByClass("TextView");

        const mainTextViews = allTextViews.filter(tv => {
            return tv && !commentTextViews.has(tv);
        });

        const ignoredTexts = new Set(['关注', '赞', '评论', '收藏', '翻译', '说点什么...', postInfo.author]);
        if (postInfo.author === '未知') {
            ignoredTexts.delete('未知');
        }

        const contentTextNodes = mainTextViews.filter(tv => {
            if (!tv.text) return false;
            const text = tv.text.trim();
            return text.length > 0 && !ignoredTexts.has(text) && !/^\d+(\.\d+)?[万wW]?$/.test(text) && tv !== authorTextView;
        });

        if (contentTextNodes.length > 0) {
            postInfo.title = contentTextNodes[0].text!.trim();
            if (contentTextNodes.length > 1) {
                postInfo.content = contentTextNodes.slice(1).map(tv => tv.text!.trim()).join('\n');
            }
        }

        // 4. Scroll and parse comments iteratively
        console.log("开始滚动并解析评论...");
        const seenComments = new Set<string>();
        let stableScrolls = 0;
        const maxScrolls = 10;

        for (let i = 0; i < maxScrolls; i++) {
            const currentCommentCount = postInfo.comments.length;
            const currentPage = await UINode.getCurrentPage();

            const commentList = currentPage.findAllByClass("RecyclerView").find(rv => {
                const parent = rv.parent;
                return !(parent && parent.className === 'FrameLayout' && parent.contentDesc && (parent.contentDesc.includes("图片") || parent.contentDesc.includes("视频")));
            });

            if (commentList) {
                for (const item of commentList.children) {
                    if (postInfo.comments.length >= 20) break;

                    const textViews = item.findAllByClass("TextView");
                    if (textViews.length < 2) continue;

                    const comment: Comment = { author: "未知", content: "未知", likes: "0", date: "未知" };

                    const likesRegex = /^\d+(\.\d+)?[万wW]?$/;
                    const likesNode = item.find(n => n.className === 'TextView' && n.text && likesRegex.test(n.text));
                    comment.likes = likesNode?.text ?? "0";

                    const contentCandidates = textViews.filter(tv => tv.text && tv !== likesNode);
                    if (contentCandidates.length === 0) continue;

                    contentCandidates.sort((a, b) => (b.text?.length ?? 0) - (a.text?.length ?? 0));

                    let contentText = contentCandidates[0].text ?? "";
                    const authorNode = contentCandidates.length > 1 ? contentCandidates[1] : undefined;

                    comment.author = authorNode?.text?.replace("作者", "").trim() || "未知";

                    const dateRegex = /(\d{2}-\d{2}( \d{2}:\d{2})?|\d+天前|昨天 \d{2}:\d{2})/;
                    let dateMatch = contentText.match(dateRegex);
                    if (dateMatch) {
                        comment.date = dateMatch[0];
                        comment.content = contentText.replace(dateRegex, ' ').replace(/回复$/, '').trim();
                    } else {
                        comment.content = contentText.replace(/回复$/, '').trim();
                    }

                    if (comment.content.startsWith(comment.author)) {
                        comment.content = comment.content.substring(comment.author.length).trim();
                    }

                    if (comment.content === comment.author || !comment.content) continue;

                    const uniqueKey = `${comment.author}:${comment.content}`;
                    if (comment.content && comment.content !== "未知" && !seenComments.has(uniqueKey)) {
                        seenComments.add(uniqueKey);
                        postInfo.comments.push(comment);
                    }
                }
            }

            if (postInfo.comments.length >= 20) {
                console.log("已收集到20条评论，停止滚动。");
                break;
            }

            if (postInfo.comments.length === currentCommentCount) {
                stableScrolls++;
                if (stableScrolls >= 2) {
                    console.log("滚动两次未发现新评论，停止。");
                    break;
                }
            } else {
                stableScrolls = 0;
            }

            console.log(`第 ${i + 1}/${maxScrolls} 次滚动... 当前评论数: ${postInfo.comments.length}`);
            await Tools.UI.swipe(540, 1600, 540, 800);
            await Tools.System.sleep(2000);
        }

        return createResponse(true, "获取帖子信息成功", { post_info: postInfo });
    }

    async function publish_post(params: { content_text: string; image_paths?: string; tags?: string; location?: string }): Promise<ToolResponse> {
        const { content_text, image_paths, tags, location } = params;

        console.log(`发布帖子: ${content_text.substring(0, 20)}...`);

        // 确保小红书已在前台运行
        if (!await ensureMain()) {
            return createResponse(false, "无法启动或切换到小红书");
        }

        // 点击发布按钮（通常在底部导航栏中间）
        const page = await UINode.getCurrentPage();
        const publishButton = page.findByText("发布") ||
            page.findByText("+") ||
            page.findByContentDesc("发布");

        if (!publishButton) {
            return createResponse(false, "未找到发布按钮");
        }

        await publishButton.click();
        await Tools.System.sleep(3000);

        // 选择发布类型（如果需要）
        const noteOption = (await UINode.getCurrentPage()).findByText("笔记") ||
            (await UINode.getCurrentPage()).findByText("图文");
        if (noteOption) {
            await noteOption.click();
            await Tools.System.sleep(2000);
        }

        // 如果提供了图片路径，添加图片
        if (image_paths) {
            const imagePaths = image_paths.split(',');
            for (const imagePath of imagePaths) {
                // 这里需要根据实际的图片选择界面来实现
                console.log(`添加图片: ${imagePath.trim()}`);
                // 实际实现中需要打开相册、选择图片等步骤
            }
        }

        // 输入文字内容
        const pageForInput = await UINode.getCurrentPage();
        const textInput = pageForInput.findByClass("EditText") ||
            pageForInput.findByText("分享你的生活...");

        if (textInput) {
            await textInput.click();
            await Tools.System.sleep(1000);
            await Tools.UI.setText(content_text);
            await Tools.System.sleep(1000);
        }

        // 添加标签
        if (tags) {
            const tagList = tags.split(',');
            for (const tag of tagList) {
                const tagText = tag.trim();
                if (tagText) {
                    // 在文本末尾添加话题标签
                    await Tools.UI.setText(` #${tagText}`);
                    await Tools.System.sleep(500);
                }
            }
        }

        // 添加位置信息
        if (location) {
            const locationButton = (await UINode.getCurrentPage()).findByText("添加地点");
            if (locationButton) {
                await locationButton.click();
                await Tools.System.sleep(2000);
                // 搜索并选择位置
                await Tools.UI.setText(location);
                await Tools.System.sleep(1000);
                const firstLocation = (await UINode.getCurrentPage()).findByClass("TextView");
                if (firstLocation && firstLocation.text && firstLocation.text.includes(location)) {
                    await firstLocation.click();
                    await Tools.System.sleep(1000);
                }
            }
        }

        // 发布帖子
        const finalPublishButton = (await UINode.getCurrentPage()).findByText("发布");

        if (finalPublishButton) {
            await finalPublishButton.click();
            await Tools.System.sleep(5000); // 等待发布完成

            return createResponse(true, "帖子发布成功", {
                content_text: content_text,
                image_paths: image_paths,
                tags: tags,
                location: location
            });
        } else {
            return createResponse(false, "未找到最终发布按钮", {
                content_text: content_text
            });
        }
    }

    async function navigate_to_home(params: {}): Promise<ToolResponse> {
        console.log("导航到首页");

        if (await ensureMain()) {
            const page = await UINode.getCurrentPage();
            const homeTab = page.findByText("首页");
            if (homeTab) {
                await homeTab.click();
                await Tools.System.sleep(1000);
            }
            return createResponse(true, "已导航到首页");
        } else {
            return createResponse(false, "无法导航到首页");
        }
    }

    async function navigate_to_profile(params: {}): Promise<ToolResponse> {
        console.log("导航到个人主页");

        if (await ensureMain()) {
            const page = await UINode.getCurrentPage();
            const profileTab = page.findByText("我") ||
                page.findByText("个人主页");
            if (profileTab) {
                await profileTab.click();
                await Tools.System.sleep(2000);
                return createResponse(true, "已导航到个人主页");
            } else {
                return createResponse(false, "未找到个人主页标签");
            }
        } else {
            return createResponse(false, "无法导航到个人主页");
        }
    }

    async function navigate_to_search(params: {}): Promise<ToolResponse> {
        console.log("导航到搜索页面");

        if (await ensureMain()) {
            const page = await UINode.getCurrentPage();
            const searchTab = page.findByText("搜索") ||
                page.findByContentDesc("搜索");

            if (searchTab) {
                await searchTab.click();
                await Tools.System.sleep(1500);
                return createResponse(true, "已导航到搜索页面");
            } else {
                // 尝试点击顶部搜索区域
                await Tools.UI.tap(540, 200);
                await Tools.System.sleep(1500);
                return createResponse(true, "已打开搜索页面");
            }
        } else {
            return createResponse(false, "无法导航到搜索页面");
        }
    }

    async function navigate_to_publish(params: {}): Promise<ToolResponse> {
        console.log("导航到发布页面");

        if (await ensureMain()) {
            const page = await UINode.getCurrentPage();
            const publishTab = page.findByText("发布") ||
                page.findByText("+");

            if (publishTab) {
                await publishTab.click();
                await Tools.System.sleep(2000);
                return createResponse(true, "已导航到发布页面");
            } else {
                return createResponse(false, "未找到发布按钮");
            }
        } else {
            return createResponse(false, "无法导航到发布页面");
        }
    }

    async function back_to_feed(params: {}): Promise<ToolResponse> {
        console.log("从帖子详情页返回到信息流");

        if (await isInPostDetail()) {
            await Tools.UI.pressKey("KEYCODE_BACK");
            await Tools.System.sleep(2000);
            return createResponse(true, "已返回到信息流列表");
        } else {
            return createResponse(true, "当前不在帖子详情页，无需返回");
        }
    }

    async function wrapToolExecution<P>(func: (params: P) => Promise<ToolResponse | void>, params: P) {
        try {
            const result = await func(params);
            if (result) {
                complete(result);
            } else {
                complete({ success: true, message: "操作成功完成但无返回数据。" });
            }
        } catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error instanceof Error ? error.message : String(error)}`,
            });
        }
    }

    async function main(params: {}): Promise<void> {
        console.log("=== 小红书智能助手测试 ===");
        console.log("测试搜索功能...");
        // let res = await search_content({ keyword: "美食" });
        // console.log(JSON.stringify(res, null, 2));

        // if (res.success && (res.result_count as number) > 0) {
        //     console.log("测试查看帖子功能...");
        //     let view_res = await view_post({ post_index: 10 });
        //     console.log(JSON.stringify(view_res, null, 2));
        // } else {
        //     console.log("搜索无结果，跳过查看帖子测试。");
        // }
        console.log(JSON.stringify(await get_post_info({}), null, 2));
    }

    // Tool parameter interfaces
    interface BrowseHomeFeedParams { scroll_count?: number; collect_posts?: boolean; }
    interface SearchContentParams { keyword: string; search_type?: string; }
    interface ViewPostParams { post_title?: string; post_index?: number; }
    interface LikePostParams { }
    interface CollectPostParams { collection_name?: string; }
    interface FollowUserParams { }
    interface UnfollowUserParams { }
    interface CommentPostParams { comment_text: string; }
    interface GetPostInfoParams { }
    interface PublishPostParams { content_text: string; image_paths?: string; tags?: string; location?: string; }
    interface NavigateToHomeParams { }
    interface NavigateToProfileParams { }
    interface NavigateToSearchParams { }
    interface NavigateToPublishParams { }
    interface BackToFeedParams { }
    interface MainParams { }

    // 导出所有工具
    return {
        browse_home_feed: async (params: BrowseHomeFeedParams) => await wrapToolExecution(browse_home_feed, params),
        search_content: async (params: SearchContentParams) => await wrapToolExecution(search_content, params),
        view_post: async (params: ViewPostParams) => await wrapToolExecution(view_post, params),
        like_post: async (params: LikePostParams) => await wrapToolExecution(like_post, params),
        collect_post: async (params: CollectPostParams) => await wrapToolExecution(collect_post, params),
        follow_user: async (params: FollowUserParams) => await wrapToolExecution(follow_user, params),
        unfollow_user: async (params: UnfollowUserParams) => await wrapToolExecution(unfollow_user, params),
        comment_post: async (params: CommentPostParams) => await wrapToolExecution(comment_post, params),
        get_post_info: async (params: GetPostInfoParams) => await wrapToolExecution(get_post_info, params),
        publish_post: async (params: PublishPostParams) => await wrapToolExecution(publish_post, params),
        navigate_to_home: async (params: NavigateToHomeParams) => await wrapToolExecution(navigate_to_home, params),
        navigate_to_profile: async (params: NavigateToProfileParams) => await wrapToolExecution(navigate_to_profile, params),
        navigate_to_search: async (params: NavigateToSearchParams) => await wrapToolExecution(navigate_to_search, params),
        navigate_to_publish: async (params: NavigateToPublishParams) => await wrapToolExecution(navigate_to_publish, params),
        back_to_feed: async (params: BackToFeedParams) => await wrapToolExecution(back_to_feed, params),
        main: async (params: MainParams) => await wrapToolExecution(main, params),
    };
})();

// 逐个导出，以便在外部调用
exports.browse_home_feed = XiaohongshuAssistant.browse_home_feed;
exports.search_content = XiaohongshuAssistant.search_content;
exports.view_post = XiaohongshuAssistant.view_post;
exports.like_post = XiaohongshuAssistant.like_post;
exports.collect_post = XiaohongshuAssistant.collect_post;
exports.follow_user = XiaohongshuAssistant.follow_user;
exports.unfollow_user = XiaohongshuAssistant.unfollow_user;
exports.comment_post = XiaohongshuAssistant.comment_post;
exports.get_post_info = XiaohongshuAssistant.get_post_info;
exports.publish_post = XiaohongshuAssistant.publish_post;
exports.navigate_to_home = XiaohongshuAssistant.navigate_to_home;
exports.navigate_to_profile = XiaohongshuAssistant.navigate_to_profile;
exports.navigate_to_search = XiaohongshuAssistant.navigate_to_search;
exports.navigate_to_publish = XiaohongshuAssistant.navigate_to_publish;
exports.back_to_feed = XiaohongshuAssistant.back_to_feed;
exports.main = XiaohongshuAssistant.main; 