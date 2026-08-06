package com.nbp.cobbleplus.feature.impl

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/** Runtime bridge for RCT. Reflection keeps RCT optional at compile time. */
object RctRuntimeRegistry {
    private val data = ConcurrentHashMap<String, Any>()

    fun register(definition: EditableRctTrainer): Boolean = runCatching {
        val api = Class.forName("com.gitlab.srcmc.rctmod.ModCommon").getField("RCT").get(null)
        val registry = api.javaClass.getMethod("getTrainerRegistry").invoke(api)
        val gson = Class.forName("com.gitlab.srcmc.rctmod.api.utils.JsonUtils").getField("GSON").get(null)
        val teamClass = Class.forName("com.gitlab.srcmc.rctmod.api.data.pack.TrainerTeam")
        val teamJson = JsonObject().apply {
            addProperty("name", definition.name)
            add("team", JsonParser.parseString(com.google.gson.Gson().toJson(definition.team)).asJsonArray)
        }
        val team = gson.javaClass.getMethod("fromJson", String::class.java, Class::class.java).invoke(gson, teamJson.toString(), teamClass)
        registry.javaClass.getMethod("registerNPC", String::class.java, Class.forName("com.gitlab.srcmc.rctapi.api.models.TrainerModel"))
            .invoke(registry, definition.id, team)
        val mobClass = Class.forName("com.gitlab.srcmc.rctmod.api.data.pack.TrainerMobData")
        val mob = gson.javaClass.getMethod("fromJson", String::class.java, Class::class.java)
            .invoke(gson, "{\"series\":[\"${definition.series}\"]}", mobClass)
        setField(mobClass, mob, "trainerTeam", team)
        setField(mobClass, mob, "trainerId", definition.id)
        data[definition.id] = mob
        true
    }.getOrDefault(false)

    fun dataFor(id: String): Any? = data[id]
    fun clear() = data.clear()

    private fun setField(type: Class<*>, instance: Any, name: String, value: Any?) {
        var current: Class<*>? = type
        while (current != null) {
            runCatching {
                val field: Field = current!!.getDeclaredField(name)
                field.isAccessible = true
                field.set(instance, value)
                return
            }
            current = current!!.superclass
        }
    }
}
