"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.wrap = wrap;
async function wrap(func, params, successMessage, failMessage) {
    try {
        const data = await func(params);
        const result = { success: true, message: successMessage, data };
        complete(result);
    }
    catch (error) {
        const result = {
            success: false,
            message: `${failMessage}: ${String(error && error.message ? error.message : error)}`,
            error_stack: String(error && error.stack ? error.stack : '')
        };
        complete(result);
    }
}
