"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.applyLocalReplace = applyLocalReplace;
exports.applyLocalDelete = applyLocalDelete;
exports.overwriteLocalFile = overwriteLocalFile;
async function applyLocalReplace(params) {
    return Tools.Files.apply(params.path, 'replace', params.old, params.new, params.environment);
}
async function applyLocalDelete(params) {
    return Tools.Files.apply(params.path, 'delete', params.old, undefined, params.environment);
}
async function overwriteLocalFile(params) {
    const exists = await Tools.Files.exists(params.path, params.environment);
    if (exists.exists) {
        await Tools.Files.deleteFile(params.path, true, params.environment);
    }
    return Tools.Files.write(params.path, String(params.content ?? ''), false, params.environment);
}
