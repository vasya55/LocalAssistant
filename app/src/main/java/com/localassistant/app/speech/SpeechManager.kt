package com.localassistant.app.speech

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Полностью офлайн STT. Модель Vosk (например, vosk-model-small-ru-0.22,
 * ~45 МБ) распаковывается один раз из assets в приватную папку приложения.
 * Никаких сетевых вызовов на этапе распознавания.
 */
class SpeechManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechService: SpeechService? = null

    fun start() {
        StorageService.unpack(
            context, "model-ru", "model",
            { model: Model -> initRecognizer(model) },
            { exception -> onError("Не удалось загрузить модель распознавания: ${exception.message}") }
        )
    }

    private fun initRecognizer(model: Model) {
        val recognizer = Recognizer(model, 16000.0f)
        speechService = SpeechService(recognizer, 16000.0f).apply {
            startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {}
                override fun onResult(hypothesis: String?) {
                    hypothesis?.let { extractText(it)?.let(onResult) }
                }
                override fun onFinalResult(hypothesis: String?) {
                    hypothesis?.let { extractText(it)?.let(onResult) }
                }
                override fun onError(exception: Exception?) {
                    onError(exception?.message ?: "Ошибка распознавания")
                }
                override fun onTimeout() {}
            })
        }
    }

    private fun extractText(jsonHypothesis: String): String? {
        // Vosk отдаёт {"text": "..."}; простой парсинг без лишних зависимостей
        val regex = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"")
        val match = regex.find(jsonHypothesis)?.groupValues?.get(1)
        return match?.takeIf { it.isNotBlank() }
    }

    fun stop() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }
}
