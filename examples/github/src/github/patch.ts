import { getFileContent, createOrUpdateFile } from './contents';

export type PatchFileInRepoParams = {
    owner: string;
    repo: string;
    path: string;

    message: string;
    patch: string;

    branch?: string;
};

type EditAction = 'REPLACE' | 'DELETE';

type EditOperation = {
    action: EditAction;
    oldContent: string;
    newContent: string;
};

function normalizeNewlines(text: string): string {
    return String(text ?? '').replace(/\r\n/g, '\n');
}

function parseBlocks(patch: string): EditOperation[] {
    const src = normalizeNewlines(patch);
    const ops: EditOperation[] = [];

    const blockRegex = /\[START-(REPLACE|DELETE)\]\s*\n([\s\S]*?)\[END-\1\]/g;
    let match: RegExpExecArray | null;
    while ((match = blockRegex.exec(src)) !== null) {
        const action = match[1] as EditAction;
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

function applyOps(original: string, ops: EditOperation[]): string {
    let out = normalizeNewlines(original);

    for (const op of ops) {
        if (!out.includes(op.oldContent)) {
            throw new Error('OLD block content not found in target file.');
        }

        if (op.action === 'DELETE') {
            out = out.replace(op.oldContent, '');
        } else {
            out = out.replace(op.oldContent, op.newContent);
        }
    }

    return out;
}

export async function patchFileInRepo(params: PatchFileInRepoParams): Promise<any> {
    const file = await getFileContent({
        owner: params.owner,
        repo: params.repo,
        path: params.path,
        ref: params.branch
    });

    const originalText: string = typeof file?.decoded_text === 'string' ? file.decoded_text : '';
    const sha: string | undefined = typeof file?.sha === 'string' ? file.sha : undefined;

    if (!sha) {
        throw new Error('Cannot patch file: sha missing (is it a file path?)');
    }

    const ops = parseBlocks(params.patch);
    const patched = applyOps(originalText, ops);

    return createOrUpdateFile({
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
