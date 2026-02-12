package com.controleescalas.app.data

import android.os.Build

/**
 * Utilitários para detectar fabricantes com restrições de notificação
 */
object DeviceUtils {
    
    /**
     * Xiaomi, Redmi, POCO usam MIUI e bloqueiam notificações com app fechado
     * a menos que o usuário configure "Sem restrições" e "Inicialização automática"
     */
    fun isXiaomiFamily(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        return manufacturer == "xiaomi" || brand in listOf("xiaomi", "redmi", "poco")
    }
}
