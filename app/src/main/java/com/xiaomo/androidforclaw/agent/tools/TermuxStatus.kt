package com.xiaomo.androidforclaw.agent.tools

enum class TermuxSetupStep {
    TERMUX_NOT_INSTALLED,
    TERMUX_API_NOT_INSTALLED,
    KEYPAIR_MISSING,
    RUN_COMMAND_PERMISSION_DENIED,
    RUN_COMMAND_SERVICE_MISSING,
    AUTO_SETUP_DISPATCH_FAILED,
    SSHD_NOT_REACHABLE,
    SSH_CONFIG_MISSING,
    SSH_AUTH_FAILED,
    READY,
    UNKNOWN
}

data class TermuxStatus(
    val termuxInstalled: Boolean,
    val termuxApiInstalled: Boolean,
    val runCommandPermissionDeclared: Boolean,
    val runCommandServiceAvailable: Boolean,
    val sshReachable: Boolean,
    val sshConfigPresent: Boolean,
    val keypairPresent: Boolean,
    val lastStep: TermuxSetupStep,
    val message: String
) {
    val ready: Boolean
        get() = termuxInstalled && sshReachable && sshConfigPresent && lastStep == TermuxSetupStep.READY
}
