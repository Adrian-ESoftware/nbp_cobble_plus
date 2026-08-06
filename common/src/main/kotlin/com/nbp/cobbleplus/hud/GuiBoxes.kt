package com.nbp.cobbleplus.hud

import com.cobblemon.mod.common.api.gui.blitk
import com.nbp.cobbleplus.NbpCobblePlus
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

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
    const val GTS_ROW_WIDTH = 334
    const val GTS_ROW_HEIGHT = 54
    const val GTS_ROW_GAP = 4
    const val GTS_SELL_CARD_WIDTH = 334
    const val GTS_SELL_CARD_HEIGHT = 54
    const val GTS_BACK_WIDTH = 50
    const val GTS_BACK_HEIGHT = 18
    const val GTS_CANCEL_WIDTH = 60
    const val GTS_CANCEL_HEIGHT = 22
    const val GTS_COLLECT_WIDTH = 90
    const val GTS_COLLECT_HEIGHT = 22

    private val panel = loc("panel")
    private val card = loc("card")
    private val button = loc("button")
    private val buttonHover = loc("button_hover")
    private val tab = loc("tab")
    private val tabActive = loc("tab_active")
    private val missionCard = loc("mission_card")
    private val bar = loc("bar")
    private val barFill = loc("bar_fill")
    private val gtsRow = loc("gts_row")
    private val gtsRowHover = loc("gts_row_hover")
    private val gtsSellCard = loc("gts_sell_card")
    private val gtsSellCardHover = loc("gts_sell_card_hover")
    private val gtsScrollbarBg = loc("gts_scrollbar_bg")
    private val gtsScrollbarHandle = loc("gts_scrollbar_handle")
    private val gtsBack = loc("gts_back")
    private val gtsBackHover = loc("gts_back_hover")
    private val gtsCancel = loc("gts_cancel")
    private val gtsCancelHover = loc("gts_cancel_hover")
    private val gtsCollect = loc("gts_collect")
    private val gtsCollectHover = loc("gts_collect_hover")

    private fun loc(name: String) =
        ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "textures/gui/$name.png")

    fun GuiGraphics.drawPanel(x: Int, y: Int) =
        blit(panel, x, y, PANEL_WIDTH, PANEL_HEIGHT)

    fun GuiGraphics.drawCard(x: Int, y: Int) =
        blit(card, x, y, CARD_WIDTH, CARD_HEIGHT)

    fun GuiGraphics.drawButton(x: Int, y: Int, hovered: Boolean) =
        blit(if (hovered) buttonHover else button, x, y, BUTTON_WIDTH, BUTTON_HEIGHT)

    fun GuiGraphics.drawTabButton(x: Int, y: Int, width: Int, active: Boolean) {
        blitk(
            pose(), if (active) tabActive else tab, x, y,
            height = BUTTON_HEIGHT, width = width,
            textureWidth = width, textureHeight = BUTTON_HEIGHT
        )
    }

    fun GuiGraphics.drawMissionCard(x: Int, y: Int) =
        blit(missionCard, x, y, MISSION_CARD_WIDTH, MISSION_CARD_HEIGHT)

    fun GuiGraphics.drawBarBg(x: Int, y: Int) =
        blit(bar, x, y, BAR_WIDTH, BAR_HEIGHT)

    fun GuiGraphics.drawBarFill(x: Int, y: Int, fraction: Float) {
        val w = (BAR_WIDTH * fraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(2)
        blitk(pose(), barFill, x, y, height = BAR_HEIGHT, width = w, textureWidth = BAR_WIDTH, textureHeight = BAR_HEIGHT)
    }

    fun GuiGraphics.drawGtsRow(x: Int, y: Int, hovered: Boolean) =
        blit(if (hovered) gtsRowHover else gtsRow, x, y, GTS_ROW_WIDTH, GTS_ROW_HEIGHT)

    fun GuiGraphics.drawGtsSellCard(x: Int, y: Int, hovered: Boolean) =
        blit(if (hovered) gtsSellCardHover else gtsSellCard, x, y, GTS_SELL_CARD_WIDTH, GTS_SELL_CARD_HEIGHT)

    fun GuiGraphics.drawGtsScrollbarBg(x: Int, y: Int) =
        blit(gtsScrollbarBg, x, y, 6, 200)

    fun GuiGraphics.drawGtsScrollbarHandle(x: Int, y: Int) =
        blit(gtsScrollbarHandle, x, y, 6, 40)

    fun GuiGraphics.drawGtsBack(x: Int, y: Int, hovered: Boolean) =
        blit(if (hovered) gtsBackHover else gtsBack, x, y, GTS_BACK_WIDTH, GTS_BACK_HEIGHT)

    fun GuiGraphics.drawGtsCancel(x: Int, y: Int, hovered: Boolean) =
        blit(if (hovered) gtsCancelHover else gtsCancel, x, y, GTS_CANCEL_WIDTH, GTS_CANCEL_HEIGHT)

    fun GuiGraphics.drawGtsCollect(x: Int, y: Int, hovered: Boolean) =
        blit(if (hovered) gtsCollectHover else gtsCollect, x, y, GTS_COLLECT_WIDTH, GTS_COLLECT_HEIGHT)

    private fun GuiGraphics.blit(res: ResourceLocation, x: Int, y: Int, w: Int, h: Int) {
        blitk(pose(), res, x, y, height = h, width = w, textureWidth = w, textureHeight = h)
    }
}
