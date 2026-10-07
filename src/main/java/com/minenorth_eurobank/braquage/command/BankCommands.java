package com.minenorth_eurobank.braquage.command;

import com.mojang.brigadier.CommandDispatcher;
import com.minenorth_eurobank.braquage.bank.MainBankData;
import com.minenorth_eurobank.braquage.block.entity.BankVaultBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Map; import java.util.UUID; import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public class BankCommands {
 private enum SelectionMode { ADD_BLOCK,HACK_BLOCK,ADD_GRILLE,GRILLE_HACK_BLOCK }
 private static final Map<UUID,SelectionMode> SELECTIONS=new ConcurrentHashMap<>();
 @SubscribeEvent public static void register(RegisterCommandsEvent event){var d=event.getDispatcher();
  d.register(Commands.literal("banquereload").requires(s->s.hasPermission(2)).executes(c->reload(c.getSource())));
  d.register(Commands.literal("banquereset").requires(s->s.hasPermission(2)).executes(c->reset(c.getSource())));
  d.register(Commands.literal("banque").requires(s->s.hasPermission(2))
   .then(Commands.literal("bloc").then(Commands.literal("add").executes(c->start(c.getSource(),SelectionMode.ADD_BLOCK))).then(Commands.literal("clear").executes(c->clear(c.getSource()))).then(Commands.literal("list").executes(c->list(c.getSource()))))
   .then(Commands.literal("hackblock").then(Commands.literal("clear").executes(c->clearHack(c.getSource()))).executes(c->start(c.getSource(),SelectionMode.HACK_BLOCK)))
   .then(Commands.literal("grille").then(Commands.literal("add").executes(c->start(c.getSource(),SelectionMode.ADD_GRILLE))).then(Commands.literal("clear").executes(c->clearGrille(c.getSource()))).then(Commands.literal("list").executes(c->listGrille(c.getSource()))).then(Commands.literal("hackblock").then(Commands.literal("clear").executes(c->clearGrilleHack(c.getSource()))).executes(c->start(c.getSource(),SelectionMode.GRILLE_HACK_BLOCK))))
   .then(Commands.literal("stop").executes(c->stop(c.getSource())))
   .then(Commands.literal("status").executes(c->status(c.getSource()))));
 }
 private static int reload(CommandSourceStack s){return 1;}
 private static int reset(CommandSourceStack s){ServerLevel l=s.getLevel(); MainBankData d=MainBankData.get(l); int a=BankVaultBlockEntity.resetLoadedVaults(d.nextVaultGeneration()); int r=d.restoreBank(l)+d.restoreGrille(l); s.sendSystemMessage(Component.literal("Coffres et banque réinitialisés : "+(a+r)));return 1;}
 private static int start(CommandSourceStack s,SelectionMode m)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=s.getPlayerOrException();SELECTIONS.put(p.getUUID(),m);p.displayClientMessage(Component.literal("Cliquez sur le bloc à configurer."),false);return 1;}
 private static int stop(CommandSourceStack s){ServerPlayer p=s.getPlayer(); if(p!=null){SELECTIONS.remove(p.getUUID()); p.displayClientMessage(Component.literal("Mode de configuration désactivé."),false);} return 1;}
 private static int clear(CommandSourceStack s){MainBankData.get(s.getLevel()).clearBlocks();return 1;}
 private static int clearGrille(CommandSourceStack s){MainBankData.get(s.getLevel()).clearGrilleBlocks();return 1;}
 private static int clearHack(CommandSourceStack s){MainBankData.get(s.getLevel()).clearHackBlock();return 1;}
 private static int clearGrilleHack(CommandSourceStack s){MainBankData.get(s.getLevel()).clearGrilleHackBlock();return 1;}
 private static int list(CommandSourceStack s){var d=MainBankData.get(s.getLevel());s.sendSystemMessage(Component.literal("Blocs coffre : "+d.getBlocks().size()));return d.getBlocks().size();}
 private static int listGrille(CommandSourceStack s){var d=MainBankData.get(s.getLevel());s.sendSystemMessage(Component.literal("Blocs grille : "+d.getGrilleBlocks().size()));return d.getGrilleBlocks().size();}
 private static int status(CommandSourceStack s){var d=MainBankData.get(s.getLevel());s.sendSystemMessage(Component.literal("Coffre : "+(d.isHacked()?"piraté":"fermé")+" | Grilles : "+(d.isGrilleHacked()?"piratées":"fermées")));return 1;}
 @SubscribeEvent public static void click(PlayerInteractEvent.RightClickBlock e){if(e.getLevel().isClientSide||!(e.getEntity() instanceof ServerPlayer p)||!(e.getLevel() instanceof ServerLevel l)||e.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;var m=SELECTIONS.get(p.getUUID());if(m==null)return;var d=MainBankData.get(l);boolean changed=switch(m){case ADD_BLOCK->d.addBlock(l,e.getPos());case ADD_GRILLE->d.addGrilleBlock(l,e.getPos());case HACK_BLOCK->{d.setHackBlock(l,e.getPos()); yield true;}case GRILLE_HACK_BLOCK->{d.setGrilleHackBlock(l,e.getPos()); yield true;}}; if(changed) p.displayClientMessage(Component.literal("Bloc ajouté. Cliquez sur un autre bloc pour continuer. /banque stop pour terminer."),false); else p.displayClientMessage(Component.literal("Ce bloc ne peut pas être ajouté ou est déjà configuré."),true);e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);}
}
