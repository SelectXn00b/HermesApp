package com.ai.assistance.operit.desktop

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import com.ai.assistance.operit.desktop.ui.theme.OperitDesktopTheme
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OperitDesktopTheme {
                DesktopScreen()
            }
        }
    }
}

private data class DesktopApp(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap,
    val launchCount: Int
)

private const val PREFS_NAME = "operit_desktop_prefs"
private const val KEY_PREFIX_LAUNCH_COUNT = "launch_count_"

private fun getLaunchCount(context: Context, packageName: String): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_PREFIX_LAUNCH_COUNT + packageName, 0)
}

private fun incrementLaunchCount(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val key = KEY_PREFIX_LAUNCH_COUNT + packageName
    val newCount = prefs.getInt(key, 0) + 1
    prefs.edit().putInt(key, newCount).apply()
}

private fun loadDesktopApps(context: Context): List<DesktopApp> {
    val pm = context.packageManager
    val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

    return packages
        .mapNotNull { packageInfo ->
            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            val packageName = appInfo.packageName

            // 过滤掉没有启动入口的应用
            pm.getLaunchIntentForPackage(packageName) ?: return@mapNotNull null

            val label = appInfo.loadLabel(pm)?.toString() ?: packageName
            val drawable = appInfo.loadIcon(pm)
            val bitmap = drawable.toBitmap(64, 64, null)
            val count = getLaunchCount(context, packageName)

            DesktopApp(
                name = label,
                packageName = packageName,
                icon = bitmap.asImageBitmap(),
                launchCount = count
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(
            compareByDescending<DesktopApp> { it.launchCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.packageName }
        )
}

@Composable
fun DesktopScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf(loadDesktopApps(context)) }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        coroutineScope.launch {
                            // 从右向左滑动（dragAmount < 0）视为向下滚动；反方向视为向上滚动
                            val currentIndex = gridState.firstVisibleItemIndex
                            val currentOffset = gridState.firstVisibleItemScrollOffset
                            val newOffset = (currentOffset - dragAmount.toInt()).coerceAtLeast(0)
                            gridState.scrollToItem(currentIndex, newOffset)
                        }
                    }
                }
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(apps) { app ->
                    AppIconCard(
                        app = app,
                        onClick = {
                            launchApp(context, app.packageName)
                            incrementLaunchCount(context, app.packageName)
                            apps = loadDesktopApps(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIconCard(
    app: DesktopApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(96.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.name,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2
            )
        }
    }
}

private fun launchApp(context: Context, packageName: String) {
    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage(packageName)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    } else {
        Toast.makeText(context, "未找到应用：$packageName", Toast.LENGTH_SHORT).show()
    }
}

@Preview(showBackground = true)
@Composable
fun DesktopScreenPreview() {
    OperitDesktopTheme {
        DesktopScreen()
    }
}