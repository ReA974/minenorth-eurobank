package com.minenorth_eurobank;

import com.minenorth_eurobank.blocks.AtmBlock;
import com.minenorth_eurobank.loan.LoanService;
import com.minenorth_eurobank.packet.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Network {
    private static final String PROTOCOL = "10";   // 10 : offres de prêt, remboursement anticipé, relevé (StatePacket, ActionPacket) ; 9 : menu du guichet
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EuroBank.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** Distributeur actuellement utilisé par chaque joueur (anti-triche : les actions sont validées serveur). */
    public static final Map<UUID, BlockPos> SESSIONS = new ConcurrentHashMap<>();

    /** Joueurs qui ont actuellement l'écran correspondant ouvert (mises à jour poussées en direct). */
    public static final Set<UUID> WATCH_ATM = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> WATCH_BANKER = ConcurrentHashMap.newKeySet();

    private Network() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
        CHANNEL.registerMessage(id++, StatePacket.class, StatePacket::encode, StatePacket::decode, StatePacket::handle);
        CHANNEL.registerMessage(id++, TabletPacket.class, TabletPacket::encode, TabletPacket::decode, TabletPacket::handle);
        CHANNEL.registerMessage(id++, TabletDataPacket.class, TabletDataPacket::encode, TabletDataPacket::decode, TabletDataPacket::handle);
        CHANNEL.registerMessage(id++, WatchPacket.class, WatchPacket::encode, WatchPacket::decode, WatchPacket::handle);
        CHANNEL.registerMessage(id++, DeskPacket.class, DeskPacket::encode, DeskPacket::decode, DeskPacket::handle);
        CHANNEL.registerMessage(id++, DeskSubmitPacket.class, DeskSubmitPacket::encode, DeskSubmitPacket::decode, DeskSubmitPacket::handle);
        CHANNEL.registerMessage(id++, DeskMenuPacket.class, DeskMenuPacket::encode, DeskMenuPacket::decode, DeskMenuPacket::handle);
    }

    public static void openAtm(ServerPlayer p, BlockPos pos) {
        SESSIONS.put(p.getUUID(), pos);
        sendState(p, true, "");
    }

    public static void sendState(ServerPlayer p, boolean open, String message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), StatePacket.compute(p, open, message));
    }

    public static void unwatchAll(UUID id) {
        com.minenorth_eurobank.banker.TabletService.VIEWS.remove(id);
        com.minenorth_eurobank.banker.LoanDesk.close(id);
        WATCH_ATM.remove(id);
        WATCH_BANKER.remove(id);
    }

    /** Renvoie l'état à tous ceux qui ont un écran ouvert. atmOnly : seulement les ATM (rafraîchit les espèces). */
    public static void refreshWatchers(MinecraftServer s, boolean atmOnly) {
        if (WATCH_ATM.isEmpty() && WATCH_BANKER.isEmpty()) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (WATCH_ATM.contains(id)) sendState(p, false, "");
            if (atmOnly) continue;
            if (WATCH_BANKER.contains(id) && LoanService.isBanker(p)) com.minenorth_eurobank.banker.TabletService.push(p, false, "");
        }
    }

    public static boolean nearAtm(ServerPlayer p) {
        BlockPos pos = SESSIONS.get(p.getUUID());
        return pos != null
                && p.level().getBlockState(pos).getBlock() instanceof AtmBlock
                && p.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0;
    }
}
