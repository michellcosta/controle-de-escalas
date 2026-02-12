package com.controleescalas.app.data.models

import kotlinx.serialization.Serializable

/**
 * Representa uma sessão salva de um usuário em uma transportadora
 */
@Serializable
data class SavedSession(
    val userId: String,           // ID do documento do motorista/admin no Firestore (não telefone)
    val baseId: String,           // ID da transportadora
    val baseName: String,         // Nome da transportadora
    val userName: String,         // Nome do usuário
    val userRole: String,         // Papel: admin, auxiliar, motorista
    val lastAccessTimestamp: Long = System.currentTimeMillis() // Última vez que usou
)

/**
 * Container para todas as sessões do dispositivo
 */
@Serializable
data class UserSessions(
    val sessions: List<SavedSession> = emptyList(),
    val lastActiveSessionIndex: Int = -1  // Índice da última sessão ativa
) {
    fun getLastActiveSession(): SavedSession? {
        return if (lastActiveSessionIndex in sessions.indices) {
            sessions[lastActiveSessionIndex]
        } else null
    }
    
    fun getSessionsGroupedByBase(): Map<String, List<SavedSession>> {
        return sessions.groupBy { it.baseId }
    }
    
    fun addOrUpdateSession(session: SavedSession): UserSessions {
        val existingIndex = sessions.indexOfFirst { 
            it.userId == session.userId && it.baseId == session.baseId 
        }
        
        val updatedSessions = if (existingIndex >= 0) {
            // Atualizar sessão existente
            sessions.toMutableList().apply {
                set(existingIndex, session.copy(lastAccessTimestamp = System.currentTimeMillis()))
            }
        } else {
            // Adicionar nova sessão
            sessions + session.copy(lastAccessTimestamp = System.currentTimeMillis())
        }
        
        // Encontrar novo índice da sessão atualizada/adicionada
        val newIndex = updatedSessions.indexOfFirst {
            it.userId == session.userId && it.baseId == session.baseId
        }
        
        return copy(sessions = updatedSessions, lastActiveSessionIndex = newIndex)
    }
    
    fun removeSession(userId: String, baseId: String): UserSessions {
        val filteredSessions = sessions.filter { 
            !(it.userId == userId && it.baseId == baseId)
        }
        return copy(
            sessions = filteredSessions,
            lastActiveSessionIndex = if (filteredSessions.isEmpty()) -1 else 0
        )
    }
    
    fun removeSessionsByBaseId(baseId: String): UserSessions {
        val filteredSessions = sessions.filter { it.baseId != baseId }
        // Ajustar índice da última sessão ativa se necessário
        val newIndex = if (filteredSessions.isEmpty()) {
            -1
        } else if (lastActiveSessionIndex >= 0 && lastActiveSessionIndex < sessions.size) {
            val removedSession = sessions[lastActiveSessionIndex]
            if (removedSession.baseId == baseId) {
                // A última sessão ativa foi removida, usar a primeira disponível
                0
            } else {
                // Recalcular índice após remoção
                val oldSession = sessions[lastActiveSessionIndex]
                filteredSessions.indexOfFirst { 
                    it.userId == oldSession.userId && it.baseId == oldSession.baseId 
                }.takeIf { it >= 0 } ?: 0
            }
        } else {
            lastActiveSessionIndex
        }
        return copy(
            sessions = filteredSessions,
            lastActiveSessionIndex = newIndex
        )
    }
}

