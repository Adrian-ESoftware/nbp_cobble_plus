package com.nbp.cobbleplus.hud

import com.nbp.cobbleplus.network.GtsViewRow
import com.nbp.cobbleplus.network.GtsViewSyncPayload
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.item.PokemonItem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Custom GTS screen, styled as a lightweight overlay like Points/Missions. */
class GtsScreen(payload: GtsViewSyncPayload) : Screen(Component.literal("GTS")) {
    private var rows: List<GtsViewRow> = payload.rows
    private var balance = payload.balance
    private var pending = payload.pending
    private val panelWidth = 560
    private val panelHeight = 360
    private val columns = 5
    private val cardWidth = 104
    private val cardHeight = 78

    override fun renderBlurredBackground(partialTick: Float) = Unit
    override fun isPauseScreen(): Boolean = false

    fun update(payload: GtsViewSyncPayload) {
        rows = payload.rows
        balance = payload.balance
        pending = payload.pending
    }

    private fun origin(): Pair<Int, Int> = Pair((width - panelWidth) / 2, (height - panelHeight) / 2)
    private fun cardBounds(index: Int): IntArray {
        val (x, y) = origin()
        val col = index % columns
        val row = index / columns
        return intArrayOf(x + 12 + col * cardWidth, y + 52 + row * cardHeight, cardWidth - 6, cardHeight - 6)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)
        val (x, y) = origin()
        val collectX = x + panelWidth - 170
        if (mouseX in collectX.toDouble()..(collectX + 150).toDouble() && mouseY in (y + 20).toDouble()..(y + 42).toDouble()) {
            GtsClientNetworking.collect()
            return true
        }
        rows.forEachIndexed { index, row ->
            val bounds = cardBounds(index)
            if (mouseX in bounds[0].toDouble()..(bounds[0] + bounds[2]).toDouble() && mouseY in bounds[1].toDouble()..(bounds[1] + bounds[3]).toDouble()) {
                GtsClientNetworking.purchase(row.id)
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fillGradient(0, 0, width, height, 0x50101820, 0x1A080A10)
        val (x, y) = origin()
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xE818202C.toInt())
        graphics.fill(x + 1, y + 1, x + panelWidth - 1, y + 2, 0xFF6BA7D6.toInt())
        graphics.drawCenteredString(font, "§bGlobal Trade Station", width / 2, y + 8, 0xFFFFFFFF.toInt())
        graphics.drawString(font, "§7Saldo: §e$balance", x + 14, y + 27, 0xFFFFFFFF.toInt(), true)
        graphics.drawString(font, "§7A receber: §e$pending", x + 135, y + 27, 0xFFFFFFFF.toInt(), true)
        graphics.fill(x + panelWidth - 170, y + 20, x + panelWidth - 20, y + 42, 0xFF2E8B57.toInt())
        graphics.drawCenteredString(font, "§fColetar", x + panelWidth - 95, y + 27, 0xFFFFFFFF.toInt())

        rows.forEachIndexed { index, row ->
            val bounds = cardBounds(index)
            val hovered = mouseX in bounds[0]..(bounds[0] + bounds[2]) && mouseY in bounds[1]..(bounds[1] + bounds[3])
            graphics.fill(bounds[0], bounds[1], bounds[0] + bounds[2], bounds[1] + bounds[3], if (hovered) 0xFF344C63.toInt() else 0xFF263342.toInt())
            val stack = PokemonItem.from(PokemonProperties.parse("species=${row.species} shiny=${row.shiny}"))
            graphics.renderItem(stack, bounds[0] + 36, bounds[1] + 4)
            graphics.drawCenteredString(font, "§e#${row.id} §f${row.species.substringAfter(':').take(14)}", bounds[0] + bounds[2] / 2, bounds[1] + 42, 0xFFFFFFFF.toInt())
            graphics.drawCenteredString(font, "§a${row.price} CD", bounds[0] + bounds[2] / 2, bounds[1] + 56, 0xFFFFFFFF.toInt())
            if (hovered) graphics.renderTooltip(font, stack, mouseX, mouseY)
        }
        if (rows.isEmpty()) graphics.drawCenteredString(font, "§7Nenhum Pokémon anunciado.", width / 2, y + 180, 0xFFFFFFFF.toInt())
        super.render(graphics, mouseX, mouseY, partialTick)
    }
}
