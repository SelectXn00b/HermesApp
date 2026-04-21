/*
METADATA
{
    "name": "network_test",

    "display_name": {
        "zh": "网络测试",
        "en": "Network Test"
    },
    "category": "Network",
    "description": { "zh": "网络测试工具集合，提供基于OkHttp3的网络请求功能，包括GET、POST、PUT、DELETE请求方法，以及请求超时设置、重定向控制和拦截器管理。支持多种数据格式，便于测试API接口和网络连接性能。", "en": "Network testing tools based on OkHttp3. Provides GET/POST/PUT/DELETE requests, timeout settings, redirect control, and interceptor management. Supports multiple data formats for API testing and connectivity diagnostics." },
    "tools": [
        {
            "name": "http_get",
            "description": { "zh": "发送HTTP GET请求", "en": "Send an HTTP GET request." },
            "parameters": [
                {
                    "name": "url",
                    "description": { "zh": "请求URL", "en": "Request URL." },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "headers",
                    "description": { "zh": "请求头", "en": "Request headers." },
                    "type": "object",
                    "required": false
                }
            ]
        },
        {
            "name": "http_post",
            "description": { "zh": "发送HTTP POST请求", "en": "Send an HTTP POST request." },
            "parameters": [
                {
                    "name": "url",
                    "description": { "zh": "请求URL", "en": "Request URL." },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "body",
                    "description": { "zh": "请求体", "en": "Request body." },
                    "type": "object",
                    "required": true
                },
                {
                    "name": "headers",
                    "description": { "zh": "请求头", "en": "Request headers." },
                    "type": "object",
                    "required": false
                },
                {
                    "name": "body_type",
                    "description": { "zh": "请求体类型，支持'text'、'json'、'form'、'multipart'", "en": "Body type: 'text', 'json', 'form', or 'multipart'." },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "http_put",
            "description": { "zh": "发送HTTP PUT请求", "en": "Send an HTTP PUT request." },
            "parameters": [
                {
                    "name": "url",
                    "description": { "zh": "请求URL", "en": "Request URL." },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "body",
                    "description": { "zh": "请求体", "en": "Request body." },
                    "type": "object",
                    "required": true
                },
                {
                    "name": "headers",
                    "description": { "zh": "请求头", "en": "Request headers." },
                    "type": "object",
                    "required": false
                },
                {
                    "name": "body_type",
                    "description": { "zh": "请求体类型，支持'text'、'json'、'form'、'multipart'", "en": "Body type: 'text', 'json', 'form', or 'multipart'." },
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "http_delete",
            "description": { "zh": "发送HTTP DELETE请求", "en": "Send an HTTP DELETE request." },
            "parameters": [
                {
                    "name": "url",
                    "description": { "zh": "请求URL", "en": "Request URL." },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "headers",
                    "description": { "zh": "请求头", "en": "Request headers." },
                    "type": "object",
                    "required": false
                }
            ]
        },
        {
            "name": "config_client",
            "description": { "zh": "配置HTTP客户端", "en": "Configure the HTTP client." },
            "parameters": [
                {
                    "name": "connect_timeout",
                    "description": { "zh": "连接超时时间(毫秒)", "en": "Connection timeout (milliseconds)." },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "read_timeout",
                    "description": { "zh": "读取超时时间(毫秒)", "en": "Read timeout (milliseconds)." },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "write_timeout",
                    "description": { "zh": "写入超时时间(毫秒)", "en": "Write timeout (milliseconds)." },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "follow_redirects",
                    "description": { "zh": "是否跟随重定向", "en": "Whether to follow redirects." },
                    "type": "boolean",
                    "required": false
                },
                {
                    "name": "retry_on_failure",
                    "description": { "zh": "是否在连接失败时重试", "en": "Whether to retry on connection failure." },
                    "type": "boolean",
                    "required": false
                }
            ]
        },
        {
            "name": "ping_test",
            "description": { "zh": "测试与指定URL的网络连接", "en": "Test network connectivity to a specified URL." },
            "parameters": [
                {
                    "name": "url",
                    "description": { "zh": "要测试的URL", "en": "URL to test." },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "count",
                    "description": { "zh": "测试次数", "en": "Number of test attempts." },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "test_all",
            "description": { "zh": "运行所有网络测试", "en": "Run all network tests." },
            "parameters": []
        }
    ]
}*/

const networkTest = (function () {
    // 默认客户端
    let defaultClient = OkHttp.newClient();

    // 客户端配置
    let clientConfig: OkHttpConfig = {
        timeouts: {
            connect: 10000,
            read: 10000,
            write: 10000
        },
        followRedirects: true,
        retryOnConnectionFailure: true,
        interceptors: []
    };

    /**
     * 配置HTTP客户端
     * @param params - 配置参数
     */
    async function config_client(params: {
        connect_timeout?: number,
        read_timeout?: number,
        write_timeout?: number,
        follow_redirects?: boolean,
        retry_on_failure?: boolean
    }): Promise<any> {
        try {
            // 更新配置
            if (params.connect_timeout !== undefined) {
                clientConfig.timeouts.connect = params.connect_timeout;
            }
            if (params.read_timeout !== undefined) {
                clientConfig.timeouts.read = params.read_timeout;
            }
            if (params.write_timeout !== undefined) {
                clientConfig.timeouts.write = params.write_timeout;
            }
            if (params.follow_redirects !== undefined) {
                clientConfig.followRedirects = params.follow_redirects;
            }
            if (params.retry_on_failure !== undefined) {
                clientConfig.retryOnConnectionFailure = params.retry_on_failure;
            }

            // 创建新客户端
            const builder = OkHttp.newBuilder()
                .connectTimeout(clientConfig.timeouts.connect)
                .readTimeout(clientConfig.timeouts.read)
                .writeTimeout(clientConfig.timeouts.write)
                .followRedirects(clientConfig.followRedirects)
                .retryOnConnectionFailure(clientConfig.retryOnConnectionFailure);

            // 添加拦截器
            if (clientConfig.interceptors.length > 0) {
                for (const interceptor of clientConfig.interceptors) {
                    builder.addInterceptor(interceptor);
                }
            }

            // 构建客户端
            defaultClient = builder.build();

            return {
                success: true,
                message: "HTTP客户端配置已更新",
                config: {
                    ...clientConfig
                }
            };
        } catch (error) {
            throw new Error(`配置HTTP客户端失败: ${error.message}`);
        }
    }

    /**
     * 发送HTTP GET请求
     * @param params - 请求参数
     */
    async function http_get(params: { url: string, headers?: Record<string, string> }): Promise<any> {
        try {
            if (!params.url) {
                throw new Error("URL不能为空");
            }

            console.log(`发送GET请求: ${params.url}`);
            console.log(`请求头: ${JSON.stringify(params.headers || {})}`);

            // 准备请求
            const request = defaultClient.newRequest()
                .url(params.url)
                .method('GET');

            // 添加请求头
            if (params.headers) {
                request.headers(params.headers);
            }

            // 执行请求
            const response = await request.build().execute();

            // 返回结果
            const result = await formatResponse(response);

            // 额外输出重要的响应信息
            console.log(`\n🟢 GET请求完成: ${params.url}`);
            console.log(`🟢 状态码: ${result.status_code} ${result.status_message}`);
            if (result.json) {
                console.log(`🟢 返回数据类型: ${Array.isArray(result.json) ? '数组' : (typeof result.json === 'object' ? '对象' : typeof result.json)}`);
            }
            console.log(`🟢 响应大小: ${result.content_length} 字节\n`);

            return result;
        } catch (error) {
            throw new Error(`GET请求失败: ${error.message}`);
        }
    }

    /**
     * 发送HTTP POST请求
     * @param params - 请求参数
     */
    async function http_post(params: {
        url: string,
        body: any,
        headers?: Record<string, string>,
        body_type?: 'text' | 'json' | 'form' | 'multipart'
    }): Promise<any> {
        try {
            if (!params.url) {
                throw new Error("URL不能为空");
            }

            if (params.body === undefined) {
                throw new Error("请求体不能为空");
            }

            console.log(`发送POST请求: ${params.url}`);

            // 准备请求
            const request = defaultClient.newRequest()
                .url(params.url)
                .method('POST');

            // 添加请求头
            if (params.headers) {
                request.headers(params.headers);
            }

            // 添加请求体
            const bodyType = params.body_type || 'json';
            request.body(params.body, bodyType);

            // 执行请求
            const response = await request.build().execute();

            // 返回结果
            return await formatResponse(response);
        } catch (error) {
            throw new Error(`POST请求失败: ${error.message}`);
        }
    }

    /**
     * 发送HTTP PUT请求
     * @param params - 请求参数
     */
    async function http_put(params: {
        url: string,
        body: any,
        headers?: Record<string, string>,
        body_type?: 'text' | 'json' | 'form' | 'multipart'
    }): Promise<any> {
        try {
            if (!params.url) {
                throw new Error("URL不能为空");
            }

            if (params.body === undefined) {
                throw new Error("请求体不能为空");
            }

            console.log(`发送PUT请求: ${params.url}`);

            // 准备请求
            const request = defaultClient.newRequest()
                .url(params.url)
                .method('PUT');

            // 添加请求头
            if (params.headers) {
                request.headers(params.headers);
            }

            // 添加请求体
            const bodyType = params.body_type || 'json';
            request.body(params.body, bodyType);

            // 执行请求
            const response = await request.build().execute();

            // 返回结果
            return await formatResponse(response);
        } catch (error) {
            throw new Error(`PUT请求失败: ${error.message}`);
        }
    }

    /**
     * 发送HTTP DELETE请求
     * @param params - 请求参数
     */
    async function http_delete(params: { url: string, headers?: Record<string, string> }): Promise<any> {
        try {
            if (!params.url) {
                throw new Error("URL不能为空");
            }

            console.log(`发送DELETE请求: ${params.url}`);

            // 准备请求
            const request = defaultClient.newRequest()
                .url(params.url)
                .method('DELETE');

            // 添加请求头
            if (params.headers) {
                request.headers(params.headers);
            }

            // 执行请求
            const response = await request.build().execute();

            // 返回结果
            return await formatResponse(response);
        } catch (error) {
            throw new Error(`DELETE请求失败: ${error.message}`);
        }
    }

    /**
     * 格式化响应结果
     * @param response - OkHttp响应
     */
    async function formatResponse(response: OkHttpResponse): Promise<any> {
        try {
            let responseBody = '';
            let jsonData: any = undefined;

            // 获取响应文本
            responseBody = response.content;
            console.log(`\n===== 响应内容开始 =====`);
            console.log(`状态码: ${response.statusCode} ${response.statusMessage}`);
            console.log(`内容类型: ${response.contentType || '未知'}`);

            // 如果响应体不太长，完整显示
            if (responseBody && responseBody.length < 1000) {
                console.log(`响应体:\n${responseBody}`);
            } else if (responseBody) {
                console.log(`响应体(截断):\n${responseBody.substring(0, 500)}...`);
                console.log(`[完整长度: ${responseBody.length} 字符]`);
            } else {
                console.log(`响应体为空`);
            }

            // 尝试解析JSON
            if (response.contentType && response.contentType.includes('application/json')) {
                try {
                    jsonData = response.json();
                    if (jsonData) {
                        if (Array.isArray(jsonData)) {
                            console.log(`- 数组数据, 长度: ${(jsonData as any[]).length}`);
                            if ((jsonData as any[]).length > 0) {
                                console.log(`- 第一项样本: ${JSON.stringify(jsonData[0]).substring(0, 100)}${JSON.stringify(jsonData[0]).length > 100 ? '...' : ''}`);
                            }
                        } else if (typeof jsonData === 'object') {
                            const keys = Object.keys(jsonData);
                            console.log(`- 对象数据, 字段数: ${keys.length}`);
                            console.log(`- 字段列表: ${keys.slice(0, 5).join(', ')}${keys.length > 5 ? '...' : ''}`);
                            // 输出一个示例值
                            if (keys.length > 0) {
                                const sampleKey = keys[0];
                                const sampleValue = JSON.stringify(jsonData[sampleKey]);
                                console.log(`- 示例 "${sampleKey}": ${sampleValue.substring(0, 60)}${sampleValue.length > 60 ? '...' : ''}`);
                            }

                            // 输出完整的JSON格式化数据
                            console.log(`\nJSON完整数据:`);
                            console.log(JSON.stringify(jsonData, undefined, 2));
                        } else {
                            console.log(`- 基本类型数据: ${JSON.stringify(jsonData).substring(0, 100)}`);
                        }
                    }
                } catch (e) {
                    console.warn("响应内容不是有效的JSON格式");
                }
            }
            console.log(`===== 响应内容结束 =====\n`);

            return {
                success: response.isSuccessful(),
                status_code: response.statusCode,
                status_message: response.statusMessage,
                headers: response.headers,
                content_type: response.contentType,
                content_length: response.size,
                body: responseBody,
                json: jsonData,
                time_info: {
                    timestamp: new Date().toISOString()
                }
            };
        } catch (error) {
            console.error(`格式化响应出错: ${error.message}`);
            return {
                success: false,
                error: `格式化响应出错: ${error.message}`
            };
        }
    }

    /**
     * 测试与指定URL的网络连接
     * @param params - 测试参数
     */
    async function ping_test(params: { url: string, count?: number }): Promise<any> {
        try {
            if (!params.url) {
                throw new Error("URL不能为空");
            }

            const count = params.count || 3;
            const results: Array<{
                attempt: number;
                success: boolean;
                time_ms: number;
                status?: number;
                error?: string;
            }> = [];
            let totalTime = 0;
            let successCount = 0;
            let failCount = 0;

            console.log(`开始Ping测试: ${params.url}, 次数: ${count}`);

            for (let i = 0; i < count; i++) {
                const startTime = Date.now();
                try {
                    // 发送简单的HEAD请求来测试连接
                    const request = defaultClient.newRequest()
                        .url(params.url)
                        .method('HEAD')
                        .build();

                    const response = await request.execute();
                    const endTime = Date.now();
                    const elapsed = endTime - startTime;

                    totalTime += elapsed;
                    successCount++;

                    results.push({
                        attempt: i + 1,
                        success: true,
                        time_ms: elapsed,
                        status: response.statusCode
                    });

                    console.log(`Ping #${i + 1}: ${elapsed}ms, 状态: ${response.statusCode}`);

                    // 等待一小段时间后再发送下一个请求
                    if (i < count - 1) {
                        await sleep(500);
                    }
                } catch (error) {
                    const endTime = Date.now();
                    const elapsed = endTime - startTime;
                    failCount++;

                    results.push({
                        attempt: i + 1,
                        success: false,
                        time_ms: elapsed,
                        error: error.message
                    });

                    console.log(`Ping #${i + 1}: 失败, 错误: ${error.message}`);

                    // 等待一小段时间后再发送下一个请求
                    if (i < count - 1) {
                        await sleep(500);
                    }
                }
            }

            const avgTime = successCount > 0 ? totalTime / successCount : 0;

            return {
                url: params.url,
                success: successCount > 0,
                summary: {
                    total_count: count,
                    success_count: successCount,
                    fail_count: failCount,
                    success_rate: `${(successCount / count * 100).toFixed(1)}%`,
                    average_time: `${avgTime.toFixed(2)}ms`
                },
                detail_results: results
            };
        } catch (error) {
            throw new Error(`Ping测试失败: ${error.message}`);
        }
    }

    /**
     * 等待指定的毫秒数
     * @param ms 等待的毫秒数
     */
    async function sleep(ms: number | string): Promise<void> {
        const sleepTime = Number(ms);
        if (isNaN(sleepTime)) {
            throw new Error("无效的等待时间");
        }
        return new Promise(resolve => setTimeout(resolve, sleepTime));
    }

    /**
     * 测试所有网络功能
     */
    async function test_all(): Promise<any> {
        try {
            console.log("开始网络功能测试...");

            const results: {
                config?: any;
                ping?: any;
                get?: any;
                post?: any;
                put?: any;
                delete?: any;
            } = {};

            // 1. 测试客户端配置
            console.log("测试客户端配置...");
            try {
                const configResult = await config_client({
                    connect_timeout: 8000,
                    read_timeout: 8000,
                    write_timeout: 8000,
                    follow_redirects: true,
                    retry_on_failure: true
                });
                results.config = configResult;
                console.log("✓ 客户端配置成功");
            } catch (error) {
                results.config = { error: `客户端配置失败: ${error.message}` };
                console.log("✗ 客户端配置失败");
            }

            // 2. 测试连接
            console.log("测试网络连接...");
            try {
                const pingResult = await ping_test({
                    url: "https://httpbin.org",
                    count: 2
                });
                results.ping = pingResult;
                console.log("✓ Ping测试成功");
            } catch (error) {
                results.ping = { error: `Ping测试失败: ${error.message}` };
                console.log("✗ Ping测试失败");
            }

            // 3. 测试GET请求
            console.log("测试GET请求...");
            try {
                const getResult = await http_get({
                    url: "https://httpbin.org/get",
                    headers: {
                        "User-Agent": "OkHttp-Network-Tester/1.0"
                    }
                });
                results.get = getResult;
                console.log("✓ GET请求成功");

                // 显示更多关于GET响应的信息
                console.log("\n====== GET请求结果摘要 ======");
                console.log(`状态: ${getResult.status_code} ${getResult.status_message}`);

                // 如果有JSON数据，显示一些关键信息
                if (getResult.json) {
                    console.log("GET响应数据预览:");

                    // httpbin.org/get 通常会返回请求信息的镜像
                    if (getResult.json.headers) {
                        console.log("- 发送的请求头:");
                        Object.keys(getResult.json.headers).forEach(key => {
                            console.log(`  ${key}: ${getResult.json.headers[key]}`);
                        });
                    }

                    if (getResult.json.url) {
                        console.log(`- 请求URL: ${getResult.json.url}`);
                    }

                    if (getResult.json.origin) {
                        console.log(`- 来源IP: ${getResult.json.origin}`);
                    }
                }
                console.log("===============================\n");
            } catch (error) {
                results.get = { error: `GET请求失败: ${error.message}` };
                console.log("✗ GET请求失败");
            }

            // 4. 测试POST请求
            console.log("测试POST请求...");
            try {
                const postResult = await http_post({
                    url: "https://httpbin.org/post",
                    body: {
                        name: "OkHttp网络测试",
                        timestamp: new Date().toISOString()
                    },
                    headers: {
                        "Content-Type": "application/json",
                        "User-Agent": "OkHttp-Network-Tester/1.0"
                    }
                });
                results.post = postResult;
                console.log("✓ POST请求成功");
            } catch (error) {
                results.post = { error: `POST请求失败: ${error.message}` };
                console.log("✗ POST请求失败");
            }

            // 5. 测试PUT请求
            console.log("测试PUT请求...");
            try {
                const putResult = await http_put({
                    url: "https://httpbin.org/put",
                    body: {
                        name: "OkHttp网络测试",
                        timestamp: new Date().toISOString(),
                        action: "update"
                    },
                    headers: {
                        "Content-Type": "application/json",
                        "User-Agent": "OkHttp-Network-Tester/1.0"
                    }
                });
                results.put = putResult;
                console.log("✓ PUT请求成功");
            } catch (error) {
                results.put = { error: `PUT请求失败: ${error.message}` };
                console.log("✗ PUT请求失败");
            }

            // 6. 测试DELETE请求
            console.log("测试DELETE请求...");
            try {
                const deleteResult = await http_delete({
                    url: "https://httpbin.org/delete",
                    headers: {
                        "User-Agent": "OkHttp-Network-Tester/1.0"
                    }
                });
                results.delete = deleteResult;
                console.log("✓ DELETE请求成功");
            } catch (error) {
                results.delete = { error: `DELETE请求失败: ${error.message}` };
                console.log("✗ DELETE请求失败");
            }

            // 返回所有测试结果
            return {
                message: "网络功能测试完成",
                test_results: results,
                timestamp: new Date().toISOString(),
                summary: "测试了各种网络功能，包括配置、Ping测试和HTTP请求。请查看各功能的测试结果。"
            };
        } catch (error) {
            return {
                success: false,
                message: `测试过程中发生错误: ${error.message}`
            };
        }
    }

    /**
     * 包装函数 - 统一处理所有network_test函数的返回结果
     * @param func 原始函数
     * @param params 函数参数
     * @param successMessage 成功消息
     * @param failMessage 失败消息
     * @param additionalInfo 附加信息(可选)
     */
    async function network_wrap<T>(
        func: (params: any) => Promise<any>,
        params: any,
        successMessage: string,
        failMessage: string,
        additionalInfo: string = ""
    ): Promise<void> {
        try {
            console.log(`开始执行函数: ${func.name || '匿名函数'}`);
            console.log(`参数:`, JSON.stringify(params, undefined, 2));

            // 执行原始函数
            const result = await func(params);

            console.log(`函数 ${func.name || '匿名函数'} 执行结果:`, JSON.stringify(result, undefined, 2));

            // 如果原始函数已经调用了complete，就不需要再次调用
            if (result === undefined) return;

            // 根据结果类型处理
            if (typeof result === "boolean") {
                // 布尔类型结果
                complete({
                    success: result,
                    message: result ? successMessage : failMessage,
                    additionalInfo: additionalInfo
                });
            } else {
                // 数据类型结果
                complete({
                    success: true,
                    message: successMessage,
                    additionalInfo: additionalInfo,
                    data: result
                });
            }
        } catch (error) {
            // 详细记录错误信息
            console.error(`函数 ${func.name || '匿名函数'} 执行失败!`);
            console.error(`错误信息: ${error.message}`);
            console.error(`错误堆栈: ${error.stack}`);

            // 处理错误
            complete({
                success: false,
                message: `${failMessage}: ${error.message}`,
                additionalInfo: additionalInfo,
                error_stack: error.stack
            });
        }
    }

    return {
        config_client: async (params) => await network_wrap(
            config_client,
            params,
            "配置HTTP客户端成功",
            "配置HTTP客户端失败"
        ),
        http_get: async (params) => await network_wrap(
            http_get,
            params,
            "GET请求成功",
            "GET请求失败"
        ),
        http_post: async (params) => await network_wrap(
            http_post,
            params,
            "POST请求成功",
            "POST请求失败"
        ),
        http_put: async (params) => await network_wrap(
            http_put,
            params,
            "PUT请求成功",
            "PUT请求失败"
        ),
        http_delete: async (params) => await network_wrap(
            http_delete,
            params,
            "DELETE请求成功",
            "DELETE请求失败"
        ),
        ping_test: async (params) => await network_wrap(
            ping_test,
            params,
            "网络连接测试成功",
            "网络连接测试失败"
        ),
        test_all: async () => await network_wrap(
            test_all,
            {},
            "网络功能测试完成",
            "网络功能测试失败"
        )
    };
})();

// 导出所有函数
exports.config_client = networkTest.config_client;
exports.http_get = networkTest.http_get;
exports.http_post = networkTest.http_post;
exports.http_put = networkTest.http_put;
exports.http_delete = networkTest.http_delete;
exports.ping_test = networkTest.ping_test;
exports.test_all = networkTest.test_all;
exports.main = networkTest.test_all; 