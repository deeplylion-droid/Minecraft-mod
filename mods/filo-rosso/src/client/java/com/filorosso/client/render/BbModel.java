package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Blockbench model (.bbmodel, "bedrock" or "free" format, per-face UV) turned into ModelParts,
 * with its animations played the way Blockbench plays them (linear, step and catmullrom keyframes,
 * loops wrapping like in the editor). So what you see in Blockbench is what you get in the game,
 * and a model saved from Blockbench can simply replace the file.
 *
 * Blockbench is Y up with the model's front towards -Z; ModelParts are Y down and mirrored in X:
 * positions become (-x, -y, z) and rotations (-x, -y, z), like Blockbench's own Java export.
 */
public final class BbModel {
    public final ModelPart root;
    /** Every bone (Blockbench group) by name. */
    public final Map<String, ModelPart> bones;
    public final Map<String, Animation> animations;

    private BbModel(ModelPart root, Map<String, ModelPart> bones, Map<String, Animation> animations) {
        this.root = root;
        this.bones = bones;
        this.animations = animations;
    }

    public ModelPart bone(String name) {
        ModelPart p = this.bones.get(name);
        if (p == null) {
            throw new IllegalStateException("Missing bone " + name);
        }
        return p;
    }

    public Animation animation(String name) {
        Animation a = this.animations.get(name);
        if (a == null) {
            throw new IllegalStateException("Missing animation " + name);
        }
        return a;
    }

    // ------------------------------------------------------------------ loading

    public static BbModel load(ResourceManager manager, Identifier file, String... skipBones) {
        Optional<Resource> resource = manager.getResource(file);
        if (resource.isEmpty()) {
            throw new IllegalStateException("Missing model " + file);
        }
        try (Reader reader = resource.get().openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject(), skipBones);
        } catch (Exception e) {
            FiloRosso.LOGGER.error("Could not load model {}", file, e);
            throw new IllegalStateException("Could not load model " + file, e);
        }
    }

    private record Element(float[] from, float[] to, float[] origin, float[] rotation, float inflate, Map<String, float[]> faces) {
    }

    private static float[] vec(JsonObject o, String key, float... fallback) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            return fallback;
        }
        JsonArray a = o.getAsJsonArray(key);
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = number(a.get(i));
        }
        return out;
    }

    /** Numbers can be saved as strings (or simple Molang): anything that is not a number counts as 0. */
    private static float number(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return 0.0F;
        }
        try {
            return e.getAsFloat();
        } catch (RuntimeException ex) {
            try {
                return Float.parseFloat(e.getAsString().trim());
            } catch (RuntimeException ex2) {
                return 0.0F;
            }
        }
    }

    public static BbModel parse(JsonObject json, String... skipBones) {
        JsonObject res = json.getAsJsonObject("resolution");
        float texW = res != null ? number(res.get("width")) : 16.0F;
        float texH = res != null ? number(res.get("height")) : 16.0F;
        Map<String, Element> elements = new HashMap<>();
        for (JsonElement el : json.getAsJsonArray("elements")) {
            JsonObject e = el.getAsJsonObject();
            if (e.has("type") && !"cube".equals(e.get("type").getAsString())) {
                continue;
            }
            if (e.has("visibility") && !e.get("visibility").getAsBoolean()) {
                continue;
            }
            Map<String, float[]> faces = new LinkedHashMap<>();
            JsonObject f = e.getAsJsonObject("faces");
            if (f != null) {
                for (String dir : new String[]{"north", "east", "south", "west", "up", "down"}) {
                    if (!f.has(dir)) {
                        continue;
                    }
                    JsonObject face = f.getAsJsonObject(dir);
                    if (!face.has("texture") || face.get("texture").isJsonNull()) {
                        continue;
                    }
                    float[] uv = vec(face, "uv", 0, 0, 0, 0);
                    if (uv.length == 4) {
                        faces.put(dir, uv);
                    }
                }
            }
            elements.put(e.get("uuid").getAsString(), new Element(vec(e, "from", 0, 0, 0), vec(e, "to", 0, 0, 0), vec(e, "origin", 0, 0, 0),
                    vec(e, "rotation", 0, 0, 0), e.has("inflate") ? number(e.get("inflate")) : 0.0F, faces));
        }
        Map<String, ModelPart> bones = new LinkedHashMap<>();
        Map<String, String> uuidToBone = new HashMap<>();
        Map<String, ModelPart> top = new LinkedHashMap<>();
        List<String> skip = List.of(skipBones);
        for (JsonElement n : json.getAsJsonArray("outliner")) {
            if (n.isJsonObject()) {
                JsonObject g = n.getAsJsonObject();
                if (skip.contains(g.get("name").getAsString())) {
                    continue;
                }
                top.put(g.get("name").getAsString(), group(g, null, elements, bones, uuidToBone, texW, texH, skip));
            }
        }
        ModelPart root = new ModelPart(List.of(), top);
        // before format 5.0 keyframes were saved with X (and Y for rotations) inverted; Blockbench flips them on load
        JsonObject meta = json.getAsJsonObject("meta");
        boolean legacy = meta != null && meta.has("format_version") && olderThan(meta.get("format_version").getAsString(), 5, 0);
        Map<String, Animation> animations = new HashMap<>();
        if (json.has("animations")) {
            for (JsonElement a : json.getAsJsonArray("animations")) {
                Animation anim = Animation.parse(a.getAsJsonObject(), uuidToBone, bones, legacy);
                animations.put(anim.name, anim);
            }
        }
        return new BbModel(root, bones, animations);
    }

    /** Is a version like "4.10" older than major.minor? */
    private static boolean olderThan(String version, int major, int minor) {
        String[] p = version.trim().split("\\.");
        try {
            int a = Integer.parseInt(p[0]);
            int b = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            return a < major || a == major && b < minor;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static ModelPart group(JsonObject g, float[] parentOrigin, Map<String, Element> elements, Map<String, ModelPart> bones,
                                   Map<String, String> uuidToBone, float texW, float texH, List<String> skip) {
        String name = g.get("name").getAsString();
        float[] origin = vec(g, "origin", 0, 0, 0);
        float[] rot = vec(g, "rotation", 0, 0, 0);
        List<ModelPart.Cube> cubes = new ArrayList<>();
        Map<String, ModelPart> children = new LinkedHashMap<>();
        int n = 0;
        for (JsonElement ch : g.getAsJsonArray("children")) {
            if (ch.isJsonPrimitive()) {
                Element e = elements.get(ch.getAsString());
                if (e == null) {
                    continue;
                }
                boolean rotated = e.rotation[0] != 0.0F || e.rotation[1] != 0.0F || e.rotation[2] != 0.0F;
                if (!rotated) {
                    ModelPart.Cube c = cube(e, origin, texW, texH);
                    if (c != null) {
                        cubes.add(c);
                    }
                } else {
                    // a rotated cube gets its own part, pivoting round the cube's origin (like Blockbench's export)
                    ModelPart.Cube c = cube(e, e.origin, texW, texH);
                    if (c != null) {
                        ModelPart part = new ModelPart(List.of(c), Map.of());
                        part.setInitialPose(PartPose.offsetAndRotation(-(e.origin[0] - origin[0]), -(e.origin[1] - origin[1]), e.origin[2] - origin[2],
                                -e.rotation[0] * Mth.DEG_TO_RAD, -e.rotation[1] * Mth.DEG_TO_RAD, e.rotation[2] * Mth.DEG_TO_RAD));
                        part.resetPose();
                        children.put(name + "#cube" + n++, part);
                    }
                }
            } else if (ch.isJsonObject()) {
                JsonObject sub = ch.getAsJsonObject();
                if (skip.contains(sub.get("name").getAsString())) {
                    continue;
                }
                children.put(sub.get("name").getAsString(), group(sub, origin, elements, bones, uuidToBone, texW, texH, skip));
            }
        }
        ModelPart part = new ModelPart(cubes, children);
        float ox = origin[0] - (parentOrigin != null ? parentOrigin[0] : 0.0F);
        float oy = origin[1] - (parentOrigin != null ? parentOrigin[1] : 0.0F);
        float oz = origin[2] - (parentOrigin != null ? parentOrigin[2] : 0.0F);
        part.setInitialPose(PartPose.offsetAndRotation(-ox, parentOrigin != null ? -oy : 24.0F - oy, oz,
                -rot[0] * Mth.DEG_TO_RAD, -rot[1] * Mth.DEG_TO_RAD, rot[2] * Mth.DEG_TO_RAD));
        part.resetPose();
        bones.put(name, part);
        if (g.has("uuid")) {
            uuidToBone.put(g.get("uuid").getAsString(), name);
        }
        return part;
    }

    /**
     * One cube, its faces with their own UV rectangles, in the space of a part whose pivot is at
     * {@code pivot} (Blockbench coordinates).
     */
    private static ModelPart.Cube cube(Element e, float[] pivot, float texW, float texH) {
        if (e.faces.isEmpty()) {
            return null;
        }
        float g = e.inflate;
        float x0 = e.from[0] - g, y0 = e.from[1] - g, z0 = e.from[2] - g;
        float x1 = e.to[0] + g, y1 = e.to[1] + g, z1 = e.to[2] + g;
        // a box-UV cube with as many faces as we need; its polygons are replaced below
        EnumSet<Direction> dirs = EnumSet.noneOf(Direction.class);
        Direction[] all = Direction.values();
        for (int i = 0; i < e.faces.size(); i++) {
            dirs.add(all[i]);
        }
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, pivot[0] - x1, pivot[1] - y1, z0 - pivot[2], x1 - x0, y1 - y0, z1 - z0, 0, 0, 0, false,
                texW, texH, dirs);
        int i = 0;
        for (Map.Entry<String, float[]> face : e.faces.entrySet()) {
            float[][] c = corners(face.getKey(), x0, y0, z0, x1, y1, z1);
            float[] uv = face.getValue();
            float u1 = uv[0] / texW, v1 = uv[1] / texH, u2 = uv[2] / texW, v2 = uv[3] / texH;
            // Blockbench's corner order v0..v3 has uv (u1,v1) (u2,v1) (u1,v2) (u2,v2); v0 v2 v3 v1 goes round the face
            ModelPart.Vertex[] verts = {
                    vertex(c[0], pivot, u1, v1), vertex(c[2], pivot, u1, v2), vertex(c[3], pivot, u2, v2), vertex(c[1], pivot, u2, v1)};
            float[] nrm = normal(face.getKey());
            cube.polygons[i++] = new ModelPart.Polygon(verts, new Vector3f(-nrm[0], -nrm[1], nrm[2]));
        }
        return cube;
    }

    /** A cube of side {@code size} centred on its part, every face showing the same small square of the texture. */
    public static ModelPart.Cube solidCube(float size, float u, float v, float texW, float texH) {
        float h = size / 2.0F;
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, -h, -h, -h, size, size, size, 0, 0, 0, false, texW, texH, EnumSet.allOf(Direction.class));
        float[] pivot = {0, 0, 0};
        int i = 0;
        for (String face : new String[]{"north", "east", "south", "west", "up", "down"}) {
            float[][] c = corners(face, -h, -h, -h, h, h, h);
            float u1 = u / texW, v1 = v / texH, u2 = (u + 0.5F) / texW, v2 = (v + 0.5F) / texH;
            ModelPart.Vertex[] verts = {
                    vertex(c[0], pivot, u1, v1), vertex(c[2], pivot, u1, v2), vertex(c[3], pivot, u2, v2), vertex(c[1], pivot, u2, v1)};
            float[] nrm = normal(face);
            cube.polygons[i++] = new ModelPart.Polygon(verts, new Vector3f(-nrm[0], -nrm[1], nrm[2]));
        }
        return cube;
    }

    private static ModelPart.Vertex vertex(float[] p, float[] pivot, float u, float v) {
        return new ModelPart.Vertex(pivot[0] - p[0], pivot[1] - p[1], p[2] - pivot[2], u, v);
    }

    /** The corners of a face in Blockbench's order (see its setShape / updateUV). */
    private static float[][] corners(String face, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (face) {
            case "east" -> new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z1}, {x1, y0, z0}};
            case "west" -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z0}, {x0, y0, z1}};
            case "up" -> new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x0, y1, z1}, {x1, y1, z1}};
            case "down" -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x0, y0, z0}, {x1, y0, z0}};
            case "south" -> new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x0, y0, z1}, {x1, y0, z1}};
            default -> new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x1, y0, z0}, {x0, y0, z0}};
        };
    }

    private static float[] normal(String face) {
        return switch (face) {
            case "east" -> new float[]{1, 0, 0};
            case "west" -> new float[]{-1, 0, 0};
            case "up" -> new float[]{0, 1, 0};
            case "down" -> new float[]{0, -1, 0};
            case "south" -> new float[]{0, 0, 1};
            default -> new float[]{0, 0, -1};
        };
    }

    // ------------------------------------------------------------------ animations

    public static final class Animation {
        public final String name;
        public final boolean loop;
        public final float length;
        final List<Track> tracks = new ArrayList<>();

        private Animation(String name, boolean loop, float length) {
            this.name = name;
            this.loop = loop;
            this.length = length;
        }

        static Animation parse(JsonObject a, Map<String, String> uuidToBone, Map<String, ModelPart> bones, boolean legacy) {
            String loopMode = a.has("loop") ? a.get("loop").getAsString() : "once";
            Animation anim = new Animation(a.get("name").getAsString(), "loop".equals(loopMode), number(a.get("length")));
            JsonObject animators = a.getAsJsonObject("animators");
            if (animators == null) {
                return anim;
            }
            for (Map.Entry<String, JsonElement> en : animators.entrySet()) {
                JsonObject an = en.getValue().getAsJsonObject();
                String bone = uuidToBone.getOrDefault(en.getKey(), an.has("name") ? an.get("name").getAsString() : "");
                ModelPart part = bones.get(bone);
                if (part == null || !an.has("keyframes")) {
                    continue;
                }
                Map<String, List<Key>> byChannel = new HashMap<>();
                for (JsonElement k : an.getAsJsonArray("keyframes")) {
                    JsonObject kf = k.getAsJsonObject();
                    JsonArray dp = kf.getAsJsonArray("data_points");
                    if (dp == null || dp.isEmpty()) {
                        continue;
                    }
                    JsonObject p = dp.get(0).getAsJsonObject();
                    String interp = kf.has("interpolation") ? kf.get("interpolation").getAsString() : "linear";
                    String channel = kf.get("channel").getAsString();
                    float x = number(p.get("x")), y = number(p.get("y")), z = number(p.get("z"));
                    if (legacy && ("rotation".equals(channel) || "position".equals(channel))) {
                        x = -x;
                        if ("rotation".equals(channel)) {
                            y = -y;
                        }
                    }
                    byChannel.computeIfAbsent(channel, c -> new ArrayList<>()).add(new Key(number(kf.get("time")), x, y, z, interp));
                }
                for (Map.Entry<String, List<Key>> ch : byChannel.entrySet()) {
                    int channel = switch (ch.getKey()) {
                        case "rotation" -> 0;
                        case "position" -> 1;
                        case "scale" -> 2;
                        default -> -1;
                    };
                    if (channel < 0) {
                        continue;
                    }
                    List<Key> keys = ch.getValue();
                    keys.sort((x, y) -> Float.compare(x.time, y.time));
                    anim.tracks.add(new Track(part, channel, keys.toArray(new Key[0])));
                }
            }
            return anim;
        }

        /**
         * Adds this animation at {@code time} seconds, times {@code weight}, to the parts (call after
         * they were reset). Rotation in degrees and position in px, both converted from Blockbench.
         */
        public void apply(float time, float weight) {
            if (weight <= 0.0F) {
                return;
            }
            float t = this.loop && this.length > 0.0F ? time - Mth.floor(time / this.length) * this.length : Math.min(time, this.length);
            float[] out = new float[3];
            for (Track tr : this.tracks) {
                tr.sample(t, this.loop, out);
                ModelPart p = tr.part;
                switch (tr.channel) {
                    case 0 -> {
                        p.xRot -= out[0] * Mth.DEG_TO_RAD * weight;
                        p.yRot -= out[1] * Mth.DEG_TO_RAD * weight;
                        p.zRot += out[2] * Mth.DEG_TO_RAD * weight;
                    }
                    case 1 -> {
                        p.x -= out[0] * weight;
                        p.y -= out[1] * weight;
                        p.z += out[2] * weight;
                    }
                    default -> {
                        p.xScale *= Mth.lerp(weight, 1.0F, out[0]);
                        p.yScale *= Mth.lerp(weight, 1.0F, out[1]);
                        p.zScale *= Mth.lerp(weight, 1.0F, out[2]);
                    }
                }
            }
        }
    }

    record Key(float time, float x, float y, float z, String interpolation) {
    }

    record Track(ModelPart part, int channel, Key[] keys) {
        /** Blockbench's BoneAnimator.interpolate + Keyframe.getLerp / getCatmullromLerp. */
        void sample(float time, boolean loop, float[] out) {
            Key[] k = this.keys;
            int before = -1, after = -1;
            for (int i = 0; i < k.length; i++) {
                if (k[i].time < time && (before < 0 || k[i].time > k[before].time)) {
                    before = i;
                }
                if (k[i].time >= time && (after < 0 || k[i].time < k[after].time)) {
                    after = i;
                }
            }
            float eps = 1.0F / 1200.0F;
            if (before >= 0 && Math.abs(k[before].time - time) < eps) {
                set(out, k[before]);
                return;
            }
            if (after >= 0 && Math.abs(k[after].time - time) < eps) {
                set(out, k[after]);
                return;
            }
            if (before < 0 || after < 0) {
                set(out, k[before < 0 ? after : before]);
                return;
            }
            Key b = k[before], a = k[after];
            float alpha = (time - b.time) / (a.time - b.time);
            if ("step".equals(b.interpolation)) {
                set(out, b);
                return;
            }
            if (!"catmullrom".equals(b.interpolation) && !"catmullrom".equals(a.interpolation)) {
                out[0] = Mth.lerp(alpha, b.x, a.x);
                out[1] = Mth.lerp(alpha, b.y, a.y);
                out[2] = Mth.lerp(alpha, b.z, a.z);
                return;
            }
            int bp = before - 1 >= 0 ? before - 1 : -1;
            int ap = before + 2 < k.length ? before + 2 : -1;
            if (loop && k.length >= 3) {
                if (bp < 0) {
                    bp = k.length - 2;
                }
                if (ap < 0) {
                    ap = 1;
                }
            }
            // THREE.SplineCurve over (before_plus, before, after, after_plus), at this segment's parameter
            Key[] pts = new Key[4];
            int n = 0;
            if (bp >= 0) {
                pts[n++] = k[bp];
            }
            pts[n++] = b;
            pts[n++] = a;
            if (ap >= 0) {
                pts[n++] = k[ap];
            }
            float u = (alpha + (bp >= 0 ? 1 : 0)) / (n - 1);
            float p = (n - 1) * u;
            int i = Mth.floor(p);
            float w = p - i;
            if (i > n - 2) {
                i = n - 2;
                w = 1.0F;
            }
            Key q0 = pts[i > 0 ? i - 1 : i], q1 = pts[i], q2 = pts[Math.min(i + 1, n - 1)], q3 = pts[Math.min(i + 2, n - 1)];
            out[0] = cr(w, q0.x, q1.x, q2.x, q3.x);
            out[1] = cr(w, q0.y, q1.y, q2.y, q3.y);
            out[2] = cr(w, q0.z, q1.z, q2.z, q3.z);
        }

        private static void set(float[] out, Key k) {
            out[0] = k.x;
            out[1] = k.y;
            out[2] = k.z;
        }

        private static float cr(float t, float p0, float p1, float p2, float p3) {
            float v0 = (p2 - p0) * 0.5F;
            float v1 = (p3 - p1) * 0.5F;
            float t2 = t * t, t3 = t2 * t;
            return (2 * p1 - 2 * p2 + v0 + v1) * t3 + (-3 * p1 + 3 * p2 - 2 * v0 - v1) * t2 + v0 * t + p1;
        }
    }
}
