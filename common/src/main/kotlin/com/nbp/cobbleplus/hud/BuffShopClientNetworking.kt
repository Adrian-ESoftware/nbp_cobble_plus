package com.nbp.cobbleplus.hud

/** Platform hooks used by the client-only Buff Shop screen to send server purchase requests. */
object BuffShopClientNetworking {
    var purchase: (String, Int) -> Unit = { _, _ -> }
}
