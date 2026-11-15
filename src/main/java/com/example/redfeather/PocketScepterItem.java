package com.example.redfeather;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Sceptre de poche : clic droit = aller dans ta dimension de poche, re-clic droit = revenir.
 * Chaque joueur a sa propre salle dans la dimension "redfeather:pocket".
 */
public class PocketScepterItem extends Item {

    public static final ResourceKey<Level> POCKET = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(RedFeatherMod.MOD_ID, "pocket"));

    /** Distance entre les salles de deux joueurs (en blocs, sur l'axe X). */
    private static final int SPACING = 128;
    /** Hauteur (Y) du sol de la salle. */
    private static final int FLOOR_Y = 63;
    /** Demi-largeur de l'intérieur : 7 -> salle de 15x15. */
    private static final int HALF = 7;
    /** Hauteur de l'intérieur de la salle. */
    private static final int HEIGHT = 30;
    /** Temps de recharge en ticks (40 = 2 secondes). */
    private static final int COOLDOWN = 40;

    public PocketScepterItem(Item.Properties properties) {
        super(properties
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            MinecraftServer server = serverLevel.getServer();
            ServerLevel pocket = server.getLevel(POCKET);
            if (pocket == null) {
                sp.displayClientMessage(Component.translatable("message.redfeather.no_pocket"), true);
                return InteractionResultHolder.fail(stack);
            }

            PocketData data = PocketData.get(server);
            serverLevel.playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

            if (level.dimension().equals(POCKET)) {
                leavePocket(sp, server, data);
            } else {
                enterPocket(sp, pocket, data);
            }

            player.getCooldowns().addCooldown(this, COOLDOWN);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static BlockPos originOf(int slot) {
        return new BlockPos(slot * SPACING, FLOOR_Y, 0);
    }

    private static void enterPocket(ServerPlayer sp, ServerLevel pocket, PocketData data) {
        boolean firstTime = !data.hasSlot(sp.getUUID());
        int slot = data.slotFor(sp.getUUID());
        BlockPos origin = originOf(slot);

        if (firstTime) {
            buildRoom(pocket, origin);
        }

        data.setReturn(sp.getUUID(), new Return(
                sp.level().dimension(), sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot()));

        sp.teleportTo(pocket, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5,
                sp.getYRot(), sp.getXRot());
    }

    private static void leavePocket(ServerPlayer sp, MinecraftServer server, PocketData data) {
        Return r = data.getReturn(sp.getUUID());
        ServerLevel dest = (r != null) ? server.getLevel(r.dim()) : null;

        if (dest == null) {
            dest = server.overworld();
            BlockPos spawn = dest.getSharedSpawnPos();
            sp.teleportTo(dest, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, sp.getYRot(), sp.getXRot());
        } else {
            sp.teleportTo(dest, r.x(), r.y(), r.z(), r.yRot(), r.xRot());
        }
    }

    /** Construit une salle en quartz avec des lanternes marines. */
    private static void buildRoom(ServerLevel level, BlockPos origin) {
        BlockState floor = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState wall = Blocks.QUARTZ_BRICKS.defaultBlockState();
        BlockState ceiling = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
            for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
                boolean edge = Math.abs(dx) == HALF + 1 || Math.abs(dz) == HALF + 1;
                for (int dy = 0; dy <= HEIGHT + 1; dy++) {
                    BlockState state;
                    if (dy == 0) {
                        state = floor;
                    } else if (dy == HEIGHT + 1) {
                        boolean isLantern = (dx == 0 && dz == 0)
                                || (Math.abs(dx) == 5 && Math.abs(dz) == 5);
                        state = isLantern ? lantern : ceiling;
                    } else if (edge) {
                        // lanternes au milieu de chaque mur, tous les 8 blocs de hauteur
                        boolean wallLantern = ((Math.abs(dx) == HALF + 1 && dz == 0)
                                || (Math.abs(dz) == HALF + 1 && dx == 0)) && dy % 8 == 4;
                        state = wallLantern ? lantern : wall;
                    } else {
                        state = air;
                    }
                    level.setBlock(origin.offset(dx, dy, dz), state, 3);
                }
            }
        }
    }

    /**
     * Vrai si ce bloc fait partie de la coque (sol, murs, plafond) d'une salle de poche
     * ET est encore un bloc de la salle (quartz / lanterne). Les blocs posés par le joueur
     * à l'intérieur de la salle ne sont donc pas protégés.
     */
    private static boolean isProtected(Level level, BlockPos pos) {
        if (!level.dimension().equals(POCKET)) return false;

        int slot = Math.floorDiv(pos.getX() + SPACING / 2, SPACING);
        if (slot < 0) return false;

        int dx = pos.getX() - slot * SPACING;
        int dz = pos.getZ();
        int dy = pos.getY() - FLOOR_Y;

        if (Math.abs(dx) > HALF + 1 || Math.abs(dz) > HALF + 1 || dy < 0 || dy > HEIGHT + 1) return false;

        boolean shell = dy == 0 || dy == HEIGHT + 1 || Math.abs(dx) == HALF + 1 || Math.abs(dz) == HALF + 1;
        if (!shell) return false;

        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.SMOOTH_QUARTZ) || state.is(Blocks.QUARTZ_BRICKS)
                || state.is(Blocks.QUARTZ_BLOCK) || state.is(Blocks.SEA_LANTERN);
    }

    /** Empêche de casser la coque de la salle. */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && isProtected(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Empêche les explosions de détruire la coque de la salle. */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (!level.dimension().equals(POCKET)) return;
        event.getAffectedBlocks().removeIf(pos -> isProtected(level, pos));
    }

    /** Si le joueur tombe dans le vide de sa dimension de poche, on le ramène dans sa salle. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 5 != 0) return;
        if (!(sp.level() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(POCKET)) return;
        if (sp.getY() >= 0) return;

        Integer slot = PocketData.get(level.getServer()).getSlot(sp.getUUID());
        BlockPos origin = originOf(slot == null ? 0 : slot);

        sp.resetFallDistance();
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hurtMarked = true;
        sp.teleportTo(level, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5,
                sp.getYRot(), sp.getXRot());
    }

    // ------------------------------------------------------------------

    private record Return(ResourceKey<Level> dim, double x, double y, double z, float yRot, float xRot) {}

    /** Données sauvegardées : numéro de salle de chaque joueur + son point de retour. */
    public static class PocketData extends SavedData {
        private static final String NAME = "redfeather_pocket";

        private final Map<UUID, Integer> slots = new HashMap<>();
        private final Map<UUID, Return> returns = new HashMap<>();
        private int next = 0;

        private PocketData() {}

        static PocketData get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<PocketData>(PocketData::new, PocketData::load, null), NAME);
        }

        boolean hasSlot(UUID id) {
            return slots.containsKey(id);
        }

        Integer getSlot(UUID id) {
            return slots.get(id);
        }

        int slotFor(UUID id) {
            Integer existing = slots.get(id);
            if (existing != null) return existing;
            int slot = next++;
            slots.put(id, slot);
            setDirty();
            return slot;
        }

        Return getReturn(UUID id) {
            return returns.get(id);
        }

        void setReturn(UUID id, Return r) {
            returns.put(id, r);
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("next", next);

            ListTag slotList = new ListTag();
            for (Map.Entry<UUID, Integer> e : slots.entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putUUID("id", e.getKey());
                t.putInt("slot", e.getValue());
                slotList.add(t);
            }
            tag.put("slots", slotList);

            ListTag retList = new ListTag();
            for (Map.Entry<UUID, Return> e : returns.entrySet()) {
                Return r = e.getValue();
                CompoundTag t = new CompoundTag();
                t.putUUID("id", e.getKey());
                t.putString("dim", r.dim().location().toString());
                t.putDouble("x", r.x());
                t.putDouble("y", r.y());
                t.putDouble("z", r.z());
                t.putFloat("yRot", r.yRot());
                t.putFloat("xRot", r.xRot());
                retList.add(t);
            }
            tag.put("returns", retList);
            return tag;
        }

        static PocketData load(CompoundTag tag, HolderLookup.Provider registries) {
            PocketData data = new PocketData();
            data.next = tag.getInt("next");

            ListTag slotList = tag.getList("slots", Tag.TAG_COMPOUND);
            for (int i = 0; i < slotList.size(); i++) {
                CompoundTag t = slotList.getCompound(i);
                data.slots.put(t.getUUID("id"), t.getInt("slot"));
            }

            ListTag retList = tag.getList("returns", Tag.TAG_COMPOUND);
            for (int i = 0; i < retList.size(); i++) {
                CompoundTag t = retList.getCompound(i);
                ResourceKey<Level> dim = ResourceKey.create(
                        Registries.DIMENSION, ResourceLocation.parse(t.getString("dim")));
                data.returns.put(t.getUUID("id"), new Return(
                        dim, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"),
                        t.getFloat("yRot"), t.getFloat("xRot")));
            }
            return data;
        }
    }
}
