package com.injuries.client;

import com.example.injuries.InjuriesMod;
import com.example.injuries.ModRegistry;
import com.example.injuries.OpenPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class InjuriesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModRegistry.INJURY_MENU, InjuryScreen::new);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(InjuriesMod.MOD_ID, "main"));
        KeyMapping openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.injuries.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) {
                if (mc.player == null || mc.gui.screen() != null) continue;
                // Crouching + key = examine the player in front of you
                ClientPlayNetworking.send(new OpenPayload(mc.player.isShiftKeyDown()));
            }
        });
    }
}
