package com.example.injuries;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class InjuryMenu extends AbstractContainerMenu {
    // ---- layout, in pixels relative to the panel's top-left corner (measured from the texture) ----
    public static final int MODEL_X = 8, MODEL_Y = 8, MODEL_W = 49, MODEL_H = 70;
    private static final int[] INJURY_COLS = {59, 77};
    private static final int[] INJURY_ROWS = {8, 26, 44, 62};
    public static final int MAX_INJURY_SLOTS = INJURY_COLS.length * INJURY_ROWS.length; // 8 == Slots.COUNT

    /**
     * Eight treatment slots, numbered 0-7 (left to right, top to bottom). Which items a slot takes and which injuries
     * it cures is decided by the "slot" field of the cure and injury definitions (see Slots).
     */
    public static final int INJURY_SLOT_COUNT = MAX_INJURY_SLOTS;
    public static int injurySlotX(int i) { return INJURY_COLS[i % INJURY_COLS.length]; }
    public static int injurySlotY(int i) { return INJURY_ROWS[i / INJURY_COLS.length]; }
    public static final int CRAFT_X = 98, CRAFT_Y = 18;
    public static final int RESULT_X = 154, RESULT_Y = 28;
    public static final int INV_X = 8, INV_Y = 84;

    private final ContainerData data;
    private final Inventory playerInv;
    /** Only set on the server: the player whose body this menu is showing/treating. */
    private final ServerPlayer target;

    private final Container[] injuryContainers = new Container[INJURY_SLOT_COUNT];
    private final CraftingContainer craft = new TransientCraftingContainer(this, 2, 2);
    private final ResultContainer result = new ResultContainer();

    /** Client constructor, used by MenuType when the server tells the client to open this screen. */
    public InjuryMenu(int id, Inventory inv) {
        this(id, inv, null, new SimpleContainerData(1));
    }

    /** Server constructor. */
    public InjuryMenu(int id, Inventory inv, ServerPlayer target) {
        this(id, inv, target, new ContainerData() {
            @Override public int get(int i) { return i == 0 ? target.getId() : 0; }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return 1; }
        });
    }

    private InjuryMenu(int id, Inventory inv, ServerPlayer target, ContainerData data) {
        super(ModRegistry.INJURY_MENU, id);
        this.target = target;
        this.playerInv = inv;
        this.data = data;
        addDataSlots(data);

        for (int i = 0; i < INJURY_SLOT_COUNT; i++) {
            SimpleContainer c = new SimpleContainer(1);
            injuryContainers[i] = c;
            addSlot(new InjurySlot(c, 0, injurySlotX(i), injurySlotY(i), i, inv.player.level().registryAccess(), target));
        }

        // Vanilla 2x2 crafting grid, wired up exactly like the one in the regular inventory screen.
        for (int cy = 0; cy < 2; cy++) {
            for (int cx = 0; cx < 2; cx++) {
                addSlot(new Slot(craft, cx + cy * 2, CRAFT_X + cx * 18, CRAFT_Y + cy * 18));
            }
        }
        addSlot(new ResultSlot(inv.player, craft, result, 0, RESULT_X, RESULT_Y));

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
    }

    @Override
    public void slotsChanged(Container c) {
        super.slotsChanged(c);
        if (c == craft) updateCraftingResult();
    }

    private void updateCraftingResult() {
        Level level = playerInv.player.level();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        net.minecraft.world.item.crafting.CraftingInput input = craft.asCraftInput();
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> match =
                serverLevel.recipeAccess().getRecipeFor(
                        net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, serverLevel);

        if (!input.isEmpty() && match.isPresent()) {
            ItemStack output = match.get().value().assemble(input);
            result.setItem(0, output);
        } else {
            result.setItem(0, ItemStack.EMPTY);
        }
        broadcastChanges();
    }

    // ---- read-only view for the screen (injuries and health are read from the synced target entity) ----
    public int targetEntityId() { return data.get(0); }

    @Override
    public boolean stillValid(Player player) {
        if (target == null) return true; // client side: server decides, this is just a stub
        return target.isAlive() && (target == player || player.distanceToSqr(target) < 36.0);
    }

    private static final int INJURY_COUNT = INJURY_SLOT_COUNT;
    private static final int CRAFT_FIRST = INJURY_COUNT, CRAFT_LAST = INJURY_COUNT + 3,
            RESULT_INDEX = INJURY_COUNT + 4, INV_FIRST = INJURY_COUNT + 5, INV_LAST = INJURY_COUNT + 41;

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == RESULT_INDEX) {
            if (!moveItemStackTo(stack, INV_FIRST, INV_LAST + 1, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, copy);
        } else if (index < INJURY_COUNT) { // an injury slot
            if (!moveItemStackTo(stack, INV_FIRST, INV_LAST + 1, true)) return ItemStack.EMPTY;
        } else if (index >= CRAFT_FIRST && index <= CRAFT_LAST) {
            if (!moveItemStackTo(stack, INV_FIRST, INV_LAST + 1, true)) return ItemStack.EMPTY;
        } else if (Cures.isMedicine(playerInv.player.level().registryAccess(), stack)) {
            boolean placedInInjurySlot = false;
            for (int s = 0; s < INJURY_COUNT; s++) {
                if (slots.get(s).mayPlace(stack) && moveItemStackTo(stack, s, s + 1, false)) {
                    placedInInjurySlot = true;
                    break;
                }
            }
            if (!placedInInjurySlot && !moveItemStackTo(stack, INV_FIRST, INV_LAST + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, INV_FIRST, INV_LAST + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, craft);
        result.removeItemNoUpdate(0);
        for (Container c : injuryContainers) clearContainer(player, c);
    }
}