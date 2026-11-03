package nel.riposte;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class FinisherLoader extends SimplePreparableReloadListener<List<FinisherDefinition>> {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final List<FinisherDefinition> REGISTRY = new ArrayList<>();
    private static final Random RANDOM = new Random();

    @Override
    protected List<FinisherDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<FinisherDefinition> definitions = new ArrayList<>();

        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources("finishers", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = new InputStreamReader(entry.getValue().open())) {
                FinisherDefinition def = GSON.fromJson(reader, FinisherDefinition.class);
                if (def != null) definitions.add(def);
            } catch (Exception e) {
                Riposte.LOGGER.error("Failed to load finisher logic JSON: " + entry.getKey(), e);
            }
        }
        return definitions;
    }

    @Override
    protected void apply(List<FinisherDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        REGISTRY.clear();
        REGISTRY.addAll(prepared);
        Riposte.LOGGER.info("Successfully loaded " + REGISTRY.size() + " data-driven finishers!");
    }

    public static String evaluateSize(LivingEntity entity) {
        float height = entity.getBbHeight();
        float width = entity.getBbWidth();

        if (height < 0.9f || (width > 1.2f && height < 1.2f)) return "SMALL";
        if (height > 2.3f || width > 1.4f) return "LARGE";
        return "MEDIUM";
    }

    public static FinisherDefinition getValidFinisher(LivingEntity target, boolean hasWeapon) {
        String targetSize = evaluateSize(target);
        boolean isGrounded = target.onGround();

        List<FinisherDefinition> valid = REGISTRY.stream()
                .filter(def -> def.target_size != null && (def.target_size.equalsIgnoreCase(targetSize) || def.target_size.equalsIgnoreCase("ALL")))
                .filter(def -> !def.requires_grounded || isGrounded)
                .filter(def -> def.weapon_requirement == null ||
                        def.weapon_requirement.equalsIgnoreCase("ANY") ||
                        (def.weapon_requirement.equalsIgnoreCase("WEAPON") && hasWeapon) ||
                        (def.weapon_requirement.equalsIgnoreCase("FIST") && !hasWeapon))
                .toList();

        if (valid.isEmpty()) return null;
        return valid.get(RANDOM.nextInt(valid.size()));
    }

    public static FinisherDefinition getFinisherById(String id) {
        return REGISTRY.stream().filter(def -> def.id.equals(id)).findFirst().orElse(null);
    }
}
