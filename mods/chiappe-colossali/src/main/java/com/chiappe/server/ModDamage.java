package com.chiappe.server;

import com.chiappe.Chiappe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** Damage types (data/chiappe/damage_type): they give their own death messages. */
public final class ModDamage {
    private ModDamage() {
    }

    /** Someone's fart. */
    public static final ResourceKey<DamageType> FART = ResourceKey.create(Registries.DAMAGE_TYPE, Chiappe.id("fart"));
}
