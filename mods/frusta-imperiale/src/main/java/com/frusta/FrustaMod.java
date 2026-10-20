package com.frusta;

import com.frusta.config.FrustaConfig;
import com.frusta.network.FrustaNetwork;
import com.frusta.registry.ModEnchantments;
import com.frusta.registry.ModItems;
import com.frusta.registry.ModRecipes;
import com.frusta.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Frusta Imperiale: a black leather whip, five to six blocks long, that drags behind you, cracks
 * louder than anything else in the game, spins over your head for the Imperial Crack and grabs your
 * friends to pull them in. It can be upgraded to diamond and netherite and enchanted with
 * Sottomissione, which makes whipped targets kneel.
 */
@Mod(FrustaMod.MODID)
public class FrustaMod {
    public static final String MODID = "frusta";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FrustaMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModEnchantments.ENCHANTMENTS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        modBus.addListener(FrustaMod::addToCreativeTabs);
        FrustaNetwork.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, FrustaConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FrustaConfig.CLIENT_SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.WHIP);
            event.accept(ModItems.DIAMOND_WHIP);
            event.accept(ModItems.NETHERITE_WHIP);
            Enchantment sottomissione = ModEnchantments.SOTTOMISSIONE.get();
            for (int level = 1; level <= sottomissione.getMaxLevel(); level++) {
                event.accept(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(sottomissione, level)));
            }
        }
    }
}
