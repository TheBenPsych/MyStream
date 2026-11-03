package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.touch.Dialog

@Composable
fun QuickConnectDialog(
    onSubmit: (String) -> Unit,
    onDismissRequest: () -> Unit,
    serverError: String? = null,
    busy: Boolean = false,
) {
    var code by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val isValidCode: (String) -> Boolean = { value ->
        val trimmed = value.trim()
        trimmed.length == 6 && trimmed.all { it.isDigit() }
    }

    val onSubmitCode = {
        if (isValidCode(code)) {
            showError = false
            onSubmit(code.trim())
        } else {
            showError = true
        }
    }

    Dialog(
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        onDismissRequest = onDismissRequest,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .padding(16.dp)
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    // Benflix: plain graphite, not the reddish-brown elevation tint
                    .background(
                        color = Color(0xFF18181C),
                        shape = RoundedCornerShape(12.dp),
                    ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .padding(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.quick_connect),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.quick_connect_how),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBBBBBB),
                )

                EditTextBox(
                    value = code,
                    onValueChange = {
                        code = it
                        showError = false
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                onSubmitCode()
                            },
                        ),
                    isInputValid = { value ->
                        !showError || isValidCode(value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (showError || serverError != null) {
                    Text(
                        text = if (showError) stringResource(R.string.quick_connect_code_error) else serverError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                TextButton(
                    stringRes = if (busy) R.string.quick_connect_busy else R.string.quick_connect_sign_in,
                    onClick = { if (!busy) onSubmitCode() },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
