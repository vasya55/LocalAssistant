package com.localassistant.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.localassistant.app.command.CommandExecutor
import com.localassistant.app.command.ExecutionResult
import com.localassistant.app.command.LlmCommand
import com.localassistant.app.databinding.ActivityMainBinding
import com.localassistant.app.llm.LlmEngine
import com.localassistant.app.speech.SpeechManager
import com.localassistant.app.tts.TtsManager
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var executor: CommandExecutor
    private lateinit var tts: TtsManager
    private var llmEngine: LlmEngine? = null
    private var speechManager: SpeechManager? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* результат обрабатывается по факту вызова конкретных действий */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        executor = CommandExecutor(this)
        tts = TtsManager(this)

        requestNeededPermissions()

        // Модель распаковывается из assets/models/gemma2b_q4.task при первом запуске
        // (см. README — файл модели нужно скачать один раз при сборке,
        // дальнейшая работа приложения полностью офлайн)
        val modelFile = File(filesDir, "models/gemma2b_q4.task")
        if (modelFile.exists()) {
            llmEngine = LlmEngine(this, modelFile.absolutePath)
        }

        binding.micButton.setOnClickListener { startListening() }
    }

    private fun requestNeededPermissions() {
        val needed = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startListening() {
        speechManager = SpeechManager(
            context = this,
            onResult = { text -> handleUserText(text) },
            onError = { err -> binding.statusText.text = err }
        ).also { it.start() }
    }

    private fun handleUserText(userText: String) {
        binding.statusText.text = "Вы сказали: $userText"
        val engine = llmEngine ?: run {
            binding.statusText.text = "Модель не загружена. См. README для установки."
            return
        }

        lifecycleScope.launch {
            val command = engine.parseUserRequest(userText)
            runCommand(command)
        }
    }

    private fun runCommand(command: LlmCommand, confirmed: Boolean = false) {
        when (val result = executor.execute(command, confirmed)) {
            is ExecutionResult.Success -> {
                binding.statusText.text = result.message
                tts.speak(result.message)
            }
            is ExecutionResult.Failure -> {
                binding.statusText.text = result.reason
                tts.speak(result.reason)
            }
            is ExecutionResult.NeedsConfirmation -> {
                AlertDialog.Builder(this)
                    .setTitle("Подтвердите действие")
                    .setMessage(result.prompt)
                    .setPositiveButton("Да") { _, _ -> runCommand(result.command, confirmed = true) }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }
    }

    override fun onDestroy() {
        speechManager?.stop()
        llmEngine?.close()
        tts.shutdown()
        super.onDestroy()
    }
}
