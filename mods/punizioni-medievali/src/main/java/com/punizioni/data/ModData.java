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

    public static void init() {
    }
}
