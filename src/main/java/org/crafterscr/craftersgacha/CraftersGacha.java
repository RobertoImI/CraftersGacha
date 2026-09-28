package org.crafterscr.craftersgacha;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Clase principal de CraftersGacha.
 *
 * No registramos bloques, ítems, entidades personalizadas ni pantallas.
 * Esto es intencional para mantener el mod lo más server-side posible.
 */
@Mod(CraftersGacha.MOD_ID)
public final class CraftersGacha {

    public static final String MOD_ID = "craftersgacha";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CraftersGacha(IEventBus modBus, ModContainer modContainer) {
        LOGGER.info("CraftersGacha cargado.");
    }
}