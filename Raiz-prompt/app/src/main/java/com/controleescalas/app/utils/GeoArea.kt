package com.controleescalas.app.utils

import android.location.Location
import com.controleescalas.app.data.models.GeoPonto
import com.controleescalas.app.data.models.GeofenceConfig

object GeoArea {
    fun temPoligono(pontos: List<GeoPonto>): Boolean = pontos.size >= 3

    fun configurado(config: GeofenceConfig): Boolean {
        return temPoligono(config.pontos) || (config.lat != 0.0 && config.lng != 0.0)
    }

    fun centroide(pontos: List<GeoPonto>): GeoPonto {
        if (pontos.isEmpty()) return GeoPonto()
        return GeoPonto(
            lat = pontos.map { it.lat }.average(),
            lng = pontos.map { it.lng }.average()
        )
    }

    fun raioEnvolvente(pontos: List<GeoPonto>, paddingMetros: Int = 20): Int {
        if (pontos.isEmpty()) return 100
        val centro = centroide(pontos)
        var max = 0f
        val results = FloatArray(1)
        for (ponto in pontos) {
            Location.distanceBetween(centro.lat, centro.lng, ponto.lat, ponto.lng, results)
            if (results[0] > max) max = results[0]
        }
        return (max + paddingMetros).toInt().coerceAtLeast(30)
    }

    fun pontoNoPoligono(lat: Double, lng: Double, pontos: List<GeoPonto>): Boolean {
        if (pontos.size < 3) return false
        var inside = false
        var j = pontos.lastIndex
        for (i in pontos.indices) {
            val yi = pontos[i].lat
            val xi = pontos[i].lng
            val yj = pontos[j].lat
            val xj = pontos[j].lng
            if ((yi > lat) != (yj > lat) && (yj - yi) != 0.0) {
                val cruza = (xj - xi) * (lat - yi) / (yj - yi) + xi
                if (lng < cruza) inside = !inside
            }
            j = i
        }
        return inside
    }

    fun contem(config: GeofenceConfig, lat: Double, lng: Double): Boolean {
        return contem(lat, lng, config.pontos, config.lat, config.lng, config.raio)
    }

    fun contem(
        lat: Double,
        lng: Double,
        pontos: List<GeoPonto>,
        centroLat: Double,
        centroLng: Double,
        raio: Int
    ): Boolean {
        if (temPoligono(pontos)) return pontoNoPoligono(lat, lng, pontos)
        if (centroLat == 0.0 && centroLng == 0.0) return false
        val results = FloatArray(1)
        Location.distanceBetween(lat, lng, centroLat, centroLng, results)
        return results[0] <= raio
    }
}
