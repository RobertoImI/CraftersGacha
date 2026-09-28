package org.crafterscr.craftersgacha.data;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import org.crafterscr.craftersgacha.CraftersGacha;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Base de datos central de CraftersGacha.
 *
 * Se guarda en:
 *
 * world/craftersgacha/gachas.json
 *
 * El JSON es legible, pero la administración normal se hará
 * desde comandos.
 */
public final class GachaManager {

    private static GachaManager INSTANCE;

    private final MinecraftServer server;

    private final Map<String, GachaDefinition> gachas =
            new LinkedHashMap<>();

    private final Map<String, GachaChestLink> chestLinks =
            new LinkedHashMap<>();

    private final Gson gson =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private final Path file;

    private GachaManager(MinecraftServer server) {
        this.server = server;

        this.file = server
                .getWorldPath(LevelResource.ROOT)
                .resolve("craftersgacha")
                .resolve("gachas.json");

        load();
    }

    /**
     * Devuelve la instancia correspondiente al servidor actual.
     */
    public static synchronized GachaManager get(MinecraftServer server) {
        if (INSTANCE == null || INSTANCE.server != server) {
            INSTANCE = new GachaManager(server);
        }

        return INSTANCE;
    }

    public static synchronized void shutdown(MinecraftServer server) {
        if (INSTANCE != null && INSTANCE.server == server) {
            INSTANCE.save();
            INSTANCE = null;
        }
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Collection<String> getIds() {
        return Collections.unmodifiableSet(gachas.keySet());
    }

    public Collection<GachaDefinition> getGachas() {
        return Collections.unmodifiableCollection(gachas.values());
    }

    public Collection<GachaChestLink> getChestLinks() {
        return Collections.unmodifiableCollection(chestLinks.values());
    }

    public GachaDefinition getGacha(String id) {
        return gachas.get(normalizeId(id));
    }

    public boolean hasGacha(String id) {
        return gachas.containsKey(normalizeId(id));
    }

    public GachaDefinition createGacha(String rawId) {
        String id = normalizeId(rawId);

        if (gachas.containsKey(id)) {
            return null;
        }

        GachaDefinition definition =
                new GachaDefinition(id);

        gachas.put(id, definition);

        save();

        return definition;
    }

    /**
     * Borra el gacha y todos los cofres que lo utilizaban.
     *
     * Devuelve los cofres eliminados para que el servicio
     * pueda retirar sus hologramas.
     */
    public List<GachaChestLink> deleteGacha(String rawId) {
        String id = normalizeId(rawId);

        if (gachas.remove(id) == null) {
            return List.of();
        }

        List<GachaChestLink> removed =
                chestLinks.values()
                        .stream()
                        .filter(link -> link.gachaId().equals(id))
                        .toList();

        removed.forEach(link ->
                chestLinks.remove(chestKey(link.dimension(), link.pos()))
        );

        save();

        return removed;
    }

    /**
     * Vincula un cofre o barril con un Gacha.
     *
     * En un double chest guardamos una sola asociación para las dos mitades.
     * La posición elegida es determinista, de modo que hacer click en cualquiera
     * de las dos partes siempre resuelva al mismo Gacha.
     */
    public void setChest(
            ServerLevel level,
            net.minecraft.core.BlockPos pos,
            String rawGachaId
    ) {
        String gachaId = normalizeId(rawGachaId);

        String dimension =
                level.dimension().location().toString();

        net.minecraft.core.BlockPos connected =
                getConnectedChestPos(
                        level,
                        pos
                );

        /*
         * Limpia asociaciones antiguas de cualquiera de las dos mitades.
         * Esto también migra de forma natural cofres dobles creados antes
         * de este parche cuando el admin vuelve a asignarlos.
         */
        chestLinks.remove(
                chestKey(
                        dimension,
                        pos
                )
        );

        if (connected != null) {
            chestLinks.remove(
                    chestKey(
                            dimension,
                            connected
                    )
            );
        }

        net.minecraft.core.BlockPos storagePos =
                getCanonicalChestPos(
                        level,
                        pos
                );

        GachaChestLink link =
                new GachaChestLink(
                        dimension,
                        storagePos.immutable(),
                        gachaId
                );

        chestLinks.put(
                chestKey(
                        dimension,
                        storagePos
                ),
                link
        );

        save();
    }

    /**
     * Busca un Gacha asociado a un bloque.
     *
     * Si el bloque es una mitad de un double chest también revisa
     * automáticamente la otra mitad. Gracias a esto:
     *
     * - ambas mitades abren el mismo Gacha;
     * - ambas mitades quedan protegidas;
     * - datos creados antes del parche siguen funcionando.
     */
    public GachaChestLink getChest(
            ServerLevel level,
            net.minecraft.core.BlockPos pos
    ) {
        String dimension =
                level.dimension().location().toString();

        GachaChestLink direct =
                chestLinks.get(
                        chestKey(
                                dimension,
                                pos
                        )
                );

        if (direct != null) {
            return direct;
        }

        net.minecraft.core.BlockPos connected =
                getConnectedChestPos(
                        level,
                        pos
                );

        if (connected == null) {
            return null;
        }

        return chestLinks.get(
                chestKey(
                        dimension,
                        connected
                )
        );
    }

    /**
     * Elimina la asociación completa de un cofre.
     *
     * En double chest se limpian las dos posiciones para impedir que
     * quede una mitad huérfana vinculada al Gacha.
     */
    public GachaChestLink removeChest(
            ServerLevel level,
            net.minecraft.core.BlockPos pos
    ) {
        String dimension =
                level.dimension().location().toString();

        GachaChestLink removed =
                getChest(
                        level,
                        pos
                );

        if (removed == null) {
            return null;
        }

        net.minecraft.core.BlockPos connected =
                getConnectedChestPos(
                        level,
                        pos
                );

        chestLinks.remove(
                chestKey(
                        dimension,
                        removed.pos()
                )
        );

        chestLinks.remove(
                chestKey(
                        dimension,
                        pos
                )
        );

        if (connected != null) {
            chestLinks.remove(
                    chestKey(
                            dimension,
                            connected
                    )
            );
        }

        save();

        return removed;
    }

    public void removeChest(GachaChestLink link) {
        chestLinks.remove(
                chestKey(
                        link.dimension(),
                        link.pos()
                )
        );
    }

    public void reload() {
        gachas.clear();
        chestLinks.clear();

        load();
    }

    /**
     * Devuelve la otra mitad de un double chest.
     *
     * Para cofres simples, barriles u otros bloques devuelve null.
     */
    private static net.minecraft.core.BlockPos getConnectedChestPos(
            ServerLevel level,
            net.minecraft.core.BlockPos pos
    ) {
        var state =
                level.getBlockState(pos);

        if (!(state.getBlock() instanceof ChestBlock)
                || !state.hasProperty(ChestBlock.TYPE)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {

            return null;
        }

        net.minecraft.core.BlockPos connected =
                pos.relative(
                        ChestBlock.getConnectedDirection(
                                state
                        )
                );

        /*
         * Validación defensiva: solamente aceptamos la posición vecina
         * si realmente continúa siendo un cofre.
         */
        if (!(level
                .getBlockState(connected)
                .getBlock()
                instanceof ChestBlock)) {

            return null;
        }

        return connected;
    }

    /**
     * Elige siempre la misma mitad para almacenar un double chest.
     *
     * No afecta visualmente al cofre; solamente evita guardar
     * dos asociaciones diferentes para un mismo double chest.
     */
    private static net.minecraft.core.BlockPos getCanonicalChestPos(
            ServerLevel level,
            net.minecraft.core.BlockPos pos
    ) {
        net.minecraft.core.BlockPos connected =
                getConnectedChestPos(
                        level,
                        pos
                );

        if (connected == null) {
            return pos;
        }

        return Long.compare(
                pos.asLong(),
                connected.asLong()
        ) <= 0
                ? pos
                : connected;
    }

    private static String normalizeId(String id) {
        return id
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String chestKey(
            String dimension,
            net.minecraft.core.BlockPos pos
    ) {
        return dimension + "|" + pos.asLong();
    }

    // =====================================================================
    // Guardado
    // =====================================================================

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());

            JsonObject root = new JsonObject();

            root.addProperty("version", 1);

            JsonObject gachaObject = new JsonObject();

            for (GachaDefinition definition : gachas.values()) {
                JsonObject object = new JsonObject();

                object.addProperty(
                        "nextRewardId",
                        definition.getNextRewardId()
                );

                // ---------------------------------------------------------
                // Premios
                // ---------------------------------------------------------

                JsonArray rewards = new JsonArray();

                for (GachaDefinition.Reward reward :
                        definition.getRewards()) {

                    JsonObject rewardJson =
                            new JsonObject();

                    rewardJson.addProperty(
                            "id",
                            reward.getId()
                    );

                    rewardJson.addProperty(
                            "amount",
                            reward.getAmount()
                    );

                    rewardJson.addProperty(
                            "weight",
                            reward.getWeight()
                    );

                    rewardJson.add(
                            "item",
                            encodeItem(reward.getItem())
                    );

                    rewards.add(rewardJson);
                }

                object.add("rewards", rewards);

                // ---------------------------------------------------------
                // Llave
                // ---------------------------------------------------------

                if (definition.getKey() != null) {
                    JsonObject key =
                            new JsonObject();

                    key.addProperty(
                            "amount",
                            definition.getKey().getAmount()
                    );

                    key.add(
                            "item",
                            encodeItem(
                                    definition.getKey().getItem()
                            )
                    );

                    object.add("key", key);
                }

                // ---------------------------------------------------------
                // Holograma
                // ---------------------------------------------------------

                JsonArray hologram =
                        new JsonArray();

                for (String line :
                        definition.getHologramLines()) {
                    hologram.add(line);
                }

                object.add(
                        "hologram",
                        hologram
                );

                // ---------------------------------------------------------
                // Partículas
                // ---------------------------------------------------------

                GachaDefinition.ParticleSettings settings =
                        definition.getParticleSettings();

                JsonObject particle =
                        new JsonObject();

                particle.addProperty(
                        "enabled",
                        settings.isEnabled()
                );

                particle.addProperty(
                        "count",
                        settings.getCount()
                );

                particle.addProperty(
                        "radius",
                        settings.getRadius()
                );

                particle.addProperty(
                        "speed",
                        settings.getSpeed()
                );

                particle.add(
                        "options",
                        encodeParticle(
                                settings.getParticle()
                        )
                );

                object.add(
                        "particle",
                        particle
                );

                gachaObject.add(
                        definition.getId(),
                        object
                );
            }

            root.add(
                    "gachas",
                    gachaObject
            );

            // -------------------------------------------------------------
            // Cofres
            // -------------------------------------------------------------

            JsonArray chests =
                    new JsonArray();

            for (GachaChestLink link :
                    chestLinks.values()) {

                JsonObject object =
                        new JsonObject();

                object.addProperty(
                        "dimension",
                        link.dimension()
                );

                object.addProperty(
                        "x",
                        link.pos().getX()
                );

                object.addProperty(
                        "y",
                        link.pos().getY()
                );

                object.addProperty(
                        "z",
                        link.pos().getZ()
                );

                object.addProperty(
                        "gacha",
                        link.gachaId()
                );

                chests.add(object);
            }

            root.add(
                    "chests",
                    chests
            );

            Files.writeString(
                    file,
                    gson.toJson(root),
                    StandardCharsets.UTF_8
            );

        } catch (Exception exception) {
            CraftersGacha.LOGGER.error(
                    "No se pudo guardar CraftersGacha.",
                    exception
            );
        }
    }

    // =====================================================================
    // Carga
    // =====================================================================

    private synchronized void load() {
        if (!Files.exists(file)) {
            return;
        }

        try {
            String text =
                    Files.readString(
                            file,
                            StandardCharsets.UTF_8
                    );

            JsonObject root =
                    JsonParser
                            .parseString(text)
                            .getAsJsonObject();

            // -------------------------------------------------------------
            // Gachas
            // -------------------------------------------------------------

            JsonObject gachaObject =
                    root.has("gachas")
                            ? root.getAsJsonObject("gachas")
                            : new JsonObject();

            for (Map.Entry<String, JsonElement> entry :
                    gachaObject.entrySet()) {

                String id =
                        normalizeId(entry.getKey());

                JsonObject object =
                        entry.getValue()
                                .getAsJsonObject();

                GachaDefinition definition =
                        new GachaDefinition(id);

                // ---------------------------------------------------------
                // Premios
                // ---------------------------------------------------------

                if (object.has("rewards")) {
                    for (JsonElement element :
                            object.getAsJsonArray("rewards")) {

                        JsonObject rewardJson =
                                element.getAsJsonObject();

                        ItemStack item =
                                decodeItem(
                                        rewardJson.get("item")
                                );

                        if (item.isEmpty()) {
                            continue;
                        }

                        int rewardId =
                                rewardJson
                                        .get("id")
                                        .getAsInt();

                        int amount =
                                rewardJson
                                        .get("amount")
                                        .getAsInt();

                        double weight =
                                rewardJson
                                        .get("weight")
                                        .getAsDouble();

                        definition.addLoadedReward(
                                new GachaDefinition.Reward(
                                        rewardId,
                                        item,
                                        amount,
                                        weight
                                )
                        );
                    }
                }

                if (object.has("nextRewardId")) {
                    definition.setNextRewardId(
                            Math.max(
                                    definition.getNextRewardId(),
                                    object.get("nextRewardId")
                                            .getAsInt()
                            )
                    );
                }

                // ---------------------------------------------------------
                // Llave
                // ---------------------------------------------------------

                if (object.has("key")) {
                    JsonObject keyJson =
                            object.getAsJsonObject("key");

                    ItemStack keyItem =
                            decodeItem(
                                    keyJson.get("item")
                            );

                    if (!keyItem.isEmpty()) {
                        definition.setKey(
                                new GachaDefinition.KeyRequirement(
                                        keyItem,
                                        keyJson
                                                .get("amount")
                                                .getAsInt()
                                )
                        );
                    }
                }

                // ---------------------------------------------------------
                // Holograma
                // ---------------------------------------------------------

                if (object.has("hologram")) {
                    definition
                            .getHologramLines()
                            .clear();

                    for (JsonElement line :
                            object.getAsJsonArray("hologram")) {

                        definition
                                .getHologramLines()
                                .add(line.getAsString());
                    }
                }

                // ---------------------------------------------------------
                // Partículas
                // ---------------------------------------------------------

                if (object.has("particle")) {
                    JsonObject particleJson =
                            object.getAsJsonObject("particle");

                    GachaDefinition.ParticleSettings settings =
                            definition.getParticleSettings();

                    settings.setEnabled(
                            particleJson.has("enabled")
                                    && particleJson
                                    .get("enabled")
                                    .getAsBoolean()
                    );

                    if (particleJson.has("count")) {
                        settings.setCount(
                                particleJson
                                        .get("count")
                                        .getAsInt()
                        );
                    }

                    if (particleJson.has("radius")) {
                        settings.setRadius(
                                particleJson
                                        .get("radius")
                                        .getAsDouble()
                        );
                    }

                    if (particleJson.has("speed")) {
                        settings.setSpeed(
                                particleJson
                                        .get("speed")
                                        .getAsDouble()
                        );
                    }

                    if (particleJson.has("options")) {
                        ParticleOptions options =
                                decodeParticle(
                                        particleJson
                                                .get("options")
                                );

                        if (options != null) {
                            settings.setParticle(options);
                        }
                    }
                }

                gachas.put(
                        id,
                        definition
                );
            }

            // -------------------------------------------------------------
            // Cofres
            // -------------------------------------------------------------

            if (root.has("chests")) {
                for (JsonElement element :
                        root.getAsJsonArray("chests")) {

                    JsonObject object =
                            element.getAsJsonObject();

                    String dimension =
                            object
                                    .get("dimension")
                                    .getAsString();

                    net.minecraft.core.BlockPos pos =
                            new net.minecraft.core.BlockPos(
                                    object.get("x").getAsInt(),
                                    object.get("y").getAsInt(),
                                    object.get("z").getAsInt()
                            );

                    String gacha =
                            normalizeId(
                                    object
                                            .get("gacha")
                                            .getAsString()
                            );

                    GachaChestLink link =
                            new GachaChestLink(
                                    dimension,
                                    pos,
                                    gacha
                            );

                    chestLinks.put(
                            chestKey(
                                    dimension,
                                    pos
                            ),
                            link
                    );
                }
            }

            CraftersGacha.LOGGER.info(
                    "CraftersGacha: {} gachas y {} cofres cargados.",
                    gachas.size(),
                    chestLinks.size()
            );

        } catch (IOException | RuntimeException exception) {
            CraftersGacha.LOGGER.error(
                    "No se pudo cargar {}.",
                    file,
                    exception
            );
        }
    }

    // =====================================================================
    // Codec ItemStack
    // =====================================================================

    private RegistryOps<JsonElement> jsonOps() {
        return RegistryOps.create(
                JsonOps.INSTANCE,
                server.registryAccess()
        );
    }

    private JsonElement encodeItem(ItemStack stack) {
        return ItemStack.CODEC
                .encodeStart(
                        jsonOps(),
                        stack
                )
                .resultOrPartial(message ->
                        CraftersGacha.LOGGER.error(
                                "Error serializando ItemStack: {}",
                                message
                        )
                )
                .orElse(JsonNull.INSTANCE);
    }

    private ItemStack decodeItem(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return ItemStack.EMPTY;
        }

        return ItemStack.CODEC
                .parse(
                        jsonOps(),
                        json
                )
                .resultOrPartial(message ->
                        CraftersGacha.LOGGER.error(
                                "Error leyendo ItemStack: {}",
                                message
                        )
                )
                .orElse(ItemStack.EMPTY);
    }

    // =====================================================================
    // Codec ParticleOptions
    // =====================================================================

    private JsonElement encodeParticle(
            ParticleOptions particle
    ) {
        return ParticleTypes.CODEC
                .encodeStart(
                        jsonOps(),
                        particle
                )
                .resultOrPartial(message ->
                        CraftersGacha.LOGGER.error(
                                "Error serializando partícula: {}",
                                message
                        )
                )
                .orElse(JsonNull.INSTANCE);
    }

    private ParticleOptions decodeParticle(
            JsonElement json
    ) {
        if (json == null || json.isJsonNull()) {
            return ParticleTypes.ENCHANT;
        }

        return ParticleTypes.CODEC
                .parse(
                        jsonOps(),
                        json
                )
                .resultOrPartial(message ->
                        CraftersGacha.LOGGER.error(
                                "Error leyendo partícula: {}",
                                message
                        )
                )
                .orElse(ParticleTypes.ENCHANT);
    }
}