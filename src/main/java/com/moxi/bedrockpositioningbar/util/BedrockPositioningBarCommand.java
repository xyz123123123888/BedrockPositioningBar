package com.moxi.bedrockpositioningbar.util;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.moxi.bedrockpositioningbar.config.BedrockPositioningBarConfig;
import com.moxi.bedrockpositioningbar.config.BedrockPositioningBarConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * 客户端命令 /bpb，用于热重载、调整配置与打开配置界面。
 *
 * <p>切换配置后立即落盘并持久化。</p>
 */
public final class BedrockPositioningBarCommand {

    private BedrockPositioningBarCommand() {
    }

    /** 注册客户端命令。 */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("bpb")
                        .then(literal("onlyOnBedrock")
                                .then(argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setOnlyOnBedrock(
                                                ctx.getSource(), BoolArgumentType.getBool(ctx, "value")))))
                        .then(literal("showPlayers")
                                .then(argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setShowPlayers(
                                                ctx.getSource(), BoolArgumentType.getBool(ctx, "value")))))
                        .then(literal("showOffscreenArrows")
                                .then(argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setShowOffscreenArrows(
                                                ctx.getSource(), BoolArgumentType.getBool(ctx, "value")))))
                        .then(literal("reload")
                                .executes(ctx -> reload(ctx.getSource())))
                        .then(literal("gui")
                                .executes(ctx -> openGui(ctx.getSource())))
                        .then(literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                )
        );
    }

    private static int status(FabricClientCommandSource source) {
        String detail = ViaFabricPlusDetector.describeStatus(source.getClient());
        source.sendFeedback(Component.literal(detail).withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static int openGui(FabricClientCommandSource source) {
        Minecraft client = source.getClient();
        // 必须在游戏线程且在主渲染循环的干净时机打开，否则 setScreenAndShow 内部的
        // renderFrame 会与当前命令执行栈冲突，导致界面闪一下即被下一轮 tick 重置。
        client.execute(() -> client.setScreenAndShow(new BedrockPositioningBarConfigScreen()));
        return 1;
    }

    private static int setOnlyOnBedrock(FabricClientCommandSource source, boolean value) {
        BedrockPositioningBarConfig.get().onlyOnBedrock = value;
        BedrockPositioningBarConfig.save();
        source.sendFeedback(Component.literal("onlyOnBedrock = " + value));
        return 1;
    }

    private static int setShowPlayers(FabricClientCommandSource source, boolean value) {
        BedrockPositioningBarConfig.get().showPlayers = value;
        BedrockPositioningBarConfig.save();
        source.sendFeedback(Component.literal("showPlayers = " + value));
        return 1;
    }

    private static int setShowOffscreenArrows(FabricClientCommandSource source, boolean value) {
        BedrockPositioningBarConfig.get().showOffscreenArrows = value;
        BedrockPositioningBarConfig.save();
        source.sendFeedback(Component.literal("showOffscreenArrows = " + value));
        return 1;
    }

    private static int reload(FabricClientCommandSource source) {
        BedrockPositioningBarConfig.load();
        source.sendFeedback(Component.literal("配置已重新加载").withStyle(ChatFormatting.GREEN));
        return 1;
    }
}