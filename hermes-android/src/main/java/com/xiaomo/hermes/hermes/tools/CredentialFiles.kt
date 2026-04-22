package com.xiaomo.hermes.hermes.tools

import java.io.File

/**
 * Credential file path helpers.
 * Ported from credential_files.py
 */
object CredentialFiles {

    data class CredentialPaths(
        val hermesDir: File,
        val authFile: File,
        val configFile: File,
        val secretsDir: File)

    fun getHermesDir(): File {
        val home = System.getProperty("user.home") ?: "."
        return File(home, ".hermes").absoluteFile
    }

    fun getAuthFile(): File = File(getHermesDir(), "auth.json")
    fun getConfigFile(): File = File(getHermesDir(), "config.yaml")
    fun getSecretsDir(): File = File(getHermesDir(), "secrets")

    fun getAllPaths(): CredentialPaths = CredentialPaths(
        hermesDir = getHermesDir(),
        authFile = getAuthFile(),
        configFile = getConfigFile(),
        secretsDir = getSecretsDir())

    fun ensureDirs() {
        getHermesDir().mkdirs()
        getSecretsDir().mkdirs()
    }


}
