package com.moxi.bedrockpositioningbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组主类（非客户端入口）。
 *
 * <p>注意：Fabric 26.x 环境下真正的客户端初始化在
 * {@link com.moxi.bedrockpositioningbar.client.BedrockPositioningBarClient} 中进行。
 * 本类仅作为模组标识与公共常量的容器。</p>
 */
public final class BedrockPositioningBarMod {

    /** 模组 ID，需与 fabric.mod.json 中的 id 一致。 */
    public static final String MOD_ID = "bedrockpositioningbar";

    /** 全局日志器。 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private BedrockPositioningBarMod() {
        throw new AssertionError("No BedrockPositioningBarMod instances for you!");
    }
}
