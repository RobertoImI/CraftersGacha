package org.crafterscr.craftersgacha.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import org.crafterscr.craftersgacha.data.GachaDefinition;
import org.crafterscr.craftersgacha.menu.LockedChestMenu;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Controla todas las ruletas actualmente activas.
 */
public final class GachaSpinManager {

    private static final Map<UUID, GachaSpinSession> SESSIONS =
            new ConcurrentHashMap<>();

    private GachaSpinManager() {
    }

    public static boolean start(
            ServerPlayer player,
            GachaDefinition definition,
            boolean consumeKey
    ) {
        if (SESSIONS.containsKey(player.getUUID())) {
            player.sendSystemMessage(
                    Component.literal(
                            "Ya tienes una ruleta en curso."
                    ).withStyle(ChatFormatting.RED)
            );

            return false;
        }

        if (definition.getRewards().isEmpty()
                || definition.getTotalWeight() <= 0) {

            player.sendSystemMessage(
                    Component.literal(
                            "Este gacha no tiene premios válidos."
                    ).withStyle(ChatFormatting.RED)
            );

            return false;
        }

        /*
         * Validamos la llave antes de elegir/abrir.
         */
        if (consumeKey
                && definition.getKey() != null) {

            if (!hasRequiredItem(
                    player,
                    definition.getKey()
            )) {
                player.sendSystemMessage(
                        Component.literal(
                                "Necesitas "
                                        + definition
                                        .getKey()
                                        .getAmount()
                                        + "x "
                                        + definition
                                        .getKey()
                                        .getItem()
                                        .getHoverName()
                                        .getString()
                        ).withStyle(ChatFormatting.RED)
                );

                return false;
            }
        }

        GachaDefinition.Reward finalReward =
                definition.pickWeighted(
                        ThreadLocalRandom.current()
                );

        if (finalReward == null) {
            return false;
        }

        /*
         * Consumimos la llave justo antes de abrir.
         */
        if (consumeKey
                && definition.getKey() != null) {

            consumeRequiredItem(
                    player,
                    definition.getKey()
            );
        }

        SimpleContainer displayContainer =
                new SimpleContainer(27);

        UUID token =
                UUID.randomUUID();

        OptionalInt menuId =
                player.openMenu(
                        new SimpleMenuProvider(
                                (containerId,
                                 playerInventory,
                                 ignoredPlayer) ->

                                        new LockedChestMenu(
                                                containerId,
                                                playerInventory,
                                                displayContainer,
                                                token
                                        ),

                                Component.literal(
                                        "Gacha: "
                                                + definition.getId()
                                )
                        )
                );

        /*
         * Si por algún motivo Minecraft no pudo abrir
         * el menú, devolvemos la llave.
         */
        if (menuId.isEmpty()) {
            if (consumeKey
                    && definition.getKey() != null) {

                giveItem(
                        player,
                        definition
                                .getKey()
                                .getItem(),
                        definition
                                .getKey()
                                .getAmount()
                );
            }

            return false;
        }

        GachaSpinSession session =
                new GachaSpinSession(
                        player,
                        token,
                        displayContainer,
                        definition,
                        finalReward
                );

        SESSIONS.put(
                player.getUUID(),
                session
        );

        return true;
    }

    public static void tick(
            MinecraftServer server
    ) {
        Iterator<Map.Entry<UUID, GachaSpinSession>> iterator =
                SESSIONS
                        .entrySet()
                        .iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, GachaSpinSession> entry =
                    iterator.next();

            if (entry
                    .getValue()
                    .tick(server)) {

                iterator.remove();
            }
        }
    }

    /**
     * Si el jugador desconecta durante una ruleta,
     * se le entrega inmediatamente su premio.
     *
     * Así nunca puede perder la llave por un logout.
     */
    public static void finishFor(
            ServerPlayer player
    ) {
        GachaSpinSession session =
                SESSIONS.remove(
                        player.getUUID()
                );

        if (session != null) {
            session.finishNow(player);
        }
    }

    public static void finishAll(
            MinecraftServer server
    ) {
        for (Map.Entry<UUID, GachaSpinSession> entry :
                List.copyOf(
                        SESSIONS.entrySet()
                )) {

            ServerPlayer player =
                    server
                            .getPlayerList()
                            .getPlayer(
                                    entry.getKey()
                            );

            if (player != null) {
                entry
                        .getValue()
                        .finishNow(player);
            }
        }

        SESSIONS.clear();
    }

    public static boolean isSpinning(
            ServerPlayer player
    ) {
        return SESSIONS.containsKey(
                player.getUUID()
        );
    }

    // =====================================================================
    // Llaves
    // =====================================================================

    private static boolean hasRequiredItem(
            ServerPlayer player,
            GachaDefinition.KeyRequirement requirement
    ) {
        int found = 0;

        for (int slot = 0;
             slot < player
                     .getInventory()
                     .getContainerSize();
             slot++) {

            ItemStack stack =
                    player
                            .getInventory()
                            .getItem(slot);

            if (stack.isEmpty()) {
                continue;
            }

            /*
             * Comparamos:
             *
             * - item
             * - componentes
             *
             * Esto es importante para llaves modded,
             * CustomModelData, nombres, NBT/components, etc.
             */
            if (ItemStack.isSameItemSameComponents(
                    stack,
                    requirement.getItem()
            )) {
                found += stack.getCount();

                if (found >= requirement.getAmount()) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void consumeRequiredItem(
            ServerPlayer player,
            GachaDefinition.KeyRequirement requirement
    ) {
        int remaining =
                requirement.getAmount();

        for (int slot = 0;
             slot < player
                     .getInventory()
                     .getContainerSize()
                     && remaining > 0;
             slot++) {

            ItemStack stack =
                    player
                            .getInventory()
                            .getItem(slot);

            if (!ItemStack.isSameItemSameComponents(
                    stack,
                    requirement.getItem()
            )) {
                continue;
            }

            int remove =
                    Math.min(
                            remaining,
                            stack.getCount()
                    );

            stack.shrink(remove);

            remaining -= remove;
        }

        player
                .getInventory()
                .setChanged();
    }

    private static void giveItem(
            ServerPlayer player,
            ItemStack template,
            int amount
    ) {
        int remaining = amount;

        while (remaining > 0) {
            int stackAmount =
                    Math.min(
                            remaining,
                            template.getMaxStackSize()
                    );

            ItemStack stack =
                    template.copyWithCount(
                            stackAmount
                    );

            player
                    .getInventory()
                    .add(stack);

            if (!stack.isEmpty()) {
                player.drop(
                        stack,
                        false
                );
            }

            remaining -= stackAmount;
        }
    }
}