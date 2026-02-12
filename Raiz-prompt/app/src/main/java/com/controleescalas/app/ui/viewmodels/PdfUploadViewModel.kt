package com.controleescalas.app.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.controleescalas.app.data.PdfUploadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar uploads de PDF
 */
class PdfUploadViewModel(application: Application) : AndroidViewModel(application) {
    private val pdfUploadService = PdfUploadService(application.applicationContext)
    
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()
    
    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()
    
    private val _uploadResult = MutableStateFlow<String?>(null)
    val uploadResult: StateFlow<String?> = _uploadResult.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Upload de PDF por URI
     */
    fun uploadPdf(
        baseId: String,
        rotaCodigo: String,
        pdfUri: Uri
    ) {
        viewModelScope.launch {
            _isUploading.value = true
            _error.value = null
            _uploadResult.value = null
            _uploadProgress.value = 0f
            
            try {
                // Simular progresso (Firebase não fornece progresso detalhado)
                _uploadProgress.value = 0.3f
                
                val result = pdfUploadService.uploadPdfFromUri(baseId, rotaCodigo, pdfUri)
                
                _uploadProgress.value = 0.7f
                
                result.onSuccess { downloadUrl ->
                    _uploadResult.value = downloadUrl
                    _uploadProgress.value = 1f
                }.onFailure { exception ->
                    _error.value = "Erro no upload: ${exception.message}"
                }
            } catch (e: Exception) {
                _error.value = "Erro inesperado: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    /**
     * Upload de PDF por bytes
     */
    fun uploadPdfFromBytes(
        baseId: String,
        rotaCodigo: String,
        pdfBytes: ByteArray
    ) {
        viewModelScope.launch {
            _isUploading.value = true
            _error.value = null
            _uploadResult.value = null
            _uploadProgress.value = 0f
            
            try {
                _uploadProgress.value = 0.3f
                
                val result = pdfUploadService.uploadPdfFromBytes(baseId, rotaCodigo, pdfBytes)
                
                _uploadProgress.value = 0.7f
                
                result.onSuccess { downloadUrl ->
                    _uploadResult.value = downloadUrl
                    _uploadProgress.value = 1f
                }.onFailure { exception ->
                    _error.value = "Erro no upload: ${exception.message}"
                }
            } catch (e: Exception) {
                _error.value = "Erro inesperado: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    /**
     * Limpar estados
     */
    fun clearStates() {
        _uploadResult.value = null
        _error.value = null
        _uploadProgress.value = 0f
    }
}



