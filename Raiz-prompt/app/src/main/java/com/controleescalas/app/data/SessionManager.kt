package com.controleescalas.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.controleescalas.app.data.models.SavedSession
import com.controleescalas.app.data.models.UserSessions
import com.controleescalas.app.data.models.Base
import com.controleescalas.app.data.repositories.BaseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Extensão para criar DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_sessions")

/**
 * Gerenciador de sessões de usuário
 * Persiste sessões usando DataStore
 */
class SessionManager(private val context: Context) {
    
    companion object {
        private val SESSIONS_KEY = stringPreferencesKey("user_sessions")
        private val THEME_KEY = stringPreferencesKey("theme_mode")
        private val json = Json { 
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    
    /**
     * Fluxo da preferência de tema (dark ou light)
     */
    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: "dark"
    }

    /**
     * Salvar preferência de tema
     */
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = mode
        }
    }
    
    /**
     * Fluxo de todas as sessões salvas
     */
    val userSessionsFlow: Flow<UserSessions> = context.dataStore.data.map { preferences ->
        val sessionsJson = preferences[SESSIONS_KEY] ?: ""
        if (sessionsJson.isBlank()) {
            UserSessions()
        } else {
            try {
                json.decodeFromString<UserSessions>(sessionsJson)
            } catch (e: Exception) {
                UserSessions()
            }
        }
    }
    
    /**
     * Obter sessões de forma suspensa (não flow)
     */
    suspend fun getUserSessions(): UserSessions {
        return userSessionsFlow.first()
    }
    
    /**
     * Obter última sessão ativa (para auto-login)
     */
    suspend fun getLastActiveSession(): SavedSession? {
        return getUserSessions().getLastActiveSession()
    }
    
    /**
     * Obter todas as sessões agrupadas por transportadora
     */
    suspend fun getSessionsGroupedByBase(): Map<String, List<SavedSession>> {
        return getUserSessions().getSessionsGroupedByBase()
    }
    
    /**
     * Salvar ou atualizar uma sessão
     * Define automaticamente como última sessão ativa
     */
    suspend fun saveSession(session: SavedSession) {
        println("💾 SessionManager.saveSession: Salvando sessão - baseId: ${session.baseId}, userId: ${session.userId}, baseName: ${session.baseName}, userName: ${session.userName}")
        context.dataStore.edit { preferences ->
            val currentSessions = try {
                val sessionsJson = preferences[SESSIONS_KEY] ?: ""
                if (sessionsJson.isBlank()) {
                    UserSessions()
                } else {
                    json.decodeFromString<UserSessions>(sessionsJson)
                }
            } catch (e: Exception) {
                println("⚠️ SessionManager.saveSession: Erro ao decodificar sessões existentes: ${e.message}")
                UserSessions()
            }
            
            val updatedSessions = currentSessions.addOrUpdateSession(session)
            preferences[SESSIONS_KEY] = json.encodeToString(updatedSessions)
            println("✅ SessionManager.saveSession: Sessão salva com sucesso - Total de sessões: ${updatedSessions.sessions.size}")
            updatedSessions.sessions.forEach { s ->
                println("   - ${s.baseName} (${s.userName}) - userId: ${s.userId}, baseId: ${s.baseId}, timestamp: ${s.lastAccessTimestamp}")
            }
        }
    }
    
    /**
     * Remover uma sessão específica
     */
    suspend fun removeSession(userId: String, baseId: String) {
        context.dataStore.edit { preferences ->
            val currentSessions = try {
                val sessionsJson = preferences[SESSIONS_KEY] ?: ""
                if (sessionsJson.isBlank()) {
                    UserSessions()
                } else {
                    json.decodeFromString<UserSessions>(sessionsJson)
                }
            } catch (e: Exception) {
                UserSessions()
            }
            
            val updatedSessions = currentSessions.removeSession(userId, baseId)
            preferences[SESSIONS_KEY] = json.encodeToString(updatedSessions)
        }
    }
    
    /**
     * Limpar todas as sessões (logout completo)
     */
    suspend fun clearAllSessions() {
        context.dataStore.edit { preferences ->
            preferences.remove(SESSIONS_KEY)
        }
    }
    
    /**
     * Remover sessões duplicadas
     * Remove sessões antigas que usam telefone como userId (apenas números)
     * em favor de sessões que usam ID do documento do Firestore
     */
    suspend fun removeDuplicateSessions() {
        println("🔍 SessionManager.removeDuplicateSessions: Iniciando limpeza de duplicatas")
        context.dataStore.edit { preferences ->
            val currentSessions = try {
                val sessionsJson = preferences[SESSIONS_KEY] ?: ""
                if (sessionsJson.isBlank()) {
                    return@edit
                }
                json.decodeFromString<UserSessions>(sessionsJson)
            } catch (e: Exception) {
                println("⚠️ SessionManager.removeDuplicateSessions: Erro ao decodificar sessões: ${e.message}")
                return@edit
            }
            
            // Agrupar sessões por baseId
            val sessionsByBase = currentSessions.sessions.groupBy { it.baseId }
            
            val cleanedSessions = mutableListOf<SavedSession>()
            
            for ((baseId, sessions) in sessionsByBase) {
                if (sessions.size == 1) {
                    // Não há duplicatas para esta base
                    cleanedSessions.add(sessions[0])
                } else {
                    // Há duplicatas, manter apenas a sessão com userId que NÃO seja apenas números (ID do documento)
                    val validSession = sessions.firstOrNull { session ->
                        !session.userId.matches(Regex("^[0-9]+$"))
                    } ?: sessions[0] // Se não encontrar, manter a primeira
                    
                    cleanedSessions.add(validSession)
                    
                    println("⚠️ SessionManager: Duplicatas encontradas para base $baseId:")
                    sessions.forEach { s ->
                        println("   - ${s.baseName} (${s.userName}) - userId: ${s.userId} ${if (s == validSession) "[MANTIDA]" else "[REMOVIDA]"}")
                    }
                }
            }
            
            val updatedSessions = UserSessions(cleanedSessions)
            preferences[SESSIONS_KEY] = json.encodeToString(updatedSessions)
            
            if (cleanedSessions.size != currentSessions.sessions.size) {
                println("✅ SessionManager: Duplicatas removidas - ${currentSessions.sessions.size} -> ${cleanedSessions.size} sessões")
            } else {
                println("✅ SessionManager: Nenhuma duplicata encontrada")
            }
        }
    }
    
    /**
     * Atualizar timestamp de acesso de uma sessão existente
     * Útil quando o usuário faz logout mas quer manter a sessão salva
     */
    suspend fun updateSessionTimestamp(userId: String, baseId: String) {
        println("🔄 SessionManager.updateSessionTimestamp: Atualizando timestamp para userId=$userId, baseId=$baseId")
        context.dataStore.edit { preferences ->
            val currentSessions = try {
                val sessionsJson = preferences[SESSIONS_KEY] ?: ""
                if (sessionsJson.isBlank()) {
                    UserSessions()
                } else {
                    json.decodeFromString<UserSessions>(sessionsJson)
                }
            } catch (e: Exception) {
                println("⚠️ SessionManager.updateSessionTimestamp: Erro ao decodificar sessões: ${e.message}")
                UserSessions()
            }
            
            val sessionIndex = currentSessions.sessions.indexOfFirst { 
                it.userId == userId && it.baseId == baseId 
            }
            
            if (sessionIndex >= 0) {
                val session = currentSessions.sessions[sessionIndex]
                val updatedSessions = currentSessions.sessions.toMutableList().apply {
                    set(sessionIndex, session.copy(lastAccessTimestamp = System.currentTimeMillis()))
                }
                preferences[SESSIONS_KEY] = json.encodeToString(
                    currentSessions.copy(sessions = updatedSessions)
                )
                println("✅ SessionManager: Timestamp atualizado para sessão ${session.baseName} (${session.userName})")
            } else {
                println("⚠️ SessionManager.updateSessionTimestamp: Sessão não encontrada para userId=$userId, baseId=$baseId")
            }
        }
    }
    
    /**
     * Validar se uma sessão ainda é válida
     * (verifica se os dados ainda existem no Firebase)
     */
    suspend fun validateSession(session: SavedSession): Boolean {
        return try {
            println("🔍 SessionManager: Validando sessão - baseId: ${session.baseId}, userId: ${session.userId}, baseName: ${session.baseName}")
            
            // Tentar até 3 vezes com delay entre tentativas
            var base: Base? = null
            var attempts = 0
            val maxAttempts = 3
            
            while (base == null && attempts < maxAttempts) {
                attempts++
                try {
                    val baseRepository = BaseRepository()
                    base = baseRepository.getBase(session.baseId)
                    
                    if (base == null && attempts < maxAttempts) {
                        println("⚠️ SessionManager: Tentativa $attempts/$maxAttempts - Base não encontrada, tentando novamente...")
                        delay((500 * attempts).toLong()) // Delay crescente
                    }
                } catch (e: Exception) {
                    if (attempts < maxAttempts) {
                        println("⚠️ SessionManager: Tentativa $attempts/$maxAttempts - Erro: ${e.message}, tentando novamente...")
                        delay((500 * attempts).toLong())
                    } else {
                        throw e
                    }
                }
            }
            
            if (base == null) {
                println("⚠️ SessionManager: Base ${session.baseId} não encontrada após $maxAttempts tentativas")
                return false
            }
            
            val isValid = base.statusAprovacao == "ativa"
            println("${if (isValid) "✅" else "⚠️"} SessionManager: Base ${session.baseId} (${session.baseName}) - status: ${base.statusAprovacao}, válida: $isValid")
            
            isValid
        } catch (e: Exception) {
            println("❌ SessionManager: Erro ao validar sessão ${session.baseId} após múltiplas tentativas: ${e.message}")
            e.printStackTrace()
            // Em caso de erro após todas as tentativas, retornar true para não remover sessões válidas
            true
        }
    }
    
    /**
     * Remover todas as sessões de uma base específica
     */
    suspend fun removeSessionsByBaseId(baseId: String) {
        context.dataStore.edit { preferences ->
            val currentSessions = try {
                val sessionsJson = preferences[SESSIONS_KEY] ?: ""
                if (sessionsJson.isBlank()) {
                    UserSessions()
                } else {
                    json.decodeFromString<UserSessions>(sessionsJson)
                }
            } catch (e: Exception) {
                UserSessions()
            }
            
            val updatedSessions = currentSessions.removeSessionsByBaseId(baseId)
            preferences[SESSIONS_KEY] = json.encodeToString(updatedSessions)
        }
    }
}

