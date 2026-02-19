package com.controleescalas.app.data.repositories

import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.NotificationApiService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository para pedido de localização/ETA do motorista.
 * Usado pelo assistente quando admin pergunta "quanto tempo para X chegar?".
 * Usa o backend Python (não Cloud Functions).
 */
class LocationRequestRepository {
    private val firestore = FirebaseManager.firestore
    private val apiService = NotificationApiService()

    /**
     * Solicita localização do motorista (admin/assistente).
     * Chama o backend Python que envia push silenciosa para o app do motorista.
     */
    suspend fun requestDriverLocation(baseId: String, motoristaId: String): Result<Unit> {
        val user = FirebaseManager.auth.currentUser ?: return Result.failure(SecurityException("Usuário não autenticado"))
        return try {
            val tokenResult = user.getIdToken(true).await()
            val idToken = tokenResult?.token ?: return Result.failure(SecurityException("Token inválido"))
            val (success, error) = apiService.requestDriverLocation(baseId, motoristaId, idToken)
            if (success) Result.success(Unit) else Result.failure(Exception(error))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Escuta a resposta de localização do motorista em tempo real.
     * Firestore: bases/{baseId}/location_responses/{motoristaId}
     */
    fun listenToLocationResponse(baseId: String, motoristaId: String): Flow<LocationResponse> = callbackFlow {
        val ref = firestore
            .collection("bases")
            .document(baseId)
            .collection("location_responses")
            .document(motoristaId)

        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val data = snapshot?.data ?: return@addSnapshotListener
            val status = data["status"] as? String ?: "pending"
            trySend(
                LocationResponse(
                    status = status,
                    motoristaNome = data["motoristaNome"] as? String ?: "",
                    distanceKm = (data["distanceKm"] as? Number)?.toDouble(),
                    etaMinutes = (data["etaMinutes"] as? Number)?.toInt(),
                    error = data["error"] as? String
                )
            )
        }
        awaitClose { listener.remove() }
    }
}

data class LocationResponse(
    val status: String,
    val motoristaNome: String,
    val distanceKm: Double?,
    val etaMinutes: Int?,
    val error: String?
)
