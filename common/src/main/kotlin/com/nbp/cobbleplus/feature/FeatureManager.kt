package com.nbp.cobbleplus.feature

import org.slf4j.LoggerFactory

object FeatureManager {
    private val logger = LoggerFactory.getLogger("NBP-FeatureManager")
    private val features = mutableListOf<FeatureModule>()

    fun register(feature: FeatureModule) {
        features.add(feature)
    }

    fun initAll() {
        logger.info("Inicializando módulos de recursos do NBP Cobble Plus...")
        for (feature in features) {
            if (feature.isEnabled) {
                try {
                    feature.onEnable()
                    logger.info("Módulo [${feature.name}] ativado.")
                } catch (e: Exception) {
                    logger.error("Erro ao ativar módulo [${feature.name}]", e)
                }
            } else {
                logger.info("Módulo [${feature.name}] desativado pela configuração.")
            }
        }
    }

    fun reloadAll() {
        logger.info("Recarregando módulos de recursos do NBP Cobble Plus...")
        for (feature in features) {
            try {
                feature.onReload()
            } catch (e: Exception) {
                logger.error("Erro ao recarregar módulo [${feature.name}]", e)
            }
        }
    }

    fun getFeatures(): List<FeatureModule> = features.toList()
}
