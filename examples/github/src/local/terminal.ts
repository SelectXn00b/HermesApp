let terminalSessionId: string | null = null;

export async function getTerminalSession(sessionName?: string): Promise<string> {
    if (terminalSessionId) return terminalSessionId;
    const session = await Tools.System.terminal.create(sessionName || 'github_tools_session');
    terminalSessionId = session.sessionId;
    return terminalSessionId;
}

export async function terminalExec(params: { command: string; session_name?: string; close?: boolean }): Promise<any> {
    const sessionId = await getTerminalSession(params.session_name);
    const result = await Tools.System.terminal.exec(sessionId, params.command);
    if (params.close) {
        await Tools.System.terminal.close(sessionId);
        terminalSessionId = null;
    }
    return result;
}
