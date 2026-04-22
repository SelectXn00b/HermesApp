package com.xiaomo.hermes.hermes

import android.util.Log

/**
 * Ssh - Ported from ../hermes-agent/tools/environments/ssh.py
 *
 * SSH remote execution environment with ControlMaster connection persistence.
 * On Android, subprocess-based SSH is not available. This is a structural stub
 * that documents the interface. Real SSH execution would use JSch or similar.
 */
class SSHEnvironment(
    private val host: String,
    private val user: String,
    private val cwd: String = "~",
    private val timeout: Int = 60,
    private val port: Int = 22,
    private val keyPath: String = ""
) {
    companion object {
        private const val _TAG = "SSHEnvironment"
    }

    private var controlSocket: String = ""
    private var remoteHome: String = ""

    init {
        Log.d(_TAG, "SSHEnvironment initialized for $user@$host:$port")
    }

    /**
     * Build the SSH command with ControlMaster options.
     * On Android, SSH subprocess spawning is not directly available.
     */
    fun _buildSshCommand(extraArgs: List<String>? = null): List<String> {
        val cmd = mutableListOf("ssh")
        cmd.addAll(listOf("-o", "ControlPath=$controlSocket"))
        cmd.addAll(listOf("-o", "ControlMaster=auto"))
        cmd.addAll(listOf("-o", "ControlPersist=300"))
        cmd.addAll(listOf("-o", "BatchMode=yes"))
        cmd.addAll(listOf("-o", "StrictHostKeyChecking=accept-new"))
        cmd.addAll(listOf("-o", "ConnectTimeout=10"))
        if (port != 22) {
            cmd.addAll(listOf("-p", port.toString()))
        }
        if (keyPath.isNotEmpty()) {
            cmd.addAll(listOf("-i", keyPath))
        }
        if (extraArgs != null) {
            cmd.addAll(extraArgs)
        }
        cmd.add("$user@$host")
        return cmd
    }

    /**
     * Establish SSH connection using ControlMaster.
     * On Android, this is a stub - real implementation would use JSch.
     */
    fun _establishConnection() {
        // Android stub: SSH connections would use JSch or similar library
        Log.d(_TAG, "establishConnection: would connect to $user@$host:$port")
    }

    /**
     * Detect the remote user's home directory.
     */
    fun _detectRemoteHome(): String {
        // Default detection logic
        return if (user == "root") "/root" else "/home/$user"
    }

    /**
     * Create base ~/.hermes directory tree on remote in one SSH call.
     */
    fun _ensureRemoteDirs() {
        val base = "$remoteHome/.hermes"
        Log.d(_TAG, "ensureRemoteDirs: would create $base/{skills,credentials,cache}")
    }

    /**
     * Upload a single file via scp over ControlMaster.
     * On Android, file upload would use SFTP via JSch.
     */
    fun _scpUpload(hostPath: String, remotePath: String) {
        Log.d(_TAG, "scpUpload: $hostPath -> $remotePath (stub)")
    }

    /**
     * Upload many files in a single tar-over-SSH stream.
     * Pipes tar on the local side through SSH to tar on the remote,
     * transferring all files in one TCP stream.
     */
    fun _sshBulkUpload(files: List<Pair<String, String>>) {
        if (files.isEmpty()) return
        Log.d(_TAG, "sshBulkUpload: ${files.size} files (stub)")
    }

    /**
     * Download remote .hermes/ as a tar archive.
     */
    fun _sshBulkDownload(dest: String) {
        Log.d(_TAG, "sshBulkDownload: -> $dest (stub)")
    }

    /**
     * Batch-delete remote files in one SSH call.
     */
    fun _sshDelete(remotePaths: List<String>) {
        if (remotePaths.isEmpty()) return
        Log.d(_TAG, "sshDelete: ${remotePaths.size} files (stub)")
    }

    /**
     * Sync files to remote via FileSyncManager before command execution.
     */
    fun _beforeExecute() {
        // Would trigger FileSyncManager.sync()
        Log.d(_TAG, "beforeExecute: file sync (stub)")
    }

    /**
     * Spawn an SSH process that runs bash on the remote host.
     * On Android, subprocess spawning is limited. This is a stub.
     */
    fun _runBash(cmdString: String): Any? {
        // Android stub: would use JSch to execute remote commands
        Log.d(_TAG, "runBash: command execution (stub)")
        return null
    }

    /**
     * Clean up SSH connection and sync files back.
     */
    fun cleanup() {
        Log.d(_TAG, "cleanup: closing SSH connection to $user@$host")
    }
}

/** Python `_ensure_ssh_available` — stub. */
private fun _ensureSshAvailable(): Boolean = false
