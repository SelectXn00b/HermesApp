/* METADATA
{
    "name": "Automatic_ui_base",

    "display_name": {
        "zh": "自动化基础工具",
        "en": "Automation Base Tools"
    },
    "description": { "zh": "提供基本的UI自动化工具，能够按照用户的要求帮助操作设备屏幕（如点击、滑动、输入等）。", "en": "Basic UI automation tools to operate the device screen as requested (tap, swipe, input, etc.)." },
    "category": "Automatic",
    "enabledByDefault": true,
    "tools": [
        {
            "name": "usage_advice",
            "description": { "zh": "UI自动化建议：\\n- 元素定位选项：\\n  • 列表：使用index参数（例如，“点击索引为2的列表项”）\\n  • 文本：使用bounds或partialMatch进行模糊匹配（例如，“点击包含‘登录’文字的按钮”）\\n- 操作链：组合多个操作以完成复杂任务（例如，“获取页面信息，然后点击元素”）\\n- 错误处理：如果操作失败，分析页面信息找出原因，并尝试其他方法。\\n- **组合调用（推荐）**：强烈建议在一次响应中组合调用2~3个真实存在的工具，例如依次调用tap → get_page_info，或 click_element → sleep → get_page_info，一次性输出完整的操作序列。软件会自动按顺序依次执行这些工具调用。", "en": "UI automation advice:\\n- Element targeting options:\\n  • Lists: use the index parameter (e.g., tap list item at index 2).\\n  • Text: use bounds or partialMatch for fuzzy matching (e.g., tap a button containing the text 'Login').\\n- Action chains: combine multiple actions to complete complex tasks (e.g., get page info, then click an element).\\n- Error handling: if an action fails, inspect the page info to find the cause and try alternative methods.\\n- **Combined calls (recommended)**: strongly recommend combining 2~3 real tools in a single response, e.g. tap → get_page_info, or click_element → sleep → get_page_info. The system will execute these tool calls sequentially." },
            "parameters": [],
            "advice": true
        },
        {
            "name": "app_launch",
            "description": { "zh": "根据应用包名直接启动应用。如果未找到该包名对应的应用，则返回当前设备的软件安装列表，供你选择其他应用。", "en": "Launch an app by package name. If not found, returns the installed app list for you to choose from." },
            "parameters": [
                { "name": "package_name", "description": { "zh": "应用包名，例如'com.tencent.mm'", "en": "App package name, e.g. 'com.tencent.mm'." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "get_page_info",
            "description": { "zh": "获取当前UI屏幕的信息，包括完整的UI层次结构。", "en": "Get information about the current UI screen, including the full UI hierarchy." },
            "parameters": [
                { "name": "format", "description": { "zh": "格式，可选：'xml'或'json'，默认'xml'", "en": "Format: 'xml' or 'json' (default: 'xml')." }, "type": "string", "required": false },
                { "name": "detail", "description": { "zh": "详细程度，可选：'minimal'、'summary'或'full'，默认'summary'", "en": "Detail level: 'minimal', 'summary', or 'full' (default: 'summary')." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "get_page_screenshot_image",
            "description": { "zh": "获取当前屏幕内容的图片版本（截图），返回保存路径。", "en": "Capture the current screen as an image (screenshot) and return the saved file path." },
            "parameters": []
        },
        {
            "name": "tap",
            "description": { "zh": "在特定坐标模拟点击。", "en": "Simulate a tap at the specified coordinates." },
            "parameters": [
                { "name": "x", "description": { "zh": "X坐标", "en": "X coordinate." }, "type": "number", "required": true },
                { "name": "y", "description": { "zh": "Y坐标", "en": "Y coordinate." }, "type": "number", "required": true }
            ]
        },
        {
            "name": "double_tap",
            "description": { "zh": "在特定坐标模拟双击（快速连续点击两次）。", "en": "Simulate a double tap at the specified coordinates (two quick taps)." },
            "parameters": [
                { "name": "x", "description": { "zh": "X坐标", "en": "X coordinate." }, "type": "number", "required": true },
                { "name": "y", "description": { "zh": "Y坐标", "en": "Y coordinate." }, "type": "number", "required": true }
            ]
        },
        {
            "name": "long_press",
            "description": { "zh": "在特定坐标模拟长按操作。适用于呼出上下文菜单、拖拽前的按住等场景。", "en": "Simulate a long press at the specified coordinates. Useful for context menus or starting a drag." },
            "parameters": [
                { "name": "x", "description": { "zh": "X坐标", "en": "X coordinate." }, "type": "number", "required": true },
                { "name": "y", "description": { "zh": "Y坐标", "en": "Y coordinate." }, "type": "number", "required": true }
            ]
        },
        {
            "name": "click_element",
            "description": { "zh": "点击由资源ID或类名标识的元素。必须至少提供一个标识参数。", "en": "Click an element identified by resourceId or className. You must provide at least one identifier." },
            "parameters": [
                { "name": "resourceId", "description": { "zh": "元素资源ID", "en": "Element resourceId." }, "type": "string", "required": false },
                { "name": "className", "description": { "zh": "元素类名", "en": "Element class name." }, "type": "string", "required": false },
                { "name": "index", "description": { "zh": "要点击的匹配元素，从0开始计数，默认0", "en": "Index of the matched element to click (0-based, default: 0)." }, "type": "number", "required": false },
                { "name": "partialMatch", "description": { "zh": "是否启用部分匹配，默认false", "en": "Enable partial match (default: false)." }, "type": "boolean", "required": false },
                { "name": "bounds", "description": { "zh": "元素边界，格式为'[left,top][right,bottom]'", "en": "Element bounds in format '[left,top][right,bottom]'." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "set_input_text",
            "description": { "zh": "在输入字段中设置文本。", "en": "Set text in the current input field." },
            "parameters": [
                { "name": "text", "description": { "zh": "要输入的文本", "en": "Text to input." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "press_key",
            "description": { "zh": "模拟按键。", "en": "Simulate a key press." },
            "parameters": [
                { "name": "key_code", "description": { "zh": "键码，例如'KEYCODE_BACK'、'KEYCODE_HOME'等", "en": "Key code, e.g. 'KEYCODE_BACK', 'KEYCODE_HOME'." }, "type": "string", "required": true }
            ]
        },
        {
            "name": "swipe",
            "description": { "zh": "模拟滑动手势。", "en": "Simulate a swipe gesture." },
            "parameters": [
                { "name": "start_x", "description": { "zh": "起始X坐标", "en": "Start X coordinate." }, "type": "number", "required": true },
                { "name": "start_y", "description": { "zh": "起始Y坐标", "en": "Start Y coordinate." }, "type": "number", "required": true },
                { "name": "end_x", "description": { "zh": "结束X坐标", "en": "End X coordinate." }, "type": "number", "required": true },
                { "name": "end_y", "description": { "zh": "结束Y坐标", "en": "End Y coordinate." }, "type": "number", "required": true },
                { "name": "duration", "description": { "zh": "持续时间，毫秒，默认300", "en": "Duration in milliseconds (default: 300)." }, "type": "number", "required": false }
            ]
        }
    ]
}*/
const UIAutomationTools = (function () {
    async function get_page_info(params) {
        const result = (await UINode.getCurrentPage()).toFormattedString();
        return { success: true, message: '成功获取页面信息', data: result };
    }
    async function get_page_screenshot_image(params) {
        try {
            const screenshotDir = OPERIT_CLEAN_ON_EXIT_DIR;
            // Ensure the directory exists
            await Tools.Files.mkdir(screenshotDir, true);
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            const filePath = `${screenshotDir}/ui_screenshot_${timestamp}.png`;
            console.log(`截取当前UI屏幕并保存到: ${filePath}`);
            const result = await Tools.System.shell(`screencap -p ${filePath}`);
            const imageLink = NativeInterface.registerImageFromPath(filePath);
            return {
                success: true,
                message: `截图已保存到 ${filePath}`,
                data: {
                    file_path: filePath,
                    image_link: imageLink,
                    raw_result: result,
                },
            };
        }
        catch (error) {
            console.error(`获取屏幕截图失败: ${error.message}`);
            return {
                success: false,
                message: `获取屏幕截图失败: ${error.message}`,
            };
        }
    }
    async function tap(params) {
        const result = await Tools.UI.tap(params.x, params.y);
        return { success: true, message: '点击操作成功', data: result };
    }
    async function double_tap(params) {
        const first = await Tools.UI.tap(params.x, params.y);
        await Tools.System.sleep(120);
        const second = await Tools.UI.tap(params.x, params.y);
        return {
            success: true,
            message: '双击操作成功',
            data: { first, second },
        };
    }
    async function long_press(params) {
        const result = await Tools.UI.longPress(params.x, params.y);
        return { success: true, message: '长按操作成功', data: result };
    }
    async function click_element(params) {
        const result = await Tools.UI.clickElement(params);
        return { success: true, message: '点击元素操作成功', data: result };
    }
    async function set_input_text(params) {
        const result = await Tools.UI.setText(params.text);
        return { success: true, message: '输入文本操作成功', data: result };
    }
    async function press_key(params) {
        const result = await Tools.UI.pressKey(params.key_code);
        return { success: true, message: '按键操作成功', data: result };
    }
    async function swipe(params) {
        const result = await Tools.UI.swipe(params.start_x, params.start_y, params.end_x, params.end_y);
        return { success: true, message: '滑动操作成功', data: result };
    }
    async function app_launch(params) {
        if (!params.package_name) {
            return { success: false, message: '必须提供package_name参数' };
        }
        try {
            const startResult = await Tools.System.startApp(params.package_name);
            if (startResult && startResult.success) {
                return {
                    success: true,
                    message: '应用启动成功',
                    data: {
                        operation: startResult,
                    },
                };
            }
            const appList = await Tools.System.listApps(false);
            return {
                success: false,
                message: '未能启动应用，可能未安装或无法找到启动入口。已返回当前安装的应用列表。',
                data: {
                    operation: startResult,
                    installed_apps: appList,
                },
            };
        }
        catch (error) {
            console.error(`app_launch 执行失败: ${error.message}`);
            try {
                const appList = await Tools.System.listApps(false);
                return {
                    success: false,
                    message: `启动应用时发生错误: ${error.message}。已返回当前安装的应用列表。`,
                    data: {
                        installed_apps: appList,
                    },
                };
            }
            catch (listError) {
                console.error(`获取应用列表失败: ${listError.message}`);
                return {
                    success: false,
                    message: `启动应用失败且无法获取应用列表: ${listError.message}`,
                };
            }
        }
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
        console.log("=== UI Automation Tools 测试开始 ===\n");
        const results = [];
        try {
            // 1. 测试 get_page_info
            console.log("1. 测试 get_page_info...");
            const pageInfoResult = await get_page_info({});
            results.push({ tool: 'get_page_info', result: pageInfoResult });
            console.log("✓ get_page_info 测试完成\n");
            // 2. 测试 tap (点击屏幕中心位置)
            console.log("2. 测试 tap...");
            const tapResult = await tap({ x: 500, y: 1000 });
            results.push({ tool: 'tap', result: tapResult });
            console.log("✓ tap 测试完成\n");
            await Tools.System.sleep(500);
            // 3. 测试 press_key (按音量上键)
            console.log("3. 测试 press_key...");
            const pressKeyResult = await press_key({ key_code: 'KEYCODE_VOLUME_UP' });
            results.push({ tool: 'press_key', result: pressKeyResult });
            console.log("✓ press_key 测试完成\n");
            await Tools.System.sleep(500);
            // 4. 测试 set_input_text
            console.log("4. 测试 set_input_text...");
            const setTextResult = await set_input_text({ text: 'UI自动化测试文本' });
            results.push({ tool: 'set_input_text', result: setTextResult });
            console.log("✓ set_input_text 测试完成\n");
            await Tools.System.sleep(500);
            // 5. 测试 swipe (向上滑动)
            console.log("5. 测试 swipe...");
            const swipeResult = await swipe({
                start_x: 500,
                start_y: 1500,
                end_x: 500,
                end_y: 500,
                duration: 300
            });
            results.push({ tool: 'swipe', result: swipeResult });
            console.log("✓ swipe 测试完成\n");
            await Tools.System.sleep(500);
            // 6. 测试 click_element (尝试点击一个常见的元素)
            console.log("6. 测试 click_element...");
            try {
                const clickResult = await click_element({
                    className: 'android.widget.Button',
                    index: 0
                });
                results.push({ tool: 'click_element', result: clickResult });
                console.log("✓ click_element 测试完成\n");
            }
            catch (error) {
                console.log("⚠ click_element 测试失败（这可能是正常的，如果当前页面没有按钮）:", error.message, "\n");
                results.push({ tool: 'click_element', result: { success: false, message: error.message } });
            }
            console.log("=== UI Automation Tools 测试完成 ===\n");
            console.log("测试结果汇总:");
            results.forEach((r, i) => {
                const status = r.result.success ? '✓' : '✗';
                console.log(`${i + 1}. ${status} ${r.tool}: ${r.result.message}`);
            });
            complete({
                success: true,
                message: "所有UI工具测试完成",
                data: results
            });
        }
        catch (error) {
            console.error("测试过程中发生错误:", error);
            complete({
                success: false,
                message: `测试失败: ${error.message}`,
                data: results
            });
        }
    }
    return {
        get_page_info: (params) => wrapToolExecution(get_page_info, params),
        app_launch: (params) => wrapToolExecution(app_launch, params),
        get_page_screenshot_image: () => wrapToolExecution(get_page_screenshot_image, {}),
        tap: (params) => wrapToolExecution(tap, params),
        double_tap: (params) => wrapToolExecution(double_tap, params),
        long_press: (params) => wrapToolExecution(long_press, params),
        click_element: (params) => wrapToolExecution(click_element, params),
        set_input_text: (params) => wrapToolExecution(set_input_text, params),
        press_key: (params) => wrapToolExecution(press_key, params),
        swipe: (params) => wrapToolExecution(swipe, params),
        main,
    };
})();
exports.get_page_info = UIAutomationTools.get_page_info;
exports.app_launch = UIAutomationTools.app_launch;
exports.get_page_screenshot_image = UIAutomationTools.get_page_screenshot_image;
exports.tap = UIAutomationTools.tap;
exports.double_tap = UIAutomationTools.double_tap;
exports.long_press = UIAutomationTools.long_press;
exports.click_element = UIAutomationTools.click_element;
exports.set_input_text = UIAutomationTools.set_input_text;
exports.press_key = UIAutomationTools.press_key;
exports.swipe = UIAutomationTools.swipe;
exports.main = UIAutomationTools.main;
