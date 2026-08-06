package com.nbp.cobbleplus.hud

/** Platform hooks used by the client-only GTS screen to send server requests. */
object GtsClientNetworking {
    var purchase: (Long) -> Unit = {}
    var collect: () -> Unit = {}
}
