package com.example.injuries;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "open the injury screen". other=true means examine the player I'm looking at. */
public record OpenPayload(boolean other) implements CustomPacketPayload {
    public static final Type<OpenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(InjuriesMod.MOD_ID, "open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, OpenPayload::other, OpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
