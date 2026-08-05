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
    const val MISSION_CARD_WIDTH = 334
    const val MISSION_CARD_HEIGHT = 44
    const val BAR_WIDTH = 140
    const val BAR_HEIGHT = 6

    private val panel = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/panel.png")
    private val card = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/card.png")
    private val button = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/button.png")
    private val buttonHover = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/button_hover.png")
    private val tab = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/tab.png")
    private val tabActive = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/tab_active.png")
    private val missionCard = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/mission_card.png")
    private val bar = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/bar.png")
    private val barFill = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/bar_fill.png")

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

    /** Pill de aba de missões. As texturas de tab têm o tamanho final fixo (100x22), por isso
     *  `textureWidth` acompanha `width` — evita UV > 1 que faz o blitk repetir a textura cortada. */
    fun GuiGraphics.drawTabButton(x: Int, y: Int, width: Int, active: Boolean) {
        blitk(
            pose(), if (active) tabActive else tab, x, y,
            height = BUTTON_HEIGHT, width = width,
            textureWidth = width, textureHeight = BUTTON_HEIGHT
        )
    }

    /** Card grande das linhas de missão. */
    fun GuiGraphics.drawMissionCard(x: Int, y: Int) {
        blitk(
            pose(), missionCard, x, y,
            height = MISSION_CARD_HEIGHT, width = MISSION_CARD_WIDTH,
            textureWidth = MISSION_CARD_WIDTH, textureHeight = MISSION_CARD_HEIGHT
        )
    }

    /** Fundo da barra de progresso. */
    fun GuiGraphics.drawBarBg(x: Int, y: Int) {
        blitk(
            pose(), bar, x, y,
            height = BAR_HEIGHT, width = BAR_WIDTH,
            textureWidth = BAR_WIDTH, textureHeight = BAR_HEIGHT
        )
    }

    /** Preenchimento da barra de progresso (0..1). */
    fun GuiGraphics.drawBarFill(x: Int, y: Int, fraction: Float) {
        val width = (BAR_WIDTH * fraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(2)
        blitk(
            pose(), barFill, x, y,
            height = BAR_HEIGHT, width = width,
            textureWidth = BAR_WIDTH, textureHeight = BAR_HEIGHT
        )
    }
}
