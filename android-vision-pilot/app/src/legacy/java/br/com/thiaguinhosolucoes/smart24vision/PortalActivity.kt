package br.com.thiaguinhosolucoes.smart24vision

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Tela principal do APK completo.
 *
 * A loja 3D, os produtos, as etiquetas e os eventos continuam sendo mantidos
 * pelo mesmo painel do GitHub Pages. Esta Activity o exibe dentro do SMART24 e
 * mantém o Vision Pilot como um módulo nativo separado.
 */
class PortalActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var connectionText: TextView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermission: PermissionRequest? = null

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val selected = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileCallback?.onReceiveValue(selected)
        fileCallback = null
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = pendingWebPermission
        pendingWebPermission = null
        if (request == null) return@registerForActivityResult
        if (granted && isTrustedOrigin(request.origin)) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        } else {
            request.deny()
            Toast.makeText(this, "A câmera não foi autorizada para leitura de QR.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_portal)
        webView = findViewById(R.id.smart24WebView)
        progress = findViewById(R.id.portalProgress)
        connectionText = findViewById(R.id.portalConnectionText)

        findViewById<Button>(R.id.openVisionButton).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<Button>(R.id.reloadPortalButton).setOnClickListener {
            connectionText.text = "Atualizando o SMART24…"
            webView.reload()
        }

        configureWebView()
        if (savedInstanceState == null) {
            webView.loadUrl(BuildConfig.SMART24_WEB_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = true
            setSupportMultipleWindows(false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
                connectionText.text = "Carregando loja, produtos e eventos…"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                connectionText.text = "SMART24 completo • conectado ao GitHub Pages"
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host.equals(PORTAL_HOST, ignoreCase = true)) return false
                return openExternal(uri)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                val intent = runCatching { fileChooserParams?.createIntent() }.getOrNull()
                    ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                return runCatching { fileChooser.launch(intent) }
                    .onFailure {
                        fileCallback?.onReceiveValue(null)
                        fileCallback = null
                        Toast.makeText(this@PortalActivity, "Não foi possível abrir o seletor de imagens.", Toast.LENGTH_LONG).show()
                    }
                    .isSuccess
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { authorizeWebCamera(request) }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                if (pendingWebPermission === request) pendingWebPermission = null
            }
        }
    }

    private fun authorizeWebCamera(request: PermissionRequest) {
        val asksForVideo = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        if (!asksForVideo || !isTrustedOrigin(request.origin)) {
            request.deny()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            return
        }
        pendingWebPermission?.deny()
        pendingWebPermission = request
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun isTrustedOrigin(uri: Uri): Boolean =
        uri.scheme == "https" && uri.host.equals(PORTAL_HOST, ignoreCase = true)

    private fun openExternal(uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        return if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            true
        } else {
            Toast.makeText(this, "Não há aplicativo para abrir este link.", Toast.LENGTH_LONG).show()
            true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        pendingWebPermission?.deny()
        pendingWebPermission = null
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val PORTAL_HOST = "tsvalencio-ia.github.io"
    }
}
