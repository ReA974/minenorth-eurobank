package com.minenorth.eurobank;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class BankCommands {
    private BankCommands() {}

    private enum Mode { ADD, TAKE, SET }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("bank")
                .then(Commands.literal("balance").executes(BankCommands::balance))
                .then(Commands.literal("pay")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                        .executes(BankCommands::pay))))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .executes(BankCommands::openAdmin))
                .then(Commands.literal("add").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                        .executes(c -> admin(c, Mode.ADD)))))
                .then(Commands.literal("take").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                        .executes(c -> admin(c, Mode.TAKE)))))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(c -> admin(c, Mode.SET))))));
    }

    private static long cents(CommandContext<CommandSourceStack> c) {
        return Math.round(DoubleArgumentType.getDouble(c, "amount") * 100);
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int balance(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BankData d = BankData.get(c.getSource().getServer());
        if (!d.has(p.getUUID())) return fail(c, "Vous n'avez pas de compte. Utilisez un distributeur (ATM).");
        return ok(c, "Solde : " + Money.format(d.balance(p.getUUID())));
    }

    private static int pay(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer from = c.getSource().getPlayerOrException();
        ServerPlayer to = EntityArgument.getPlayer(c, "player");
        long amount = cents(c);
        BankData d = BankData.get(c.getSource().getServer());
        if (from.getUUID().equals(to.getUUID())) return fail(c, "Vous ne pouvez pas vous payer vous-même.");
        if (!d.has(from.getUUID())) return fail(c, "Vous n'avez pas de compte.");
        if (!d.has(to.getUUID())) return fail(c, to.getGameProfile().getName() + " n'a pas de compte.");
        if (amount <= 0) return fail(c, "Montant invalide.");
        if (d.balance(from.getUUID()) < amount) return fail(c, "Solde insuffisant.");
        d.add(from.getUUID(), -amount);
        d.add(to.getUUID(), amount);
        to.sendSystemMessage(Component.literal(from.getGameProfile().getName() + " vous a envoyé " + Money.format(amount) + "."));
        return ok(c, "Virement de " + Money.format(amount) + " envoyé à " + to.getGameProfile().getName() + ".");
    }

    private static int openAdmin(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Network.sendAdminList(c.getSource().getPlayerOrException(), true, "");
        return 1;
    }

    /** add / take / set : fonctionne aussi depuis la console et pour les joueurs hors ligne. */
    private static int admin(CommandContext<CommandSourceStack> c, Mode mode) throws CommandSyntaxException {
        GameProfile gp = GameProfileArgument.getGameProfiles(c, "player").iterator().next();
        UUID id = gp.getId();
        long amount = cents(c);
        BankData d = BankData.get(c.getSource().getServer());
        d.rename(id, gp.getName());
        String who = d.name(id);
        switch (mode) {
            case ADD:
                d.open(id);
                d.add(id, amount);
                return ok(c, Money.format(amount) + " ajoutés à " + who + ". Solde : " + Money.format(d.balance(id)));
            case SET:
                d.open(id);
                d.set(id, amount);
                return ok(c, "Solde de " + who + " fixé à " + Money.format(amount) + ".");
            case TAKE:
                if (!d.has(id)) return fail(c, who + " n'a pas de compte.");
                if (d.balance(id) < amount) return fail(c, "Solde insuffisant (" + Money.format(d.balance(id)) + ").");
                d.add(id, -amount);
                return ok(c, Money.format(amount) + " retirés à " + who + ". Solde : " + Money.format(d.balance(id)));
            default:
                return 0;
        }
    }
}
