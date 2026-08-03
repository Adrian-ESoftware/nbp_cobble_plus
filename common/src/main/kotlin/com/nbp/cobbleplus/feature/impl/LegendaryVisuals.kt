package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.ChatFormatting
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

object LegendaryVisuals {
    private const val RESISTANCE_AMPLIFIER = 4 // amplifier 4 = Resistance V

    fun apply(entity: PokemonEntity, species: String) {
        entity.addEffect(
            MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                MobEffectInstance.INFINITE_DURATION,
                RESISTANCE_AMPLIFIER,
                false,
                false,
                true
            )
        )
        entity.setGlowingTag(true)

        val color = colorFor(species.lowercase())
        val scoreboard = entity.level().scoreboard
        val teamName = "nbp_leg_${color.name.lowercase()}".take(16)
        val team = scoreboard.getPlayerTeam(teamName) ?: scoreboard.addPlayerTeam(teamName).also {
            it.color = color
            it.isAllowFriendlyFire = false
        }
        scoreboard.addPlayerToTeam(entity.scoreboardName, team)
    }

    internal fun colorFor(species: String): ChatFormatting = when (species.lowercase()) {
        "dialga", "articuno", "kyogre", "suicune", "lugia", "deoxys" -> ChatFormatting.AQUA
        "palkia", "mew", "mewtwo", "mesprit", "hoopa" -> ChatFormatting.LIGHT_PURPLE
        "giratina", "darkrai", "yveltal", "eternatus" -> ChatFormatting.DARK_PURPLE
        "groudon", "moltres", "entei", "hooh", "heatran", "volcanion" -> ChatFormatting.RED
        "zapdos", "raikou", "regieleki", "jirachi" -> ChatFormatting.YELLOW
        "rayquaza", "celebi", "shaymin", "zygarde" -> ChatFormatting.GREEN
        "arceus", "regigigas", "reshiram" -> ChatFormatting.WHITE
        "zekrom", "necrozma" -> ChatFormatting.DARK_GRAY
        else -> ChatFormatting.GOLD
    }
}
