package com.localassistant.app.command

import kotlinx.serialization.Serializable

/**
 * Закрытый набор действий, которые может запросить модель.
 * Ключевая идея безопасности: LLM никогда не выполняет код и не получает
 * прямого доступа к API Android — она только выбирает одно из этих действий
 * и заполняет параметры. Executor валидирует и выполняет.
 */
@Serializable
data class LlmCommand(
    val action: String,
    val params: Map<String, String> = emptyMap(),
    // Модель обязана объяснить, что собирается сделать —
    // это показывается пользователю перед выполнением чувствительных действий
    val reasoning: String = ""
)

enum class ActionType(val key: String, val requiresConfirmation: Boolean) {
    OPEN_APP("open_app", requiresConfirmation = false),
    CALL_CONTACT("call_contact", requiresConfirmation = true),
    SEND_SMS("send_sms", requiresConfirmation = true),
    SET_ALARM("set_alarm", requiresConfirmation = false),
    TOGGLE_SETTING("toggle_setting", requiresConfirmation = false),
    READ_SCREEN("read_screen", requiresConfirmation = false),
    CLICK_ELEMENT("click_element", requiresConfirmation = false),
    TYPE_TEXT("type_text", requiresConfirmation = false),
    SCROLL("scroll", requiresConfirmation = false),
    GO_BACK("go_back", requiresConfirmation = false),
    GO_HOME("go_home", requiresConfirmation = false),
    UNKNOWN("unknown", requiresConfirmation = false);

    companion object {
        fun fromKey(key: String) = entries.find { it.key == key } ?: UNKNOWN
    }
}
