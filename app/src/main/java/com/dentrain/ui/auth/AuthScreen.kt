package com.dentrain.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.dentrain.R
import com.dentrain.ui.components.DateField
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.GlassBackground
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.appViewModel
import com.dentrain.util.EmailSender
import com.dentrain.util.Fmt
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

enum class AuthStep {
    AUTH,                // Форма входа / регистрации
    EMAIL_VERIFICATION,  // Экран ввода 6-значного кода подтверждения
    PROFILE_SETUP        // Экран заполнения данных профиля (можно пропустить)
}

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var step by mutableStateOf(AuthStep.AUTH)

    // Поля авторизации
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var isRegisterMode by mutableStateOf(false)

    // Поля 6-значного кода
    var generatedCode by mutableStateOf("")
    var inputCode by mutableStateOf("")

    // Поля начального профиля
    var nickname by mutableStateOf("")
    var username by mutableStateOf("")
    var dobEpochDay by mutableStateOf(LocalDate.now().toEpochDay())
    var heightCm by mutableStateOf("")
    var weightKg by mutableStateOf("")
    var gender by mutableStateOf("male")

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var successMsg by mutableStateOf<String?>(null)

    fun checkUserStatus(onSuccess: () -> Unit) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(user.uid).get().await()
                if (doc.exists()) {
                    onSuccess()
                } else {
                    step = AuthStep.PROFILE_SETUP
                }
            } catch (_: Exception) {
                onSuccess()
            }
        }
    }

    fun authenticate(onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            error = "Введите email и пароль"
            return
        }
        if (password.length < 6) {
            error = "Пароль должен быть не менее 6 символов"
            return
        }
        if (isRegisterMode && password != confirmPassword) {
            error = "Пароли не совпадают"
            return
        }

        isLoading = true
        error = null
        successMsg = null

        viewModelScope.launch {
            try {
                if (isRegisterMode) {
                    val result = auth.createUserWithEmailAndPassword(email, password).await()
                    val user = result.user
                    if (user != null) {
                        try {
                            user.sendEmailVerification().await()
                        } catch (_: Exception) {}
                        step = AuthStep.EMAIL_VERIFICATION
                        successMsg = "Ссылка для подтверждения отправлена на почту $email"
                    }
                } else {
                    val result = auth.signInWithEmailAndPassword(email, password).await()
                    val user = result.user
                    if (user != null && !user.isEmailVerified) {
                        user.sendEmailVerification()
                        step = AuthStep.EMAIL_VERIFICATION
                        error = "Email ещё не подтверждён! Перейдите по ссылке из письма на вашей почте."
                    } else {
                        checkUserStatus(onSuccess)
                    }
                }
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Ошибка авторизации"
            } finally {
                isLoading = false
            }
        }
    }

    fun verifyEmailCode(onSuccess: () -> Unit) {
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                auth.currentUser?.reload()?.await()
                val user = auth.currentUser
                if (user != null && user.isEmailVerified) {
                    step = AuthStep.PROFILE_SETUP
                } else {
                    error = "Ссылка подтверждения пока не нажата. Откройте письмо на почте $email и перейдите по ссылке!"
                }
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Ошибка проверки почты"
            } finally {
                isLoading = false
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            try {
                auth.currentUser?.sendEmailVerification()?.await()
                successMsg = "Новое письмо с ссылкой подтверждения отправлено на почту!"
            } catch (e: Exception) {
                error = "Не удалось отправить повторно: ${e.localizedMessage}"
            }
        }
    }

    fun cancelRegistration() {
        viewModelScope.launch {
            try {
                auth.currentUser?.delete()?.await()
            } catch (_: Exception) {}
            auth.signOut()
            step = AuthStep.AUTH
            email = ""
            password = ""
            confirmPassword = ""
            inputCode = ""
            generatedCode = ""
            error = null
            successMsg = null
        }
    }

    fun saveInitialProfile(skip: Boolean, onSuccess: () -> Unit) {
        val uid = auth.uid ?: run {
            onSuccess()
            return
        }
        isLoading = true
        error = null

        viewModelScope.launch {
            try {
                val data = if (skip) {
                    hashMapOf(
                        "nickname" to (email.substringBefore("@").ifBlank { "Пользователь" }),
                        "username" to "",
                        "dobEpochDay" to dobEpochDay,
                        "avatarBase64" to "",
                        "role" to "athlete",
                        "gender" to "male"
                    )
                } else {
                    val heightNum = heightCm.filter { char -> char.isDigit() }.take(3).toIntOrNull()
                    val weightNum = Fmt.parseWeight(weightKg)
                    hashMapOf(
                        "nickname" to nickname.trim().ifBlank { email.substringBefore("@") },
                        "username" to username.trim().filter { char -> char.isLetterOrDigit() || char == '_' },
                        "dobEpochDay" to dobEpochDay,
                        "avatarBase64" to "",
                        "role" to "athlete",
                        "heightCm" to heightNum,
                        "weightKg" to weightNum,
                        "gender" to gender
                    )
                }
                db.collection("users").document(uid).set(data).await()
                onSuccess()
            } catch (e: Exception) {
                error = "Ошибка сохранения профиля: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    fun signInWithGoogleCredential(idToken: String, onSuccess: () -> Unit) {
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
                checkUserStatus(onSuccess)
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Ошибка авторизации Google"
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
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { token ->
                vm.signInWithGoogleCredential(token, onAuthSuccess)
            } ?: run {
                vm.error = "Не удалось получить токен Google"
            }
        } catch (e: ApiException) {
            vm.error = "Ошибка Google: ${e.statusCode}"
        }
    }

    fun launchGoogleSignIn() {
        val webClientId = "800963952423-ji6die6dk2qgaskefhnjomverqnh8hdk.apps.googleusercontent.com"
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    LaunchedEffect(Unit) {
        if (FirebaseAuth.getInstance().currentUser != null) {
            vm.checkUserStatus(onAuthSuccess)
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
                modifier = Modifier.size(90.dp).clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "DenTrain",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Персональный дневник тренировок",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(32.dp))

            when (vm.step) {
                AuthStep.AUTH -> {
                    ZCard {
                        Text(
                            if (vm.isRegisterMode) "Регистрация" else "Вход в аккаунт",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))

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

                        if (vm.isRegisterMode) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = vm.confirmPassword,
                                onValueChange = { vm.confirmPassword = it },
                                label = { Text("Повторите пароль") },
                                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                                trailingIcon = {
                                    val image = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Icon(imageVector = image, contentDescription = "Показать пароль")
                                    }
                                },
                                singleLine = true,
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (vm.error != null) {
                            Spacer(Modifier.height(8.dp))
                            ErrorText(vm.error, Modifier.fillMaxWidth())
                        }

                        Spacer(Modifier.height(20.dp))

                        if (vm.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            GradientButton(
                                if (vm.isRegisterMode) "Зарегистрироваться" else "Войти",
                                onClick = { vm.authenticate(onAuthSuccess) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        TextButton(
                            onClick = {
                                vm.isRegisterMode = !vm.isRegisterMode
                                vm.error = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (vm.isRegisterMode) "Уже есть аккаунт? Войти" else "Нет аккаунта? Создать")
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "или",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { launchGoogleSignIn() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !vm.isLoading
                        ) {
                            Text("Войти через Google")
                        }
                    }
                }

                AuthStep.EMAIL_VERIFICATION -> {
                    ZCard {
                        Icon(
                            Icons.Filled.MarkEmailRead,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Подтверждение почты",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "На ваш адрес ${vm.email} отправлено письмо со ссылкой подтверждения.\n\nОткройте вашу почту, перейдите по ссылке и затем нажмите кнопку ниже.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (vm.error != null) {
                            Spacer(Modifier.height(12.dp))
                            ErrorText(vm.error, Modifier.fillMaxWidth())
                        }
                        if (vm.successMsg != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                vm.successMsg!!,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        if (vm.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            GradientButton(
                                "Я подтвердил почту (Проверить)",
                                onClick = { vm.verifyEmailCode(onAuthSuccess) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        TextButton(
                            onClick = { vm.resendVerificationEmail() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Отправить письмо повторно")
                        }

                        Spacer(Modifier.height(4.dp))

                        TextButton(
                            onClick = { vm.cancelRegistration() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Отмена / Использовать другой email", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                AuthStep.PROFILE_SETUP -> {
                    ZCard {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Создание профиля",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Вы можете заполнить данные сейчас или пропустить и настроить позже в профиле.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(20.dp))

                        OutlinedTextField(
                            value = vm.nickname,
                            onValueChange = { vm.nickname = it },
                            label = { Text("Никнейм (Имя)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = vm.username,
                            onValueChange = { input -> vm.username = input.filter { char -> char.isLetterOrDigit() || char == '_' } },
                            label = { Text("@username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        DateField(
                            epochDay = vm.dobEpochDay,
                            onChange = { vm.dobEpochDay = it },
                            label = "Дата рождения",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))
                        SectionTitle("Антропометрия")

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = vm.gender == "male",
                                onClick = { vm.gender = "male" },
                                label = { Text("Мужчина") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = vm.gender == "female",
                                onClick = { vm.gender = "female" },
                                label = { Text("Женщина") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = vm.heightCm,
                                onValueChange = { input -> vm.heightCm = input.filter { char -> char.isDigit() } },
                                label = { Text("Рост, см") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = vm.weightKg,
                                onValueChange = { input -> vm.weightKg = input.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                                label = { Text("Вес, кг") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (vm.error != null) {
                            Spacer(Modifier.height(8.dp))
                            ErrorText(vm.error, Modifier.fillMaxWidth())
                        }

                        Spacer(Modifier.height(20.dp))

                        if (vm.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            GradientButton(
                                "Сохранить и продолжить",
                                onClick = { vm.saveInitialProfile(skip = false, onSuccess = onAuthSuccess) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { vm.saveInitialProfile(skip = true, onSuccess = onAuthSuccess) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Пропустить")
                            }
                        }
                    }
                }
            }
        }
    }
}
