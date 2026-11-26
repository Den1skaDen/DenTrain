package com.dentrain.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import com.dentrain.ui.icons.filled.Visibility
import com.dentrain.ui.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dentrain.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    emailValue: String,
    onEmailChange: (String) -> Unit,
    passwordValue: String,
    onPasswordChange: (String) -> Unit,
    isRegisterMode: Boolean,
    confirmPasswordValue: String,
    onConfirmPasswordChange: (String) -> Unit,
    onModeChange: () -> Unit,
    isLoading: Boolean,
    error: String?,
    onLoginClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onForgotPassword: () -> Unit,
    logoModifier: Modifier = Modifier,
    animateReturn: Boolean = false,
    revealForm: Boolean = true,
    onCancelGooglePassword: (() -> Unit)? = null
) {
    val creatingGooglePassword = onCancelGooglePassword != null
    var policyAcknowledged by rememberSaveable { mutableStateOf(false) }
    var dataConsent by rememberSaveable { mutableStateOf(false) }
    var consentAttempt by remember { mutableIntStateOf(0) }
    val consentComplete = policyAcknowledged && dataConsent
    val primaryColor = MaterialTheme.colorScheme.primary
    var sloganLineEnd by remember { mutableFloatStateOf(0f) }
    var passwordVisible by remember(creatingGooglePassword) { mutableStateOf(false) }
    var confirmPasswordVisible by remember(creatingGooglePassword) { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(!animateReturn) }
    LaunchedEffect(revealForm) {
        if (!revealForm) return@LaunchedEffect
        withFrameNanos { }
        showForm = true
    }
    val formTarget = if (showForm) 1f else 0f
    val formAlpha by animateFloatAsState(formTarget, tween(AuthFormEnterMillis), label = "Auth form opacity")
    val formPosition by animateFloatAsState(formTarget, tween(AuthFormEnterMillis), label = "Auth form position")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthBackground)
    ) {
        AuthWallpaper()

        // Container fitting whole screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .systemBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // --- HEADER ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_dt),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .height(52.dp)
                        .padding(bottom = 4.dp)
                        .then(logoModifier),
                    contentScale = ContentScale.Fit
                )

                Text(
                    modifier = Modifier.graphicsLayer { alpha = formAlpha },
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)) {
                            append("Den")
                        }
                        withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 28.sp)) {
                            append("Train")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    modifier = Modifier.graphicsLayer { alpha = formAlpha },
                    text = "ТВОИ ТРЕНИРОВКИ\nВ ОДНОМ ПРИЛОЖЕНИИ",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    lineHeight = 16.sp,
                    onTextLayout = { layout ->
                        sloganLineEnd = layout.getLineRight(layout.lineCount - 1)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .graphicsLayer { alpha = formAlpha }
                        .width(with(LocalDensity.current) { sloganLineEnd.toDp() })
                        .height(3.dp)
                        .background(primaryColor, RoundedCornerShape(1.5.dp))
                )
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                    alpha = formAlpha
                    translationY = size.height / 12f * (1f - formPosition)
                },
                contentAlignment = Alignment.Center
            ) {
                // --- GLASS CARD ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF15181C).copy(alpha = 0.85f))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(
                            targetState = isRegisterMode,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = {
                                fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(150))
                            },
                            label = "Auth title"
                        ) { registerMode ->
                            Text(text = buildAnnotatedString {
                                withStyle(style = SpanStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)) {
                                    append(if (creatingGooglePassword) "Создайте " else if (registerMode) "Создание " else "Вход в ")
                                }
                                withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 22.sp)) {
                                    append(if (creatingGooglePassword) "пароль" else if (registerMode) "аккаунта" else "аккаунт")
                                }
                            })
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (creatingGooglePassword) "С этим паролем вы сможете входить по электронной почте."
                            else "Начните свой путь к сильной версии себя",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // TextField: Email
                        OutlinedTextField(
                            value = emailValue,
                            onValueChange = onEmailChange,
                            readOnly = creatingGooglePassword,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Email", color = Color.Gray) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email Icon",
                                    tint = Color.Gray
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedContainerColor = Color(0xFF1A1D24),
                                unfocusedContainerColor = Color(0xFF1A1D24),
                                cursorColor = primaryColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // TextField: Password
                        OutlinedTextField(
                            value = passwordValue,
                            onValueChange = onPasswordChange,
                            enabled = !creatingGooglePassword || !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Пароль", color = Color.Gray) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Icon",
                                    tint = Color.Gray
                                )
                            },
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = Color.Gray)
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedContainerColor = Color(0xFF1A1D24),
                                unfocusedContainerColor = Color(0xFF1A1D24),
                                cursorColor = primaryColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true
                        )

                        AnimatedContent(
                            targetState = isRegisterMode,
                            modifier = Modifier.fillMaxWidth().height(68.dp),
                            contentAlignment = Alignment.CenterEnd,
                            transitionSpec = {
                                fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(150))
                            },
                            label = "Password options"
                        ) { registerMode ->
                            if (registerMode) {
                            OutlinedTextField(
                                value = confirmPasswordValue,
                                enabled = isRegisterMode && (!creatingGooglePassword || !isLoading),
                                onValueChange = onConfirmPasswordChange,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                placeholder = { Text("Повторите пароль", color = Color.Gray) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Lock Icon",
                                        tint = Color.Gray
                                    )
                                },
                                trailingIcon = {
                                    val image = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Icon(imageVector = image, contentDescription = "Toggle confirm password visibility", tint = Color.Gray)
                                    }
                                },
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryColor,
                                    unfocusedBorderColor = Color(0xFF333333),
                                    focusedContainerColor = Color(0xFF1A1D24),
                                    unfocusedContainerColor = Color(0xFF1A1D24),
                                    cursorColor = primaryColor,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true
                            )
                            } else {
                            TextButton(
                                onClick = onForgotPassword,
                                enabled = !isLoading && !isRegisterMode,
                                modifier = Modifier.wrapContentWidth(Alignment.End)
                            ) {
                                Text("Забыли пароль?")
                            }
                            }
                        }

                        if (!creatingGooglePassword && (isRegisterMode || consentAttempt > 0)) {
                            ConsentCheckbox(
                                "Политика конфиденциальности прочитана",
                                policyAcknowledged, { policyAcknowledged = it }, consentAttempt, !isLoading
                            )
                            ConsentCheckbox(
                                "Согласен на обработку персональных данных",
                                dataConsent, { dataConsent = it }, consentAttempt, !isLoading
                            )
                        }

                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isLoading) {
                            CircularProgressIndicator(color = primaryColor)
                        } else {
                            // Войти Button
                            Button(
                                onClick = {
                                    if (isRegisterMode && !creatingGooglePassword && !consentComplete) {
                                        consentAttempt++
                                    } else {
                                        onLoginClick()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) {
                                AnimatedContent(
                                    targetState = isRegisterMode,
                                    transitionSpec = {
                                        fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(150))
                                    },
                                    label = "Auth button text"
                                ) { registerMode -> Text(
                                    text = if (creatingGooglePassword) "Продолжить" else if (registerMode) "Зарегистрироваться" else "Войти",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ) }
                            }
                        }

                        if (onCancelGooglePassword != null) {
                            TextButton(onClick = onCancelGooglePassword, enabled = !isLoading) {
                                Text("Вернуться ко входу")
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Divider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
                                Text(
                                    text = "Или войдите через",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Google Button
                            Surface(
                                onClick = {
                                    if (consentComplete) onGoogleClick() else consentAttempt++
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Transparent,
                                contentColor = Color.White,
                                border = BorderStroke(1.dp, Color(0xFF333333))
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "G",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Text("Google", fontSize = 15.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Sign Up Text
                            AnimatedContent(
                                targetState = isRegisterMode,
                                transitionSpec = {
                                    fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(150))
                                },
                                label = "Auth mode switch"
                            ) { registerMode -> Row {
                                Text(text = if (registerMode) "Уже есть аккаунт? " else "Нет аккаунта? ", color = Color.Gray, fontSize = 13.sp)
                                Text(
                                    text = if (registerMode) "Войти" else "Создать аккаунт",
                                    color = primaryColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onModeChange() }
                                )
                            } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    attempt: Int,
    enabled: Boolean
) {
    val showError = attempt > 0 && !checked
    val errorColor = MaterialTheme.colorScheme.error
    val sweep = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(if (checked) 1f else 0f) }
    val fillProgress = remember { Animatable(if (checked) 1f else 0f) }
    val checkedColor = Color(0xFF51D88A)
    LaunchedEffect(checked) {
        if (checked) {
            checkProgress.animateTo(1f, tween(220, easing = LinearEasing))
            fillProgress.animateTo(1f, tween(160))
        } else {
            checkProgress.snapTo(0f)
            fillProgress.snapTo(0f)
        }
    }
    LaunchedEffect(attempt) {
        if (showError) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(500, easing = LinearEasing))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(22.dp)
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    val inset = stroke / 2
                    val contour = Path().apply {
                        addRoundRect(RoundRect(
                            Rect(Offset(inset, inset), Offset(size.width - inset, size.height - inset)),
                            CornerRadius(5.dp.toPx() - inset)
                        ))
                        close()
                    }
                    val outline = Stroke(stroke, cap = StrokeCap.Round)
                    if (checked) {
                        val fill = fillProgress.value
                        drawRoundRect(checkedColor.copy(alpha = fill), cornerRadius = CornerRadius(5.dp.toPx()))
                        drawPath(contour, lerp(Color.Gray, checkedColor, fill), style = outline)
                        val check = Path().apply {
                            moveTo(size.width * .24f, size.height * .5f)
                            lineTo(size.width * .43f, size.height * .68f)
                            lineTo(size.width * .76f, size.height * .33f)
                        }
                        val measure = PathMeasure().apply { setPath(check, false) }
                        val segment = Path()
                        measure.getSegment(0f, measure.length * checkProgress.value, segment)
                        drawPath(segment, lerp(Color.White, Color.Black, fill), style = outline)
                    } else {
                        val progress = if (showError) sweep.value else 0f
                        if (progress >= 1f) {
                            drawPath(contour, errorColor, style = outline)
                        } else {
                            drawPath(contour, Color.Gray, style = outline)
                            if (progress > 0f) {
                                val measure = PathMeasure().apply { setPath(contour, true) }
                                val segment = Path()
                                measure.getSegment(0f, measure.length * progress, segment)
                                drawPath(segment, errorColor, style = outline)
                            }
                        }
                    }
                }
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            color = if (showError) errorColor else Color.White,
            fontSize = 11.sp
        )
    }
}
