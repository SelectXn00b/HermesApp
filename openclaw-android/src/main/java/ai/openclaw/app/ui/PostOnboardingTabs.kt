package ai.openclaw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.R

private enum class HomeTab(
  @StringRes val labelRes: Int,
  val icon: ImageVector,
) {
  Chat(labelRes = R.string.tab_chat, icon = Icons.Default.ChatBubble),
  Voice(labelRes = R.string.tab_voice, icon = Icons.Default.RecordVoiceOver),
  Screen(labelRes = R.string.tab_screen, icon = Icons.AutoMirrored.Filled.ScreenShare),
  Settings(labelRes = R.string.tab_settings, icon = Icons.Default.Settings),
}

@Composable
fun PostOnboardingTabs(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier,
  settingsTabSlot: (@Composable () -> Unit)? = null,
) {
  var activeTab by rememberSaveable { mutableStateOf(HomeTab.Chat) }
  var chatTabStarted by rememberSaveable { mutableStateOf(false) }
  var screenTabStarted by rememberSaveable { mutableStateOf(false) }

  // Stop TTS when user navigates away from voice tab, and lazily keep the Chat/Screen tabs
  // alive after the first visit so repeated tab switches do not rebuild their UI trees.
  LaunchedEffect(activeTab) {
    viewModel.setVoiceScreenActive(activeTab == HomeTab.Voice)
    if (activeTab == HomeTab.Chat) {
      chatTabStarted = true
    }
    if (activeTab == HomeTab.Screen) {
      screenTabStarted = true
    }
  }

  val density = LocalDensity.current
  val imeVisible = WindowInsets.ime.getBottom(density) > 0
  val hideBottomTabBar = activeTab == HomeTab.Chat && imeVisible

  Scaffold(
    modifier = modifier,
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = { if (activeTab != HomeTab.Screen) TopStatusBar() },
    bottomBar = {
      if (!hideBottomTabBar) {
        BottomTabBar(
          activeTab = activeTab,
          onSelect = { activeTab = it },
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .consumeWindowInsets(innerPadding)
          .background(mobileBackgroundGradient),
    ) {
      if (chatTabStarted) {
        Box(
          modifier =
            Modifier
              .matchParentSize()
              .alpha(if (activeTab == HomeTab.Chat) 1f else 0f)
              .zIndex(if (activeTab == HomeTab.Chat) 1f else 0f),
        ) {
          ChatSheet(viewModel = viewModel)
        }
      }

      if (screenTabStarted) {
        ScreenTabScreen(
          viewModel = viewModel,
          visible = activeTab == HomeTab.Screen,
          modifier =
            Modifier
              .matchParentSize()
              .alpha(if (activeTab == HomeTab.Screen) 1f else 0f)
              .zIndex(if (activeTab == HomeTab.Screen) 1f else 0f),
        )
      }

      when (activeTab) {
        HomeTab.Chat -> if (!chatTabStarted) ChatSheet(viewModel = viewModel)
        HomeTab.Voice -> VoiceTabScreen(viewModel = viewModel)
        HomeTab.Screen -> Unit
        HomeTab.Settings -> if (settingsTabSlot != null) settingsTabSlot() else SettingsSheet(viewModel = viewModel)
      }
    }
  }
}

@Composable
private fun ScreenTabScreen(viewModel: MainViewModel, visible: Boolean, modifier: Modifier = Modifier) {
  var refreshedOnce by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(visible, refreshedOnce) {
    if (visible && !refreshedOnce) {
      viewModel.refreshHomeCanvasOverviewIfConnected()
      refreshedOnce = true
    }
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
    CanvasScreen(viewModel = viewModel, visible = visible, modifier = Modifier.fillMaxSize())
  }
}

@Composable
private fun TopStatusBar() {
  val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

  Surface(
    modifier = Modifier.fillMaxWidth().windowInsetsPadding(safeInsets),
    color = Color.Transparent,
    shadowElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.brand_name),
        style = mobileTitle2,
        color = mobileText,
      )
    }
  }
}

@Composable
private fun BottomTabBar(
  activeTab: HomeTab,
  onSelect: (HomeTab) -> Unit,
) {
  val safeInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

  Box(
    modifier =
      Modifier
        .fillMaxWidth(),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = mobileCardSurface.copy(alpha = 0.97f),
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
      border = BorderStroke(1.dp, mobileBorder),
      shadowElevation = 6.dp,
    ) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .windowInsetsPadding(safeInsets)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        HomeTab.entries.forEach { tab ->
          val active = tab == activeTab
          Surface(
            onClick = { onSelect(tab) },
            modifier = Modifier.weight(1f).heightIn(min = 58.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (active) mobileAccentSoft else Color.Transparent,
            border = if (active) BorderStroke(1.dp, LocalMobileColors.current.chipBorderConnecting) else null,
            shadowElevation = 0.dp,
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              val label = stringResource(tab.labelRes)
              Icon(
                imageVector = tab.icon,
                contentDescription = label,
                tint = if (active) mobileAccent else mobileTextTertiary,
              )
              Text(
                text = label,
                color = if (active) mobileAccent else mobileTextSecondary,
                style = mobileCaption2.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
              )
            }
          }
        }
      }
    }
  }
}
