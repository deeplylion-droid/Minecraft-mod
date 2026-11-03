package com.punizioni.item;

import com.punizioni.Punizioni;
import com.punizioni.machine.MachineKind;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import com.punizioni.entity.ModEntities;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public final class ModItems {
    private ModItems() {
    }

    /**
     * The executioner's gear: black leather. The vanilla armour layer draws nothing for it (its
     * equipment asset has no layers): the hood, the apron, the breeches and the boots are 3D models
     * drawn by the mod.
     */
    public static final ResourceKey<EquipmentAsset> EXECUTIONER_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Punizioni.id("executioner"));
    public static final ArmorMaterial EXECUTIONER = new ArmorMaterial(18,
            Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 3, ArmorType.CHESTPLATE, 4, ArmorType.HELMET, 2, ArmorType.BODY, 4),
            14, SoundEvents.ARMOR_EQUIP_LEATHER, 0.5F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR, EXECUTIONER_ASSET);

    public static final Item EXECUTIONER_HOOD = register("executioner_hood", Item::new,
            new Item.Properties().humanoidArmor(EXECUTIONER, ArmorType.HELMET));
    public static final Item EXECUTIONER_APRON = register("executioner_apron", Item::new,
            new Item.Properties().humanoidArmor(EXECUTIONER, ArmorType.CHESTPLATE));
    public static final Item EXECUTIONER_BREECHES = register("executioner_breeches", Item::new,
            new Item.Properties().humanoidArmor(EXECUTIONER, ArmorType.LEGGINGS));
    public static final Item EXECUTIONER_BOOTS = register("executioner_boots", Item::new,
            new Item.Properties().humanoidArmor(EXECUTIONER, ArmorType.BOOTS));

    /** Nine knotted leather tails on a wooden handle: every lash leaves a mark on the back. */
    public static final Item CAT_O_NINE_TAILS = register("cat_o_nine_tails", CatOfNineTailsItem::new,
            new Item.Properties().stacksTo(1).durability(600).attributes(weapon(3.0, -2.5)).component(DataComponents.WEAPON, new Weapon(1)));
    /** A broad, heavy headsman's axe: takes hands and feet on the chopping block. */
    public static final Item EXECUTIONER_AXE = register("executioner_axe", Item::new,
            new Item.Properties().axe(ToolMaterial.IRON, 7.0F, -3.3F).durability(900).rarity(Rarity.UNCOMMON));
    /** An iron-bound mallet: breaks bones on the wheel. */
    public static final Item EXECUTIONER_MALLET = register("executioner_mallet", Item::new,
            new Item.Properties().stacksTo(1).durability(900).attributes(weapon(7.0, -3.4)).component(DataComponents.WEAPON, new Weapon(1))
                    .rarity(Rarity.UNCOMMON));
    /** Pitch-soaked torch: lights the stake and the bull. */
    public static final Item EXECUTIONER_TORCH = register("executioner_torch", Item::new, new Item.Properties().stacksTo(1));

    /**
     * The king's regalia: a gold crown and an ermine-lined velvet mantle. Like the executioner's gear
     * the vanilla armour layer draws nothing (transparent equipment asset): the crown is a 3D model on
     * the head, the mantle is cloth simulated on every client.
     */
    public static final ResourceKey<EquipmentAsset> ROYAL_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Punizioni.id("royal"));
    public static final ArmorMaterial ROYAL_GOLD = new ArmorMaterial(33,
            Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 3, ArmorType.CHESTPLATE, 4, ArmorType.HELMET, 3, ArmorType.BODY, 4),
            25, SoundEvents.ARMOR_EQUIP_GOLD, 1.0F, 0.0F, ItemTags.REPAIRS_GOLD_ARMOR, ROYAL_ASSET);
    public static final ArmorMaterial ROYAL_CLOTH = new ArmorMaterial(33,
            Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 3, ArmorType.CHESTPLATE, 4, ArmorType.HELMET, 2, ArmorType.BODY, 4),
            25, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.0F, ItemTags.WOOL, ROYAL_ASSET);

    public static final Item ROYAL_CROWN = register("royal_crown", Item::new,
            new Item.Properties().humanoidArmor(ROYAL_GOLD, ArmorType.HELMET).rarity(Rarity.EPIC));
    public static final Item ROYAL_CAPE = register("royal_cape", Item::new,
            new Item.Properties().humanoidArmor(ROYAL_CLOTH, ArmorType.CHESTPLATE).rarity(Rarity.EPIC));
    /** The king's lens: looks into a subject's pockets, their Ender chest and their recent misdeeds. */
    public static final Item ROYAL_LENS = register("royal_lens", RoyalLensItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    /** The overseer's bullwhip: a crack on the back and the peasant works faster (Speed and Haste). */
    public static final Item OVERSEER_WHIP = register("overseer_whip", OverseerWhipItem::new,
            new Item.Properties().stacksTo(1).durability(800).attributes(OverseerWhipItem.attributes()).component(DataComponents.WEAPON, new Weapon(1))
                    .rarity(Rarity.UNCOMMON));

    /** The prisoner's rope: ties a player's hands and leads them on a 4-block rope (see server.Bonds). */
    public static final Item PRISONER_ROPE = register("prisoner_rope", Item::new, new Item.Properties().stacksTo(16));

    /**
     * The jester's motley: red and yellow halves, gold bells on every point. Like the king's
     * regalia the vanilla armour layer draws nothing: hat, collar, tunic, hose and shoes are 3D
     * models (OutfitsLayer), the points of the hat swing, the bells ring as the jester walks.
     */
    public static final ResourceKey<EquipmentAsset> JESTER_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Punizioni.id("jester"));
    public static final ArmorMaterial JESTER = new ArmorMaterial(10,
            Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 1, ArmorType.CHESTPLATE, 2, ArmorType.HELMET, 1, ArmorType.BODY, 2),
            22, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.WOOL, JESTER_ASSET);

    public static final Item JESTER_HAT = register("jester_hat", Item::new,
            new Item.Properties().humanoidArmor(JESTER, ArmorType.HELMET).rarity(Rarity.UNCOMMON));
    public static final Item JESTER_TUNIC = register("jester_tunic", Item::new,
            new Item.Properties().humanoidArmor(JESTER, ArmorType.CHESTPLATE).rarity(Rarity.UNCOMMON));
    public static final Item JESTER_HOSE = register("jester_hose", Item::new,
            new Item.Properties().humanoidArmor(JESTER, ArmorType.LEGGINGS).rarity(Rarity.UNCOMMON));
    public static final Item JESTER_SHOES = register("jester_shoes", Item::new,
            new Item.Properties().humanoidArmor(JESTER, ArmorType.BOOTS).rarity(Rarity.UNCOMMON));

    /** The prisoner's rags: a sackcloth hood and tunic, torn breeches, foot rags, rusty irons on wrists and ankles (3D, OutfitsLayer). */
    public static final ResourceKey<EquipmentAsset> PEASANT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Punizioni.id("peasant"));
    public static final ArmorMaterial PEASANT = new ArmorMaterial(4,
            Map.of(ArmorType.BOOTS, 0, ArmorType.LEGGINGS, 1, ArmorType.CHESTPLATE, 1, ArmorType.HELMET, 0, ArmorType.BODY, 1),
            5, SoundEvents.ARMOR_EQUIP_CHAIN, 0.0F, 0.0F, ItemTags.WOOL, PEASANT_ASSET);

    public static final Item PEASANT_HOOD = register("peasant_hood", Item::new,
            new Item.Properties().humanoidArmor(PEASANT, ArmorType.HELMET));
    public static final Item PEASANT_TUNIC = register("peasant_tunic", Item::new,
            new Item.Properties().humanoidArmor(PEASANT, ArmorType.CHESTPLATE));
    public static final Item PEASANT_BREECHES = register("peasant_breeches", Item::new,
            new Item.Properties().humanoidArmor(PEASANT, ArmorType.LEGGINGS));
    public static final Item PEASANT_WRAPS = register("peasant_wraps", Item::new,
            new Item.Properties().humanoidArmor(PEASANT, ArmorType.BOOTS));

    /** Dung: animals drop it now and then; thrown, it stains whoever it hits (see server.Dung). */
    public static final Item POOP = register("poop", PoopItem::new, new Item.Properties().stacksTo(16));

    /** A whole roast pig on its platter: set it on a table; a right click tears off the piece you look at. */
    public static final Item ROAST_PIG = register("roast_pig", p -> new PropItem(() -> ModEntities.ROAST_PIG, p),
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    /** The pieces of the roast. */
    public static final Item ROAST_LEG = register("roast_leg", Item::new,
            new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.8F).build()));
    public static final Item ROAST_RIBS = register("roast_ribs", Item::new,
            new Item.Properties().food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.8F).build()));
    public static final Item ROAST_HAM = register("roast_ham", Item::new,
            new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.9F).build()));
    /** A pot of gruel on its fire: bowls fill from it. */
    public static final Item GRUEL_POT = register("gruel_pot", p -> new PropItem(() -> ModEntities.GRUEL_POT, p), new Item.Properties().stacksTo(1));
    /** A bowl of gruel: fills a little, tastes awful; right click someone to feed them. */
    public static final Item GRUEL = register("gruel", GruelItem::new, new Item.Properties().stacksTo(16)
            .food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.1F).alwaysEdible().build(),
                    Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.NAUSEA, 140, 0), 0.35F))
                            .build())
            .usingConvertsTo(Items.BOWL));

    public static final Map<MachineKind, Item> MACHINES = new EnumMap<>(MachineKind.class);

    static {
        for (MachineKind kind : MachineKind.ALL) {
            MACHINES.put(kind, register(kind.id(), p -> new MachineItem(kind, p), new Item.Properties().stacksTo(1)));
        }
    }

    private static ItemAttributeModifiers weapon(double damage, double speed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Punizioni.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
            output.accept(EXECUTIONER_HOOD);
            output.accept(EXECUTIONER_APRON);
            output.accept(EXECUTIONER_BREECHES);
            output.accept(EXECUTIONER_BOOTS);
            output.accept(CAT_O_NINE_TAILS);
            output.accept(EXECUTIONER_AXE);
            output.accept(EXECUTIONER_MALLET);
            output.accept(ROYAL_CROWN);
            output.accept(ROYAL_CAPE);
            output.accept(OVERSEER_WHIP);
            output.accept(JESTER_HAT);
            output.accept(JESTER_TUNIC);
            output.accept(JESTER_HOSE);
            output.accept(JESTER_SHOES);
            output.accept(PEASANT_HOOD);
            output.accept(PEASANT_TUNIC);
            output.accept(PEASANT_BREECHES);
            output.accept(PEASANT_WRAPS);
            output.accept(POOP);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
            output.accept(ROAST_PIG);
            output.accept(ROAST_LEG);
            output.accept(ROAST_RIBS);
            output.accept(ROAST_HAM);
            output.accept(GRUEL_POT);
            output.accept(GRUEL);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(ROYAL_LENS);
            output.accept(PRISONER_ROPE);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
            output.accept(EXECUTIONER_TORCH);
            for (MachineKind kind : MachineKind.ALL) {
                output.accept(MACHINES.get(kind));
            }
        });
    }
}
