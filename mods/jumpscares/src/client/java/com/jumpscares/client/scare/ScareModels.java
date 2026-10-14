package com.jumpscares.client.scare;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jumpscares.Jumpscares;
import com.jumpscares.scare.ScareKind;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the monsters from assets/jumpscares/scares/&lt;kind&gt;.json: parts (pivot, rotation, parent)
 * made of cubes with box UV, like vanilla entity models. Resource packs can replace them.
 */
public final class ScareModels implements SimpleSynchronousResourceReloadListener {
    public static final ScareModels INSTANCE = new ScareModels();
    private static final Map<ScareKind, ScareModel> MODELS = new EnumMap<>(ScareKind.class);

    private ScareModels() {
    }

    public static ScareModel get(ScareKind kind) {
        return MODELS.get(kind);
    }

    @Override
    public ResourceLocation getFabricId() {
        return Jumpscares.id("scare_models");
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        MODELS.clear();
        for (ScareKind kind : ScareKind.values()) {
            ResourceLocation file = Jumpscares.id("scares/" + kind.id + ".json");
            Optional<Resource> resource = manager.getResource(file);
            if (resource.isEmpty()) {
                Jumpscares.LOGGER.error("Missing jumpscare model {}", file);
                continue;
            }
            try (Reader reader = resource.get().openAsReader()) {
                MODELS.put(kind, bake(JsonParser.parseReader(reader).getAsJsonObject(), kind));
            } catch (Exception e) {
                Jumpscares.LOGGER.error("Could not load jumpscare model {}", file, e);
            }
        }
    }

    private static float[] floats(JsonObject o, String key) {
        JsonArray a = o.getAsJsonArray(key);
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    private static ScareModel bake(JsonObject json, ScareKind kind) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> defs = new HashMap<>();
        List<String[]> order = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("parts")) {
            JsonObject part = element.getAsJsonObject();
            String name = part.get("name").getAsString();
            String parent = part.get("parent").getAsString();
            float[] pivot = floats(part, "pivot");
            float[] rot = floats(part, "rotation");
            CubeListBuilder cubes = CubeListBuilder.create();
            for (JsonElement c : part.getAsJsonArray("cubes")) {
                JsonObject cube = c.getAsJsonObject();
                float[] uv = floats(cube, "uv");
                int density = cube.get("density").getAsInt();
                float[] from = floats(cube, "from");
                float[] size = floats(cube, "size");
                float inflate = cube.get("inflate").getAsFloat();
                // density = texels per model unit: the texture is read as if it were density times smaller
                cubes.texOffs((int) uv[0] / density, (int) uv[1] / density)
                        .addBox(from[0], from[1], from[2], size[0], size[1], size[2], new CubeDeformation(inflate),
                                1.0F / density, 1.0F / density);
            }
            PartDefinition parentDef = parent.isEmpty() ? root : defs.get(parent);
            if (parentDef == null) {
                throw new IllegalArgumentException("Part " + name + " comes before its parent " + parent);
            }
            defs.put(name, parentDef.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2],
                    rot[0] * Mth.DEG_TO_RAD, rot[1] * Mth.DEG_TO_RAD, rot[2] * Mth.DEG_TO_RAD)));
            order.add(new String[]{name, parent});
        }
        float[] size = floats(json, "texture_size");
        ModelPart baked = LayerDefinition.create(mesh, (int) size[0], (int) size[1]).bakeRoot();
        Map<String, ModelPart> parts = new HashMap<>();
        for (String[] np : order) {
            ModelPart parent = np[1].isEmpty() ? baked : parts.get(np[1]);
            parts.put(np[0], parent.getChild(np[0]));
        }
        return new ScareModel(baked, parts, Jumpscares.id("textures/scare/" + kind.id + ".png"),
                Jumpscares.id("textures/scare/" + kind.id + "_glow.png"), json.get("unit").getAsFloat());
    }
}
