package com.localassistant.app.command

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.telephony.SmsManager
import com.localassistant.app.accessibility.AssistantAccessibilityService

sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class NeedsConfirmation(val command: LlmCommand, val prompt: String) : ExecutionResult()
    data class Failure(val reason: String) : ExecutionResult()
}

class CommandExecutor(private val context: Context) {

    /**
     * Единая точка входа. Сначала проверяем, требует ли действие
     * подтверждения пользователя (звонок, SMS) — модель не может
     * обойти это, т.к. проверка идёт по типу действия, а не по её словам.
     */
    fun execute(command: LlmCommand, confirmed: Boolean = false): ExecutionResult {
        val type = ActionType.fromKey(command.action)

        if (type.requiresConfirmation && !confirmed) {
            return ExecutionResult.NeedsConfirmation(
                command,
                prompt = "Подтвердите: ${command.reasoning.ifBlank { command.action }}"
            )
        }

        return try {
            when (type) {
                ActionType.OPEN_APP -> openApp(command.params["package_name"] ?: command.params["app_name"].orEmpty())
                ActionType.CALL_CONTACT -> callNumber(command.params["number"].orEmpty())
                ActionType.SEND_SMS -> sendSms(command.params["number"].orEmpty(), command.params["text"].orEmpty())
                ActionType.SET_ALARM -> setAlarm(
                    command.params["hour"]?.toIntOrNull() ?: 0,
                    command.params["minute"]?.toIntOrNull() ?: 0
                )
                ActionType.GO_HOME -> goHome()

                // Действия, требующие доступа к текущему экрану,
                // делегируются в работающий AccessibilityService
                ActionType.CLICK_ELEMENT -> delegateToAccessibility(command)
                ActionType.TYPE_TEXT -> delegateToAccessibility(command)
                ActionType.SCROLL -> delegateToAccessibility(command)
                ActionType.READ_SCREEN -> delegateToAccessibility(command)
                ActionType.GO_BACK -> delegateToAccessibility(command)

                else -> ExecutionResult.Failure("Неизвестное действие: ${command.action}")
            }
        } catch (e: SecurityException) {
            ExecutionResult.Failure("Нет разрешения для действия: ${type.key}")
        } catch (e: Exception) {
            ExecutionResult.Failure("Ошибка выполнения: ${e.message}")
        }
    }

    private fun openApp(identifier: String): ExecutionResult {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(identifier)
            ?: findPackageByLabel(identifier)?.let { pm.getLaunchIntentForPackage(it) }
            ?: return ExecutionResult.Failure("Приложение не найдено: $identifier")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return ExecutionResult.Success("Открываю $identifier")
    }

    private fun findPackageByLabel(label: String): String? {
        val pm = context.packageManager
        return pm.getInstalledApplications(0).firstOrNull {
            pm.getApplicationLabel(it).toString().equals(label, ignoreCase = true)
        }?.packageName
    }

    private fun callNumber(number: String): ExecutionResult {
        if (number.isBlank()) return ExecutionResult.Failure("Не указан номер")
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ExecutionResult.Success("Звоню $number")
    }

    private fun sendSms(number: String, text: String): ExecutionResult {
        if (number.isBlank()) return ExecutionResult.Failure("Не указан номер")
        val smsManager = context.getSystemService(SmsManager::class.java)
        smsManager.sendTextMessage(number, null, text, null, null)
        return ExecutionResult.Success("Сообщение отправлено")
    }

    private fun setAlarm(hour: Int, minute: Int): ExecutionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ExecutionResult.Success("Будильник на %02d:%02d поставлен".format(hour, minute))
    }

    private fun goHome(): ExecutionResult {
        context.startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return ExecutionResult.Success("Домой")
    }

    private fun delegateToAccessibility(command: LlmCommand): ExecutionResult {
        val service = AssistantAccessibilityService.instance
            ?: return ExecutionResult.Failure(
                "Специальные возможности не включены. Включите в Настройки → Специальные возможности → Local Assistant"
            )
        return service.handleCommand(command)
    }
}
