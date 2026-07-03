package com.minitimer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.ui.theme.AppTheme
import com.minitimer.ui.theme.Dims

@Composable
internal fun AppPrimaryButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(Dims.buttonHeight),
        shape = RoundedCornerShape(Dims.button),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = c.onAccent,
            disabledContainerColor = c.track,
            disabledContentColor = c.textDim,
        ),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
internal fun AppOutlineButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(Dims.buttonHeight),
        shape = RoundedCornerShape(Dims.button),
        border = BorderStroke(1.dp, AppTheme.colors.track),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
internal fun AppStepButton(
    symbol: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(c.track)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            color = if (enabled) accent else c.textDim,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
    }
}

@Composable
internal fun AppStepper(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 3600,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    val c = AppTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = c.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        AppStepButton("−", accent) { onChange((value - step).coerceAtLeast(min)) }
        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text(format(value), color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        AppStepButton("+", accent) { onChange((value + step).coerceAtMost(max)) }
    }
}

@Composable
internal fun SwitchRow(
    label: String,
    desc: String?,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = c.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (desc != null) {
                Spacer(Modifier.height(4.dp))
                Text(desc, color = c.textDim, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color(0xFFCFD3D6),
                uncheckedTrackColor = c.track,
            ),
        )
    }
}
