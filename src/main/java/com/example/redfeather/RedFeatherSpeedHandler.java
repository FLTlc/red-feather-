package com.example.redfeather;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = RedFeatherMod.MOD_ID)
public class RedFeatherSpeedHandler {

    /** Temps de marche (en ticks) pour atteindre la vitesse max. 600 ticks = 30 secondes. */
    private static final int MAX_CHARGE = 600;
    /** Bonus de vitesse max : 2.5 = +250 % (soit 3,5x la vitesse normale). */
    private static final double MAX_BONUS = 2.5;
    /** Vitesse à laquelle la charge retombe quand on s'arrête (ticks perdus par tick). */
    private static final int DECAY = 3;
    /** La modification n'est mise à jour que par paliers (évite de spammer le réseau). */
    private static final int STEP = 10;

    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(RedFeatherMod.MOD_ID, "red_feather_speed");

    private static final DustParticleOptions RED_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.05f, 0.05f), 1.2f);

    private static final Map<UUID, Integer> CHARGE = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        UUID id = player.getUUID();
        boolean holding = player.getMainHandItem().is(RedFeatherMod.RED_FEATHER.get())
                || player.getOffhandItem().is(RedFeatherMod.RED_FEATHER.get());

        // Plume pas en main : on retire tout
        if (!holding) {
            if (CHARGE.remove(id) != null || speed.hasModifier(MODIFIER_ID)) {
                speed.removeModifier(MODIFIER_ID);
            }
            return;
        }

        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        boolean moving = (dx * dx + dz * dz) > 1.0E-4
                && !player.isFallFlying()
                && !player.isPassenger();

        int old = CHARGE.getOrDefault(id, 0);
        int charge = moving ? Math.min(MAX_CHARGE, old + 1) : Math.max(0, old - DECAY);

        if (charge == 0) {
            CHARGE.remove(id);
            if (speed.hasModifier(MODIFIER_ID)) speed.removeModifier(MODIFIER_ID);
            return;
        }

        CHARGE.put(id, charge);

        // Particules rouges aux pieds : de plus en plus nombreuses avec la vitesse
        if (moving && player.level() instanceof ServerLevel serverLevel && player.tickCount % 2 == 0) {
            int count = 1 + charge * 6 / MAX_CHARGE;
            serverLevel.sendParticles(RED_DUST,
                    player.getX(), player.getY() + 0.1, player.getZ(),
                    count, 0.25, 0.05, 0.25, 0.01);
        }

        // Mise à jour de l'attribut seulement quand on change de palier
        if (charge / STEP != old / STEP || !speed.hasModifier(MODIFIER_ID)) {
            double bonus = MAX_BONUS * ((double) (charge / STEP * STEP) / MAX_CHARGE);
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHARGE.remove(event.getEntity().getUUID());
    }
}
