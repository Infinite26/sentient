package dev.sentient.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import dev.sentient.Sentient;
import dev.sentient.chat.SentientChat;
import dev.sentient.config.SentientConfig;
import dev.sentient.llm.LlmRouter;
import dev.sentient.memory.SentientMemoryData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class SentientCommand {

    private SentientCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sentient")
                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            SentientConfig.load();
                            LlmRouter.reload();
                            ctx.getSource().sendSuccess(() -> Component.literal("Sentient config reloaded."), true);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Sentient  providers=" + LlmRouter.describe()
                                            + "  tts=" + (Sentient.config().ttsEnabled ? Sentient.config().ttsEndpoint : "off")
                                            + "  npcs=" + SentientMemoryData.get(ctx.getSource().getServer()).size()), false);
                            return 1;
                        }))
                .then(Commands.literal("test")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("Testing LLM endpoint..."), false);
                            SentientChat.testEndpoint(ctx.getSource());
                            return 1;
                        }))
                .then(Commands.literal("model")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    Sentient.config().model = name;
                                    Sentient.config().save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Sentient model set to " + name), true);
                                    return 1;
                                })))
                .then(Commands.literal("ask")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            Entity entity = EntityArgument.getEntity(ctx, "target");
                                            if (!(entity instanceof LivingEntity living)) {
                                                ctx.getSource().sendFailure(Component.literal("That is not a living creature."));
                                                return 0;
                                            }
                                            String message = StringArgumentType.getString(ctx, "message");
                                            SentientChat.askFromCommand(ctx.getSource(), living, message);
                                            return 1;
                                        }))))
                .then(Commands.literal("forget")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> {
                                    Entity entity = EntityArgument.getEntity(ctx, "target");
                                    boolean removed = SentientMemoryData.get(ctx.getSource().getServer())
                                            .forget(entity.getUUID());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            removed ? "Forgot that creature." : "No memory for that creature."), false);
                                    return 1;
                                })))
                .then(Commands.literal("forgetall")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            SentientMemoryData data = SentientMemoryData.get(ctx.getSource().getServer());
                            int n = data.size();
                            data.clearAll();
                            ctx.getSource().sendSuccess(() -> Component.literal("Forgot " + n + " creatures."), true);
                            return 1;
                        })));
    }
}
