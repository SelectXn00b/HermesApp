"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getTerminalSession = getTerminalSession;
exports.terminalExec = terminalExec;
let terminalSessionId = null;
async function getTerminalSession(sessionName) {
    if (terminalSessionId)
        return terminalSessionId;
    const session = await Tools.System.terminal.create(sessionName || 'github_tools_session');
    terminalSessionId = session.sessionId;
    return terminalSessionId;
}
async function terminalExec(params) {
    const sessionId = await getTerminalSession(params.session_name);
    const result = await Tools.System.terminal.exec(sessionId, params.command);
    if (params.close) {
        await Tools.System.terminal.close(sessionId);
        terminalSessionId = null;
    }
    return result;
}
