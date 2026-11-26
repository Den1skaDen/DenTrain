package com.dentrain.ui.auth

import android.content.SharedPreferences
import android.os.SystemClock
import com.dentrain.util.UserFacingException
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.CommonStatusCodes
import com.dentrain.util.userMessage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.dentrain.ui.theme.LocalSplashActive
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.BuildConfig
import com.dentrain.DenTrainApp
import com.dentrain.ui.components.DateField
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.appViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.net.URL

enum class AuthStep {
    AUTH,                // Форма входа / регистрации
    GOOGLE_PASSWORD,
    EMAIL_VERIFICATION,
    REGISTRATION_COMPLETE,
    PROFILE_SETUP        // Экран заполнения данных профиля (можно пропустить)
}

internal fun FirebaseUser.needsGooglePassword(): Boolean =
    providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } &&
        providerData.none { it.providerId == EmailAuthProvider.PROVIDER_ID }

class AuthViewModel(private val resendPreferences: SharedPreferences) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val pendingEmail = auth.currentUser?.takeUnless { it.isEmailVerified }?.email.orEmpty()
    private val pendingPasswords = mutableMapOf<String, String>()

    var step by mutableStateOf(
        when {
            auth.currentUser?.needsGooglePassword() == true -> AuthStep.GOOGLE_PASSWORD
            pendingEmail.isNotEmpty() -> AuthStep.EMAIL_VERIFICATION
            else -> AuthStep.AUTH
        }
    )
        private set

    // Поля авторизации
    var email by mutableStateOf(auth.currentUser?.email.orEmpty())
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var isRegisterMode by mutableStateOf(pendingEmail.isNotEmpty())

    // Поля начального профиля
    var nickname by mutableStateOf("")
    var username by mutableStateOf("")
    var dobEpochDay by mutableStateOf(LocalDate.now().toEpochDay())

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var verificationError by mutableStateOf<String?>(null)
        private set
    var emailVerified by mutableStateOf(false)
        private set
    var verificationEmailResent by mutableStateOf(false)
        private set
    var resendDeadline by mutableStateOf(auth.uid?.let(::readResendDeadline) ?: 0L)
        private set
    private var openExistingProfile = false
    private var googlePasswordLinked = false

    private fun readResendDeadline(uid: String): Long {
        val remaining = (resendPreferences.getLong(uid, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
        return SystemClock.elapsedRealtime() + remaining
    }

    private fun deferResend(uid: String, response: Any?) {
        val seconds = ((response as? Map<*, *>)?.get("retryAfterSeconds") as? Number)
            ?.toLong()?.takeIf { it >= 0 } ?: 60L
        val delayMillis = seconds * 1_000L
        resendDeadline = SystemClock.elapsedRealtime() + delayMillis
        resendPreferences.edit().putLong(uid, System.currentTimeMillis() + delayMillis).apply()
    }

    suspend fun restoreSession(onSuccess: () -> Unit) {
        if (step != AuthStep.AUTH || isLoading || auth.currentUser == null) return
        isLoading = true
        try {
            checkUserStatus(onSuccess = onSuccess)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage("Не удалось загрузить профиль.")
        } finally {
            isLoading = false
        }
    }

    private suspend fun checkUserStatus(onSuccess: () -> Unit) {
        val user = auth.currentUser ?: return
        email = user.email.orEmpty()
        if (user.needsGooglePassword()) {
            password = ""
            confirmPassword = ""
            googlePasswordLinked = false
            step = AuthStep.GOOGLE_PASSWORD
            return
        }
        if (!user.isEmailVerified) {
            resendDeadline = readResendDeadline(user.uid)
            step = AuthStep.EMAIL_VERIFICATION
            return
        }
        loadProfile(user.uid, onSuccess)
    }

    private suspend fun loadProfile(uid: String, onSuccess: () -> Unit) {
        val doc = db.collection("profiles").document(uid).get().await()
        if (auth.uid != uid) return
        if (step == AuthStep.EMAIL_VERIFICATION) {
            openExistingProfile = doc.exists()
            error = null
            step = AuthStep.REGISTRATION_COMPLETE
            return
        }
        if (doc.exists()) {
            clearCredentials()
            onSuccess()
        } else {
            error = null
            step = AuthStep.PROFILE_SETUP
        }
    }

    fun finishRegistration(onSuccess: () -> Unit) {
        if (step != AuthStep.REGISTRATION_COMPLETE) return
        if (openExistingProfile) {
            clearCredentials()
            onSuccess()
        } else {
            step = AuthStep.PROFILE_SETUP
        }
    }

    fun authenticate(onSuccess: () -> Unit) {
        if (isLoading) return
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
        verificationEmailResent = false

        viewModelScope.launch {
            try {
                if (isRegisterMode) {
                    registerOrResume(email, password)
                } else {
                    auth.signInWithEmailAndPassword(email, password).await()
                }
                checkUserStatus(onSuccess)
                if (step == AuthStep.EMAIL_VERIFICATION) {
                    sendVerificationEmail()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.userMessage("Ошибка авторизации.")
            } finally {
                isLoading = false
            }
        }
    }

    fun saveGooglePassword(onSuccess: () -> Unit) {
        if (step != AuthStep.GOOGLE_PASSWORD || isLoading) return
        if (!googlePasswordLinked) {
            error = when {
                password.isBlank() || password.length < 6 -> "Пароль должен быть не менее 6 символов"
                password != confirmPassword -> "Пароли не совпадают"
                else -> null
            }
            if (error != null) return
        }
        val user = auth.currentUser ?: run {
            cancelGooglePassword()
            return
        }
        val newPassword = password
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                if (!googlePasswordLinked) {
                    linkGooglePassword(auth, user, newPassword)
                    googlePasswordLinked = true
                    password = ""
                    confirmPassword = ""
                }
                checkUserStatus(onSuccess)
                if (step == AuthStep.EMAIL_VERIFICATION) sendVerificationEmail()
            } catch (e: CancellationException) {
                throw e
            } catch (_: FirebaseAuthRecentLoginRequiredException) {
                leaveGooglePassword()
                error = "Войдите через Google ещё раз, чтобы создать пароль."
            } catch (e: Exception) {
                error = e.userMessage(
                    if (googlePasswordLinked) "Пароль сохранён. Не удалось загрузить профиль."
                    else "Не удалось сохранить пароль. Попробуйте ещё раз."
                )
            } finally {
                isLoading = false
            }
        }
    }

    fun cancelGooglePassword() {
        if (step == AuthStep.GOOGLE_PASSWORD && !isLoading) leaveGooglePassword()
    }

    private fun leaveGooglePassword() {
        auth.signOut()
        clearCredentials()
        googlePasswordLinked = false
        isRegisterMode = false
        error = null
        step = AuthStep.AUTH
    }

    private suspend fun registerOrResume(address: String, newPassword: String) {
        val key = address.lowercase()
        try {
            auth.createUserWithEmailAndPassword(address, newPassword).await()
        } catch (collision: FirebaseAuthUserCollisionException) {
            val previousPassword = pendingPasswords[key] ?: newPassword
            val user = try {
                auth.signInWithEmailAndPassword(address, previousPassword).await().user
            } catch (_: FirebaseAuthInvalidCredentialsException) {
                throw UserFacingException("Для продолжения регистрации введите пароль, который вы указали ранее.")
            } ?: throw collision

            try {
                user.reload().await()
                if (user.isEmailVerified) {
                    if (key !in pendingPasswords) throw collision
                    if (previousPassword != newPassword) {
                        throw UserFacingException("Почта уже подтверждена. Войдите с прежним паролем.")
                    }
                    return
                }
                if (previousPassword != newPassword) user.updatePassword(newPassword).await()
            } catch (e: Exception) {
                if (auth.uid == user.uid) auth.signOut()
                throw e
            }
        }
        pendingPasswords[key] = newPassword
    }

    private fun clearCredentials() {
        email = ""
        password = ""
        confirmPassword = ""
        pendingPasswords.clear()
    }

    suspend fun awaitEmailVerification(onSuccess: () -> Unit) {
        while (step == AuthStep.EMAIL_VERIFICATION) {
            val user = auth.currentUser
            if (user == null) {
                returnToLogin()
                return
            }
            try {
                if (!user.isEmailVerified) user.reload().await()
                if (step != AuthStep.EMAIL_VERIFICATION) return
                val refreshedUser = auth.currentUser
                if (refreshedUser?.uid == user.uid) {
                    emailVerified = refreshedUser.isEmailVerified
                    verificationError = null
                    if (emailVerified) {
                        refreshedUser.getIdToken(true).await()
                        loadProfile(user.uid, onSuccess)
                        return
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: FirebaseAuthInvalidUserException) {
                if (auth.uid == user.uid) auth.signOut()
                returnToLogin()
                return
            } catch (e: Exception) {
                verificationError = e.userMessage(
                    if (emailVerified) "Не удалось загрузить профиль. Повторим автоматически."
                    else "Не удалось проверить почту. Повторим автоматически."
                )
            }
            delay(5_000)
        }
    }

    private fun returnToLogin() {
        emailVerified = false
        verificationError = null
        verificationEmailResent = false
        isRegisterMode = false
        error = "Сессия завершилась. Войдите снова, чтобы продолжить."
        step = AuthStep.AUTH
    }

    fun backToLogin() {
        if (isLoading || emailVerified) return
        auth.signOut()
        emailVerified = false
        verificationError = null
        verificationEmailResent = false
        error = null
        step = AuthStep.AUTH
    }

    private suspend fun sendVerificationEmail(resend: Boolean = false) {
        val uid = auth.uid ?: return
        if (SystemClock.elapsedRealtime() < resendDeadline || emailVerified) return
        try {
            val functions = FirebaseFunctions.getInstance()
            val endpoint = BuildConfig.VERIFICATION_ENDPOINT
            val callable = if (endpoint.isEmpty()) {
                functions.getHttpsCallable("sendVerificationEmail")
            } else {
                functions.getHttpsCallableFromUrl(URL(endpoint))
            }
            val result = callable.call().await()
            if (auth.uid != uid || step != AuthStep.EMAIL_VERIFICATION || emailVerified) return
            val alreadyVerified = (result.data as? Map<*, *>)?.get("alreadyVerified") == true
            if (!alreadyVerified) deferResend(uid, result.data)
            verificationEmailResent = resend && !alreadyVerified
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: FirebaseFunctionsException) {
            if (auth.uid != uid || step != AuthStep.EMAIL_VERIFICATION || emailVerified) return
            val sendFailure = "Не удалось отправить письмо для подтверждения."
            error = when (failure.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Войдите в аккаунт заново."
                else -> {
                    deferResend(uid, failure.details)
                    failure.userMessage(sendFailure)
                }
            }
        } catch (failure: Exception) {
            if (auth.uid == uid && step == AuthStep.EMAIL_VERIFICATION && !emailVerified) {
                deferResend(uid, null)
                error = failure.userMessage("Не удалось отправить письмо для подтверждения.")
            }
        }
    }

    fun resendVerificationEmail() {
        if (step != AuthStep.EMAIL_VERIFICATION || isLoading || emailVerified ||
            SystemClock.elapsedRealtime() < resendDeadline
        ) return
        isLoading = true
        error = null
        verificationEmailResent = false
        viewModelScope.launch {
            try {
                sendVerificationEmail(resend = true)
            } finally {
                isLoading = false
            }
        }
    }

    fun saveInitialProfile(skip: Boolean, onSuccess: () -> Unit) {
        if (isLoading) return
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
                        "role" to "athlete"
                    )
                } else {
                    hashMapOf(
                        "nickname" to nickname.trim().ifBlank { email.substringBefore("@").ifBlank { "Пользователь" } },
                        "username" to username.trim().filter { char -> char.isLetterOrDigit() || char == '_' },
                        "dobEpochDay" to dobEpochDay,
                        "avatarBase64" to "",
                        "role" to "athlete"
                    )
                }
                val batch = db.batch()
                batch.set(db.collection("profiles").document(uid), data)
                auth.currentUser?.email?.let { batch.set(db.collection("accounts").document(uid), mapOf("email" to it)) }
                batch.commit().await()
                clearCredentials()
                onSuccess()
            } catch (e: Exception) {
                error = e.userMessage("Ошибка сохранения профиля.")
            } finally {
                isLoading = false
            }
        }
    }

    suspend fun signInWithGoogleCredential(idToken: String, onSuccess: () -> Unit) {
        isLoading = true
        error = null
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            checkUserStatus(onSuccess = onSuccess)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage("Ошибка авторизации Google.")
        } finally {
            isLoading = false
        }
    }
}

@Composable
internal fun AuthScreen(
    showLogo: Boolean,
    onLoginLogoPositioned: (Rect) -> Unit,
    onAuthTransition: (Rect?, suspend () -> Unit) -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val vm = appViewModel {
        AuthViewModel(context.applicationContext.getSharedPreferences("verification_resend", 0))
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnAuthSuccess by rememberUpdatedState(onAuthSuccess)
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val overlayActive = LocalSplashActive.current
    var movingLogo by remember { mutableStateOf(false) }
    var logoBounds by remember { mutableStateOf<Rect?>(null) }
    var choosingGoogleAccount by remember { mutableStateOf(false) }
    var showPasswordResetRequest by rememberSaveable { mutableStateOf(false) }

    if (showPasswordResetRequest && vm.step == AuthStep.AUTH) {
        PasswordResetRequestDialog(vm.email) { showPasswordResetRequest = false }
    }

    fun moveLogo(action: suspend () -> Unit) {
        if (movingLogo) return
        focusManager.clearFocus(force = true)
        movingLogo = true
        onAuthTransition(logoBounds, action)
    }

    LaunchedEffect(overlayActive) {
        if (!overlayActive) movingLogo = false
    }

    val trackLogo = Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        if (bounds.width > 0f && bounds.height > 0f) {
            logoBounds = bounds
            if (vm.step == AuthStep.AUTH || vm.step == AuthStep.GOOGLE_PASSWORD) {
                onLoginLogoPositioned(bounds)
            }
        }
    }.alpha(if (showLogo && !movingLogo) 1f else 0f)

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        choosingGoogleAccount = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { token ->
                scope.launch {
                    vm.signInWithGoogleCredential(token) { moveLogo { currentOnAuthSuccess() } }
                }
            } ?: run {
                vm.error = "Google не подтвердил вход. Попробуйте войти ещё раз."
            }
        } catch (e: ApiException) {
            vm.error = when (e.statusCode) {
                CommonStatusCodes.CANCELED, GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> null
                CommonStatusCodes.NETWORK_ERROR -> "Не удалось войти через Google. Проверьте подключение к интернету."
                else -> "Не удалось войти через Google. Повторите попытку или войдите по электронной почте."
            }
        }
    }

    fun launchGoogleSignIn() {
        val webClientId = "800963952423-ji6die6dk2qgaskefhnjomverqnh8hdk.apps.googleusercontent.com"
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        if (choosingGoogleAccount) return
        choosingGoogleAccount = true
        vm.error = null
        scope.launch {
            try {
                // Clear Google's remembered selection so the picker offers every account again.
                googleSignInClient.signOut().await()
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                choosingGoogleAccount = false
                vm.error = e.userMessage("Не удалось открыть выбор аккаунта Google.")
            }
        }
    }

    val accountUid = (context.applicationContext as DenTrainApp).container.uid
    LaunchedEffect(vm, accountUid) {
        if (!movingLogo) vm.restoreSession { currentOnAuthSuccess() }
    }
    var returningToAuth by remember { mutableStateOf(false) }
    val beginReturn = {
        if (!vm.isLoading && !vm.emailVerified && vm.step == AuthStep.EMAIL_VERIFICATION) {
            returningToAuth = true
        }
    }
    if (vm.step == AuthStep.EMAIL_VERIFICATION || vm.step == AuthStep.REGISTRATION_COMPLETE) {
        BackHandler(onBack = beginReturn)
        LaunchedEffect(returningToAuth, vm.emailVerified) {
            if (returningToAuth && !vm.emailVerified) {
                delay(AuthLogoTransitionMillis.toLong() + 32L)
                vm.backToLogin()
            }
        }
    }
    if (vm.step == AuthStep.EMAIL_VERIFICATION) {
        LaunchedEffect(vm, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.awaitEmailVerification { currentOnAuthSuccess() }
            }
        }
    }
    if (vm.step == AuthStep.GOOGLE_PASSWORD) {
        BackHandler(onBack = vm::cancelGooglePassword)
    }

    when (vm.step) {
        AuthStep.AUTH, AuthStep.GOOGLE_PASSWORD -> {
            val creatingGooglePassword = vm.step == AuthStep.GOOGLE_PASSWORD
            LoginScreen(
                emailValue = vm.email,
                onEmailChange = { vm.email = it.trim() },
                passwordValue = vm.password,
                onPasswordChange = { vm.password = it },
                isRegisterMode = creatingGooglePassword || vm.isRegisterMode,
                confirmPasswordValue = vm.confirmPassword,
                onConfirmPasswordChange = { vm.confirmPassword = it },
                onModeChange = {
                    vm.isRegisterMode = !vm.isRegisterMode
                    vm.error = null
                },
                isLoading = vm.isLoading || choosingGoogleAccount,
                error = vm.error,
                onLoginClick = {
                    returningToAuth = false
                    val finish = {
                        moveLogo { currentOnAuthSuccess() }
                    }
                    if (creatingGooglePassword) vm.saveGooglePassword(finish)
                    else vm.authenticate(finish)
                },
                onGoogleClick = {
                    returningToAuth = false
                    launchGoogleSignIn()
                },
                onForgotPassword = { showPasswordResetRequest = true },
                logoModifier = trackLogo,
                animateReturn = returningToAuth || !showLogo,
                revealForm = showLogo,
                onCancelGooglePassword = if (creatingGooglePassword) vm::cancelGooglePassword else null
            )
        }

        AuthStep.EMAIL_VERIFICATION, AuthStep.REGISTRATION_COMPLETE -> EmailVerificationScreen(
            email = vm.email,
            verified = vm.emailVerified,
            sendingEmail = vm.isLoading,
            emailResent = vm.verificationEmailResent,
            verificationError = vm.verificationError,
            sendError = vm.error,
            resendDeadline = vm.resendDeadline,
            complete = vm.step == AuthStep.REGISTRATION_COMPLETE,
            onFinished = { vm.finishRegistration { moveLogo { currentOnAuthSuccess() } } },
            logoModifier = trackLogo,
            onResend = { if (!returningToAuth) vm.resendVerificationEmail() },
            returning = returningToAuth && !vm.emailVerified,
            onBack = beginReturn
        )

        AuthStep.PROFILE_SETUP -> InitialProfileScreen(vm, trackLogo) {
            moveLogo { currentOnAuthSuccess() }
        }
    }
}

@Composable
private fun InitialProfileScreen(vm: AuthViewModel, logoModifier: Modifier, onSuccess: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(24.dp)

    AuthFlowLayout(showContent = entered, logoModifier = logoModifier) {
        Column(
            Modifier.fillMaxWidth()
                .background(AuthPanel.copy(alpha = 0.88f), shape)
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AuthFlowTitle("Настройте\n", "профиль")
            Text(
                "Данные можно добавить позже.",
                color = AuthSecondaryText,
                style = MaterialTheme.typography.bodyMedium
            )
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = accent,
                unfocusedLabelColor = AuthSecondaryText,
                focusedBorderColor = accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                cursorColor = accent
            )
            OutlinedTextField(
                value = vm.nickname,
                onValueChange = { vm.nickname = it },
                label = { Text("Имя или никнейм") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = !vm.isLoading,
                colors = fieldColors
            )
            OutlinedTextField(
                value = vm.username,
                onValueChange = { input -> vm.username = input.filter { it.isLetterOrDigit() || it == '_' } },
                label = { Text("Имя пользователя") },
                prefix = { Text("@", color = AuthSecondaryText) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = !vm.isLoading,
                colors = fieldColors
            )
            DateField(
                epochDay = vm.dobEpochDay,
                onChange = { vm.dobEpochDay = it },
                label = "Дата рождения",
                modifier = Modifier.fillMaxWidth()
            )
            ErrorText(vm.error, Modifier.fillMaxWidth())
            Button(
                onClick = { vm.saveInitialProfile(skip = false, onSuccess = onSuccess) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                enabled = !vm.isLoading,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (vm.isLoading) "Сохраняем…" else "Сохранить и продолжить", fontWeight = FontWeight.Bold)
            }
            TextButton(
                onClick = { vm.saveInitialProfile(skip = true, onSuccess = onSuccess) },
                enabled = !vm.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Пропустить", color = AuthSecondaryText)
            }
        }
    }
}
