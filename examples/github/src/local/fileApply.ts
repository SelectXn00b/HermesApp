export type FileEnvironment = 'android' | 'linux';

export async function applyLocalReplace(params: { path: string; old: string; new: string; environment?: FileEnvironment }): Promise<any> {
    return Tools.Files.apply(params.path, 'replace', params.old, params.new, params.environment);
}

export async function applyLocalDelete(params: { path: string; old: string; environment?: FileEnvironment }): Promise<any> {
    return Tools.Files.apply(params.path, 'delete', params.old, undefined, params.environment);
}

export async function overwriteLocalFile(params: { path: string; content: string; environment?: FileEnvironment }): Promise<any> {
    const exists = await Tools.Files.exists(params.path, params.environment);
    if (exists.exists) {
        await Tools.Files.deleteFile(params.path, true, params.environment);
    }
    return Tools.Files.write(params.path, String(params.content ?? ''), false, params.environment);
}
