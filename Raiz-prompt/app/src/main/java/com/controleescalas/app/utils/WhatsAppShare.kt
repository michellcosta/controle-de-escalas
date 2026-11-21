package com.controleescalas.app.utils

import android.content.Context
import android.content.Intent

/**
 * Compartilhamento de texto no WhatsApp
 * (padrão usado pela escala do dashboard e pela pré-escala da disponibilidade)
 */
object WhatsAppShare {

    /**
     * Abre o WhatsApp com o texto pronto (fallback: seletor genérico de apps)
     */
    fun compartilharTextoNoWhatsApp(context: Context, texto: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val intentGenerico = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, texto)
            }
            context.startActivity(Intent.createChooser(intentGenerico, "Compartilhar via"))
        }
    }
}
