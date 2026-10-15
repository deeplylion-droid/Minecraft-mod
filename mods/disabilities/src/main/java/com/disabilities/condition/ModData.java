package com.disabilities.condition;

import com.disabilities.Disabilities;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.Entity;

/** The data attached to players (and to the overworld) and quick ways to read it on either side. */
public final class ModData {
    private ModData() {
    }

    /** The chosen conditions: saved, kept on death, known by every client (they draw them). */
    public static final AttachmentType<Picks> PICKS = AttachmentRegistry.create(Disabilities.id("picks"),
            b -> b.persistent(Picks.CODEC).copyOnDeath().syncWith(Picks.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** Episodes in progress (asleep, bleeding...): not saved, known by every client. */
    public static final AttachmentType<Status> STATUS = AttachmentRegistry.create(Disabilities.id("status"),
            b -> b.syncWith(Status.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** On the overworld: who holds which conditions, for the "unique" rule. */
    public static final AttachmentType<Taken> TAKEN = AttachmentRegistry.create(Disabilities.id("taken"),
            b -> b.persistent(Taken.CODEC));

    public static void init() {
    }

    public static Picks picks(Entity entity) {
        return entity.getAttachedOrElse(PICKS, Picks.NONE);
    }

    public static boolean has(Entity entity, Condition condition) {
        return picks(entity).has(condition);
    }

    public static Status status(Entity entity) {
        return entity.getAttachedOrElse(STATUS, Status.NONE);
    }

    public static boolean asleep(Entity entity) {
        return status(entity).asleep(entity.level().getGameTime());
    }

    public static boolean frozen(Entity entity) {
        long now = entity.level().getGameTime();
        Status s = status(entity);
        return s.asleep(now) || s.frozen(now);
    }
}
