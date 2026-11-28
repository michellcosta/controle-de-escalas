package com.controleescalas.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Vibração nos momentos causais — um por evento, nunca por frame, e nunca
 * como único feedback (o visual precisa bastar sozinho). Referência:
 * emilkowalski/skills (animate-expo — haptics).
 *
 * - toque:   seleção, reset leve (EFFECT_CLICK)
 * - impacto: chamada para vaga/estacionamento, exclusão confirmada (EFFECT_HEAVY_CLICK)
 * - sucesso: conclusão de carregamento (tique duplo)
 */
object Vibrar {

    private fun vibrador(context: Context): Vibrator? {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val gerente = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            gerente?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return vibrator?.takeIf { it.hasVibrator() }
    }

    private fun vibrar(context: Context, efeito: () -> VibrationEffect) {
        try {
            vibrador(context)?.vibrate(efeito())
        } catch (_: Exception) {
            // Aparelho sem vibrador ou com o recurso desligado: o visual basta
        }
    }

    fun toque(context: Context) = vibrar(context) {
        if (Build.VERSION.SDK_INT >= 29) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    fun impacto(context: Context) = vibrar(context) {
        if (Build.VERSION.SDK_INT >= 29) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        } else {
            VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    fun sucesso(context: Context) = vibrar(context) {
        if (Build.VERSION.SDK_INT >= 26) {
            VibrationEffect.createWaveform(longArrayOf(0, 35, 70, 35), -1)
        } else {
            VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }
}
