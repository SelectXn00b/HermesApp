import { wrap } from './utils/wrap';
import { searchRepositories, getRepository } from './github/repos';
import { listIssues, createIssue, commentIssue, listIssueComments } from './github/issues';
import { listPullRequests, createPullRequest, getPullRequest, mergePullRequest } from './github/pulls';
import { getFileContent, createOrUpdateFile, deleteFile } from './github/contents';
import { createBranch } from './github/branches';
import { patchFileInRepo } from './github/patch';
import { applyLocalReplace, applyLocalDelete, overwriteLocalFile } from './local/fileApply';
import { terminalExec } from './local/terminal';

export const toolImpl = {
    search_repositories: (p: any) => wrap(searchRepositories, p, '搜索仓库成功', '搜索仓库失败'),
    get_repository: (p: any) => wrap(getRepository, p, '获取仓库信息成功', '获取仓库信息失败'),

    list_issues: (p: any) => wrap(listIssues, p, '获取 Issues 成功', '获取 Issues 失败'),
    create_issue: (p: any) => wrap(createIssue, p, '创建 Issue 成功', '创建 Issue 失败'),
    comment_issue: (p: any) => wrap(commentIssue, p, '发表评论成功', '发表评论失败'),
    list_issue_comments: (p: any) => wrap(listIssueComments, p, '获取 Issue 评论成功', '获取 Issue 评论失败'),

    list_pull_requests: (p: any) => wrap(listPullRequests, p, '获取 PR 列表成功', '获取 PR 列表失败'),
    create_pull_request: (p: any) => wrap(createPullRequest, p, '创建 PR 成功', '创建 PR 失败'),
    get_pull_request: (p: any) => wrap(getPullRequest, p, '获取 PR 成功', '获取 PR 失败'),
    merge_pull_request: (p: any) => wrap(mergePullRequest, p, '合并 PR 成功', '合并 PR 失败'),

    get_file_content: (p: any) => wrap(getFileContent, p, '读取文件成功', '读取文件失败'),
    create_or_update_file: (p: any) => wrap(createOrUpdateFile, p, '提交文件成功', '提交文件失败'),
    delete_file: (p: any) => wrap(deleteFile, p, '删除文件成功', '删除文件失败'),
    create_branch: (p: any) => wrap(createBranch, p, '创建分支成功', '创建分支失败'),
    patch_file_in_repo: (p: any) => wrap(patchFileInRepo, p, '仓库文件差异更新成功', '仓库文件差异更新失败'),

    apply_local_replace: (p: any) => wrap(applyLocalReplace as any, p, '本地差异更新成功', '本地差异更新失败'),
    apply_local_delete: (p: any) => wrap(applyLocalDelete as any, p, '本地删除片段成功', '本地删除片段失败'),
    overwrite_local_file: (p: any) => wrap(overwriteLocalFile as any, p, '本地覆盖写入成功', '本地覆盖写入失败'),

    terminal_exec: (p: any) => wrap(terminalExec as any, p, '终端执行成功', '终端执行失败')
};
