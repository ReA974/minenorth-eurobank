package com.minenorth.eurobank;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(EuroBank.MODID)
public class EuroBank {
    public static final String MODID = "minenorth_eurobank";

    public EuroBank() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModTabs.TABS.register(bus);
        bus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(Network::register);
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        BankCommands.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            BankData d = BankData.get(sp.server);
            if (d.has(sp.getUUID())) d.rename(sp.getUUID(), sp.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Network.SESSIONS.remove(e.getEntity().getUUID());
    }
}
