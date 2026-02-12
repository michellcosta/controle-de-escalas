package com.controleescalas.app.data

import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.data.models.Base
import com.controleescalas.app.data.models.ConfiguracaoBase
import com.controleescalas.app.data.models.Escala
import com.controleescalas.app.data.models.GeofenceConfig
import com.controleescalas.app.data.models.Motorista
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.data.models.StatusMotorista
import com.controleescalas.app.data.models.toAdminMotoristaCardData
import com.controleescalas.app.data.models.toDriverEscalaInfo
import com.controleescalas.app.data.models.toDriverStatusInfo


import com.controleescalas.app.ui.screens.DriverEscalaInfo
import com.controleescalas.app.ui.screens.DriverStatusInfo
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository para operações do Firestore
 * 
 * Centraliza todas as operações de banco de dados
 */
class Repository {
    val firestore = FirebaseManager.firestore
    
    /**
     * Garante que o usuário esteja autenticado (anônimo ou não)
     */
    private suspend fun ensureAuth() {
        if (FirebaseManager.auth.currentUser == null) {
            println("👤 Repository: Usuário não autenticado. Tentando login anônimo...")
            try {
                FirebaseManager.auth.signInAnonymously().await()
                println("✅ Repository: Login anônimo realizado com sucesso: ${FirebaseManager.auth.currentUser?.uid}")
            } catch (e: Exception) {
                println("❌ Repository: Falha no login anônimo: ${e.message}")
            }
        }
    }
    
    /**
     * Normalizar telefone removendo caracteres especiais
     */
    private fun normalizeTelefone(telefone: String): String {
        return telefone.replace(Regex("[^0-9]"), "")
    }
    
    // ========== OPERAÇÕES DE BASE ==========
    
    /**
     * Criar nova base
     */
    suspend fun createBase(baseData: com.controleescalas.app.ui.screens.CreateBaseData): String? {
        return try {
            ensureAuth()
            println("🏗️ Repository: Criando base: ${baseData.nomeBase}")
            
            val base = Base(
                nome = baseData.nomeBase,
                transportadora = baseData.nomeTransportadora,
                corTema = baseData.corTema
            )
            
            val docRef = firestore.collection("bases").add(base).await()
            val baseId = docRef.id
            
            println("✅ Repository: Base criada com ID: $baseId")
            
            // Criar admin da base
            val adminId = createMotorista(
                baseId = baseId,
                nome = "Admin",
                telefone = baseData.telefoneAdmin,
                pin = baseData.pinAdmin,
                papel = "admin"
            )
            
            if (adminId != null) {
                println("✅ Repository: Admin criado com ID: $adminId")
            } else {
                println("❌ Repository: Erro ao criar admin")
            }
            
            baseId
        } catch (e: Exception) {
            println("❌ Repository: Erro ao criar base: ${e.message}")
            null
        }
    }
    
    /**
     * Buscar base por ID
     */
    suspend fun getBase(baseId: String): Base? {
        return try {
            ensureAuth()
            val doc = firestore.collection("bases").document(baseId).get().await()
            doc.toObject(Base::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    // ========== OPERAÇÕES DE MOTORISTA ==========
    
    /**
     * Criar motorista
     */
    suspend fun createMotorista(
        baseId: String,
        nome: String,
        telefone: String,
        pin: String,
        papel: String = "motorista",
        criadoPor: String? = null
    ): String? {
        return try {
            ensureAuth()
            println("👤 Repository: Criando motorista: $nome ($papel) - Telefone: $telefone")
            
            val telefoneNormalizado = normalizeTelefone(telefone)
            println("📱 Repository: Telefone original: '$telefone' -> Normalizado: '$telefoneNormalizado'")
            
            val motorista = Motorista(
                nome = nome,
                telefone = telefoneNormalizado,
                pinHash = pin, // Já deve vir como hash
                papel = papel,
                baseId = baseId,
                criadoPor = criadoPor
            )
            
            val docRef = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .add(motorista)
                .await()
            
            val motoristaId = docRef.id
            println("✅ Repository: Motorista criado com ID: $motoristaId")
            
            motoristaId
        } catch (e: Exception) {
            println("❌ Repository: Erro ao criar motorista: ${e.message}")
            null
        }
    }
    
    /**
     * Buscar motoristas de uma base
     */
    suspend fun getMotoristas(baseId: String): List<AdminMotoristaCardData> {
        return try {
            ensureAuth()
            val snapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .whereEqualTo("ativo", true)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                val motorista = doc.toObject(Motorista::class.java)
                motorista?.toAdminMotoristaCardData()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Buscar motorista por telefone
     * Usa busca em todas as bases para encontrar o motorista
     */
    suspend fun getMotoristaByTelefone(telefone: String): Motorista? {
        return try {
            ensureAuth()
            val telefoneNormalizado = normalizeTelefone(telefone)
            println("🔍 Repository: Buscando telefone original: '$telefone' -> Normalizado: '$telefoneNormalizado'")
            
            // Primeiro, tentar usar collectionGroup (mais eficiente se configurado)
            try {
                val snapshot = firestore
                    .collectionGroup("motoristas")
                    .whereEqualTo("telefone", telefoneNormalizado)
                    .whereEqualTo("ativo", true)
                    .limit(1)
                    .get()
                    .await()
                
                val motorista = snapshot.documents.firstOrNull()?.toObject(Motorista::class.java)
                if (motorista != null) {
                    println("✅ Repository: Motorista encontrado via collectionGroup: ${motorista.nome}")
                    return motorista
                }
            } catch (e: Exception) {
                // Se collectionGroup falhar, usar busca manual
                println("⚠️ Repository: CollectionGroup falhou, usando busca manual: ${e.message}")
            }
            
            // Busca manual em todas as bases
            println("🔍 Repository: Iniciando busca manual em todas as bases...")
            val basesSnapshot = firestore.collection("bases").get().await()
            println("📊 Repository: Encontradas ${basesSnapshot.documents.size} bases para buscar")
            
            for (baseDoc in basesSnapshot.documents) {
                val motoristasSnapshot = baseDoc.reference
                    .collection("motoristas")
                    .whereEqualTo("telefone", telefoneNormalizado)
                    .whereEqualTo("ativo", true)
                    .limit(1)
                    .get()
                    .await()
                
                val motorista = motoristasSnapshot.documents.firstOrNull()?.toObject(Motorista::class.java)
                if (motorista != null) {
                    println("✅ Repository: Motorista encontrado via busca manual: ${motorista.nome}")
                    return motorista.copy(id = motoristasSnapshot.documents.firstOrNull()?.id ?: "")
                }
            }
            
            println("❌ Repository: Nenhum motorista encontrado com telefone: '$telefoneNormalizado'")
            null
        } catch (e: Exception) {
            println("❌ Repository: Erro ao buscar motorista por telefone: ${e.message}")
            null
        }
    }
    
    /**
     * Função de debug para listar todos os motoristas
     */
    suspend fun debugListAllMotoristas() {
        try {
            ensureAuth()
            println("🔍 DEBUG: Listando todos os motoristas...")
            val basesSnapshot = firestore.collection("bases").get().await()
            println("📊 DEBUG: Encontradas ${basesSnapshot.documents.size} bases")
            
            for (baseDoc in basesSnapshot.documents) {
                val baseId = baseDoc.id
                val baseData = baseDoc.toObject(Base::class.java)
                println("🏢 DEBUG: Base: ${baseData?.nome ?: "N/A"} (ID: $baseId)")
                
                val motoristasSnapshot = baseDoc.reference
                    .collection("motoristas")
                    .get()
                    .await()
                
                println("👥 DEBUG: ${motoristasSnapshot.documents.size} motoristas nesta base:")
                for (motoristaDoc in motoristasSnapshot.documents) {
                    val motorista = motoristaDoc.toObject(Motorista::class.java)
                    motorista?.let {
                        println("  - ${it.nome} | ${it.telefone} | ${it.papel} | Ativo: ${it.ativo}")
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ DEBUG: Erro ao listar motoristas: ${e.message}")
        }
    }
    
    /**
     * Atualizar papel do usuário
     */
    suspend fun updateUserRole(userId: String, baseId: String, newRole: String) {
        try {
            ensureAuth()
            firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(userId)
                .update("papel", newRole)
                .await()
            
            println("✅ Repository: Papel atualizado para $newRole")
        } catch (e: Exception) {
            println("❌ Repository: Erro ao atualizar papel: ${e.message}")
            throw e
        }
    }
    
    /**
     * Remover usuário
     */
    suspend fun removeUser(userId: String, baseId: String) {
        try {
            ensureAuth()
            firestore
                .collection("bases")
                .document(baseId)
                .collection("motoristas")
                .document(userId)
                .update("ativo", false)
                .await()
            
            println("✅ Repository: Usuário removido (desativado)")
        } catch (e: Exception) {
            println("❌ Repository: Erro ao remover usuário: ${e.message}")
            throw e
        }
    }
    
    // ========== OPERAÇÕES DE ESCALA ==========
    
    /**
     * Buscar escala do dia para um motorista
     * Busca em ambos os turnos (AM e PM) até encontrar o motorista
     */
    suspend fun getEscalaDoDia(baseId: String, motoristaId: String): DriverEscalaInfo? {
        return try {
            ensureAuth()
            val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            println("🔍 Repository.getEscalaDoDia: Buscando escala para motorista $motoristaId")
            
            // Buscar em ambos os turnos
            val turnos = listOf("AM", "PM")
            
            for (turno in turnos) {
                val docId = "${hoje}_${turno}"
                val doc = firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("escalas")
                    .document(docId)
                    .get()
                    .await()
                
                val escala = doc.toObject(Escala::class.java)
                
                // Verificar se motorista está nesta escala
                val escalaInfo = escala?.toDriverEscalaInfo(motoristaId)
                if (escalaInfo != null) {
                    println("✅ Repository.getEscalaDoDia: Motorista encontrado no turno $turno")
                    return escalaInfo
                }
            }
            
            println("ℹ️ Repository.getEscalaDoDia: Motorista não encontrado em nenhum turno")
            null
        } catch (e: Exception) {
            println("❌ Repository.getEscalaDoDia erro: ${e.message}")
            null
        }
    }
    
    /**
     * Buscar ondas de um turno
     */
    suspend fun getOndasDoTurno(baseId: String, turno: String): List<Onda> {
        return try {
            ensureAuth()
            val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val escalaDoc = firestore
                .collection("bases")
                .document(baseId)
                .collection("escalas")
                .document(hoje)
                .get()
                .await()

            val escala = escalaDoc.toObject(Escala::class.java)
            escala?.ondas ?: emptyList()
        } catch (e: Exception) {
            println("❌ Erro ao buscar ondas: ${e.message}")
            emptyList()
        }
    }
    
    // ========== OPERAÇÕES DE STATUS ==========
    
    /**
     * Buscar status atual de um motorista
     */
    suspend fun getStatusMotorista(motoristaId: String): DriverStatusInfo? {
        return try {
            ensureAuth()
            val snapshot = firestore
                .collectionGroup("statusMotoristas")
                .whereEqualTo("motoristaId", motoristaId)
                .orderBy("atualizadoEm", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            
            val status = snapshot.documents.firstOrNull()?.toObject(StatusMotorista::class.java)
            status?.toDriverStatusInfo()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Atualizar status de um motorista
     */
    suspend fun updateStatusMotorista(
        motoristaId: String,
        baseId: String,
        estado: String,
        mensagem: String = "",
        vagaAtual: String? = null,
        rotaAtual: String? = null
    ): Boolean {
        return try {
            ensureAuth()
            val status = StatusMotorista(
                motoristaId = motoristaId,
                baseId = baseId,
                estado = estado,
                mensagem = mensagem,
                vagaAtual = vagaAtual,
                rotaAtual = rotaAtual
            )
            
            firestore
                .collection("bases")
                .document(baseId)
                .collection("statusMotoristas")
                .add(status)
                .await()
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // ========== OPERAÇÕES DE CONFIGURAÇÃO ==========
    
    /**
     * Buscar configuração de uma base
     */
    suspend fun getConfiguracaoBase(baseId: String): ConfiguracaoBase? {
        return try {
            ensureAuth()
            val doc = firestore
                .collection("bases")
                .document(baseId)
                .collection("configuracao")
                .document("principal")
                .get()
                .await()
            
            doc.toObject(ConfiguracaoBase::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Salvar configuração de geofence
     */
    suspend fun saveGeofenceConfig(
        baseId: String,
        tipo: String, // "galpao" ou "estacionamento"
        lat: Double,
        lng: Double,
        raio: Int
    ): Boolean {
        return try {
            ensureAuth()
            val config = ConfiguracaoBase(
                baseId = baseId,
                galpao = if (tipo == "galpao") GeofenceConfig(lat, lng, raio) else GeofenceConfig(),
                estacionamento = if (tipo == "estacionamento") GeofenceConfig(lat, lng, raio) else GeofenceConfig()
            )
            
            firestore
                .collection("bases")
                .document(baseId)
                .collection("configuracao")
                .document("principal")
                .set(config)
                .await()
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Buscar escala por data e turno
     */
    suspend fun getEscalaByDateAndTurno(baseId: String, data: String, turno: String): Escala? {
        return try {
            ensureAuth()
            val docId = "${data}_${turno}"
            println("🔍 Repository.getEscalaByDateAndTurno: Buscando escala $docId")
            
            val doc = firestore
                .collection("bases")
                .document(baseId)
                .collection("escalas")
                .document(docId)
                .get()
                .await()
            
            val escala = doc.toObject(Escala::class.java)
            if (escala != null) {
                println("✅ Repository.getEscalaByDateAndTurno: Escala encontrada com ${escala.ondas.size} ondas")
            } else {
                println("ℹ️ Repository.getEscalaByDateAndTurno: Nenhuma escala encontrada")
            }
            escala
        } catch (e: Exception) {
            println("❌ Repository.getEscalaByDateAndTurno erro: ${e.message}")
            null
        }
    }
    
    /**
     * Salvar escala no Firestore
     */
    suspend fun saveEscala(baseId: String, escala: Escala) {
        try {
            ensureAuth()
            val docId = "${escala.data}_${escala.turno}"
            println("💾 Repository.saveEscala: Salvando escala $docId para base $baseId")
            
            firestore
                .collection("bases")
                .document(baseId)
                .collection("escalas")
                .document(docId)
                .set(escala)
                .await()
            
            println("✅ Repository.saveEscala: Escala salva com sucesso!")
        } catch (e: Exception) {
            println("❌ Repository.saveEscala erro: ${e.message}")
            throw e
        }
    }
}
