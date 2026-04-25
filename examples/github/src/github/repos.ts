import { buildUrl, requestJson } from './api';

export type SearchRepositoriesParams = {
    query: string;
    sort?: string;
    order?: string;
    page?: number;
    per_page?: number;
};

export type RepoIdParams = {
    owner: string;
    repo: string;
};

export async function searchRepositories(params: SearchRepositoriesParams): Promise<any> {
    const page = params.page ?? 1;
    const perPage = params.per_page ?? 30;

    const url = buildUrl('/search/repositories', {
        q: params.query,
        sort: params.sort,
        order: params.order,
        page,
        per_page: perPage
    });

    return requestJson<any>({ method: 'GET', url });
}

export async function getRepository(params: RepoIdParams): Promise<any> {
    const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}`);
    return requestJson<any>({ method: 'GET', url });
}
