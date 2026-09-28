package org.crafterscr.craftersgacha.data;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Contiene toda la configuración de un ID de gacha.
 *
 * Ejemplo:
 *
 * pokemon:
 * - premios
 * - pesos
 * - llave
 * - holograma
 * - partículas
 */
public final class GachaDefinition {

    private final String id;

    private final List<Reward> rewards = new ArrayList<>();

    /**
     * Los premios tienen IDs permanentes:
     *
     * 1, 2, 3...
     *
     * Esto evita depender del orden de la lista.
     */
    private int nextRewardId = 1;

    private KeyRequirement key;

    private final List<String> hologramLines = new ArrayList<>();

    private final ParticleSettings particleSettings = new ParticleSettings();

    public GachaDefinition(String id) {
        this.id = id;

        // Holograma por defecto.
        hologramLines.add("&6&lGACHA {id}");
        hologramLines.add("&eClick derecho para jugar");
        hologramLines.add("&7Costo: {key}");
    }

    public String getId() {
        return id;
    }

    public List<Reward> getRewards() {
        return rewards;
    }

    public KeyRequirement getKey() {
        return key;
    }

    public void setKey(KeyRequirement key) {
        this.key = key;
    }

    public List<String> getHologramLines() {
        return hologramLines;
    }

    public ParticleSettings getParticleSettings() {
        return particleSettings;
    }

    public int getNextRewardId() {
        return nextRewardId;
    }

    public void setNextRewardId(int nextRewardId) {
        this.nextRewardId = Math.max(1, nextRewardId);
    }

    /**
     * Agrega un premio nuevo y devuelve su ID.
     */
    public int addReward(ItemStack template, int amount, double weight) {
        int rewardId = nextRewardId++;

        rewards.add(
                new Reward(
                        rewardId,
                        template.copyWithCount(1),
                        amount,
                        weight
                )
        );

        return rewardId;
    }

    /**
     * Se usa al cargar desde JSON.
     */
    public void addLoadedReward(Reward reward) {
        rewards.add(reward);

        nextRewardId = Math.max(
                nextRewardId,
                reward.getId() + 1
        );
    }

    public Optional<Reward> findReward(int rewardId) {
        return rewards.stream()
                .filter(reward -> reward.getId() == rewardId)
                .findFirst();
    }

    public boolean removeReward(int rewardId) {
        return rewards.removeIf(reward -> reward.getId() == rewardId);
    }

    /**
     * Peso total.
     *
     * Por ejemplo:
     *
     * carne = 30
     * masterball = 5
     *
     * total = 35
     */
    public double getTotalWeight() {
        return rewards.stream()
                .mapToDouble(Reward::getWeight)
                .filter(weight -> weight > 0)
                .sum();
    }

    /**
     * Selección aleatoria ponderada.
     *
     * Este mismo método se usa tanto para:
     * - elegir el premio verdadero;
     * - llenar visualmente la ruleta.
     *
     * Por eso los objetos de peso alto aparecen más veces
     * durante la animación.
     */
    public Reward pickWeighted(RandomGenerator random) {
        double total = getTotalWeight();

        if (rewards.isEmpty() || total <= 0) {
            return null;
        }

        double roll = random.nextDouble(total);

        double accumulated = 0.0;

        for (Reward reward : rewards) {
            if (reward.getWeight() <= 0) {
                continue;
            }

            accumulated += reward.getWeight();

            if (roll < accumulated) {
                return reward;
            }
        }

        return rewards.getLast();
    }

    // ---------------------------------------------------------------------
    // Premio
    // ---------------------------------------------------------------------

    public static final class Reward {

        private final int id;

        /**
         * Siempre almacenamos el ItemStack base con count 1.
         * La cantidad real entregada está en amount.
         */
        private ItemStack item;

        private int amount;

        private double weight;

        public Reward(
                int id,
                ItemStack item,
                int amount,
                double weight
        ) {
            this.id = id;
            this.item = item.copyWithCount(1);
            this.amount = amount;
            this.weight = weight;
        }

        public int getId() {
            return id;
        }

        public ItemStack getItem() {
            return item;
        }

        public void setItem(ItemStack item) {
            this.item = item.copyWithCount(1);
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public double getWeight() {
            return weight;
        }

        public void setWeight(double weight) {
            this.weight = weight;
        }

        public Reward copy() {
            return new Reward(
                    id,
                    item.copy(),
                    amount,
                    weight
            );
        }
    }

    // ---------------------------------------------------------------------
    // Llave
    // ---------------------------------------------------------------------

    public static final class KeyRequirement {

        private final ItemStack item;

        private final int amount;

        public KeyRequirement(ItemStack item, int amount) {
            this.item = item.copyWithCount(1);
            this.amount = amount;
        }

        public ItemStack getItem() {
            return item;
        }

        public int getAmount() {
            return amount;
        }
    }

    // ---------------------------------------------------------------------
    // Partículas
    // ---------------------------------------------------------------------

    public static final class ParticleSettings {

        private boolean enabled = false;

        /**
         * ENCHANT es solamente el fallback.
         * No aparece mientras enabled == false.
         */
        private ParticleOptions particle = ParticleTypes.ENCHANT;

        private int count = 3;

        private double radius = 0.45D;

        private double speed = 0.01D;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public ParticleOptions getParticle() {
            return particle;
        }

        public void setParticle(ParticleOptions particle) {
            this.particle = particle;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public double getRadius() {
            return radius;
        }

        public void setRadius(double radius) {
            this.radius = radius;
        }

        public double getSpeed() {
            return speed;
        }

        public void setSpeed(double speed) {
            this.speed = speed;
        }
    }
}