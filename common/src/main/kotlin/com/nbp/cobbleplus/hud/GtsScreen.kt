package com.nbp.cobbleplus.hud

import com.nbp.cobbleplus.hud.GuiBoxes.drawButton
import com.nbp.cobbleplus.hud.GuiBoxes.drawGtsBack
import com.nbp.cobbleplus.hud.GuiBoxes.drawGtsCancel
import com.nbp.cobbleplus.hud.GuiBoxes.drawGtsCollect
import com.nbp.cobbleplus.hud.GuiBoxes.drawGtsRow
import com.nbp.cobbleplus.hud.GuiBoxes.drawGtsSellCard
import com.nbp.cobbleplus.hud.GuiBoxes.drawPanel
import com.nbp.cobbleplus.network.GtsPartyRow
import com.nbp.cobbleplus.network.GtsPartyViewPayload
import com.nbp.cobbleplus.network.GtsViewRow
import com.nbp.cobbleplus.network.GtsViewSyncPayload
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.item.PokemonItem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.lwjgl.opengl.GL11

class GtsScreen(payload: GtsViewSyncPayload) : Screen(Component.literal("Global Trade Station")) {

    private enum class View { BROWSE, MY_AUCTIONS, SELL, SELL_CONFIRM }

    private var currentView = View.BROWSE
    private var rows: List<GtsViewRow> = payload.rows
    private var balance: String = payload.balance
    private var pending: Long = payload.pending
    private var scrollOffset: Double = 0.0
    private var partyPokemon: List<GtsPartyRow> = emptyList()

    private var confirmSellSlot: Int = -1
    private var confirmSellPokemon: GtsPartyRow? = null
    private var priceInput: EditBox? = null

    private val panelWidth = GuiBoxes.PANEL_WIDTH
    private val panelHeight = GuiBoxes.PANEL_HEIGHT
    private val rowHeight = GuiBoxes.GTS_ROW_HEIGHT + GuiBoxes.GTS_ROW_GAP
    private val contentPadX = 8

    private val browseContentStart = 70
    private val myAuctionsContentStart = 56
    private val sellContentStart = 30
    private val contentEnd = 286

    private fun panelX(): Int = (width - panelWidth) / 2
    private fun panelY(): Int = (height - panelHeight) / 2

    private fun currentContentStart(): Int = when (currentView) {
        View.BROWSE -> browseContentStart
        View.MY_AUCTIONS -> myAuctionsContentStart
        View.SELL -> sellContentStart
        View.SELL_CONFIRM -> 0
    }

    private fun currentDisplayRows(): List<GtsViewRow> = when (currentView) {
        View.BROWSE -> rows
        View.MY_AUCTIONS -> rows.filter { it.seller == playerName() }
        else -> emptyList()
    }

    private fun playerName(): String = Minecraft.getInstance().player?.gameProfile?.name ?: ""

    private fun statColor(pct: Float): String = when {
        pct >= 80 -> "§a"
        pct >= 50 -> "§e"
        else -> "§c"
    }

    private fun ivColor(v: Int): String = when (v) {
        31 -> "§a"
        in 20..30 -> "§e"
        else -> "§c"
    }

    private fun genderText(gender: String): String = when (gender.uppercase()) {
        "MALE" -> "§9♂ Male"
        "FEMALE" -> "§d♀ Female"
        else -> "§7— Genderless"
    }

    override fun init() {
        super.init()
        priceInput = EditBox(font, 0, 0, 200, 20, Component.literal("Price"))
        priceInput!!.setFilter { s -> s.all { it.isDigit() } && s.length <= 10 }
        priceInput!!.visible = false
        addRenderableWidget(priceInput!!)
    }

    fun update(payload: GtsViewSyncPayload) {
        rows = payload.rows
        balance = payload.balance
        pending = payload.pending
    }

    fun updateParty(payload: GtsPartyViewPayload) {
        partyPokemon = payload.party
    }

    override fun renderBlurredBackground(partialTick: Float) = Unit
    override fun isPauseScreen(): Boolean = false

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (currentView == View.SELL_CONFIRM) return true
        val displayRows = currentDisplayRows()
        val visible = (contentEnd - currentContentStart()) / rowHeight
        val maxScroll = maxOf(0.0, displayRows.size - visible.toDouble())
        scrollOffset = (scrollOffset - scrollY * 0.5).coerceIn(0.0, maxScroll)
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        val px = panelX()
        val py = panelY()

        val closeX = px + 130
        val closeY = py + 296
        if (mouseX >= closeX && mouseX <= closeX + 90 && mouseY >= closeY && mouseY <= closeY + 22) {
            onClose()
            return true
        }

        when (currentView) {
            View.BROWSE -> handleBrowseClick(mouseX, mouseY, px, py)
            View.MY_AUCTIONS -> handleMyAuctionsClick(mouseX, mouseY, px, py)
            View.SELL -> handleSellClick(mouseX, mouseY, px, py)
            View.SELL_CONFIRM -> handleSellConfirmClick(mouseX, mouseY, px, py)
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun handleBrowseClick(mouseX: Double, mouseY: Double, px: Int, py: Int) {
        val totalBtnWidth = GuiBoxes.BUTTON_WIDTH * 2 + 8
        val btnStartX = px + (panelWidth - totalBtnWidth) / 2

        val myAuctionsX = btnStartX
        val myAuctionsY = py + 30
        if (mouseX >= myAuctionsX && mouseX <= myAuctionsX + GuiBoxes.BUTTON_WIDTH && mouseY >= myAuctionsY && mouseY <= myAuctionsY + GuiBoxes.BUTTON_HEIGHT) {
            currentView = View.MY_AUCTIONS
            scrollOffset = 0.0
            return
        }

        val sellX = btnStartX + GuiBoxes.BUTTON_WIDTH + 8
        val sellY = py + 30
        if (mouseX >= sellX && mouseX <= sellX + GuiBoxes.BUTTON_WIDTH && mouseY >= sellY && mouseY <= sellY + GuiBoxes.BUTTON_HEIGHT) {
            currentView = View.SELL
            scrollOffset = 0.0
            GtsClientNetworking.requestParty()
            return
        }

        handleRowClick(mouseX, mouseY, px, py) { row ->
            GtsClientNetworking.purchase(row.id)
        }
    }

    private fun handleMyAuctionsClick(mouseX: Double, mouseY: Double, px: Int, py: Int) {
        val backX = px + 8
        val backY = py + 8
        if (mouseX >= backX && mouseX <= backX + GuiBoxes.GTS_BACK_WIDTH && mouseY >= backY && mouseY <= backY + GuiBoxes.GTS_BACK_HEIGHT) {
            currentView = View.BROWSE
            scrollOffset = 0.0
            return
        }

        val collectX = px + panelWidth - GuiBoxes.GTS_COLLECT_WIDTH - 12
        val collectY = py + 30
        if (pending > 0 && mouseX >= collectX && mouseX <= collectX + GuiBoxes.GTS_COLLECT_WIDTH && mouseY >= collectY && mouseY <= collectY + GuiBoxes.GTS_COLLECT_HEIGHT) {
            GtsClientNetworking.collect()
            return
        }

        val displayRows = currentDisplayRows()
        val contentStart = currentContentStart()
        for (i in displayRows.indices) {
            val row = displayRows[i]
            val rowY = py + contentStart + ((i - scrollOffset) * rowHeight).toInt()
            val rowX = px + contentPadX

            val cancelX = rowX + GuiBoxes.GTS_ROW_WIDTH - GuiBoxes.GTS_CANCEL_WIDTH - 4
            val cancelY = rowY + (GuiBoxes.GTS_ROW_HEIGHT - GuiBoxes.GTS_CANCEL_HEIGHT) / 2
            if (mouseX >= cancelX && mouseX <= cancelX + GuiBoxes.GTS_CANCEL_WIDTH && mouseY >= cancelY && mouseY <= cancelY + GuiBoxes.GTS_CANCEL_HEIGHT) {
                GtsClientNetworking.cancel(row.id)
                return
            }
        }
    }

    private fun handleSellClick(mouseX: Double, mouseY: Double, px: Int, py: Int) {
        val backX = px + 8
        val backY = py + 8
        if (mouseX >= backX && mouseX <= backX + GuiBoxes.GTS_BACK_WIDTH && mouseY >= backY && mouseY <= backY + GuiBoxes.GTS_BACK_HEIGHT) {
            currentView = View.BROWSE
            scrollOffset = 0.0
            return
        }

        val contentStart = sellContentStart
        for (i in partyPokemon.indices) {
            val rowY = py + contentStart + ((i - scrollOffset) * rowHeight).toInt()
            val rowX = px + contentPadX
            if (mouseX >= rowX && mouseX <= rowX + GuiBoxes.GTS_SELL_CARD_WIDTH && mouseY >= rowY && mouseY <= rowY + GuiBoxes.GTS_ROW_HEIGHT) {
                confirmSellSlot = partyPokemon[i].slot
                confirmSellPokemon = partyPokemon[i]
                currentView = View.SELL_CONFIRM
                priceInput!!.visible = true
                priceInput!!.value = ""
                priceInput!!.setFocused(true)
                return
            }
        }
    }

    private fun handleSellConfirmClick(mouseX: Double, mouseY: Double, px: Int, py: Int) {
        val confirmX = px + 8
        val confirmY = py + panelHeight - GuiBoxes.BUTTON_HEIGHT - 30
        if (mouseX >= confirmX && mouseX <= confirmX + 100 && mouseY >= confirmY && mouseY <= confirmY + GuiBoxes.BUTTON_HEIGHT) {
            val price = priceInput!!.value.toLongOrNull() ?: 0L
            if (price > 0) {
                GtsClientNetworking.sell(confirmSellSlot, price)
                currentView = View.BROWSE
                scrollOffset = 0.0
                priceInput!!.visible = false
                priceInput!!.setFocused(false)
            }
            return
        }

        val cancelX = px + panelWidth - 108
        val cancelY = py + panelHeight - GuiBoxes.BUTTON_HEIGHT - 30
        if (mouseX >= cancelX && mouseX <= cancelX + 100 && mouseY >= cancelY && mouseY <= cancelY + GuiBoxes.BUTTON_HEIGHT) {
            currentView = View.SELL
            scrollOffset = 0.0
            priceInput!!.visible = false
            priceInput!!.setFocused(false)
            return
        }
    }

    private fun handleRowClick(mouseX: Double, mouseY: Double, px: Int, py: Int, action: (GtsViewRow) -> Unit) {
        val displayRows = currentDisplayRows()
        val contentStart = currentContentStart()
        for (i in displayRows.indices) {
            val row = displayRows[i]
            val rowY = py + contentStart + ((i - scrollOffset) * rowHeight).toInt()
            val rowX = px + contentPadX
            if (mouseX >= rowX && mouseX <= rowX + GuiBoxes.GTS_ROW_WIDTH && mouseY >= rowY && mouseY <= rowY + GuiBoxes.GTS_ROW_HEIGHT) {
                action(row)
                return
            }
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.fillGradient(0, 0, width, height, 0x40101820.toInt(), 0x1A0A0A10.toInt())

        val px = panelX()
        val py = panelY()
        graphics.drawPanel(px, py)

        when (currentView) {
            View.BROWSE -> renderBrowse(graphics, mouseX, mouseY, px, py)
            View.MY_AUCTIONS -> renderMyAuctions(graphics, mouseX, mouseY, px, py)
            View.SELL -> renderSell(graphics, mouseX, mouseY, px, py)
            View.SELL_CONFIRM -> renderSellConfirm(graphics, mouseX, mouseY, px, py)
        }

        if (currentView != View.SELL_CONFIRM) {
            val closeX = px + 130
            val closeY = py + 296
            val closeHovered = mouseX >= closeX && mouseX <= closeX + 90 && mouseY >= closeY && mouseY <= closeY + 22
            graphics.drawButton(closeX, closeY, closeHovered)
            graphics.drawCenteredString(font, "§fClose", closeX + 45, closeY + 7, 0xFFFFFFFF.toInt())

            renderScrollbar(graphics, px, py)
        }
    }

    private fun renderBrowse(graphics: GuiGraphics, mouseX: Int, mouseY: Int, px: Int, py: Int) {
        graphics.drawCenteredString(font, "§fGlobal Trade Station", width / 2, py + 14, 0xFFFFFFFF.toInt())

        val totalBtnWidth = GuiBoxes.BUTTON_WIDTH * 2 + 8
        val btnStartX = px + (panelWidth - totalBtnWidth) / 2

        val myAuctionsX = btnStartX
        val myAuctionsY = py + 30
        val myAuctionsHovered = mouseX >= myAuctionsX && mouseX <= myAuctionsX + GuiBoxes.BUTTON_WIDTH && mouseY >= myAuctionsY && mouseY <= myAuctionsY + GuiBoxes.BUTTON_HEIGHT
        graphics.drawButton(myAuctionsX, myAuctionsY, myAuctionsHovered)
        graphics.drawCenteredString(font, "§fMy Auctions", myAuctionsX + GuiBoxes.BUTTON_WIDTH / 2, myAuctionsY + 7, 0xFFFFFFFF.toInt())

        val sellX = btnStartX + GuiBoxes.BUTTON_WIDTH + 8
        val sellY = py + 30
        val sellHovered = mouseX >= sellX && mouseX <= sellX + GuiBoxes.BUTTON_WIDTH && mouseY >= sellY && mouseY <= sellY + GuiBoxes.BUTTON_HEIGHT
        graphics.drawButton(sellX, sellY, sellHovered)
        graphics.drawCenteredString(font, "§fSell", sellX + GuiBoxes.BUTTON_WIDTH / 2, sellY + 7, 0xFFFFFFFF.toInt())

        graphics.drawString(font, "§7Balance: §e$balance CD", px + 14, py + 56, 0xFFFFFFFF.toInt(), true)

        renderRowList(graphics, mouseX, mouseY, px, py, currentDisplayRows(), ::renderPokemonRow)

        if (currentDisplayRows().isEmpty()) {
            graphics.drawCenteredString(font, "§7No Pokémon listed on the GTS.", width / 2, py + 170, 0xFFFFFFFF.toInt())
        }
    }

    private fun renderMyAuctions(graphics: GuiGraphics, mouseX: Int, mouseY: Int, px: Int, py: Int) {
        graphics.drawCenteredString(font, "§fMy Auctions", width / 2, py + 14, 0xFFFFFFFF.toInt())

        val backX = px + 8
        val backY = py + 8
        val backHovered = mouseX >= backX && mouseX <= backX + GuiBoxes.GTS_BACK_WIDTH && mouseY >= backY && mouseY <= backY + GuiBoxes.GTS_BACK_HEIGHT
        graphics.drawGtsBack(backX, backY, backHovered)

        graphics.drawString(font, "§7Pending: §e$pending CD", px + 14, py + 30, 0xFFFFFFFF.toInt(), true)

        val collectX = px + panelWidth - GuiBoxes.GTS_COLLECT_WIDTH - 12
        val collectY = py + 30
        val collectHovered = pending > 0 && mouseX >= collectX && mouseX <= collectX + GuiBoxes.GTS_COLLECT_WIDTH && mouseY >= collectY && mouseY <= collectY + GuiBoxes.GTS_COLLECT_HEIGHT
        graphics.drawGtsCollect(collectX, collectY, collectHovered)
        val collectText = if (pending > 0) "§fCollect" else "§7Collect"
        graphics.drawCenteredString(font, collectText, collectX + GuiBoxes.GTS_COLLECT_WIDTH / 2, collectY + 7, 0xFFFFFFFF.toInt())

        renderRowList(graphics, mouseX, mouseY, px, py, currentDisplayRows(), ::renderPokemonRowWithCancel)

        if (currentDisplayRows().isEmpty()) {
            graphics.drawCenteredString(font, "§7You have no active listings.", width / 2, py + 170, 0xFFFFFFFF.toInt())
        }
    }

    private fun renderSell(graphics: GuiGraphics, mouseX: Int, mouseY: Int, px: Int, py: Int) {
        graphics.drawCenteredString(font, "§fSell Pokémon", width / 2, py + 14, 0xFFFFFFFF.toInt())

        val backX = px + 8
        val backY = py + 8
        val backHovered = mouseX >= backX && mouseX <= backX + GuiBoxes.GTS_BACK_WIDTH && mouseY >= backY && mouseY <= backY + GuiBoxes.GTS_BACK_HEIGHT
        graphics.drawGtsBack(backX, backY, backHovered)

        val contentStart = sellContentStart

        val mc = Minecraft.getInstance()
        val scaleFactor = mc.window.guiScale
        val clipX = (px + contentPadX) * scaleFactor
        val clipY = (py + contentStart) * scaleFactor
        val clipW = GuiBoxes.GTS_SELL_CARD_WIDTH * scaleFactor
        val clipH = (contentEnd - contentStart) * scaleFactor

        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(clipX.toInt(), mc.window.height - (clipY + clipH).toInt(), clipW.toInt(), clipH.toInt())

        for (i in partyPokemon.indices) {
            val pokemon = partyPokemon[i]
            val rowY = py + contentStart + ((i - scrollOffset) * rowHeight).toInt()
            val rowX = px + contentPadX
            val hovered = mouseX >= rowX && mouseX <= rowX + GuiBoxes.GTS_SELL_CARD_WIDTH && mouseY >= rowY && mouseY <= rowY + GuiBoxes.GTS_ROW_HEIGHT
            renderPartyRow(graphics, rowX, rowY, pokemon, hovered, mouseX, mouseY)
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST)

        if (partyPokemon.isEmpty()) {
            graphics.drawCenteredString(font, "§7No Pokémon in your party.", width / 2, py + 170, 0xFFFFFFFF.toInt())
        }
    }

    private fun renderSellConfirm(graphics: GuiGraphics, mouseX: Int, mouseY: Int, px: Int, py: Int) {
        graphics.drawCenteredString(font, "§fConfirm Sale", width / 2, py + 14, 0xFFFFFFFF.toInt())

        val pokemon = confirmSellPokemon ?: return
        val speciesName = pokemon.species.substringAfter(':').replaceFirstChar { it.uppercase() }

        val infoX = px + 14
        var infoY = py + 34

        val stack = runCatching {
            PokemonItem.from(PokemonProperties.parse("species=${pokemon.species} shiny=${pokemon.shiny}"))
        }.getOrNull() ?: net.minecraft.world.item.ItemStack.EMPTY
        graphics.pose().pushPose()
        graphics.pose().translate(infoX.toDouble(), infoY.toDouble(), 0.0)
        graphics.pose().scale(2.5f, 2.5f, 1.0f)
        graphics.renderItem(stack, 0, 0)
        graphics.pose().popPose()

        val textX = infoX + 46
        graphics.drawString(font, "§f$speciesName §7Lv.${pokemon.level}", textX, infoY + 2, 0xFFFFFFFF.toInt(), true)
        graphics.drawString(font, "§7Nature: §f${pokemon.nature}", textX, infoY + 14, 0xFFFFFFFF.toInt(), true)
        graphics.drawString(font, "§7Ability: §f${pokemon.ability}", textX, infoY + 26, 0xFFFFFFFF.toInt(), true)
        graphics.drawString(font, "§7IV: ${statColor(pokemon.ivPct)}${"%.1f".format(pokemon.ivPct)}%% §7| §7EV: ${statColor(pokemon.evPct)}${"%.1f".format(pokemon.evPct)}%%", textX, infoY + 38, 0xFFFFFFFF.toInt(), true)
        val shinyText = if (pokemon.shiny) "§6✦ Yes" else "§7No"
        graphics.drawString(font, "§7Shiny: $shinyText §7| ${genderText(pokemon.gender)}", textX, infoY + 50, 0xFFFFFFFF.toInt(), true)

        infoY = py + 120
        graphics.drawString(font, "§7Price (CD):", px + 14, infoY, 0xFFFFFFFF.toInt(), true)

        priceInput!!.x = px + 14
        priceInput!!.y = infoY + 14
        priceInput!!.render(graphics, mouseX, mouseY, 0f)

        val confirmX = px + 8
        val confirmY = py + panelHeight - GuiBoxes.BUTTON_HEIGHT - 30
        val confirmHovered = mouseX >= confirmX && mouseX <= confirmX + 100 && mouseY >= confirmY && mouseY <= confirmY + GuiBoxes.BUTTON_HEIGHT
        graphics.drawButton(confirmX, confirmY, confirmHovered)
        graphics.drawCenteredString(font, "§fConfirm", confirmX + 50, confirmY + 7, 0xFFFFFFFF.toInt())

        val cancelX = px + panelWidth - 108
        val cancelY = confirmY
        val cancelHovered = mouseX >= cancelX && mouseX <= cancelX + 100 && mouseY >= cancelY && mouseY <= cancelY + GuiBoxes.BUTTON_HEIGHT
        graphics.drawButton(cancelX, cancelY, cancelHovered)
        graphics.drawCenteredString(font, "§fCancel", cancelX + 50, cancelY + 7, 0xFFFFFFFF.toInt())
    }

    private fun renderRowList(graphics: GuiGraphics, mouseX: Int, mouseY: Int, px: Int, py: Int, displayRows: List<GtsViewRow>, renderer: (GuiGraphics, Int, Int, GtsViewRow, Boolean, Int, Int) -> Unit) {
        val contentStart = currentContentStart()

        val mc = Minecraft.getInstance()
        val scaleFactor = mc.window.guiScale
        val clipX = (px + contentPadX) * scaleFactor
        val clipY = (py + contentStart) * scaleFactor
        val clipW = GuiBoxes.GTS_ROW_WIDTH * scaleFactor
        val clipH = (contentEnd - contentStart) * scaleFactor

        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(clipX.toInt(), mc.window.height - (clipY + clipH).toInt(), clipW.toInt(), clipH.toInt())

        for (i in 0 until displayRows.size) {
            val row = displayRows[i]
            val rowY = py + contentStart + ((i - scrollOffset) * rowHeight).toInt()
            val rowX = px + contentPadX
            val hovered = mouseX >= rowX && mouseX <= rowX + GuiBoxes.GTS_ROW_WIDTH && mouseY >= rowY && mouseY <= rowY + GuiBoxes.GTS_ROW_HEIGHT
            renderer(graphics, rowX, rowY, row, hovered, mouseX, mouseY)
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST)
    }

    private fun renderPokemonRow(graphics: GuiGraphics, x: Int, y: Int, row: GtsViewRow, hovered: Boolean, mouseX: Int, mouseY: Int) {
        graphics.drawGtsRow(x, y, hovered)
        renderPokemonModel(graphics, x, y, row.species, row.shiny)

        val textX = x + 40
        val speciesName = row.species.substringAfter(':').replaceFirstChar { it.uppercase() }
        graphics.drawString(font, "§f$speciesName §7Lv.${row.level}", textX, y + 6, 0xFFFFFFFF.toInt(), true)
        graphics.drawString(font, "§7Nature: §f${row.nature} §7| §7Ability: §f${row.ability}", textX, y + 18, 0xFFFFFFFF.toInt(), true)
        graphics.drawString(font, "§7IV: §f${"%.1f".format(row.ivPct)}%%  §7EV: §f${"%.1f".format(row.evPct)}%%", textX, y + 30, 0xFFFFFFFF.toInt(), true)

        val shinyPrefix = if (row.shiny) "§6✦ " else ""
        val genderStr = genderText(row.gender)
        graphics.drawString(font, "$shinyPrefix$genderStr", textX, y + 42, 0xFFFFFFFF.toInt(), true)

        val priceText = "§e${row.price} CD"
        val priceWidth = font.width(priceText)
        graphics.drawString(font, priceText, x + GuiBoxes.GTS_ROW_WIDTH - 10 - priceWidth, y + 42, 0xFFFFFFFF.toInt(), true)

        if (hovered) {
            val tooltip = buildTooltip(row).map { it.visualOrderText }
            graphics.renderTooltip(font, tooltip, mouseX, mouseY)
        }
    }

    private fun renderPokemonRowWithCancel(graphics: GuiGraphics, x: Int, y: Int, row: GtsViewRow, hovered: Boolean, mouseX: Int, mouseY: Int) {
        graphics.drawGtsRow(x, y, hovered)
        renderPokemonModel(graphics, x, y, row.species, row.shiny)

        val textX = x + 40
        val speciesName = row.species.substringAfter(':').replaceFirstChar { it.uppercase() }
        graphics.drawString(font, "§f$speciesName §7Lv.${row.level}", textX, y + 6, 0xFFFFFFFF.toInt(), true)
        graphics.drawString(font, "§7Nature: §f${row.nature} §7| §7Ability: §f${row.ability}", textX, y + 18, 0xFFFFFFFF.toInt(), true)
        graphics.drawString(font, "§7IV: §f${"%.1f".format(row.ivPct)}%%  §7EV: §f${"%.1f".format(row.evPct)}%%", textX, y + 30, 0xFFFFFFFF.toInt(), true)

        val priceText = "§e${row.price} CD"
        val priceWidth = font.width(priceText)
        graphics.drawString(font, priceText, x + GuiBoxes.GTS_ROW_WIDTH - GuiBoxes.GTS_CANCEL_WIDTH - 14 - priceWidth, y + 42, 0xFFFFFFFF.toInt(), true)

        val cancelX = x + GuiBoxes.GTS_ROW_WIDTH - GuiBoxes.GTS_CANCEL_WIDTH - 4
        val cancelY = y + (GuiBoxes.GTS_ROW_HEIGHT - GuiBoxes.GTS_CANCEL_HEIGHT) / 2
        val cancelHovered = mouseX >= cancelX && mouseX <= cancelX + GuiBoxes.GTS_CANCEL_WIDTH && mouseY >= cancelY && mouseY <= cancelY + GuiBoxes.GTS_CANCEL_HEIGHT
        graphics.drawGtsCancel(cancelX, cancelY, cancelHovered)
        graphics.drawCenteredString(font, "§fCancel", cancelX + GuiBoxes.GTS_CANCEL_WIDTH / 2, cancelY + 7, 0xFFFFFFFF.toInt())

        if (hovered) {
            val tooltip = buildTooltip(row).map { it.visualOrderText }
            graphics.renderTooltip(font, tooltip, mouseX, mouseY)
        }
    }

    private fun renderPartyRow(graphics: GuiGraphics, x: Int, y: Int, pokemon: GtsPartyRow, hovered: Boolean, mouseX: Int, mouseY: Int) {
        graphics.drawGtsSellCard(x, y, hovered)
        renderPokemonModel(graphics, x, y, pokemon.species, pokemon.shiny)

        val textX = x + 40
        val speciesName = pokemon.species.substringAfter(':').replaceFirstChar { it.uppercase() }
        graphics.drawString(font, "§f$speciesName §7Lv.${pokemon.level}", textX, y + 6, 0xFFFFFFFF.toInt(), true)
        graphics.drawString(font, "§7Nature: §f${pokemon.nature} §7| §7Ability: §f${pokemon.ability}", textX, y + 18, 0xFFFFFFFF.toInt(), true)
        graphics.drawString(font, "§7IV: §f${"%.1f".format(pokemon.ivPct)}%%  §7EV: §f${"%.1f".format(pokemon.evPct)}%%", textX, y + 30, 0xFFFFFFFF.toInt(), true)

        val shinyPrefix = if (pokemon.shiny) "§6✦ " else ""
        val genderStr = genderText(pokemon.gender)
        graphics.drawString(font, "${shinyPrefix}§7Slot ${pokemon.slot}  $genderStr", textX, y + 42, 0xFFFFFFFF.toInt(), true)

        if (hovered) {
            val tooltip = buildPartyTooltip(pokemon).map { it.visualOrderText }
            graphics.renderTooltip(font, tooltip, mouseX, mouseY)
        }
    }

    private fun renderPokemonModel(graphics: GuiGraphics, x: Int, y: Int, species: String, shiny: Boolean) {
        val stack = runCatching {
            PokemonItem.from(PokemonProperties.parse("species=$species shiny=$shiny"))
        }.getOrNull() ?: net.minecraft.world.item.ItemStack.EMPTY

        graphics.pose().pushPose()
        graphics.pose().translate((x + 4).toDouble(), (y + 2).toDouble(), 0.0)
        graphics.pose().scale(1.8f, 1.8f, 1.0f)
        graphics.renderItem(stack, 0, 0)
        graphics.pose().popPose()
    }

    private fun buildTooltip(row: GtsViewRow): List<Component> = listOf(
        Component.literal("§7HP:  ${ivColor(row.ivHp)}${row.ivHp}§7  §7Atk: ${ivColor(row.ivAtk)}${row.ivAtk}§7  §7Def: ${ivColor(row.ivDef)}${row.ivDef}"),
        Component.literal("§7SpA: ${ivColor(row.ivSpAtk)}${row.ivSpAtk}§7  §7SpD: ${ivColor(row.ivSpDef)}${row.ivSpDef}§7  §7Spe: ${ivColor(row.ivSpd)}${row.ivSpd}"),
        Component.literal(""),
        Component.literal("§7HP:  §f${row.evHp}§7  §7Atk: §f${row.evAtk}§7  §7Def: §f${row.evDef}"),
        Component.literal("§7SpA: §f${row.evSpAtk}§7  §7SpD: §f${row.evSpDef}§7  §7Spe: §f${row.evSpd}")
    )

    private fun buildPartyTooltip(pokemon: GtsPartyRow): List<Component> = listOf(
        Component.literal("§7HP:  ${ivColor(pokemon.ivHp)}${pokemon.ivHp}§7  §7Atk: ${ivColor(pokemon.ivAtk)}${pokemon.ivAtk}§7  §7Def: ${ivColor(pokemon.ivDef)}${pokemon.ivDef}"),
        Component.literal("§7SpA: ${ivColor(pokemon.ivSpAtk)}${pokemon.ivSpAtk}§7  §7SpD: ${ivColor(pokemon.ivSpDef)}${pokemon.ivSpDef}§7  §7Spe: ${ivColor(pokemon.ivSpd)}${pokemon.ivSpd}"),
        Component.literal(""),
        Component.literal("§7HP:  §f${pokemon.evHp}§7  §7Atk: §f${pokemon.evAtk}§7  §7Def: §f${pokemon.evDef}"),
        Component.literal("§7SpA: §f${pokemon.evSpAtk}§7  §7SpD: §f${pokemon.evSpDef}§7  §7Spe: §f${pokemon.evSpd}")
    )

    private fun renderScrollbar(graphics: GuiGraphics, px: Int, py: Int) {
        val displayRows = currentDisplayRows()
        val visible = (contentEnd - currentContentStart()) / rowHeight
        val maxScroll = maxOf(0.0, displayRows.size - visible.toDouble())
        if (maxScroll <= 0.0) return

        val scrollbarX = px + panelWidth - 10
        val scrollbarY = py + currentContentStart()
        val scrollbarHeight = contentEnd - currentContentStart()

        graphics.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0x20FFFFFF)

        val handleHeight = 40
        val handleY = scrollbarY + (scrollOffset / maxScroll * (scrollbarHeight - handleHeight)).toInt()
        graphics.fill(scrollbarX, handleY, scrollbarX + 6, handleY + handleHeight, 0x80FFFFFF.toInt())
    }
}
