package org.crafterscr.craftersgacha.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Utilizamos el menú vanilla GENERIC_9X3.
 *
 * El cliente ya sabe dibujarlo, por lo que no necesita
 * instalar CraftersGacha.
 *
 * Lo bloqueamos para impedir que el jugador pueda sacar
 * los objetos ficticios de la ruleta.
 */
public final class LockedChestMenu extends ChestMenu {

    private final UUID sessionToken;

    public LockedChestMenu(
            int containerId,
            Inventory playerInventory,
            Container container,
            UUID sessionToken
    ) {
        super(
                MenuType.GENERIC_9x3,
                containerId,
                playerInventory,
                container,
                3
        );

        this.sessionToken = sessionToken;
    }

    public UUID getSessionToken() {
        return sessionToken;
    }

    /**
     * Bloqueamos absolutamente todos los clicks.
     *
     * La interfaz es únicamente visual.
     */
    @Override
    public void clicked(
            int slotId,
            int button,
            ClickType clickType,
            Player player
    ) {
        // Intencionalmente vacío.
    }

    /**
     * También deshabilitamos shift-click.
     */
    @Override
    public ItemStack quickMoveStack(
            Player player,
            int index
    ) {
        return ItemStack.EMPTY;
    }
}