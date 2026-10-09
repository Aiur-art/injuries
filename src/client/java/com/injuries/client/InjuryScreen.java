package com.injuries.client;

import com.example.injuries.Injuries;
import com.example.injuries.Injury;
import com.example.injuries.InjuryMenu;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

public class InjuryScreen extends AbstractContainerScreen<InjuryMenu> {
    private static final int W = 176, H = 167;
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("injuries", "textures/gui/injury_screen.png");

    private static final Identifier[] SLOT_ICONS = new Identifier[InjuryMenu.INJURY_SLOT_COUNT];
    static {
        for (int i = 0; i < SLOT_ICONS.length; i++)
            SLOT_ICONS[i] = Identifier.fromNamespaceAndPath("injuries", "textures/gui/slot/slot_" + i + ".png");
    }

    public InjuryScreen(InjuryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.empty(), W, H);
    }

    // Suppresses the default "<title>" / "Inventory" labels the base class would otherwise draw.
    // NOTE: guessing the name/signature here - if this doesn't compile, Ctrl+Click into
    // AbstractContainerScreen in your IDE, find the method that draws playerInventoryTitle /
    // the "Inventory" text, and send me its real name + signature.
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // intentionally empty - no title, no "Inventory" label
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = leftPos, y = topPos;

        // Same dim-the-world-behind-you effect as the vanilla inventory screen.
        g.fill(0, 0, this.width, this.height, 0x75000000);

        // The whole panel - border, model box, crafting grid, arrow, output slot and
        // inventory grid - is baked into this one texture now.
        g.blit(TEXTURE, x, y, x + W, y + H, 0.0F, 1.0F, 0.0F, 1.0F);

        // --- the live 3D model, drawn into the black box baked into the texture ---
        Entity e = this.minecraft.level == null ? null : this.minecraft.level.getEntity(menu.targetEntityId());
        if (e instanceof LivingEntity living) {
            int x1 = x + InjuryMenu.MODEL_X, y1 = y + InjuryMenu.MODEL_Y;
            int x2 = x1 + InjuryMenu.MODEL_W, y2 = y1 + InjuryMenu.MODEL_H;
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    g, x1, y1, x2, y2, 30, 0.0F, (float) mouseX, (float) mouseY, living);
        }

        // --- slot icons: assets/injuries/textures/gui/slot/slot_<n>.png (16x16), hidden once an item is in the slot ---
        for (int slot = 0; slot < InjuryMenu.INJURY_SLOT_COUNT; slot++) {
            int sx = x + InjuryMenu.injurySlotX(slot), sy = y + InjuryMenu.injurySlotY(slot);
            if (!menu.slots.get(slot).hasItem()) {
                g.blit(SLOT_ICONS[slot], sx, sy, sx + 16, sy + 16, 0.0F, 1.0F, 0.0F, 1.0F);
            }
        }

        if (e instanceof Player target) {
            // health, above the crafting grid
            Component hp = Component.literal(String.format("\u2764 %.1f", target.getHealth()));
            g.text(this.font, hp, x + InjuryMenu.CRAFT_X, y + 6, 0xFFAA0000, false);

            // tint the slots that can cure one of the target's injuries
            for (ResourceKey<Injury> k : Injuries.active(target)) {
                Optional<Injury> def = Injuries.definition(this.minecraft.level.registryAccess(), k);
                if (def.isEmpty()) continue;
                for (int slot = 0; slot < InjuryMenu.INJURY_SLOT_COUNT; slot++) {
                    if (!def.get().highlights(slot)) continue;
                    int sx = x + InjuryMenu.injurySlotX(slot), sy = y + InjuryMenu.injurySlotY(slot);
                    g.fill(sx, sy, sx + 16, sy + 16, 0x66CC2222);
                }
            }

            // active injuries, listed beside the panel (names and colours come from the datapack definitions)
            int lx = x + W + 6, ly = y + 6;
            List<ResourceKey<Injury>> active = Injuries.active(target);
            if (active.isEmpty()) {
                g.text(this.font, Component.translatable("injuries.screen.healthy"), lx, ly, 0xFF55FF55, true);
            }
            for (ResourceKey<Injury> k : active) {
                Optional<Injury> def = Injuries.definition(this.minecraft.level.registryAccess(), k);
                if (def.isEmpty()) continue;
                g.text(this.font, def.get().name(), lx, ly, 0xFF000000 | def.get().color(), true);
                ly += 10;
            }
        }
    }
}
