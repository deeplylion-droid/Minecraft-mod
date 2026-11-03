package com.punizioni.item;

import com.punizioni.entity.Prop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** Sets a prop (the roast pig, the pot of gruel) down on the block clicked, a table or the floor, facing whoever sets it. */
public class PropItem extends Item {
    private final Supplier<EntityType<? extends Prop>> type;

    public PropItem(Supplier<EntityType<? extends Prop>> type, Item.Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        BlockPos pos = new BlockPlaceContext(context).getClickedPos();
        // on top of whatever was clicked (a slab or a stair counts: the top of its shape)
        Vec3 at = new Vec3(pos.getX() + 0.5, context.getClickLocation().y, pos.getZ() + 0.5);
        AABB box = this.type.get().getDimensions().makeBoundingBox(at.x, at.y, at.z).deflate(0.05);
        Player player = context.getPlayer();
        if (!level.noCollision(null, box) || !level.getEntitiesOfClass(Prop.class, box.inflate(0.1)).isEmpty()) {
            if (player != null && !level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("punizioni.msg.no_room"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            Prop prop = this.type.get().create(server, EntitySpawnReason.SPAWN_ITEM_USE);
            if (prop == null) {
                return InteractionResult.FAIL;
            }
            float yaw = Math.round((context.getRotation() - 180.0F) / 45.0F) * 45.0F;
            prop.snapTo(at.x, at.y, at.z, yaw, 0.0F);
            server.addFreshEntity(prop);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            prop.gameEvent(GameEvent.ENTITY_PLACE, player);
        }
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
