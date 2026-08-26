package com.localassistant.app.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.localassistant.app.command.LlmCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val SYSTEM_PROMPT = """
Ты — офлайн-ассистент управления Android-телефоном. Ты ВСЕГДА отвечаешь
ТОЛЬКО одним JSON-объектом, без пояснений вне JSON, в формате:
{"action": "<имя_действия>", "params": {...}, "reasoning": "<кратко зачем>"}

Доступные действия:
- open_app {package_name или app_name}
- call_contact {number}
- send_sms {number, text}
- set_alarm {hour, minute}
- click_element {description}  — нажать на элемент экрана по его тексту
- type_text {text}             — ввести текст в активное поле
- scroll {direction: up|down}
- read_screen {}               — прочитать, что сейчас на экране
- go_back {}
- go_home {}

Если запрос неясен или невыполним — action: "unknown".
"""

class LlmEngine(context: Context, modelPath: String) {

    private val llmInference: LlmInference

    init {
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)   // модель лежит локально, например /data/local/.../gemma.task
            .setMaxTokens(512)
            .setTemperature(0.2f)      // низкая температура — нам нужен предсказуемый JSON, не креатив
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
    }

    suspend fun parseUserRequest(userText: String, screenContext: String? = null): LlmCommand =
        withContext(Dispatchers.Default) {
            val prompt = buildString {
                append(SYSTEM_PROMPT)
                if (!screenContext.isNullOrBlank()) {
                    append("\nТекущий экран: ").append(screenContext)
                }
                append("\nЗапрос пользователя: ").append(userText)
                append("\nJSON:")
            }

            val rawResponse = llmInference.generateResponse(prompt)
            parseJsonSafely(rawResponse)
        }

    private fun parseJsonSafely(raw: String): LlmCommand {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            Json { ignoreUnknownKeys = true }.decodeFromString<LlmCommand>(cleaned)
        } catch (e: Exception) {
            LlmCommand(action = "unknown", reasoning = "Не удалось разобрать ответ модели")
        }
    }

    fun close() = llmInference.close()
}
