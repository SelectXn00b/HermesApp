"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.toolImpl = void 0;
const wrap_1 = require("./utils/wrap");
const repos_1 = require("./github/repos");
const issues_1 = require("./github/issues");
const pulls_1 = require("./github/pulls");
const contents_1 = require("./github/contents");
const branches_1 = require("./github/branches");
const patch_1 = require("./github/patch");
const fileApply_1 = require("./local/fileApply");
const terminal_1 = require("./local/terminal");
exports.toolImpl = {
    search_repositories: (p) => (0, wrap_1.wrap)(repos_1.searchRepositories, p, '搜索仓库成功', '搜索仓库失败'),
    get_repository: (p) => (0, wrap_1.wrap)(repos_1.getRepository, p, '获取仓库信息成功', '获取仓库信息失败'),
    list_issues: (p) => (0, wrap_1.wrap)(issues_1.listIssues, p, '获取 Issues 成功', '获取 Issues 失败'),
    create_issue: (p) => (0, wrap_1.wrap)(issues_1.createIssue, p, '创建 Issue 成功', '创建 Issue 失败'),
    comment_issue: (p) => (0, wrap_1.wrap)(issues_1.commentIssue, p, '发表评论成功', '发表评论失败'),
    list_issue_comments: (p) => (0, wrap_1.wrap)(issues_1.listIssueComments, p, '获取 Issue 评论成功', '获取 Issue 评论失败'),
    list_pull_requests: (p) => (0, wrap_1.wrap)(pulls_1.listPullRequests, p, '获取 PR 列表成功', '获取 PR 列表失败'),
    create_pull_request: (p) => (0, wrap_1.wrap)(pulls_1.createPullRequest, p, '创建 PR 成功', '创建 PR 失败'),
    get_pull_request: (p) => (0, wrap_1.wrap)(pulls_1.getPullRequest, p, '获取 PR 成功', '获取 PR 失败'),
    merge_pull_request: (p) => (0, wrap_1.wrap)(pulls_1.mergePullRequest, p, '合并 PR 成功', '合并 PR 失败'),
    get_file_content: (p) => (0, wrap_1.wrap)(contents_1.getFileContent, p, '读取文件成功', '读取文件失败'),
    create_or_update_file: (p) => (0, wrap_1.wrap)(contents_1.createOrUpdateFile, p, '提交文件成功', '提交文件失败'),
    delete_file: (p) => (0, wrap_1.wrap)(contents_1.deleteFile, p, '删除文件成功', '删除文件失败'),
    create_branch: (p) => (0, wrap_1.wrap)(branches_1.createBranch, p, '创建分支成功', '创建分支失败'),
    patch_file_in_repo: (p) => (0, wrap_1.wrap)(patch_1.patchFileInRepo, p, '仓库文件差异更新成功', '仓库文件差异更新失败'),
    apply_local_replace: (p) => (0, wrap_1.wrap)(fileApply_1.applyLocalReplace, p, '本地差异更新成功', '本地差异更新失败'),
    apply_local_delete: (p) => (0, wrap_1.wrap)(fileApply_1.applyLocalDelete, p, '本地删除片段成功', '本地删除片段失败'),
    overwrite_local_file: (p) => (0, wrap_1.wrap)(fileApply_1.overwriteLocalFile, p, '本地覆盖写入成功', '本地覆盖写入失败'),
    terminal_exec: (p) => (0, wrap_1.wrap)(terminal_1.terminalExec, p, '终端执行成功', '终端执行失败')
};
