package me.julionxn.blocmpact.client.impl.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.julionxn.blocmpact.Blocmpact;
import me.julionxn.blocmpact.client.api.EffectFactory;
import me.julionxn.blocmpact.client.impl.FailureLog;
import me.julionxn.blocmpact.client.impl.post.PostChain;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads data-driven effects from {@code assets/<ns>/blocmpact/effects/<path>.json} (id {@code ns:path}) on every
 * resource reload (F3+T included), and drops compiled shaders so edited {@code .fsh} files are rebuilt.
 */
public final class DataEffects extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {
    public static final DataEffects INSTANCE = new DataEffects();
    public static final Identifier ID = Blocmpact.id("effects");
    private static final String FOLDER = "blocmpact/effects";

    private volatile Map<Identifier, DataEffectDef> definitions = Map.of();

    private DataEffects() {
    }

    public boolean has(Identifier id) {
        return definitions.containsKey(id);
    }

    public Set<Identifier> ids() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public EffectFactory factory(Identifier id) {
        DataEffectDef def = definitions.get(id);
        return def == null ? null : factory(def);
    }

    /**
     * Parses a definition that does not come from a resource pack. The JSON is copied (the parsed form keeps
     * references into it), so the caller may reuse or mutate its object afterwards.
     */
    public static EffectFactory parse(Identifier id, JsonObject json) {
        DataEffectDef def;
        try {
            def = DataEffectDef.parse(id, json.deepCopy());
        } catch (RuntimeException ex) {
            // Gson's getAs* throw on wrong types with bare messages ("JsonObject"); keep the exception name for those.
            String why = ex instanceof Specs.DataException ? ex.getMessage() : ex.toString();
            throw new IllegalArgumentException("data effect " + id + " is invalid: " + why, ex);
        }
        return factory(def);
    }

    private static EffectFactory factory(DataEffectDef def) {
        return spawn -> new DataEffect(spawn, def);
    }

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> out = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Resource> e : manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
            String path = e.getKey().getPath();
            Identifier id = Identifier.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(FOLDER.length() + 1, path.length() - 5));
            try (Reader r = e.getValue().openAsReader()) {
                out.put(id, JsonParser.parseReader(r));
            } catch (Exception ex) {
                Blocmpact.LOGGER.error("[Blocmpact] could not read effect {}: {}", e.getKey(), ex.toString());
            }
        }
        return out;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> jsons, ResourceManager manager, ProfilerFiller profiler) {
        FailureLog.clear();
        Map<Identifier, DataEffectDef> defs = new HashMap<>();
        jsons.forEach((id, json) -> {
            try {
                if (!(json instanceof JsonObject o)) throw new Specs.DataException("the root must be an object");
                defs.put(id, DataEffectDef.parse(id, o));
            } catch (RuntimeException ex) {
                FailureLog.report("data effect " + id + " is invalid: " + ex.getMessage(), ex.toString());
            }
        });
        definitions = Map.copyOf(defs);
        Blocmpact.LOGGER.info("[Blocmpact] loaded {} data-driven effects", defs.size());
        PostChain.INSTANCE.invalidate();
    }
}
