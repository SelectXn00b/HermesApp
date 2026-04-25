package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.liquidGlass

/** Displays a header in the navigation drawer */
@Composable
fun NavigationDrawerItemHeader(title: String, appearance: NavigationDrawerAppearance) {
    Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = appearance.titleColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
    )
}

/** Displays a navigation item in the drawer with icon and label */
@Composable
fun CompactNavigationDrawerItem(
        icon: ImageVector,
        label: String,
        selected: Boolean,
        appearance: NavigationDrawerAppearance,
        onClick: () -> Unit
) {
    val itemShape = MaterialTheme.shapes.small
    val glassBaseColor = appearance.buttonContainerColor
    val selectedGlassOverlayColor =
            if (selected) {
                    appearance.selectedContainerColor.copy(alpha = 0.18f)
            } else {
                    Color.Transparent
            }
    Surface(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .height(40.dp)
                            .liquidGlass(
                                    enabled = appearance.buttonLiquidGlassEnabled,
                                    shape = itemShape,
                                    containerColor = glassBaseColor,
                                    shadowElevation = if (selected) 6.dp else 4.dp,
                                    borderWidth = 0.5.dp,
                                    blurRadius = 12.dp,
                                    overlayAlphaBoost = 0.04f,
                                    enableLens = false
                            )
                            .clip(itemShape)
                            .background(selectedGlassOverlayColor),
            onClick = onClick,
            color =
                    if (appearance.buttonLiquidGlassEnabled) {
                            Color.Transparent
                    } else if (selected) {
                            appearance.selectedContainerColor
                    } else {
                            Color.Transparent
                    },
            shape = itemShape
    ) {
        Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint =
                            if (selected) appearance.selectedContentColor
                            else appearance.itemColor,
                    modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color =
                            if (selected) appearance.selectedContentColor
                            else appearance.itemColor
            )
        }
    }
}
