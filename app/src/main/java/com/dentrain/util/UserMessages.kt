package com.dentrain.util

import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.FirebaseNetworkException
import android.database.sqlite.SQLiteFullException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException

internal class UserFacingException(message: String) : IllegalStateException(message)

internal inline fun checkForUser(condition: Boolean, message: () -> String) {
    if (!condition) throw UserFacingException(message())
}

internal fun Throwable.userMessage(fallback: String): String {
    val causes = generateSequence(this) { error -> error.cause?.takeUnless { it === error } }.take(10).toList()
    causes.filterIsInstance<CancellationException>().firstOrNull()?.let { throw it }
    causes.filterIsInstance<UserFacingException>().firstOrNull()?.message?.let { return it }
    val firestore = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    val auth = causes.filterIsInstance<FirebaseAuthException>().firstOrNull()
    val detail = when (firestore?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Нет доступа к этой операции."
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Войдите в аккаунт заново."
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Проверьте подключение к интернету и повторите попытку."
        FirebaseFirestoreException.Code.NOT_FOUND -> "Данные больше недоступны."
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> "Слишком много запросов. Повторите позже."
        FirebaseFirestoreException.Code.ABORTED -> "Данные изменились. Обновите экран и повторите попытку."
        else -> when (auth?.errorCode) {
            "ERROR_INVALID_EMAIL" -> "Проверьте адрес электронной почты."
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" ->
                "Проверьте адрес электронной почты и пароль."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Этот адрес уже используется."
            "ERROR_WEAK_PASSWORD" -> "Пароль слишком простой."
            "ERROR_TOO_MANY_REQUESTS" -> "Слишком много попыток. Повторите позже."
            "ERROR_REQUIRES_RECENT_LOGIN", "ERROR_USER_TOKEN_EXPIRED" -> "Войдите в аккаунт заново."
            "ERROR_USER_DISABLED" -> "Аккаунт отключён."
            else -> when {
                causes.any { it is FirebaseNetworkException } ->
                    "Проверьте подключение к интернету и повторите попытку."
                causes.any { it is FirebaseTooManyRequestsException } ->
                    "Слишком много попыток. Повторите позже."
                causes.any { it is SQLiteFullException } ->
                    "На устройстве недостаточно места. Освободите память и повторите попытку."
                else -> null
            }
        }
    }
    return if (detail == null) fallback else "$fallback $detail"
}
