/*
METADATA
{
    // 百度地图智能助手
    name: "Automatic_baiduMap_assistant",
    display_name: {
      zh: "百度地图自动化助手"
      en: "Baidu Map Automation Assistant"
    }description: { zh: "高级百度地图智能助手，通过UI自动化技术实现地图交互，支持地点搜索、路线规划、周边查询等功能，为AI提供强大的地理位置服务能力。适用于出行规划、信息查询、智能问答等场景。", en: "Advanced Baidu Maps assistant powered by UI automation. Supports place search, navigation/route planning, and nearby discovery, enabling AI-driven location services. Useful for trip planning, information lookup, and Q&A." },
 
    // Tools in this package
    category: "Automatic"
    tools: [
        {
            name: "workflow_guide",
            description: { zh: "百度地图助手工具使用流程指南。要完成复杂任务，请按以下顺序组合使用工具：\n1. **搜索地点**: 使用 `search_location` 查找地点、餐馆、公司等，这是多数操作的起点。\n2. **选择地点**: 如果 `search_location` 返回多个结果，使用 `select_location_from_list` 从列表中选择一个。\n3. **开始导航**: 搜索到唯一地点或从列表中选择地点后，使用 `go_to_location` 来规划到该地的路线。\n4. **周边搜索**: 使用 `search_nearby` 查找当前位置或指定地点附近的设施。\n- **随时导航**: `navigate_to_home` 可随时返回地图主页。", en: "Workflow guide for the Baidu Maps assistant. For complex tasks, combine tools in this order:\n1. **Search**: use `search_location` to find places/restaurants/companies (starting point for most tasks).\n2. **Select**: if `search_location` returns multiple results, use `select_location_from_list` to pick one from the list.\n3. **Navigate**: after a unique result is found or selected, use `go_to_location` to plan a route.\n4. **Nearby**: use `search_nearby` to find facilities near your current location or a specified place.\n- **Go home anytime**: use `navigate_to_home` to return to the map home screen." }
            parameters: []
            advice: true
        },
        {
            name: "search_location",
            description: { zh: "在百度地图中搜索地点、餐馆、公司等信息。", en: "Search places, restaurants, companies, and other POIs in Baidu Maps." },
            parameters: [
                {
                    name: "keyword",
                    description: { zh: "要搜索的地点关键词", en: "Keyword of the place to search for." },
                    type: "string",
                    required: true
                }
            ]
        },
        {
            name: "get_directions",
            description: { zh: "规划从起点到终点的路线。", en: "Plan a route from a start point to a destination." },
            parameters: [
                {
                    name: "start_point",
                    description: { zh: "路线起点，默认为'我的位置'", en: "Route start point (default: 'My location')." },
                    type: "string",
                    required: false
                },
                {
                    name: "end_point",
                    description: { zh: "路线终点", en: "Route destination." },
                    type: "string",
                    required: true
                },
                {
                    name: "transport_mode",
                    description: { zh: "交通方式：driving(驾车), transit(公交), walking(步行), cycling(骑行)", en: "Transport mode: driving, transit, walking, cycling." },
                    type: "string",
                    required: false
                }
            ]
        },
        {
            name: "select_location_from_list",
            description: { zh: "从 'search_location' 返回的搜索结果列表中选择一个地点。", en: "Select a place from the result list returned by `search_location`." },
            parameters: [
                {
                    name: "keyword",
                    description: { zh: "要选择的地点名称关键词", en: "Keyword to match the place name to select." },
                    type: "string",
                    required: false
                },
                {
                    name: "index",
                    description: { zh: "要选择的地点在列表中的索引位置（从1开始）", en: "Index of the item in the list (1-based)." },
                    type: "number",
                    required: false
                }
            ]
        },
        {
            name: "go_to_location",
            description: { zh: "在地点详情页点击“到这去”，以开启导航或路线规划。可以选择交通方式。", en: "On the place detail page, tap \"Go\" (到这去) to start navigation/route planning. You can optionally select a transport mode." },
            parameters: [
                {
                    name: "transport_mode",
                    description: { zh: "交通方式：新能源, 驾车, 打车, 公共交通, 代驾, 骑行, 步行, 拼车, 飞机, 火车, 客车, 摩托车", en: "Transport mode tab label in the app (e.g., 新能源, 驾车, 打车, 公共交通, 代驾, 骑行, 步行, 拼车, 飞机, 火车, 客车, 摩托车)." },
                    type: "string",
                    required: false
                }
            ]
        },
        {
            name: "search_nearby",
            description: { zh: "搜索中心点附近的地点信息。", en: "Search places around a center point." },
            parameters: [
                {
                    name: "keyword",
                    description: { zh: "要搜索的周边设施关键词（如：银行, 加油站）", en: "Nearby keyword (e.g., bank, gas station)." },
                    type: "string",
                    required: true
                },
                {
                    name: "center_point",
                    description: { zh: "搜索的中心点，默认为'我的位置'", en: "Center point for the search (default: 'My location')." },
                    type: "string",
                    required: false
                }
            ]
        },
        {
            name: "navigate_to_home",
            description: { zh: "返回百度地图的主界面。", en: "Return to the Baidu Maps home screen." },
            parameters: []
        },
        {
            name: "activate_voice_assistant",
            description: { zh: "在主界面点击语音搜索按钮，启动“小度”语音助手。", en: "Tap the voice search button on the home screen to launch the Xiaodu voice assistant." },
            parameters: []
        }
    ]
}*/

interface Array<T> {
    at(index: number): T | undefined;
}

const BaiduMapAssistant = (function () {

    // 添加 Array.prototype.at 支持
    if (!Array.prototype.at) {
        Object.defineProperty(Array.prototype, 'at', {
            value: function (index: number) {
                if (index < 0) {
                    index += this.length;
                }
                return this[index];
            },
            enumerable: false,
            configurable: true,
            writable: true
        });
    }

    // 百度地图应用包名
    const BAIDU_MAP_PACKAGE = "com.baidu.BaiduMap";
    const MAIN_ACTIVITY = "com.baidu.baidumaps.MapsActivity";

    // Helper to create response objects
    function createResponse(success: boolean, message: string, data: object | string = {}) {
        if (typeof data === 'string') {
            return { success, message, data };
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

    async function isOnMainPage(): Promise<boolean> {
        const page = await UINode.getCurrentPage();
        // Check for the presence of key elements that indicate the main map screen.
        const drivingButton = page.findByText("驾车");
        const transitButton = page.findByText("公共交通");
        const taxiButton = page.findByText("打车");
        const hotelButton = page.findByText("订酒店");

        if (drivingButton && transitButton && taxiButton && hotelButton) {
            console.log("Detected Baidu Map main page by key elements.");
            return true;
        }
        console.log("Not on main page, key elements not found.");
        return false;
    }

    async function ensureMain(packageName: string = BAIDU_MAP_PACKAGE): Promise<boolean> {
        let pageInfo = await Tools.UI.getPageInfo();

        // 1. If not in the correct app, start it.
        if (pageInfo.packageName !== packageName) {
            console.log(`Not in the correct app. Current: ${pageInfo.packageName}. Starting ${packageName}.`);
            await Tools.System.startApp(packageName);
            await Tools.System.sleep(5000); // Wait for app to load
            pageInfo = await Tools.UI.getPageInfo(); // Re-check page info
            if (pageInfo.packageName !== packageName) {
                console.error(`Failed to start ${packageName}.`);
                return false;
            }
        }

        // 2. Check if we are already on the main page.
        if (await isOnMainPage()) {
            console.log("Already on the main page.");
            return true;
        }

        // 3. If not on the main page, try pressing back up to 4 times.
        console.log("Not on the main page. Attempting to go back.");
        for (let i = 0; i < 4; i++) {
            await Tools.UI.pressKey("KEYCODE_BACK");
            await Tools.System.sleep(1000); // Wait for UI to settle

            pageInfo = await Tools.UI.getPageInfo();
            // If we have exited the app, stop trying.
            if (pageInfo.packageName !== packageName) {
                console.log("Exited the app while trying to go back.");
                break; // Exit the loop, will proceed to restart
            }

            if (await isOnMainPage()) {
                console.log(`Successfully returned to main page on attempt ${i + 1}.`);
                return true;
            }
        }

        // 4. If pressing back failed, perform a full app restart.
        console.log("Pressing back failed or exited app. Performing a full app restart.");
        await Tools.System.stopApp(packageName);
        await Tools.System.sleep(1000);
        await Tools.System.startApp(packageName);
        await Tools.System.sleep(5000); // Longer wait for cold start

        pageInfo = await Tools.UI.getPageInfo();
        if (pageInfo.packageName !== packageName) {
            console.error(`Failed to restart ${packageName}.`);
            return false;
        }

        if (await isOnMainPage()) {
            console.log("Successfully reached main page after restart.");
            return true;
        }

        console.error(`Failed to navigate to the main page of ${packageName} even after restart.`);
        return false;
    }

    async function search_location(params: { keyword: string }): Promise<any> {
        const { keyword } = params;
        console.log(`Searching for location: ${keyword}`);

        // 确保百度地图已在前台运行
        if (!await ensureMain()) {
            return createResponse(false, "无法启动或切换到百度地图");
        }

        await Tools.System.sleep(2000); // 等待首页完全加载

        // 查找并点击搜索框
        const page = await UINode.getCurrentPage();
        const searchBox = page.findById('serachbox_container');
        if (!searchBox) {
            return createResponse(false, "在地图主页未找到搜索框 (ID: serachbox_container)");
        }
        await searchBox.click();
        await Tools.System.sleep(300);
        await Tools.UI.setText(keyword);
        await Tools.System.sleep(300);


        // 点击搜索按钮
        const searchButton = (await UINode.getCurrentPage()).findByText("搜索");
        if (searchButton) {
            await searchButton.click();
        }

        await Tools.System.sleep(5000); // 等待搜索结果加载

        // 上拉以显示完整的结果列表，这在某些情况下是必要的
        console.log("执行上拉手势以确保结果列表完全可见...");
        await Tools.UI.swipe(540, 1800, 540, 900); // 从屏幕底部向上滑动
        await Tools.System.sleep(1500); // 等待动画完成

        // 检查页面是唯一结果还是列表
        const pageAfterSearch = await UINode.getCurrentPage();
        const listContainer = pageAfterSearch.findById("com.baidu.BaiduMap:id/talosListContainer");

        if (listContainer) {
            // 情况2: 结果列表
            console.log("检测到列表容器，开始解析结果列表...");
            const results = await get_map_search_results();

            return createResponse(true, `搜索到 ${results.length} 个相关地点。请使用 'select_location_from_list' 选择一个。`, {
                keyword: keyword,
                results: results.map(({ element, ...rest }) => rest), // 移除 element 属性
                result_count: results.length
            });
        }

        const goToButton = pageAfterSearch.findByText("到这去");
        if (goToButton) {
            // 情况1: 唯一结果
            return createResponse(true, `已找到唯一结果 "${keyword}"。您现在可以使用 'go_to_location' 进行导航。`, { keyword });
        } else {
            return createResponse(false, `搜索 "${keyword}" 后既未找到结果列表，也未找到唯一结果。`);
        }
    }

    async function get_map_search_results(desiredCount: number = 20): Promise<any[]> {
        const results: any[] = [];
        const seenTitles = new Set<string>();
        const maxScrolls = 1;
        let lastResultCount = -1;

        for (let i = 0; i < maxScrolls; i++) {
            let page = await UINode.getCurrentPage();
            const resultsContainer = page.findById('com.baidu.BaiduMap:id/talosListContainer');
            if (!resultsContainer) {
                console.log("未找到搜索结果容器 (ID: com.baidu.BaiduMap:id/talosListContainer)");
                break;
            }

            const recyclerView = resultsContainer.findByClass("RecyclerView");
            if (!recyclerView) {
                console.log("未在结果容器中找到 RecyclerView。");
                break;
            }

            // 通过查找所有“到这去”按钮来定位每个列表项
            const goToButtons = recyclerView.findAllByText("到这去");

            for (const button of goToButtons) {
                // 找到按钮所属的、作为RecyclerView直接子节点的那个父节点
                const item = button.closest((node) =>
                    node.parent !== undefined && node.parent.equals(recyclerView)
                );
                if (!item) continue;

                const textViews = item.findAllByClass('TextView');
                const relevantTextViews = textViews.filter(tv => tv.text && tv.text.trim() !== '到这去' && tv.text.trim() !== '');

                if (relevantTextViews.length === 0) continue;

                // 按纵坐标排序，最上面的通常是标题
                relevantTextViews.sort((a, b) => {
                    try {
                        const [, y1a] = a.bounds!.match(/\[\d+,(\d+)\]/)!;
                        const [, y1b] = b.bounds!.match(/\[\d+,(\d+)\]/)!;
                        return parseInt(y1a) - parseInt(y1b);
                    } catch (e) {
                        return 0;
                    }
                });

                const title = relevantTextViews[0].text;
                if (!title || seenTitles.has(title)) {
                    continue;
                }
                seenTitles.add(title);

                // 查找包含地址特征的文本作为地址
                const addressNode = relevantTextViews.find(tv =>
                    tv.text && (tv.text.includes('省') || tv.text.includes('市') || tv.text.includes('区') || tv.text.includes('路') || tv.text.includes('号'))
                );
                const address = addressNode ? addressNode.text : "N/A";

                results.push({
                    index: results.length + 1, // 临时索引
                    title: title,
                    address: address,
                    element: item
                });
            }

            // 滚动前去重并检查是否需要停止
            const uniqueResults = Array.from(new Map(results.map(r => [r.title, r])).values());

            if (uniqueResults.length >= desiredCount) {
                console.log(`已找到 ${uniqueResults.length} 条不重复结果，满足要求。`);
                break;
            }
            if (uniqueResults.length === lastResultCount) {
                console.log("滚动未产生新结果，停止滚动。");
                break;
            }
            lastResultCount = uniqueResults.length;

            // 向下滚动加载更多
            await Tools.UI.swipe(540, 1800, 540, 600);
            await Tools.System.sleep(2000); // 增加等待时间以确保加载
        }

        // 最终去重并重新编号索引
        const finalResults = Array.from(new Map(results.map(item => [item.title, item])).values())
            .map((item, index) => ({ ...item, index: index + 1 }));

        return finalResults.slice(0, desiredCount);
    }

    // --- Placeholder functions for other tools ---
    async function select_location_from_list(params: { keyword?: string, index?: number }): Promise<any> {
        const { keyword, index } = params;

        if (!keyword && !index) {
            return createResponse(false, "请提供地点关键词或索引。");
        }
        console.log(`尝试从列表中选择 - 关键词: "${keyword}", 索引: ${index}`);

        const seenTitles = new Set<string>();
        let currentItemCount = 0;
        const maxScrolls = 10;
        let lastSeenCount = -1;

        for (let i = 0; i < maxScrolls; i++) {
            const page = await UINode.getCurrentPage();
            // In Baidu Maps, the results are often inside a RecyclerView
            const recyclerView = page.findByClass("RecyclerView");
            if (!recyclerView) {
                console.log("未找到 RecyclerView, 无法选择项目。");
                return createResponse(false, "未找到结果列表容器 (RecyclerView)。");
            }

            const goToButtons = recyclerView.findAllByText("到这去");

            for (const button of goToButtons) {
                const item = button.closest((node) =>
                    node.parent !== undefined && node.parent.equals(recyclerView)
                );
                if (!item) continue;

                const textViews = item.findAllByClass('TextView');
                const relevantTextViews = textViews.filter(tv => tv.text && tv.text.trim() !== '到这去' && tv.text.trim() !== '');
                if (relevantTextViews.length === 0) continue;

                relevantTextViews.sort((a, b) => {
                    try {
                        const [, y1a] = a.bounds!.match(/\[\d+,(\d+)\]/)!;
                        const [, y1b] = b.bounds!.match(/\[\d+,(\d+)\]/)!;
                        return parseInt(y1a) - parseInt(y1b);
                    } catch (e) { return 0; }
                });

                const title = relevantTextViews[0].text;
                if (!title || seenTitles.has(title)) {
                    continue;
                }

                seenTitles.add(title);
                currentItemCount++;

                let shouldClick = false;
                if (index) {
                    if (currentItemCount === index) {
                        shouldClick = true;
                    }
                } else if (keyword) {
                    if (title.includes(keyword)) {
                        shouldClick = true;
                    }
                }

                if (shouldClick) {
                    await item.click();
                    await Tools.System.sleep(2000);
                    return createResponse(true, `已成功选择地点: "${title}"`);
                }
            }

            if (seenTitles.size === lastSeenCount) {
                console.log("滚动未产生新结果，停止。");
                break;
            }
            lastSeenCount = seenTitles.size;

            if (index && currentItemCount >= index) {
                // If we're looking for an index and have passed it, no need to scroll more.
                break;
            }

            await Tools.UI.swipe(540, 1800, 540, 600); // Swipe up
            await Tools.System.sleep(2000);
        }

        return createResponse(false, "在列表中未找到或无法选择指定的地点。", { keyword, index });
    }

    async function go_to_location(params: { transport_mode?: string }): Promise<any> {
        const transportMode = params.transport_mode || "驾车"; // 默认为驾车
        console.log("尝试点击 '到这去' 按钮...");
        const page = await UINode.getCurrentPage();
        const goToButton = page.findByText("到这去");
        if (goToButton) {
            await goToButton.click();
            await Tools.System.sleep(3000); // 等待路线规划页面加载

            if (transportMode) {
                console.log(`尝试选择交通方式: ${transportMode}`);
                let pageAfterClick = await UINode.getCurrentPage();

                // 尝试直接点击可见的交通方式按钮
                try {
                    let modeButton = pageAfterClick.findByText(transportMode);
                    if (modeButton) {
                        await modeButton.click();
                        await Tools.System.sleep(1000);
                        return createResponse(true, `已点击“到这去”，并成功切换到“${transportMode}”模式。`);
                    }
                } catch (e) {
                    console.log(`直接点击失败，按钮可能在屏幕外。尝试滚动查找。`);
                }

                // 如果未直接找到，尝试展开更多选项
                console.log(`未直接找到 "${transportMode}"，尝试展开更多选项...`);
                const moreModesButton = pageAfterClick.findById('route_tab_group');
                if (moreModesButton) {
                    await moreModesButton.click();
                    await Tools.System.sleep(1500); // 等待展开动画
                    pageAfterClick = await UINode.getCurrentPage(); // 刷新页面节点
                    let modeButton = pageAfterClick.findByText(transportMode);
                    if (modeButton) {
                        await modeButton.click();
                        await Tools.System.sleep(1500);
                        return createResponse(true, `已点击“到这去”，并成功切换到“${transportMode}”模式。`);
                    }
                }

                return createResponse(false, `已点击“到这去”，但无法找到交通方式“${transportMode}”。`);
            }

            return createResponse(true, "已点击“到这去”，进入路线规划页面。");
        } else {
            return createResponse(false, "在当前页面未找到“到这去”按钮。");
        }
    }

    async function get_directions(params: { start_point?: string, end_point: string, transport_mode?: string }): Promise<any> {
        return createResponse(false, "功能 'get_directions' 尚未实现。");
    }

    async function search_nearby(params: { keyword: string, center_point?: string }): Promise<any> {
        return createResponse(false, "功能 'search_nearby' 尚未实现。");
    }

    async function navigate_to_home(params: {}): Promise<any> {
        console.log("Navigating to home screen.");
        if (await ensureMain()) {
            // 在地图应用中，通常按返回键可以回到主图区
            // 这里可以根据实际情况增加返回逻辑
            await Tools.UI.pressKey("KEYCODE_BACK");
            await Tools.System.sleep(500);
            await Tools.UI.pressKey("KEYCODE_BACK");
            return createResponse(true, "已尝试返回主界面。");
        } else {
            return createResponse(false, "无法导航到主界面，因为无法启动百度地图。");
        }
    }

    async function activate_voice_assistant(params: {}): Promise<any> {
        console.log("正在激活语音助手“小度”...");
        if (!await ensureMain()) {
            return createResponse(false, "无法启动或切换到百度地图主页，因此无法激活语音助手。");
        }
        await Tools.System.sleep(1000); // 等待UI稳定

        const page = await UINode.getCurrentPage();
        const voiceButton = page.findById('voice_search');

        if (voiceButton) {
            await voiceButton.click();
            await Tools.System.sleep(2000); // 等待语音助手界面弹出
            return createResponse(true, "已成功激活“小度”语音助手。");
        } else {
            return createResponse(false, "在主界面上未找到语音搜索按钮 (ID: voice_search)。");
        }
    }

    async function wrapToolExecution(func: (params: any) => Promise<any>, params: any) {
        try {
            const result = await func(params);
            complete({
                ...result,
            });
        } catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }

    async function main(params: {}) {
        console.log("Running main for testing...");
        // 这是一个用于测试的函数，它会搜索一个默认位置
        // console.log("--- Test Case 1: Searching for '上海' ---");
        // await search_location({ keyword: "上海" });
        // await Tools.System.sleep(2000); // Wait for page to load

        // // console.log("--- Test Case 1: Clicking 'go_to_location' ---");
        // await go_to_location({ "transport_mode": "飞机" });
        // await Tools.System.sleep(2000); // Wait for action

        // // Navigate back to home for the next test
        await navigate_to_home({});
        await Tools.System.sleep(3000);

        console.log("--- Test Case 2: Searching for '长安大学' to test multiple results ---");
        await search_location({ keyword: "长安大学" });
        await Tools.System.sleep(2000);
        await select_location_from_list({ index: 1 });
        await Tools.System.sleep(2000);
        await go_to_location({ transport_mode: "飞机" });
        await Tools.System.sleep(2000);
    }

    // 导出所有工具
    return {
        search_location: async (params) => await wrapToolExecution(search_location, params),
        select_location_from_list: async (params) => await wrapToolExecution(select_location_from_list, params),
        go_to_location: async (params) => await wrapToolExecution(go_to_location, params),
        get_directions: async (params) => await wrapToolExecution(get_directions, params),
        search_nearby: async (params) => await wrapToolExecution(search_nearby, params),
        navigate_to_home: async (params) => await wrapToolExecution(navigate_to_home, params),
        activate_voice_assistant: async (params) => await wrapToolExecution(activate_voice_assistant, params),
        main: async (params) => await wrapToolExecution(main, params),
    };
})();

// 逐个导出，以便在外部调用
exports.search_location = BaiduMapAssistant.search_location;
exports.select_location_from_list = BaiduMapAssistant.select_location_from_list;
exports.go_to_location = BaiduMapAssistant.go_to_location;
exports.get_directions = BaiduMapAssistant.get_directions;
exports.search_nearby = BaiduMapAssistant.search_nearby;
exports.navigate_to_home = BaiduMapAssistant.navigate_to_home;
exports.activate_voice_assistant = BaiduMapAssistant.activate_voice_assistant;
exports.main = BaiduMapAssistant.main;