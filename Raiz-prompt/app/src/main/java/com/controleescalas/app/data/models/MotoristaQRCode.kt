package com.controleescalas.app.data.models

import kotlinx.serialization.Serializable

/**
 * Modelo de dados para o QR Code do motorista
 * Representa o JSON que é lido do QR Code
 */
@Serializable
data class MotoristaQRCode(
    val id: Long,
    val carrier_id: Long,
    val carrier_name: String,
    val license_plate: String,
    val vehicle_type_description: String,
    val vehicle_type_id: Int,
    val tracking_provider_ids: List<String>
)

