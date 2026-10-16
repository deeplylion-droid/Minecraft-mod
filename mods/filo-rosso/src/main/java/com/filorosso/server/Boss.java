package com.filorosso.server;

import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import com.filorosso.entity.ModEntities;
import com.filorosso.entity.Spezzacuori;
import com.filorosso.item.ModItems;
import com.filorosso.sound.ModSounds;
import com.filorosso.util.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Calling Lo Spezzacuori, and the end of the fight. */
public final class Boss {
    private Boss() {
    }

    /** Health per couple online (at least two couples' worth). */
    public static final float HEALTH_PER_COUPLE = 160.0F;
    private static final Set<UUID> ALIVE = new HashSet<>();

    public static boolean fighting(MinecraftServer server) {
        ALIVE.removeIf(id -> {
            for (ServerLevel level : server.getAllLevels()) {
                var e = level.getEntity(id);
                if (e instanceof Spezzacuori s && s.isAlive()) {
                    return false;
                }
            }
            return true;
        });
        return !ALIVE.isEmpty();
    }

    public static int couplesOnline(MinecraftServer server) {
        int n = 0;
        for (Couple c : CoupleBook.get(server).couples) {
            if (Bonds.player(server, c.a) != null || Bonds.player(server, c.b) != null) {
                n++;
            }
        }
        return n;
    }

    public static boolean summon(MinecraftServer server, ServerLevel level, Vec3 near) {
        if (fighting(server)) {
            return false;
        }
        BlockPos at = BlockPos.containing(near);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        Spezzacuori boss = ModEntities.SPEZZACUORI.create(level, EntitySpawnReason.TRIGGERED);
        if (boss == null) {
            return false;
        }
        boss.setPos(at.getX() + 0.5, y, at.getZ() + 0.5);
        float health = HEALTH_PER_COUPLE * Math.max(2, couplesOnline(server));
        var max = boss.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(health);
        }
        boss.setHealth(health);
        boss.rise();
        level.addFreshEntity(boss);
        ALIVE.add(boss.getUUID());
        for (int i = 0; i < 3; i++) {
            LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setPos(boss.getX() + (i - 1) * 4, y, boss.getZ() + (i % 2) * 3);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        for (ServerPlayer p : level.players()) {
            Bonds.title(p, Component.translatable("filorosso.title.boss").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("filorosso.title.boss.sub").withStyle(ChatFormatting.GRAY), 10, 80, 20);
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
            Bonds.sound(p, ModSounds.BOSS_ROAR, 0.6F);
        }
        server.getPlayerList().broadcastSystemMessage(Component.empty().append(Text.icon(Text.BROKEN, 0xC02040)).append(Component.literal(" "))
                .append(Component.translatable("filorosso.msg.boss_arrived").withStyle(ChatFormatting.DARK_RED)), false);
        return true;
    }

    public static void onDeath(MinecraftServer server, Spezzacuori boss) {
        ALIVE.remove(boss.getUUID());
        CoupleBook book = CoupleBook.get(server);
        book.bossBeaten = true;
        for (Couple c : book.couples) {
            if (c.bossHits > 0) {
                c.points += c.bossHits >= 5 ? 5 : 2;
                c.bossHits = 0;
                Affinity.add(server, c, 200, false);
            }
        }
        book.setDirty();
        Scores.refresh(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Bonds.title(p, Component.translatable("filorosso.title.victory").withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.translatable("filorosso.title.victory.sub").withStyle(ChatFormatting.GOLD), 10, 120, 30);
            Bonds.sound(p, ModSounds.VICTORY, 1.0F);
            if (p.level() == boss.level() && p.distanceTo(boss) < 96) {
                ItemStack heart = new ItemStack(ModItems.GOLDEN_HEART);
                if (!p.getInventory().add(heart)) {
                    p.drop(heart, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
        }
        server.getPlayerList().broadcastSystemMessage(Component.empty().append(Text.icon(Text.HEART, 0xFF5090)).append(Component.literal(" "))
                .append(Component.translatable("filorosso.msg.boss_beaten").withStyle(ChatFormatting.LIGHT_PURPLE)), false);
        for (Couple c : book.couples) {
            Challenges.sendInfo(server, c);
        }
    }
}
