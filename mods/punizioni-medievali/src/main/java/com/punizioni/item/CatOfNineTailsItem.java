package com.punizioni.item;

import com.punizioni.ModSounds;
import com.punizioni.data.Body;
import com.punizioni.data.ModData;
import com.punizioni.machine.WhippingPost;
import com.punizioni.net.Net;
import com.punizioni.server.Gore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The cat-o'-nine-tails: every lash cracks, sprays blood off the back and leaves a red welt there, on
 * the skin itself (Body.lashes; the client draws them: see MarkedSkins). On someone chained to the
 * whipping post it hurts even more.
 */
public class CatOfNineTailsItem extends Item {
    public CatOfNineTailsItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.hurtEnemy(stack, target, attacker);
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Body body = ModData.body(target).lashed(level.getGameTime());
        ModData.setBody(target, body);
        level.playSound(null, target.getX(), target.getY() + 1.2, target.getZ(), ModSounds.LASH, SoundSource.PLAYERS, 1.6F,
                0.85F + level.getRandom().nextFloat() * 0.3F);
        // the back: behind the body, at the shoulder blades
        float yaw = target.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 back = target.position().add(Mth.sin(yaw) * 0.3, target.getBbHeight() * 0.68, -Mth.cos(yaw) * 0.3);
        Gore.burst(level, back, 14, 0.18, 0.12);
        Gore.jet(level, back, new Vec3(Mth.sin(yaw), 0.4, -Mth.cos(yaw)), 3, 0.25);
        Gore.hurtScreen(target, Net.VictimFx.LASH, target.getVehicle() instanceof WhippingPost ? 1.0F : 0.6F);
        if (target.getVehicle() instanceof WhippingPost post) {
            post.lashed(level, target, body.lashes());
        }
    }
}
