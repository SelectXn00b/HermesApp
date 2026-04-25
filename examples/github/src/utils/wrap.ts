export type ToolResponse<T> = {
    success: boolean;
    message: string;
    data?: T;
    error_stack?: string;
};

export async function wrap<T>(
    func: (params: any) => Promise<T>,
    params: any,
    successMessage: string,
    failMessage: string
): Promise<void> {
    try {
        const data = await func(params);
        const result: ToolResponse<T> = { success: true, message: successMessage, data };
        complete(result);
    } catch (error: any) {
        const result: ToolResponse<T> = {
            success: false,
            message: `${failMessage}: ${String(error && error.message ? error.message : error)}`,
            error_stack: String(error && error.stack ? error.stack : '')
        };
        complete(result);
    }
}
