package com.dentrain.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentrain.R
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.GlassBackground
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.appViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var isRegisterMode by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun authenticate(onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            error = "Введите email и пароль"
            return
        }
        if (password.length < 6) {
            error = "Пароль должен быть не менее 6 символов"
            return
        }
        isLoading = true
        error = null

        viewModelScope.launch {
            try {
                if (isRegisterMode) {
                    auth.createUserWithEmailAndPassword(email, password).await()
                } else {
                    auth.signInWithEmailAndPassword(email, password).await()
                }
                onSuccess()
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Ошибка авторизации"
            } finally {
                isLoading = false
            }
        }
    }
}

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val vm = appViewModel { AuthViewModel() }
    var passwordVisible by remember { mutableStateOf(false) }

    // Проверяем, может пользователь уже вошел
    LaunchedEffect(Unit) {
        if (FirebaseAuth.getInstance().currentUser != null) {
            onAuthSuccess()
        }
    }

    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_dt),
                contentDescription = "Логотип",
                modifier = Modifier.size(100.dp).clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "DenTrain",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Персональный дневник тренировок",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(48.dp))

            ZCard {
                Text(
                    if (vm.isRegisterMode) "Регистрация" else "Вход в аккаунт",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = vm.email,
                    onValueChange = { vm.email = it.trim() },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = vm.password,
                    onValueChange = { vm.password = it },
                    label = { Text("Пароль") },
                    leadingIcon = { Icon(Icons.Filled.Lock, null) },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = "Показать пароль")
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (vm.error != null) {
                    Spacer(Modifier.height(8.dp))
                    ErrorText(vm.error, Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(24.dp))

                if (vm.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    GradientButton(
                        if (vm.isRegisterMode) "Зарегистрироваться" else "Войти",
                        onClick = { vm.authenticate(onAuthSuccess) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = { vm.isRegisterMode = !vm.isRegisterMode },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (vm.isRegisterMode) "Уже есть аккаунт? Войти" else "Нет аккаунта? Создать")
                }
            }
        }
    }
}
