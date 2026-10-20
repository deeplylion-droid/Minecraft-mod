package com.filorosso.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * On Windows some graphics drivers crash the game while it loads (no error, the window just closes).
 * The "Default" graphics API tries Vulkan first; the API being tried is written to a small file
 * that is removed once the game has started. If the game dies while loading, the next start knows
 * which API crashed and uses the other one, instead of vanilla's "always OpenGL after a crash".
 */
public final class GraphicsFallback {
    private GraphicsFallback() {
    }

    public static final String VULKAN = "vulkan";
    public static final String OPENGL = "opengl";

    private static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("filorosso-graphics.txt");
    }

    public static boolean enabled() {
        return Util.getPlatform() == Util.OS.WINDOWS;
    }

    /** The API the last start was trying when it died; null if it started fine. */
    public static String crashedWith() {
        try {
            Path f = file();
            return Files.exists(f) ? Files.readString(f).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static void trying(String api) {
        try {
            Files.writeString(file(), api);
        } catch (IOException ignored) {
        }
    }

    public static void started() {
        try {
            Files.deleteIfExists(file());
        } catch (IOException ignored) {
        }
    }
}
