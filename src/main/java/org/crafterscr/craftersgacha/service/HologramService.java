package org.crafterscr.craftersgacha.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.crafterscr.craftersgacha.data.GachaChestLink;
import org.crafterscr.craftersgacha.data.GachaDefinition;
import org.crafterscr.craftersgacha.data.GachaManager;
import org.crafterscr.craftersgacha.util.GachaBlockUtil;
import org.crafterscr.craftersgacha.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Maneja todos los hologramas de CraftersGacha.
 *
 * Cada cofre utiliza un único TextDisplay con varias líneas.
 */
public final class HologramService {

    private static final String GLOBAL_TAG =
            "craftersgacha_hologram";

    private HologramService() {
    }

    public static void ensureAllLoaded(
            MinecraftServer server
    ) {
        GachaManager manager =
                GachaManager.get(server);

        List<GachaChestLink> invalid =
                new ArrayList<>();

        for (GachaChestLink link :
                List.copyOf(manager.getChestLinks())) {

            ServerLevel level =
                    getLevel(
                            server,
                            link.dimension()
                    );

            if (level == null) {
                continue;
            }

            // No forzamos la carga de chunks solamente por un holograma.
            if (!level.hasChunkAt(link.pos())) {
                continue;
            }

            if (!GachaBlockUtil.isSupported(
                    level.getBlockState(link.pos())
            )) {
                remove(
                        server,
                        link
                );

                invalid.add(link);

                continue;
            }

            GachaDefinition definition =
                    manager.getGacha(
                            link.gachaId()
                    );

            if (definition == null) {
                continue;
            }

            ensure(
                    level,
                    link,
                    definition
            );
        }

        if (!invalid.isEmpty()) {
            for (GachaChestLink link : invalid) {
                manager.removeChest(link);
            }

            manager.save();
        }
    }

    /**
     * Fuerza la reconstrucción de todos los hologramas
     * que estén en chunks actualmente cargados.
     */
    public static void refreshAllLoaded(
            MinecraftServer server
    ) {
        GachaManager manager =
                GachaManager.get(server);

        for (GachaChestLink link :
                manager.getChestLinks()) {

            ServerLevel level =
                    getLevel(
                            server,
                            link.dimension()
                    );

            if (level == null
                    || !level.hasChunkAt(link.pos())) {
                continue;
            }

            remove(
                    level,
                    link.pos()
            );

            GachaDefinition definition =
                    manager.getGacha(
                            link.gachaId()
                    );

            if (definition != null) {
                spawn(
                        level,
                        link,
                        definition
                );
            }
        }
    }

    /**
     * Actualiza todos los cofres que utilizan un mismo ID.
     */
    public static void refreshGacha(
            MinecraftServer server,
            String gachaId
    ) {
        GachaManager manager =
                GachaManager.get(server);

        GachaDefinition definition =
                manager.getGacha(gachaId);

        if (definition == null) {
            return;
        }

        for (GachaChestLink link :
                manager.getChestLinks()) {

            if (!link.gachaId().equals(gachaId)) {
                continue;
            }

            ServerLevel level =
                    getLevel(
                            server,
                            link.dimension()
                    );

            if (level == null
                    || !level.hasChunkAt(link.pos())) {
                continue;
            }

            remove(
                    level,
                    link.pos()
            );

            spawn(
                    level,
                    link,
                    definition
            );
        }
    }

    public static void refresh(
            MinecraftServer server,
            GachaChestLink link
    ) {
        ServerLevel level =
                getLevel(
                        server,
                        link.dimension()
                );

        if (level == null
                || !level.hasChunkAt(link.pos())) {
            return;
        }

        remove(
                level,
                link.pos()
        );

        GachaDefinition definition =
                GachaManager
                        .get(server)
                        .getGacha(
                                link.gachaId()
                        );

        if (definition != null) {
            spawn(
                    level,
                    link,
                    definition
            );
        }
    }

    public static void remove(
            MinecraftServer server,
            GachaChestLink link
    ) {
        ServerLevel level =
                getLevel(
                        server,
                        link.dimension()
                );

        if (level != null
                && level.hasChunkAt(link.pos())) {

            remove(
                    level,
                    link.pos()
            );
        }
    }

    private static void ensure(
            ServerLevel level,
            GachaChestLink link,
            GachaDefinition definition
    ) {
        String baseTag =
                getBaseTag(
                        link.pos()
                );

        String expectedHash =
                getHashTag(
                        definition
                );

        AABB area =
                getSearchArea(
                        link.pos()
                );

        List<Display.TextDisplay> holograms =
                level.getEntitiesOfClass(
                        Display.TextDisplay.class,
                        area,
                        entity ->
                                entity.getTags()
                                        .contains(baseTag)
                );

        boolean valid =
                holograms.size() == 1
                        && holograms
                        .getFirst()
                        .getTags()
                        .contains(expectedHash);

        if (valid) {
            return;
        }

        for (Display.TextDisplay display :
                holograms) {
            display.discard();
        }

        spawn(
                level,
                link,
                definition
        );
    }

    private static void spawn(
            ServerLevel level,
            GachaChestLink link,
            GachaDefinition definition
    ) {
        if (definition
                .getHologramLines()
                .isEmpty()) {
            return;
        }

        MutableComponent component =
                buildComponent(
                        definition
                );

        Display.TextDisplay display =
                new Display.TextDisplay(
                        EntityType.TEXT_DISPLAY,
                        level
                );

        /*
         * Los setters específicos de TextDisplay son privados
         * en 1.21.1.
         *
         * Por eso cargamos las propiedades vanilla mediante NBT.
         */
        CompoundTag tag =
                new CompoundTag();

        tag.putString(
                "text",
                Component.Serializer.toJson(
                        component,
                        level.registryAccess()
                )
        );

        tag.putString(
                "billboard",
                "center"
        );

        tag.putString(
                "alignment",
                "center"
        );

        tag.putBoolean(
                "shadow",
                true
        );

        tag.putBoolean(
                "see_through",
                false
        );

        tag.putInt(
                "background",
                0
        );

        tag.putInt(
                "line_width",
                300
        );

        display.load(tag);

        display.setNoGravity(true);

        display.setPos(
                link.pos().getX() + 0.5D,
                link.pos().getY() + 1.65D,
                link.pos().getZ() + 0.5D
        );

        display.addTag(GLOBAL_TAG);

        display.addTag(
                getBaseTag(
                        link.pos()
                )
        );

        display.addTag(
                getHashTag(
                        definition
                )
        );

        level.addFreshEntity(display);
    }

    private static MutableComponent buildComponent(
            GachaDefinition definition
    ) {
        MutableComponent result =
                Component.empty();

        List<String> lines =
                definition.getHologramLines();

        for (int i = 0; i < lines.size(); i++) {
            String line =
                    replaceVariables(
                            lines.get(i),
                            definition
                    );

            result.append(
                    TextUtil.colorize(line)
            );

            if (i < lines.size() - 1) {
                result.append(
                        Component.literal("\n")
                );
            }
        }

        return result;
    }

    private static String replaceVariables(
            String line,
            GachaDefinition definition
    ) {
        String keyDescription;

        if (definition.getKey() == null) {
            keyDescription = "Gratis";
        } else {
            keyDescription =
                    definition
                            .getKey()
                            .getAmount()
                            + "x "
                            + definition
                            .getKey()
                            .getItem()
                            .getHoverName()
                            .getString();
        }

        return line
                .replace(
                        "{id}",
                        definition.getId()
                )
                .replace(
                        "{key}",
                        keyDescription
                )
                .replace(
                        "{key_amount}",
                        definition.getKey() == null
                                ? "0"
                                : Integer.toString(
                                definition
                                        .getKey()
                                        .getAmount()
                        )
                );
    }

    private static String getBaseTag(
            BlockPos pos
    ) {
        return "cg_holo_"
                + Long.toUnsignedString(
                pos.asLong(),
                36
        );
    }

    /**
     * Si cambia:
     *
     * - texto
     * - llave
     * - cantidad
     *
     * cambia el hash y el holograma se reconstruye.
     */
    private static String getHashTag(
            GachaDefinition definition
    ) {
        int keyHash = 0;

        if (definition.getKey() != null) {
            keyHash =
                    Objects.hash(
                            ItemStackHash.hash(
                                    definition
                                            .getKey()
                                            .getItem()
                            ),
                            definition
                                    .getKey()
                                    .getAmount()
                    );
        }

        int hash =
                Objects.hash(
                        definition.getHologramLines(),
                        keyHash
                );

        return "cg_hash_"
                + Integer.toHexString(hash);
    }

    private static AABB getSearchArea(
            BlockPos pos
    ) {
        return new AABB(
                pos.getX() - 1.0D,
                pos.getY(),
                pos.getZ() - 1.0D,

                pos.getX() + 2.0D,
                pos.getY() + 4.0D,
                pos.getZ() + 2.0D
        );
    }

    private static void remove(
            ServerLevel level,
            BlockPos pos
    ) {
        String baseTag =
                getBaseTag(pos);

        for (Display.TextDisplay display :
                level.getEntitiesOfClass(
                        Display.TextDisplay.class,
                        getSearchArea(pos),
                        entity ->
                                entity.getTags()
                                        .contains(baseTag)
                )) {

            display.discard();
        }
    }

    private static ServerLevel getLevel(
            MinecraftServer server,
            String dimension
    ) {
        ResourceLocation location =
                ResourceLocation.tryParse(
                        dimension
                );

        if (location == null) {
            return null;
        }

        ResourceKey<Level> key =
                ResourceKey.create(
                        Registries.DIMENSION,
                        location
                );

        return server.getLevel(key);
    }

    /**
     * Wrapper pequeño para no hacer depender
     * el código exterior de la implementación del hash.
     */
    private static final class ItemStackHash {

        private static int hash(
                net.minecraft.world.item.ItemStack stack
        ) {
            return net.minecraft.world.item.ItemStack
                    .hashItemAndComponents(stack);
        }
    }
}