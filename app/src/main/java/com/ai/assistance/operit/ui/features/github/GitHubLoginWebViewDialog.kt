package com.ai.assistance.operit.ui.features.github

import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.token.webview.WebViewConfig
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

private const val TAG = "GitHubLoginWebView"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubLoginWebViewDialog(
    onDismissRequest: () -> Unit,
    onLoginSuccess: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val githubApiService = remember { GitHubApiService(context) }
    val expectedState = rememberSaveable { GitHubAuthPreferences.createOAuthState() }
    val authorizationUrl = remember(expectedState) { githubAuth.getAuthorizationUrl(state = expectedState) }
    val webView = remember { WebViewConfig.createWebView(context) }

    var isLoading by remember { mutableStateOf(true) }
    var isCompletingLogin by remember { mutableStateOf(false) }

    fun reportFailure(message: String) {
        isCompletingLogin = false
        isLoading = false
        AppLogger.e(TAG, message)
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun finishLogin(code: String) {
        if (isCompletingLogin) {
            return
        }

        isCompletingLogin = true
        isLoading = true
        scope.launch {
            try {
                val tokenResponse = githubApiService.getAccessToken(code).getOrElse { error ->
                    throw error
                }
                githubAuth.updateAccessToken(tokenResponse.access_token, tokenResponse.token_type)

                val user = githubApiService.getCurrentUser().getOrElse { error ->
                    throw error
                }

                githubAuth.saveAuthInfo(
                    accessToken = tokenResponse.access_token,
                    tokenType = tokenResponse.token_type,
                    userInfo = user
                )

                Toast.makeText(
                    context,
                    context.getString(R.string.main_github_login_success, user.login),
                    Toast.LENGTH_LONG
                ).show()
                onLoginSuccess?.invoke()
                onDismissRequest()
            } catch (e: Exception) {
                AppLogger.e(TAG, "GitHub login failed", e)
                reportFailure(
                    context.getString(R.string.main_github_login_error, e.message ?: "")
                )
            }
        }
    }

    fun handleOAuthRedirect(uri: Uri?): Boolean {
        if (!GitHubAuthPreferences.isOAuthRedirectUri(uri)) {
            return false
        }

        val returnedState = uri?.getQueryParameter("state")
        if (returnedState.isNullOrBlank() || returnedState != expectedState) {
            reportFailure(
                context.getString(R.string.main_github_login_failed, "OAuth state mismatch")
            )
            return true
        }

        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val errorDescription = uri.getQueryParameter("error_description").orEmpty()
            if (error == "access_denied") {
                onDismissRequest()
            } else {
                reportFailure(
                    context.getString(
                        R.string.main_github_login_failed,
                        errorDescription.ifBlank { error }
                    )
                )
            }
            return true
        }

        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            reportFailure(
                context.getString(R.string.main_github_login_failed, "Missing authorization code")
            )
            return true
        }

        finishLogin(code)
        return true
    }

    DisposableEffect(webView) {
        webView.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    if (request?.isForMainFrame != false && handleOAuthRedirect(request?.url)) {
                        return true
                    }
                    return false
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    super.onPageStarted(view, url, favicon)
                    isLoading = true
                    if (handleOAuthRedirect(url?.let(Uri::parse))) {
                        view?.stopLoading()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!isCompletingLogin) {
                        isLoading = false
                    }
                }
            }

        onDispose {
            releaseWebView(webView)
        }
    }

    LaunchedEffect(authorizationUrl) {
        webView.loadUrl(authorizationUrl)
    }

    Dialog(
        onDismissRequest = {
            if (!isCompletingLogin) {
                onDismissRequest()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            CustomScaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.login_github)) },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismissRequest,
                                enabled = !isCompletingLogin
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { webView.reload() },
                                enabled = !isCompletingLogin
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    AndroidView(
                        factory = { webView },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isLoading || isCompletingLogin) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }
}

private fun releaseWebView(webView: WebView) {
    try {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to release GitHub login WebView", e)
    }
}
