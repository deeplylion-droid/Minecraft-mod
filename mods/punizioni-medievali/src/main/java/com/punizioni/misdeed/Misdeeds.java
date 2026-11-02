package com.punizioni.misdeed;

import com.punizioni.ModDamage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What every player did lately, for the king's lens: containers opened and what was taken from them
 * (from someone else's: theft), blocks broken, who they hit and killed, fire, lava and TNT. Kept in
 * memory for an hour (a server restart forgets it). Theft, hits and kills make a subject "guilty"
 * for ten minutes: through the lens they glow red.
 */
public final class Misdeeds {
    private Misdeeds() {
    }

    /** How long a guilty deed keeps the subject glowing (10 minutes). */
    public static final long GUILTY_TICKS = 20L * 60L * 10L;
    /** How long anything is remembered (1 hour). */
    public static final long KEEP_TICKS = 20L * 60L * 60L;
    /** Repeats of the same thing within this time add up (3 minutes). */
    public static final long SAME_TICKS = 20L * 60L * 3L;
    public static final int MAX_PER_PLAYER = 60;

    private static final Map<UUID, List<Misdeed>> LOG = new HashMap<>();

    /** The block each player right-clicked last (a menu opened in the same tick came from it). */
    private record LastUse(BlockPos pos, BlockState state, long time) {
    }

    private static final Map<UUID, LastUse> LAST_USE = new HashMap<>();

    /** A container menu open now, what it held when it opened, and whose it is. */
    private record Snapshot(AbstractContainerMenu menu, Map<Item, Integer> counts, BlockPos pos, BlockState state, Owners.@Nullable Owner owner) {
    }

    private static final Map<UUID, Snapshot> OPEN = new HashMap<>();

    public static void init() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer p) {
                BlockPos pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                LAST_USE.put(p.getUUID(), new LastUse(pos.immutable(), state, server.getGameTime()));
                ItemStack held = player.getItemInHand(hand);
                if (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) {
                    if (state.is(Blocks.TNT)) {
                        add(p, Misdeed.Kind.TNT, "tnt", Component.translatable(Blocks.TNT.getDescriptionId()), null, Items.TNT, pos, 1);
                    } else if (!harmlessFire(state)) {
                        add(p, Misdeed.Kind.FIRE, "fire", Component.translatable(Items.FLINT_AND_STEEL.getDescriptionId()), null, Items.FLINT_AND_STEEL,
                                pos, 1);
                    }
                } else if (held.is(Items.LAVA_BUCKET)) {
                    add(p, Misdeed.Kind.LAVA, "lava", Component.translatable(Items.LAVA_BUCKET.getDescriptionId()), null, Items.LAVA_BUCKET, pos, 1);
                }
            }
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer p)) {
                return;
            }
            Owners owners = Owners.of(server);
            Owners.Owner owner = owners.get(pos);
            Item item = state.getBlock().asItem();
            if (item == Items.AIR) {
                item = Items.PAPER;
            }
            if (owner != null) {
                owners.remove(pos);
                if (!owner.id().equals(p.getUUID())) {
                    add(p, Misdeed.Kind.BROKE_OWNED, "broke_owned:" + pos.asLong(), state.getBlock().getName(), Component.literal(owner.name()), item, pos, 1);
                    return;
                }
            }
            add(p, Misdeed.Kind.BROKE, "broke:" + BuiltInRegistries.BLOCK.getKey(state.getBlock()), state.getBlock().getName(), null, item, pos, 1);
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseTaken, taken, blocked) -> {
            if (source.getEntity() instanceof ServerPlayer p && entity != p && !source.is(ModDamage.PUNISHMENT) && protectedVictim(entity, p)) {
                add(p, Misdeed.Kind.HIT, "hit:" + entity.getUUID(), entity.getName(), null, Items.IRON_SWORD, entity.blockPosition(), 1);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer p) || entity == p) {
                return;
            }
            if (protectedVictim(entity, p)) {
                add(p, Misdeed.Kind.KILL, "kill:" + entity.getUUID(), entity.getName(), null,
                        entity instanceof Player ? Items.SKELETON_SKULL : Items.BONE, entity.blockPosition(), 1);
            } else if (entity instanceof Animal) {
                add(p, Misdeed.Kind.KILL_ANIMAL, "kill_animal:" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), entity.getType().getDescription(),
                        null, Items.BONE, entity.blockPosition(), 1);
            }
        });
    }

    /** Players, villagers, golems, named creatures and other people's pets: hurting them is a misdeed. */
    private static boolean protectedVictim(LivingEntity entity, ServerPlayer attacker) {
        if (entity instanceof Player || entity instanceof AbstractVillager || entity instanceof IronGolem) {
            return true;
        }
        if (entity instanceof OwnableEntity pet && pet.getOwnerReference() != null) {
            return !pet.getOwnerReference().getUUID().equals(attacker.getUUID());
        }
        return entity instanceof Animal && entity.hasCustomName();
    }

    /** Lighting a portal, a fireplace, a campfire or a candle is not arson. */
    private static boolean harmlessFire(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.NETHERRACK) || state.is(BlockTags.CAMPFIRES)
                || state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
    }

    // ------------------------------------------------------------------ containers

    /** A placed block that holds items gets its owner (BlockItemMixin). */
    public static void placed(ServerLevel level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof Container) {
            Owners.of(level).put(pos, player.getUUID(), player.getGameProfile().name());
        }
    }

    /** A menu opened (ServerPlayerMixin): if it came from a container block, remember what it held. */
    public static void opened(ServerPlayer player, AbstractContainerMenu menu) {
        OPEN.remove(player.getUUID());
        LastUse use = LAST_USE.get(player.getUUID());
        ServerLevel level = player.level();
        if (use == null || use.time != level.getGameTime() || !(level.getBlockEntity(use.pos) instanceof Container)) {
            return;
        }
        Owners owners = Owners.of(level);
        Owners.Owner owner = owners.get(use.pos);
        if (owner == null && use.state.getBlock() instanceof ChestBlock && use.state.hasProperty(ChestBlock.TYPE)
                && use.state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            owner = owners.get(use.pos.relative(ChestBlock.getConnectedDirection(use.state)));
        }
        OPEN.put(player.getUUID(), new Snapshot(menu, count(player, menu), use.pos, use.state, owner));
    }

    /** The menu closes (ServerPlayerMixin): what is missing was taken. */
    public static void closing(ServerPlayer player, AbstractContainerMenu menu) {
        Snapshot s = OPEN.get(player.getUUID());
        if (s == null || s.menu != menu) {
            return;
        }
        OPEN.remove(player.getUUID());
        if (s.owner != null && s.owner.id().equals(player.getUUID())) {
            return;
        }
        Map<Item, Integer> now = count(player, menu);
        Component where = s.state.getBlock().getName();
        Component whose = s.owner != null ? Component.literal(s.owner.name()) : null;
        boolean took = false;
        for (Map.Entry<Item, Integer> e : s.counts.entrySet()) {
            int missing = e.getValue() - now.getOrDefault(e.getKey(), 0);
            if (missing <= 0) {
                continue;
            }
            took = true;
            Item item = e.getKey();
            String key = BuiltInRegistries.ITEM.getKey(item) + "@" + s.pos.asLong();
            if (s.owner != null) {
                add(player, Misdeed.Kind.THEFT, "theft:" + key, Component.translatable(item.getDescriptionId()), whose, item, s.pos, missing);
            } else {
                add(player, Misdeed.Kind.TOOK, "took:" + key, Component.translatable(item.getDescriptionId()), where, item, s.pos, missing);
            }
        }
        if (!took) {
            Item icon = s.state.getBlock().asItem() == Items.AIR ? Items.CHEST : s.state.getBlock().asItem();
            if (s.owner != null) {
                add(player, Misdeed.Kind.OPENED_OWNED, "opened:" + s.pos.asLong(), where, whose, icon, s.pos, 1);
            } else {
                add(player, Misdeed.Kind.OPENED, "opened:" + s.pos.asLong(), where, null, icon, s.pos, 1);
            }
        }
    }

    private static Map<Item, Integer> count(Player player, AbstractContainerMenu menu) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory() || !slot.hasItem()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    // ------------------------------------------------------------------ the log

    public static void add(ServerPlayer player, Misdeed.Kind kind, String key, Component what, @Nullable Component whose, Item icon, BlockPos pos,
                           int amount) {
        long now = player.level().getGameTime();
        List<Misdeed> list = LOG.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        for (Misdeed m : list) {
            if (m.key.equals(key) && now - m.time < SAME_TICKS) {
                m.count += amount;
                m.time = now;
                list.remove(m);
                list.add(0, m);
                return;
            }
        }
        list.add(0, new Misdeed(kind, key, what, whose, icon, pos.immutable(), player.level().dimension(), now, amount));
        while (list.size() > MAX_PER_PLAYER) {
            list.remove(list.size() - 1);
        }
    }

    /** The subject's misdeeds: suspicious and guilty ones first, then the plain facts; newest first. */
    public static List<Misdeed> of(UUID player, long now) {
        List<Misdeed> list = LOG.get(player);
        if (list == null) {
            return List.of();
        }
        for (Iterator<Misdeed> it = list.iterator(); it.hasNext(); ) {
            if (now - it.next().time > KEEP_TICKS) {
                it.remove();
            }
        }
        List<Misdeed> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingInt((Misdeed m) -> m.kind.severity > 0 ? 0 : 1).thenComparingLong(m -> -m.time));
        return sorted;
    }

    /** Whether the subject did something guilty in the last ten minutes. */
    public static boolean guilty(UUID player, long now) {
        List<Misdeed> list = LOG.get(player);
        if (list == null) {
            return false;
        }
        for (Misdeed m : list) {
            if (m.kind.severity >= 2 && now - m.time < GUILTY_TICKS) {
                return true;
            }
        }
        return false;
    }

    /** The king pardons: everything the subject did is forgotten. */
    public static void pardon(UUID player) {
        LOG.remove(player);
    }

    public static void forget(Entity player) {
        LAST_USE.remove(player.getUUID());
        OPEN.remove(player.getUUID());
    }
}
