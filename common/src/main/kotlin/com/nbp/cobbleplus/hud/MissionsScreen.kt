package com.nbp.cobbleplus.hud

import com.nbp.cobbleplus.hud.GuiBoxes.drawBarBg
import com.nbp.cobbleplus.hud.GuiBoxes.drawBarFill
import com.nbp.cobbleplus.hud.GuiBoxes.drawButton
import com.nbp.cobbleplus.hud.GuiBoxes.drawMissionCard
import com.nbp.cobbleplus.hud.GuiBoxes.drawPanel
import com.nbp.cobbleplus.hud.GuiBoxes.drawTabButton
import com.nbp.cobbleplus.mission.Difficulty
import com.nbp.cobbleplus.network.MissionViewRow
import com.nbp.cobbleplus.network.RewardViewRow
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * Tela client-only com as missões diárias e semanais em duas abas (mesmo padrão do
 * [PointsScreen] via blitk). Nunca carrega num servidor dedicado — só é instanciada a
 * partir dos receivers client-side (fabric/neoforge).
 */
class MissionsScreen(
    private val portuguese: Boolean,
    private val daily: List<MissionViewRow>,
    private val weekly: List<MissionViewRow>
) : Screen(Component.literal(if (portuguese) "Missões" else "Missions")) {

    private var selectedTab = 0
    private val paddingX = 8
    private val contentStartY = 62
    private val rowHeight = 46

    override fun renderBlurredBackground(partialTick: Float) = Unit
    override fun isPauseScreen(): Boolean = false

    private val panelWidth = GuiBoxes.PANEL_WIDTH
    private val panelHeight = GuiBoxes.PANEL_HEIGHT
    private val closeWidth = GuiBoxes.BUTTON_WIDTH
    private val closeHeight = GuiBoxes.BUTTON_HEIGHT

    private fun panelX(): Int = (width - panelWidth) / 2
    private fun panelY(): Int = (height - panelHeight) / 2

    private fun tabBounds(index: Int): IntArray {
        val tabWidth = 100
        val total = tabWidth * 2 + 8
        val x0 = panelX() + (panelWidth - total) / 2
        val y = panelY() + 30
        return intArrayOf(x0 + index * (tabWidth + 8), y, tabWidth, GuiBoxes.BUTTON_HEIGHT)
    }

    private fun closeBounds(): IntArray {
        val x = width / 2 - closeWidth / 2
        val y = panelY() + panelHeight - closeHeight - 7
        return intArrayOf(x, y, closeWidth, closeHeight)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            (0..1).forEach { index ->
                val tab = tabBounds(index)
                if (mouseX >= tab[0] && mouseX <= tab[0] + tab[2] && mouseY >= tab[1] && mouseY <= tab[1] + tab[3]) {
                    selectedTab = index
                    return true
                }
            }
            val close = closeBounds()
            if (mouseX >= close[0] && mouseX <= close[0] + close[2] && mouseY >= close[1] && mouseY <= close[1] + close[3]) {
                onClose()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fillGradient(0, 0, width, height, 0x40101820.toInt(), 0x1A0A0A10.toInt())

        val panelX = panelX()
        val panelY = panelY()
        guiGraphics.drawPanel(panelX, panelY)

        val title = if (portuguese) "§fMissões" else "§fMissions"
        guiGraphics.drawCenteredString(font, title, width / 2, panelY + 14, 0xFFFFFFFF.toInt())

        val tabLabels = if (portuguese) arrayOf("Diárias", "Semanais") else arrayOf("Daily", "Weekly")
        tabLabels.forEachIndexed { index, label ->
            val tab = tabBounds(index)
            val active = index == selectedTab
            guiGraphics.drawTabButton(tab[0], tab[1], tab[2], active || (mouseX in tab[0]..tab[0] + tab[2] && mouseY in tab[1]..tab[1] + tab[3]))
            guiGraphics.drawCenteredString(font, "§f$label", tab[0] + tab[2] / 2, tab[1] + 7, 0xFFFFFFFF.toInt())
        }

        val rows = if (selectedTab == 0) daily else weekly
        var y = panelY + contentStartY
        rows.take(6).forEach { row ->
            guiGraphics.drawMissionCard(panelX + paddingX, y)
            renderRow(guiGraphics, panelX, y, row)
            y += rowHeight
        }

        if (rows.isEmpty()) {
            val emptyText = if (portuguese) "§7Nenhuma missão ativa neste ciclo." else "§7No active missions this cycle."
            guiGraphics.drawCenteredString(font, emptyText, width / 2, y + 10, 0xFFFFFFFF.toInt())
        }

        val close = closeBounds()
        val hovered = mouseX >= close[0] && mouseX <= close[0] + close[2] && mouseY >= close[1] && mouseY <= close[1] + close[3]
        guiGraphics.drawButton(close[0], close[1], hovered)
        val closeLabel = if (portuguese) "Fechar" else "Close"
        guiGraphics.drawCenteredString(font, "§f$closeLabel", close[0] + closeWidth / 2, close[1] + 7, 0xFFFFFFFF.toInt())

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderRow(guiGraphics: GuiGraphics, panelX: Int, y: Int, row: MissionViewRow) {
        val description = font.plainSubstrByWidth(row.description, 200)
        guiGraphics.drawString(font, description, panelX + 16, y + 6, 0xFFFFFFFF.toInt(), true)

        val localName = Minecraft.getInstance().player?.gameProfile?.name ?: ""
        val statusText = when {
            row.completed && !row.lockedBy.isNullOrBlank() && row.lockedBy != localName ->
                "§c🔒 ${row.lockedBy}"
            row.completed -> if (portuguese) "§a✔ Concluída" else "§a✔ Completed"
            else -> "§7${row.progress}/${row.quantity}"
        }
        guiGraphics.drawString(font, statusText, panelX + 144, y + 16, 0xFFFFFFFF.toInt(), true)
        if (!row.completed) {
            guiGraphics.drawBarBg(panelX + 16, y + 26)
            guiGraphics.drawBarFill(panelX + 16, y + 26, row.progress.toFloat() / row.quantity.coerceAtLeast(1))
        }

        val difficulty = Difficulty.byId(row.difficulty)
        val tag = if (difficulty != null) {
            "${difficulty.color}[${difficulty.id.uppercase()}]"
        } else {
            "§7[?]"
        }
        guiGraphics.drawString(font, tag, panelX + 16, y + 34, 0xFFFFFFFF.toInt(), true)

        renderRewards(guiGraphics, panelX, y, row.rewards)
    }

    private fun renderRewards(guiGraphics: GuiGraphics, panelX: Int, y: Int, rewards: List<RewardViewRow>) {
        val shown = rewards.take(5)
        if (shown.isEmpty()) return
        val slotWidth = 28
        val total = shown.size * slotWidth
        var x = panelX + GuiBoxes.MISSION_CARD_WIDTH - 16 - total
        shown.forEach { reward ->
            val item = runCatching {
                BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(reward.itemId)).orElse(null)
            }.getOrNull()
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                guiGraphics.pose().pushPose()
                guiGraphics.pose().translate((x + 2).toDouble(), (y + 26).toDouble(), 0.0)
                guiGraphics.pose().scale(0.625f, 0.625f, 1f)
                guiGraphics.renderItem(ItemStack(item), 0, 0)
                guiGraphics.pose().popPose()
            }
            guiGraphics.drawString(font, "§7x${reward.count}", x + 12, y + 34, 0xFFFFFFFF.toInt(), true)
            x += slotWidth
        }
    }
}