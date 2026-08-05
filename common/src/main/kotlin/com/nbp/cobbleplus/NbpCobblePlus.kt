package com.nbp.cobbleplus

import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureManager
import com.nbp.cobbleplus.feature.impl.AutoAnnouncerFeature
import com.nbp.cobbleplus.feature.impl.BroadcastFeature
import com.nbp.cobbleplus.feature.impl.CatchComboFeature
import com.nbp.cobbleplus.feature.impl.PartyHealFeature
import com.nbp.cobbleplus.feature.impl.WelcomeFeature
import com.nbp.cobbleplus.feature.impl.VanillaMobSpawnBlockerFeature
import com.nbp.cobbleplus.feature.impl.LegendarySpawnerFeature
import com.nbp.cobbleplus.feature.impl.MeltanFurnaceFeature
import com.nbp.cobbleplus.feature.impl.ItemMechanicsFeature
import com.nbp.cobbleplus.feature.impl.PokemonLootModifierFeature
import com.nbp.cobbleplus.feature.impl.CaptureCapFeature
import com.nbp.cobbleplus.feature.impl.PokemonApiaryFeature
import com.nbp.cobbleplus.feature.impl.EconomyFeature
import com.nbp.cobbleplus.feature.impl.PointsFeature
import com.nbp.cobbleplus.feature.impl.SafariZoneFeature
import com.nbp.cobbleplus.feature.impl.WagerBattleFeature
import com.nbp.cobbleplus.feature.impl.ServerEventsFeature
import com.nbp.cobbleplus.config.PokemonLootConfig
import com.nbp.cobbleplus.config.MissionsConfigFile
import com.nbp.cobbleplus.feature.impl.MissionsFeature
import com.nbp.cobbleplus.feature.impl.SynchronizedNaturesFeature
import org.slf4j.LoggerFactory

object NbpCobblePlus {
    const val MOD_ID = "nbp_cobble_plus"
    const val MOD_NAME = "NBP Cobble Plus"

    val logger = LoggerFactory.getLogger(MOD_NAME)

    fun init() {
        logger.info("[$MOD_NAME] Inicializando suíte multifuncional do Time NBP...")
        
        // Carrega configurações
        NbpConfig.load()
        PokemonLootConfig.load()
        MissionsConfigFile.load()

        // Registra módulos
        FeatureManager.register(BroadcastFeature)
        FeatureManager.register(WelcomeFeature)
        FeatureManager.register(AutoAnnouncerFeature)
        FeatureManager.register(PartyHealFeature)
        FeatureManager.register(CatchComboFeature)
        FeatureManager.register(CaptureCapFeature)
        FeatureManager.register(VanillaMobSpawnBlockerFeature)
        FeatureManager.register(LegendarySpawnerFeature)
        FeatureManager.register(MeltanFurnaceFeature)
        FeatureManager.register(ItemMechanicsFeature)
        FeatureManager.register(PokemonLootModifierFeature)
        FeatureManager.register(PokemonApiaryFeature)
        FeatureManager.register(EconomyFeature)
        FeatureManager.register(PointsFeature)
        FeatureManager.register(SafariZoneFeature)
        FeatureManager.register(WagerBattleFeature)
        FeatureManager.register(ServerEventsFeature)
        FeatureManager.register(MissionsFeature)
        FeatureManager.register(SynchronizedNaturesFeature)

        // Inicializa módulos
        FeatureManager.initAll()

        logger.info("[$MOD_NAME] Suíte multifuncional do Time NBP ativada com sucesso!")
    }
}
