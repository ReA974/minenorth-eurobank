package com.minenorth_eurobank.braquage.bank;

import com.minenorth_eurobank.braquage.network.ModNetwork;
import com.minenorth_eurobank.braquage.item.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MainBankManager {
    private MainBankManager() {}
    private static final Map<UUID, HackSession> SESSIONS = new ConcurrentHashMap<>();

    public enum HackType { GRILLE, COFFRE }

    public static boolean start(ServerPlayer player, ServerLevel level, HackType type) {
        MainBankData data = MainBankData.get(level);
        BlockPos trigger = type == HackType.GRILLE ? data.getGrilleHackBlock() : data.getHackBlock();
        boolean configured = type == HackType.GRILLE ? data.isGrilleConfigured() : data.isConfigured();
        boolean hacked = type == HackType.GRILLE ? data.isGrilleHacked() : data.isHacked();
        var dimension = type == HackType.GRILLE ? data.getGrilleHackDimension() : data.getHackDimension();
        if (!configured || hacked || trigger == null || dimension == null) return false;
        if (SESSIONS.containsKey(player.getUUID())) return false;
        int min = com.minenorth_eurobank.braquage.config.ModConfig.POLICE_MIN.get();
        if (min > 0 && !player.hasPermissions(2) && fr.minenorth.api.MineNorth.police().online(player.server).size() < min) {
            player.displayClientMessage(Component.literal("§cPas assez de policiers en service pour tenter un braquage (" + min + " minimum)."), true);
            return false;
        }
        if (!level.dimension().equals(dimension)) return false;
        if (!trigger.equals(player.blockPosition()) && player.distanceToSqr(trigger.getX()+0.5, trigger.getY()+0.5, trigger.getZ()+0.5) > 9.0D) return false;

        List<Integer> sequence = new ArrayList<>();
        Random random = new Random(level.random.nextLong() ^ player.getUUID().getLeastSignificantBits() ^ type.ordinal());
        while (sequence.size() < 4) {
            int cell = random.nextInt(6);
            if (!sequence.contains(cell)) sequence.add(cell);
        }
        HackSession session = new HackSession(level.dimension().location().toString(), type, sequence, System.currentTimeMillis() + 30000L);
        SESSIONS.put(player.getUUID(), session);
        ModNetwork.sendOpenHack(player, sequence);
        return true;
    }

    public static void click(ServerPlayer player, int cell) {
        HackSession session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (System.currentTimeMillis() > session.deadline) { fail(player, "message.minenorth_eurobank.hack_timeout"); return; }
        if (session.index >= session.sequence.size()) return;
        if (session.sequence.get(session.index) != cell) { fail(player, "message.minenorth_eurobank.hack_failed"); return; }
        session.index++;
        if (session.index >= session.sequence.size()) {
            net.minecraft.resources.ResourceLocation dim = net.minecraft.resources.ResourceLocation.tryParse(session.dimension);
            ServerLevel level = dim == null ? null : player.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim));
            if (level != null) {
                MainBankData data = MainBankData.get(level);
                BlockPos soundPos;
                if (session.type == HackType.GRILLE) {
                    data.hideGrille(level);
                    soundPos = data.getGrilleHackBlock();
                } else {
                    data.hideBank(level);
                    soundPos = data.getHackBlock();
                }
                if (soundPos != null) level.playSound(null, soundPos, ModSounds.HACK_SUCCESS.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 1.0F);
                broadcastAlert(player);
            }
            SESSIONS.remove(player.getUUID());
            ModNetwork.sendCloseHack(player);
            player.displayClientMessage(Component.translatable("message.minenorth_eurobank.hack_success"), true);
        } else {
            ModNetwork.sendHackProgress(player, session.index, session.sequence.size());
        }
    }

    /** Alarme : la police reçoit l'alerte détaillée (MineNorth API) ; le reste du serveur seulement si alerte_globale. */
    private static void broadcastAlert(ServerPlayer player) {
        BlockPos at = player.blockPosition();
        Component detail = Component.literal("§c§l[ALERTE BANQUE] §r§cBraquage en cours ! Position : " + at.getX() + " " + at.getY() + " " + at.getZ());
        for (ServerPlayer cop : fr.minenorth.api.MineNorth.police().online(player.server)) cop.sendSystemMessage(detail);
        if (com.minenorth_eurobank.braquage.config.ModConfig.GLOBAL_ALERT.get()) {
            player.server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.minenorth_eurobank.bank_alert").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD),
                    false);
        }
    }

    private static void fail(ServerPlayer player, String key) {
        SESSIONS.remove(player.getUUID());
        player.level().playSound(null, player.blockPosition(), ModSounds.HACK_FAIL.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
        ModNetwork.sendCloseHack(player);
        player.displayClientMessage(Component.translatable(key), true);
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, HackSession> entry : List.copyOf(SESSIONS.entrySet())) {
            if (now > entry.getValue().deadline) SESSIONS.remove(entry.getKey());
        }
    }

    public static void cancel(UUID uuid) { SESSIONS.remove(uuid); }

    private static class HackSession {
        final String dimension;
        final HackType type;
        final List<Integer> sequence;
        final long deadline;
        int index;
        HackSession(String dimension, HackType type, List<Integer> sequence, long deadline) {
            this.dimension = dimension; this.type = type; this.sequence = sequence; this.deadline = deadline;
        }
    }
}
