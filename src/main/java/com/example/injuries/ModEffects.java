package com.example.injuries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cosmetic HUD effects for the built-in injuries, registered as injuries:<id>.
 * Icon: assets/injuries/textures/mob_effect/<id>.png (18x18). Name: lang key effect.injuries.<id>.
 * Mob effects are a static registry, so datapack-made injuries cannot add new ones (they can still point
 * "hud_effect" at any registered inert effect, or have no HUD icon).
 */
public final class ModEffects {
    private ModEffects() {}

    private static final Map<String, Integer> BUILT_IN = new LinkedHashMap<>();
    static {
        BUILT_IN.put("bleeding", 0x8B0000);      BUILT_IN.put("broken_arm", 0x8B5A2B);
        BUILT_IN.put("broken_leg", 0x6B4423);    BUILT_IN.put("concussion", 0x9A9AC8);
        BUILT_IN.put("infection", 0x6B8E23);     BUILT_IN.put("burn", 0xE25822);
        BUILT_IN.put("poisoned", 0x4E9331);      BUILT_IN.put("fractured_rib", 0xD8D0C0);
        BUILT_IN.put("magic_curse", 0x7B2CBF);   BUILT_IN.put("laceration", 0xB22222);
        BUILT_IN.put("sprained_ankle", 0xA0522D); BUILT_IN.put("dislocated_shoulder", 0xC19A6B);
        BUILT_IN.put("vertigo", 0xB0A8E0);       BUILT_IN.put("fever", 0xE8B040);
        BUILT_IN.put("frostbite", 0x8FD3F4);     BUILT_IN.put("wither_rot", 0x2B2B2B);
        BUILT_IN.put("punctured_lung", 0xC77D8E); BUILT_IN.put("hex", 0x4B0082);
        BUILT_IN.put("soul_burn", 0x2FD6D0);     BUILT_IN.put("cursed_wound", 0x6A0DAD);
        BUILT_IN.put("compound_fracture", 0xE8E0D0); BUILT_IN.put("heatstroke", 0xFF9F1C);
        BUILT_IN.put("sepsis", 0x7A9A01);        BUILT_IN.put("head_wound", 0xC0392B);
        BUILT_IN.put("helminth", 0xf590d7);       BUILT_IN.put("overcharge", 0xfffda1);
        BUILT_IN.put("hack", 0x8854f7);       BUILT_IN.put("short_circuit", 0xc25632);
        BUILT_IN.put("radiation", 0xfad052);       BUILT_IN.put("mutation", 0x147041);
    }

    static void init() {
        BUILT_IN.forEach((id, color) -> Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(InjuriesMod.MOD_ID, id), new InjuryEffect(color)));
    }
}
