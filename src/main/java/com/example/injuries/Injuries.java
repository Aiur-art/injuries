package com.example.injuries;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Runtime for the data-driven injuries. Everything gameplay-related (causes, penalties, periodic effects)
 * comes from {@link Injury} definitions; this class only stores who is injured and applies the definitions.
 */
public final class Injuries {
    private Injuries() {}

    /** Active injuries per player: persisted, and synced to clients so screens can read other players' injuries. */
    public static final AttachmentType<List<ResourceKey<Injury>>> ACTIVE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(InjuriesMod.MOD_ID, "active_injuries"),
            b -> b.persistent(ResourceKey.codec(InjuryRegistries.INJURY).listOf())
                    .initializer(List::of)
                    .syncWith(ResourceKey.streamCodec(InjuryRegistries.INJURY).apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));

    /** Injury groups a player belongs to (set with /injuries group ...). Persisted and kept across death. */
    public static final AttachmentType<List<String>> GROUPS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(InjuriesMod.MOD_ID, "groups"),
            b -> b.persistent(Groups.NAME_CODEC.listOf())
                    .initializer(List::of)
                    .copyOnDeath());

    /** Pre-datapack saves stored a bitmask under this id; migrated on join, then removed. */
    private static final AttachmentType<Integer> LEGACY_MASK = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(InjuriesMod.MOD_ID, "injuries"),
            b -> b.persistent(Codec.INT).initializer(() -> 0));
    /** The old enum order (bit = ordinal). Never reorder. */
    private static final String[] LEGACY_ORDER = {"bleeding", "broken_arm", "broken_leg", "concussion", "infection", "burn",
            "poisoned", "fractured_rib", "magic_curse", "laceration", "sprained_ankle", "dislocated_shoulder", "vertigo",
            "fever", "frostbite", "wither_rot", "punctured_lung", "hex", "soul_burn", "cursed_wound", "compound_fracture",
            "heatstroke", "sepsis", "head_wound", "helminth"};

    // ------------------------------------------------------------------ lookup helpers

    public static List<ResourceKey<Injury>> active(Player p) { return p.getAttachedOrElse(ACTIVE, List.of()); }

    public static boolean has(Player p, ResourceKey<Injury> k) { return active(p).contains(k); }

    public static List<String> groups(Player p) { return p.getAttachedOrElse(GROUPS, List.of()); }

    /** Can this injury affect this player, given the groups they are in? */
    public static boolean appliesTo(Player p, Injury injury) { return injury.appliesToGroups(groups(p)); }

    public static boolean addGroup(Player p, String group) {
        List<String> cur = groups(p);
        if (cur.contains(group)) return false;
        List<String> next = new ArrayList<>(cur);
        next.add(group);
        p.setAttached(GROUPS, List.copyOf(next));
        return true;
    }

    public static boolean removeGroup(Player p, String group) {
        List<String> cur = groups(p);
        if (!cur.contains(group)) return false;
        List<String> next = new ArrayList<>(cur);
        next.remove(group);
        p.setAttached(GROUPS, List.copyOf(next));
        return true;
    }

    public static boolean clearGroups(Player p) {
        if (groups(p).isEmpty()) return false;
        p.setAttached(GROUPS, List.of());
        return true;
    }

    public static Optional<Injury> definition(HolderLookup.Provider registries, ResourceKey<Injury> k) {
        return registries.lookupOrThrow(InjuryRegistries.INJURY).get(k).map(Holder.Reference::value);
    }

    /** Every place that turns a key into an Identifier goes through here. */
    static Identifier idOf(ResourceKey<?> key) { return key.identifier(); }

    private static Identifier modifierId(ResourceKey<Injury> k, int index) {
        Identifier id = idOf(k);
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "injury/" + id.getPath() + "/" + index);
    }

    // ------------------------------------------------------------------ add / remove

    public static boolean add(ServerPlayer p, ResourceKey<Injury> k) {
        Optional<Injury> def = definition(p.level().registryAccess(), k);
        if (def.isEmpty() || has(p, k) || !appliesTo(p, def.get())) return false;
        List<ResourceKey<Injury>> next = new ArrayList<>(active(p));
        next.add(k);
        p.setAttached(ACTIVE, List.copyOf(next));
        refresh(p);
        p.sendSystemMessage(Component.translatable("injuries.msg.gained", def.get().name()).withStyle(ChatFormatting.RED), true);
        return true;
    }

    public static boolean remove(Player p, ResourceKey<Injury> k) {
        if (!has(p, k)) return false;
        List<ResourceKey<Injury>> next = new ArrayList<>(active(p));
        next.remove(k);
        p.setAttached(ACTIVE, List.copyOf(next));
        if (p instanceof ServerPlayer sp) refresh(sp);
        return true;
    }

    public static void clear(Player p) {
        p.setAttached(ACTIVE, List.of());
        if (p instanceof ServerPlayer sp) refresh(sp);
    }

    // ------------------------------------------------------------------ applying definitions

    /**
     * Brings attribute modifiers and the HUD effects in line with the active injuries: applies what is missing,
     * removes what is stale (cured injuries, milk, /effect clear, a changed datapack).
     */
    static void refresh(ServerPlayer p) {
        HolderLookup.RegistryLookup<Injury> registry = p.level().registryAccess().lookupOrThrow(InjuryRegistries.INJURY);
        List<ResourceKey<Injury>> active = active(p);
        Set<Holder<MobEffect>> wantedHud = new HashSet<>();

        registry.listElements().forEach(ref -> {
            ResourceKey<Injury> key = ref.key();
            Injury inj = ref.value();
            boolean on = active.contains(key);
            for (int i = 0; i < inj.attributes().size(); i++) {
                Injury.Penalty pen = inj.attributes().get(i);
                AttributeInstance inst = p.getAttribute(pen.attribute());
                if (inst == null) continue;
                Identifier mid = modifierId(key, i);
                if (on) {
                    AttributeModifier mod = new AttributeModifier(mid, pen.amount(), pen.operation());
                    if (!mod.equals(inst.getModifier(mid))) inst.addOrUpdateTransientModifier(mod);
                } else if (inst.getModifier(mid) != null) {
                    inst.removeModifier(mid);
                }
            }
            if (on) inj.hudEffect().ifPresent(wantedHud::add);
        });

        registry.listElements().forEach(ref -> ref.value().hudEffect().ifPresent(h -> {
            boolean has = p.hasEffect(h);
            if (wantedHud.contains(h)) {
                // infinite duration, no particles, icon visible
                if (!has) p.addEffect(new MobEffectInstance(h, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
            } else if (has) {
                p.removeEffect(h);
            }
        }));
    }

    private static void migrateLegacy(ServerPlayer p) {
        int mask = p.getAttachedOrElse(LEGACY_MASK, 0);
        if (mask == 0) return;
        List<ResourceKey<Injury>> next = new ArrayList<>(active(p));
        for (int i = 0; i < LEGACY_ORDER.length; i++) {
            if ((mask & (1 << i)) == 0) continue;
            ResourceKey<Injury> k = ResourceKey.create(InjuryRegistries.INJURY, Identifier.fromNamespaceAndPath(InjuriesMod.MOD_ID, LEGACY_ORDER[i]));
            if (!next.contains(k)) next.add(k);
        }
        p.setAttached(ACTIVE, List.copyOf(next));
        p.removeAttached(LEGACY_MASK);
    }

    /** What each player was eating at the end of the previous tick (to notice when an item gets finished). */
    private record Using(ItemStack stack, int remaining) {}
    private static final Map<UUID, Using> USING = new HashMap<>();

    /** Called when a player has just finished eating/drinking {@code eaten}: ask every injury if it causes one. */
    private static void onFinishedEating(ServerPlayer p, ItemStack eaten) {
        List<Holder.Reference<Injury>> all = new ArrayList<>();
        p.level().registryAccess().lookupOrThrow(InjuryRegistries.INJURY).listElements().forEach(all::add);
        all.sort(Comparator.<Holder.Reference<Injury>>comparingInt(r -> r.value().priority())
                .thenComparing(r -> idOf(r.key()).toString()));
        for (Holder.Reference<Injury> ref : all) {
            if (ref.value().rollEat(p, eaten)) add(p, ref.key());
        }
    }

    static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            migrateLegacy(handler.getPlayer());
            refresh(handler.getPlayer());
        });

        // --- Getting hurt: ask every injury definition whether this hit causes it ---
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (!(entity instanceof ServerPlayer p) || blocked || damageTaken <= 0) return;

            List<Holder.Reference<Injury>> all = new ArrayList<>();
            p.level().registryAccess().lookupOrThrow(InjuryRegistries.INJURY).listElements().forEach(all::add);
            all.sort(Comparator.<Holder.Reference<Injury>>comparingInt(r -> r.value().priority())
                    .thenComparing(r -> idOf(r.key()).toString()));

            Set<ResourceKey<Injury>> granted = new HashSet<>();
            for (Holder.Reference<Injury> ref : all) {
                if (ref.value().rollCauses(p, source, damageTaken, granted)) {
                    granted.add(ref.key());
                    add(p, ref.key());
                }
            }
        });

        // --- Living with injuries ---
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> USING.remove(handler.getPlayer().getUUID()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            HolderLookup.RegistryLookup<Injury> registry = server.registryAccess().lookupOrThrow(InjuryRegistries.INJURY);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!p.isAlive() || p.isSpectator()) continue;

                // Eating: the use ends on the tick the countdown reaches 0, i.e. last tick we saw remaining == 1.
                Using prev = USING.get(p.getUUID());
                if (p.isUsingItem()) {
                    USING.put(p.getUUID(), new Using(p.getUseItem().copy(), p.getUseItemRemainingTicks()));
                } else if (prev != null) {
                    USING.remove(p.getUUID());
                    if (prev.remaining() <= 1) onFinishedEating(p, prev.stack());
                }

                if (p.tickCount % 20 == 0) refresh(p); // safety net, also picks up /reload changes

                for (ResourceKey<Injury> key : active(p)) {
                    Optional<Holder.Reference<Injury>> ref = registry.get(key);
                    if (ref.isEmpty()) continue;
                    for (Injury.TickAction a : ref.get().value().tick()) {
                        a.run(p);
                        if (!p.isAlive()) break;
                    }
                    if (!p.isAlive()) break;
                }
            }
        });
    }
}