package com.example.injuries;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One way eating/drinking an item can cause an injury.
 *
 * @param items  item id, list of ids, or "#tag"
 * @param chance probability (0..1) that finishing the item gives the injury
 */
public record EatCause(HolderSet<Item> items, float chance) {

    public static final Codec<EatCause> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("items").forGetter(EatCause::items),
            Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1f).forGetter(EatCause::chance)
    ).apply(i, EatCause::new));

    public boolean matches(ItemStack eaten) {
        return eaten.is(items);
    }

    public boolean roll(ServerPlayer p) {
        return chance >= 1f || p.getRandom().nextFloat() < chance;
    }
}
