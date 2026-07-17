package com.pilerks1.hdrrecorder.ui.manualcontrols

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import com.pilerks1.hdrrecorder.ui.ManualControl

@Composable
fun BaseSlider(
    control: ManualControl,
    value: Float,
    isLandscape: Boolean,
    caps: CameraCapabilities?,
    onValueChange: (Float) -> Unit,
    primaryButtonText: String,
    modifier: Modifier = Modifier,
    primaryButtonEnabled: Boolean = true,
    primaryButtonColor: Color,
    primaryButtonTextColor: Color,
    onPrimaryButtonClick: () -> Unit,
    secondaryButtonText: String? = null,
    secondaryButtonEnabled: Boolean = false,
    secondaryButtonColor: Color = Color.DarkGray,
    secondaryButtonTextColor: Color = Color.LightGray,
    onSecondaryButtonClick: (() -> Unit)? = null
) {
    val buttons = @Composable {
        Button(
            onClick = onPrimaryButtonClick,
            enabled = primaryButtonEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryButtonColor,
                disabledContainerColor = Color.DarkGray
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = primaryButtonText,
                color = if (primaryButtonEnabled) primaryButtonTextColor else Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        if (secondaryButtonText != null && onSecondaryButtonClick != null) {
            Spacer(modifier = Modifier.size(4.dp))
            Button(
                onClick = onSecondaryButtonClick,
                enabled = secondaryButtonEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = secondaryButtonColor,
                    disabledContainerColor = Color.DarkGray
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = secondaryButtonText,
                    color = if (secondaryButtonEnabled) secondaryButtonTextColor else Color.LightGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    val content: @Composable (Modifier) -> Unit = { weightModifier ->
        if (isLandscape) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { buttons() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) { buttons() }
        }
        
        Spacer(modifier = Modifier.size(8.dp))
        
        val liveValueStr = SliderMath.formatSliderValue(control, value, caps)
        
        val ticks = remember(control, caps) { SliderMath.getTickPositions(control, caps) }
        RibbonSlider(
            value = value,
            onValueChange = onValueChange,
            isLandscape = isLandscape,
            labelString = liveValueStr,
            ticks = ticks,
            modifier = weightModifier
        )
    }

    if (isLandscape) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) { content(Modifier.weight(1f)) }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { content(Modifier.weight(1f)) }
    }
}
