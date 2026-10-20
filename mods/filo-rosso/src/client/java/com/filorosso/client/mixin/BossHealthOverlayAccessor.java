package com.filorosso.client.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/** How many boss bars are on screen (the event banner goes below them). */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> filorosso$events();
}
