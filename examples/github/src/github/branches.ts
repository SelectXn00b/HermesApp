import { buildUrl, requestJson, getToken } from './api';
import { getRepository } from './repos';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

export type CreateBranchParams = RepoIdParams & {
    new_branch: string;
    from_branch?: string;
};

async function getBranchHeadSha(params: RepoIdParams & { branch: string }): Promise<string> {
    const url = buildUrl(
        `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/git/ref/heads/${encodeURIComponent(
            params.branch
        )}`
    );

    const data = await requestJson<any>({ method: 'GET', url });
    const sha = data?.object?.sha;
    if (!sha) {
        throw new Error(`Cannot resolve branch sha for ${params.branch}`);
    }
    return sha;
}

export async function createBranch(params: CreateBranchParams): Promise<any> {
    const token = getToken();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for create_branch.');
    }

    const fromBranch = params.from_branch ?? String((await getRepository({ owner: params.owner, repo: params.repo }))?.default_branch || 'main');

    const sha = await getBranchHeadSha({ owner: params.owner, repo: params.repo, branch: fromBranch });

    const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/git/refs`);
    return requestJson<any>({
        method: 'POST',
        url,
        body: {
            ref: `refs/heads/${params.new_branch}`,
            sha
        }
    });
}
