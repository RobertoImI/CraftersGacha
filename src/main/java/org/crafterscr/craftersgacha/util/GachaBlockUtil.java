package org.crafterscr.craftersgacha.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Utilidades relacionadas con los bloques que pueden
 * convertirse en gachas.
 */
public final class GachaBlockUtil {

    private GachaBlockUtil() {
    }

    /**
     * Primera versión:
     *
     * - cofres
     * - cofres trampa (heredan de ChestBlock)
     * - barriles
     */
    public static boolean isSupported(
            BlockState state
    ) {
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock;
    }

    /**
     * Obtiene el bloque que está mirando el jugador.
     */
    public static BlockPos getLookedAtBlock(
            ServerPlayer player,
            double distance
    ) {
        Vec3 start =
                player.getEyePosition();

        Vec3 direction =
                player.getViewVector(1.0F);

        Vec3 end =
                start.add(
                        direction.scale(distance)
                );

        BlockHitResult hit =
                player.serverLevel().clip(
                        new ClipContext(
                                start,
                                end,
                                ClipContext.Block.OUTLINE,
                                ClipContext.Fluid.NONE,
                                player
                        )
                );

        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        return hit.getBlockPos();
    }
}