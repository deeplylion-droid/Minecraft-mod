package com.filorosso.data;

import com.filorosso.FiloRosso;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.Entity;

/** Data attached to players and quick ways to read it on either side. */
public final class ModData {
    private ModData() {
    }

    /** The player's bond: saved, kept on death, known by every client (they draw the thread). */
    public static final AttachmentType<Link> LINK = AttachmentRegistry.create(FiloRosso.id("link"),
            b -> b.persistent(Link.CODEC).copyOnDeath().syncWith(Link.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** Downed and revive progress: not saved, known by every client. */
    public static final AttachmentType<Status> STATUS = AttachmentRegistry.create(FiloRosso.id("status"),
            b -> b.syncWith(Status.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** Powers working on a player right now (slingshot, Eterni, a kiss, the Burattinaio's strings): not saved, known by every client. */
    public static final AttachmentType<Powers> POWERS = AttachmentRegistry.create(FiloRosso.id("powers"),
            b -> b.syncWith(Powers.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** Where a player came from before the Theatre (saved, kept on death, nobody else needs it). */
    public static final AttachmentType<Home> HOME = AttachmentRegistry.create(FiloRosso.id("home"),
            b -> b.persistent(Home.CODEC).copyOnDeath());

    public static void init() {
    }

    public static Powers powers(Entity entity) {
        return entity.getAttachedOrElse(POWERS, Powers.NONE);
    }

    public static Link link(Entity entity) {
        return entity.getAttachedOrElse(LINK, Link.NONE);
    }

    public static Status status(Entity entity) {
        return entity.getAttachedOrElse(STATUS, Status.NONE);
    }

    public static boolean downed(Entity entity) {
        return status(entity).downed();
    }
}
