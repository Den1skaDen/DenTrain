package com.dentrain.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import com.dentrain.ui.theme.LocalGradient

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
    onGoogleClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
    ) {
        // 1. Background Image
        Image(
            painter = painterResource(id = R.drawable.bg_athlete),
            contentDescription = "Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Light top/bottom gradient to ensure text readability if needed
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.4f),
                            0.4f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )

        // Container fitting whole screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .systemBarsPadding(),
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
                        .padding(bottom = 4.dp),
                    contentScale = ContentScale.Fit
                )
                
                Text(
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
                    text = "ТВОИ ТРЕНИРОВКИ\nВ ОДНОМ ПРИЛОЖЕНИИ",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    lineHeight = 16.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(3.dp)
                        .background(primaryColor, RoundedCornerShape(1.5.dp))
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // --- GLASS CARD ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)) {
                                append(if (isRegisterMode) "Создание " else "Вход в ")
                            }
                            withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 22.sp)) {
                                append("аккаунт")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Продолжите свой путь к сильной версии себя",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // TextField: Email
                    OutlinedTextField(
                        value = emailValue,
                        onValueChange = onEmailChange,
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

                    if (isRegisterMode) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = confirmPasswordValue,
                            onValueChange = onConfirmPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
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
                            onClick = onLoginClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text(
                                text = if (isRegisterMode) "Зарегистрироваться" else "Войти",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

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
                    OutlinedButton(
                        onClick = onGoogleClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFF333333))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Row {
                        Text(text = if (isRegisterMode) "Уже есть аккаунт? " else "Нет аккаунта? ", color = Color.Gray, fontSize = 13.sp)
                        Text(
                            text = if (isRegisterMode) "Войти" else "Создать",
                            color = primaryColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onModeChange() }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))

            // --- FOOTER ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                DumbbellIcon(color = primaryColor)
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    HorizontalDivider(modifier = Modifier.width(40.dp), color = Color(0xFF333333))
                    Text(
                        text = "СТАНЬ СИЛЬНЕЕ",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        letterSpacing = 4.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    HorizontalDivider(modifier = Modifier.width(40.dp), color = Color(0xFF333333))
                }
            }
        }
    }
}

@Composable
fun DumbbellIcon(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(48.dp)
            .height(24.dp)
            .drawBehind {
                val strokeWidth = 3.dp.toPx()
                val cap = StrokeCap.Round
                
                // Центральная ось
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.2f, size.height / 2),
                    end = Offset(size.width * 0.8f, size.height / 2),
                    strokeWidth = strokeWidth,
                    cap = cap
                )
                
                // Внутренние блины
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.35f, size.height * 0.15f),
                    end = Offset(size.width * 0.35f, size.height * 0.85f),
                    strokeWidth = strokeWidth * 1.5f,
                    cap = cap
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.65f, size.height * 0.15f),
                    end = Offset(size.width * 0.65f, size.height * 0.85f),
                    strokeWidth = strokeWidth * 1.5f,
                    cap = cap
                )

                // Внешние блины
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.2f, size.height * 0.3f),
                    end = Offset(size.width * 0.2f, size.height * 0.7f),
                    strokeWidth = strokeWidth * 1.2f,
                    cap = cap
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.8f, size.height * 0.3f),
                    end = Offset(size.width * 0.8f, size.height * 0.7f),
                    strokeWidth = strokeWidth * 1.2f,
                    cap = cap
                )
            }
    )
}
