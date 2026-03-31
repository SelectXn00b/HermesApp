package ai.openclaw.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.skill.NoOpSkillActions
import ai.openclaw.app.skill.SkillActions

@Composable
fun RootScreen(
  viewModel: MainViewModel,
  settingsTabSlot: (@Composable () -> Unit)? = null,
  skillActions: SkillActions = NoOpSkillActions,
) {
  val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()

  if (!onboardingCompleted) {
    OnboardingFlow(viewModel = viewModel, modifier = Modifier.fillMaxSize())
    return
  }

  PostOnboardingTabs(
    viewModel = viewModel,
    modifier = Modifier.fillMaxSize(),
    settingsTabSlot = settingsTabSlot,
    skillActions = skillActions,
  )
}
