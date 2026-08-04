package com.nbp.cobbleplus.hud

import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.feature.impl.PointType
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Tela client-only com o extrato completo de pontos do jogador. Só é instanciada a partir
 * do receiver de rede client-only de cada plataforma (nunca carrega num servidor dedicado).
 * O servidor já resolve idioma e valores; aqui é só layout.
 *
 * Todo o visual (fundo, botão) é desenhado à mão em vez de usar os widgets padrão do
 * Minecraft, pra manter o mesmo estilo escuro/colorido em toda a tela.
 */
class PointsScreen(
    private val portuguese: Boolean,
    private val values: LongArray
) : Screen(Component.literal(if (portuguese) "Seus Pontos NBP" else "Your NBP Points")) {

    private val specialTypes = listOf(
        PointType.CAPTURE, PointType.VICTORY, PointType.BREEDING, PointType.SHINY,
        PointType.LEGENDARY, PointType.MYTHICAL, PointType.ULTRA_BEAST
    )
    private val elementalTypes = PointType.entries.filter { it !in specialTypes }

    private val panelWidth = 350
    private val specialRowHeight = 14
    private val typeRowHeight = 18
    private val iconSize = 16
    private val columns = 3

    companion object {
        // Ícones oficiais do Cobblemon usados em textures/gui/types/*.png são 36x36.
        private const val ICON_SOURCE_SIZE = 36
    }

    private val closeWidth = 90
    private val closeHeight = 20

    // Fundo próprio desenhado no render(); sem o blur padrão do jogo atrás do menu.
    override fun renderBlurredBackground(partialTick: Float) = Unit

    override fun isPauseScreen(): Boolean = false

    private fun panelHeight(): Int {
        val typeRows = (elementalTypes.size + columns - 1) / columns
        return 20 + specialTypes.size * specialRowHeight + 18 + 12 + typeRows * typeRowHeight + 36
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
        val y = panelY + panelHeight - closeHeight - 10
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
        // Fundo escuro sólido no lugar do blur padrão do Minecraft.
        guiGraphics.fill(0, 0, width, height, 0xF00A0A10.toInt())

        val origin = panelOrigin()
        val panelX = origin[0]
        val panelY = origin[1]
        val panelHeight = origin[2]

        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF4A4A5E.toInt())
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF17171F.toInt())
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF5A5A72.toInt())

        var y = panelY + 8
        guiGraphics.drawCenteredString(font, title, width / 2, y, 0xFFFFFF)
        y += 18

        specialTypes.forEach { type ->
            val label = "${type.color}${type.displayName(portuguese)}"
            val amountText = "§f${amountOf(type)}"
            guiGraphics.drawString(font, label, panelX + 14, y, 0xFFFFFF, false)
            val amountWidth = font.width(amountText)
            guiGraphics.drawString(font, amountText, panelX + panelWidth - 14 - amountWidth, y, 0xFFFFFF, false)
            y += specialRowHeight
        }

        y += 4
        guiGraphics.fill(panelX + 12, y, panelX + panelWidth - 12, y + 1, 0xFF4A4A5E.toInt())
        y += 8

        val subheader = if (portuguese) "§7Pontos de Tipagem" else "§7Type Points"
        guiGraphics.drawString(font, subheader, panelX + 14, y, 0xFFFFFF, false)
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
            guiGraphics.drawString(font, text, x + iconSize + 4, rowY + (iconSize - 8) / 2, 0xFFFFFF, false)
        }

        val closeBounds = closeButtonBounds()
        val closeX = closeBounds[0]
        val closeY = closeBounds[1]
        val hovered = mouseX >= closeX && mouseX <= closeX + closeWidth && mouseY >= closeY && mouseY <= closeY + closeHeight
        val closeBg = if (hovered) 0xFF3A3A52.toInt() else 0xFF23232E.toInt()
        guiGraphics.fill(closeX, closeY, closeX + closeWidth, closeY + closeHeight, closeBg)
        guiGraphics.fill(closeX, closeY, closeX + closeWidth, closeY + 1, 0xFF6A6A85.toInt())
        guiGraphics.fill(closeX, closeY + closeHeight - 1, closeX + closeWidth, closeY + closeHeight, 0xFF6A6A85.toInt())
        guiGraphics.fill(closeX, closeY, closeX + 1, closeY + closeHeight, 0xFF6A6A85.toInt())
        guiGraphics.fill(closeX + closeWidth - 1, closeY, closeX + closeWidth, closeY + closeHeight, 0xFF6A6A85.toInt())
        val closeLabel = if (portuguese) "Fechar" else "Close"
        guiGraphics.drawCenteredString(font, closeLabel, closeX + closeWidth / 2, closeY + (closeHeight - 8) / 2, 0xFFFFFF)

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }
}
