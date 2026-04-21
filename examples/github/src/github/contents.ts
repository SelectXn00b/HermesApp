import { buildUrl, requestJson, getToken } from './api';
import { safeAtobBase64, safeBtoaBase64 } from '../utils/base64';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

export type GetFileContentParams = RepoIdParams & {
    path: string;
    ref?: string;
};

export type CreateOrUpdateFileParams = RepoIdParams & {
    path: string;
    message: string;
    content: string;
    content_encoding?: 'utf-8' | 'base64' | string;
    branch?: string;
    sha?: string;
};

export type DeleteFileParams = RepoIdParams & {
    path: string;
    message: string;
    branch?: string;
    sha?: string;
};

export async function getFileContent(params: GetFileContentParams): Promise<any> {
    const url = buildUrl(
        `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path
            .split('/')
            .map((p) => encodeURIComponent(p))
            .join('/')}`,
        { ref: params.ref }
    );

    const data = await requestJson<any>({ method: 'GET', url });

    if (data && data.type === 'file' && typeof data.content === 'string' && data.encoding === 'base64') {
        const decoded = safeAtobBase64(data.content);
        return {
            ...data,
            decoded_text: decoded
        };
    }

    return data;
}

async function resolveFileSha(params: RepoIdParams & { path: string; branch?: string }): Promise<string | undefined> {
    try {
        const existing = await getFileContent({
            owner: params.owner,
            repo: params.repo,
            path: params.path,
            ref: params.branch
        });

        const sha = existing && typeof existing.sha === 'string' ? existing.sha : undefined;
        return sha;
    } catch (e) {
        return undefined;
    }
}

export async function createOrUpdateFile(params: CreateOrUpdateFileParams): Promise<any> {
    const token = getToken();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for create_or_update_file.');
    }

    const encoding = (params.content_encoding || 'utf-8').toLowerCase();
    const base64Content = encoding === 'base64' ? params.content : safeBtoaBase64(params.content);

    const sha = params.sha ?? (await resolveFileSha({ owner: params.owner, repo: params.repo, path: params.path, branch: params.branch }));

    const url = buildUrl(
        `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path
            .split('/')
            .map((p) => encodeURIComponent(p))
            .join('/')}`
    );

    return requestJson<any>({
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

export async function deleteFile(params: DeleteFileParams): Promise<any> {
    const token = getToken();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for delete_file.');
    }

    const sha = params.sha ?? (await resolveFileSha({ owner: params.owner, repo: params.repo, path: params.path, branch: params.branch }));
    if (!sha) {
        throw new Error('File sha is required (unable to resolve automatically).');
    }

    const url = buildUrl(
        `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path
            .split('/')
            .map((p) => encodeURIComponent(p))
            .join('/')}`
    );

    return requestJson<any>({
        method: 'DELETE',
        url,
        body: {
            message: params.message,
            branch: params.branch,
            sha
        }
    });
}
