"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.listIssues = listIssues;
exports.createIssue = createIssue;
exports.commentIssue = commentIssue;
exports.listIssueComments = listIssueComments;
const api_1 = require("./api");
async function listIssues(params) {
    const page = params.page ?? 1;
    const perPage = params.per_page ?? 30;
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues`, {
        state: params.state ?? 'open',
        labels: params.labels,
        creator: params.creator,
        page,
        per_page: perPage
    });
    const items = await (0, api_1.requestJson)({ method: 'GET', url });
    const includePRs = params.include_pull_requests === true;
    if (includePRs)
        return items;
    return items.filter((it) => !it || !it.pull_request);
}
async function createIssue(params) {
    const token = (0, api_1.getToken)();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for create_issue.');
    }
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues`);
    return (0, api_1.requestJson)({
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
async function commentIssue(params) {
    const token = (0, api_1.getToken)();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for comment_issue.');
    }
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues/${params.issue_number}/comments`);
    return (0, api_1.requestJson)({ method: 'POST', url, body: { body: params.body } });
}
async function listIssueComments(params) {
    const page = params.page ?? 1;
    const perPage = params.per_page ?? 30;
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues/${params.issue_number}/comments`, { page, per_page: perPage });
    return (0, api_1.requestJson)({ method: 'GET', url });
}
