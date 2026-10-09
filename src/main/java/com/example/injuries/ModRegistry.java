package com.example.injuries;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.function.Function;

public final class ModRegistry {
    private ModRegistry() {}

    /** What each item cures is no longer defined here: see data/injuries/injuries/cure/*.json. */
    public static final Item BANDAGE = item("bandage", Item::new);
    public static final Item SPLINT = item("splint", Item::new);
    public static final Item COLD_COMPRESS = item("cold_compress", Item::new);
    public static final Item ANTISEPTIC_HERBS = item("antiseptic_herbs", Item::new);
    public static final Item BURN_SALVE = item("burn_salve", Item::new);
    public static final Item ANTIDOTE = item("antidote", Item::new);
    public static final Item RIB_BRACE = item("rib_brace", Item::new);
    public static final Item OFUDA = item("ofuda", Item::new);
    public static final Item MEDKIT = item("medkit", Item::new);

    public static final Item HEALING_CRYSTAL = item("healing_crystal", Item::new);
    public static final Item CHILLING_PEARL = item("chilling_pearl", Item::new);
    public static final Item GOLDEN_BONE = item("golden_bone", Item::new);
    public static final Item ANTIBIOTICS = item("antibiotics", Item::new);

    public static final MenuType<InjuryMenu> INJURY_MENU = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(InjuriesMod.MOD_ID, "injury_menu"),
            new MenuType<>(InjuryMenu::new, FeatureFlags.VANILLA_SET));

    private static <T extends Item> T item(String name, Function<Item.Properties, T> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(InjuriesMod.MOD_ID, name));
        T item = factory.apply(new Item.Properties().setId(key).stacksTo(16));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(BANDAGE);
            output.accept(SPLINT);
            output.accept(COLD_COMPRESS);
            output.accept(ANTISEPTIC_HERBS);
            output.accept(BURN_SALVE);
            output.accept(ANTIDOTE);
            output.accept(RIB_BRACE);
            output.accept(OFUDA);
            output.accept(MEDKIT);
            output.accept(HEALING_CRYSTAL);
            output.accept(CHILLING_PEARL);
            output.accept(GOLDEN_BONE);
            output.accept(ANTIBIOTICS);
        });
    }
}