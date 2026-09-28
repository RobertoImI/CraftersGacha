package org.crafterscr.craftersgacha.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.crafterscr.craftersgacha.data.GachaChestLink;
import org.crafterscr.craftersgacha.data.GachaDefinition;
import org.crafterscr.craftersgacha.data.GachaManager;
import org.crafterscr.craftersgacha.runtime.GachaSpinManager;
import org.crafterscr.craftersgacha.service.HologramService;
import org.crafterscr.craftersgacha.util.GachaBlockUtil;

import java.util.List;
import java.util.Locale;

/**
 * Árbol completo de /gacha.
 *
 * Todos los IDs existentes tienen sugerencias Brigadier.
 */
public final class GachaCommands {

    private GachaCommands() {
    }

    // =====================================================================
    // Brigadier Suggestions
    // =====================================================================

    private static final SuggestionProvider<CommandSourceStack> GACHA_IDS =
            (context, builder) -> {

                GachaManager manager =
                        GachaManager.get(
                                context
                                        .getSource()
                                        .getServer()
                        );

                return SharedSuggestionProvider.suggest(
                        manager.getIds(),
                        builder
                );
            };

    private static final SuggestionProvider<CommandSourceStack> REWARD_IDS =
            (context, builder) -> {

                try {
                    String id =
                            StringArgumentType.getString(
                                    context,
                                    "id"
                            );

                    GachaDefinition definition =
                            GachaManager
                                    .get(
                                            context
                                                    .getSource()
                                                    .getServer()
                                    )
                                    .getGacha(id);

                    if (definition == null) {
                        return builder.buildFuture();
                    }

                    List<String> rewardIds =
                            definition
                                    .getRewards()
                                    .stream()
                                    .map(reward ->
                                            Integer.toString(
                                                    reward.getId()
                                            )
                                    )
                                    .toList();

                    return SharedSuggestionProvider.suggest(
                            rewardIds,
                            builder
                    );

                } catch (Exception ignored) {
                    return builder.buildFuture();
                }
            };

    // =====================================================================
    // Registro
    // =====================================================================

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext
    ) {
        dispatcher.register(
                Commands.literal("gacha")

                        // Solo administración.
                        .requires(source ->
                                source.hasPermission(2)
                        )

                        // =================================================
                        // CREATE
                        // =================================================

                        .then(
                                Commands.literal("create")
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                StringArgumentType.word()
                                                        )
                                                        .executes(context ->
                                                                create(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "id"
                                                                        )
                                                                )
                                                        )
                                        )
                        )

                        // =================================================
                        // DELETE
                        // =================================================

                        .then(
                                Commands.literal("delete")
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests(GACHA_IDS)
                                                        .executes(context ->
                                                                delete(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "id"
                                                                        )
                                                                )
                                                        )
                                        )
                        )

                        // =================================================
                        // LIST
                        // =================================================

                        .then(
                                Commands.literal("list")
                                        .executes(context ->
                                                list(
                                                        context.getSource()
                                                )
                                        )
                        )

                        // =================================================
                        // REWARD
                        // =================================================

                        .then(
                                Commands.literal("reward")

                                        // ---------------------------------
                                        // reward add
                                        // ---------------------------------

                                        .then(
                                                Commands.literal("add")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "amount",
                                                                                                IntegerArgumentType.integer(
                                                                                                        1
                                                                                                )
                                                                                        )
                                                                                        .then(
                                                                                                Commands.argument(
                                                                                                                "weight",
                                                                                                                DoubleArgumentType.doubleArg(
                                                                                                                        0.0001D
                                                                                                                )
                                                                                                        )
                                                                                                        .executes(context ->
                                                                                                                addReward(
                                                                                                                        context.getSource(),
                                                                                                                        StringArgumentType.getString(
                                                                                                                                context,
                                                                                                                                "id"
                                                                                                                        ),
                                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                                context,
                                                                                                                                "amount"
                                                                                                                        ),
                                                                                                                        DoubleArgumentType.getDouble(
                                                                                                                                context,
                                                                                                                                "weight"
                                                                                                                        )
                                                                                                                )
                                                                                                        )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        // ---------------------------------
                                        // reward remove
                                        // ---------------------------------

                                        .then(
                                                Commands.literal("remove")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "reward",
                                                                                                IntegerArgumentType.integer(
                                                                                                        1
                                                                                                )
                                                                                        )
                                                                                        .suggests(REWARD_IDS)
                                                                                        .executes(context ->
                                                                                                removeReward(
                                                                                                        context.getSource(),
                                                                                                        StringArgumentType.getString(
                                                                                                                context,
                                                                                                                "id"
                                                                                                        ),
                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                context,
                                                                                                                "reward"
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        // ---------------------------------
                                        // reward amount
                                        // ---------------------------------

                                        .then(
                                                Commands.literal("amount")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "reward",
                                                                                                IntegerArgumentType.integer(
                                                                                                        1
                                                                                                )
                                                                                        )
                                                                                        .suggests(REWARD_IDS)
                                                                                        .then(
                                                                                                Commands.argument(
                                                                                                                "amount",
                                                                                                                IntegerArgumentType.integer(
                                                                                                                        1
                                                                                                                )
                                                                                                        )
                                                                                                        .executes(context ->
                                                                                                                rewardAmount(
                                                                                                                        context.getSource(),
                                                                                                                        StringArgumentType.getString(
                                                                                                                                context,
                                                                                                                                "id"
                                                                                                                        ),
                                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                                context,
                                                                                                                                "reward"
                                                                                                                        ),
                                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                                context,
                                                                                                                                "amount"
                                                                                                                        )
                                                                                                                )
                                                                                                        )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        // ---------------------------------
                                        // reward weight
                                        // ---------------------------------

                                        .then(
                                                Commands.literal("weight")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "reward",
                                                                                                IntegerArgumentType.integer(
                                                                                                        1
                                                                                                )
                                                                                        )
                                                                                        .suggests(REWARD_IDS)
                                                                                        .then(
                                                                                                Commands.argument(
                                                                                                                "weight",
                                                                                                                DoubleArgumentType.doubleArg(
                                                                                                                        0.0001D
                                                                                                                )
                                                                                                        )
                                                                                                        .executes(context ->
                                                                                                                rewardWeight(
                                                                                                                        context.getSource(),
                                                                                                                        StringArgumentType.getString(
                                                                                                                                context,
                                                                                                                                "id"
                                                                                                                        ),
                                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                                context,
                                                                                                                                "reward"
                                                                                                                        ),
                                                                                                                        DoubleArgumentType.getDouble(
                                                                                                                                context,
                                                                                                                                "weight"
                                                                                                                        )
                                                                                                                )
                                                                                                        )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        // ---------------------------------
                                        // reward replace
                                        // ---------------------------------

                                        .then(
                                                Commands.literal("replace")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "reward",
                                                                                                IntegerArgumentType.integer(
                                                                                                        1
                                                                                                )
                                                                                        )
                                                                                        .suggests(REWARD_IDS)
                                                                                        .executes(context ->
                                                                                                replaceReward(
                                                                                                        context.getSource(),
                                                                                                        StringArgumentType.getString(
                                                                                                                context,
                                                                                                                "id"
                                                                                                        ),
                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                context,
                                                                                                                "reward"
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        // ---------------------------------
                                        // reward list
                                        // ---------------------------------

                                        .then(
                                                Commands.literal("list")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .executes(context ->
                                                                                listRewards(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "id"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )

                        // =================================================
                        // KEY
                        // =================================================

                        .then(
                                Commands.literal("key")

                                        .then(
                                                Commands.literal("set")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "amount",
                                                                                                IntegerArgumentType.integer(
                                                                                                        1
                                                                                                )
                                                                                        )
                                                                                        .executes(context ->
                                                                                                setKey(
                                                                                                        context.getSource(),
                                                                                                        StringArgumentType.getString(
                                                                                                                context,
                                                                                                                "id"
                                                                                                        ),
                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                context,
                                                                                                                "amount"
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        .then(
                                                Commands.literal("clear")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .executes(context ->
                                                                                clearKey(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "id"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )

                        // =================================================
                        // CHEST
                        // =================================================

                        .then(
                                Commands.literal("chest")

                                        .then(
                                                Commands.literal("set")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .executes(context ->
                                                                                setChest(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "id"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )

                                        .then(
                                                Commands.literal("remove")
                                                        .executes(context ->
                                                                removeChest(
                                                                        context.getSource()
                                                                )
                                                        )
                                        )

                                        .then(
                                                Commands.literal("info")
                                                        .executes(context ->
                                                                chestInfo(
                                                                        context.getSource()
                                                                )
                                                        )
                                        )
                        )

                        // =================================================
                        // HOLOGRAM
                        // =================================================

                        .then(
                                Commands.literal("hologram")

                                        .then(
                                                Commands.literal("add")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "text",
                                                                                                StringArgumentType.greedyString()
                                                                                        )
                                                                                        .executes(context ->
                                                                                                hologramAdd(
                                                                                                        context.getSource(),
                                                                                                        StringArgumentType.getString(
                                                                                                                context,
                                                                                                                "id"
                                                                                                        ),
                                                                                                        StringArgumentType.getString(
                                                                                                                context,
                                                                                                                "text"
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        .then(
                                                Commands.literal("set")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "line",
                                                                                                IntegerArgumentType.integer(
                                                                                                        1
                                                                                                )
                                                                                        )
                                                                                        .then(
                                                                                                Commands.argument(
                                                                                                                "text",
                                                                                                                StringArgumentType.greedyString()
                                                                                                        )
                                                                                                        .executes(context ->
                                                                                                                hologramSet(
                                                                                                                        context.getSource(),
                                                                                                                        StringArgumentType.getString(
                                                                                                                                context,
                                                                                                                                "id"
                                                                                                                        ),
                                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                                context,
                                                                                                                                "line"
                                                                                                                        ),
                                                                                                                        StringArgumentType.getString(
                                                                                                                                context,
                                                                                                                                "text"
                                                                                                                        )
                                                                                                                )
                                                                                                        )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        .then(
                                                Commands.literal("remove")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "line",
                                                                                                IntegerArgumentType.integer(
                                                                                                        1
                                                                                                )
                                                                                        )
                                                                                        .executes(context ->
                                                                                                hologramRemove(
                                                                                                        context.getSource(),
                                                                                                        StringArgumentType.getString(
                                                                                                                context,
                                                                                                                "id"
                                                                                                        ),
                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                context,
                                                                                                                "line"
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        .then(
                                                Commands.literal("clear")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .executes(context ->
                                                                                hologramClear(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "id"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )

                        // =================================================
                        // PARTICLE
                        // =================================================

                        .then(
                                Commands.literal("particle")

                                        .then(
                                                Commands.literal("set")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "particle",
                                                                                                ParticleArgument.particle(
                                                                                                        buildContext
                                                                                                )
                                                                                        )
                                                                                        .executes(context ->
                                                                                                setParticle(
                                                                                                        context.getSource(),
                                                                                                        StringArgumentType.getString(
                                                                                                                context,
                                                                                                                "id"
                                                                                                        ),
                                                                                                        ParticleArgument.getParticle(
                                                                                                                context,
                                                                                                                "particle"
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        .then(
                                                Commands.literal("settings")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "count",
                                                                                                IntegerArgumentType.integer(
                                                                                                        1,
                                                                                                        100
                                                                                                )
                                                                                        )
                                                                                        .then(
                                                                                                Commands.argument(
                                                                                                                "radius",
                                                                                                                DoubleArgumentType.doubleArg(
                                                                                                                        0.0D,
                                                                                                                        5.0D
                                                                                                                )
                                                                                                        )
                                                                                                        .then(
                                                                                                                Commands.argument(
                                                                                                                                "speed",
                                                                                                                                DoubleArgumentType.doubleArg(
                                                                                                                                        0.0D,
                                                                                                                                        2.0D
                                                                                                                                )
                                                                                                                        )
                                                                                                                        .executes(context ->
                                                                                                                                particleSettings(
                                                                                                                                        context.getSource(),
                                                                                                                                        StringArgumentType.getString(
                                                                                                                                                context,
                                                                                                                                                "id"
                                                                                                                                        ),
                                                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                                                context,
                                                                                                                                                "count"
                                                                                                                                        ),
                                                                                                                                        DoubleArgumentType.getDouble(
                                                                                                                                                context,
                                                                                                                                                "radius"
                                                                                                                                        ),
                                                                                                                                        DoubleArgumentType.getDouble(
                                                                                                                                                context,
                                                                                                                                                "speed"
                                                                                                                                        )
                                                                                                                                )
                                                                                                                        )
                                                                                                        )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        .then(
                                                Commands.literal("off")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(GACHA_IDS)
                                                                        .executes(context ->
                                                                                particleOff(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "id"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )

                        // =================================================
                        // TEST
                        // =================================================

                        .then(
                                Commands.literal("test")
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests(GACHA_IDS)
                                                        .executes(context ->
                                                                test(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "id"
                                                                        )
                                                                )
                                                        )
                                        )
                        )

                        // =================================================
                        // SAVE / RELOAD
                        // =================================================

                        .then(
                                Commands.literal("save")
                                        .executes(context ->
                                                save(
                                                        context.getSource()
                                                )
                                        )
                        )

                        .then(
                                Commands.literal("reload")
                                        .executes(context ->
                                                reload(
                                                        context.getSource()
                                                )
                                        )
                        )
        );
    }

    // =====================================================================
    // Implementación
    // =====================================================================

    private static int create(
            CommandSourceStack source,
            String id
    ) {
        GachaManager manager =
                GachaManager.get(
                        source.getServer()
                );

        GachaDefinition definition =
                manager.createGacha(id);

        if (definition == null) {
            source.sendFailure(
                    Component.literal(
                            "Ya existe un gacha con ID "
                                    + id
                    )
            );

            return 0;
        }

        success(
                source,
                "Gacha '"
                        + definition.getId()
                        + "' creado."
        );

        return 1;
    }

    private static int delete(
            CommandSourceStack source,
            String id
    ) {
        GachaManager manager =
                GachaManager.get(
                        source.getServer()
                );

        if (!manager.hasGacha(id)) {
            source.sendFailure(
                    Component.literal(
                            "Ese ID no existe."
                    )
            );

            return 0;
        }

        List<GachaChestLink> links =
                manager.deleteGacha(id);

        for (GachaChestLink link : links) {
            HologramService.remove(
                    source.getServer(),
                    link
            );
        }

        success(
                source,
                "Gacha '"
                        + id
                        + "' eliminado."
        );

        return 1;
    }

    private static int list(
            CommandSourceStack source
    ) {
        GachaManager manager =
                GachaManager.get(
                        source.getServer()
                );

        if (manager.getIds().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "No existen gachas."
                    ),
                    false
            );

            return 1;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Gachas: "
                                + String.join(
                                ", ",
                                manager.getIds()
                        )
                ).withStyle(ChatFormatting.GOLD),
                false
        );

        return 1;
    }

    // ---------------------------------------------------------------------
    // Premios
    // ---------------------------------------------------------------------

    private static int addReward(
            CommandSourceStack source,
            String id,
            int amount,
            double weight
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        ServerPlayer player =
                source.getPlayerOrException();

        ItemStack hand =
                player.getMainHandItem();

        if (hand.isEmpty()) {
            source.sendFailure(
                    Component.literal(
                            "Debes tener el premio en la mano principal."
                    )
            );

            return 0;
        }

        int rewardId =
                definition.addReward(
                        hand,
                        amount,
                        weight
                );

        GachaManager
                .get(source.getServer())
                .save();

        double chance =
                100.0D
                        * weight
                        / definition.getTotalWeight();

        success(
                source,
                "Premio #"
                        + rewardId
                        + " agregado: "
                        + amount
                        + "x "
                        + hand.getHoverName().getString()
                        + " | peso "
                        + weight
                        + " | probabilidad actual "
                        + formatPercent(chance)
                        + "%"
        );

        return 1;
    }

    private static int removeReward(
            CommandSourceStack source,
            String id,
            int rewardId
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        if (!definition.removeReward(rewardId)) {
            source.sendFailure(
                    Component.literal(
                            "No existe el premio #"
                                    + rewardId
                    )
            );

            return 0;
        }

        GachaManager
                .get(source.getServer())
                .save();

        success(
                source,
                "Premio #"
                        + rewardId
                        + " eliminado."
        );

        return 1;
    }

    private static int rewardAmount(
            CommandSourceStack source,
            String id,
            int rewardId,
            int amount
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        GachaDefinition.Reward reward =
                definition
                        .findReward(rewardId)
                        .orElse(null);

        if (reward == null) {
            source.sendFailure(
                    Component.literal(
                            "No existe ese premio."
                    )
            );

            return 0;
        }

        reward.setAmount(amount);

        GachaManager
                .get(source.getServer())
                .save();

        success(
                source,
                "Premio #"
                        + rewardId
                        + " ahora entrega "
                        + amount
                        + "."
        );

        return 1;
    }

    private static int rewardWeight(
            CommandSourceStack source,
            String id,
            int rewardId,
            double weight
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        GachaDefinition.Reward reward =
                definition
                        .findReward(rewardId)
                        .orElse(null);

        if (reward == null) {
            source.sendFailure(
                    Component.literal(
                            "No existe ese premio."
                    )
            );

            return 0;
        }

        reward.setWeight(weight);

        GachaManager
                .get(source.getServer())
                .save();

        success(
                source,
                "Peso del premio #"
                        + rewardId
                        + " cambiado a "
                        + weight
                        + "."
        );

        return 1;
    }

    private static int replaceReward(
            CommandSourceStack source,
            String id,
            int rewardId
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        GachaDefinition.Reward reward =
                definition
                        .findReward(rewardId)
                        .orElse(null);

        if (reward == null) {
            source.sendFailure(
                    Component.literal(
                            "No existe ese premio."
                    )
            );

            return 0;
        }

        ServerPlayer player =
                source.getPlayerOrException();

        ItemStack hand =
                player.getMainHandItem();

        if (hand.isEmpty()) {
            source.sendFailure(
                    Component.literal(
                            "Debes sostener el nuevo objeto."
                    )
            );

            return 0;
        }

        reward.setItem(hand);

        GachaManager
                .get(source.getServer())
                .save();

        success(
                source,
                "Objeto del premio #"
                        + rewardId
                        + " reemplazado por "
                        + hand
                        .getHoverName()
                        .getString()
                        + "."
        );

        return 1;
    }

    private static int listRewards(
            CommandSourceStack source,
            String id
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        if (definition.getRewards().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "El gacha no tiene premios."
                    ),
                    false
            );

            return 1;
        }

        double total =
                definition.getTotalWeight();

        source.sendSuccess(
                () -> Component.literal(
                        "Premios de "
                                + definition.getId()
                                + ":"
                ).withStyle(ChatFormatting.GOLD),
                false
        );

        for (GachaDefinition.Reward reward :
                definition.getRewards()) {

            double chance =
                    total <= 0
                            ? 0
                            : reward.getWeight()
                            / total
                            * 100.0D;

            source.sendSuccess(
                    () -> Component.literal(
                            "#"
                                    + reward.getId()
                                    + " | "
                                    + reward.getAmount()
                                    + "x "
                                    + reward
                                    .getItem()
                                    .getHoverName()
                                    .getString()
                                    + " | peso "
                                    + reward.getWeight()
                                    + " | "
                                    + formatPercent(chance)
                                    + "%"
                    ),
                    false
            );
        }

        return 1;
    }

    // ---------------------------------------------------------------------
    // Llaves
    // ---------------------------------------------------------------------

    private static int setKey(
            CommandSourceStack source,
            String id,
            int amount
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        ServerPlayer player =
                source.getPlayerOrException();

        ItemStack hand =
                player.getMainHandItem();

        if (hand.isEmpty()) {
            source.sendFailure(
                    Component.literal(
                            "Debes tener la llave/ticket en la mano."
                    )
            );

            return 0;
        }

        definition.setKey(
                new GachaDefinition.KeyRequirement(
                        hand,
                        amount
                )
        );

        GachaManager
                .get(source.getServer())
                .save();

        HologramService.refreshGacha(
                source.getServer(),
                definition.getId()
        );

        success(
                source,
                "Llave establecida: "
                        + amount
                        + "x "
                        + hand
                        .getHoverName()
                        .getString()
        );

        return 1;
    }

    private static int clearKey(
            CommandSourceStack source,
            String id
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        definition.setKey(null);

        GachaManager
                .get(source.getServer())
                .save();

        HologramService.refreshGacha(
                source.getServer(),
                definition.getId()
        );

        success(
                source,
                "El gacha ahora es gratuito."
        );

        return 1;
    }

    // ---------------------------------------------------------------------
    // Cofres
    // ---------------------------------------------------------------------

    private static int setChest(
            CommandSourceStack source,
            String id
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        ServerPlayer player =
                source.getPlayerOrException();

        BlockPos pos =
                GachaBlockUtil.getLookedAtBlock(
                        player,
                        8.0D
                );

        if (pos == null
                || !GachaBlockUtil.isSupported(
                player
                        .serverLevel()
                        .getBlockState(pos)
        )) {
            source.sendFailure(
                    Component.literal(
                            "Debes mirar un cofre o barril."
                    )
            );

            return 0;
        }

        GachaManager manager =
                GachaManager.get(
                        source.getServer()
                );

        manager.setChest(
                player.serverLevel(),
                pos,
                definition.getId()
        );

        GachaChestLink link =
                manager.getChest(
                        player.serverLevel(),
                        pos
                );

        if (link != null) {
            HologramService.refresh(
                    source.getServer(),
                    link
            );
        }

        success(
                source,
                "Cofre vinculado al gacha '"
                        + definition.getId()
                        + "'."
        );

        return 1;
    }

    private static int removeChest(
            CommandSourceStack source
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        BlockPos pos =
                GachaBlockUtil.getLookedAtBlock(
                        player,
                        8.0D
                );

        if (pos == null) {
            source.sendFailure(
                    Component.literal(
                            "No estás mirando un bloque."
                    )
            );

            return 0;
        }

        GachaManager manager =
                GachaManager.get(
                        source.getServer()
                );

        GachaChestLink link =
                manager.getChest(
                        player.serverLevel(),
                        pos
                );

        if (link == null) {
            source.sendFailure(
                    Component.literal(
                            "Ese bloque no es un gacha."
                    )
            );

            return 0;
        }

        HologramService.remove(
                source.getServer(),
                link
        );

        manager.removeChest(
                player.serverLevel(),
                pos
        );

        success(
                source,
                "Gacha retirado de ese cofre."
        );

        return 1;
    }

    private static int chestInfo(
            CommandSourceStack source
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        BlockPos pos =
                GachaBlockUtil.getLookedAtBlock(
                        player,
                        8.0D
                );

        if (pos == null) {
            return 0;
        }

        GachaChestLink link =
                GachaManager
                        .get(source.getServer())
                        .getChest(
                                player.serverLevel(),
                                pos
                        );

        if (link == null) {
            source.sendFailure(
                    Component.literal(
                            "Ese bloque no tiene gacha asignado."
                    )
            );

            return 0;
        }

        success(
                source,
                "Este cofre usa el ID '"
                        + link.gachaId()
                        + "'."
        );

        return 1;
    }

    // ---------------------------------------------------------------------
    // Hologramas
    // ---------------------------------------------------------------------

    private static int hologramAdd(
            CommandSourceStack source,
            String id,
            String text
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        definition
                .getHologramLines()
                .add(text);

        saveAndRefresh(
                source.getServer(),
                definition
        );

        success(
                source,
                "Línea de holograma agregada."
        );

        return 1;
    }

    private static int hologramSet(
            CommandSourceStack source,
            String id,
            int line,
            String text
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        int index =
                line - 1;

        if (index < 0
                || index >= definition
                .getHologramLines()
                .size()) {

            source.sendFailure(
                    Component.literal(
                            "Esa línea no existe."
                    )
            );

            return 0;
        }

        definition
                .getHologramLines()
                .set(
                        index,
                        text
                );

        saveAndRefresh(
                source.getServer(),
                definition
        );

        success(
                source,
                "Línea "
                        + line
                        + " actualizada."
        );

        return 1;
    }

    private static int hologramRemove(
            CommandSourceStack source,
            String id,
            int line
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        int index =
                line - 1;

        if (index < 0
                || index >= definition
                .getHologramLines()
                .size()) {

            source.sendFailure(
                    Component.literal(
                            "Esa línea no existe."
                    )
            );

            return 0;
        }

        definition
                .getHologramLines()
                .remove(index);

        saveAndRefresh(
                source.getServer(),
                definition
        );

        success(
                source,
                "Línea eliminada."
        );

        return 1;
    }

    private static int hologramClear(
            CommandSourceStack source,
            String id
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        definition
                .getHologramLines()
                .clear();

        saveAndRefresh(
                source.getServer(),
                definition
        );

        success(
                source,
                "Holograma eliminado."
        );

        return 1;
    }

    // ---------------------------------------------------------------------
    // Partículas
    // ---------------------------------------------------------------------

    private static int setParticle(
            CommandSourceStack source,
            String id,
            ParticleOptions particle
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        definition
                .getParticleSettings()
                .setParticle(particle);

        definition
                .getParticleSettings()
                .setEnabled(true);

        GachaManager
                .get(source.getServer())
                .save();

        success(
                source,
                "Partícula establecida."
        );

        return 1;
    }

    private static int particleSettings(
            CommandSourceStack source,
            String id,
            int count,
            double radius,
            double speed
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        GachaDefinition.ParticleSettings settings =
                definition.getParticleSettings();

        settings.setCount(count);
        settings.setRadius(radius);
        settings.setSpeed(speed);

        GachaManager
                .get(source.getServer())
                .save();

        success(
                source,
                "Partículas: count="
                        + count
                        + ", radius="
                        + radius
                        + ", speed="
                        + speed
        );

        return 1;
    }

    private static int particleOff(
            CommandSourceStack source,
            String id
    ) {
        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        definition
                .getParticleSettings()
                .setEnabled(false);

        GachaManager
                .get(source.getServer())
                .save();

        success(
                source,
                "Partículas desactivadas."
        );

        return 1;
    }

    // ---------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------

    private static int test(
            CommandSourceStack source,
            String id
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        GachaDefinition definition =
                requireGacha(
                        source,
                        id
                );

        if (definition == null) {
            return 0;
        }

        ServerPlayer player =
                source.getPlayerOrException();

        /*
         * false = no consumir llave.
         */
        boolean success =
                GachaSpinManager.start(
                        player,
                        definition,
                        false
                );

        return success ? 1 : 0;
    }

    private static int save(
            CommandSourceStack source
    ) {
        GachaManager
                .get(source.getServer())
                .save();

        success(
                source,
                "CraftersGacha guardado."
        );

        return 1;
    }

    private static int reload(
            CommandSourceStack source
    ) {
        /*
         * Una tirada activa mantiene referencias a:
         *
         * - la definición del Gacha;
         * - el premio seleccionado;
         * - su contenedor visual.
         *
         * Recargar el JSON a mitad de una animación podría dejar
         * dos versiones de la configuración coexistiendo en memoria.
         * Por seguridad el reload se rechaza hasta que terminen.
         */
        int activeSpins =
                GachaSpinManager.getActiveSpinCount();

        if (activeSpins > 0) {
            source.sendFailure(
                    Component.literal(
                            "No se puede usar /gacha reload mientras hay "
                                    + activeSpins
                                    + (activeSpins == 1
                                    ? " tirada activa."
                                    : " tiradas activas.")
                    )
            );

            return 0;
        }

        GachaManager manager =
                GachaManager.get(
                        source.getServer()
                );

        manager.reload();

        HologramService.refreshAllLoaded(
                source.getServer()
        );

        success(
                source,
                "CraftersGacha recargado desde disco."
        );

        return 1;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static GachaDefinition requireGacha(
            CommandSourceStack source,
            String id
    ) {
        GachaDefinition definition =
                GachaManager
                        .get(source.getServer())
                        .getGacha(id);

        if (definition == null) {
            source.sendFailure(
                    Component.literal(
                            "No existe un gacha con ID '"
                                    + id
                                    + "'."
                    )
            );
        }

        return definition;
    }

    private static void saveAndRefresh(
            MinecraftServer server,
            GachaDefinition definition
    ) {
        GachaManager
                .get(server)
                .save();

        HologramService.refreshGacha(
                server,
                definition.getId()
        );
    }

    private static void success(
            CommandSourceStack source,
            String message
    ) {
        source.sendSuccess(
                () -> Component.literal(message)
                        .withStyle(
                                ChatFormatting.GREEN
                        ),
                false
        );
    }

    private static String formatPercent(
            double number
    ) {
        return String.format(
                Locale.ROOT,
                "%.2f",
                number
        );
    }
}