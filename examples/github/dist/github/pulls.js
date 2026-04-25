"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.listPullRequests = listPullRequests;
exports.createPullRequest = createPullRequest;
exports.getPullRequest = getPullRequest;
exports.mergePullRequest = mergePullRequest;
const api_1 = require("./api");
async function listPullRequests(params) {
    const page = params.page ?? 1;
    const perPage = params.per_page ?? 30;
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls`, {
        state: params.state ?? 'open',
        head: params.head,
        base: params.base,
        page,
        per_page: perPage
    });
    return (0, api_1.requestJson)({ method: 'GET', url });
}
async function createPullRequest(params) {
    const token = (0, api_1.getToken)();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for create_pull_request.');
    }
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls`);
    return (0, api_1.requestJson)({
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
async function getPullRequest(params) {
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls/${params.pull_number}`);
    return (0, api_1.requestJson)({ method: 'GET', url });
}
async function mergePullRequest(params) {
    const token = (0, api_1.getToken)();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for merge_pull_request.');
    }
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls/${params.pull_number}/merge`);
    return (0, api_1.requestJson)({
        method: 'PUT',
        url,
        body: {
            commit_title: params.commit_title,
            commit_message: params.commit_message,
            merge_method: params.merge_method
        }
    });
}
