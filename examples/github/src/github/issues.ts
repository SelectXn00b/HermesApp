import { buildUrl, requestJson, getToken } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

export type ListIssuesParams = RepoIdParams & {
    state?: 'open' | 'closed' | 'all' | string;
    labels?: string;
    creator?: string;
    page?: number;
    per_page?: number;
    include_pull_requests?: boolean;
};

export type CreateIssueParams = RepoIdParams & {
    title: string;
    body?: string;
    labels?: string[];
    assignees?: string[];
};

export type CommentIssueParams = RepoIdParams & {
    issue_number: number;
    body: string;
};

export type ListIssueCommentsParams = RepoIdParams & {
    issue_number: number;
    page?: number;
    per_page?: number;
};

export async function listIssues(params: ListIssuesParams): Promise<any[]> {
    const page = params.page ?? 1;
    const perPage = params.per_page ?? 30;

    const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues`, {
        state: params.state ?? 'open',
        labels: params.labels,
        creator: params.creator,
        page,
        per_page: perPage
    });

    const items = await requestJson<any[]>({ method: 'GET', url });

    const includePRs = params.include_pull_requests === true;
    if (includePRs) return items;
    return items.filter((it) => !it || !it.pull_request);
}

export async function createIssue(params: CreateIssueParams): Promise<any> {
    const token = getToken();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for create_issue.');
    }

    const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues`);
    return requestJson<any>({
        method: 'POST',
        url,
        body: {
            title: params.title,
            body: params.body,
            labels: params.labels,
            assignees: params.assignees
        }
    });
}

export async function commentIssue(params: CommentIssueParams): Promise<any> {
    const token = getToken();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for comment_issue.');
    }

    const url = buildUrl(
        `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues/${params.issue_number}/comments`
    );

    return requestJson<any>({ method: 'POST', url, body: { body: params.body } });
}

export async function listIssueComments(params: ListIssueCommentsParams): Promise<any[]> {
    const page = params.page ?? 1;
    const perPage = params.per_page ?? 30;

    const url = buildUrl(
        `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues/${params.issue_number}/comments`,
        { page, per_page: perPage }
    );

    return requestJson<any[]>({ method: 'GET', url });
}
