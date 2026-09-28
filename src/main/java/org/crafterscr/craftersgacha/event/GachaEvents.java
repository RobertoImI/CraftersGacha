package org.crafterscr.craftersgacha.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.crafterscr.craftersgacha.CraftersGacha;
import org.crafterscr.craftersgacha.command.GachaCommands;
import org.crafterscr.craftersgacha.data.GachaChestLink;
import org.crafterscr.craftersgacha.data.GachaDefinition;
import org.crafterscr.craftersgacha.data.GachaManager;
import org.crafterscr.craftersgacha.runtime.GachaSpinManager;
import org.crafterscr.craftersgacha.service.GachaPreviewService;
import org.crafterscr.craftersgacha.service.HologramService;
import org.crafterscr.craftersgacha.service.ParticleService;

/**
 * Eventos globales de CraftersGacha.
 */
@EventBusSubscriber(
        modid = CraftersGacha.MOD_ID
)
public final class GachaEvents {

    private GachaEvents() {
    }

    // =====================================================================
    // Comandos
    // =====================================================================

    @SubscribeEvent
    public static void onRegisterCommands(
            RegisterCommandsEvent event
    ) {
        GachaCommands.register(
                event.getDispatcher(),
                event.getBuildContext()
        );
    }

    // =====================================================================
    // Servidor
    // =====================================================================

    @SubscribeEvent
    public static void onServerStarted(
            ServerStartedEvent event
    ) {
        GachaManager.get(
                event.getServer()
        );

        HologramService.ensureAllLoaded(
                event.getServer()
        );
    }

    @SubscribeEvent
    public static void onServerStopping(
            ServerStoppingEvent event
    ) {
        /*
         * Entregamos cualquier premio pendiente
         * antes de cerrar el servidor.
         */
        GachaSpinManager.finishAll(
                event.getServer()
        );

        GachaManager.shutdown(
                event.getServer()
        );
    }

    // =====================================================================
    // Tick
    // =====================================================================

    @SubscribeEvent
    public static void onServerTick(
            ServerTickEvent.Post event
    ) {
        /*
         * Animaciones activas de la ruleta.
         */
        GachaSpinManager.tick(
                event.getServer()
        );

        int tick =
                event.getServer()
                        .getTickCount();

        /*
         * Partículas decorativas:
         * cada 10 ticks = 2 veces por segundo.
         */
        if (tick % 10 == 0) {
            ParticleService.tick(
                    event.getServer()
            );
        }

        /*
         * Comprobación de hologramas:
         * cada 100 ticks = cada 5 segundos.
         */
        if (tick % 100 == 0) {
            HologramService.ensureAllLoaded(
                    event.getServer()
            );
        }
    }

    // =====================================================================
    // Interacción con Gacha
    // =====================================================================

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event
    ) {

        /*
         * Todo se procesa únicamente en servidor.
         */
        if (event.getLevel().isClientSide()) {
            return;
        }

        /*
         * Evitamos que Minecraft dispare dos veces
         * la interacción por MAIN_HAND y OFF_HAND.
         */
        if (event.getHand()
                != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        GachaManager manager =
                GachaManager.get(
                        player
                                .serverLevel()
                                .getServer()
                );

        GachaChestLink link =
                manager.getChest(
                        player.serverLevel(),
                        event.getPos()
                );

        /*
         * Si no es uno de nuestros cofres,
         * Minecraft continúa normalmente.
         */
        if (link == null) {
            return;
        }

        /*
         * El cofre ya es un Gacha.
         *
         * Por tanto evitamos que se abra como
         * un cofre vanilla convencional.
         */
        event.setCanceled(true);

        event.setCancellationResult(
                InteractionResult.SUCCESS
        );

        GachaDefinition definition =
                manager.getGacha(
                        link.gachaId()
                );

        if (definition == null) {
            player.sendSystemMessage(
                    Component.literal(
                                    "Este Gacha ya no existe."
                            )
                            .withStyle(
                                    ChatFormatting.RED
                            )
            );

            return;
        }

        /*
         * =========================================================
         * SHIFT + CLICK DERECHO
         * =========================================================
         *
         * Abre la lista de premios.
         *
         * NO:
         * - consume llave;
         * - inicia ruleta;
         * - modifica probabilidades;
         * - entrega premios.
         */
        if (player.isShiftKeyDown()) {

            GachaPreviewService.open(
                    player,
                    definition
            );

            return;
        }

        /*
         * =========================================================
         * CLICK DERECHO NORMAL
         * =========================================================
         *
         * Ejecuta la ruleta normalmente.
         */
        GachaSpinManager.start(
                player,
                definition,
                true
        );
    }

    // =====================================================================
    // Protección del cofre
    // =====================================================================

    @SubscribeEvent
    public static void onBlockBreak(
            BlockEvent.BreakEvent event
    ) {
        if (!(event.getPlayer()
                instanceof ServerPlayer player)) {
            return;
        }

        GachaManager manager =
                GachaManager.get(
                        player
                                .serverLevel()
                                .getServer()
                );

        GachaChestLink link =
                manager.getChest(
                        player.serverLevel(),
                        event.getPos()
                );

        if (link == null) {
            return;
        }

        /*
         * Un jugador normal no puede destruir
         * un cofre que sea Gacha.
         */
        if (!player.hasPermissions(2)) {
            event.setCanceled(true);

            player.sendSystemMessage(
                    Component.literal(
                                    "No puedes romper un cofre de Gacha."
                            )
                            .withStyle(
                                    ChatFormatting.RED
                            )
            );

            return;
        }

        /*
         * Si es administrador:
         *
         * - dejamos que rompa el bloque;
         * - eliminamos la asociación;
         * - eliminamos su holograma.
         */
        HologramService.remove(
                player
                        .serverLevel()
                        .getServer(),
                link
        );

        manager.removeChest(
                player.serverLevel(),
                event.getPos()
        );
    }

    // =====================================================================
    // Protección contra explosiones
    // =====================================================================

    /**
     * ExplosionEvent.Detonate permite retirar bloques concretos de la lista
     * de destrucción sin cancelar toda la explosión.
     *
     * Resultado:
     * - TNT/creepers/etc. continúan explotando normalmente;
     * - el cofre Gacha no se destruye;
     * - el resto de bloques sí puede verse afectado.
     */
    @SubscribeEvent
    public static void onExplosion(
            ExplosionEvent.Detonate event
    ) {
        if (!(event.getLevel()
                instanceof ServerLevel level)) {
            return;
        }

        GachaManager manager =
                GachaManager.get(
                        level.getServer()
                );

        event.getAffectedBlocks()
                .removeIf(pos ->
                        manager.getChest(
                                level,
                                pos
                        ) != null
                );
    }

    // =====================================================================
    // Protección contra pistones
    // =====================================================================

    /**
     * Cancela el movimiento completo del pistón si entre los bloques
     * que intentaría mover o destruir existe un cofre Gacha.
     *
     * getChest() también resuelve la otra mitad de un double chest,
     * así que la protección cubre ambos lados.
     */
    @SubscribeEvent
    public static void onPiston(
            PistonEvent.Pre event
    ) {
        if (!(event.getLevel()
                instanceof ServerLevel level)) {
            return;
        }

        var resolver =
                event.getStructureHelper();

        if (resolver == null
                || !resolver.resolve()) {
            return;
        }

        GachaManager manager =
                GachaManager.get(
                        level.getServer()
                );

        for (var pos : resolver.getToPush()) {
            if (manager.getChest(
                    level,
                    pos
            ) != null) {

                event.setCanceled(true);
                return;
            }
        }

        for (var pos : resolver.getToDestroy()) {
            if (manager.getChest(
                    level,
                    pos
            ) != null) {

                event.setCanceled(true);
                return;
            }
        }
    }

    // =====================================================================
    // Logout
    // =====================================================================

    @SubscribeEvent
    public static void onLogout(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        if (event.getEntity()
                instanceof ServerPlayer player) {

            /*
             * Si estaba tirando cuando salió,
             * recibe inmediatamente el premio.
             */
            GachaSpinManager.finishFor(
                    player
            );
        }
    }
}