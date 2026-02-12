package com.controleescalas.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Utilitário para detectar qualidade e tipo de conexão de rede
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"
    
    /**
     * Tipo de conexão detectado
     */
    enum class ConnectionType {
        WIFI,
        CELLULAR_4G,
        CELLULAR_3G,
        CELLULAR_2G,
        UNKNOWN,
        OFFLINE
    }
    
    /**
     * Qualidade da conexão
     */
    enum class ConnectionQuality {
        EXCELLENT,  // WiFi ou 4G
        GOOD,       // 4G
        FAIR,       // 3G
        POOR,       // 2G
        OFFLINE     // Sem conexão
    }
    
    /**
     * Detectar tipo de conexão atual
     */
    fun getConnectionType(context: Context): ConnectionType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return ConnectionType.OFFLINE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return ConnectionType.OFFLINE
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // Tentar detectar geração da rede celular
                when {
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> {
                        // Verificar velocidade estimada (não perfeito, mas útil)
                        val downstream = capabilities.linkDownstreamBandwidthKbps
                        when {
                            downstream > 10000 -> ConnectionType.CELLULAR_4G
                            downstream > 2000 -> ConnectionType.CELLULAR_3G
                            else -> ConnectionType.CELLULAR_2G
                        }
                    }
                    else -> ConnectionType.CELLULAR_3G
                }
            }
            else -> ConnectionType.UNKNOWN
        }
    }
    
    /**
     * Verificar se está online
     */
    fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Obter qualidade da conexão
     */
    fun getConnectionQuality(context: Context): ConnectionQuality {
        val type = getConnectionType(context)
        return when (type) {
            ConnectionType.WIFI -> ConnectionQuality.EXCELLENT
            ConnectionType.CELLULAR_4G -> ConnectionQuality.GOOD
            ConnectionType.CELLULAR_3G -> ConnectionQuality.FAIR
            ConnectionType.CELLULAR_2G -> ConnectionQuality.POOR
            ConnectionType.OFFLINE -> ConnectionQuality.OFFLINE
            ConnectionType.UNKNOWN -> ConnectionQuality.FAIR
        }
    }
    
    /**
     * Obter mensagem descritiva da conexão
     */
    fun getConnectionMessage(context: Context): String {
        val type = getConnectionType(context)
        val quality = getConnectionQuality(context)
        
        return when (quality) {
            ConnectionQuality.EXCELLENT -> "🟢 Conectado via WiFi"
            ConnectionQuality.GOOD -> "🟢 Conectado via 4G"
            ConnectionQuality.FAIR -> "🟡 Conexão 3G - atualizações podem demorar"
            ConnectionQuality.POOR -> "🟠 Conexão 2G - usando dados salvos"
            ConnectionQuality.OFFLINE -> "🔴 Sem conexão - modo offline"
        }
    }
    
    /**
     * Verificar se conexão é suficientemente boa para operações normais
     */
    fun isConnectionGoodEnough(context: Context): Boolean {
        val quality = getConnectionQuality(context)
        return quality == ConnectionQuality.EXCELLENT || quality == ConnectionQuality.GOOD
    }
}

