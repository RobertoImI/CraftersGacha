package org.crafterscr.craftersgacha.data;

import net.minecraft.core.BlockPos;

/**
 * Representa un bloque del mundo que fue convertido
 * en un punto de gacha.
 */
public record GachaChestLink(
        String dimension,
        BlockPos pos,
        String gachaId
) {
}