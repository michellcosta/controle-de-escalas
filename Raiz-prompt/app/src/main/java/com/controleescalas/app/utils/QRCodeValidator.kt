package com.controleescalas.app.utils

import com.controleescalas.app.data.models.MotoristaQRCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException

/**
 * Validador de QR Code do motorista
 */
object QRCodeValidator {
    
    private val json = Json { 
        ignoreUnknownKeys = true  // Aceitar campos extras no JSON
        encodeDefaults = true
        isLenient = true  // Mais tolerante com JSON
    }
    
    /**
     * Valida e parseia o JSON do QR Code
     * @param qrCodeText Texto lido do QR Code
     * @return Pair<MotoristaQRCode?, String?> onde o primeiro é o objeto parseado (ou null) e o segundo é mensagem de erro (ou null)
     */
    fun validateAndParse(qrCodeText: String): Pair<MotoristaQRCode?, String?> {
        // 1. Verificar se o texto não está vazio
        if (qrCodeText.isBlank()) {
            return null to "QR Code vazio"
        }
        
        // 2. Limpar o texto (remover espaços extras, quebras de linha)
        val cleanedText = qrCodeText.trim()
        println("🔍 QRCodeValidator: Tentando parsear JSON (tamanho: ${cleanedText.length})")
        println("🔍 QRCodeValidator: Primeiros 100 caracteres: ${cleanedText.take(100)}")
        
        // 3. Tentar parsear como JSON
        val qrCode = try {
            json.decodeFromString<MotoristaQRCode>(cleanedText)
        } catch (e: SerializationException) {
            println("❌ QRCodeValidator: Erro ao parsear JSON: ${e.message}")
            println("❌ QRCodeValidator: JSON recebido: $cleanedText")
            e.printStackTrace()
            return null to "QR Code inválido. Peça ao seu dispatcher um QR Code válido."
        } catch (e: Exception) {
            println("❌ QRCodeValidator: Erro inesperado: ${e.message}")
            println("❌ QRCodeValidator: JSON recebido: $cleanedText")
            e.printStackTrace()
            return null to "QR Code inválido. Peça ao seu dispatcher um QR Code válido."
        }
        
        println("✅ QRCodeValidator: JSON parseado com sucesso: id=${qrCode.id}, carrier=${qrCode.carrier_name}")
        
        // 4. Validar campos obrigatórios
        val validationError = validateFields(qrCode)
        if (validationError != null) {
            println("❌ QRCodeValidator: Validação falhou: $validationError")
            return null to validationError
        }
        
        println("✅ QRCodeValidator: QR Code válido!")
        // 5. Retornar QR Code válido
        return qrCode to null
    }
    
    /**
     * Valida se todos os campos obrigatórios estão presentes e válidos
     */
    private fun validateFields(qrCode: MotoristaQRCode): String? {
        // Validar id (deve ser > 0)
        if (qrCode.id <= 0) {
            return "QR Code inválido. Peça ao seu dispatcher um QR Code válido."
        }
        
        // Validar carrier_id (deve ser > 0)
        if (qrCode.carrier_id <= 0) {
            return "QR Code inválido. Peça ao seu dispatcher um QR Code válido."
        }
        
        // Validar carrier_name (não pode estar vazio)
        if (qrCode.carrier_name.isBlank()) {
            return "QR Code inválido. Peça ao seu dispatcher um QR Code válido."
        }
        
        // Validar license_plate (não pode estar vazio)
        if (qrCode.license_plate.isBlank()) {
            return "QR Code inválido. Peça ao seu dispatcher um QR Code válido."
        }
        
        // Validar vehicle_type_description (não pode estar vazio)
        if (qrCode.vehicle_type_description.isBlank()) {
            return "QR Code inválido. Peça ao seu dispatcher um QR Code válido."
        }
        
        // Validar vehicle_type_id (deve ser > 0)
        if (qrCode.vehicle_type_id <= 0) {
            return "QR Code inválido. Peça ao seu dispatcher um QR Code válido."
        }
        
        // Validar tracking_provider_ids (deve ter pelo menos 1 item)
        if (qrCode.tracking_provider_ids.isEmpty()) {
            return "QR Code inválido. Peça ao seu dispatcher um QR Code válido."
        }
        
        // Validar que todos os IDs no array não estão vazios
        if (qrCode.tracking_provider_ids.any { it.isBlank() }) {
            return "QR Code inválido. Peça ao seu dispatcher um QR Code válido."
        }
        
        // Tudo válido
        return null
    }
}

