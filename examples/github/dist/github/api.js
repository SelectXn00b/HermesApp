"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getBaseUrl = getBaseUrl;
exports.getToken = getToken;
exports.buildUrl = buildUrl;
exports.defaultHeaders = defaultHeaders;
exports.createHttpClient = createHttpClient;
exports.requestJson = requestJson;
function getBaseUrl() {
    const fromEnv = (typeof getEnv === 'function' ? getEnv('GITHUB_API_BASE_URL') : undefined) || '';
    return (fromEnv && String(fromEnv).trim()) || 'https://api.github.com';
}
function getToken() {
    const token = (typeof getEnv === 'function' ? getEnv('GITHUB_TOKEN') : undefined) || '';
    const trimmed = String(token || '').trim();
    return trimmed ? trimmed : undefined;
}
function buildUrl(pathname, query) {
    const base = getBaseUrl().replace(/\/+$/, '');
    const path = pathname.startsWith('/') ? pathname : `/${pathname}`;
    const qs = [];
    if (query) {
        Object.keys(query).forEach((k) => {
            const v = query[k];
            if (v === undefined || v === null)
                return;
            qs.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
        });
    }
    return qs.length > 0 ? `${base}${path}?${qs.join('&')}` : `${base}${path}`;
}
function defaultHeaders(extra) {
    const headers = {
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
function createHttpClient(timeoutMs = 30000) {
    return OkHttp.newBuilder().connectTimeout(timeoutMs).readTimeout(timeoutMs).writeTimeout(timeoutMs).build();
}
async function requestJson(options) {
    const client = createHttpClient(options.timeoutMs ?? 30000);
    const req = client.newRequest().url(options.url).method(options.method);
    const headers = defaultHeaders(options.headers);
    req.headers(headers);
    if (options.body !== undefined && options.body !== null && options.method !== 'GET') {
        req.body(JSON.stringify(options.body), 'json');
    }
    const resp = await req.build().execute();
    if (!resp.isSuccessful()) {
        throw new Error(`GitHub API Error: ${resp.statusCode} ${resp.statusMessage}\n${resp.content}`);
    }
    return resp.json();
}
