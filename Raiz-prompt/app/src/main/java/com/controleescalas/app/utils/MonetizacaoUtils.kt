package com.controleescalas.app.utils

import com.controleescalas.app.data.repositories.SistemaRepository
import kotlinx.coroutines.runBlocking

/**
 * Utilitários para verificação de monetização
 */
object MonetizacaoUtils {
    private val sistemaRepository = SistemaRepository()

    /**
     * Verificar se monetização está ativa
     * Usa runBlocking para permitir uso em contextos não-suspend
     */
    fun verificarMonetizacaoAtiva(): Boolean {
        return runBlocking {
            sistemaRepository.verificarMonetizacaoAtiva()
        }
    }

    /**
     * Verificar se pode criar motorista (respeitando limite de 5)
     */
    suspend fun podeCriarMotorista(baseId: String, totalMotoristasAtivos: Int): Boolean {
        val monetizacaoAtiva = sistemaRepository.verificarMonetizacaoAtiva()
        
        if (!monetizacaoAtiva) {
            // Se monetização não está ativa, pode criar sem limites
            return true
        }
        
        // Se monetização está ativa, verificar plano da base
        // TODO: Implementar verificação de plano da base
        // Por enquanto, assumir que todas as bases têm limite de 5 no plano gratuito
        // Isso será implementado quando adicionarmos campos de plano nas bases
        
        // Limite padrão para plano gratuito: 5 motoristas
        val limiteMotoristas = 5
        
        return totalMotoristasAtivos < limiteMotoristas
    }

    /**
     * Calcular tempo restante até ativação agendada
     */
    suspend fun calcularTempoRestanteAtivacao(): Long? {
        val config = sistemaRepository.getConfiguracao()
        
        if (config.monetizacaoAtiva || config.dataAtivacaoAutomatica == null) {
            return null
        }
        
        val agora = System.currentTimeMillis()
        val dataAgendada = config.dataAtivacaoAutomatica
        
        return if (dataAgendada > agora) {
            dataAgendada - agora
        } else {
            null // Já passou
        }
    }
}

