package com.example.injuries;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;

import java.util.List;

/**
 * The treatment slots of the injury screen. Slot numbers 0..7 are the 8 positions in the panel
 * (left to right, top to bottom). Their icons are assets/injuries/textures/gui/slot/slot_<n>.png (16x16).
 * In JSON a "slot" field is either one number or a list of numbers.
 */
public final class Slots {
    private Slots() {}

    public static final int COUNT = 8;

    public static final Codec<List<Integer>> CODEC = Codec.either(
            Codec.intRange(0, COUNT - 1),
            Codec.intRange(0, COUNT - 1).listOf()
    ).xmap(
            e -> e.map(v -> List.of(v), l -> List.copyOf(l)),
            l -> l.size() == 1 ? Either.left(l.get(0)) : Either.right(l)
    );
}
