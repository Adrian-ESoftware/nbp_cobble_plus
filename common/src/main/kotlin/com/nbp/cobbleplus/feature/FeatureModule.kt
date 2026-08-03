package com.nbp.cobbleplus.feature

interface FeatureModule {
    val name: String
    val isEnabled: Boolean

    fun onEnable()
    fun onDisable()
    fun onReload() {
        onDisable()
        if (isEnabled) {
            onEnable()
        }
    }
}
