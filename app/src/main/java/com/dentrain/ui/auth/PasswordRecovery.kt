package com.dentrain.ui.auth

import android.net.Uri
import android.util.Patterns
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentrain.BuildConfig
import com.dentrain.R
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.BorderedBackButton
import com.dentrain.ui.components.workoutFieldColors
import com.dentrain.ui.theme.DenTrainPalette
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.ActionCodeResult
import com.google.firebase.auth.ActionCodeUrl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URL

private const val RESET_LINK_HOST = "dentrain-9af07.firebaseapp.com"
private const val RESET_LINK_PATH = "/__/auth/links"

@Composable
internal fun PasswordResetRequestDialog(initialEmail: String, onDismiss: () -> Unit) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFF1A1D24),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFB5B8C3),
        title = { Text(if (sent) "Проверьте почту" else "Восстановление пароля", fontWeight = FontWeight.Bold) },
        text = {
            if (sent) {
                Text(buildAnnotatedString {
                    append("Если аккаунт с этой почтой существует, на ")
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(email) }
                    append(" придёт письмо. Откройте ссылку из письма на телефоне с DenTrain.")
                })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Введите почту, которую используете для входа. Мы отправим ссылку для создания нового пароля.")
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            error = null
                        },
                        label = { Text("Электронная почта") },
                        enabled = !sending,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            unfocusedBorderColor = Color(0xFF484B57),
                            unfocusedLabelColor = Color(0xFFB5B8C3)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    ErrorText(error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !sending,
                shape = RoundedCornerShape(14.dp),
                onClick = {
                    if (sent) {
                        onDismiss()
                    } else if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                        error = "Введите корректный адрес почты."
                    } else {
                        email = email.trim()
                        focus.clearFocus()
                        sending = true
                        error = null
                        scope.launch {
                            try {
                                FirebaseFunctions.getInstance()
                                    .getHttpsCallableFromUrl(URL(BuildConfig.PASSWORD_RESET_ENDPOINT))
                                    .call(mapOf("email" to email))
                                    .await()
                                sent = true
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: FirebaseFunctionsException) {
                                error = if (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                                    "Слишком много запросов. Попробуйте через минуту."
                                } else {
                                    "Не удалось отправить письмо. Попробуйте ещё раз."
                                }
                            } catch (e: Exception) {
                                error = recoveryError(e, "Не удалось отправить письмо. Попробуйте ещё раз.")
                            } finally {
                                sending = false
                            }
                        }
                    }
                }
            ) { Text(if (sent) "Готово" else if (sending) "Отправляем…" else "Отправить письмо") }
        },
        dismissButton = {
            if (!sent) TextButton(onClick = onDismiss, enabled = !sending) { Text("Отмена") }
        }
    )
}

internal class PasswordRecoveryViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private var code: String? = null
    private var verification: Job? = null
    var attempt by mutableStateOf(0)
        private set
    var active by mutableStateOf(false)
        private set
    var email by mutableStateOf<String?>(null)
        private set
    var password by mutableStateOf("")
    var confirmation by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var linkExpired by mutableStateOf(false)
        private set
    var changed by mutableStateOf(false)
        private set
    var signedIn by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun openLink(uri: Uri) {
        if (uri.scheme != "https" || uri.host != RESET_LINK_HOST || uri.path != RESET_LINK_PATH) return
        val action = ActionCodeUrl.parseLink(uri.toString()) ?: return
        if (action.operation != ActionCodeResult.PASSWORD_RESET || action.code == code ||
            (busy && email != null) || changed
        ) return

        code = action.code
        attempt++
        active = true
        email = null
        password = ""
        confirmation = ""
        linkExpired = false
        verifyLink()
    }

    fun verifyLink() {
        val currentCode = code ?: return
        verification?.cancel()
        busy = true
        error = null
        verification = viewModelScope.launch {
            try {
                email = auth.verifyPasswordResetCode(currentCode).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showLinkError(e)
            } finally {
                if (code == currentCode) busy = false
            }
        }
    }

    fun submit(focus: FocusManager) {
        val address = email ?: return
        val currentCode = code ?: return
        if (busy || linkExpired || signedIn) return
        if (!changed && (password.length < 6 || password != confirmation)) {
            error = if (password.length < 6) "Пароль должен быть не менее 6 символов." else "Пароли не совпадают."
            return
        }
        focus.clearFocus()
        val newPassword = password
        error = null
        busy = true
        viewModelScope.launch {
            try {
                if (!changed) {
                    auth.confirmPasswordReset(currentCode, newPassword).await()
                    changed = true
                    confirmation = ""
                }
                auth.signInWithEmailAndPassword(address, newPassword).await()
                password = ""
                signedIn = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (changed) {
                    error = recoveryError(e, "Не удалось войти автоматически. Повторите попытку или войдите с новым паролем.")
                } else if (e is FirebaseAuthWeakPasswordException) {
                    error = "Пароль не соответствует требованиям безопасности. Введите другой пароль."
                } else {
                    showLinkError(e)
                }
            } finally {
                busy = false
            }
        }
    }

    private fun showLinkError(e: Exception) {
        linkExpired = e is FirebaseAuthActionCodeException || e is FirebaseAuthInvalidUserException
        error = if (linkExpired) "Ссылка истекла или уже использована. Запросите новое письмо."
        else recoveryError(e, "Не удалось выполнить восстановление. Попробуйте ещё раз.")
    }

    fun close() {
        verification?.cancel()
        code = null
        password = ""
        confirmation = ""
        email = null
        active = false
        busy = false
        changed = false
        signedIn = false
        error = null
        linkExpired = false
    }
}

private fun recoveryError(e: Exception, fallback: String) = when (e) {
    is FirebaseNetworkException -> "Проверьте подключение к интернету и попробуйте ещё раз."
    is FirebaseTooManyRequestsException -> "Слишком много попыток. Попробуйте позже."
    else -> fallback
}

@Composable
internal fun PasswordRecoveryScreen(
    vm: PasswordRecoveryViewModel,
    logoModifier: Modifier,
    onClose: () -> Unit
) {
    val focus = LocalFocusManager.current
    var requestNewLink by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (!vm.busy && !vm.signedIn) onClose() }
    if (requestNewLink) {
        PasswordResetRequestDialog(vm.email.orEmpty()) { requestNewLink = false }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.logo_dt),
            contentDescription = null,
            modifier = Modifier.size(132.dp).then(logoModifier)
        )
        Column(
            Modifier.widthIn(max = 400.dp).fillMaxWidth()
                .animateContentSize(animationSpec = tween(500))
                .clip(RoundedCornerShape(16.dp))
                .background(DenTrainPalette.Panel)
                .border(1.dp, DenTrainPalette.SubtleBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (vm.changed) "Пароль изменён" else "Восстановление пароля",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (vm.changed) {
                if (vm.busy || vm.signedIn) Text("Входим в аккаунт…", color = DenTrainPalette.MutedText)
            } else if (vm.email != null && !vm.linkExpired) {
                vm.email?.let { Text(it, color = DenTrainPalette.MutedText) }
                OutlinedTextField(
                    value = vm.password,
                    onValueChange = { vm.password = it },
                    label = { Text("Новый пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !vm.busy,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = workoutFieldColors(vm.password.isNotBlank(), false),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = vm.confirmation,
                    onValueChange = { vm.confirmation = it },
                    label = { Text("Повторите новый пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !vm.busy,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = workoutFieldColors(vm.confirmation.isNotBlank(), false),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ErrorText(vm.error)
            when {
                vm.busy -> CircularProgressIndicator()
                vm.signedIn -> Unit
                vm.linkExpired -> Button(onClick = { requestNewLink = true }) { Text("Запросить новое письмо") }
                vm.email == null -> Button(onClick = vm::verifyLink) { Text("Проверить ссылку ещё раз") }
                else -> Button(onClick = { vm.submit(focus) }) {
                    Text(if (vm.changed) "Повторить вход" else "Изменить пароль")
                }
            }
        }
    }
    if (!vm.busy && !vm.signedIn) {
        Box(Modifier.offset(x = 4.dp, y = 4.dp)) {
            BorderedBackButton(onClick = onClose)
        }
    }
    }
}
