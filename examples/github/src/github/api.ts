export type GithubApiOptions = {
    baseUrl?: string;
    token?: string;
};

export function getBaseUrl(): string {
    const fromEnv = (typeof getEnv === 'function' ? getEnv('GITHUB_API_BASE_URL') : undefined) || '';
    return (fromEnv && String(fromEnv).trim()) || 'https://api.github.com';
}

export function getToken(): string | undefined {
    const token = (typeof getEnv === 'function' ? getEnv('GITHUB_TOKEN') : undefined) || '';
    const trimmed = String(token || '').trim();
    return trimmed ? trimmed : undefined;
}

export function buildUrl(pathname: string, query?: Record<string, string | number | boolean | undefined>): string {
    const base = getBaseUrl().replace(/\/+$/, '');
    const path = pathname.startsWith('/') ? pathname : `/${pathname}`;

    const qs: string[] = [];
    if (query) {
        Object.keys(query).forEach((k) => {
            const v = query[k];
            if (v === undefined || v === null) return;
            qs.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
        });
    }

    return qs.length > 0 ? `${base}${path}?${qs.join('&')}` : `${base}${path}`;
}

export function defaultHeaders(extra?: Record<string, string>): Record<string, string> {
    const headers: Record<string, string> = {
        Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        'User-Agent': 'Operit-Examples-GitHub'
    };

    const token = getToken();
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    if (extra) {
        Object.keys(extra).forEach((k) => {
            headers[k] = extra[k];
        });
    }

    return headers;
}

export function createHttpClient(timeoutMs: number = 30000): OkHttpClient {
    return OkHttp.newBuilder().connectTimeout(timeoutMs).readTimeout(timeoutMs).writeTimeout(timeoutMs).build();
}

export async function requestJson<T>(options: {
    method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
    url: string;
    headers?: Record<string, string>;
    body?: any;
    timeoutMs?: number;
}): Promise<T> {
    const client = createHttpClient(options.timeoutMs ?? 30000);
    const req = client.newRequest().url(options.url).method(options.method);

    const headers = defaultHeaders(options.headers);
    req.headers(headers);

    if (options.body !== undefined && options.body !== null && options.method !== 'GET') {
        req.body(JSON.stringify(options.body), 'json');
    }

    const resp: OkHttpResponse = await req.build().execute();

    if (!resp.isSuccessful()) {
        throw new Error(`GitHub API Error: ${resp.statusCode} ${resp.statusMessage}\n${resp.content}`);
    }

    return resp.json() as T;
}
