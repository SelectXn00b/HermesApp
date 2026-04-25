/* METADATA
{
    "name": "pdf_vision_parser",
    "display_name": {
        "zh": "PDF 逐页识图解析",
        "en": "PDF Vision Parser"
    },
    "description": {
        "zh": "将 PDF 逐页渲染为图片，并调用用户当前配置的识图模型进行解析，最后按页拼接结果。",
        "en": "Render a PDF page by page into images, send each image to the user's configured vision model, and merge the results."
    },
    "enabledByDefault": false,
    "category": "File",
    "tools": [
        {
            "name": "parse_pdf_with_vision",
            "description": {
                "zh": "将 PDF 逐页拆成图片，逐页调用 IMAGE_RECOGNITION 功能模型识图，并返回分页索引与输出文件路径。",
                "en": "Split a PDF into page images, analyze each page with the IMAGE_RECOGNITION function model, and return per-page indexes plus output file paths."
            },
            "parameters": [
                {
                    "name": "pdf_path",
                    "description": {
                        "zh": "PDF 文件路径。",
                        "en": "Path to the PDF file."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "page_prompt",
                    "description": {
                        "zh": "每页识图提示词；不传则使用默认的尽量转文字提示词。",
                        "en": "Vision prompt for each page; defaults to a text-extraction-oriented prompt."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "start_page",
                    "description": {
                        "zh": "起始页码，1-based，默认 1。",
                        "en": "Start page number, 1-based, default 1."
                    },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "end_page",
                    "description": {
                        "zh": "结束页码，1-based，默认文档最后一页。",
                        "en": "End page number, 1-based, default is the last page."
                    },
                    "type": "number",
                    "required": false
                }
            ]
        }
    ]
}*/

const PdfVisionParser = (function () {
    const DEFAULT_PAGE_PROMPT =
        "尽量完整提取本页可见文字与结构，保留标题、列表、表格顺序，不做总结，不补充页外信息；公式和图表转成可读文本描述";
    const TOOL_NAME = "parse_pdf_with_vision";
    const TOOL_FUNCTION_TYPE = "IMAGE_RECOGNITION";
    const RENDER_SCALE = 2;
    const IMAGE_FORMAT = "png";
    const MERGED_OUTPUT_FILE_NAME = "parsed_output.txt";

    const File = Java.type("java.io.File");
    const FileOutputStream = Java.type("java.io.FileOutputStream");
    const OutputStreamWriter = Java.type("java.io.OutputStreamWriter");
    const BufferedWriter = Java.type("java.io.BufferedWriter");
    const ParcelFileDescriptor = Java.type("android.os.ParcelFileDescriptor");
    const PdfRenderer = Java.type("android.graphics.pdf.PdfRenderer");
    const PdfRendererPage = Java.type("android.graphics.pdf.PdfRenderer$Page");
    const Bitmap = Java.type("android.graphics.Bitmap");
    const BitmapConfig = Java.type("android.graphics.Bitmap$Config");
    const BitmapCompressFormat = Java.type("android.graphics.Bitmap$CompressFormat");
    const Color = Java.type("android.graphics.Color");
    const StandardCharsets = Java.type("java.nio.charset.StandardCharsets");
    const EnhancedAIService = Java.type("com.ai.assistance.operit.api.chat.EnhancedAIService");

    interface ParsePdfWithVisionParams {
        pdf_path: string;
        page_prompt?: string;
        start_page?: number;
        end_page?: number;
    }

    interface FailedPageInfo {
        page_number: number;
        stage: "precheck" | "render" | "vision";
        error: string;
    }

    interface PageResult {
        page_number: number;
        image_path: string;
        image_link: string;
        output_path: string;
    }

    interface InternalPageResult extends PageResult {
        analysis: string;
    }

    interface VisionModelInfo {
        function_type: "IMAGE_RECOGNITION";
        config_id: string;
        config_name: string;
        selected_model: string;
    }

    interface ParsePdfWithVisionData {
        pdf_path: string;
        image_dir: string;
        output_path: string;
        total_page_count: number;
        processed_page_count: number;
        pages: PageResult[];
        vision_model: VisionModelInfo;
        failed_page?: FailedPageInfo;
    }

    interface ToolResponse {
        success: boolean;
        message: string;
        data?: ParsePdfWithVisionData;
    }

    function isBlank(value: unknown): boolean {
        return String(value ?? "").trim().length === 0;
    }

    function toTrimmedString(value: unknown): string {
        return String(value ?? "").trim();
    }

    function parsePositiveInteger(
        value: number | undefined,
        fieldName: string,
        options?: { allowUndefined?: boolean }
    ): number | undefined {
        if (value === undefined || value === null) {
            if (options?.allowUndefined) {
                return undefined;
            }
            throw new Error(`${fieldName} 不能为空。`);
        }

        if (!Number.isInteger(value) || value <= 0) {
            throw new Error(`${fieldName} 必须是大于 0 的整数。`);
        }
        return value;
    }

    function buildMergedText(pages: InternalPageResult[]): string {
        return pages
            .map(page => `===== Page ${page.page_number} =====\n${page.analysis}`)
            .join("\n\n");
    }

    function buildVisionModelInfo(binding: FunctionModelConfigResultData): VisionModelInfo {
        return {
            function_type: TOOL_FUNCTION_TYPE,
            config_id: String(binding.configId || ""),
            config_name: String(binding.configName || ""),
            selected_model: String(binding.selectedModel || "")
        };
    }

    function buildData(
        pdfPath: string,
        imageDir: string,
        outputPath: string,
        totalPageCount: number,
        pages: InternalPageResult[],
        visionModel: VisionModelInfo,
        failedPage?: FailedPageInfo
    ): ParsePdfWithVisionData {
        const data: ParsePdfWithVisionData = {
            pdf_path: pdfPath,
            image_dir: imageDir,
            output_path: outputPath,
            total_page_count: totalPageCount,
            processed_page_count: pages.length,
            pages: pages.map(page => ({
                page_number: page.page_number,
                image_path: page.image_path,
                image_link: page.image_link,
                output_path: page.output_path
            })),
            vision_model: visionModel
        };

        if (failedPage) {
            data.failed_page = failedPage;
        }

        return data;
    }

    function buildPrecheckFailure(message: string): ToolResponse {
        return {
            success: false,
            message,
            data: {
                pdf_path: "",
                image_dir: "",
                output_path: "",
                total_page_count: 0,
                processed_page_count: 0,
                pages: [],
                vision_model: {
                    function_type: TOOL_FUNCTION_TYPE,
                    config_id: "",
                    config_name: "",
                    selected_model: ""
                },
                failed_page: {
                    page_number: 0,
                    stage: "precheck",
                    error: message
                }
            }
        };
    }

    function safeClose(resource: any, label: string): void {
        if (!resource) {
            return;
        }
        try {
            resource.close();
        } catch (error) {
            console.warn(`[${TOOL_NAME}] 关闭 ${label} 失败: ${String(error)}`);
        }
    }

    function safeRecycle(bitmap: any): void {
        if (!bitmap) {
            return;
        }
        try {
            bitmap.recycle();
        } catch (error) {
            console.warn(`[${TOOL_NAME}] 回收 bitmap 失败: ${String(error)}`);
        }
    }

    function ensureDirectoryExists(dir: any): void {
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                throw new Error(`目标路径不是目录: ${String(dir.getAbsolutePath())}`);
            }
            return;
        }
        const created = dir.mkdirs();
        if (!created) {
            throw new Error(`无法创建目录: ${String(dir.getAbsolutePath())}`);
        }
    }

    function resolveAppContext(): JavaBridgeInstance {
        if (typeof Java.getApplicationContext === "function") {
            return Java.getApplicationContext();
        }
        if (typeof Java.getContext === "function") {
            return Java.getContext();
        }
        throw new Error("无法获取应用 Context。");
    }

    function createOutputDirectory(): any {
        const parserRootDir = new File(OPERIT_CLEAN_ON_EXIT_DIR, "pdf_vision_parser");
        ensureDirectoryExists(parserRootDir);

        const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
        const runDir = new File(parserRootDir, timestamp);
        ensureDirectoryExists(runDir);
        return runDir;
    }

    function writeTextFile(targetFile: any, content: string): string {
        let outputStream: any = null;
        let writer: any = null;
        let bufferedWriter: any = null;
        try {
            outputStream = new FileOutputStream(targetFile);
            writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            bufferedWriter = new BufferedWriter(writer);
            bufferedWriter.write(String(content ?? ""));
            bufferedWriter.flush();
            return String(targetFile.getAbsolutePath());
        } finally {
            safeClose(bufferedWriter, "bufferedWriter");
            safeClose(writer, "writer");
            safeClose(outputStream, "outputStream");
        }
    }

    function writePageOutputFile(outputDir: any, pageNumber: number, content: string): string {
        const textFile = new File(outputDir, `page-${padPageNumber(pageNumber)}.txt`);
        return writeTextFile(textFile, content);
    }

    function writeMergedOutputFile(outputDir: any, pages: InternalPageResult[]): string {
        if (!outputDir) {
            return "";
        }
        const mergedFile = new File(outputDir, MERGED_OUTPUT_FILE_NAME);
        return writeTextFile(mergedFile, buildMergedText(pages));
    }

    function padPageNumber(pageNumber: number): string {
        return String(pageNumber).padStart(4, "0");
    }

    function assertVisionBinding(binding: FunctionModelConfigResultData): void {
        if (!binding || isBlank(binding.configId)) {
            throw new Error("IMAGE_RECOGNITION 当前没有可用绑定。");
        }
        if (!binding.config || binding.config.enableDirectImageProcessing !== true) {
            throw new Error("IMAGE_RECOGNITION 当前绑定的模型未启用识图能力。");
        }
    }

    function validatePdfFile(pdfPath: string): any {
        if (isBlank(pdfPath)) {
            throw new Error("pdf_path 不能为空。");
        }

        const pdfFile = new File(pdfPath);
        if (!pdfFile.exists() || !pdfFile.isFile()) {
            throw new Error(`PDF 文件不存在: ${pdfPath}`);
        }

        if (!String(pdfFile.getName()).toLowerCase().endsWith(".pdf")) {
            throw new Error(`文件不是 PDF: ${pdfPath}`);
        }

        return pdfFile;
    }

    function stripThinkingContent(content: string): string {
        return String(content ?? "")
            .replace(/<think(?:ing)?>[\s\S]*?(<\/think(?:ing)?>|\z)/gi, "")
            .replace(/<search>[\s\S]*?(<\/search>|\z)/gi, "")
            .trim();
    }

    async function analyzePageImage(
        service: JavaBridgeInstance,
        imagePath: string,
        prompt: string
    ): Promise<string> {
        const rawAnalysis = String(
            await service.callSuspend("analyzeImageWithIntent", imagePath, prompt)
        ).trim();
        const analysis = stripThinkingContent(rawAnalysis);

        if (analysis.length === 0) {
            throw new Error("识图模型返回了空结果。");
        }
        if (/^Image recognition failed:/i.test(rawAnalysis) || /^Image recognition failed:/i.test(analysis)) {
            throw new Error(rawAnalysis || analysis);
        }

        return analysis;
    }

    async function parsePdfWithVisionInternal(
        params: ParsePdfWithVisionParams
    ): Promise<ToolResponse> {
        let binding: FunctionModelConfigResultData;
        try {
            binding = await Tools.SoftwareSettings.getFunctionModelConfig(TOOL_FUNCTION_TYPE);
            assertVisionBinding(binding);
        } catch (error) {
            const message = String(error instanceof Error ? error.message : error);
            return buildPrecheckFailure(message);
        }

        const visionModel = buildVisionModelInfo(binding);
        const rawPdfPath = toTrimmedString(params?.pdf_path);

        let pdfFile: any;
        let startPage: number | undefined;
        let endPageInput: number | undefined;
        const resolvedPrompt =
            toTrimmedString(params?.page_prompt) || DEFAULT_PAGE_PROMPT;

        try {
            pdfFile = validatePdfFile(rawPdfPath);
            startPage = parsePositiveInteger(params?.start_page, "start_page", {
                allowUndefined: true
            }) ?? 1;
            endPageInput = parsePositiveInteger(params?.end_page, "end_page", {
                allowUndefined: true
            });
        } catch (error) {
            const message = String(error instanceof Error ? error.message : error);
            return {
                success: false,
                message,
                data: {
                    pdf_path: rawPdfPath,
                    image_dir: "",
                    output_path: "",
                    total_page_count: 0,
                    processed_page_count: 0,
                    pages: [],
                    vision_model: visionModel,
                    failed_page: {
                        page_number: 0,
                        stage: "precheck",
                        error: message
                    }
                }
            };
        }

        const pages: InternalPageResult[] = [];
        const context = resolveAppContext();
        const enhancedAiService = EnhancedAIService.getInstance(context) as JavaBridgeInstance;
        let outputDir: any = null;
        let imageDir = "";
        let outputPath = "";

        let fileDescriptor: any = null;
        let pdfRenderer: any = null;
        let totalPageCount = 0;

        try {
            fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            );
            pdfRenderer = new PdfRenderer(fileDescriptor);
            totalPageCount = Number(pdfRenderer.getPageCount());

            if (!Number.isInteger(totalPageCount) || totalPageCount <= 0) {
                const error = "PDF 没有可处理的页面。";
                return {
                    success: false,
                    message: error,
                    data: buildData(rawPdfPath, imageDir, outputPath, 0, pages, visionModel, {
                        page_number: 0,
                        stage: "render",
                        error
                    })
                };
            }

            const resolvedEndPage = Math.min(endPageInput ?? totalPageCount, totalPageCount);
            if ((startPage ?? 1) > resolvedEndPage) {
                const error =
                    (startPage ?? 1) > totalPageCount
                        ? `start_page 超出了总页数，文档总页数为 ${totalPageCount}。`
                        : "start_page 不能大于 end_page。";
                return {
                    success: false,
                    message: error,
                    data: buildData(rawPdfPath, imageDir, outputPath, totalPageCount, pages, visionModel, {
                        page_number: 0,
                        stage: "precheck",
                        error
                    })
                };
            }

            outputDir = createOutputDirectory();
            imageDir = String(outputDir.getAbsolutePath());

            for (let pageNumber = startPage ?? 1; pageNumber <= resolvedEndPage; pageNumber += 1) {
                console.log(`[${TOOL_NAME}] 开始处理第 ${pageNumber} 页`);

                const zeroBasedPageIndex = pageNumber - 1;
                const imageFile = new File(
                    outputDir,
                    `page-${padPageNumber(pageNumber)}.${IMAGE_FORMAT}`
                );
                const imagePath = String(imageFile.getAbsolutePath());

                let page: any = null;
                let bitmap: any = null;
                let outputStream: any = null;
                let stage: FailedPageInfo["stage"] = "render";

                try {
                    page = pdfRenderer.openPage(zeroBasedPageIndex);
                    const width = Number(page.getWidth()) * RENDER_SCALE;
                    const height = Number(page.getHeight()) * RENDER_SCALE;

                    bitmap = Bitmap.createBitmap(width, height, BitmapConfig.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRendererPage.RENDER_MODE_FOR_DISPLAY
                    );

                    outputStream = new FileOutputStream(imageFile);
                    const compressed = bitmap.compress(
                        BitmapCompressFormat.PNG,
                        100,
                        outputStream
                    );
                    if (!compressed) {
                        throw new Error(`页面 ${pageNumber} 图片写入失败。`);
                    }

                    safeClose(outputStream, `page-${pageNumber} output stream`);
                    outputStream = null;

                    const imageLink = String(
                        NativeInterface.registerImageFromPath(imagePath) || ""
                    ).trim();
                    if (!imageLink) {
                        throw new Error(`页面 ${pageNumber} 图片链接注册失败。`);
                    }

                    stage = "vision";
                    const analysis = await analyzePageImage(
                        enhancedAiService,
                        imagePath,
                        resolvedPrompt
                    );
                    const pageOutputPath = writePageOutputFile(outputDir, pageNumber, analysis);

                    pages.push({
                        page_number: pageNumber,
                        image_path: imagePath,
                        image_link: imageLink,
                        output_path: pageOutputPath,
                        analysis
                    });

                    console.log(`[${TOOL_NAME}] 第 ${pageNumber} 页处理完成`);
                } catch (error) {
                    const message = String(error instanceof Error ? error.message : error);
                    outputPath = writeMergedOutputFile(outputDir, pages);

                    return {
                        success: false,
                        message: `第 ${pageNumber} 页处理失败: ${message}`,
                        data: buildData(rawPdfPath, imageDir, outputPath, totalPageCount, pages, visionModel, {
                            page_number: pageNumber,
                            stage,
                            error: message
                        })
                    };
                } finally {
                    safeClose(outputStream, `page-${pageNumber} output stream`);
                    safeRecycle(bitmap);
                    safeClose(page, `page-${pageNumber}`);
                }
            }

            outputPath = writeMergedOutputFile(outputDir, pages);
            return {
                success: true,
                message: `成功解析 ${pages.length} 页 PDF 并完成识图，结果已写入 ${outputPath}。`,
                data: buildData(rawPdfPath, imageDir, outputPath, totalPageCount, pages, visionModel)
            };
        } catch (error) {
            const message = String(error instanceof Error ? error.message : error);
            outputPath = writeMergedOutputFile(outputDir, pages);
            return {
                success: false,
                message,
                data: buildData(rawPdfPath, imageDir, outputPath, totalPageCount, pages, visionModel, {
                    page_number: 0,
                    stage: "render",
                    error: message
                })
            };
        } finally {
            safeClose(pdfRenderer, "pdfRenderer");
            safeClose(fileDescriptor, "parcelFileDescriptor");
        }
    }

    async function wrapToolExecution(
        func: (params: ParsePdfWithVisionParams) => Promise<ToolResponse>,
        params: ParsePdfWithVisionParams
    ): Promise<void> {
        try {
            const result = await func(params || ({} as ParsePdfWithVisionParams));
            complete(result);
        } catch (error) {
            const message = String(error instanceof Error ? error.message : error);
            complete({
                success: false,
                message,
                data: {
                    pdf_path: toTrimmedString(params?.pdf_path),
                    image_dir: "",
                    output_path: "",
                    total_page_count: 0,
                    processed_page_count: 0,
                    pages: [],
                    vision_model: {
                        function_type: TOOL_FUNCTION_TYPE,
                        config_id: "",
                        config_name: "",
                        selected_model: ""
                    },
                    failed_page: {
                        page_number: 0,
                        stage: "precheck",
                        error: message
                    }
                }
            });
        }
    }

    async function parse_pdf_with_vision(
        params: ParsePdfWithVisionParams
    ): Promise<void> {
        await wrapToolExecution(parsePdfWithVisionInternal, params);
    }

    async function main(params: ParsePdfWithVisionParams): Promise<void> {
        await wrapToolExecution(parsePdfWithVisionInternal, params);
    }

    return {
        parse_pdf_with_vision,
        main
    };
})();

exports.parse_pdf_with_vision = PdfVisionParser.parse_pdf_with_vision;
exports.main = PdfVisionParser.main;
