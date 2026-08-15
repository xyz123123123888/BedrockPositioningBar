package com.moxi.bedrockpositioningbar.client;

import com.moxi.bedrockpositioningbar.BedrockPositioningBarMod;
import com.moxi.bedrockpositioningbar.config.BedrockPositioningBarConfig;
import com.moxi.bedrockpositioningbar.hud.BedrockLocatorBarHud;
import com.moxi.bedrockpositioningbar.util.BedrockDebugLog;
import com.moxi.bedrockpositioningbar.util.BedrockPositioningBarCommand;
import net.fabricmc.api.ClientModInitializer;

/**
 * 客户端初始化入口。
 *
 * <p>Fabric 通过 fabric.mod.json 中的 "client" 入口点反射加载本类。</p>
 */
public final class BedrockPositioningBarClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BedrockDebugLog.init();
        BedrockPositioningBarConfig.load();
        BedrockLocatorBarHud.register();
        BedrockPositioningBarCommand.register();
        BedrockDebugLog.info("客户端已初始化");
        BedrockPositioningBarMod.LOGGER.info("[BedrockPositioningBar] 客户端已初始化");
    }
}
