package com.nbp.cobbleplus.hud

import com.cobblemon.mod.common.api.gui.blitk
import com.nbp.cobbleplus.NbpCobblePlus
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

/**
 * Blit das texturas de caixa arredondada via `blitk` (utilidade de UI do Cobblemon).
 * As texturas são geradas nos tamanhos exatos finais (PNGs em textures/gui) com cores e
 * alpha já embutidos — aqui só é feito o blit 1:1. `blitk` habilita blend com
 * SRC_ALPHA/ONE_MINUS_SRC_ALPHA e shader position-tex, então o alpha é respeitado
 * (diferente do GuiGraphics.blit do 1.21.1, que desenha sem blend).
 */
object GuiBoxes {

    const val PANEL_WIDTH = 350
    const val PANEL_HEIGHT = 322
    const val CARD_WIDTH = 334
    const val CARD_HEIGHT = 14
    const val BUTTON_WIDTH = 90
    const val BUTTON_HEIGHT = 22

    private val panel = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/panel.png")
    private val card = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/card.png")
    private val button = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/button.png")
    private val buttonHover = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/button_hover.png")

    /** Box principal do extrato. */
    fun GuiGraphics.drawPanel(x: Int, y: Int) {
        blitk(
            pose(), panel, x, y,
            height = PANEL_HEIGHT, width = PANEL_WIDTH,
            textureWidth = PANEL_WIDTH, textureHeight = PANEL_HEIGHT
        )
    }

    /** Card pequeno das linhas de destaque. */
    fun GuiGraphics.drawCard(x: Int, y: Int) {
        blitk(
            pose(), card, x, y,
            height = CARD_HEIGHT, width = CARD_WIDTH,
            textureWidth = CARD_WIDTH, textureHeight = CARD_HEIGHT
        )
    }

    /** Botão pill com hover. */
    fun GuiGraphics.drawButton(x: Int, y: Int, hovered: Boolean) {
        blitk(
            pose(), if (hovered) buttonHover else button, x, y,
            height = BUTTON_HEIGHT, width = BUTTON_WIDTH,
            textureWidth = BUTTON_WIDTH, textureHeight = BUTTON_HEIGHT
        )
    }
}
