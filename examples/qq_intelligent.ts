/*
METADATA
{
    // QQ Intelligent Assistant Package
    name: Experimental_qq_intelligent

    display_name: {
      zh: "实验性 QQ 智能助手"
      en: "Experimental QQ Intelligent Assistant"
    }description: { zh: "高级QQ智能助手，通过UI自动化技术实现QQ应用交互，支持消息自动回复、历史记录读取、联系人搜索与通讯等功能，为AI赋予QQ社交能力。适用于智能客服、自动回复、社交辅助等场景。", en: "Advanced QQ assistant powered by UI automation. Supports sending messages, reading chat history, searching contacts/groups, and basic communication workflows, enabling AI-driven social messaging scenarios." }

    // Tools in this package
    category: "Media"
    tools: [
        {
            name: reply
            description: { zh: "在当前聊天窗口输入消息并发送。一般情况下，用户想要ai帮忙发送消息时，需要ai自己去生成回复的消息，如果不确定发送的内容，请不要调用工具。只要是停留在聊天界面，就可以直接调用这个。", en: "Type and send a message in the current chat window. Usually the AI should generate the message content by itself; if you are unsure what to send, do not call this tool. As long as you are staying on a chat page, you can call it directly." }
            parameters: [
                {
                    name: message
                    description: { zh: "要发送的消息", en: "Message text to send." }
                    type: string
                    required: true
                },
                {
                    name: click_send
                    description: { zh: "是否点击发送按钮", en: "Whether to tap the Send button." }
                    type: boolean
                    required: false
                }
            ]
        },
        {
            name: find_user
            description: { zh: "在QQ联系人或群成员中查找用户", en: "Find a user in QQ contacts or group chats." }
            parameters: [
                {
                    name: user_name
                    description: { zh: "搜索用户名称", en: "User name keyword to search." }
                    type: string
                    required: true
                },
                {
                    name: user_type
                    description: { zh: "搜索类型（contacts/groups）", en: "Search type: contacts or groups." }
                    type: string
                    required: true
                }
            ]
        },
        {
            name: find_and_reply
            description: { zh: "查找用户并发送消息", en: "Find a user and send a message." }
            parameters: [
                {
                    name: message
                    description: { zh: "要发送的消息", en: "Message text to send." }
                    type: string
                    required: true
                },
                {
                    name: user_name
                    description: { zh: "发送目标用户名称", en: "Target user name keyword." }
                    type: string
                    required: true
                },
                {
                    name: user_type
                    description: { zh: "用户类型（contacts/groups）", en: "Target type: contacts or groups." }
                    type: string
                    required: true
                },
                {
                    name: click_send
                    description: { zh: "是否点击发送按钮", en: "Whether to tap the Send button." }
                    type: boolean
                    required: false
                }
            ]
        },
        {
            name: get_history
            description: { zh: "获取当前聊天窗口的历史消息。如果用户要求读取聊天记录，那么可以用这个工具或者find_and_get_history。", en: "Get chat history from the current chat window. If the user asks to read chat history, you can use this tool or `find_and_get_history`." }
            parameters: [
                {
                    name: message_num
                    description: { zh: "获取的消息数量", en: "Number of messages to retrieve." }
                    type: number
                    required: false
                }
            ]
        },
        {
            name: find_and_get_history
            description: { zh: "查找用户并获取聊天历史记录。一般情况下，用户想要让ai帮忙回消息的时候，需要先调用这个，得到历史记录后直接调用reply。", en: "Find a user and retrieve chat history. Typically, when the user wants the AI to reply, call this first to get context, then call `reply`." }
            parameters: [
                {
                    name: user_name
                    description: { zh: "搜索用户名称", en: "User name keyword to search." }
                    type: string
                    required: true
                },
                {
                    name: user_type
                    description: { zh: "搜索类型（contacts/groups）", en: "Search type: contacts or groups." }
                    type: string
                    required: true
                },
                {
                    name: message_num
                    description: { zh: "获取的消息数量", en: "Number of messages to retrieve." }
                    type: number
                    required: false
                }
            ]
        }
    ]
}*/

interface Array<T> {
    at(index: number): T | undefined;
}

const QQIntelligent = (function () {
    async function close_keyboard() {
        await Tools.UI.pressKey("KEYCODE_BACK");
    }

    async function is_in_group() {
        let page = await UINode.getCurrentPage();
        return page.findAllByContentDesc('语音').findIndex(e => e.className?.includes('ImageButton') && (e.centerPoint?.y ?? 0) > 1000) === -1;
    }




    Array.prototype.at = function (index: number) {
        if (index < 0) {
            index = this.length + index;
        }
        return this[index];
    }

    async function get_history(params: { message_num: number | string }) {
        const message_num = Number(params.message_num) || 10;
        let page = await UINode.getCurrentPage();

        //获取群名称
        const chat_title = page.findById('com.tencent.mobileqq:id/ivTitleBtnLeft')?.parent?.allTexts()[0] ?? "";
        // const is_group = await is_in_group();
        console.log("chat_title", chat_title);
        // console.log("is_group", is_group);
        //先滑动到最底部
        let tryMax = 0;
        while (tryMax < 1) {
            tryMax++;
            await Tools.UI.swipe(100, 1000, 100, 200);
            await Tools.System.sleep(500);
        }

        let messageList: { message: string, sender: string }[] = [];
        let allMessages: { message: string, sender: string }[] = [];

        //获取历史消息
        tryMax = 0;
        while (tryMax < 5) {
            tryMax++;

            page = await UINode.getCurrentPage();
            console.log("page", page.toFormattedString!());
            const list_view = page.findByClass('RecyclerView');
            if (!list_view) {
                return undefined;
            }

            // 清空临时消息列表
            messageList = [];

            // 获取当前可见的消息列表
            for (const message of list_view.children) {
                let message_text = message.allTexts().join('/');

                let sender = "other";
                //判断头像再左边还是右边
                const avatar = message.findByClass('ImageView');
                if (avatar) {
                    if ((avatar.centerPoint?.x ?? 0) < 300) {
                        sender = "other";
                        console.log("other", avatar.centerPoint?.x);
                    } else {
                        sender = "self";
                        console.log("self", avatar.centerPoint?.x);
                    }
                } else {
                    sender = "self";
                    console.log("self");
                }
                messageList.push({ message: message_text, sender: sender });
            }

            // 将当前视图中的消息添加到总集合中，保持顺序
            // 从下往上滑动时，将新获取的消息放在前面（保持旧消息在前，新消息在后的顺序）
            allMessages = [...messageList, ...allMessages];

            // 如果已经获取了足够多的消息，停止滚动
            if (allMessages.length >= message_num) {
                break;
            }
            await Tools.UI.swipe(100, 300, 100, 1000);
            await Tools.System.sleep(500);
        }

        // 删除重复的消息，但保持原始顺序
        const uniqueMessages: { message: string, sender: string }[] = [];
        const seen = new Set();

        for (const msg of allMessages) {
            const key = `${msg.message}-${msg.sender}`;
            if (!seen.has(key)) {
                seen.add(key);
                uniqueMessages.push(msg);
            }
        }

        // 返回符合数量要求的消息列表（保持原始顺序）
        return { messages: uniqueMessages.slice(0, message_num), chat_title: chat_title };
    }

    async function reply(params: { message: string, click_send: boolean }) {
        // 提取参数
        const message = params.message || "";
        const click_send = params.click_send || false;
        await Tools.UI.setText(message);
        await Tools.System.sleep(500);

        if (click_send) {
            await Tools.UI.clickElement({
                resourceId: "com.tencent.mobileqq:id/send_btn",
                index: "0"
            });
        }
        return true;
    }

    async function find_and_reply(params: { message: string, user_name: string, user_type: "contacts" | "groups", click_send: boolean }) {
        // 提取参数
        const message = params.message || "";
        const user_name = params.user_name || "";
        const user_type = params.user_type || "contacts";
        const click_send = params.click_send || false;
        let result = await find_user({ user_name: user_name, user_type: user_type });
        if (!result) {
            return false;
        }
        await Tools.System.sleep(1000);
        return await reply({ message: message, click_send: click_send });
    }

    async function ensureActivity(activityName: string = "", packageName: string = "com.tencent.mobileqq", enterActivity: () => Promise<boolean> = async () => true, tryMax: number = 1) {
        let android = new Android();
        let activity = await Tools.UI.getPageInfo();
        if (activity.activityName?.includes(activityName)) {
            return true;
        }

        while (tryMax > 0) {
            tryMax--;
            await Tools.System.stopApp(packageName);
            await Tools.System.sleep(2000);
            await Tools.System.startApp(packageName);
            // let intent = android.createIntent();
            // intent.setComponent(packageName, activityName);
            // await intent.start();
            await Tools.System.sleep(3000); // Give some time for app to launch
            if (await enterActivity()) {
                activity = await Tools.UI.getPageInfo();
                if (activity.activityName?.includes(activityName)) {
                    return true;
                }
            }
        }
        return false;
    }

    async function find_user(params: { user_name: string, user_type: "contacts" | "groups" }) {
        // 提取参数
        const user_name = params.user_name || "";
        const user_type = params.user_type || "contacts";

        if (!await ensureActivity("com.tencent.mobileqq.search.activity.UniteSearchActivity", "com.tencent.mobileqq", async () => {
            const search_btn = (await UINode.getCurrentPage()).findByText("搜索");
            if (search_btn) {
                await search_btn.click();
                return true;
            }
            return false;
        })) {
            return false;
        }

        let firstTarget: UINode | undefined = undefined;
        let tryMax = 0;
        while (tryMax < 2) {
            tryMax++;
            const search_btn = (await UINode.getCurrentPage()).findByText("搜索");
            if (search_btn) {
                await search_btn.click();
                await Tools.System.sleep(500);
            }
            // 输入搜索内容
            await Tools.UI.setText(user_name);
            await Tools.System.sleep(3000 * tryMax);
            await close_keyboard();

            const currentPage = await UINode.getCurrentPage();
            const searchResult = currentPage.findAllById('com.tencent.mobileqq:id/title');

            let isNeedToCatch = false;
            for (const child of searchResult!) {
                if (isNeedToCatch) {
                    firstTarget = child;
                    break;
                }
                // const title = child.findById('com.tencent.mobileqq:id/title');
                const title = child;
                if (title) {
                    if (user_type == "contacts" && title.text == "联系人") {
                        isNeedToCatch = true;
                    } else if (user_type == "groups" && title.text == "群聊") {
                        isNeedToCatch = true;
                    }
                }
            }

            if (firstTarget) {
                await firstTarget.click();
                return true;
            }
        }

        return false;
    }

    async function find_and_get_history(params: { user_name: string, user_type: "contacts" | "groups", message_num: number }) {
        const user_name = params.user_name || "";
        const user_type = params.user_type || "contacts";
        const message_num = params.message_num || 10;
        let result = await find_user({ user_name: user_name, user_type: user_type });
        if (!result) {
            return undefined;
        }
        await Tools.System.sleep(1000);
        return await get_history({ message_num: message_num });
    }

    async function main() {
        // let result = await find_and_reply({ message: "你好你好！我是OPERIT，很高兴认识你！", user_name: "韩韩韩", user_type: "contacts", click_send: true });
        // await find_user({ user_name: "Wind", user_type: "contacts" });
        // await find_user({ user_name: "Dec", user_type: "groups" });
        // await Tools.System.sleep(1000);
        let result = await find_and_get_history({ user_name: "Dec", user_type: "groups", message_num: 20 });
        console.log(result);
        complete({
            success: result
        });
    }

    async function wrap_bool(func: (params: any) => Promise<boolean>, params: any, successMessage: string, failMessage: string, additionalMessage: string = "") {
        if (await func(params)) {
            complete({
                success: true,
                message: successMessage,
                additionalMessage: additionalMessage
            })
        } else {
            complete({
                success: false,
                message: failMessage,
                additionalMessage: additionalMessage
            })
        }
    }

    async function wrap_data(func: (params: any) => Promise<any>, params: any, successMessage: string, failMessage: string, additionalMessage: string = "") {
        const result = await func(params);
        complete({
            success: true,
            message: successMessage,
            additionalMessage: additionalMessage,
            data: result
        })
    }

    return {
        main: main,
        reply: async (params) => await wrap_bool(reply, params, "发送成功", "发送失败"),
        find_user: async (params) => await wrap_bool(find_user, params, "查找成功", "查找失败，停留在界面", (await UINode.getCurrentPage()).toFormattedString!()),
        find_and_reply: async (params) => await wrap_bool(find_and_reply, params, "发送成功", "发送失败，停留在界面", (await UINode.getCurrentPage()).toFormattedString!()),
        get_history: async (params) => await wrap_data(get_history, params, "获取历史消息成功", "获取历史消息失败"),
        find_and_get_history: async (params) => await wrap_data(find_and_get_history, params, "获取历史消息成功", "获取历史消息失败")
    }
})();

//逐个导出
exports.reply = QQIntelligent.reply;
exports.find_user = QQIntelligent.find_user;
exports.find_and_reply = QQIntelligent.find_and_reply;
exports.get_history = QQIntelligent.get_history;
exports.find_and_get_history = QQIntelligent.find_and_get_history;
exports.main = QQIntelligent.main;