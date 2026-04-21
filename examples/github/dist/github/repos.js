"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.searchRepositories = searchRepositories;
exports.getRepository = getRepository;
const api_1 = require("./api");
async function searchRepositories(params) {
    const page = params.page ?? 1;
    const perPage = params.per_page ?? 30;
    const url = (0, api_1.buildUrl)('/search/repositories', {
        q: params.query,
        sort: params.sort,
        order: params.order,
        page,
        per_page: perPage
    });
    return (0, api_1.requestJson)({ method: 'GET', url });
}
async function getRepository(params) {
    const url = (0, api_1.buildUrl)(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}`);
    return (0, api_1.requestJson)({ method: 'GET', url });
}
