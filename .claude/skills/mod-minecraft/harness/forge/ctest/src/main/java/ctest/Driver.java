package ctest;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Test driver for Minecraft 1.20.1 + Forge clients (test only, never shipped). It logs what the client
 * sees, with lines containing "[ctest]" (screens and their buttons, chat, action bar, status), and runs
 * the requests a driver script writes into ftest_cmd.txt in the game directory (run/), one per line:
 *
 *   cmd <command without />          run a command as this player
 *   look <yaw> <pitch>               turn the camera
 *   face <player>                    look at a player
 *   faceat <x> <y> <z>               look at a point
 *   facetype <text>                  look at the nearest entity whose type contains the text
 *   orbit <player> <angle> <dist> <height>
 *                                    teleport (needs op) to dist blocks from the player, angle 0 =
 *                                    right behind them, and look at them
 *   cam fp|back|front                camera mode
 *   shot <name>                      screenshot (run/screenshots/<name>.png)
 *   burst <name> <count> <every>     screenshots every <every> ticks, exactly
 *   hud on|off                       F1
 *   screen                           describe the open screen and where its buttons are
 *   close                            close the open screen
 *   status                           one "st" line: position, speed, health, effects, players
 *   ent <text>                       position, health (living ones) and size of every entity whose type contains the text
 *   track <ticks>                    log height and vertical speed every tick for a while
 *   particles                        how many particles there are
 *
 * Commands of the mod being tested go in an optional class ctest.Extra, with a method
 * "public static boolean run(Minecraft mc, String[] words)" (true = handled); the mod's classes are on
 * the classpath when the driver is built with -PmodJar=...
 */
public class Driver {
    private static final Path CMD = Path.of("ftest_cmd.txt");
    private String lastScreen = "";
    private int ticks;
    private int track;
    private final Map<Integer, String> planned = new HashMap<>();
    private Method extra;
    private boolean extraChecked;

    public static void log(String message) {
        System.out.println("[ctest] " + message);
    }

    static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> log("screenshot " + name));
    }

    public static Player find(Minecraft mc, String name) {
        for (Player p : mc.level.players()) {
            if (p.getGameProfile().getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    public static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    public static float[] angles(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        float yaw = (float) (Math.atan2(-d.x, d.z) * 180.0 / Math.PI);
        float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0 / Math.PI);
        return new float[]{yaw, pitch};
    }

    private void describe(Minecraft mc, Screen screen) {
        StringBuilder sb = new StringBuilder("screen " + screen.getClass().getSimpleName() + " gui " + screen.width + "x" + screen.height
                + " scale " + mc.getWindow().getGuiScale());
        for (GuiEventListener l : screen.children()) {
            if (l instanceof AbstractWidget w) {
                sb.append(String.format(Locale.ROOT, " | button '%s' %d %d %d %d", w.getMessage().getString(), w.getX(), w.getY(), w.getWidth(), w.getHeight()));
            }
        }
        log(sb.toString());
    }

    private boolean extra(Minecraft mc, String[] w) throws Exception {
        if (!this.extraChecked) {
            this.extraChecked = true;
            try {
                this.extra = Class.forName("ctest.Extra").getMethod("run", Minecraft.class, String[].class);
            } catch (ClassNotFoundException e) {
                this.extra = null;
            }
        }
        return this.extra != null && (Boolean) this.extra.invoke(null, mc, w);
    }

    private void run(Minecraft mc, String line) {
        line = line.trim();
        if (line.isEmpty()) {
            return;
        }
        log("do: " + line);
        String[] w = line.split(" ");
        try {
            switch (w[0]) {
                case "cmd" -> mc.player.connection.sendCommand(line.substring(4));
                case "look" -> look(mc, Float.parseFloat(w[1]), Float.parseFloat(w[2]));
                case "face" -> {
                    Player p = find(mc, w[1]);
                    if (p != null) {
                        float[] a = angles(mc.player.getEyePosition(), p.position().add(0, p.getBbHeight() * 0.6, 0));
                        look(mc, a[0], a[1]);
                    } else {
                        log("cannot see " + w[1]);
                    }
                }
                case "faceat" -> {
                    float[] a = angles(mc.player.getEyePosition(), new Vec3(Double.parseDouble(w[1]), Double.parseDouble(w[2]), Double.parseDouble(w[3])));
                    look(mc, a[0], a[1]);
                }
                case "facetype" -> {
                    Entity best = null;
                    for (Entity e : mc.level.entitiesForRendering()) {
                        if (e.getType().toString().contains(w[1]) && (best == null || e.distanceTo(mc.player) < best.distanceTo(mc.player))) {
                            best = e;
                        }
                    }
                    if (best != null) {
                        float[] a = angles(mc.player.getEyePosition(), best.position().add(0, best.getBbHeight() * 0.5, 0));
                        look(mc, a[0], a[1]);
                        log("facing " + best.getType() + " at " + best.blockPosition());
                    } else {
                        log("no " + w[1]);
                    }
                }
                case "orbit" -> {
                    Player p = find(mc, w[1]);
                    if (p == null) {
                        log("cannot see " + w[1]);
                        break;
                    }
                    Vec3 c = p.position().add(0.0, 1.0, 0.0);
                    double angle = Math.toRadians(Double.parseDouble(w[2]));
                    Vec3 behind = Vec3.directionFromRotation(0.0F, p.yBodyRot).scale(-1.0);
                    Vec3 dir = new Vec3(behind.x * Math.cos(angle) - behind.z * Math.sin(angle), 0.0, behind.x * Math.sin(angle) + behind.z * Math.cos(angle));
                    Vec3 eye = c.add(dir.scale(Double.parseDouble(w[3]))).add(0.0, Double.parseDouble(w[4]), 0.0);
                    float[] a = angles(eye, c);
                    mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.2f %.2f",
                            eye.x, eye.y - mc.player.getEyeHeight(), eye.z, a[0], a[1]));
                }
                case "cam" -> mc.options.setCameraType(switch (w[1]) {
                    case "front" -> CameraType.THIRD_PERSON_FRONT;
                    case "back" -> CameraType.THIRD_PERSON_BACK;
                    default -> CameraType.FIRST_PERSON;
                });
                case "shot" -> shot(mc, w[1]);
                case "burst" -> {
                    int count = Integer.parseInt(w[2]);
                    int every = Integer.parseInt(w[3]);
                    for (int i = 0; i < count; i++) {
                        this.planned.put(this.ticks + 1 + i * every, w[1] + "_" + String.format(Locale.ROOT, "%02d", i + 1));
                    }
                }
                case "hud" -> mc.options.hideGui = w[1].equals("off");
                case "screen" -> {
                    if (mc.screen != null) {
                        this.describe(mc, mc.screen);
                    } else {
                        log("screen none");
                    }
                }
                case "close" -> mc.setScreen(null);
                case "status" -> this.status(mc);
                case "ent" -> {
                    for (Entity e : mc.level.entitiesForRendering()) {
                        if (e.getType().toString().contains(w[1])) {
                            String hp = e instanceof LivingEntity le ? String.format(Locale.ROOT, "%.2f", le.getHealth()) : "-";
                            log(String.format(Locale.ROOT, "ent %s id %d at %.2f %.2f %.2f dist %.2f hp %s alive %s size %.2f x %.2f", e.getType(), e.getId(),
                                    e.getX(), e.getY(), e.getZ(), e.distanceTo(mc.player), hp, e.isAlive(), e.getBbWidth(), e.getBbHeight()));
                        }
                    }
                }
                case "track" -> this.track = Integer.parseInt(w[1]);
                case "particles" -> log("particles " + mc.particleEngine.countParticles());
                default -> {
                    if (!this.extra(mc, w)) {
                        log("unknown: " + line);
                    }
                }
            }
        } catch (Exception e) {
            log("failed: " + line + " " + e);
        }
    }

    private void status(Minecraft mc) {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "st pos %.2f %.2f %.2f vel %.3f %.3f %.3f ground %s hp %.2f xp %d",
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().y,
                mc.player.getDeltaMovement().z, mc.player.onGround(), mc.player.getHealth(), mc.player.experienceLevel));
        mc.player.getActiveEffects().forEach(e -> sb.append(" effect ").append(e.getEffect().getDescriptionId()));
        for (Player p : mc.level.players()) {
            sb.append(String.format(Locale.ROOT, " | %s hp %.1f at %.2f %.2f %.2f", p.getGameProfile().getName(), p.getHealth(), p.getX(), p.getY(), p.getZ()));
        }
        log(sb.toString());
    }

    /** Chat and action bar: command results, death messages, the mod's own messages. */
    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        boolean overlay = event instanceof ClientChatReceivedEvent.System system && system.isOverlay();
        log((overlay ? "overlay " : "chat ") + event.getMessage().getString());
    }

    /** Mouse buttons as the game gets them (to check that xdotool's clicks arrive). */
    @SubscribeEvent
    public void onMouse(InputEvent.MouseButton.Pre event) {
        log("mouse button " + event.getButton() + " action " + event.getAction());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        String name = screen == null ? "none" : screen.getClass().getSimpleName();
        if (!name.equals(this.lastScreen)) {
            this.lastScreen = name;
            if (screen != null) {
                this.describe(mc, screen);
            } else {
                log("screen none");
            }
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        // the window of a headless client never has the focus
        mc.options.pauseOnLostFocus = false;
        this.ticks++;
        String plannedShot = this.planned.remove(this.ticks);
        if (plannedShot != null) {
            shot(mc, plannedShot);
        }
        if (this.track > 0) {
            this.track--;
            log(String.format(Locale.ROOT, "track y %.3f vy %.3f ground %s x %.3f z %.3f", mc.player.getY(), mc.player.getDeltaMovement().y,
                    mc.player.onGround(), mc.player.getX(), mc.player.getZ()));
        }
        if (Files.exists(CMD)) {
            try {
                List<String> lines = Files.readAllLines(CMD);
                Files.delete(CMD);
                for (String l : lines) {
                    this.run(mc, l);
                }
            } catch (Exception e) {
                log("cmd file: " + e);
            }
        }
    }
}
