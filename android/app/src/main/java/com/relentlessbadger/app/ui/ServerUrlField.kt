package com.relentlessbadger.app.ui

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Server URL input shared by the sign-in and settings advanced sections. */
@Composable
fun ServerUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Server URL") },
        supportingText = { Text("The machine on your network running the API") },
        singleLine = true,
        modifier = modifier,
    )
}
