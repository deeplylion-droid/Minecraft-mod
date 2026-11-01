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
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
            output.accept(EXECUTIONER_TORCH);
            for (MachineKind kind : MachineKind.ALL) {
                output.accept(MACHINES.get(kind));
            }
        });
    }
}
