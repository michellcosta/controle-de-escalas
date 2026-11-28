package com.controleescalas.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Tokens de motion — referência: emilkowalski/skills (emil-design-eng/animate).
 *
 * Entradas e saídas usam ease-out; movimento dentro da tela, ease-in-out;
 * nunca ease-in (o começo lento trava exatamente quando o usuário está olhando).
 * Duração de UI fica abaixo de 300ms (press 100-160ms); saída é mais rápida que entrada.
 */
val MovEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
val MovEaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

object Duracao {
    const val Press = 120      // feedback de toque (100-160ms)
    const val Pequeno = 180    // chips, fade de aba (150-250ms)
    const val Conteudo = 250   // expandir/recolher conteúdo
    const val Painel = 300     // telas empurradas, painel inferior (~300ms)
}

/**
 * Respeita "remover animações" do sistema (escala do animator em 0):
 * quando desligado, as animações viram fade e as infinitas param —
 * menos movimento, não zero.
 */
object Movimento {
    fun ligado(context: Context): Boolean =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
}

@Composable
fun rememberMovimentoLigado(): Boolean {
    val context = LocalContext.current
    return remember { Movimento.ligado(context) }
}

/**
 * Feedback de press (scale 0.97, 120ms) para acumular antes de um
 * clickable/Surface(onClick) que receba o MESMO [interacao] — é ele que
 * informa o estado pressed. O ripple padrão do Material permanece.
 *
 * Ex.:
 *   val interacao = remember { MutableInteractionSource() }
 *   Box(Modifier.pressionar(interacao).clickable(interactionSource = interacao, indication = LocalIndication.current) { })
 */
fun Modifier.pressionar(
    interacao: InteractionSource,
    fator: Float = 0.97f,
    habilitado: Boolean = true
): Modifier = composed {
    val pressionado by interacao.collectIsPressedAsState()
    val movimentoLigado = rememberMovimentoLigado()
    val escala = animateFloatAsState(
        targetValue = if (pressionado && habilitado && movimentoLigado) fator else 1f,
        animationSpec = tween(Duracao.Press, easing = MovEaseOut),
        label = "press"
    )
    this.graphicsLayer {
        scaleX = escala.value
        scaleY = escala.value
    }
}
