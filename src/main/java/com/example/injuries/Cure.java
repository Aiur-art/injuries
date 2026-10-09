package com.example.injuries;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Something that cures injuries, loaded from data/&lt;ns&gt;/injuries/cure/&lt;name&gt;.json.
 * Works for ANY item (vanilla items, items with custom_data, other mods' items) via a vanilla item predicate.
 * Put the item in a treatment slot of the injury screen: one item is used up per {@code amount} injuries cured.
 *
 * @param item       vanilla item predicate ("items": id / list / "#tag", optional "components")
 * @param slots      treatment slot(s) (0-7) the item may be placed in; empty = any slot
 * @param injuries   injuries it cures
 * @param categories every injury of these categories it cures
 * @param amount     how many injuries one item cures (default 1)
 */
public record Cure(ItemPredicate item, List<Integer> slots, List<ResourceKey<Injury>> injuries, List<String> categories, int amount) {

    public static final Codec<Cure> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemPredicate.CODEC.fieldOf("item").forGetter(Cure::item),
            Slots.CODEC.optionalFieldOf("slot", List.of()).forGetter(Cure::slots),
            ResourceKey.codec(InjuryRegistries.INJURY).listOf().optionalFieldOf("injuries", List.of()).forGetter(Cure::injuries),
            Codec.STRING.listOf().optionalFieldOf("categories", List.of()).forGetter(Cure::categories),
            Codec.intRange(1, 64).optionalFieldOf("amount", 1).forGetter(Cure::amount)
    ).apply(i, Cure::new));

    public boolean matches(ItemStack stack) { return item.test(stack); }

    public boolean fitsSlot(int slot) { return slots.isEmpty() || slots.contains(slot); }

    public boolean treats(ResourceKey<Injury> key, Injury injury) {
        return injuries.contains(key) || categories.contains(injury.category());
    }
}