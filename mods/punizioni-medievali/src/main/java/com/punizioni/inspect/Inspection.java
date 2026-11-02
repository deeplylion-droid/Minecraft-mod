package com.punizioni.inspect;

import com.punizioni.Punizioni;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/** Opening the lens' views: a subject's inventory and misdeeds, or their Ender chest. */
public final class Inspection {
    private Inspection() {
    }

    /** The view closes if the subject gets further than this (blocks). */
    public static final double KEEP_DISTANCE = 14.0;

    public static final MenuType<InspectMenu> MENU = Registry.register(BuiltInRegistries.MENU, Punizioni.id("inspect"),
            new MenuType<>(InspectMenu::client, FeatureFlags.VANILLA_SET));

    public static void init() {
    }

    public static void open(ServerPlayer king, ServerPlayer subject, boolean enderChest) {
        if (subject == king || subject.isSpectator()) {
            return;
        }
        if (enderChest) {
            king.openMenu(new SimpleMenuProvider((id, inventory, player) -> ChestMenu.threeRows(id, inventory, new EnderView(king, subject)),
                    Component.translatable("punizioni.inspect.ender", subject.getName())));
        } else {
            king.openMenu(new SimpleMenuProvider((id, inventory, player) -> new InspectMenu(id, inventory, new InspectContainer(king, subject)),
                    Component.translatable("punizioni.inspect.title", subject.getName()).withStyle(ChatFormatting.DARK_RED)));
        }
        king.level().playSound(null, king.getX(), king.getY(), king.getZ(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
    }
}
