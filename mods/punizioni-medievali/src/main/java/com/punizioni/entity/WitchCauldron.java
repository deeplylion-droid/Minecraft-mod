package com.punizioni.entity;

import com.punizioni.ModSounds;
import com.punizioni.item.ModItems;
import com.punizioni.witch.CurseKind;
import com.punizioni.witch.Witches;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The witch's giant cauldron: a pot-bellied iron cauldron on three short legs over a fire, full of
 * a bubbling brew. A witch (whoever wears the pointed hat) throws the ingredients in, one at a time
 * (or right clicks with one: she tosses it in); each changes the brew's colour. The three of a
 * recipe make the curse: the brew glows, and a glass bottle fills with it (three bottles a brew).
 * Something from another recipe ruins the brew in a black cloud; anything else, or anything thrown
 * by someone who is not a witch, is spat back out.
 */
public class WitchCauldron extends Prop {
    /** The brew's surface over the feet, the rim, the mouth's radius (blocks). */
    public static final double SURFACE = 1.08;
    public static final double RIM = 1.36;
    public static final double MOUTH = 0.7;
    public static final int DOSES = 3;
    /** Plain water, bubbling (not a curse yet). */
    public static final int WATER = 0x2C5A4E;

    public static final int EVENT_NONE = 0;
    public static final int EVENT_SPLASH = 1;
    public static final int EVENT_READY = 2;
    public static final int EVENT_FAIL = 3;
    public static final int EVENT_FILL = 4;

    private static final String SPAT = "punizioni.spat";

    /** The curse ready to bottle (its index), or -1. */
    private static final EntityDataAccessor<Integer> DATA_BREW = SynchedEntityData.defineId(WitchCauldron.class, EntityDataSerializers.INT);
    /** The ingredients in so far: bit 3 * curse + ingredient. */
    private static final EntityDataAccessor<Integer> DATA_MASK = SynchedEntityData.defineId(WitchCauldron.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DOSES = SynchedEntityData.defineId(WitchCauldron.class, EntityDataSerializers.INT);
    /** The last thing that happened and when (game time), for the drawing. */
    private static final EntityDataAccessor<Integer> DATA_EVENT = SynchedEntityData.defineId(WitchCauldron.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_EVENT_TIME = SynchedEntityData.defineId(WitchCauldron.class, EntityDataSerializers.LONG);

    public WitchCauldron(EntityType<? extends WitchCauldron> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BREW, -1);
        builder.define(DATA_MASK, 0);
        builder.define(DATA_DOSES, 0);
        builder.define(DATA_EVENT, EVENT_NONE);
        builder.define(DATA_EVENT_TIME, 0L);
    }

    @Override
    public ItemStack item() {
        return new ItemStack(ModItems.WITCH_CAULDRON);
    }

    public @Nullable CurseKind brew() {
        return CurseKind.byIndex(this.entityData.get(DATA_BREW));
    }

    public int mask() {
        return this.entityData.get(DATA_MASK);
    }

    public int doses() {
        return this.entityData.get(DATA_DOSES);
    }

    public int event() {
        return this.entityData.get(DATA_EVENT);
    }

    /** Ticks since the last event. */
    public float sinceEvent(float partial) {
        return this.level().getGameTime() - this.entityData.get(DATA_EVENT_TIME) + partial;
    }

    private void event(int kind) {
        this.entityData.set(DATA_EVENT, kind);
        this.entityData.set(DATA_EVENT_TIME, this.level().getGameTime());
    }

    /** The curse an ingredient belongs to and which of its three it is (3 * curse + i), or -1. */
    public static int ingredient(Item item) {
        for (CurseKind k : CurseKind.ALL) {
            int i = k.ingredients().indexOf(item);
            if (i >= 0) {
                return k.ordinal() * 3 + i;
            }
        }
        return -1;
    }

    /** The brew's colour now: water, turning towards the curse's colour with every right ingredient. */
    public int color() {
        CurseKind ready = this.brew();
        if (ready != null) {
            return ready.color();
        }
        int mask = this.mask();
        for (CurseKind k : CurseKind.ALL) {
            int bits = mask >> k.ordinal() * 3 & 7;
            if (bits != 0) {
                return ARGB.srgbLerp(Integer.bitCount(bits) / 3.0F * 0.75F, WATER, k.color()) & 0xFFFFFF;
            }
        }
        return WATER;
    }

    /** The middle of the brew's surface (world). */
    public Vec3 surface() {
        return this.position().add(0.0, SURFACE, 0.0);
    }

    // ------------------------------------------------------------------ what is thrown in

    @Override
    protected void serverTick(ServerLevel level) {
        if (this.random.nextInt(45) == 0) {
            Vec3 s = this.surface();
            level.playSound(null, s.x, s.y, s.z, ModSounds.CAULDRON_BUBBLE, SoundSource.BLOCKS, 0.7F, 0.8F + this.random.nextFloat() * 0.3F);
        }
        AABB mouth = new AABB(this.getX() - MOUTH, this.getY() + SURFACE - 0.5, this.getZ() - MOUTH, this.getX() + MOUTH, this.getY() + RIM + 0.5,
                this.getZ() + MOUTH);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, mouth, e -> e.isAlive() && !e.entityTags().contains(SPAT))) {
            double dx = item.getX() - this.getX();
            double dz = item.getZ() - this.getZ();
            if (dx * dx + dz * dz < MOUTH * MOUTH && item.getDeltaMovement().y < 0.15) {
                this.thrownIn(level, item);
            }
        }
    }

    private void thrownIn(ServerLevel level, ItemEntity item) {
        ItemStack stack = item.getItem();
        Entity owner = item.getOwner();
        Player thrower = owner instanceof Player p ? p : null;
        int ing = ingredient(stack.getItem());
        if (thrower == null || !Witches.isWitch(thrower) || ing < 0 || this.brew() != null) {
            this.spit(level, item, thrower);
            return;
        }
        Vec3 s = this.surface();
        CurseKind kind = CurseKind.ALL[ing / 3];
        int mask = this.mask();
        int others = mask & ~(7 << kind.ordinal() * 3);
        item.discard();
        level.playSound(null, s.x, s.y, s.z, ModSounds.CAULDRON_SPLASH, SoundSource.BLOCKS, 1.2F, 0.85F + this.random.nextFloat() * 0.3F);
        level.sendParticles(ParticleTypes.SPLASH, s.x, s.y + 0.05, s.z, 20, 0.3, 0.05, 0.3, 0.1);
        if (others != 0) {
            // two recipes do not mix: the brew curdles in a black cloud and is lost
            this.entityData.set(DATA_MASK, 0);
            this.event(EVENT_FAIL);
            level.playSound(null, s.x, s.y, s.z, ModSounds.CAULDRON_FAIL, SoundSource.BLOCKS, 1.5F, 1.0F);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, s.x, s.y + 0.3, s.z, 40, 0.4, 0.3, 0.4, 0.05);
            level.sendParticles(ParticleTypes.SQUID_INK, s.x, s.y + 0.3, s.z, 20, 0.4, 0.3, 0.4, 0.05);
            thrower.sendOverlayMessage(Component.translatable("punizioni.cauldron.ruined").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        mask |= 1 << ing;
        this.entityData.set(DATA_MASK, mask);
        this.event(EVENT_SPLASH);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | this.color()), s.x, s.y + 0.2, s.z, 25, 0.35, 0.2,
                0.35, 0.3);
        if ((mask >> kind.ordinal() * 3 & 7) == 7) {
            // the curse is ready
            this.entityData.set(DATA_MASK, 0);
            this.entityData.set(DATA_BREW, kind.ordinal());
            this.entityData.set(DATA_DOSES, DOSES);
            this.event(EVENT_READY);
            level.playSound(null, s.x, s.y, s.z, ModSounds.CAULDRON_READY, SoundSource.BLOCKS, 1.8F, 1.0F);
            level.playSound(null, thrower.getX(), thrower.getY() + 1.6, thrower.getZ(), ModSounds.WITCH_CACKLE, SoundSource.PLAYERS, 1.3F, 1.0F);
            level.sendParticles(ParticleTypes.WITCH, s.x, s.y + 0.5, s.z, 50, 0.5, 0.6, 0.5, 0.1);
            level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | kind.color()), s.x, s.y + 0.8, s.z, 60, 0.5,
                    0.8, 0.5, 0.5);
            thrower.sendOverlayMessage(Component.translatable("punizioni.cauldron.ready", Component.translatable(kind.potion().getDescriptionId()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            thrower.sendOverlayMessage(this.contents().withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    /** Back out it comes, with a belch (and a word to whoever is not a witch). */
    private void spit(ServerLevel level, ItemEntity item, @Nullable Player thrower) {
        Vec3 s = this.surface();
        double a = this.random.nextDouble() * Math.PI * 2.0;
        item.setPos(s.x, this.getY() + RIM + 0.3, s.z);
        item.setDeltaMovement(Math.cos(a) * 0.22, 0.42, Math.sin(a) * 0.22);
        item.addTag(SPAT);
        level.playSound(null, s.x, s.y, s.z, ModSounds.CAULDRON_SPIT, SoundSource.BLOCKS, 1.2F, 0.9F + this.random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.SPLASH, s.x, s.y + 0.1, s.z, 15, 0.25, 0.05, 0.25, 0.1);
        if (thrower != null) {
            String key = !Witches.isWitch(thrower) ? "punizioni.cauldron.not_witch"
                    : this.brew() != null ? "punizioni.cauldron.bottle_first" : "punizioni.cauldron.not_ingredient";
            thrower.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }

    /** "In the cauldron: ...", for the witch. */
    private MutableComponent contents() {
        int mask = this.mask();
        if (mask == 0) {
            return Component.translatable("punizioni.cauldron.water");
        }
        MutableComponent list = Component.empty();
        boolean first = true;
        for (int i = 0; i < CurseKind.ALL.length * 3; i++) {
            if ((mask & 1 << i) != 0) {
                Item item = CurseKind.ALL[i / 3].ingredients().get(i % 3);
                if (!first) {
                    list.append(", ");
                }
                list.append(Component.translatable(item.getDescriptionId()));
                first = false;
            }
        }
        return Component.translatable("punizioni.cauldron.contents", list);
    }

    // ------------------------------------------------------------------ right click

    @Override
    protected InteractionResult use(Player player, InteractionHand hand, ItemStack stack, Vec3 location) {
        ServerLevel level = this.level() instanceof ServerLevel l ? l : null;
        boolean witch = Witches.isWitch(player);
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (level != null) {
                CurseKind kind = this.brew();
                if (!witch) {
                    player.sendOverlayMessage(Component.translatable("punizioni.cauldron.not_witch").withStyle(ChatFormatting.GRAY));
                } else if (kind == null) {
                    player.sendOverlayMessage(Component.translatable("punizioni.cauldron.not_ready").withStyle(ChatFormatting.GRAY));
                } else {
                    ItemStack potion = new ItemStack(kind.potion());
                    stack.consume(1, player);
                    if (stack.isEmpty()) {
                        player.setItemInHand(hand, potion);
                    } else if (!player.getInventory().add(potion)) {
                        player.spawnAtLocation(level, potion);
                    }
                    int left = this.doses() - 1;
                    this.entityData.set(DATA_DOSES, left);
                    if (left <= 0) {
                        this.entityData.set(DATA_BREW, -1);
                    }
                    this.event(EVENT_FILL);
                    Vec3 s = this.surface();
                    level.playSound(null, s.x, s.y, s.z, SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 0.8F);
                    level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | kind.color()), s.x, s.y + 0.3, s.z, 15,
                            0.2, 0.2, 0.2, 0.2);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (witch && ingredient(stack.getItem()) >= 0) {
            // tossed in from the hand: it flies into the pot and splashes in like a thrown one
            if (level != null) {
                ItemStack one = stack.split(1);
                Vec3 from = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.5)).add(0.0, -0.3, 0.0);
                ItemEntity item = new ItemEntity(level, from.x, from.y, from.z, one);
                Vec3 to = this.position().add(0.0, RIM + 0.2, 0.0);
                Vec3 d = to.subtract(from);
                double flight = 10.0;
                item.setDeltaMovement(d.x / flight, d.y / flight + 0.04 * flight * 0.5, d.z / flight);
                item.setThrower(player);
                item.setPickUpDelay(40);
                level.addFreshEntity(item);
                player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            }
            return InteractionResult.SUCCESS;
        }
        if (level != null) {
            CurseKind kind = this.brew();
            Component line;
            if (!witch) {
                line = Component.translatable("punizioni.cauldron.looks");
            } else if (kind != null) {
                line = Component.translatable("punizioni.cauldron.ready_doses", Component.translatable(kind.potion().getDescriptionId()), this.doses());
            } else {
                line = this.contents();
            }
            player.sendOverlayMessage(line.copy().withStyle(ChatFormatting.DARK_PURPLE));
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ the client

    @Override
    protected void clientTick() {
        Vec3 s = this.surface();
        int color = 0xFF000000 | this.color();
        if (this.random.nextInt(2) == 0) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(this.random.nextDouble()) * (MOUTH - 0.12);
            this.level().addParticle(ParticleTypes.BUBBLE_POP, s.x + Math.cos(a) * r, s.y + 0.02, s.z + Math.sin(a) * r, 0.0, 0.03, 0.0);
        }
        if (this.random.nextInt(3) == 0) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(this.random.nextDouble()) * (MOUTH - 0.15);
            this.level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, color), s.x + Math.cos(a) * r, s.y + 0.1,
                    s.z + Math.sin(a) * r, 0.0, 0.0, 0.0);
        }
        if (this.random.nextInt(4) == 0) {
            this.level().addParticle(ParticleTypes.WHITE_SMOKE, s.x + (this.random.nextDouble() - 0.5) * 0.8, s.y + 0.3,
                    s.z + (this.random.nextDouble() - 0.5) * 0.8, 0.0, 0.03, 0.0);
        }
        if (this.brew() != null && this.random.nextInt(3) == 0) {
            // a curse is ready: it swirls and glitters
            double a = this.level().getGameTime() * 0.25 + this.random.nextDouble() * 0.6;
            this.level().addParticle(ParticleTypes.WITCH, s.x + Math.cos(a) * 0.5, s.y + 0.4 + this.random.nextDouble() * 0.6,
                    s.z + Math.sin(a) * 0.5, 0.0, 0.05, 0.0);
        }
        // the fire under the pot
        if (this.random.nextInt(3) == 0) {
            Vec3 f = this.local((this.random.nextDouble() - 0.5) * 0.7, 0.12, (this.random.nextDouble() - 0.5) * 0.7);
            this.level().addParticle(this.random.nextInt(3) == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME, f.x, f.y, f.z, 0.0, 0.02, 0.0);
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.entityData.set(DATA_BREW, input.getIntOr("brew", -1));
        this.entityData.set(DATA_MASK, input.getIntOr("mask", 0));
        this.entityData.set(DATA_DOSES, input.getIntOr("doses", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("brew", this.entityData.get(DATA_BREW));
        output.putInt("mask", this.mask());
        output.putInt("doses", this.doses());
    }
}
