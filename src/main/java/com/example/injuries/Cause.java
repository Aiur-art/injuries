package com.example.injuries;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One way a hit can cause an injury. All present conditions must hold (AND), except that
 * {@code any_source} makes "damage_types OR attacker" enough when both are given.
 *
 * @param damageTypes        damage type id, list of ids, or "#tag"
 * @param excludeDamageTypes never matches these damage types
 * @param attacker           vanilla entity predicate for the attacking entity (must exist); {} = "any attacker"
 * @param anySource          when both damage_types and attacker are set, either one is enough
 * @param victim             vanilla entity predicate for the player who was hit (location, dimension, effects, stepping_on...)
 * @param minDamage          damage actually taken must be at least this
 * @param chance             base probability (1 = always)
 * @param chancePerDamage    extra probability per point of damage taken
 * @param unlessGranted      skipped if one of these injuries was already granted by this same hit ("else if")
 */
public record Cause(Optional<HolderSet<DamageType>> damageTypes, Optional<HolderSet<DamageType>> excludeDamageTypes,
                    Optional<EntityPredicate> attacker, boolean anySource, Optional<EntityPredicate> victim,
                    float minDamage, float chance, float chancePerDamage, List<ResourceKey<Injury>> unlessGranted) {

    public static final Codec<Cause> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.DAMAGE_TYPE).optionalFieldOf("damage_types").forGetter(Cause::damageTypes),
            RegistryCodecs.homogeneousList(Registries.DAMAGE_TYPE).optionalFieldOf("exclude_damage_types").forGetter(Cause::excludeDamageTypes),
            EntityPredicate.CODEC.optionalFieldOf("attacker").forGetter(Cause::attacker),
            Codec.BOOL.optionalFieldOf("any_source", false).forGetter(Cause::anySource),
            EntityPredicate.CODEC.optionalFieldOf("victim").forGetter(Cause::victim),
            Codec.floatRange(0f, Float.MAX_VALUE).optionalFieldOf("min_damage", 0f).forGetter(Cause::minDamage),
            Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1f).forGetter(Cause::chance),
            Codec.floatRange(0f, 1f).optionalFieldOf("chance_per_damage", 0f).forGetter(Cause::chancePerDamage),
            ResourceKey.codec(InjuryRegistries.INJURY).listOf().optionalFieldOf("unless_granted", List.of()).forGetter(Cause::unlessGranted)
    ).apply(i, Cause::new));

    public boolean matches(ServerPlayer p, DamageSource src, float damage, Set<ResourceKey<Injury>> granted) {
        if (damage < minDamage) return false;
        for (ResourceKey<Injury> k : unlessGranted) if (granted.contains(k)) return false;
        if (excludeDamageTypes.isPresent() && excludeDamageTypes.get().contains(src.typeHolder())) return false;

        ServerLevel level = (ServerLevel) p.level();
        boolean typeSet = damageTypes.isPresent();
        boolean attSet = attacker.isPresent();
        boolean typeOk = !typeSet || damageTypes.get().contains(src.typeHolder());
        boolean attOk = !attSet || attacker.get().matches(level, p.position(), src.getEntity());
        if (typeSet && attSet && anySource) {
            if (!(typeOk || attOk)) return false;
        } else if (!typeOk || !attOk) {
            return false;
        }
        return victim.isEmpty() || victim.get().matches(level, p.position(), p);
    }

    public boolean roll(ServerPlayer p, float damage) {
        float prob = chance + chancePerDamage * damage;
        return prob >= 1f || p.getRandom().nextFloat() < prob;
    }
}