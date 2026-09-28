package org.crafterscr.craftersgacha.menu;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.crafterscr.craftersgacha.data.GachaDefinition;

import java.util.List;
import java.util.Locale;

/**
 * Menú de vista previa de un Gacha.
 *
 * Es completamente server-side porque utiliza el menú vanilla
 * GENERIC_9x6. El cliente no necesita conocer esta clase.
 *
 * Distribución:
 *
 * Slots 0 - 44:
 *      Premios del Gacha.
 *
 * Slots 45 - 53:
 *      Barra inferior de información y navegación.
 *
 * 45 = página anterior
 * 47 = coste/llave
 * 49 = información de página
 * 53 = página siguiente
 */
public final class GachaPreviewMenu extends ChestMenu {

    private static final int ITEMS_PER_PAGE = 45;

    private static final int PREVIOUS_SLOT = 45;
    private static final int KEY_SLOT = 47;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final Container container;
    private final GachaDefinition definition;

    private int page = 0;

    public GachaPreviewMenu(
            int containerId,
            Inventory playerInventory,
            Container container,
            GachaDefinition definition
    ) {
        super(
                MenuType.GENERIC_9x6,
                containerId,
                playerInventory,
                container,
                6
        );

        this.container = container;
        this.definition = definition;

        renderPage();
    }

    /**
     * Bloqueamos cualquier interacción con los objetos.
     *
     * Solamente los botones de página anterior/siguiente
     * realizan una acción.
     */
    @Override
    public void clicked(
            int slotId,
            int button,
            ClickType clickType,
            Player player
    ) {
        if (slotId == PREVIOUS_SLOT) {
            if (page > 0) {
                page--;

                renderPage();

                broadcastChanges();
            }

            return;
        }

        if (slotId == NEXT_SLOT) {
            if (page < getTotalPages() - 1) {
                page++;

                renderPage();

                broadcastChanges();
            }

            return;
        }

        /*
         * Cualquier otro click queda completamente bloqueado.
         *
         * De esta forma los objetos mostrados nunca pueden
         * ser retirados de la GUI.
         */
    }

    /**
     * También bloqueamos Shift + Click dentro del inventario.
     */
    @Override
    public ItemStack quickMoveStack(
            Player player,
            int index
    ) {
        return ItemStack.EMPTY;
    }

    /**
     * Renderiza la página actual.
     */
    private void renderPage() {

        /*
         * Limpiamos solamente los 54 slots del cofre.
         * El inventario real del jugador no se toca.
         */
        for (int slot = 0; slot < 54; slot++) {
            container.setItem(
                    slot,
                    ItemStack.EMPTY
            );
        }

        List<GachaDefinition.Reward> rewards =
                definition.getRewards();

        double totalWeight =
                definition.getTotalWeight();

        int start =
                page * ITEMS_PER_PAGE;

        int end =
                Math.min(
                        start + ITEMS_PER_PAGE,
                        rewards.size()
                );

        /*
         * ---------------------------------------------------------
         * Premios
         * ---------------------------------------------------------
         */
        for (int rewardIndex = start;
             rewardIndex < end;
             rewardIndex++) {

            GachaDefinition.Reward reward =
                    rewards.get(rewardIndex);

            int guiSlot =
                    rewardIndex - start;

            double probability =
                    totalWeight <= 0.0D
                            ? 0.0D
                            : reward.getWeight()
                            / totalWeight
                            * 100.0D;

            container.setItem(
                    guiSlot,
                    createRewardDisplay(
                            reward,
                            probability
                    )
            );
        }

        /*
         * ---------------------------------------------------------
         * Barra inferior
         * ---------------------------------------------------------
         */
        ItemStack filler =
                createNamedItem(
                        new ItemStack(
                                Items.GRAY_STAINED_GLASS_PANE
                        ),
                        Component.literal(" ")
                );

        for (int slot = 45; slot <= 53; slot++) {
            container.setItem(
                    slot,
                    filler.copy()
            );
        }

        /*
         * ---------------------------------------------------------
         * Página anterior
         * ---------------------------------------------------------
         */
        if (page > 0) {
            ItemStack previous =
                    createNamedItem(
                            new ItemStack(
                                    Items.ARROW
                            ),
                            Component.literal(
                                            "Página anterior"
                                    )
                                    .withStyle(
                                            ChatFormatting.YELLOW
                                    )
                    );

            container.setItem(
                    PREVIOUS_SLOT,
                    previous
            );
        }

        /*
         * ---------------------------------------------------------
         * Información de la llave
         * ---------------------------------------------------------
         */
        container.setItem(
                KEY_SLOT,
                createKeyDisplay()
        );

        /*
         * ---------------------------------------------------------
         * Información general
         * ---------------------------------------------------------
         */
        MutableComponent pageName =
                Component.literal(
                                "Página "
                                        + (page + 1)
                                        + "/"
                                        + getTotalPages()
                        )
                        .withStyle(
                                ChatFormatting.GOLD
                        )
                        .append(
                                Component.literal(
                                                " • "
                                                        + rewards.size()
                                                        + " premios"
                                        )
                                        .withStyle(
                                                ChatFormatting.GRAY
                                        )
                        );

        ItemStack info =
                createNamedItem(
                        new ItemStack(
                                Items.BOOK
                        ),
                        pageName
                );

        container.setItem(
                INFO_SLOT,
                info
        );

        /*
         * ---------------------------------------------------------
         * Página siguiente
         * ---------------------------------------------------------
         */
        if (page < getTotalPages() - 1) {
            ItemStack next =
                    createNamedItem(
                            new ItemStack(
                                    Items.ARROW
                            ),
                            Component.literal(
                                            "Página siguiente"
                                    )
                                    .withStyle(
                                            ChatFormatting.YELLOW
                                    )
                    );

            container.setItem(
                    NEXT_SLOT,
                    next
            );
        }

        container.setChanged();
    }

    /**
     * Crea la representación visual de un premio.
     *
     * Ejemplo:
     *
     * Master Ball ×1 • 5.00%
     *
     * Carne cocinada ×5 • 30.00%
     *
     * Esta copia solamente existe dentro de la GUI.
     * El ItemStack original guardado en el Gacha no se modifica.
     */
    private ItemStack createRewardDisplay(
            GachaDefinition.Reward reward,
            double probability
    ) {
        ItemStack original =
                reward.getItem();

        ItemStack preview =
                original.copy();

        /*
         * La cantidad visual nunca puede superar
         * el stack máximo del objeto.
         *
         * Si el premio entrega 100 objetos pero el stack
         * máximo es 64, en el icono veremos 64, pero en el
         * nombre seguiremos mostrando ×100.
         */
        int visualAmount =
                Math.max(
                        1,
                        Math.min(
                                reward.getAmount(),
                                Math.max(
                                        1,
                                        preview.getMaxStackSize()
                                )
                        )
                );

        preview.setCount(
                visualAmount
        );

        /*
         * Conservamos el nombre original del objeto
         * y le agregamos cantidad y porcentaje.
         */
        MutableComponent name =
                original
                        .getHoverName()
                        .copy();

        name.append(
                Component.literal(
                                " ×"
                                        + reward.getAmount()
                        )
                        .withStyle(
                                ChatFormatting.GOLD
                        )
        );

        name.append(
                Component.literal(
                                " • "
                                        + String.format(
                                        Locale.ROOT,
                                        "%.2f%%",
                                        probability
                                )
                        )
                        .withStyle(
                                ChatFormatting.YELLOW
                        )
        );

        preview.set(
                DataComponents.CUSTOM_NAME,
                name
        );

        return preview;
    }

    /**
     * Muestra el coste del Gacha.
     */
    private ItemStack createKeyDisplay() {

        /*
         * Gacha gratuito.
         */
        if (definition.getKey() == null) {
            return createNamedItem(
                    new ItemStack(
                            Items.EMERALD
                    ),
                    Component.literal(
                                    "Costo: GRATIS"
                            )
                            .withStyle(
                                    ChatFormatting.GREEN
                            )
            );
        }

        GachaDefinition.KeyRequirement key =
                definition.getKey();

        ItemStack stack =
                key
                        .getItem()
                        .copy();

        int visualAmount =
                Math.max(
                        1,
                        Math.min(
                                key.getAmount(),
                                Math.max(
                                        1,
                                        stack.getMaxStackSize()
                                )
                        )
                );

        stack.setCount(
                visualAmount
        );

        MutableComponent name =
                Component.literal(
                                "Costo: "
                        )
                        .withStyle(
                                ChatFormatting.GRAY
                        );

        name.append(
                Component.literal(
                                key.getAmount()
                                        + "x "
                        )
                        .withStyle(
                                ChatFormatting.GOLD
                        )
        );

        name.append(
                key.getItem()
                        .getHoverName()
                        .copy()
        );

        stack.set(
                DataComponents.CUSTOM_NAME,
                name
        );

        return stack;
    }

    /**
     * Número total de páginas.
     *
     * Siempre devolvemos como mínimo 1.
     */
    private int getTotalPages() {
        return Math.max(
                1,
                (
                        definition
                                .getRewards()
                                .size()
                                + ITEMS_PER_PAGE
                                - 1
                ) / ITEMS_PER_PAGE
        );
    }

    private static ItemStack createNamedItem(
            ItemStack stack,
            Component name
    ) {
        stack.set(
                DataComponents.CUSTOM_NAME,
                name
        );

        return stack;
    }
}