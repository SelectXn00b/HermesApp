/* METADATA
{
  name: code_reader

  display_name: {
    zh: "代码读取器"
    en: "Code Reader"
  }
  description: { zh: "提供代码匹配抓取功能", en: "Provide code searching and extraction utilities." },
  enabledByDefault: true
  
  category: "File"
  tools: [
    {
      name: search_code_in_folder
      description: { zh: "在文件夹中搜索匹配的代码", en: "Search for matching code in a folder." }
      parameters: [
        {
          name: folder_path
          description: { zh: "要搜索的文件夹路径", en: "Folder path to search." }
          type: string
          required: true
        },
        {
          name: pattern
          description: { zh: "要搜索的代码模式(字符串或正则表达式)", en: "Pattern to search for (string or regular expression)." }
          type: string
          required: true
        },
        {
          name: file_extensions
          description: { zh: "要搜索的文件扩展名列表(如 \".ts,.js\")", en: "File extension list to include (e.g. \".ts,.js\")." }
          type: string
          required: false
        },
        {
          name: recursive
          description: { zh: "是否递归搜索子文件夹(true/false)", en: "Whether to search subfolders recursively (true/false)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: extract_functions
      description: { zh: "提取文件夹中的所有函数定义", en: "Extract all function definitions from a folder." }
      parameters: [
        {
          name: folder_path
          description: { zh: "要搜索的文件夹路径", en: "Folder path to search." }
          type: string
          required: true
        },
        {
          name: function_name_pattern
          description: { zh: "函数名匹配模式(可选)", en: "Function name matching pattern (optional)." }
          type: string
          required: false
        },
        {
          name: file_extensions
          description: { zh: "要搜索的文件扩展名列表(如 \".ts,.js\")", en: "File extension list to include (e.g. \".ts,.js\")." }
          type: string
          required: false
        },
        {
          name: recursive
          description: { zh: "是否递归搜索子文件夹(true/false)", en: "Whether to search subfolders recursively (true/false)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: extract_function_block
      description: { zh: "提取特定函数的代码块", en: "Extract the code block for a specific function." }
      parameters: [
        {
          name: file_path
          description: { zh: "要读取的文件路径", en: "File path to read." }
          type: string
          required: true
        },
        {
          name: function_name
          description: { zh: "要提取的函数名称", en: "Function name to extract." }
          type: string
          required: true
        },
        {
          name: include_line_numbers
          description: { zh: "是否在结果中包含行号(true/false)", en: "Whether to include line numbers in the result (true/false)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: find_code_blocks
      description: { zh: "查找文件中的代码块", en: "Find code blocks in a file." }
      parameters: [
        {
          name: file_path
          description: { zh: "要读取的文件路径", en: "File path to read." }
          type: string
          required: true
        },
        {
          name: block_pattern
          description: { zh: "代码块匹配模式(如类定义、接口等)", en: "Code block matching pattern (e.g., class definition, interface, etc.)." }
          type: string
          required: true
        },
        {
          name: include_line_numbers
          description: { zh: "是否在结果中包含行号(true/false)", en: "Whether to include line numbers in the result (true/false)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: read_regex_matches
      description: { zh: "读取文件中匹配正则表达式的内容", en: "Read content in a file that matches a regular expression." }
      parameters: [
        {
          name: file_path
          description: { zh: "要读取的文件路径", en: "File path to read." }
          type: string
          required: true
        },
        {
          name: regex
          description: { zh: "正则表达式字符串", en: "Regular expression string." }
          type: string
          required: true
        },
        {
          name: include_line_numbers
          description: { zh: "是否在结果中包含行号(true/false)", en: "Whether to include line numbers in the result (true/false)." }
          type: string
          required: false
        }
      ]
    }
  ]
}*/
/**
 * Writer模块 - 提供代码抓取和分析功能
 */
const writer = (function () {
    /**
     * 参数类型转换函数 - 将输入参数转换为期望的数据类型
     * @param params 输入参数对象
     * @param paramTypes 参数类型定义
     * @returns 转换后的参数对象
     */
    function convertParamTypes(params, paramTypes) {
        if (!params || !paramTypes)
            return params;
        const result = {};
        for (const key in params) {
            if (params[key] === undefined || params[key] === undefined) {
                result[key] = params[key];
                continue;
            }
            const expectedType = paramTypes[key];
            if (!expectedType) {
                // 如果没有指定类型，保持原样
                result[key] = params[key];
                continue;
            }
            // 获取参数值
            const value = params[key];
            try {
                switch (expectedType.toLowerCase()) {
                    case 'number':
                        // 将字符串转换为数字
                        if (typeof value === 'string') {
                            if (value.includes('.')) {
                                result[key] = parseFloat(value);
                            }
                            else {
                                result[key] = parseInt(value, 10);
                            }
                            // 检查转换结果是否为有效数字
                            if (isNaN(result[key])) {
                                throw new Error(`参数 ${key} 无法转换为数字: ${value}`);
                            }
                        }
                        else {
                            result[key] = value;
                        }
                        break;
                    case 'boolean':
                        // 将字符串转换为布尔值
                        if (typeof value === 'string') {
                            const lowerValue = value.toLowerCase();
                            if (lowerValue === 'true' || lowerValue === '1' || lowerValue === 'yes') {
                                result[key] = true;
                            }
                            else if (lowerValue === 'false' || lowerValue === '0' || lowerValue === 'no') {
                                result[key] = false;
                            }
                            else {
                                throw new Error(`参数 ${key} 无法转换为布尔值: ${value}`);
                            }
                        }
                        else {
                            result[key] = value;
                        }
                        break;
                    case 'array':
                        // 将字符串转换为数组
                        if (typeof value === 'string') {
                            try {
                                result[key] = JSON.parse(value);
                                if (!Array.isArray(result[key])) {
                                    throw new Error('解析结果不是数组');
                                }
                            }
                            catch (e) {
                                throw new Error(`参数 ${key} 无法转换为数组: ${value}`);
                            }
                        }
                        else {
                            result[key] = value;
                        }
                        break;
                    case 'object':
                        // 将字符串转换为对象
                        if (typeof value === 'string') {
                            try {
                                result[key] = JSON.parse(value);
                                if (Array.isArray(result[key]) || typeof result[key] !== 'object') {
                                    throw new Error('解析结果不是对象');
                                }
                            }
                            catch (e) {
                                throw new Error(`参数 ${key} 无法转换为对象: ${value}`);
                            }
                        }
                        else {
                            result[key] = value;
                        }
                        break;
                    default:
                        // 其他类型或未指定类型，保持原样
                        result[key] = value;
                }
            }
            catch (error) {
                console.error(`参数类型转换错误: ${error.message}`);
                // 转换失败时保留原始值
                result[key] = value;
            }
        }
        return result;
    }
    /**
     * 包装函数 - 统一处理所有Writer函数的返回结果
     * @param func 原始函数
     * @param params 函数参数
     * @param successMessage 成功消息
     * @param failMessage 失败消息
     */
    async function writer_wrap(func, params, successMessage, failMessage) {
        try {
            console.log(`开始执行函数: ${func.name || '匿名函数'}`);
            console.log(`参数:`, JSON.stringify(params, undefined, 2));
            // 执行原始函数
            const result = await func(params);
            console.log(`函数 ${func.name || '匿名函数'} 执行结果:`, JSON.stringify(result, undefined, 2));
            // 如果原始函数已经调用了complete，就不需要再次调用
            if (result === undefined)
                return;
            // 根据结果类型处理
            if (typeof result === "boolean") {
                // 布尔类型结果
                complete({
                    success: result,
                    message: result ? successMessage : failMessage
                });
            }
            else {
                // 数据类型结果
                complete({
                    success: true,
                    message: successMessage,
                    data: result
                });
            }
        }
        catch (error) {
            // 详细记录错误信息
            console.error(`函数 ${func.name || '匿名函数'} 执行失败!`);
            console.error(`错误信息: ${error.message}`);
            console.error(`错误堆栈: ${error.stack}`);
            // 处理错误
            complete({
                success: false,
                message: `${failMessage}: ${error.message}`,
                error_stack: error.stack
            });
        }
    }
    /**
     * 递归读取目录中的所有文件
     * @param dir 目录路径
     * @param fileList 文件列表累加器
     * @param extensions 文件扩展名过滤列表
     * @returns 文件路径列表
     */
    async function readFilesRecursively(dir, fileList = [], extensions) {
        try {
            // 使用Tools.Files.list替代fs.readdirSync
            const listResult = await Tools.Files.list(dir);
            const entries = listResult.entries || [];
            // 遍历目录条目
            for (const entry of entries) {
                const filePath = `${dir}/${entry.name}`;
                if (entry.isDirectory) {
                    // 递归处理子目录
                    await readFilesRecursively(filePath, fileList, extensions);
                }
                else {
                    // 过滤文件扩展名
                    if (!extensions || extensions.length === 0 ||
                        extensions.some(ext => entry.name.toLowerCase().endsWith(ext.toLowerCase()))) {
                        fileList.push(filePath);
                    }
                }
            }
        }
        catch (error) {
            console.error(`读取目录失败: ${error.message}`);
        }
        return fileList;
    }
    /**
     * 在文件夹中搜索匹配的代码
     * @param params 搜索参数
     * @returns 搜索结果
     */
    async function search_code_in_folder(params) {
        const paramTypes = {
            recursive: 'boolean',
        };
        params = convertParamTypes(params, paramTypes);
        const { folder_path, pattern, file_extensions = '.js,.ts,.jsx,.tsx,.java,.cs,.py', recursive = true } = params;
        // 验证必需参数
        if (!folder_path) {
            throw new Error('文件夹路径不能为空');
        }
        if (!pattern) {
            throw new Error('搜索模式不能为空');
        }
        try {
            const patternRegex = new RegExp(pattern, 'gm');
            const extensions = file_extensions.split(',');
            let files;
            // 根据recursive参数决定是否递归搜索
            if (recursive) {
                files = await readFilesRecursively(folder_path, [], extensions);
            }
            else {
                // 使用Tools.Files.list替代fs.readdirSync
                const listResult = await Tools.Files.list(folder_path);
                const entries = listResult.entries || [];
                files = entries
                    .filter(entry => !entry.isDirectory &&
                    extensions.some(ext => entry.name.toLowerCase().endsWith(ext.toLowerCase())))
                    .map(entry => `${folder_path}/${entry.name}`);
            }
            // 搜索结果
            const results = [];
            for (const file of files) {
                try {
                    // 使用Tools.Files.read替代fs.readFileSync
                    const fileResult = await Tools.Files.read(file);
                    const content = fileResult.content || '';
                    const matches = [];
                    let match;
                    // 使用正则表达式查找所有匹配项
                    patternRegex.lastIndex = 0; // 重置正则表达式索引
                    while ((match = patternRegex.exec(content)) !== undefined) {
                        const lineNumber = content.substring(0, match.index).split('\n').length;
                        const lineContent = content.split('\n')[lineNumber - 1];
                        matches.push({
                            match: match[0],
                            line: lineNumber,
                            content: lineContent
                        });
                    }
                    if (matches.length > 0) {
                        results.push({
                            file: file,
                            matches: matches
                        });
                    }
                }
                catch (error) {
                    console.error(`处理文件 ${file} 时出错: ${error.message}`);
                }
            }
            return results;
        }
        catch (error) {
            throw new Error(`搜索代码时出错: ${error.message}`);
        }
    }
    /**
     * 提取文件夹中的所有函数定义
     * @param params 提取参数
     * @returns 提取结果
     */
    async function extract_functions(params) {
        const paramTypes = {
            recursive: 'boolean',
        };
        params = convertParamTypes(params, paramTypes);
        const { folder_path, function_name_pattern = '.*', file_extensions = '.js,.ts,.jsx,.tsx', recursive = true } = params;
        // 验证必需参数
        if (!folder_path) {
            throw new Error('文件夹路径不能为空');
        }
        try {
            // 函数定义的正则表达式模式
            // 匹配函数声明、函数表达式、箭头函数、类方法等
            const functionPatterns = [
                // 函数声明: function name() {}
                `function\\s+(${function_name_pattern})\\s*\\([^)]*\\)\\s*\\{`,
                // 函数表达式: const name = function() {}
                `(?:const|let|var)\\s+(${function_name_pattern})\\s*=\\s*function\\s*\\([^)]*\\)\\s*\\{`,
                // 箭头函数: const name = () => {}
                `(?:const|let|var)\\s+(${function_name_pattern})\\s*=\\s*\\([^)]*\\)\\s*=>\\s*\\{`,
                // 类方法: name() {}
                `(?:async\\s+)?(${function_name_pattern})\\s*\\([^)]*\\)\\s*\\{`
            ];
            // 合并所有模式
            const combinedPattern = functionPatterns.join('|');
            // 使用通用代码搜索功能查找所有函数
            return await search_code_in_folder({
                folder_path,
                pattern: combinedPattern,
                file_extensions,
                recursive
            });
        }
        catch (error) {
            throw new Error(`提取函数时出错: ${error.message}`);
        }
    }
    /**
     * 提取特定函数的代码块
     * @param params 提取参数
     * @returns 提取结果
     */
    async function extract_function_block(params) {
        const paramTypes = {
            include_line_numbers: 'boolean',
        };
        params = convertParamTypes(params, paramTypes);
        const { file_path, function_name, include_line_numbers = false } = params;
        // 验证必需参数
        if (!file_path) {
            throw new Error('文件路径不能为空');
        }
        if (!function_name) {
            throw new Error('函数名称不能为空');
        }
        try {
            // 使用Tools.Files.read替代fs.readFileSync
            const fileResult = await Tools.Files.read(file_path);
            const content = fileResult.content || '';
            const lines = content.split('\n');
            // 函数定义的正则表达式模式
            const functionPatterns = [
                // 函数声明: function name() {}
                `function\\s+${function_name}\\s*\\([^)]*\\)\\s*\\{`,
                // 函数表达式: const name = function() {}
                `(?:const|let|var)\\s+${function_name}\\s*=\\s*function\\s*\\([^)]*\\)\\s*\\{`,
                // 箭头函数: const name = () => {}
                `(?:const|let|var)\\s+${function_name}\\s*=\\s*\\([^)]*\\)\\s*=>\\s*\\{`,
                // 类方法: name() {}
                `(?:async\\s+)?${function_name}\\s*\\([^)]*\\)\\s*\\{`
            ];
            // 合并所有模式
            const combinedPattern = functionPatterns.join('|');
            const functionRegex = new RegExp(combinedPattern);
            // 查找函数定义行
            let startLine = -1;
            for (let i = 0; i < lines.length; i++) {
                if (functionRegex.test(lines[i])) {
                    startLine = i;
                    break;
                }
            }
            if (startLine === -1) {
                return { error: `未找到函数: ${function_name}` };
            }
            // 追踪括号匹配，找到函数结束位置
            let bracesCount = 0;
            let endLine = startLine;
            // 检查函数定义那一行的花括号
            const openingBraces = (lines[startLine].match(/{/g) || []).length;
            const closingBraces = (lines[startLine].match(/}/g) || []).length;
            bracesCount = openingBraces - closingBraces;
            // 继续向下查找匹配的闭合括号
            for (let i = startLine + 1; i < lines.length; i++) {
                const openBraces = (lines[i].match(/{/g) || []).length;
                const closeBraces = (lines[i].match(/}/g) || []).length;
                bracesCount += openBraces;
                bracesCount -= closeBraces;
                // 当括号计数归零，找到函数结束位置
                if (bracesCount === 0) {
                    endLine = i;
                    break;
                }
            }
            // 提取函数代码块
            const functionBlock = lines.slice(startLine, endLine + 1).join('\n');
            const result = {
                function_name,
                code: functionBlock,
            };
            if (include_line_numbers) {
                result.start_line = startLine + 1; // 转换为1-based索引
                result.end_line = endLine + 1;
            }
            return result;
        }
        catch (error) {
            throw new Error(`提取函数代码块时出错: ${error.message}`);
        }
    }
    /**
     * 查找文件中的代码块
     * @param params 查找参数
     * @returns 查找结果
     */
    async function find_code_blocks(params) {
        const paramTypes = {
            include_line_numbers: 'boolean',
        };
        params = convertParamTypes(params, paramTypes);
        const { file_path, block_pattern, include_line_numbers = false } = params;
        // 验证必需参数
        if (!file_path) {
            throw new Error('文件路径不能为空');
        }
        if (!block_pattern) {
            throw new Error('代码块匹配模式不能为空');
        }
        try {
            // 使用Tools.Files.read替代fs.readFileSync
            const fileResult = await Tools.Files.read(file_path);
            const content = fileResult.content || '';
            const blockRegex = new RegExp(block_pattern, 'gm');
            const matches = [];
            let match;
            // 使用正则表达式查找所有匹配项
            while ((match = blockRegex.exec(content)) !== undefined) {
                const matchText = match[0];
                const startIndex = match.index;
                // 查找代码块的开始和结束位置
                const lines = content.substring(0, startIndex).split('\n');
                const startLine = lines.length;
                let endIndex = startIndex + matchText.length;
                let bracesCount = 0;
                // 如果匹配到的是代码块开始，需要找到对应的结束
                if (matchText.includes('{')) {
                    // 计算初始左括号数量
                    const initialOpenBraces = (matchText.match(/{/g) || []).length;
                    const initialCloseBraces = (matchText.match(/}/g) || []).length;
                    bracesCount = initialOpenBraces - initialCloseBraces;
                    // 如果括号不平衡，继续向后查找
                    if (bracesCount > 0) {
                        const remainingContent = content.substring(endIndex);
                        let currentPos = 0;
                        // 逐字符扫描，跟踪括号嵌套
                        for (let i = 0; i < remainingContent.length; i++) {
                            const char = remainingContent[i];
                            if (char === '{') {
                                bracesCount++;
                            }
                            else if (char === '}') {
                                bracesCount--;
                                // 找到匹配的闭合括号
                                if (bracesCount === 0) {
                                    currentPos = i + 1; // +1 包含闭合括号
                                    break;
                                }
                            }
                        }
                        // 更新块的结束位置
                        endIndex += currentPos;
                    }
                }
                // 提取完整的代码块
                const codeBlock = content.substring(startIndex, endIndex);
                // 确定结束行号
                const blockLines = codeBlock.split('\n');
                const endLine = startLine + blockLines.length - 1;
                const result = {
                    block: codeBlock,
                };
                if (include_line_numbers) {
                    result.start_line = startLine;
                    result.end_line = endLine;
                }
                matches.push(result);
            }
            return {
                file: file_path,
                blocks: matches
            };
        }
        catch (error) {
            throw new Error(`查找代码块时出错: ${error.message}`);
        }
    }
    /**
     * 读取文件中匹配正则表达式的内容
     * @param params 读取参数
     * @returns 读取结果
     */
    async function read_regex_matches(params) {
        const paramTypes = {
            include_line_numbers: 'boolean',
        };
        params = convertParamTypes(params, paramTypes);
        const { file_path, regex, include_line_numbers = false } = params;
        // 验证必需参数
        if (!file_path) {
            throw new Error('文件路径不能为空');
        }
        if (!regex) {
            throw new Error('正则表达式不能为空');
        }
        try {
            // 使用Tools.Files.read替代fs.readFileSync
            const fileResult = await Tools.Files.read(file_path);
            const content = fileResult.content || '';
            const lines = content.split('\n');
            const pattern = new RegExp(regex, 'gm');
            const matches = [];
            let match;
            // 使用正则表达式查找所有匹配项
            while ((match = pattern.exec(content)) !== undefined) {
                const lineNumber = content.substring(0, match.index).split('\n').length;
                const lineContent = content.split('\n')[lineNumber - 1];
                const result = {
                    match: match[0],
                    content: lineContent,
                };
                if (include_line_numbers) {
                    result.line = lineNumber;
                }
                // 添加捕获组
                if (match.length > 1) {
                    result.groups = match.slice(1);
                }
                matches.push(result);
            }
            return {
                file: file_path,
                matches: matches
            };
        }
        catch (error) {
            throw new Error(`匹配正则表达式时出错: ${error.message}`);
        }
    }
    // 返回模块公共方法
    return {
        search_code_in_folder: (params) => writer_wrap(search_code_in_folder, params, '代码搜索完成', '代码搜索失败'),
        extract_functions: (params) => writer_wrap(extract_functions, params, '函数提取完成', '函数提取失败'),
        extract_function_block: (params) => writer_wrap(extract_function_block, params, '函数代码块提取完成', '函数代码块提取失败'),
        find_code_blocks: (params) => writer_wrap(find_code_blocks, params, '代码块查找完成', '代码块查找失败'),
        read_regex_matches: (params) => writer_wrap(read_regex_matches, params, '正则表达式匹配完成', '正则表达式匹配失败')
    };
})();
// 导出所有函数
exports.search_code_in_folder = writer.search_code_in_folder;
exports.extract_functions = writer.extract_functions;
exports.extract_function_block = writer.extract_function_block;
exports.find_code_blocks = writer.find_code_blocks;
exports.read_regex_matches = writer.read_regex_matches;
