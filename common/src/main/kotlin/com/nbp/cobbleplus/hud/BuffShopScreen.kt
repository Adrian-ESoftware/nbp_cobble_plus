package com.nbp.cobbleplus.hud

import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.feature.impl.BuffCatalog
import com.nbp.cobbleplus.feature.impl.BuffDefinition
import com.nbp.cobbleplus.feature.impl.PointType
import com.nbp.cobbleplus.feature.impl.roman
import com.nbp.cobbleplus.hud.GuiBoxes.drawButton
import com.nbp.cobbleplus.hud.GuiBoxes.drawMissionCard
import com.nbp.cobbleplus.hud.GuiBoxes.drawPanel
import com.nbp.cobbleplus.hud.GuiBoxes.drawTabButton
import com.nbp.cobbleplus.network.ActiveBuffSync
import com.nbp.cobbleplus.network.BuffShopSyncPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL11

class BuffShopScreen(payload: BuffShopSyncPayload) :
    Screen(Component.literal(if (payload.portuguese) "Loja de Pontos" else "Points Shop")) {

    private var portuguese: Boolean = payload.portuguese
    private var balances: Map<String, Long> = payload.balances
    private var activeBuffs: Map<String, ActiveBuffSync> = payload.activeBuffs.associateBy { it.buffId }
    private var selectedTab: Int = 0 // 0: Buffs, 1: Mods
    private var scrollOffset: Double = 0.0

    private val panelWidth = GuiBoxes.PANEL_WIDTH
    private val panelHeight = GuiBoxes.PANEL_HEIGHT
    private val closeWidth = GuiBoxes.BUTTON_WIDTH
    private val closeHeight = GuiBoxes.BUTTON_HEIGHT

    private val contentStartY = 76
    private val contentEndY = 286
    private val cardWidth = GuiBoxes.CARD_WIDTH
    private val cardHeight = 44
    private val rowHeight = 48

    private fun panelX(): Int = (width - panelWidth) / 2
    private fun panelY(): Int = (height - panelHeight) / 2

    private fun tabBounds(index: Int): IntArray {
        val tabWidth = 100
        val total = tabWidth * 2 + 8
        val x0 = panelX() + (panelWidth - total) / 2
        val y = panelY() + 26
        return intArrayOf(x0 + index * (tabWidth + 8), y, tabWidth, GuiBoxes.BUTTON_HEIGHT)
    }

    private fun closeBounds(): IntArray {
        val x = width / 2 - closeWidth / 2
        val y = panelY() + panelHeight - closeHeight - 6
        return intArrayOf(x, y, closeWidth, closeHeight)
    }

    fun update(payload: BuffShopSyncPayload) {
        portuguese = payload.portuguese
        balances = payload.balances
        activeBuffs = payload.activeBuffs.associateBy { it.buffId }
    }

    override fun renderBlurredBackground(partialTick: Float) = Unit
    override fun isPauseScreen(): Boolean = false

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (selectedTab == 0) {
            val visible = (contentEndY - contentStartY).toDouble() / rowHeight
            val maxScroll = maxOf(0.0, BuffCatalog.ALL.size - visible)
            scrollOffset = (scrollOffset - scrollY * 0.5).coerceIn(0.0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        // Close button
        val close = closeBounds()
        if (mouseX >= close[0] && mouseX <= close[0] + close[2] && mouseY >= close[1] && mouseY <= close[1] + close[3]) {
            onClose()
            return true
        }

        // Tabs
        for (i in 0..1) {
            val tab = tabBounds(i)
            if (mouseX >= tab[0] && mouseX <= tab[0] + tab[2] && mouseY >= tab[1] && mouseY <= tab[1] + tab[3]) {
                selectedTab = i
                scrollOffset = 0.0
                return true
            }
        }

        // Buffs purchase clicks
        if (selectedTab == 0) {
            val px = panelX()
            val py = panelY()
            if (mouseY >= py + contentStartY && mouseY <= py + contentEndY) {
                val buffs = BuffCatalog.ALL
                for (i in buffs.indices) {
                    val buff = buffs[i]
                    val cardY = py + contentStartY + ((i - scrollOffset) * rowHeight).toInt()
                    val cardX = px + (panelWidth - cardWidth) / 2

                    if (cardY + cardHeight < py + contentStartY || cardY > py + contentEndY) continue

                    if (buff.maxLevel == 1) {
                        val btnW = 74
                        val btnH = 20
                        val btnX = cardX + cardWidth - btnW - 6
                        val btnY = cardY + 12
                        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                            BuffShopClientNetworking.purchase(buff.id, 1)
                            return true
                        }
                    } else {
                        val btnW = 24
                        val btnH = 20
                        val spacing = 3
                        val totalBtnsW = 5 * btnW + 4 * spacing
                        val startBtnX = cardX + cardWidth - totalBtnsW - 6
                        val btnY = cardY + 12
                        for (lvl in 1..5) {
                            val btnX = startBtnX + (lvl - 1) * (btnW + spacing)
                            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                                BuffShopClientNetworking.purchase(buff.id, lvl)
                                return true
                            }
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fillGradient(0, 0, width, height, 0x40101820.toInt(), 0x1A0A0A10.toInt())

        val px = panelX()
        val py = panelY()
        graphics.drawPanel(px, py)

        // Title
        val titleText = if (portuguese) "Loja de Pontos" else "Points Shop"
        graphics.drawCenteredString(font, "§f$titleText", width / 2, py + 12, 0xFFFFFFFF.toInt())

        // Tabs
        val tab0 = tabBounds(0)
        graphics.drawTabButton(tab0[0], tab0[1], tab0[2], selectedTab == 0)
        graphics.drawCenteredString(font, "Buffs", tab0[0] + tab0[2] / 2, tab0[1] + 7, 0xFFFFFFFF.toInt())

        val tab1 = tabBounds(1)
        graphics.drawTabButton(tab1[0], tab1[1], tab1[2], selectedTab == 1)
        graphics.drawCenteredString(font, "Mods", tab1[0] + tab1[2] / 2, tab1[1] + 7, 0xFFFFFFFF.toInt())

        if (selectedTab == 0) {
            renderBuffsTab(graphics, mouseX, mouseY, px, py)
        } else {
            renderModsTab(graphics, px, py)
        }

        // Close button
        val close = closeBounds()
        val closeHovered = mouseX >= close[0] && mouseX <= close[0] + close[2] && mouseY >= close[1] && mouseY <= close[1] + close[3]
        graphics.drawButton(close[0], close[1], closeHovered)
        val closeText = if (portuguese) "Fechar" else "Close"
        graphics.drawCenteredString(font, "§f$closeText", close[0] + closeWidth / 2, close[1] + 7, 0xFFFFFFFF.toInt())

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun renderBuffsTab(graphics: GuiGraphics, mouseX: Int, mouseY: Int, px: Int, py: Int) {
        // Balances summary
        val vPts = balances["victory"] ?: 0L
        val cPts = balances["capture"] ?: 0L
        val sPts = balances["shiny"] ?: 0L
        val lPts = balances["legendary"] ?: 0L

        val vLabel = "${PointType.VICTORY.color}${if (portuguese) "Vitória" else "Victory"}: §f$vPts"
        val cLabel = "${PointType.CAPTURE.color}${if (portuguese) "Captura" else "Capture"}: §f$cPts"
        val sLabel = "${PointType.SHINY.color}Shiny: §f$sPts"
        val lLabel = "${PointType.LEGENDARY.color}${if (portuguese) "Lendário" else "Legendary"}: §f$lPts"
        val balanceBar = "$vLabel  §8|  $cLabel  §8|  $sLabel  §8|  $lLabel"
        graphics.drawCenteredString(font, balanceBar, width / 2, py + 56, 0xFFFFFFFF.toInt())

        val mc = Minecraft.getInstance()
        val scale = mc.window.guiScale
        val clipX = (px + (panelWidth - cardWidth) / 2) * scale
        val clipY = (py + contentStartY) * scale
        val clipW = cardWidth * scale
        val clipH = (contentEndY - contentStartY) * scale

        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(clipX.toInt(), mc.window.height - (clipY + clipH).toInt(), clipW.toInt(), clipH.toInt())

        val buffs = BuffCatalog.ALL
        var hoveredTooltip: List<Component>? = null

        for (i in buffs.indices) {
            val buff = buffs[i]
            val cardY = py + contentStartY + ((i - scrollOffset) * rowHeight).toInt()
            val cardX = px + (panelWidth - cardWidth) / 2

            if (cardY + cardHeight < py + contentStartY - 10 || cardY > py + contentEndY + 10) continue

            graphics.drawMissionCard(cardX, cardY)

            // Buff icon
            val iconRes = ResourceLocation.fromNamespaceAndPath(
                NbpCobblePlus.MOD_ID,
                "textures/gui/types/${buff.iconType.ifBlank { "normal" }}.png"
            )
            graphics.pose().pushPose()
            val iconX = cardX + 8
            val iconY = cardY + 14
            val scaleIcon = 16f / 36f
            graphics.pose().translate(iconX.toDouble(), iconY.toDouble(), 0.0)
            graphics.pose().scale(scaleIcon, scaleIcon, 1f)
            graphics.blit(iconRes, 0, 0, 0f, 0f, 36, 36, 36, 36)
            graphics.pose().popPose()

            // Buff details text
            val active = activeBuffs[buff.id]
            val activeText = if (active != null) {
                val lvlRoman = if (buff.maxLevel > 1) " " + roman(active.level) else ""
                "§a[Ativo$lvlRoman - ${formatTime(active.remainingSeconds)}]"
            } else {
                "§8[Inativo]"
            }
            val titleStr = "§f${buff.displayName(portuguese)}  $activeText"
            graphics.drawString(font, titleStr, cardX + 30, cardY + 8, 0xFFFFFFFF.toInt(), true)

            val effectStr = "§7${buff.description(portuguese)}"
            graphics.drawString(font, effectStr, cardX + 30, cardY + 24, 0xFFAAAAAA.toInt(), true)

            // Buttons
            if (buff.maxLevel == 1) {
                val btnW = 74
                val btnH = 20
                val btnX = cardX + cardWidth - btnW - 6
                val btnY = cardY + 12
                val isHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH &&
                        mouseY >= py + contentStartY && mouseY <= py + contentEndY
                val isActive = active != null
                val cost = buff.cost(1)
                val curBal = balances[buff.pointType.id] ?: 0L
                val canAfford = curBal >= cost

                val bgCol = when {
                    isActive -> 0x80228822.toInt()
                    isHovered -> 0x803A5A80.toInt()
                    else -> 0x50182434.toInt()
                }
                val borderCol = when {
                    isActive -> 0xFF44FF44.toInt()
                    isHovered -> 0xFF88AAEE.toInt()
                    else -> 0x806A7A96.toInt()
                }
                drawBox(graphics, btnX, btnY, btnW, btnH, bgCol, borderCol)

                val btnLabel = if (isActive) "§aAtivo" else "§e10 ${buff.pointType.displayName(portuguese)}"
                graphics.drawCenteredString(font, btnLabel, btnX + btnW / 2, btnY + 6, 0xFFFFFFFF.toInt())

                if (isHovered) {
                    hoveredTooltip = listOf(
                        Component.literal("§6§l${buff.displayName(portuguese)}"),
                        Component.literal("§7${buff.description(portuguese)}"),
                        Component.literal("§7Duração: §f30 minutos"),
                        Component.literal("§7Custo: §e$cost pontos de ${buff.pointType.displayName(portuguese)}"),
                        Component.literal(if (canAfford) "§a✔ Clique para ativar/renovar" else "§c✖ Pontos insuficientes")
                    )
                }
            } else {
                val btnW = 24
                val btnH = 20
                val spacing = 3
                val totalBtnsW = 5 * btnW + 4 * spacing
                val startBtnX = cardX + cardWidth - totalBtnsW - 6
                val btnY = cardY + 12

                for (lvl in 1..5) {
                    val btnX = startBtnX + (lvl - 1) * (btnW + spacing)
                    val isHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH &&
                            mouseY >= py + contentStartY && mouseY <= py + contentEndY
                    val isActiveLevel = active?.level == lvl
                    val cost = buff.cost(lvl)
                    val curBal = balances[buff.pointType.id] ?: 0L
                    val canAfford = curBal >= cost

                    val bgCol = when {
                        isActiveLevel -> 0x80228822.toInt()
                        isHovered -> 0x803A5A80.toInt()
                        else -> 0x50182434.toInt()
                    }
                    val borderCol = when {
                        isActiveLevel -> 0xFF44FF44.toInt()
                        isHovered -> 0xFF88AAEE.toInt()
                        else -> 0x806A7A96.toInt()
                    }
                    drawBox(graphics, btnX, btnY, btnW, btnH, bgCol, borderCol)

                    val textCol = if (isActiveLevel) 0xFF55FF55.toInt() else 0xFFFFFFFF.toInt()
                    graphics.drawCenteredString(font, roman(lvl), btnX + btnW / 2, btnY + 6, textCol)

                    if (isHovered) {
                        val effectFormatted = buff.formattedEffect(lvl, portuguese)
                        hoveredTooltip = listOf(
                            Component.literal("§6§l${buff.displayName(portuguese)} ${roman(lvl)}"),
                            Component.literal("§7Efeito: §a$effectFormatted"),
                            Component.literal("§7Duração: §f30 minutos"),
                            Component.literal("§7Custo: §e$cost pontos de ${buff.pointType.displayName(portuguese)}"),
                            Component.literal(if (canAfford) "§a✔ Clique para comprar / renovar" else "§c✖ Pontos insuficientes")
                        )
                    }
                }
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST)

        renderScrollbar(graphics, px, py)

        hoveredTooltip?.let { lines ->
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY)
        }
    }

    private fun renderModsTab(graphics: GuiGraphics, px: Int, py: Int) {
        val cardX = px + (panelWidth - cardWidth) / 2
        val cardY = py + 90
        drawBox(graphics, cardX, cardY, cardWidth, 120, 0x40101820.toInt(), 0x806A7A96.toInt())

        val title = if (portuguese) "§6Módulos de Pontos & Upgrades" else "§6Point Mods & Upgrades"
        graphics.drawCenteredString(font, title, width / 2, cardY + 16, 0xFFFFFFFF.toInt())

        val line1 = if (portuguese) {
            "§7Esta aba está reservada para implementação futura."
        } else {
            "§7This tab is reserved for future implementation."
        }
        val line2 = if (portuguese) {
            "§fNovos módulos permanentes, auras cosméticas e expansões"
        } else {
            "§fNew permanent modules, cosmetic auras and expansions"
        }
        val line3 = if (portuguese) {
            "§fde pontos serão disponibilizados aqui em atualizações futuras."
        } else {
            "§fwill be available for purchase with points in future updates."
        }
        val line4 = if (portuguese) {
            "§eFique atento aos anúncios e novidades do modpack!"
        } else {
            "§eStay tuned to server announcements and modpack updates!"
        }

        graphics.drawCenteredString(font, line1, width / 2, cardY + 38, 0xFFAAAAAA.toInt())
        graphics.drawCenteredString(font, line2, width / 2, cardY + 56, 0xFFFFFFFF.toInt())
        graphics.drawCenteredString(font, line3, width / 2, cardY + 70, 0xFFFFFFFF.toInt())
        graphics.drawCenteredString(font, line4, width / 2, cardY + 92, 0xFFFFAA00.toInt())
    }

    private fun drawBox(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, bg: Int, border: Int) {
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg)
        graphics.fill(x, y, x + w, y + 1, border)
        graphics.fill(x, y + h - 1, x + w, y + h, border)
        graphics.fill(x, y, x + 1, y + h, border)
        graphics.fill(x + w - 1, y, x + w, y + h, border)
    }

    private fun renderScrollbar(graphics: GuiGraphics, px: Int, py: Int) {
        val visible = (contentEndY - contentStartY).toDouble() / rowHeight
        val maxScroll = maxOf(0.0, BuffCatalog.ALL.size - visible)
        if (maxScroll <= 0.0) return

        val scrollbarX = px + panelWidth - 8
        val scrollbarY = py + contentStartY
        val scrollbarHeight = contentEndY - contentStartY

        graphics.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0x20FFFFFF)

        val handleHeight = 40
        val handleY = scrollbarY + (scrollOffset / maxScroll * (scrollbarHeight - handleHeight)).toInt()
        graphics.fill(scrollbarX, handleY, scrollbarX + 4, handleY + handleHeight, 0x80FFFFFF.toInt())
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
