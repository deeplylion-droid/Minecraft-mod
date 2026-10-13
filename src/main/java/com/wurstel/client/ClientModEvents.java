package com.wurstel.client;

import com.wurstel.WurstelMod;
import com.wurstel.client.render.WurstelLayer;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client setup: adds the würstel layer to players and humanoid mobs. */
@Mod.EventBusSubscriber(modid = WurstelMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    /** Humanoid mobs that can wear leggings (and so the belt) besides players. */
    private static final List<EntityType<? extends LivingEntity>> HUMANOIDS = List.of(
            EntityType.ARMOR_STAND,
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER, EntityType.GIANT,
            EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON,
            EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN);

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            attach(renderer);
        }
        for (EntityType<? extends LivingEntity> type : HUMANOIDS) {
            EntityRenderer<?> renderer = event.getRenderer(type);
            attach(renderer);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void attach(@Nullable EntityRenderer<?> renderer) {
        if (renderer instanceof LivingEntityRenderer living && living.getModel() instanceof HumanoidModel) {
            living.addLayer(new WurstelLayer(living));
        }
    }

    private ClientModEvents() {
    }
}
