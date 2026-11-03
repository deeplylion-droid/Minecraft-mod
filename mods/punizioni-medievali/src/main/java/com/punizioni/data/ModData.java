package com.punizioni.data;

import com.punizioni.Punizioni;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.Entity;

public final class ModData {
    private ModData() {
    }

    /** Saved, lost on death, known by every client. */
    public static final AttachmentType<Body> BODY = AttachmentRegistry.create(Punizioni.id("body"),
            b -> b.persistent(Body.CODEC).syncWith(Body.STREAM_CODEC, AttachmentSyncPredicate.all()));

    public static Body body(Entity entity) {
        return entity.getAttachedOrElse(BODY, Body.NONE);
    }

    public static void setBody(Entity entity, Body body) {
        entity.setAttached(BODY, body);
    }

    /** The prisoner's rope: saved, lost on death, known by every client. */
    public static final AttachmentType<Bound> BOUND = AttachmentRegistry.create(Punizioni.id("bound"),
            b -> b.persistent(Bound.CODEC).syncWith(Bound.STREAM_CODEC, AttachmentSyncPredicate.all()));

    public static Bound bound(Entity entity) {
        return entity.getAttached(BOUND);
    }

    public static boolean isBound(Entity entity) {
        return entity.hasAttached(BOUND);
    }

    /** Dung on the body: saved, washed off in water, known by every client. */
    public static final AttachmentType<Filth> FILTH = AttachmentRegistry.create(Punizioni.id("filth"),
            b -> b.persistent(Filth.CODEC).syncWith(Filth.STREAM_CODEC, AttachmentSyncPredicate.all()));

    public static Filth filth(Entity entity) {
        return entity.getAttachedOrElse(FILTH, Filth.CLEAN);
    }

    public static void setFilth(Entity entity, Filth filth) {
        if (filth.clean()) {
            entity.removeAttached(FILTH);
        } else {
            entity.setAttached(FILTH, filth);
        }
    }

    public static void init() {
    }
}
