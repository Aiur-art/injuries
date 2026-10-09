package com.example.injuries;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class Cures {
    private Cures() {}

    public static List<Cure> matching(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        return registries.lookupOrThrow(InjuryRegistries.CURE).listElements()
                .map(Holder.Reference::value).filter(c -> c.matches(stack)).toList();
    }

    /** Cures that match the stack AND allow it in this treatment slot. */
    public static List<Cure> matching(HolderLookup.Provider registries, ItemStack stack, int slot) {
        return matching(registries, stack).stream().filter(c -> c.fitsSlot(slot)).toList();
    }

    /** True if any cure definition accepts this item in some slot (used for shift-click routing). */
    public static boolean isMedicine(HolderLookup.Provider registries, ItemStack stack) {
        return !matching(registries, stack).isEmpty();
    }
}
