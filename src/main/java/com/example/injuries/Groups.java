package com.example.injuries;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Injury groups. A group is just a name (for example "robot") that an operator puts players into with
 * {@code /injuries group add <players> <name>}. An injury definition can then say which groups it applies to
 * ({@code "group"}) or which groups it never applies to ({@code "exclude_group"}).
 *
 * In JSON both fields are either one string or a list of strings, same as the "slot" field (see {@link Slots}).
 * Names are lowercase letters, digits and _ . - only, so they are safe in commands and in JSON.
 */
public final class Groups {
    private Groups() {}

    public static final Pattern VALID = Pattern.compile("[a-z0-9_.\\-]+");

    public static boolean isValid(String name) { return VALID.matcher(name).matches(); }

    /** One group name. */
    public static final Codec<String> NAME_CODEC = Codec.STRING.validate(s -> isValid(s)
            ? DataResult.success(s)
            : DataResult.error(() -> "Invalid injury group '" + s + "' (allowed: a-z 0-9 _ . -)"));

    /** "robot" or ["robot", "cyborg"]. */
    public static final Codec<List<String>> CODEC = Codec.either(NAME_CODEC, NAME_CODEC.listOf()).xmap(
            e -> e.map(v -> List.of(v), l -> List.copyOf(l)),
            l -> l.size() == 1 ? Either.left(l.get(0)) : Either.right(l)
    );
}