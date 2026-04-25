"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getFileContent = getFileContent;
exports.createOrUpdateFile = createOrUpdateFile;
exports.deleteFile = deleteFile;
const api_1 = require("./api");
const base64_1 = require("../utils/base64");
async function getFileContent(params) {
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path
        .split('/')
        .map((p) => encodeURIComponent(p))
        .join('/')}`, { ref: params.ref });
    const data = await (0, api_1.requestJson)({ method: 'GET', url });
    if (data && data.type === 'file' && typeof data.content === 'string' && data.encoding === 'base64') {
        const decoded = (0, base64_1.safeAtobBase64)(data.content);
        return {
            ...data,
            decoded_text: decoded
        };
    }
    return data;
}
async function resolveFileSha(params) {
    try {
        const existing = await getFileContent({
            owner: params.owner,
            repo: params.repo,
            path: params.path,
            ref: params.branch
        });
        const sha = existing && typeof existing.sha === 'string' ? existing.sha : undefined;
        return sha;
    }
    catch (e) {
        return undefined;
    }
}
async function createOrUpdateFile(params) {
    const token = (0, api_1.getToken)();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for create_or_update_file.');
    }
    const encoding = (params.content_encoding || 'utf-8').toLowerCase();
    const base64Content = encoding === 'base64' ? params.content : (0, base64_1.safeBtoaBase64)(params.content);
    const sha = params.sha ?? (await resolveFileSha({ owner: params.owner, repo: params.repo, path: params.path, branch: params.branch }));
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path
        .split('/')
        .map((p) => encodeURIComponent(p))
        .join('/')}`);
    return (0, api_1.requestJson)({
        method: 'PUT',
        url,
        body: {
            message: params.message,
            content: base64Content,
            branch: params.branch,
            sha
        }
    });
}
async function deleteFile(params) {
    const token = (0, api_1.getToken)();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for delete_file.');
    }
    const sha = params.sha ?? (await resolveFileSha({ owner: params.owner, repo: params.repo, path: params.path, branch: params.branch }));
    if (!sha) {
        throw new Error('File sha is required (unable to resolve automatically).');
    }
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path
        .split('/')
        .map((p) => encodeURIComponent(p))
        .join('/')}`);
    return (0, api_1.requestJson)({
        method: 'DELETE',
        url,
        body: {
            message: params.message,
            branch: params.branch,
            sha
        }
    });
}
