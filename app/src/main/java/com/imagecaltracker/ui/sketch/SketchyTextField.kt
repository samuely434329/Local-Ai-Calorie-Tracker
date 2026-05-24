package com.imagecaltracker.ui.sketch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imagecaltracker.ui.theme.SketchColors

/**
 * A minimalist text field wrapped in a sketchy hand-drawn border.
 * Uses BasicTextField (not TextField) so we don't get Material's heavy
 * outlined styling, which would clash with the pencil aesthetic.
 */
@Composable
fun SketchyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    height: Dp = 44.dp,
    seed: Int = 71,
) {
    Box(
        modifier = modifier
            .height(height)
            .sketchyBorder(
                color = SketchColors.InkDark,
                strokeWidth = 1.4.dp,
                cornerRadius = 6.dp,
                seed = seed,
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty() && placeholder != null) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = SketchColors.InkLight,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(SketchColors.InkDark),
            textStyle = LocalTextStyle.current.copy(color = SketchColors.InkDark),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
