package com.dentrain.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object EmailSender {

    private const val TAG = "EmailSender"

    /**
     * Формирует премиальное тёмное HTML-письмо с логотипом DenTrain и 6-значным кодом
     */
    fun buildVerificationHtml(code: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0b0f19; margin: 0; padding: 24px 12px; color: #f1f5f9; }
                .card { max-width: 460px; margin: 0 auto; background: #1e293b; border-radius: 20px; padding: 36px 28px; border: 1px solid #334155; text-align: center; box-shadow: 0 20px 40px rgba(0,0,0,0.6); }
                .badge { display: inline-block; background: rgba(56, 189, 248, 0.15); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.3); font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 1.5px; padding: 6px 16px; border-radius: 20px; margin-bottom: 20px; }
                .logo-text { font-size: 34px; font-weight: 900; color: #38bdf8; letter-spacing: 1.5px; margin-bottom: 4px; }
                .subtitle { font-size: 13px; color: #94a3b8; font-weight: 500; margin-bottom: 28px; }
                .h1 { font-size: 20px; font-weight: 700; color: #ffffff; margin-bottom: 12px; }
                .p { font-size: 14px; color: #cbd5e1; line-height: 1.6; margin-bottom: 20px; }
                .code-container { background: #090d16; border: 2px dashed #38bdf8; border-radius: 14px; padding: 20px 16px; margin: 20px 0 24px 0; }
                .code-val { font-family: 'Courier New', Courier, monospace; font-size: 42px; font-weight: 900; letter-spacing: 12px; color: #38bdf8; }
                .code-sub { font-size: 12px; color: #64748b; margin-top: 8px; }
                .footer { border-top: 1px solid #334155; padding-top: 20px; font-size: 12px; color: #64748b; line-height: 1.6; margin-top: 24px; }
                .brand-name { color: #38bdf8; font-weight: 700; }
              </style>
            </head>
            <body>
              <div class="card">
                <div class="badge">Подтверждение почты</div>
                <div class="logo-text">DenTrain</div>
                <div class="subtitle">Персональный дневник тренировок</div>
                <div class="h1">Код подтверждения регистрации</div>
                <div class="p">Здравствуйте!<br>Для завершения регистрации введите этот 6-значный код в приложении:</div>
                <div class="code-container">
                  <div class="code-val">$code</div>
                  <div class="code-sub">Скопируйте код для авто-вставки в приложении</div>
                </div>
                <div class="p" style="font-size: 12px; color: #94a3b8;">Код действителен 10 минут. Никому не передавайте этот код.</div>
                <div class="footer">
                  С уважением,<br>
                  <span class="brand-name">Команда DenTrain</span>
                </div>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Отправляет премиальное HTML-письмо с высокой репутацией во «Входящие»
     */
    suspend fun sendVerificationCode(email: String, code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val htmlContent = buildVerificationHtml(code)
            val apiKey = "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"
            val url = URL("https://api.brevo.com/v3/smtp/email")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("accept", "application/json")
            connection.setRequestProperty("api-key", apiKey)
            connection.doOutput = true

            val jsonBody = JSONObject().apply {
                put("sender", JSONObject().apply {
                    put("name", "Команда DenTrain")
                    put("email", "support@dentrain.app")
                })
                put("to", JSONArray().put(JSONObject().apply {
                    put("email", email)
                }))
                put("subject", "Ваш код подтверждения DenTrain: $code")
                put("htmlContent", htmlContent)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                Log.d(TAG, "Email successfully sent to $email")
                return@withContext true
            } else {
                Log.w(TAG, "Brevo API returned HTTP status $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email: ${e.localizedMessage}")
        }
        return@withContext true
    }
}
