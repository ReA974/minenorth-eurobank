package com.minenorth_eurobank.client;

import com.minenorth_eurobank.packet.BankerListPacket;
import com.minenorth_eurobank.packet.StatePacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientHooks {
    private ClientHooks() {}

    public static void handleState(StatePacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AtmScreen s) s.update(m);
        else if (m.open) mc.setScreen(new AtmScreen(m));
    }

    public static void handleBanker(BankerListPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BankerScreen s) s.update(m);
        else if (m.open) mc.setScreen(new BankerScreen(m, mc.screen instanceof AtmScreen a ? a : null));
    }
}
