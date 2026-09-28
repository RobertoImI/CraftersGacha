package org.crafterscr.craftersgacha.service;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.crafterscr.craftersgacha.data.GachaChestLink;
import org.crafterscr.craftersgacha.data.GachaDefinition;
import org.crafterscr.craftersgacha.data.GachaManager;

/**
 * Genera partículas alrededor de los cofres.
 *
 * Se ejecuta cada 10 ticks, no cada tick,
 * para mantener el impacto bajo.
 */
public final class ParticleService {

    private ParticleService() {
    }

    public static void tick(
            MinecraftServer server
    ) {
        GachaManager manager =
                GachaManager.get(server);

        for (GachaChestLink link :
                manager.getChestLinks()) {

            GachaDefinition definition =
                    manager.getGacha(
                            link.gachaId()
                    );

            if (definition == null) {
                continue;
            }

            GachaDefinition.ParticleSettings settings =
                    definition.getParticleSettings();

            if (!settings.isEnabled()) {
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

            level.sendParticles(
                    settings.getParticle(),

                    link.pos().getX() + 0.5D,
                    link.pos().getY() + 1.0D,
                    link.pos().getZ() + 0.5D,

                    settings.getCount(),

                    settings.getRadius(),
                    0.25D,
                    settings.getRadius(),

                    settings.getSpeed()
            );
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

        return server.getLevel(
                ResourceKey.create(
                        Registries.DIMENSION,
                        location
                )
        );
    }
}