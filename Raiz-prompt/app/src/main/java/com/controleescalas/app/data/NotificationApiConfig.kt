package com.controleescalas.app.data

/**
 * Configuração da API de Notificações Python
 * 
 * IMPORTANTE: Altere a URL abaixo para a URL do seu servidor Python.
 * 
 * Opções de hospedagem gratuita:
 * - Railway: https://railway.app
 * - Render: https://render.com
 * - Fly.io: https://fly.io
 * 
 * Para desenvolvimento local, use: http://10.0.2.2:5000 (Android Emulator)
 * Para dispositivo físico na mesma rede: http://SEU_IP_LOCAL:5000
 */
object NotificationApiConfig {
    /**
     * URL base da API Python
     * 
     * ⚠️ ALTERE ESTA URL para a URL do seu servidor em produção!
     */
    const val BASE_URL = "https://controle-de-escalas.onrender.com"
    
    /**
     * Timeout em segundos para requisições HTTP
     * Render free tier pode demorar ~50s para "acordar" após inatividade
     */
    const val TIMEOUT_SECONDS = 45L
    
    /**
     * Endpoints da API
     */
    object Endpoints {
        const val NOTIFY_MOTORISTA = "/notify/motorista"
        const val HEALTH = "/health"
        const val LOCATION_REQUEST = "/location/request"
        const val LOCATION_RECEIVE = "/location/receive"
        const val ASSISTENTE_CHAT = "/assistente/chat"
    }
}
