package com.controleescalas.app.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AtualizacaoRemota(
    val versaoCodigo: Long,
    val versaoNome: String,
    val apkUrl: String,
    val novidades: List<String> = emptyList()
)

object AppUpdateChecker {
    private const val VERSAO_URL =
        "https://raw.githubusercontent.com/michellcosta/controle-de-escalas/main/versao.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun buscar(): AtualizacaoRemota? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(VERSAO_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val corpo = response.body?.string() ?: return@withContext null
                val json = JSONObject(corpo)
                val url = json.optString("apkUrl")
                if (url.isBlank()) return@withContext null
                val novidades = mutableListOf<String>()
                json.optJSONArray("novidades")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val item = arr.optString(i)
                        if (item.isNotBlank()) novidades.add(item)
                    }
                }
                AtualizacaoRemota(
                    versaoCodigo = json.optLong("versaoCodigo"),
                    versaoNome = json.optString("versaoNome"),
                    apkUrl = url,
                    novidades = novidades
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun versaoInstalada(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    fun versaoNomeInstalada(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    suspend fun baixar(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val arquivo = File(context.cacheDir, "atualizacao.apk")
                val corpo = response.body ?: return@withContext null
                val total = corpo.contentLength()
                corpo.byteStream().use { entrada ->
                    FileOutputStream(arquivo).use { saida ->
                        val buffer = ByteArray(64 * 1024)
                        var lidos = 0L
                        var ultimoPct = -1
                        var n: Int
                        while (entrada.read(buffer).also { n = it } != -1) {
                            saida.write(buffer, 0, n)
                            lidos += n
                            if (total > 0) {
                                val pct = (lidos * 100 / total).toInt()
                                if (pct != ultimoPct) {
                                    ultimoPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
                if (arquivo.length() == 0L) null else arquivo
            }
        } catch (_: Exception) {
            null
        }
    }

    fun abrirInstalador(context: Context, arquivo: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            arquivo
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
