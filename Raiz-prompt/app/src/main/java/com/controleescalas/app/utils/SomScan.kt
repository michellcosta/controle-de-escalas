package com.controleescalas.app.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/**
 * Som do escaneamento de pacotes — tom de leitor gerado pelo próprio app
 * (mais limpo que o bip padrão do Android).
 *
 * Escala PRÓPRIA do bip: 0 a 10, padrão 4, salvo no aparelho. O canal de
 * áudio é o de ALARME (independente do volume de mídia) e, a cada som, o
 * app aplica o nível escolhido no volume de alarme e devolve o anterior
 * logo depois — então o bip não depende do volume que o celular estava.
 *
 * - sucesso:  bip de leitor (2400Hz + oitava, 150ms)
 * - repetido: tom grave curto (900Hz, 180ms) para pacote já escaneado
 */
object SomScan {

    const val PREFS = "som_escaneamento"
    // Chave nova (a antiga "volume" guardava 0-100 de uma versão passada e
    // apertava para 10 na escala atual)
    const val CHAVE_VOLUME = "volume_nivel"
    const val VOLUME_PADRAO = 4
    const val VOLUME_MAXIMO = 10

    fun volume(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(CHAVE_VOLUME, VOLUME_PADRAO)
            .coerceIn(0, VOLUME_MAXIMO)

    fun salvarVolume(context: Context, valor: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(CHAVE_VOLUME, valor.coerceIn(0, VOLUME_MAXIMO))
            .apply()
    }

    fun sucesso(context: Context) = tocar(context, frequenciaHz = 2400.0, duracaoMs = 150)

    fun repetido(context: Context) = tocar(context, frequenciaHz = 900.0, duracaoMs = 180)

    // Volume de alarme de antes do primeiro som — restaurado depois do último
    private var volumeAnterior: Int? = null

    private fun tocar(context: Context, frequenciaHz: Double, duracaoMs: Int) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val maxStream = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            val alvo = ((volume(context) / VOLUME_MAXIMO.toDouble()) * maxStream)
                .toInt()
                .coerceIn(1, maxStream)

            // Guarda o volume de alarme de antes (uma vez) e aplica o nível do bip
            if (volumeAnterior == null) {
                volumeAnterior = audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            }
            audio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, alvo, 0)

            val taxa = 44100
            val amostras = taxa * duracaoMs / 1000
            val pcm = ShortArray(amostras) { i ->
                val t = i.toDouble() / taxa
                // Envelope com ataque e decaimento curtos: sem estalo no começo/fim
                val envelope = minOf(
                    1.0,
                    i / (taxa * 0.008),
                    (amostras - i) / (taxa * 0.02)
                ).coerceAtLeast(0.0)
                // Fundamental + oitava acima: mais presença no ruído do galpão
                val onda = Math.sin(2 * Math.PI * frequenciaHz * t) +
                    0.35 * Math.sin(4 * Math.PI * frequenciaHz * t)
                (onda / 1.35 * Short.MAX_VALUE * envelope).toInt().toShort()
            }

            val faixa = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(taxa)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(amostras * 2)
                .build()

            faixa.write(pcm, 0, amostras)
            faixa.setVolume(1f)
            faixa.play()

            // Libera a faixa e devolve o volume de alarme de antes
            Handler(Looper.getMainLooper()).postDelayed({
                try { faixa.release() } catch (_: Exception) {}
                volumeAnterior?.let { anterior ->
                    try {
                        audio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, anterior, 0)
                    } catch (_: Exception) {}
                }
                volumeAnterior = null
            }, duracaoMs + 120L)
        } catch (_: Exception) {
            // Falha de áudio no aparelho: o feedback visual e a vibração bastam
        }
    }
}
