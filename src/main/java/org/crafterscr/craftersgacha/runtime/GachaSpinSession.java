package org.crafterscr.craftersgacha.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.crafterscr.craftersgacha.data.GachaDefinition;
import org.crafterscr.craftersgacha.menu.LockedChestMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Representa una tirada activa.
 */
public final class GachaSpinSession {

    /**
     * Número de desplazamientos visuales.
     */
    private static final int TOTAL_STEPS = 45;

    /**
     * El centro de los nueve slots visibles.
     */
    private static final int CENTER_OFFSET = 4;

    private final UUID playerId;

    private final UUID token;

    private final SimpleContainer container;

    private final List<ItemStack> reel;

    private final GachaDefinition.Reward finalReward;

    private int cursor = 0;

    private int steps = 0;

    private int ticksUntilNext = 0;

    private boolean awarded = false;

    /**
     * Tiempo que dejamos visible el premio final.
     */
    private int finishHoldTicks = 30;

    public GachaSpinSession(
            ServerPlayer player,
            UUID token,
            SimpleContainer container,
            GachaDefinition definition,
            GachaDefinition.Reward finalReward
    ) {
        this.playerId =
                player.getUUID();

        this.token =
                token;

        this.container =
                container;

        this.finalReward =
                finalReward.copy();

        this.reel =
                generateReel(
                        definition,
                        finalReward
                );

        prepareBackground();

        renderWindow();
    }

    /**
     * Devuelve true cuando la sesión terminó por completo.
     */
    public boolean tick(
            MinecraftServer server
    ) {
        ServerPlayer player =
                server
                        .getPlayerList()
                        .getPlayer(playerId);

        /*
         * Si temporalmente no existe el jugador,
         * conservamos la sesión.
         *
         * PlayerLoggedOutEvent se encarga normalmente
         * de entregar el premio antes de desconectar.
         */
        if (player == null) {
            return false;
        }

        if (awarded) {
            finishHoldTicks--;

            if (finishHoldTicks <= 0) {
                closeOurMenu(player);

                return true;
            }

            return false;
        }

        if (ticksUntilNext > 0) {
            ticksUntilNext--;

            return false;
        }

        cursor++;
        steps++;

        renderWindow();

        /*
         * Sonido de cada salto.
         */
        float pitch =
                0.8F
                        + (steps / (float) TOTAL_STEPS)
                        * 0.6F;

        player.playNotifySound(
                SoundEvents.NOTE_BLOCK_HAT.value(),
                SoundSource.PLAYERS,
                0.45F,
                pitch
        );

        if (steps >= TOTAL_STEPS) {
            finish(player);

            return false;
        }

        /*
         * Curva de desaceleración.
         *
         * Al principio:
         * 1 tick aproximadamente.
         *
         * Al final:
         * hasta 9 ticks.
         */
        double progress =
                steps / (double) TOTAL_STEPS;

        ticksUntilNext =
                1
                        + (int) (
                        Math.pow(progress, 3.0D)
                                * 8.0D
                );

        return false;
    }

    /**
     * Entrega inmediatamente el premio.
     *
     * Se usa, por ejemplo, cuando el jugador sale
     * mientras la ruleta está funcionando.
     */
    public void finishNow(
            ServerPlayer player
    ) {
        if (!awarded) {
            finish(player);
        }
    }

    private void finish(
            ServerPlayer player
    ) {
        awarded = true;

        /*
         * Volvemos a renderizar por seguridad.
         *
         * La generación del reel garantiza que en este punto
         * el centro contiene exactamente finalReward.
         */
        renderWindow();

        giveReward(
                player,
                finalReward
        );

        player.playNotifySound(
                SoundEvents.NOTE_BLOCK_PLING.value(),
                SoundSource.PLAYERS,
                1.0F,
                1.25F
        );

        player.sendSystemMessage(
                net.minecraft.network.chat.Component
                        .literal("¡Ganaste ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(
                                net.minecraft.network.chat.Component
                                        .literal(
                                                finalReward.getAmount()
                                                        + "x "
                                                        + finalReward
                                                        .getItem()
                                                        .getHoverName()
                                                        .getString()
                                        )
                                        .withStyle(
                                                ChatFormatting.YELLOW
                                        )
                        )
                        .append(
                                net.minecraft.network.chat.Component
                                        .literal("!")
                                        .withStyle(
                                                ChatFormatting.GOLD
                                        )
                        )
        );
    }

    /**
     * Construye el carrusel.
     *
     * Cada slot se escoge utilizando el peso real.
     *
     * Por eso:
     *
     * carne peso 30
     * Master Ball peso 5
     *
     * hace que visualmente aparezca carne muchas más veces.
     */
    private List<ItemStack> generateReel(
            GachaDefinition definition,
            GachaDefinition.Reward selectedReward
    ) {
        int size =
                TOTAL_STEPS + 9;

        List<ItemStack> result =
                new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            GachaDefinition.Reward reward =
                    definition.pickWeighted(
                            ThreadLocalRandom.current()
                    );

            if (reward == null) {
                result.add(ItemStack.EMPTY);

                continue;
            }

            result.add(
                    createVisualStack(
                            reward
                    )
            );
        }

        /*
         * Cuando cursor == TOTAL_STEPS,
         * el slot central muestra:
         *
         * reel[TOTAL_STEPS + 4]
         */
        result.set(
                TOTAL_STEPS + CENTER_OFFSET,
                createVisualStack(
                        selectedReward
                )
        );

        return result;
    }

    private void prepareBackground() {
        ItemStack border =
                new ItemStack(
                        Items.GRAY_STAINED_GLASS_PANE
                );

        ItemStack marker =
                new ItemStack(
                        Items.LIME_STAINED_GLASS_PANE
                );

        /*
         * Fila superior.
         */
        for (int slot = 0; slot <= 8; slot++) {
            container.setItem(
                    slot,
                    border.copy()
            );
        }

        /*
         * Fila inferior.
         */
        for (int slot = 18; slot <= 26; slot++) {
            container.setItem(
                    slot,
                    border.copy()
            );
        }

        /*
         * Marcadores del centro.
         */
        container.setItem(
                4,
                marker.copy()
        );

        container.setItem(
                22,
                marker.copy()
        );
    }

    /**
     * Los nueve premios visibles ocupan:
     *
     * slots 9 - 17.
     */
    private void renderWindow() {
        for (int visible = 0; visible < 9; visible++) {
            int reelIndex =
                    cursor + visible;

            ItemStack stack =
                    reelIndex < reel.size()
                            ? reel.get(reelIndex)
                            : ItemStack.EMPTY;

            container.setItem(
                    9 + visible,
                    stack.copy()
            );
        }

        container.setChanged();
    }

    private static ItemStack createVisualStack(
            GachaDefinition.Reward reward
    ) {
        ItemStack template =
                reward.getItem();

        int visualAmount =
                Math.max(
                        1,
                        Math.min(
                                reward.getAmount(),
                                template.getMaxStackSize()
                        )
                );

        return template.copyWithCount(
                visualAmount
        );
    }

    /**
     * Entrega cantidades mayores al stack máximo
     * correctamente.
     *
     * Si el inventario está lleno, el sobrante
     * cae al suelo al lado del jugador.
     */
    private static void giveReward(
            ServerPlayer player,
            GachaDefinition.Reward reward
    ) {
        int remaining =
                reward.getAmount();

        while (remaining > 0) {
            int amount =
                    Math.min(
                            remaining,
                            reward
                                    .getItem()
                                    .getMaxStackSize()
                    );

            ItemStack stack =
                    reward
                            .getItem()
                            .copyWithCount(amount);

            player
                    .getInventory()
                    .add(stack);

            /*
             * Inventory.add modifica stack y deja
             * dentro cualquier sobrante.
             */
            if (!stack.isEmpty()) {
                player.drop(
                        stack,
                        false
                );
            }

            remaining -= amount;
        }
    }

    private void closeOurMenu(
            ServerPlayer player
    ) {
        if (player.containerMenu
                instanceof LockedChestMenu menu
                && menu
                .getSessionToken()
                .equals(token)) {

            player.closeContainer();
        }
    }
}