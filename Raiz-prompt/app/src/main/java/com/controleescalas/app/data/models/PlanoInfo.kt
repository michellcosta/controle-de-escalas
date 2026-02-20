package com.controleescalas.app.data.models

/**
 * Modelo de planos de assinatura
 * PRO: 1 transportadora, R$ 199/mês ou R$ 2.030/ano (15% off)
 * MULTI: até 5 transportadoras, R$ 499/mês ou R$ 5.090/ano (15% off)
 * MULTI_PRO: até 15 transportadoras, R$ 849/mês ou R$ 8.660/ano (15% off)
 * ENTERPRISE: ilimitado, sob consulta
 */
enum class PlanoTipo(
    val id: String,
    val nome: String,
    val precoMensal: Double,
    val precoAnual: Double, // 15% desconto
    val limiteTransportadoras: Int, // -1 = ilimitado
    val productIdMensal: String,
    val productIdAnual: String
) {
    TRIAL(
        id = "trial",
        nome = "Trial",
        precoMensal = 0.0,
        precoAnual = 0.0,
        limiteTransportadoras = 1,
        productIdMensal = "",
        productIdAnual = ""
    ),
    GRATUITO(
        id = "gratuito",
        nome = "Gratuito",
        precoMensal = 0.0,
        precoAnual = 0.0,
        limiteTransportadoras = 1,
        productIdMensal = "",
        productIdAnual = ""
    ),
    PRO(
        id = "pro",
        nome = "PRO",
        precoMensal = 199.0,
        precoAnual = 2030.0, // 15% off
        limiteTransportadoras = 1,
        productIdMensal = "plano_pro_mensal",
        productIdAnual = "plano_pro_anual"
    ),
    MULTI(
        id = "multi",
        nome = "MULTI",
        precoMensal = 499.0,
        precoAnual = 5090.0, // 15% off
        limiteTransportadoras = 5,
        productIdMensal = "plano_multi_mensal",
        productIdAnual = "plano_multi_anual"
    ),
    MULTI_PRO(
        id = "multi_pro",
        nome = "MULTI PRO",
        precoMensal = 849.0,
        precoAnual = 8660.0, // 15% off
        limiteTransportadoras = 15,
        productIdMensal = "plano_multi_pro_mensal",
        productIdAnual = "plano_multi_pro_anual"
    ),
    ENTERPRISE(
        id = "enterprise",
        nome = "Enterprise",
        precoMensal = 0.0,
        precoAnual = 0.0,
        limiteTransportadoras = -1,
        productIdMensal = "",
        productIdAnual = ""
    );

    val isPago: Boolean get() = precoMensal > 0
    val limiteTexto: String
        get() = if (limiteTransportadoras == -1) "Ilimitado" else "Até $limiteTransportadoras"

    /** Economia em R$ ao escolher anual */
    val economiaAnual: Double get() = (precoMensal * 12) - precoAnual

    /** Preço mensal equivalente no plano anual */
    val precoMensalEquivalenteAnual: Double get() = if (precoAnual > 0) precoAnual / 12 else 0.0

    fun productId(isAnual: Boolean): String = if (isAnual) productIdAnual else productIdMensal
}
