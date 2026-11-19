package com.controleescalas.app.data.repositories

import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.Escala
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.ui.screens.DriverEscalaInfo
import com.controleescalas.app.data.models.toDriverEscalaInfo
import com.google.firebase.firestore.FieldPath
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

/**
 * Repository para operações de Escala
 */
class EscalaRepository {
    private val firestore = FirebaseManager.firestore
    
    /**
     * Garante que o usuário esteja autenticado (anônimo ou não)
     */
    private suspend fun ensureAuth() {
        if (FirebaseManager.auth.currentUser == null) {
            println("👤 EscalaRepository: Usuário não autenticado. Tentando login anônimo...")
            try {
                FirebaseManager.auth.signInAnonymously().await()
                println("✅ EscalaRepository: Login anônimo realizado com sucesso: ${FirebaseManager.auth.currentUser?.uid}")
            } catch (e: Exception) {
                println("❌ EscalaRepository: Falha no login anônimo: ${e.message}")
            }
        }
    }
    
    /**
     * Buscar escala do dia para um motorista
     * Busca APENAS na escala de HOJE em ambos os turnos (AM e PM)
     * Não busca em outros dias - se não estiver escalado hoje, retorna null
     */
    suspend fun getEscalaDoDia(baseId: String, motoristaId: String): DriverEscalaInfo? {
        return try {
            ensureAuth()
            val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            println("🔍 EscalaRepository.getEscalaDoDia: Buscando escala para motorista $motoristaId na base $baseId para data $hoje (APENAS HOJE)")
            
            // Buscar em ambos os turnos APENAS de hoje
            val turnos = listOf("AM", "PM")
            
            for (turno in turnos) {
                val docId = "${hoje}_${turno}"
                println("📂 EscalaRepository.getEscalaDoDia: Verificando documento $docId")
                
                val doc = firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("escalas")
                    .document(docId)
                    .get()
                    .await()
                
                if (doc.exists()) {
                    println("✅ EscalaRepository.getEscalaDoDia: Documento $docId existe")
                    val escala = doc.toObject(Escala::class.java)
                    
                    if (escala != null) {
                        println("📋 EscalaRepository.getEscalaDoDia: Escala carregada com ${escala.ondas.size} ondas")
                        
                        // Debug: listar todos os motoristas nas ondas
                        escala.ondas.forEachIndexed { ondaIndex, onda ->
                            println("  📊 Onda $ondaIndex (${onda.nome}): ${onda.itens.size} motoristas")
                            onda.itens.forEach { item ->
                                println("    👤 Motorista: '${item.motoristaId.trim()}' - ${item.nome} (Buscando: '$motoristaId.trim()')")
                                if (item.motoristaId.trim() == motoristaId.trim()) {
                                    println("    ✅ MATCH! Motorista encontrado!")
                                }
                            }
                        }
                        
                        // Verificar se motorista está nesta escala
                        val escalaInfo = escala.toDriverEscalaInfo(motoristaId)
                        if (escalaInfo != null) {
                            println("✅ EscalaRepository.getEscalaDoDia: Motorista encontrado no turno $turno")
                            return escalaInfo
                        } else {
                            println("⚠️ EscalaRepository.getEscalaDoDia: Motorista $motoristaId não encontrado na escala do turno $turno")
                        }
                    } else {
                        println("⚠️ EscalaRepository.getEscalaDoDia: Documento existe mas não pôde ser convertido para Escala")
                    }
                } else {
                    println("⚠️ EscalaRepository.getEscalaDoDia: Documento $docId não existe")
                }
            }
            
            println("ℹ️ EscalaRepository.getEscalaDoDia: Motorista não encontrado em nenhum turno de HOJE")
            null
        } catch (e: Exception) {
            println("❌ EscalaRepository.getEscalaDoDia erro: ${e.message}")
            e.printStackTrace()
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
    
    /**
     * Buscar escala por data e turno
     */
    suspend fun getEscalaByDateAndTurno(baseId: String, data: String, turno: String): Escala? {
        return try {
            ensureAuth()
            val docId = "${data}_${turno}"
            println("🔍 EscalaRepository.getEscalaByDateAndTurno: Buscando escala $docId")
            
            val doc = firestore
                .collection("bases")
                .document(baseId)
                .collection("escalas")
                .document(docId)
                .get()
                .await()
            
            val escala = doc.toObject(Escala::class.java)
            if (escala != null) {
                println("✅ EscalaRepository.getEscalaByDateAndTurno: Escala encontrada com ${escala.ondas.size} ondas")
            } else {
                println("ℹ️ EscalaRepository.getEscalaByDateAndTurno: Nenhuma escala encontrada")
            }
            escala
        } catch (e: Exception) {
            println("❌ EscalaRepository.getEscalaByDateAndTurno erro: ${e.message}")
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
            
            // ✅ DEBUG: Log para verificar qual data está sendo usada
            println("🔍 DEBUG EscalaRepository.saveEscala: escala.data = '${escala.data}', escala.turno = '${escala.turno}'")
            println("🔍 DEBUG EscalaRepository.saveEscala: docId calculado = '$docId'")
            println("💾 EscalaRepository.saveEscala: Salvando escala $docId para base $baseId")
            
            firestore
                .collection("bases")
                .document(baseId)
                .collection("escalas")
                .document(docId)
                .set(escala)
                .await()
            
            println("✅ EscalaRepository.saveEscala: Escala salva com sucesso!")
        } catch (e: Exception) {
            println("❌ EscalaRepository.saveEscala erro: ${e.message}")
            throw e
        }
    }
    
    /**
     * Buscar todas as escalas de um motorista em um mês específico
     * Retorna um Set com as datas (formato: "dd/MM/yyyy") em que o motorista está escalado
     */
    suspend fun getEscalasDoMes(baseId: String, motoristaId: String, mes: Int, ano: Int): Set<String> {
        return try {
            ensureAuth()
            val datasEscaladas = mutableSetOf<String>()
            
            // Calcular primeiro e último dia do mês
            val calendar = Calendar.getInstance()
            calendar.set(ano, mes - 1, 1) // mes - 1 porque Calendar usa 0-11
            val ultimoDia = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            // Montar os IDs de todos os documentos do mês (dia x turno)
            val formatoConsulta = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formatoExibicao = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val idParaDataExibicao = mutableMapOf<String, String>()
            val idsDocumentos = mutableListOf<String>()
            for (dia in 1..ultimoDia) {
                calendar.set(ano, mes - 1, dia)
                val dataFormatada = formatoConsulta.format(calendar.time)
                val dataExibicao = formatoExibicao.format(calendar.time)
                for (turno in listOf("AM", "PM")) {
                    val docId = "${dataFormatada}_${turno}"
                    idsDocumentos.add(docId)
                    idParaDataExibicao[docId] = dataExibicao
                }
            }

            // Consultar em chunks de até 30 IDs (limite do whereIn). Em vez de até 62
            // leituras individuais — cobradas mesmo quando o documento não existe —,
            // são 2-3 consultas que só retornam (e só cobram) as escalas existentes.
            for (chunk in idsDocumentos.chunked(30)) {
                val snapshot = firestore
                    .collection("bases")
                    .document(baseId)
                    .collection("escalas")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()

                for (doc in snapshot.documents) {
                    val escala = doc.toObject(Escala::class.java) ?: continue
                    // Verificar se o motorista está nesta escala
                    val escalaInfo = escala.toDriverEscalaInfo(motoristaId)
                    if (escalaInfo != null) {
                        idParaDataExibicao[doc.id]?.let { datasEscaladas.add(it) }
                    }
                }
            }
            
            println("✅ EscalaRepository.getEscalasDoMes: Encontradas ${datasEscaladas.size} datas escaladas para motorista $motoristaId no mês $mes/$ano")
            datasEscaladas
        } catch (e: Exception) {
            println("❌ EscalaRepository.getEscalasDoMes erro: ${e.message}")
            emptySet()
        }
    }
    
    /**
     * Observar mudanças na escala do motorista em tempo real
     * Monitora APENAS a escala de HOJE em ambos os turnos (AM e PM)
     * Não monitora outros dias - se não estiver escalado hoje, retorna null
     */
    fun observeEscalaDoMotorista(
        baseId: String,
        motoristaId: String,
        onUpdate: (DriverEscalaInfo?) -> Unit,
        onError: (Exception) -> Unit
    ): List<com.google.firebase.firestore.ListenerRegistration> {
        val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val turnos = listOf("AM", "PM")
        val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
        
        // Estado compartilhado para rastrear resultados de ambos os turnos
        val resultadosTurnos = mutableMapOf<String, DriverEscalaInfo?>()
        var jaChamouNull = false
        
        println("🎧 EscalaRepository.observeEscalaDoMotorista: Iniciando listeners para motorista $motoristaId na base $baseId para data $hoje (APENAS HOJE)")
        
        // Função para verificar se ambos os turnos foram verificados e chamar onUpdate se necessário
        fun verificarEAtualizar(turno: String, escalaInfo: DriverEscalaInfo?) {
            resultadosTurnos[turno] = escalaInfo
            
            // Se encontrou em algum turno, atualizar imediatamente e resetar flag
            if (escalaInfo != null) {
                println("✅ EscalaRepository.observeEscalaDoMotorista: Motorista encontrado no turno $turno, atualizando imediatamente")
                jaChamouNull = false
                onUpdate(escalaInfo)
                return
            }
            
            // Se ambos os turnos foram verificados e não encontrou em nenhum, chamar onUpdate(null) apenas uma vez
            if (resultadosTurnos.size >= turnos.size) {
                val encontradoEmAlgumTurno = resultadosTurnos.values.any { it != null }
                if (!encontradoEmAlgumTurno && !jaChamouNull) {
                    println("⚠️ EscalaRepository.observeEscalaDoMotorista: Motorista não encontrado em nenhum turno, chamando onUpdate(null)")
                    jaChamouNull = true
                    onUpdate(null)
                }
            }
        }
        
        // Criar listeners APENAS para hoje (2 turnos: AM e PM)
        for (turno in turnos) {
            val docId = "${hoje}_${turno}"
            println("🎧 EscalaRepository.observeEscalaDoMotorista: Configurando listener para documento $docId (HOJE - Turno $turno)")
            
            val listener = firestore
                .collection("bases")
                .document(baseId)
                .collection("escalas")
                .document(docId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        println("❌ EscalaRepository.observeEscalaDoMotorista: Erro no listener para $docId: ${error.message}")
                        onError(error)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null && snapshot.exists()) {
                        println("✅ EscalaRepository.observeEscalaDoMotorista: Documento $docId atualizado (HOJE - Turno $turno)")
                        val escala = snapshot.toObject(Escala::class.java)
                        
                        if (escala != null) {
                            println("📋 EscalaRepository.observeEscalaDoMotorista: Escala carregada com ${escala.ondas.size} ondas")
                            
                            // Debug: listar todos os motoristas
                            escala.ondas.forEachIndexed { _, onda ->
                                onda.itens.forEach { item ->
                                    println("    👤 Motorista: '${item.motoristaId.trim()}' - ${item.nome} (Buscando: '${motoristaId.trim()}')")
                                    if (item.motoristaId.trim() == motoristaId.trim()) {
                                        println("    ✅ MATCH! Motorista encontrado na onda!")
                                    }
                                }
                            }
                            
                            val escalaInfo = escala.toDriverEscalaInfo(motoristaId)
                            if (escalaInfo != null) {
                                println("✅ EscalaRepository.observeEscalaDoMotorista: Motorista encontrado no turno $turno")
                                verificarEAtualizar(turno, escalaInfo)
                            } else {
                                println("⚠️ EscalaRepository.observeEscalaDoMotorista: Motorista não encontrado no turno $turno")
                                verificarEAtualizar(turno, null)
                            }
                        } else {
                            println("⚠️ EscalaRepository.observeEscalaDoMotorista: Escala é null para $docId")
                            verificarEAtualizar(turno, null)
                        }
                    } else {
                        println("⚠️ EscalaRepository.observeEscalaDoMotorista: Documento $docId não existe")
                        verificarEAtualizar(turno, null)
                    }
                }
            listeners.add(listener)
        }
        
        println("✅ EscalaRepository.observeEscalaDoMotorista: ${listeners.size} listeners configurados (APENAS HOJE - 2 turnos: AM e PM)")
        return listeners
    }
    
    /**
     * Limpar todas as ondas (escalas) do dia anterior
     * Remove todas as escalas com data anterior à data atual
     */
    suspend fun limparOndasDoDiaAnterior(baseId: String): Boolean {
        return try {
            ensureAuth()
            val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            println("🧹 EscalaRepository.limparOndasDoDiaAnterior: Limpando ondas anteriores a $hoje para base $baseId")
            
            // Buscar todas as escalas da base
            val escalasSnapshot = firestore
                .collection("bases")
                .document(baseId)
                .collection("escalas")
                .get()
                .await()
            
            var removidas = 0
            // Remover em lotes (máx. 450 por lote) em vez de um delete() por escala
            var batch = firestore.batch()
            var opsNoBatch = 0
            for (doc in escalasSnapshot.documents) {
                val escala = doc.toObject(Escala::class.java) ?: continue
                // Comparar datas (formato: "yyyy-MM-dd")
                if (escala.data < hoje) {
                    batch.delete(doc.reference)
                    removidas++
                    opsNoBatch++
                    if (opsNoBatch == 450) {
                        batch.commit().await()
                        batch = firestore.batch()
                        opsNoBatch = 0
                    }
                    println("🗑️ EscalaRepository: Escala marcada para remoção - ${doc.id} (data: ${escala.data})")
                }
            }
            if (opsNoBatch > 0) {
                batch.commit().await()
            }
            
            println("✅ EscalaRepository.limparOndasDoDiaAnterior: $removidas escalas removidas")
            true
        } catch (e: Exception) {
            println("❌ EscalaRepository.limparOndasDoDiaAnterior: Erro - ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Limpar todas as ondas de todas as bases
     */
    suspend fun limparOndasDeTodasBases(): Boolean {
        return try {
            ensureAuth()
            println("🧹 EscalaRepository.limparOndasDeTodasBases: Iniciando limpeza de todas as bases")
            
            // Buscar todas as bases
            val basesSnapshot = firestore.collection("bases").get().await()
            var totalRemovidas = 0
            var basesProcessadas = 0
            
            for (baseDoc in basesSnapshot.documents) {
                val baseId = baseDoc.id
                limparOndasDoDiaAnterior(baseId)
                basesProcessadas++
            }
            
            println("✅ EscalaRepository.limparOndasDeTodasBases: $basesProcessadas bases processadas")
            true
        } catch (e: Exception) {
            println("❌ EscalaRepository.limparOndasDeTodasBases: Erro - ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
