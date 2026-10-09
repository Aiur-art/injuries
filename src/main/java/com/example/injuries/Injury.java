package com.example.injuries;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One injury, loaded from data/&lt;ns&gt;/injuries/injury/&lt;name&gt;.json.
 *
 * @param name       display name (any text component)
 * @param category   free-form tag a cure can target ("bodily", "magical", or your own)
 * @param slots      treatment slot(s) (0-7) this injury is cured from; the first is its home slot, more means
 *                   "either/or" (any of them works). Empty = curable from any slot
 * @param color      colour used for the name in the injury screen ("#RRGGBB")
 * @param priority   causes of lower-priority injuries are rolled first (needed for "unless_granted")
 * @param hudEffect  optional INERT mob effect shown in the HUD while injured (must be a registered effect)
 * @param attributes attribute penalties applied while injured
 * @param tick       periodic actions while injured (damage, effects, spreading to other injuries...)
 * @param causes     how a hit can give this injury
 * @param onEat      food/drink items that can give this injury
 * @param groups     player groups this injury applies to (empty = everyone)
 * @param excludeGroups player groups this injury never applies to
 */
public record Injury(Component name, String category, List<Integer> slots, int color, int priority, Optional<Holder<MobEffect>> hudEffect,
                     List<Penalty> attributes, List<TickAction> tick, List<Cause> causes, List<EatCause> onEat,
                     List<String> groups, List<String> excludeGroups) {

    /** "#RRGGBB" string (preferred), or vanilla's int / [r,g,b] forms as a fallback. */
    private static final Codec<Integer> HEX_COLOR_CODEC = Codec.STRING.comapFlatMap(str -> {
        String h = str.startsWith("#") ? str.substring(1) : str;
        if (h.length() != 6) return DataResult.error(() -> "Expected #RRGGBB, got: " + str);
        try {
            return DataResult.success(Integer.parseInt(h, 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a hex colour: " + str);
        }
    }, c -> String.format("#%06X", c & 0xFFFFFF));

    public static final Codec<Integer> COLOR_CODEC = Codec.withAlternative(HEX_COLOR_CODEC, ExtraCodecs.RGB_COLOR_CODEC);

    public static final Codec<Injury> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(Injury::name),
            Codec.STRING.optionalFieldOf("category", "bodily").forGetter(Injury::category),
            Slots.CODEC.optionalFieldOf("slot", List.of()).forGetter(Injury::slots),
            COLOR_CODEC.optionalFieldOf("color", 0xAA0000).forGetter(Injury::color),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(Injury::priority),
            BuiltInRegistries.MOB_EFFECT.holderByNameCodec().optionalFieldOf("hud_effect").forGetter(Injury::hudEffect),
            Penalty.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(Injury::attributes),
            TickAction.CODEC.listOf().optionalFieldOf("tick", List.of()).forGetter(Injury::tick),
            Cause.CODEC.listOf().optionalFieldOf("causes", List.of()).forGetter(Injury::causes),
            EatCause.CODEC.listOf().optionalFieldOf("on_eat", List.of()).forGetter(Injury::onEat),
            Groups.CODEC.optionalFieldOf("group", List.of()).forGetter(Injury::groups),
            Groups.CODEC.optionalFieldOf("exclude_group", List.of()).forGetter(Injury::excludeGroups)
    ).apply(i, Injury::new));

    /** Can this injury be cured from the given treatment slot? */
    public boolean curableIn(int slot) { return slots.isEmpty() || slots.contains(slot); }

    /**
     * Can this injury affect a player who is in the given groups? Not if they are in any excluded group;
     * and if the injury names groups, they must be in at least one of them.
     */
    public boolean appliesToGroups(List<String> playerGroups) {
        for (String g : excludeGroups) if (playerGroups.contains(g)) return false;
        if (groups.isEmpty()) return true;
        for (String g : groups) if (playerGroups.contains(g)) return true;
        return false;
    }

    /** Is the given slot the one shown highlighted for this injury? (injuries without a slot highlight nothing) */
    public boolean highlights(int slot) { return slots.contains(slot); }

    /**
     * Rolls this injury for one hit. Causes are checked in order and the FIRST one whose conditions match is
     * rolled (once); later causes are ignored. So "damage >= 6 always, else damage >= 3 half the time" is two causes.
     */
    public boolean rollCauses(ServerPlayer victim, DamageSource source, float damage, Set<ResourceKey<Injury>> grantedThisHit) {
        for (Cause c : causes) {
            if (!c.matches(victim, source, damage, grantedThisHit)) continue;
            return c.roll(victim, damage);
        }
        return false;
    }

    /** Rolls this injury for one finished food/drink item: the first matching "on_eat" entry is rolled. */
    public boolean rollEat(ServerPlayer victim, ItemStack eaten) {
        for (EatCause c : onEat) {
            if (!c.matches(eaten)) continue;
            return c.roll(victim);
        }
        return false;
    }

    /** A permanent attribute modifier while injured. */
    public record Penalty(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        public static final Codec<Penalty> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(Penalty::attribute),
                Codec.DOUBLE.fieldOf("amount").forGetter(Penalty::amount),
                AttributeModifier.Operation.CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE).forGetter(Penalty::operation)
        ).apply(i, Penalty::new));
    }

    /** A hidden (no icon, no particles) vanilla effect applied by a tick action. */
    public record Pulse(Holder<MobEffect> effect, int duration, int amplifier) {
        public static final Codec<Pulse> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(Pulse::effect),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("duration", 60).forGetter(Pulse::duration),
                Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(Pulse::amplifier)
        ).apply(i, Pulse::new));

        MobEffectInstance instance() { return new MobEffectInstance(effect, duration, amplifier, false, false, false); }
    }

    /**
     * Runs every {@code interval} ticks while the player has the injury, if all conditions hold and the chance succeeds.
     *
     * @param requires   other injuries the player must ALSO have
     * @param excludes   injuries the player must NOT have
     * @param underwater only while the player is underwater
     * @param damage     generic damage dealt (0 = none)
     * @param effects    hidden effects applied
     * @param grant      injuries given
     * @param airLoss    extra air removed from the air supply
     */
    public record TickAction(int interval, float chance, List<ResourceKey<Injury>> requires, List<ResourceKey<Injury>> excludes,
                             boolean underwater, float damage, List<Pulse> effects, List<ResourceKey<Injury>> grant, int airLoss) {
        public static final Codec<TickAction> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("interval", 1).forGetter(TickAction::interval),
                Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1f).forGetter(TickAction::chance),
                ResourceKey.codec(InjuryRegistries.INJURY).listOf().optionalFieldOf("requires", List.of()).forGetter(TickAction::requires),
                ResourceKey.codec(InjuryRegistries.INJURY).listOf().optionalFieldOf("excludes", List.of()).forGetter(TickAction::excludes),
                Codec.BOOL.optionalFieldOf("underwater", false).forGetter(TickAction::underwater),
                Codec.floatRange(0f, Float.MAX_VALUE).optionalFieldOf("damage", 0f).forGetter(TickAction::damage),
                Pulse.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(TickAction::effects),
                ResourceKey.codec(InjuryRegistries.INJURY).listOf().optionalFieldOf("grant", List.of()).forGetter(TickAction::grant),
                Codec.intRange(0, 300).optionalFieldOf("air_loss", 0).forGetter(TickAction::airLoss)
        ).apply(i, TickAction::new));

        void run(ServerPlayer p) {
            if (p.tickCount % interval != 0) return;
            for (ResourceKey<Injury> k : requires) if (!Injuries.has(p, k)) return;
            for (ResourceKey<Injury> k : excludes) if (Injuries.has(p, k)) return;
            if (underwater && !p.isUnderWater()) return;
            if (chance < 1f && p.getRandom().nextFloat() >= chance) return;

            for (Pulse e : effects) p.addEffect(e.instance());
            if (airLoss > 0) p.setAirSupply(p.getAirSupply() - airLoss);
            for (ResourceKey<Injury> k : grant) Injuries.add(p, k);
            if (damage > 0f) p.hurtServer((ServerLevel) p.level(), p.damageSources().generic(), damage);
        }
    }
}