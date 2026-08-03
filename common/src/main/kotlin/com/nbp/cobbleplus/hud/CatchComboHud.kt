package com.nbp.cobbleplus.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/**
 * Guarda as últimas linhas do HUD recebidas do servidor. Simples holder de dados,
 * sem nenhuma referência a classes client-only, então é seguro viver em `common`.
 */
object CatchComboHudState {
    @Volatile
    var lines: List<String> = emptyList()
}

/**
 * Desenha o HUD do combo no canto inferior direito da tela.
 * Só é referenciada a partir de código client-only (registrado via entrypoint/dist
 * check de cada plataforma), então a classe nunca é carregada num servidor dedicado.
 */
object CatchComboHudRenderer {
    private const val MARGIN_RIGHT = 6
    private const val MARGIN_BOTTOM = 8
    private const val LINE_HEIGHT = 10
    private const val TEXT_COLOR = 0xFFFFFF

    fun render(guiGraphics: GuiGraphics, screenWidth: Int, screenHeight: Int) {
        val lines = CatchComboHudState.lines
        if (lines.isEmpty()) return

        val font = Minecraft.getInstance().font
        var y = screenHeight - MARGIN_BOTTOM - lines.size * LINE_HEIGHT

        for (line in lines) {
            val x = screenWidth - MARGIN_RIGHT - font.width(line)
            guiGraphics.drawString(font, line, x, y, TEXT_COLOR, true)
            y += LINE_HEIGHT
        }
    }
}
