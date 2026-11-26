package com.dentrain.ui.auth

import com.dentrain.util.UserFacingException
import com.google.android.gms.common.util.AndroidUtilsLight
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal suspend fun linkGooglePassword(auth: FirebaseAuth, user: FirebaseUser, password: String) {
    user.reload().await()
    val currentUser = checkNotNull(auth.currentUser?.takeIf { it.uid == user.uid })
    val email = currentUser.email
        ?: throw UserFacingException("В аккаунте Google нет адреса электронной почты.")
    if (currentUser.needsGooglePassword()) {
        val token = checkNotNull(currentUser.getIdToken(false).await().token)
        val app = auth.app
        val apiKey = app.options.apiKey
        withContext(Dispatchers.IO) {
            val context = app.applicationContext
            val packageName = context.packageName
            val certificate = AndroidUtilsLight.getPackageCertificateHashBytes(context, packageName)
            // accounts:signUp with an ID token links credentials even with email enumeration protection.
            val connection = URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey")
                .openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("X-Android-Package", packageName)
                if (certificate != null) {
                    connection.setRequestProperty("X-Android-Cert", certificate.joinToString("") { "%02X".format(it) })
                }
                val request = JSONObject()
                    .put("idToken", token)
                    .put("email", email)
                    .put("password", password)
                    .put("returnSecureToken", true)
                connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) {
                    val body = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    val code = runCatching {
                        body?.let { JSONObject(it).optJSONObject("error")?.optString("message") }
                    }.getOrNull()?.substringBefore(" : ")
                    throw when (code) {
                        "CREDENTIAL_TOO_OLD_LOGIN_AGAIN", "INVALID_ID_TOKEN", "TOKEN_EXPIRED" ->
                            FirebaseAuthRecentLoginRequiredException("ERROR_REQUIRES_RECENT_LOGIN", "Google sign-in expired")
                        "WEAK_PASSWORD", "PASSWORD_DOES_NOT_MEET_REQUIREMENTS" ->
                            UserFacingException("Пароль не соответствует требованиям безопасности.")
                        "EMAIL_EXISTS" -> UserFacingException("Этот адрес уже используется.")
                        else -> UserFacingException("Не удалось сохранить пароль. Попробуйте ещё раз.")
                    }
                }
                val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                check(response.getString("localId") == user.uid)
            } finally {
                connection.disconnect()
            }
        }
    }
    check(auth.uid == user.uid)
    val signedInUser = auth.signInWithEmailAndPassword(email, password).await().user
    if (signedInUser?.uid != user.uid) {
        auth.signOut()
        error("Password sign-in returned a different Firebase account")
    }
}
