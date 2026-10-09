package com.example.injuries;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class InjuriesMod implements ModInitializer {
    public static final String MOD_ID = "injuries";
    private static final double REACH = 4.5;

    @Override
    public void onInitialize() {
        ModRegistry.init();
        ModEffects.init();
        InjuryRegistries.init();
        Injuries.init();
        InjuryCommands.register();

        PayloadTypeRegistry.serverboundPlay().register(OpenPayload.TYPE, OpenPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(OpenPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerPlayer target = player;

            if (payload.other()) {
                target = findTarget(player);
                if (target == null) {
                    player.sendSystemMessage(Component.translatable("injuries.msg.no_target"), true);
                    return;
                }
            }

            final ServerPlayer t = target;
            Component title = t == player
                    ? Component.translatable("injuries.title.self")
                    : Component.translatable("injuries.title.other", t.getDisplayName());
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new InjuryMenu(id, inv, t), title));
        });
    }

    /** The closest other player the given player is looking at. */
    private static ServerPlayer findTarget(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(REACH));
        AABB search = p.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);

        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer other : p.level().getEntitiesOfClass(ServerPlayer.class, search, e -> e != p && !e.isSpectator())) {
            Optional<Vec3> hit = other.getBoundingBox().inflate(0.15).clip(eye, end);
            if (hit.isEmpty() || !p.hasLineOfSight(other)) continue;
            double d = eye.distanceToSqr(hit.get());
            if (d < bestDist) { bestDist = d; best = other; }
        }
        return best;
    }
}
