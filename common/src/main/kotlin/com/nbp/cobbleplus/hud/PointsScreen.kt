package com.nbp.cobbleplus.hud

import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.feature.impl.PointType
import com.nbp.cobbleplus.hud.GuiBoxes.drawButton
import com.nbp.cobbleplus.hud.GuiBoxes.drawCard
import com.nbp.cobbleplus.hud.GuiBoxes.drawPanel
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Tela client-only com o extrato completo de pontos do jogador. Só é instanciada a partir
 * do receiver de rede client-only de cada plataforma (nunca carrega num servidor dedicado).
 * O servidor já resolve idioma e valores; aqui é só layout.
 *
 * Design moderno: boxes/cards/botão com cantos arredondados e baixa opacidade (PNGs
 * em textures/gui desenhados via blitk) sobre o jogo visível, sem o blur padrão dos menus.
 */
class PointsScreen(
    private val portuguese: Boolean,
    private val values: LongArray
) : Screen(Component.literal(if (portuguese) "Seus Pontos" else "Your Points")) {

    private val specialTypes = listOf(
        PointType.CAPTURE, PointType.VICTORY, PointType.BREEDING, PointType.SHINY,
        PointType.LEGENDARY, PointType.MYTHICAL, PointType.ULTRA_BEAST
    )
    private val elementalTypes = PointType.entries.filter { it !in specialTypes }

    private val panelWidth = GuiBoxes.PANEL_WIDTH
    private val specialRowHeight = 18
    private val typeRowHeight = 18
    private val iconSize = 16
    private val columns = 3

    companion object {
        // Ícones oficiais do Cobblemon usados em textures/gui/types/*.png são 36x36.
        private const val ICON_SOURCE_SIZE = 36

        // Design tokens — cores de overlay/linha; o resto está embutido nas texturas.
        private const val BACKGROUND_TOP = 0x40101820.toInt()
        private const val BACKGROUND_BOTTOM = 0x1A0A0A10.toInt()
        private const val DIVIDER = 0x406A7A96.toInt()
        private const val TEXT_WHITE = 0xFFFFFFFF.toInt()
    }

    private val closeWidth = GuiBoxes.BUTTON_WIDTH
    private val closeHeight = GuiBoxes.BUTTON_HEIGHT

    // Sem blur atrás da tela: o jogo fica visível com nitidez sob o overlay leve.
    override fun renderBlurredBackground(partialTick: Float) = Unit

    override fun isPauseScreen(): Boolean = false

    // Deve resultar em GuiBoxes.PANEL_HEIGHT, que é o tamanho exato do panel.png.
    private fun panelHeight(): Int {
        val typeRows = (elementalTypes.size + columns - 1) / columns
        return 14 + 10 + specialTypes.size * specialRowHeight + 6 + 2 + 10 + 10 + typeRows * typeRowHeight + 36
    }

    private fun panelOrigin(): IntArray {
        val panelHeight = panelHeight()
        return intArrayOf((width - panelWidth) / 2, (height - panelHeight) / 2, panelHeight)
    }

    private fun closeButtonBounds(): IntArray {
        val origin = panelOrigin()
        val panelY = origin[1]
        val panelHeight = origin[2]
        val x = width / 2 - closeWidth / 2
        val y = panelY + panelHeight - closeHeight - 2
        return intArrayOf(x, y, closeWidth, closeHeight)
    }

    private fun amountOf(type: PointType): Long = values.getOrElse(type.ordinal) { 0L }

    private fun typeIcon(type: PointType): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/types/${type.id.removePrefix("type_")}.png")

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val bounds = closeButtonBounds()
            if (mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3]) {
                onClose()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Overlay leve (gradiente vertical, ~10-25% de opacidade) no lugar do blur.
        guiGraphics.fillGradient(0, 0, width, height, BACKGROUND_TOP, BACKGROUND_BOTTOM)

        val origin = panelOrigin()
        val panelX = origin[0]
        val panelY = origin[1]
        val panelHeight = origin[2]

        // Painel arredondado translúcido (blit 1:1 via blitk, com alpha).
        guiGraphics.drawPanel(panelX, panelY)

        var y = panelY + 14
        guiGraphics.drawCenteredString(font, title, width / 2, y, TEXT_WHITE)
        y += 17

        specialTypes.forEachIndexed { index, type ->
            if (index % 2 == 0) {
                guiGraphics.drawCard(panelX + 8, y - 2)
            }
            val label = "${type.color}${type.displayName(portuguese)}"
            val amountText = "§f${amountOf(type)}"
            guiGraphics.drawString(font, label, panelX + 14, y + 3, TEXT_WHITE, true)
            val amountWidth = font.width(amountText)
            guiGraphics.drawString(font, amountText, panelX + panelWidth - 14 - amountWidth, y + 3, TEXT_WHITE, true)
            y += specialRowHeight
        }

        y += 8
        guiGraphics.fill(panelX + 12, y, panelX + panelWidth - 12, y + 2, DIVIDER)
        y += 12

        val subheader = if (portuguese) "§7Pontos de Tipagem" else "§7Type Points"
        guiGraphics.drawString(font, subheader, panelX + 14, y, TEXT_WHITE, true)
        y += 14

        val columnWidth = (panelWidth - 28) / columns
        elementalTypes.forEachIndexed { index, type ->
            val col = index % columns
            val row = index / columns
            val x = panelX + 14 + col * columnWidth
            val rowY = y + row * typeRowHeight
            val scale = iconSize.toFloat() / ICON_SOURCE_SIZE
            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate(x.toDouble(), rowY.toDouble(), 0.0)
            guiGraphics.pose().scale(scale, scale, 1f)
            guiGraphics.blit(typeIcon(type), 0, 0, 0f, 0f, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE)
            guiGraphics.pose().popPose()
            val text = "${type.color}${type.displayName(portuguese)}: §f${amountOf(type)}"
            guiGraphics.drawString(font, text, x + iconSize + 4, rowY + (iconSize - 8) / 2, TEXT_WHITE, true)
        }

        val closeBounds = closeButtonBounds()
        val closeX = closeBounds[0]
        val closeY = closeBounds[1]
        val hovered = mouseX >= closeX && mouseX <= closeX + closeWidth && mouseY >= closeY && mouseY <= closeY + closeHeight
        guiGraphics.drawButton(closeX, closeY, hovered)
        val closeLabel = if (portuguese) "Fechar" else "Close"
        guiGraphics.drawCenteredString(font, closeLabel, closeX + closeWidth / 2, closeY + (closeHeight - 8) / 2, TEXT_WHITE)

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }
}
