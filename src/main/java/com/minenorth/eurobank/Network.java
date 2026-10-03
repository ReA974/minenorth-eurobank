package com.minenorth.eurobank;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Network {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EuroBank.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** Distributeur actuellement utilisé par chaque joueur (anti-triche : les actions sont validées serveur). */
    public static final Map<UUID, BlockPos> SESSIONS = new ConcurrentHashMap<>();

    private Network() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
        CHANNEL.registerMessage(id++, StatePacket.class, StatePacket::encode, StatePacket::decode, StatePacket::handle);
        CHANNEL.registerMessage(id++, AdminPacket.class, AdminPacket::encode, AdminPacket::decode, AdminPacket::handle);
        CHANNEL.registerMessage(id++, AdminListPacket.class, AdminListPacket::encode, AdminListPacket::decode, AdminListPacket::handle);
    }

    public static void openAtm(ServerPlayer p, BlockPos pos) {
        SESSIONS.put(p.getUUID(), pos);
        sendState(p, true, "");
    }

    public static void sendState(ServerPlayer p, boolean open, String message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), StatePacket.compute(p, open, message));
    }

    public static void sendAdminList(ServerPlayer p, boolean open, String message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), AdminListPacket.compute(p, open, message));
    }

    public static boolean nearAtm(ServerPlayer p) {
        BlockPos pos = SESSIONS.get(p.getUUID());
        return pos != null
                && p.level().getBlockState(pos).getBlock() instanceof AtmBlock
                && p.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0;
    }
}
