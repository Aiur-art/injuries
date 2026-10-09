package com.example.injuries;

import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The two datapack registries.
 *   data/<namespace>/injuries/injury/<name>.json   -> {@link Injury}
 *   data/<namespace>/injuries/cure/<name>.json     -> {@link Cure}
 * Kept free of codec fields on purpose: the codecs reference these keys, so they must not live in the same class.
 */
public final class InjuryRegistries {
    private InjuryRegistries() {}

    public static final ResourceKey<Registry<Injury>> INJURY = key("injury");
    public static final ResourceKey<Registry<Cure>> CURE = key("cure");

    private static <T> ResourceKey<Registry<T>> key(String path) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(InjuriesMod.MOD_ID, path));
    }

    static void init() {
        // "synced": the client receives the definitions too (names for the screen, cures for slot validation).
        DynamicRegistries.registerSynced(INJURY, Injury.CODEC);
        DynamicRegistries.registerSynced(CURE, Cure.CODEC);
    }
}
