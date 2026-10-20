package com.chiappe.client.mixin;

import com.chiappe.client.Cheeks;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players with big cheeks are drawn while any of the cheeks can be seen, and from farther away. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void chiappe$bigCheeks(Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player player)) {
            return;
        }
        double r = Cheeks.radius(player);
        if (r < 0.5) {
            return;
        }
        AABB box = player.getInterpolatedBoundingBox(partialTicks).inflate(2.4 * r);
        double reach = box.getSize() * 64.0 * Entity.getViewScale();
        if (player.distanceToSqr(camX, camY, camZ) >= reach * reach) {
            cir.setReturnValue(false);
        } else {
            cir.setReturnValue(culler.isVisible(box));
        }
    }
}
