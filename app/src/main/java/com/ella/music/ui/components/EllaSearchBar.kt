package com.ella.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EllaSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 15.sp
        ),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (query.isBlank()) {
                    Text(
                        text = placeholder,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 15.sp
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
