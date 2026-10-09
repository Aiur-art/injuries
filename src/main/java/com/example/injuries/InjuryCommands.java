package com.example.injuries;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TreeSet;

/**
 * /injuries add   <targets> <injury>
 * /injuries cure  <targets> <injury>
 * /injuries cure  <targets> all
 * /injuries cure  <targets> random <amount>
 * /injuries clear <targets>
 * /injuries list  <targets>
 * /injuries group add    <targets> <group>   put players into a group (e.g. "robot")
 * /injuries group remove <targets> <group>
 * /injuries group clear  <targets>           take players out of every group
 * /injuries group list   [targets]           a player's groups, or (no target) every group the loaded datapacks use
 *
 * Groups decide WHO an injury can affect: an injury with "group": "robot" in its json only ever applies to players
 * an operator put into the "robot" group. Injuries without a "group" apply to everyone (unless "exclude_group").
 *
 * Every subcommand takes a normal player selector, so it works with /execute
 * (e.g. /execute as @a[distance=..5] run injuries add @s bleeding). Requires gamemaster permission.
 * <injury> is any injury from the datapack registry (injuries:bleeding, mypack:frostnip, ...), with tab-completion.
 */
public final class InjuryCommands {
    private InjuryCommands() {}

    private static final DynamicCommandExceptionType INVALID_GROUP = new DynamicCommandExceptionType(
            name -> Component.translatable("injuries.command.group.invalid", name));

    /** Suggests every group name that a loaded injury definition mentions. */
    private static final SuggestionProvider<CommandSourceStack> KNOWN_GROUPS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(knownGroups(ctx.getSource()), builder);

    private static TreeSet<String> knownGroups(CommandSourceStack source) {
        TreeSet<String> names = new TreeSet<>();
        source.registryAccess().lookupOrThrow(InjuryRegistries.INJURY).listElements().forEach(ref -> {
            names.addAll(ref.value().groups());
            names.addAll(ref.value().excludeGroups());
        });
        return names;
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("injuries")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

            var add = Commands.literal("add");
            var cure = Commands.literal("cure");
            add.then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("injury", ResourceArgument.resource(registryAccess, InjuryRegistries.INJURY))
                            .executes(ctx -> add(ctx, injuryArg(ctx)))));
            cure.then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("injury", ResourceArgument.resource(registryAccess, InjuryRegistries.INJURY))
                            .executes(ctx -> cure(ctx, injuryArg(ctx)))));
            // cure <targets> all  /  cure <targets> random <amount>
            cure.then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.literal("all").executes(InjuryCommands::clear))
                    .then(Commands.literal("random")
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                    .executes(InjuryCommands::cureRandom))));
            root.then(add).then(cure);

            var group = Commands.literal("group");
            group.then(Commands.literal("add")
                    .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("group", StringArgumentType.word()).suggests(KNOWN_GROUPS)
                                    .executes(InjuryCommands::groupAdd))));
            group.then(Commands.literal("remove")
                    .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("group", StringArgumentType.word()).suggests(KNOWN_GROUPS)
                                    .executes(InjuryCommands::groupRemove))));
            group.then(Commands.literal("clear")
                    .then(Commands.argument("targets", EntityArgument.players()).executes(InjuryCommands::groupClear)));
            group.then(Commands.literal("list")
                    .executes(InjuryCommands::groupListKnown)
                    .then(Commands.argument("targets", EntityArgument.players()).executes(InjuryCommands::groupList)));
            root.then(group);

            root.then(Commands.literal("clear")
                    .then(Commands.argument("targets", EntityArgument.players()).executes(InjuryCommands::clear)));
            root.then(Commands.literal("list")
                    .then(Commands.argument("targets", EntityArgument.players()).executes(InjuryCommands::list)));

            dispatcher.register(root);
        });
    }

    private static Holder.Reference<Injury> injuryArg(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return ResourceArgument.getResource(ctx, "injury", InjuryRegistries.INJURY);
    }

    private static int add(CommandContext<CommandSourceStack> ctx, Holder.Reference<Injury> inj) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int changed = 0;
        int wrongGroup = 0;
        for (ServerPlayer p : targets) {
            if (Injuries.has(p, inj.key())) continue;
            if (!Injuries.appliesTo(p, inj.value())) { wrongGroup++; continue; }
            if (Injuries.add(p, inj.key())) changed++;
        }
        if (changed == 0 && wrongGroup > 0) {
            ctx.getSource().sendFailure(Component.translatable("injuries.command.add.wrong_group", inj.value().name()));
            return 0;
        }
        return report(ctx, targets, changed, "add", inj);
    }

    private static int cure(CommandContext<CommandSourceStack> ctx, Holder.Reference<Injury> inj) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int changed = 0;
        for (ServerPlayer p : targets) {
            if (!Injuries.has(p, inj.key())) continue;
            Injuries.remove(p, inj.key());
            changed++;
        }
        return report(ctx, targets, changed, "cure", inj);
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int changed = 0;
        for (ServerPlayer p : targets) {
            if (Injuries.active(p).isEmpty()) continue;
            Injuries.clear(p);
            changed++;
        }
        if (changed == 0) {
            ctx.getSource().sendFailure(Component.translatable("injuries.command.nothing_changed"));
            return 0;
        }
        final int n = changed;
        Component msg = targets.size() == 1
                ? Component.translatable("injuries.command.clear.single", targets.iterator().next().getDisplayName())
                : Component.translatable("injuries.command.clear.multiple", n);
        ctx.getSource().sendSuccess(() -> msg, true);
        return changed;
    }

    private static int cureRandom(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        int players = 0;
        int cured = 0;
        for (ServerPlayer p : targets) {
            List<ResourceKey<Injury>> active = new ArrayList<>(Injuries.active(p));
            if (active.isEmpty()) continue;
            Collections.shuffle(active, new Random(p.getRandom().nextLong()));
            int n = Math.min(amount, active.size());
            for (int i = 0; i < n; i++) Injuries.remove(p, active.get(i));
            players++;
            cured += n;
        }
        if (cured == 0) {
            ctx.getSource().sendFailure(Component.translatable("injuries.command.nothing_changed"));
            return 0;
        }
        final int total = cured;
        Component msg = targets.size() == 1
                ? Component.translatable("injuries.command.cure_random.single", total, targets.iterator().next().getDisplayName())
                : Component.translatable("injuries.command.cure_random.multiple", total, players);
        ctx.getSource().sendSuccess(() -> msg, true);
        return cured;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer p : targets) {
            List<Component> names = new ArrayList<>();
            for (ResourceKey<Injury> k : Injuries.active(p)) {
                Injuries.definition(ctx.getSource().registryAccess(), k).ifPresent(def -> names.add(def.name()));
            }
            Component msg;
            if (names.isEmpty()) {
                msg = Component.translatable("injuries.command.list.none", p.getDisplayName());
            } else {
                MutableComponent joined = Component.empty();
                for (int i = 0; i < names.size(); i++) {
                    if (i > 0) joined.append(", ");
                    joined.append(names.get(i));
                }
                msg = Component.translatable("injuries.command.list.some", p.getDisplayName(), joined);
            }
            ctx.getSource().sendSuccess(() -> msg, false);
        }
        return targets.size();
    }

    // ------------------------------------------------------------------ groups

    private static String groupArg(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "group").toLowerCase(Locale.ROOT);
        if (!Groups.isValid(name)) throw INVALID_GROUP.create(name);
        return name;
    }

    private static int groupAdd(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String group = groupArg(ctx);
        int changed = 0;
        for (ServerPlayer p : targets) if (Injuries.addGroup(p, group)) changed++;
        return reportGroup(ctx, targets, changed, "add", group);
    }

    private static int groupRemove(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String group = groupArg(ctx);
        int changed = 0;
        for (ServerPlayer p : targets) if (Injuries.removeGroup(p, group)) changed++;
        return reportGroup(ctx, targets, changed, "remove", group);
    }

    private static int groupClear(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int changed = 0;
        for (ServerPlayer p : targets) if (Injuries.clearGroups(p)) changed++;
        if (changed == 0) {
            ctx.getSource().sendFailure(Component.translatable("injuries.command.nothing_changed"));
            return 0;
        }
        final int n = changed;
        Component msg = targets.size() == 1
                ? Component.translatable("injuries.command.group.clear.single", targets.iterator().next().getDisplayName())
                : Component.translatable("injuries.command.group.clear.multiple", n);
        ctx.getSource().sendSuccess(() -> msg, true);
        return changed;
    }

    private static int groupList(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer p : targets) {
            List<String> groups = Injuries.groups(p);
            Component msg = groups.isEmpty()
                    ? Component.translatable("injuries.command.group.list.none", p.getDisplayName())
                    : Component.translatable("injuries.command.group.list.some", p.getDisplayName(), String.join(", ", groups));
            ctx.getSource().sendSuccess(() -> msg, false);
        }
        return targets.size();
    }

    /** /injuries group list  (no target): every group the loaded injury definitions refer to. */
    private static int groupListKnown(CommandContext<CommandSourceStack> ctx) {
        TreeSet<String> names = knownGroups(ctx.getSource());
        Component msg = names.isEmpty()
                ? Component.translatable("injuries.command.group.known.none")
                : Component.translatable("injuries.command.group.known.some", String.join(", ", names));
        ctx.getSource().sendSuccess(() -> msg, false);
        return names.size();
    }

    private static int reportGroup(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                   int changed, String action, String group) {
        if (changed == 0) {
            ctx.getSource().sendFailure(Component.translatable("injuries.command.nothing_changed"));
            return 0;
        }
        Component msg = targets.size() == 1
                ? Component.translatable("injuries.command.group." + action + ".single", targets.iterator().next().getDisplayName(), group)
                : Component.translatable("injuries.command.group." + action + ".multiple", changed, group);
        ctx.getSource().sendSuccess(() -> msg, true);
        return changed;
    }

    private static int report(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                              int changed, String action, Holder.Reference<Injury> inj) {
        if (changed == 0) {
            ctx.getSource().sendFailure(Component.translatable("injuries.command.nothing_changed"));
            return 0;
        }
        Component name = inj.value().name();
        Component msg = targets.size() == 1
                ? Component.translatable("injuries.command." + action + ".single", name, targets.iterator().next().getDisplayName())
                : Component.translatable("injuries.command." + action + ".multiple", name, changed);
        ctx.getSource().sendSuccess(() -> msg, true);
        return changed;
    }
}