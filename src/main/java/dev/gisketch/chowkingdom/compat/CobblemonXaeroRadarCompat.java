package dev.gisketch.chowkingdom.compat;

import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CobblemonXaeroRadarCompat {
    public static final int UNSCANNED_DOT_COLOR = 0xFFFFFF;

    private static final String POKEMON_ENTITY_CLASS = "com.cobblemon.mod.common.entity.pokemon.PokemonEntity";
    private static final String COBBLEMON_CLIENT_CLASS = "com.cobblemon.mod.common.client.CobblemonClient";
    private static final String XAERO_RADAR_ICON_MANAGER_CLASS = "xaero.hud.minimap.radar.icon.RadarIconManager";
    private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private static volatile Object xaeroDotIcon;

    private CobblemonXaeroRadarCompat() {
    }

    public static boolean shouldForceUnscannedPokemonDot(Entity entity) {
        if (!POKEMON_ENTITY_CLASS.equals(entity.getClass().getName())) {
            return false;
        }

        String speciesId = speciesId(entity);
        if (speciesId == null || speciesId.isBlank()) {
            return false;
        }

        Object pokedex = clientPokedexData();
        if (pokedex == null) {
            return true;
        }

        Object record = speciesRecord(pokedex, speciesId);
        if (record == null) {
            return true;
        }

        Object knowledge = call(record, "getKnowledge");
        if (knowledge instanceof Enum<?> entryProgress) {
            return entryProgress.ordinal() <= 0 || "NONE".equalsIgnoreCase(entryProgress.name());
        }

        return true;
    }

    public static Object xaeroDotIcon() {
        Object cached = xaeroDotIcon;
        if (cached != null) {
            return cached;
        }

        try {
            Class<?> managerClass = Class.forName(XAERO_RADAR_ICON_MANAGER_CLASS);
            Field dotField = managerClass.getField("DOT");
            Object value = dotField.get(null);
            xaeroDotIcon = value;
            return value;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String speciesId(Entity entity) {
        Object pokemon = call(entity, "getPokemon");
        Object species = pokemon == null ? null : call(pokemon, "getSpecies");
        if (species == null) {
            species = call(entity, "getExposedSpecies");
        }

        Object resourceIdentifier = species == null ? null : call(species, "getResourceIdentifier");
        return resourceIdentifier == null ? null : resourceIdentifier.toString().toLowerCase();
    }

    private static Object clientPokedexData() {
        try {
            Class<?> clientClass = Class.forName(COBBLEMON_CLIENT_CLASS);
            Object instance = clientClass.getField("INSTANCE").get(null);
            return call(instance, "getClientPokedexData");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object speciesRecord(Object pokedex, String speciesId) {
        Object records = call(pokedex, "getSpeciesRecords");
        if (!(records instanceof Map<?, ?> speciesRecords)) {
            return null;
        }

        for (Map.Entry<?, ?> entry : speciesRecords.entrySet()) {
            Object key = entry.getKey();
            if (key != null && speciesId.equalsIgnoreCase(key.toString())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private static Object call(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        Method method = method(target.getClass(), methodName);
        if (method == null) {
            return null;
        }

        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String methodName) {
        String key = owner.getName() + "#" + methodName;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            Method method = owner.getMethod(methodName);
            METHOD_CACHE.putIfAbsent(key, method);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}