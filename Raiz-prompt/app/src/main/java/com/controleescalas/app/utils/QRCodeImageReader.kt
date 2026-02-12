package com.controleescalas.app.utils

import android.graphics.Bitmap
import android.util.Base64
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Leitor de QR Code de imagens usando ML Kit
 */
object QRCodeImageReader {
    
    private val scanner = BarcodeScanning.getClient()
    
    /**
     * Redimensiona um bitmap se necessário (imagens muito grandes podem ter problemas)
     */
    private fun resizeIfNeeded(bitmap: Bitmap, maxDimension: Int = 2000): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        val scale = minOf(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height
        )
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        println("📐 QRCodeImageReader: Redimensionando imagem de ${width}x${height} para ${newWidth}x${newHeight}")
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Melhora o contraste e a nitidez da imagem para facilitar a leitura do QR Code
     */
    private fun enhanceImageForQRCode(bitmap: Bitmap): Bitmap {
        return try {
            // Converter para escala de cinza
            val gray = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(gray)
            val paint = android.graphics.Paint()
            val colorMatrix = android.graphics.ColorMatrix()
            colorMatrix.setSaturation(0f) // Remover cor
            val filter = android.graphics.ColorMatrixColorFilter(colorMatrix)
            paint.colorFilter = filter
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            // Aplicar filtro de contraste aumentado
            val contrastMatrix = android.graphics.ColorMatrix()
            contrastMatrix.set(
                floatArrayOf(
                    1.8f, 0f, 0f, 0f, -50f,  // Vermelho - aumentar contraste e reduzir brilho
                    0f, 1.8f, 0f, 0f, -50f,  // Verde
                    0f, 0f, 1.8f, 0f, -50f,  // Azul
                    0f, 0f, 0f, 1f, 0f       // Alpha
                )
            )
            
            val enhanced = Bitmap.createBitmap(gray.width, gray.height, Bitmap.Config.ARGB_8888)
            val enhancedCanvas = android.graphics.Canvas(enhanced)
            val enhancedPaint = android.graphics.Paint()
            enhancedPaint.colorFilter = android.graphics.ColorMatrixColorFilter(contrastMatrix)
            enhancedCanvas.drawBitmap(gray, 0f, 0f, enhancedPaint)
            
            println("✅ QRCodeImageReader: Imagem melhorada (contraste aumentado)")
            enhanced
        } catch (e: Exception) {
            println("⚠️ QRCodeImageReader: Erro ao melhorar imagem, usando original: ${e.message}")
            e.printStackTrace()
            bitmap
        }
    }
    
    /**
     * Tenta ler QR Code usando ML Kit
     */
    private suspend fun tryReadWithMLKit(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                println("🔍 QRCodeImageReader: ML Kit processou. Códigos encontrados: ${barcodes.size}")
                
                if (barcodes.isEmpty()) {
                    continuation.resume(null)
                    return@addOnSuccessListener
                }
                
                for (barcode in barcodes) {
                    println("📋 QRCodeImageReader: Código detectado - Tipo: ${barcode.valueType}, Formato: ${barcode.format}")
                    println("📋 QRCodeImageReader: rawValue: ${barcode.rawValue}")
                    println("📋 QRCodeImageReader: displayValue: ${barcode.displayValue}")
                    
                    val text = barcode.rawValue ?: barcode.displayValue
                    if (text != null && text.isNotBlank() && text != "Unknown encoding") {
                        println("✅ QRCodeImageReader: QR Code detectado pelo ML Kit: ${text.length} caracteres")
                        continuation.resume(text)
                        return@addOnSuccessListener
                    }
                }
                
                continuation.resume(null)
            }
            .addOnFailureListener { e ->
                println("❌ QRCodeImageReader: Erro no ML Kit: ${e.message}")
                continuation.resume(null)
            }
    }
    
    /**
     * Lê QR Code de uma imagem Bitmap
     * @param bitmap Bitmap da imagem
     * @return Texto do QR Code ou null se não encontrado
     */
    suspend fun readQRCodeFromBitmap(bitmap: Bitmap): String? {
        return suspendCancellableCoroutine { continuation ->
            println("🔍 QRCodeImageReader: Iniciando leitura de QR Code")
            println("📏 QRCodeImageReader: Dimensões da imagem: ${bitmap.width}x${bitmap.height}")
            
            // Redimensionar se necessário
            val processedBitmap = resizeIfNeeded(bitmap)
            
            // Tentar múltiplas abordagens em sequência
            kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
                // Tentativa 1: ML Kit com imagem original
                println("🔄 QRCodeImageReader: Tentativa 1 - ML Kit com imagem original")
                var result = tryReadWithMLKit(processedBitmap)
                if (result != null) {
                    continuation.resume(result)
                    return@launch
                }
                
                // Tentativa 2: ML Kit com imagem melhorada
                println("🔄 QRCodeImageReader: Tentativa 2 - ML Kit com imagem melhorada")
                val enhanced = enhanceImageForQRCode(processedBitmap)
                result = tryReadWithMLKit(enhanced)
                if (result != null) {
                    continuation.resume(result)
                    return@launch
                }
                
                // Tentativa 3: ZXing com imagem original
                println("🔄 QRCodeImageReader: Tentativa 3 - ZXing com imagem original")
                result = readQRCodeWithZXing(processedBitmap)
                if (result != null) {
                    continuation.resume(result)
                    return@launch
                }
                
                // Tentativa 4: ZXing com imagem melhorada
                println("🔄 QRCodeImageReader: Tentativa 4 - ZXing com imagem melhorada")
                result = readQRCodeWithZXing(enhanced)
                if (result != null) {
                    continuation.resume(result)
                    return@launch
                }
                
                println("⚠️ QRCodeImageReader: Todas as tentativas falharam")
                continuation.resume(null)
            }
        }
    }
    
    /**
     * Lê QR Code usando ZXing (útil para QR Codes binários que ML Kit não consegue decodificar)
     */
    private suspend fun readQRCodeWithZXing(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        try {
            println("🔍 QRCodeImageReader: Tentando ler QR Code com ZXing...")
            
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            
            val reader = MultiFormatReader()
            val result: Result = reader.decode(binaryBitmap)
            
            val text = result.text
            if (text != null && text.isNotBlank()) {
                println("✅ QRCodeImageReader: ZXing leu QR Code: ${text.length} caracteres")
                
                // Se o texto contém caracteres não imprimíveis (dados binários), converter para Base64
                val hasNonPrintableChars = text.any { it.code < 32 && it !in listOf('\n', '\r', '\t') }
                return@withContext if (hasNonPrintableChars) {
                    // Converter bytes para Base64
                    val bytes = result.rawBytes
                    if (bytes != null && bytes.isNotEmpty()) {
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        println("📦 QRCodeImageReader: Convertendo dados binários para Base64: ${base64.length} caracteres")
                        base64
                    } else {
                        text
                    }
                } else {
                    text
                }
            }
            
            null
        } catch (e: Exception) {
            println("❌ QRCodeImageReader: Erro ao ler com ZXing: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

