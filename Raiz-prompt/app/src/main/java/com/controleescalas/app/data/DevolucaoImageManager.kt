package com.controleescalas.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Gerenciador de imagens de devoluções
 * Suporta múltiplas fotos ilimitadas por devolução no storage interno do app
 * Mantém compatibilidade com fotos antigas (foto1 e foto2)
 */
class DevolucaoImageManager(private val context: Context) {
    
    companion object {
        private const val FOTO1_SUFFIX = "_foto1.jpg"
        private const val FOTO2_SUFFIX = "_foto2.jpg"
        private const val FOTO_PREFIX = "_foto_"
        private const val FOTO_SUFFIX = ".jpg"
    }
    
    // ========== MÉTODOS LEGADOS (compatibilidade com foto1/foto2) ==========
    
    private fun getFoto1File(devolucaoId: String): File {
        return File(context.filesDir, "devolucao_${devolucaoId}$FOTO1_SUFFIX")
    }
    
    private fun getFoto2File(devolucaoId: String): File {
        return File(context.filesDir, "devolucao_${devolucaoId}$FOTO2_SUFFIX")
    }
    
    fun hasFoto1(devolucaoId: String): Boolean {
        return getFoto1File(devolucaoId).exists()
    }
    
    fun hasFoto2(devolucaoId: String): Boolean {
        return getFoto2File(devolucaoId).exists()
    }
    
    suspend fun saveFoto1FromUri(devolucaoId: String, imageUri: Uri): Boolean {
        return addFotoFromUri(devolucaoId, imageUri) != null
    }
    
    suspend fun saveFoto2FromUri(devolucaoId: String, imageUri: Uri): Boolean {
        return addFotoFromUri(devolucaoId, imageUri) != null
    }
    
    suspend fun saveFoto1(devolucaoId: String, bitmap: Bitmap): Boolean {
        return addFoto(devolucaoId, bitmap) != null
    }
    
    suspend fun saveFoto2(devolucaoId: String, bitmap: Bitmap): Boolean {
        return addFoto(devolucaoId, bitmap) != null
    }
    
    fun loadFoto1(devolucaoId: String): Bitmap? {
        val file = getFoto1File(devolucaoId)
        return if (file.exists()) loadBitmapFromFile(file) else null
    }
    
    fun loadFoto2(devolucaoId: String): Bitmap? {
        val file = getFoto2File(devolucaoId)
        return if (file.exists()) loadBitmapFromFile(file) else null
    }
    
    fun getFoto1Uri(devolucaoId: String): Uri? {
        val file = getFoto1File(devolucaoId)
        return if (file.exists()) getUriFromFile(file) else null
    }
    
    fun getFoto2Uri(devolucaoId: String): Uri? {
        val file = getFoto2File(devolucaoId)
        return if (file.exists()) getUriFromFile(file) else null
    }
    
    suspend fun clearFoto1(devolucaoId: String) {
        withContext(Dispatchers.IO) {
            getFoto1File(devolucaoId).delete()
        }
    }
    
    suspend fun clearFoto2(devolucaoId: String) {
        withContext(Dispatchers.IO) {
            getFoto2File(devolucaoId).delete()
        }
    }
    
    // ========== NOVOS MÉTODOS (múltiplas fotos ilimitadas) ==========
    
    /**
     * Obtém o arquivo de uma foto pelo índice
     */
    private fun getFotoFile(devolucaoId: String, index: Int): File {
        return File(context.filesDir, "devolucao_${devolucaoId}$FOTO_PREFIX$index$FOTO_SUFFIX")
    }
    
    /**
     * Obtém todos os arquivos de fotos de uma devolução (incluindo legados)
     */
    private fun getAllFotoFiles(devolucaoId: String): List<Pair<Int, File>> {
        val files = mutableListOf<Pair<Int, File>>()
        val filesDir = context.filesDir
        val prefix = "devolucao_${devolucaoId}$FOTO_PREFIX"
        
        // Buscar fotos com novo formato (_foto_0.jpg, _foto_1.jpg, etc)
        filesDir.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith(prefix) && name.endsWith(FOTO_SUFFIX)) {
                try {
                    val indexStr = name.removePrefix(prefix).removeSuffix(FOTO_SUFFIX)
                    val index = indexStr.toInt()
                    files.add(Pair(index, file))
                } catch (e: Exception) {
                    // Ignorar arquivos com formato inválido
                }
            }
        }
        
        // Adicionar fotos legadas (foto1 e foto2) como índices 0 e 1 se existirem
        val foto1File = getFoto1File(devolucaoId)
        val foto2File = getFoto2File(devolucaoId)
        
        // Verificar se já existe foto no índice 0
        val hasIndex0 = files.any { it.first == 0 }
        if (foto1File.exists() && !hasIndex0) {
            files.add(Pair(0, foto1File))
        }
        
        // Verificar se já existe foto no índice 1
        val hasIndex1 = files.any { it.first == 1 }
        if (foto2File.exists() && !hasIndex1) {
            files.add(Pair(1, foto2File))
        }
        
        // Ordenar por índice
        return files.sortedBy { it.first }
    }
    
    /**
     * Obtém o próximo índice disponível para uma nova foto
     */
    private fun getNextFotoIndex(devolucaoId: String): Int {
        val existingFiles = getAllFotoFiles(devolucaoId)
        if (existingFiles.isEmpty()) return 0
        
        // Encontrar o maior índice e adicionar 1
        val maxIndex = existingFiles.maxOfOrNull { it.first } ?: -1
        return maxIndex + 1
    }
    
    /**
     * Carrega um bitmap de um arquivo
     */
    private fun loadBitmapFromFile(file: File): Bitmap? {
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                println("❌ DevolucaoImageManager: Erro ao decodificar bitmap de ${file.name}")
            }
            bitmap
        } catch (e: Exception) {
            println("❌ DevolucaoImageManager: Erro ao carregar bitmap de ${file.name}: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Obtém URI de um arquivo usando FileProvider
     */
    private fun getUriFromFile(file: File): Uri? {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            println("❌ DevolucaoImageManager: Erro ao obter URI de ${file.name}: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Adiciona uma nova foto a partir de uma URI
     * @param devolucaoId ID da devolução
     * @param imageUri URI da imagem a ser salva
     * @return Índice da foto salva ou null se falhar
     */
    suspend fun addFotoFromUri(devolucaoId: String, imageUri: Uri): Int? {
        return withContext(Dispatchers.IO) {
            try {
                // Usar BitmapFactory.Options para garantir alta qualidade
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = 1 // Sem redução de tamanho
                    inPreferredConfig = Bitmap.Config.ARGB_8888 // Melhor qualidade de cor
                }
                
                val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
                if (inputStream == null) {
                    println("❌ DevolucaoImageManager: Erro ao abrir input stream da URI")
                    return@withContext null
                }
                
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()
                
                if (bitmap == null) {
                    println("❌ DevolucaoImageManager: Erro ao decodificar bitmap")
                    return@withContext null
                }
                
                addFoto(devolucaoId, bitmap)
            } catch (e: Exception) {
                println("❌ DevolucaoImageManager: Erro ao salvar foto da URI: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Adiciona uma nova foto a partir de um Bitmap
     * @param devolucaoId ID da devolução
     * @param bitmap Bitmap da imagem a ser salva
     * @return Índice da foto salva ou null se falhar
     */
    suspend fun addFoto(devolucaoId: String, bitmap: Bitmap): Int? {
        return withContext(Dispatchers.IO) {
            try {
                val index = getNextFotoIndex(devolucaoId)
                val fotoFile = getFotoFile(devolucaoId, index)
                val outputStream = FileOutputStream(fotoFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream) // Qualidade máxima
                outputStream.flush()
                outputStream.close()
                
                println("✅ DevolucaoImageManager: Foto $index salva com sucesso para devolução $devolucaoId")
                index
            } catch (e: Exception) {
                println("❌ DevolucaoImageManager: Erro ao salvar foto: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Obtém todas as fotos de uma devolução
     * @param devolucaoId ID da devolução
     * @return Lista de pares (índice, Bitmap)
     */
    fun getAllFotos(devolucaoId: String): List<Pair<Int, Bitmap>> {
        return getAllFotoFiles(devolucaoId)
            .mapNotNull { (index, file) ->
                loadBitmapFromFile(file)?.let { bitmap ->
                    Pair(index, bitmap)
                }
            }
    }
    
    /**
     * Obtém todas as URIs das fotos de uma devolução
     * @param devolucaoId ID da devolução
     * @return Lista de URIs ordenadas por índice
     */
    fun getAllFotosUris(devolucaoId: String): List<Uri> {
        return getAllFotoFiles(devolucaoId)
            .mapNotNull { (_, file) ->
                getUriFromFile(file)
            }
    }
    
    /**
     * Obtém a quantidade de fotos de uma devolução
     */
    fun getFotosCount(devolucaoId: String): Int {
        return getAllFotoFiles(devolucaoId).size
    }
    
    /**
     * Remove uma foto específica pelo índice
     * @param devolucaoId ID da devolução
     * @param index Índice da foto a ser removida
     */
    suspend fun removeFoto(devolucaoId: String, index: Int) {
        withContext(Dispatchers.IO) {
            try {
                val file = getFotoFile(devolucaoId, index)
                if (file.exists()) {
                    file.delete()
                    println("✅ DevolucaoImageManager: Foto $index removida")
                } else {
                    // Tentar remover foto legada
                    when (index) {
                        0 -> getFoto1File(devolucaoId).delete()
                        1 -> getFoto2File(devolucaoId).delete()
                        else -> { /* Índice não corresponde a foto legada */ }
                    }
                }
            } catch (e: Exception) {
                println("❌ DevolucaoImageManager: Erro ao remover foto $index: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Remove todas as fotos de uma devolução
     * @param devolucaoId ID da devolução
     */
    suspend fun clearAllFotos(devolucaoId: String) {
        withContext(Dispatchers.IO) {
            try {
                val files = getAllFotoFiles(devolucaoId)
                files.forEach { (_, file) ->
                    file.delete()
                }
                println("✅ DevolucaoImageManager: Todas as fotos removidas para devolução $devolucaoId")
            } catch (e: Exception) {
                println("❌ DevolucaoImageManager: Erro ao remover todas as fotos: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Obtém as URIs de ambas as fotos legadas (compatibilidade)
     * @param devolucaoId ID da devolução
     * @return Lista de URIs (pode ter 0, 1 ou 2 elementos)
     */
    fun getFotosUris(devolucaoId: String): List<Uri> {
        // Usar o novo método que retorna todas as fotos
        return getAllFotosUris(devolucaoId)
    }
}
