package com.controleescalas.app.data.repositories

import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.Feedback
import com.controleescalas.app.data.models.FeedbackStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await

/**
 * Repository para operações de feedback
 */
class FeedbackRepository {
    private val firestore = FirebaseManager.firestore
    private val feedbacksRef = firestore.collection("feedbacks")

    /**
     * Criar novo feedback
     */
    suspend fun criarFeedback(
        baseId: String,
        adminId: String,
        adminNome: String,
        baseNome: String,
        mensagem: String
    ): String? {
        return try {
            val feedback = Feedback(
                baseId = baseId,
                adminId = adminId,
                adminNome = adminNome,
                baseNome = baseNome,
                mensagem = mensagem,
                data = System.currentTimeMillis(),
                status = FeedbackStatus.NOVO
            )

            val docRef = feedbacksRef.add(feedback).await()
            println("✅ FeedbackRepository: Feedback criado com ID: ${docRef.id}")
            docRef.id
        } catch (e: Exception) {
            println("❌ FeedbackRepository: Erro ao criar feedback: ${e.message}")
            null
        }
    }

    /**
     * Buscar feedbacks de um admin específico
     */
    suspend fun getMeusFeedbacks(adminId: String): List<Feedback> {
        return try {
            val snapshot = feedbacksRef
                .whereEqualTo("adminId", adminId)
                .orderBy("data", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                converterDocumentoParaFeedback(doc)
            }
        } catch (e: Exception) {
            println("❌ FeedbackRepository: Erro ao buscar feedbacks: ${e.message}")
            emptyList()
        }
    }

    /**
     * Buscar todos os feedbacks (para super admin)
     */
    suspend fun getAllFeedbacks(): List<Feedback> {
        return try {
            val snapshot = feedbacksRef
                .orderBy("data", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                converterDocumentoParaFeedback(doc)
            }
        } catch (e: Exception) {
            println("❌ FeedbackRepository: Erro ao buscar todos os feedbacks: ${e.message}")
            emptyList()
        }
    }

    /**
     * Marcar feedback como lido
     */
    suspend fun marcarComoLido(feedbackId: String): Boolean {
        return try {
            feedbacksRef.document(feedbackId)
                .update("status", FeedbackStatus.LIDO.name)
                .await()
            println("✅ FeedbackRepository: Feedback $feedbackId marcado como lido")
            true
        } catch (e: Exception) {
            println("❌ FeedbackRepository: Erro ao marcar como lido: ${e.message}")
            false
        }
    }

    /**
     * Curtir feedback
     */
    suspend fun curtirFeedback(feedbackId: String, superAdminId: String): Boolean {
        return try {
            val agora = System.currentTimeMillis()
            feedbacksRef.document(feedbackId)
                .update(
                    mapOf(
                        "status" to FeedbackStatus.CURTIDO.name,
                        "curtidoPor" to superAdminId,
                        "curtidoEm" to agora
                    )
                )
                .await()
            println("✅ FeedbackRepository: Feedback $feedbackId curtido por $superAdminId")
            true
        } catch (e: Exception) {
            println("❌ FeedbackRepository: Erro ao curtir feedback: ${e.message}")
            false
        }
    }

    /**
     * Editar mensagem do feedback
     */
    suspend fun editarFeedback(feedbackId: String, novaMensagem: String): Boolean {
        return try {
            feedbacksRef.document(feedbackId)
                .update("mensagem", novaMensagem.trim())
                .await()
            println("✅ FeedbackRepository: Feedback $feedbackId editado")
            true
        } catch (e: Exception) {
            println("❌ FeedbackRepository: Erro ao editar feedback: ${e.message}")
            false
        }
    }

    /**
     * Excluir feedback
     */
    suspend fun excluirFeedback(feedbackId: String): Boolean {
        return try {
            feedbacksRef.document(feedbackId)
                .delete()
                .await()
            println("✅ FeedbackRepository: Feedback $feedbackId excluído")
            true
        } catch (e: Exception) {
            println("❌ FeedbackRepository: Erro ao excluir feedback: ${e.message}")
            false
        }
    }

    /**
     * Converter DocumentSnapshot para Feedback
     */
    private fun converterDocumentoParaFeedback(doc: DocumentSnapshot): Feedback? {
        return try {
            val statusString = doc.getString("status") ?: "NOVO"
            val status = try {
                FeedbackStatus.valueOf(statusString)
            } catch (e: Exception) {
                FeedbackStatus.NOVO
            }

            Feedback(
                id = doc.id,
                baseId = doc.getString("baseId") ?: "",
                adminId = doc.getString("adminId") ?: "",
                adminNome = doc.getString("adminNome") ?: "",
                baseNome = doc.getString("baseNome") ?: "",
                mensagem = doc.getString("mensagem") ?: "",
                data = extrairTimestamp(doc, "data"),
                status = status,
                curtidoPor = doc.getString("curtidoPor"),
                curtidoEm = extrairTimestamp(doc, "curtidoEm")
            )
        } catch (e: Exception) {
            println("❌ FeedbackRepository: Erro ao converter documento: ${e.message}")
            null
        }
    }

    /**
     * Extrair timestamp do documento
     */
    private fun extrairTimestamp(doc: DocumentSnapshot, campo: String): Long {
        return try {
            val valor = doc.get(campo)
            when (valor) {
                is Timestamp -> valor.toDate().time
                is Long -> valor
                is Number -> valor.toLong()
                else -> System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}

