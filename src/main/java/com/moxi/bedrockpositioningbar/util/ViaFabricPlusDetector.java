package com.moxi.bedrockpositioningbar.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.lang.reflect.Method;

/**
 * 检测当前是否处于 ViaFabricPlus 的 Bedrock 服务器会话。
 *
 * <p><b>来源说明</b>：本项目与 ViaFabricPlus 均为 GPL-3.0，可直接阅读/借用其源码。
 * 这里采用反射调用以避免与不同版本绑定死，反射失败时静默降级，不会影响模组主流程。</p>
 *
 * <p>判定优先级（从可靠到兜底）：</p>
 * <ol>
 *   <li>反射 {@code ProtocolTranslator.getPlayNetworkUserConnection()}，非 null 表示当前处于
 *        Via 翻译会话（Bedrock）。同时兼容旧版 {@code ConnectionState.isViaServer()}。</li>
 *   <li>解析 {@link ServerData#ip} 中的 Bedrock 端口（19132/19133）。注意 ViaFabricPlus
 *       会把 Bedrock 目标改写为逗号分隔格式（形如 {@code host,0,19132}），因此需同时
 *       解析冒号与逗号两种分隔。</li>
 * </ol>
 */
public final class ViaFabricPlusDetector {

    private static final String VFP_MOD_ID = "viafabricplus";

    /**
     * 逆向得到的检测候选（新版/旧版包名），用于反射探测。
     *
     * <p>每条形如 [类名, 方法名, 返回值语义]：
     * <ul>
     *   <li>新版 ViaFabricPlus 4.6.1+：{@code ProtocolTranslator.getPlayNetworkUserConnection()}
     *       返回 {@link Object}——非 null 表示当前正处于 Via 翻译会话（连接 BE/旧版服务器）。</li>
     *   <li>旧版包名 {@code ConnectionState.isViaServer()} 返回 {@code boolean}。</li>
     * </ul>
     * 返回值语义标记：{@code "nonNull"} 表示方法返回对象、以非 null 为真；{@code "bool"} 表示返回 boolean。</p>
     */
    private static final String[][] CONNECTION_STATE_CANDIDATES = {
            {"com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator", "getPlayNetworkUserConnection", "nonNull"},
            {"net.viapf.viafabricplus.util.ConnectionState", "isViaServer", "bool"},
            {"de.florianmichael.viafabricplus.util.ConnectionState", "isViaServer", "bool"},
    };

    private ViaFabricPlusDetector() {
    }

    /** ViaFabricPlus 是否已加载。 */
    public static boolean isViaFabricPlusLoaded() {
        return FabricLoader.getInstance().isModLoaded(VFP_MOD_ID);
    }

    /**
     * 判断当前是否应该显示模组自渲染的 Bedrock 定位条。
     *
     * <p>详见类注释的优先级说明。</p>
     *
     * @param client Minecraft 客户端实例
     */
    public static boolean shouldShowCustomBar(Minecraft client) {
        if (!isViaFabricPlusLoaded()) {
            return false;
        }

        // 1) 反射检测 ViaFabricPlus 是否处于翻译会话（最可靠）
        if (isViaServerActive()) {
            return true;
        }

        // 2) 兜底：解析服务器地址中的 Bedrock 端口
        var currentServer = client.getCurrentServer();
        return currentServer != null && isBedrockServer(currentServer);
    }

    /**
     * 反射探测 ViaFabricPlus 是否处于翻译会话。
     *
     * <p>按候选依次尝试，反射失败（类或方法随版本变化）时静默跳过，
     * 不会导致模组崩溃。</p>
     */
    private static boolean isViaServerActive() {
        for (String[] candidate : CONNECTION_STATE_CANDIDATES) {
            try {
                Class<?> clazz = Class.forName(candidate[0]);
                String methodName = candidate[1];
                String semantics = candidate[2];
                Method method = clazz.getMethod(methodName);
                Object result = method.invoke(null);
                if ("nonNull".equals(semantics)) {
                    // 新版 ProtocolTranslator.getPlayNetworkUserConnection()：非 null 即翻译会话
                    if (result != null) {
                        return true;
                    }
                } else {
                    // 旧版 ConnectionState.isViaServer()：返回 boolean
                    if (Boolean.TRUE.equals(result)) {
                        return true;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // 该类/方法在当前版本不存在，尝试下一个候选
            }
        }
        return false;
    }

    /**
     * 通过服务器地址特征判断是否为 Bedrock 服务器。
     *
     * <p>Bedrock 默认端口为 19132/19133；Java 版默认端口为 25565。
     * ViaFabricPlus 会把 Bedrock 目标写在 {@code ip} 中，常见三种形态：
     * <ul>
     *   <li>带冒号端口：{@code host:19132}</li>
     *   <li>ViaFabricPlus 逗号改写：{@code host,0,19132}</li>
     *   <li>含 "bedrock" 关键字：{@code bedrock.example.com}</li>
     * </ul>
     * 本方法对以上三种均做识别。</p>
     */
    private static boolean isBedrockServer(ServerData serverData) {
        String ip = serverData.ip;
        if (ip == null || ip.isBlank()) {
            return false;
        }
        ip = ip.trim();

        // 关键字兜底：地址含 "bedrock" 视为 Bedrock
        if (ip.toLowerCase().contains("bedrock")) {
            return true;
        }

        // 逐一提取数字端口段（兼容冒号与 ViaFabricPlus 逗号改写两种分隔）
        for (String segment : ip.split("[,:]")) {
            String part = segment.trim();
            if (part.isEmpty()) {
                continue;
            }
            try {
                int port = Integer.parseInt(part);
                if (port == 19132 || port == 19133) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // 非数字段（主机名/IP），跳过
            }
        }
        return false;
    }

    /**
     * 返回当前检测状态的详细描述（供 {@code /bpb status} 诊断）。
     */
    public static String describeStatus(Minecraft client) {
        StringBuilder sb = new StringBuilder();
        sb.append("ViaFabricPlus已加载=").append(isViaFabricPlusLoaded());
        if (isViaFabricPlusLoaded()) {
            sb.append(", 翻译会话=").append(isViaServerActive());
            var current = client.getCurrentServer();
            if (current != null) {
                sb.append(", 服务器=").append(current.ip);
            } else {
                sb.append(", 当前无服务器");
            }
        }
        return sb.toString();
    }
}