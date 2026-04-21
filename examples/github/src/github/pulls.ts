import { buildUrl, requestJson, getToken } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

export type ListPullRequestsParams = RepoIdParams & {
    state?: 'open' | 'closed' | 'all' | string;
    head?: string;
    base?: string;
    page?: number;
    per_page?: number;
};

export type CreatePullRequestParams = RepoIdParams & {
    title: string;
    head: string;
    base: string;
    body?: string;
    draft?: boolean;
};

export type GetPullRequestParams = RepoIdParams & {
    pull_number: number;
};

export type MergePullRequestParams = RepoIdParams & {
    pull_number: number;
    commit_title?: string;
    commit_message?: string;
    merge_method?: 'merge' | 'squash' | 'rebase' | string;
};

export async function listPullRequests(params: ListPullRequestsParams): Promise<any[]> {
    const page = params.page ?? 1;
    const perPage = params.per_page ?? 30;

    const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls`, {
        state: params.state ?? 'open',
        head: params.head,
        base: params.base,
        page,
        per_page: perPage
    });

    return requestJson<any[]>({ method: 'GET', url });
}

export async function createPullRequest(params: CreatePullRequestParams): Promise<any> {
    const token = getToken();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for create_pull_request.');
    }

    const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls`);
    return requestJson<any>({
        method: 'POST',
        url,
        body: {
            title: params.title,
            head: params.head,
            base: params.base,
            body: params.body,
            draft: params.draft
        }
    });
}

export async function getPullRequest(params: GetPullRequestParams): Promise<any> {
    const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls/${params.pull_number}`);
    return requestJson<any>({ method: 'GET', url });
}

export async function mergePullRequest(params: MergePullRequestParams): Promise<any> {
    const token = getToken();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for merge_pull_request.');
    }

    const url = buildUrl(
        `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls/${params.pull_number}/merge`
    );

    return requestJson<any>({
        method: 'PUT',
        url,
        body: {
            commit_title: params.commit_title,
            commit_message: params.commit_message,
            merge_method: params.merge_method
        }
    });
}
