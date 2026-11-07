package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.drarunbabu.drsharmila.jananisync.ui.components.MgButton
import `in`.drarunbabu.drsharmila.jananisync.ui.components.MgTextField
import `in`.drarunbabu.drsharmila.jananisync.ui.components.MgTextStartAligned
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.ResetPasswordUiState

@Composable
internal fun ResetPasswordScreen(
    initialEmail: String,
    state: ResetPasswordUiState,
    onSubmit: (email: String) -> Unit,
    onEmailChange: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var email by rememberSaveable(initialEmail) { mutableStateOf(initialEmail) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MgTextStartAligned(text = "Reset password", basicFontSize = 28.sp, isBold = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text("Enter your username (the email address used to sign up). We'll email you a link to choose a new JananiSync password.")
        Spacer(Modifier.height(16.dp))
        MgTextField(
            value = email,
            onValueChange = { email = it; onEmailChange() },
            label = "Username",
            modifier = Modifier.fillMaxWidth().testTag("reset-password-email"),
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done)
        )
        state.message?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = if (state.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        MgButton(
            onClick = { onSubmit(email) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
            text = if (state.isLoading) "Sending reset link…" else "Send reset link"
        )
        TextButton(onClick = onBackToLogin, enabled = !state.isLoading) { Text("Back to log in") }
    }
}
