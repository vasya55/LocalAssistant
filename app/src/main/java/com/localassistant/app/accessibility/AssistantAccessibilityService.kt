package com.localassistant.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.localassistant.app.command.ExecutionResult
import com.localassistant.app.command.LlmCommand

/**
 * Единственный компонент с доступом к содержимому экрана других приложений.
 * Работает только пока пользователь явно включил его в системных настройках —
 * это системное ограничение Android, обойти его нельзя.
 */
class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AssistantAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* реактивная логика при желании */ }
    override fun onInterrupt() {}

    fun handleCommand(command: LlmCommand): ExecutionResult {
        return when (command.action) {
            "read_screen" -> ExecutionResult.Success(dumpScreenText())
            "click_element" -> clickByDescription(command.params["description"].orEmpty())
            "type_text" -> typeIntoFocused(command.params["text"].orEmpty())
            "scroll" -> performScroll(command.params["direction"] ?: "down")
            "go_back" -> { performGlobalAction(GLOBAL_ACTION_BACK); ExecutionResult.Success("Назад") }
            else -> ExecutionResult.Failure("Неподдерживаемое действие для экрана: ${command.action}")
        }
    }

    /** Собирает весь видимый текст с экрана — это то, что LLM "видит" вместо картинки. */
    private fun dumpScreenText(): String {
        val root = rootInActiveWindow ?: return "Экран недоступен"
        val builder = StringBuilder()
        collectText(root, builder)
        return builder.toString().trim().ifBlank { "На экране нет текста" }
    }

    private fun collectText(node: AccessibilityNodeInfo, builder: StringBuilder) {
        node.text?.let { if (it.isNotBlank()) builder.append(it).append(" | ") }
        node.contentDescription?.let { if (it.isNotBlank()) builder.append(it).append(" | ") }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, builder) }
        }
    }

    /** Ищет элемент по тексту/описанию (то, что назвала модель) и кликает по центру. */
    private fun clickByDescription(description: String): ExecutionResult {
        if (description.isBlank()) return ExecutionResult.Failure("Не указано, что нажать")
        val root = rootInActiveWindow ?: return ExecutionResult.Failure("Экран недоступен")

        val target = findNodeByText(root, description)
            ?: return ExecutionResult.Failure("Элемент \"$description\" не найден на экране")

        // Сначала пробуем нативное действие клика (надёжнее)
        if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return ExecutionResult.Success("Нажал: $description")
        }

        // Фолбэк — жест тапа по координатам элемента
        val bounds = Rect()
        target.getBoundsInScreen(bounds)
        return tapAt(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (text.contains(query, ignoreCase = true) || desc.contains(query, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findNodeByText(child, query)?.let { return it }
            }
        }
        return null
    }

    private fun tapAt(x: Float, y: Float): ExecutionResult {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        val ok = dispatchGesture(gesture, null, null)
        return if (ok) ExecutionResult.Success("Тап по ($x, $y)") else ExecutionResult.Failure("Не удалось выполнить тап")
    }

    private fun typeIntoFocused(text: String): ExecutionResult {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return ExecutionResult.Failure("Нет активного поля ввода")
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) ExecutionResult.Success("Введено: $text") else ExecutionResult.Failure("Не удалось ввести текст")
    }

    private fun performScroll(direction: String): ExecutionResult {
        val root = rootInActiveWindow ?: return ExecutionResult.Failure("Экран недоступен")
        val action = if (direction == "up") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                     else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        val ok = findScrollable(root)?.performAction(action) ?: false
        return if (ok) ExecutionResult.Success("Прокрутил $direction") else ExecutionResult.Failure("Нечего прокручивать")
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> findScrollable(child)?.let { return it } }
        }
        return null
    }
}
