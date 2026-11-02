package com.punizioni.item;

import com.punizioni.inspect.Inspection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The king's lens. Right click on a subject (up to 10 blocks away): their whole inventory, armour
 * included, and their recent misdeeds; crouching, their Ender chest. Whatever the king takes is
 * confiscated. While it is held, subjects with recent misdeeds glow red (the server tells the client
 * who they are: see Royalty).
 */
public class RoyalLensItem extends Item {
    public static final double REACH = 10.0;

    public RoyalLensItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player subject) || subject == player) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer king && subject instanceof ServerPlayer peasant) {
            Inspection.open(king, peasant, king.isShiftKeyDown());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Player subject = lookedAt(player, REACH);
        if (subject == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer king && subject instanceof ServerPlayer peasant) {
            Inspection.open(king, peasant, king.isShiftKeyDown());
        }
        return InteractionResult.SUCCESS;
    }

    /** The player in the crosshair, up to {@code reach} blocks and not behind a wall. */
    public static @Nullable Player lookedAt(Player player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                (Entity e) -> e instanceof Player p && p != player && !p.isSpectator() && p.isAlive(), eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof Player p ? p : null;
    }
}
