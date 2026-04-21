"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.patchFileInRepo = patchFileInRepo;
const contents_1 = require("./contents");
function normalizeNewlines(text) {
    return String(text ?? '').replace(/\r\n/g, '\n');
}
function parseBlocks(patch) {
    const src = normalizeNewlines(patch);
    const ops = [];
    const blockRegex = /\[START-(REPLACE|DELETE)\]\s*\n([\s\S]*?)\[END-\1\]/g;
    let match;
    while ((match = blockRegex.exec(src)) !== null) {
        const action = match[1];
        const body = match[2] || '';
        const oldMatch = /\[OLD\]\s*\n([\s\S]*?)\n\[\/OLD\]/.exec(body);
        if (!oldMatch) {
            throw new Error(`Patch block missing [OLD] section for ${action}`);
        }
        const oldContent = oldMatch[1];
        let newContent = '';
        if (action === 'REPLACE') {
            const newMatch = /\[NEW\]\s*\n([\s\S]*?)\n\[\/NEW\]/.exec(body);
            if (!newMatch) {
                throw new Error('REPLACE block missing [NEW] section');
            }
            newContent = newMatch[1];
        }
        ops.push({ action, oldContent, newContent });
    }
    if (ops.length === 0) {
        throw new Error('No patch blocks found. Use [START-REPLACE]/[START-DELETE] blocks.');
    }
    return ops;
}
function applyOps(original, ops) {
    let out = normalizeNewlines(original);
    for (const op of ops) {
        if (!out.includes(op.oldContent)) {
            throw new Error('OLD block content not found in target file.');
        }
        if (op.action === 'DELETE') {
            out = out.replace(op.oldContent, '');
        }
        else {
            out = out.replace(op.oldContent, op.newContent);
        }
    }
    return out;
}
async function patchFileInRepo(params) {
    const file = await (0, contents_1.getFileContent)({
        owner: params.owner,
        repo: params.repo,
        path: params.path,
        ref: params.branch
    });
    const originalText = typeof file?.decoded_text === 'string' ? file.decoded_text : '';
    const sha = typeof file?.sha === 'string' ? file.sha : undefined;
    if (!sha) {
        throw new Error('Cannot patch file: sha missing (is it a file path?)');
    }
    const ops = parseBlocks(params.patch);
    const patched = applyOps(originalText, ops);
    return (0, contents_1.createOrUpdateFile)({
        owner: params.owner,
        repo: params.repo,
        path: params.path,
        message: params.message,
        content: patched,
        content_encoding: 'utf-8',
        branch: params.branch,
        sha
    });
}
