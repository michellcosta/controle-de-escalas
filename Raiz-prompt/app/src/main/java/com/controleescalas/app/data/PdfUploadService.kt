package com.controleescalas.app.data

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Serviço para upload de PDFs para Firebase Storage
 */
class PdfUploadService(private val context: Context) {
    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.reference

    /**
     * Upload de PDF por URI (arquivo local)
     */
    suspend fun uploadPdfFromUri(
        baseId: String,
        rotaCodigo: String,
        pdfUri: Uri
    ): Result<String> {
        return try {
            val fileName = "${rotaCodigo}_${UUID.randomUUID().toString().take(8)}.pdf"
            val pdfRef = storageRef.child("rotas_pdf/${baseId}/$fileName")
            
            val uploadTask = pdfRef.putFile(pdfUri).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await()
            
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload de PDF por bytes (dados em memória)
     */
    suspend fun uploadPdfFromBytes(
        baseId: String,
        rotaCodigo: String,
        pdfBytes: ByteArray
    ): Result<String> {
        return try {
            val fileName = "${rotaCodigo}_${UUID.randomUUID().toString().take(8)}.pdf"
            val pdfRef = storageRef.child("rotas_pdf/${baseId}/$fileName")
            
            val uploadTask = pdfRef.putBytes(pdfBytes).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await()
            
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletar PDF do Storage
     */
    suspend fun deletePdf(pdfUrl: String): Result<Unit> {
        return try {
            val pdfRef = storage.getReferenceFromUrl(pdfUrl)
            pdfRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Listar PDFs de uma base
     */
    suspend fun listPdfsForBase(baseId: String): Result<List<String>> {
        return try {
            val pdfsRef = storageRef.child("rotas_pdf/$baseId")
            val listResult = pdfsRef.listAll().await()
            
            val downloadUrls = listResult.items.map { item ->
                item.downloadUrl.await().toString()
            }
            
            Result.success(downloadUrls)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}



