"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createBranch = createBranch;
const api_1 = require("./api");
const repos_1 = require("./repos");
async function getBranchHeadSha(params) {
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/git/ref/heads/${encodeURIComponent(params.branch)}`);
    const data = await (0, api_1.requestJson)({ method: 'GET', url });
    const sha = data?.object?.sha;
    if (!sha) {
        throw new Error(`Cannot resolve branch sha for ${params.branch}`);
    }
    return sha;
}
async function createBranch(params) {
    const token = (0, api_1.getToken)();
    if (!token) {
        throw new Error('GITHUB_TOKEN is required for create_branch.');
    }
    const fromBranch = params.from_branch ?? String((await (0, repos_1.getRepository)({ owner: params.owner, repo: params.repo }))?.default_branch || 'main');
    const sha = await getBranchHeadSha({ owner: params.owner, repo: params.repo, branch: fromBranch });
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/git/refs`);
    return (0, api_1.requestJson)({
        method: 'POST',
        url,
        body: {
            ref: `refs/heads/${params.new_branch}`,
            sha
        }
    });
}
