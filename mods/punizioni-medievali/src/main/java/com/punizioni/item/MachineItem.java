package com.punizioni.item;

import com.punizioni.machine.Machine;
import com.punizioni.machine.MachineKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Places a punishment machine on the block clicked, facing whoever places it. */
public class MachineItem extends Item {
    private final MachineKind kind;

    public MachineItem(MachineKind kind, Item.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public MachineKind kind() {
        return this.kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() == Direction.DOWN) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        BlockPos pos = new BlockPlaceContext(context).getClickedPos();
        Vec3 at = Vec3.atBottomCenterOf(pos);
        AABB box = this.kind.type().getDimensions().makeBoundingBox(at.x, at.y, at.z).deflate(0.05);
        Player player = context.getPlayer();
        if (!level.noCollision(null, box) || !level.getEntitiesOfClass(Machine.class, box.inflate(0.2)).isEmpty()) {
            if (player != null && !level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("punizioni.msg.no_room"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            Machine machine = this.kind.type().create(server, EntitySpawnReason.SPAWN_ITEM_USE);
            if (machine == null) {
                return InteractionResult.FAIL;
            }
            // it faces the one who placed it, squared to the four directions
            float yaw = Mth.floor((Mth.wrapDegrees(context.getRotation() - 180.0F) + 45.0F) / 90.0F) * 90.0F;
            machine.snapTo(at.x, at.y, at.z, yaw, 0.0F);
            server.addFreshEntity(machine);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.2F, 0.7F);
            machine.gameEvent(GameEvent.ENTITY_PLACE, player);
        }
        // not used up in creative
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
