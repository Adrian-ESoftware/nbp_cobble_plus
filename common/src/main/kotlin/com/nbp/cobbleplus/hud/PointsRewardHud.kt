package com.nbp.cobbleplus.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/**
 * Guarda a última linha de recompensa de pontos recebida do servidor e até quando ela
 * deve continuar visível. Simples holder de dados, sem referência a classes client-only,
 * então é seguro viver em `common` (mesmo padrão do [CatchComboHudState]).
 */
object PointsRewardHudState {
    @Volatile
    var text: String = ""
    @Volatile
    var expiresAtMillis: Long = 0L
}

/**
 * Desenha a recompensa de pontos no topo da tela, centralizada (onde normalmente
 * ficaria uma boss bar), e some sozinha depois do tempo configurado no servidor.
 * Só é referenciada a partir de código client-only, então nunca carrega num servidor dedicado.
 */
object PointsRewardHudRenderer {
    private const val MARGIN_TOP = 12
    private const val TEXT_COLOR = 0xFFFFFF

    fun render(guiGraphics: GuiGraphics, screenWidth: Int, screenHeight: Int) {
        val text = PointsRewardHudState.text
        if (text.isEmpty() || System.currentTimeMillis() >= PointsRewardHudState.expiresAtMillis) return

        val font = Minecraft.getInstance().font
        val x = (screenWidth - font.width(text)) / 2
        guiGraphics.drawString(font, text, x, MARGIN_TOP, TEXT_COLOR, true)
    }
}
