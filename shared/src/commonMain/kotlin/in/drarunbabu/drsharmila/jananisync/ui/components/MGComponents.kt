package `in`.drarunbabu.drsharmila.jananisync.ui.components

import `in`.drarunbabu.drsharmila.jananisync.ui.theme.LocalAppearanceSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MgText(
    modifier: Modifier = Modifier,
    text: String,
    basicFontSize0: Int = LocalAppearanceSettings.current.basicFontSize0,
    times: Float = 1f,
    isBold: Boolean = false,
    textColor: Color = LocalContentColor.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        fontSize = (basicFontSize0 / LocalAppearanceSettings.current.scale * times).sp,
        lineHeight = ((basicFontSize0 / LocalAppearanceSettings.current.scale * times) + 4).sp,
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        color = textColor,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = TextAlign.Center
    )
}

@Composable
fun MgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    basicFontSize0: Int = LocalAppearanceSettings.current.basicFontSize0,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { MgText(text = label, basicFontSize0 = basicFontSize0) },
        textStyle = TextStyle(fontSize = (basicFontSize0 / LocalAppearanceSettings.current.scale).sp),
        modifier = modifier,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        isError = isError,
        readOnly = readOnly,
        enabled = enabled,
        supportingText = supportingText?.let { { MgText(text = it, basicFontSize0 = basicFontSize0) } },
        singleLine = singleLine
    )
}

@Composable
fun MgButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    basicFontSize0: Int = LocalAppearanceSettings.current.basicFontSize0,
    text: String,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    times: Float = 1f,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = colors
    ) {
        leadingIcon?.let {
            it()
            Spacer(Modifier.width(8.dp))
        }
        MgText(
            text = text,
            basicFontSize0 = basicFontSize0,
            isBold = true,
            times = times
        )
    }
}

@Composable
fun MgLoginForm(
    appName: String,
    onLogin: (email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onSignUp: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    basicFontSize0: Int = LocalAppearanceSettings.current.basicFontSize0
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MgTextStartAligned(
            modifier = Modifier.fillMaxWidth(),
            text = "Login to $appName",
            basicFontSize = 28.sp,
            isBold = true
        )
        Spacer(Modifier.height(28.dp))
        MgTextField(
            value = email,
            onValueChange = { email = it },
            label = "Username",
            basicFontSize0 = basicFontSize0,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(16.dp))
        MgTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            supportingText = "This is your $appName password. It does not need to be the same as your email provider’s password.",
            basicFontSize0 = basicFontSize0,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { onResetPassword(email) }, enabled = !isLoading) {
                Text("Forgot password?")
            }
        }
        Spacer(Modifier.height(8.dp))
        MgButton(
            onClick = { onLogin(email, password) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !isLoading,
            basicFontSize0 = basicFontSize0,
            text = if (isLoading) "Signing in…" else "Log in"
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Don't have an account?")
            TextButton(onClick = onSignUp) {
                Text("Sign up")
            }
        }
    }
}

@Composable
fun MgSignUpForm(
    appName: String,
    isLoading: Boolean,
    onCreateAccount: (email: String, password: String, confirmPassword: String) -> Unit,
    modifier: Modifier = Modifier,
    basicFontSize0: Int = LocalAppearanceSettings.current.basicFontSize0
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val passwordsMatch = password == confirmPassword

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MgTextStartAligned(
            modifier = Modifier.fillMaxWidth(),
            text = "Create your $appName account",
            basicFontSize = 28.sp,
            isBold = true
        )
        Spacer(Modifier.height(24.dp))
        MgTextField(
            value = email,
            onValueChange = { email = it },
            label = "Username",
            supportingText = "Use your email address as your username.",
            basicFontSize0 = basicFontSize0,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(16.dp))
        MgTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            supportingText = "This is your $appName password. It does not need to be the same as your email provider’s password.",
            basicFontSize0 = basicFontSize0,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(16.dp))
        MgTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Confirm password",
            basicFontSize0 = basicFontSize0,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            isError = confirmPassword.isNotEmpty() && !passwordsMatch,
            supportingText = if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                "Passwords do not match."
            } else {
                null
            }
        )
        Spacer(Modifier.height(24.dp))
        Text("Creating an account provisions your sign-in and sends a verification email. Next, review the privacy policy and accept or deny. Organisation access stays blocked until acceptance is recorded. Denial retains the account and decision history; you can sign out or request deletion.")
        Spacer(Modifier.height(12.dp))
        MgButton(
            onClick = { onCreateAccount(email, password, confirmPassword) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !isLoading,
            basicFontSize0 = basicFontSize0,
            text = if (isLoading) "Creating account…" else "Create account and review privacy"
        )
    }
}

@Composable
fun MgTextStartAligned(
    modifier: Modifier = Modifier,
    text: String,
    basicFontSize: TextUnit,
    times: Float = 1f,
    isBold: Boolean = false,
    fontColor: Color = MaterialTheme.colorScheme.onSurface
){
    Text(
        modifier = modifier,
        text = text,
        fontWeight = if(isBold) FontWeight.Black else FontWeight.Normal,
        fontSize = (basicFontSize.value*times).sp,
        lineHeight = ((basicFontSize.value*times)+8).sp,
        textAlign = TextAlign.Start,
        color= fontColor
    )
}
