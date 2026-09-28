package org.crafterscr.craftersgacha.service;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import org.crafterscr.craftersgacha.data.GachaDefinition;
import org.crafterscr.craftersgacha.menu.GachaPreviewMenu;

/**
 * Servicio encargado de abrir la vista previa.
 */
public final class GachaPreviewService {

    private GachaPreviewService() {
    }

    /**
     * Abre todos los premios disponibles del Gacha.
     *
     * No consume llave.
     * No realiza ninguna tirada.
     */
    public static boolean open(
            ServerPlayer player,
            GachaDefinition definition
    ) {

        if (definition.getRewards().isEmpty()) {
            player.sendSystemMessage(
                    Component.literal(
                                    "Este Gacha todavía no tiene premios."
                            )
                            .withStyle(
                                    ChatFormatting.RED
                            )
            );

            return false;
        }

        /*
         * 6 filas = 54 slots.
         *
         * Los primeros 45 son premios.
         * La última fila es navegación/información.
         */
        SimpleContainer container =
                new SimpleContainer(54);

        return player.openMenu(
                new SimpleMenuProvider(

                        (
                                containerId,
                                playerInventory,
                                ignoredPlayer
                        ) -> new GachaPreviewMenu(
                                containerId,
                                playerInventory,
                                container,
                                definition
                        ),

                        Component.literal(
                                        "Premios: "
                                                + definition.getId()
                                )
                                .withStyle(
                                        ChatFormatting.DARK_GREEN
                                )
                )
        ).isPresent();
    }
}