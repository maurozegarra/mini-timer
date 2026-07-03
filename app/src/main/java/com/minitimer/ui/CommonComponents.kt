package com.minitimer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.ui.theme.Dims
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TRACK

@Composable
internal fun AppPrimaryButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(Dims.buttonHeight),
        shape = RoundedCornerShape(Dims.button),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = ON_ACCENT,
            disabledContainerColor = TRACK,
            disabledContentColor = TEXT_DIM,
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
        border = BorderStroke(1.dp, TRACK),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (desc != null) {
                Spacer(Modifier.height(4.dp))
                Text(desc, color = TEXT_DIM, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ON_ACCENT,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color(0xFFCFD3D6),
                uncheckedTrackColor = TRACK,
            ),
        )
    }
}
