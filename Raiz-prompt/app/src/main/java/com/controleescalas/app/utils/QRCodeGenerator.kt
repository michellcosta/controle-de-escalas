package com.controleescalas.app.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Gerador de QR Code usando ZXing
 */
object QRCodeGenerator {
    
    /**
     * Gera um Bitmap de QR Code a partir de um texto
     * @param text Texto para gerar o QR Code
     * @param width Largura desejada do QR Code
     * @param height Altura desejada do QR Code
     * @return Bitmap do QR Code ou null se houver erro
     */
    fun generateQRCode(text: String, width: Int, height: Int): Bitmap? {
        return try {
            // Usar o tamanho passado diretamente (já calculado com limites na função chamadora)
            val finalWidth = width
            val finalHeight = height
            
            val hints = hashMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L) // L = 7% redundância, módulos maiores
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 4) // Margem maior para leitura mais rápida (padrão apps governo)
            }
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, finalWidth, finalHeight, hints)
            
            // Usar dimensões reais do BitMatrix (ZXing pode retornar tamanho diferente do solicitado)
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)
            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            
            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            
            bitmap
        } catch (e: Exception) {
            println("❌ QRCodeGenerator: Erro ao gerar QR Code: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Gera um Bitmap de QR Code a partir de bytes binários
     * Usa ISO-8859-1 para preservar os bytes originais 1:1
     * @param bytes Bytes para gerar o QR Code
     * @param width Largura desejada do QR Code
     * @param height Altura desejada do QR Code
     * @return Bitmap do QR Code ou null se houver erro
     */
    fun generateQRCodeFromBytes(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        return try {
            // Usar o tamanho passado diretamente (já calculado com limites na função chamadora)
            val finalWidth = width
            val finalHeight = height
            
            // Converter bytes para String usando ISO-8859-1 (preserva bytes 1:1, sem perda de dados)
            val text = String(bytes, Charsets.ISO_8859_1)
            
            val hints = hashMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L) // L = 7% redundância, módulos maiores
                put(EncodeHintType.CHARACTER_SET, "ISO-8859-1") // Usar ISO-8859-1 para preservar bytes
                put(EncodeHintType.MARGIN, 4) // Margem maior para leitura mais rápida (padrão apps governo)
            }
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, finalWidth, finalHeight, hints)
            
            // Usar dimensões reais do BitMatrix (ZXing pode retornar tamanho diferente do solicitado)
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)
            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            
            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            
            println("✅ QRCodeGenerator: QR Code gerado a partir de ${bytes.size} bytes usando ISO-8859-1")
            bitmap
        } catch (e: Exception) {
            println("❌ QRCodeGenerator: Erro ao gerar QR Code de bytes: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

