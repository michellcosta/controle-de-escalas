package com.controleescalas.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.yalantis.ucrop.UCropActivity

/**
 * Activity de recorte customizada: título no topo, Cancelar e Confirmar na base.
 * Estende UCropActivity e substitui a toolbar padrão (com botões) por layout simplificado.
 */
class CustomCropActivity : UCropActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupCustomLayout()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Ocultar itens do menu (Crop/Loader) - usamos botões na base.
        // Não chamar super para evitar NPE quando parent acessa itens.
        menu.findItem(com.yalantis.ucrop.R.id.menu_crop)?.isVisible = false
        menu.findItem(com.yalantis.ucrop.R.id.menu_loader)?.isVisible = false
        return true
    }

    private fun setupCustomLayout() {
        val toolbar = findViewById<Toolbar>(com.yalantis.ucrop.R.id.toolbar)
        toolbar?.let {
            it.navigationIcon = null
            it.setNavigationOnClickListener(null)
        }

        val root = findViewById<ViewGroup>(com.yalantis.ucrop.R.id.ucrop_photobox) ?: return
        val ucropFrame = findViewById<View>(com.yalantis.ucrop.R.id.ucrop_frame)

        val bottomBar = LinearLayout(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER

            val cancelBtn = TextView(this@CustomCropActivity).apply {
                text = "Cancelar"
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(pad, pad, pad, pad)
                setOnClickListener { finish() }
            }

            val confirmBtn = TextView(this@CustomCropActivity).apply {
                text = "Confirmar"
                setTextColor(Color.parseColor("#10B981"))
                textSize = 16f
                setPadding(pad, pad, pad, pad)
                setOnClickListener { cropAndSaveImage() }
            }

            addView(cancelBtn)
            addView(confirmBtn)
        }

        root.addView(bottomBar)

        // Ajustar ucrop_frame para ficar acima da barra inferior
        ucropFrame?.let { frame ->
            val params = frame.layoutParams as? RelativeLayout.LayoutParams
            params?.addRule(RelativeLayout.ABOVE, bottomBar.id)
            frame.layoutParams = params
        }
    }
}
