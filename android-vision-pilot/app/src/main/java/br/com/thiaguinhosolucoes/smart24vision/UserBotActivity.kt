package br.com.thiaguinhosolucoes.smart24vision

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.thiaguinhosolucoes.smart24vision.p2p.GatewayResult
import br.com.thiaguinhosolucoes.smart24vision.p2p.YooseeSdkProbe
import br.com.thiaguinhosolucoes.smart24vision.p2p.YooseeUserBotController
import kotlinx.coroutines.launch

class UserBotActivity : AppCompatActivity() {
    private val controller = YooseeUserBotController()
    private lateinit var status: TextView
    private lateinit var email: EditText
    private lateinit var password: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_bot)

        status = findViewById(R.id.userBotStatus)
        email = findViewById(R.id.yooseeBotEmailInput)
        password = findViewById(R.id.yooseeBotPasswordInput)

        findViewById<Button>(R.id.userBotProbeButton).setOnClickListener {
            val report = YooseeSdkProbe.inspect()
            status.text = report.summary
        }

        findViewById<Button>(R.id.userBotOpenOfficialButton).setOnClickListener {
            openOfficialYoosee()
        }

        findViewById<Button>(R.id.userBotLoginButton).setOnClickListener {
            val account = email.text.toString().trim()
            val secret = password.text.toString().toCharArray()
            password.text.clear()

            lifecycleScope.launch {
                status.text = "Verificando pré-requisitos do P2P…"
                val preflight = controller.preflight()
                if (preflight !is GatewayResult.Ok) {
                    status.text = render(preflight)
                    secret.fill('\u0000')
                    return@launch
                }

                status.text = "Tentando login da conta SMART24 no gateway P2P…"
                status.text = render(controller.login(account, secret))
            }
        }

        findViewById<Button>(R.id.userBotListDevicesButton).setOnClickListener {
            lifecycleScope.launch {
                val result = controller.devices()
                status.text = buildString {
                    appendLine(render(result.result))
                    if (result.devices.isNotEmpty()) {
                        appendLine()
                        appendLine("Câmeras autorizadas:")
                        result.devices.forEach {
                            appendLine("• ${it.name} (${it.deviceId}) — ${if (it.online) "online" else "offline"}")
                        }
                    }
                }.trim()
            }
        }
    }

    override fun onDestroy() {
        password.text?.clear()
        super.onDestroy()
    }

    private fun openOfficialYoosee() {
        val intent = packageManager.getLaunchIntentForPackage("com.yoosee")
        if (intent == null) {
            status.text = "Yoosee oficial não foi encontrado neste aparelho."
            return
        }
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }.onSuccess {
            status.text = "Yoosee aberto. Entre com a conta exclusiva do SMART24 e confirme manualmente que a câmera compartilhada aparece. Isso valida a conta; não valida ainda o nosso SDK."
        }.onFailure {
            status.text = "Falha ao abrir Yoosee: ${it.message ?: "erro desconhecido"}"
        }
    }

    private fun render(result: GatewayResult): String = when (result) {
        is GatewayResult.Ok -> "OK: ${result.message}"
        is GatewayResult.Blocked -> "BLOQUEADO [${result.code}]\n${result.message}"
        is GatewayResult.Error -> "ERRO [${result.code}]\n${result.message}"
    }
}
