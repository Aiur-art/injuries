package com.example.injuries;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * A treatment slot (numbered 0-7). Accepts an item if some {@link Cure} matches it and allows this slot. The instant a fitting item sits here while the target
 * has an injury that cure treats and that can be cured from this slot, the item is used up (one item per {@link Cure#amount()} injuries) - no button needed.
 * A stack of N items can cure up to N injuries.
 */
public class InjurySlot extends Slot {
    private final int slotNumber;
    private final HolderLookup.Provider registries;
    /** Null on the client; only the server actually cures anything. */
    private final ServerPlayer target;

    public InjurySlot(Container container, int index, int x, int y, int slotNumber, HolderLookup.Provider registries, ServerPlayer target) {
        super(container, index, x, y);
        this.slotNumber = slotNumber;
        this.registries = registries;
        this.target = target;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !Cures.matching(registries, stack, slotNumber).isEmpty();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (target == null || !target.isAlive()) return;

        ItemStack stack = getItem();
        while (!stack.isEmpty()) {
            List<Cure> cures = Cures.matching(registries, stack, slotNumber);
            Cure used = null;
            for (Cure c : cures) {
                if (!treatable(c).isEmpty()) { used = c; break; }
            }
            if (used == null) return; // sitting in the slot, nothing to cure (yet)

            int left = used.amount();
            for (ResourceKey<Injury> k : treatable(used)) {
                if (left-- <= 0) break;
                Injuries.remove(target, k);
            }
            stack.shrink(1);
            target.level().playSound(null, target.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    /** The target's active injuries that this cure treats, in the order they were gained. */
    private List<ResourceKey<Injury>> treatable(Cure cure) {
        return Injuries.active(target).stream().filter(k -> {
            Optional<Injury> def = Injuries.definition(registries, k);
            return def.isPresent() && def.get().curableIn(slotNumber) && cure.treats(k, def.get());
        }).toList();
    }
}
