package com.moxi.bedrockpositioningbar.hud;

import com.moxi.bedrockpositioningbar.BedrockPositioningBarMod;
import com.moxi.bedrockpositioningbar.config.BedrockPositioningBarConfig;
import com.moxi.bedrockpositioningbar.util.BedrockDebugLog;
import com.moxi.bedrockpositioningbar.util.ViaFabricPlusDetector;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bedrock 定位条 HUD 渲染器（方案 B：自渲染）。
 *
 * <p>原理：读取客户端世界中的其他玩家，计算其相对本地玩家的方位角与俯仰角，
 * 在屏幕底部（经验条上方）绘制一条类似原版定位栏的指示条。</p>
 *
 * <p>本渲染器**精确复刻原版 Locator Bar**（{@code net.minecraft.client.gui.contextualbar.LocatorBar}）
 * 的布局、纹理与距离分级，因此视觉与 Java 版原生定位栏完全一致。</p>
 *
 * <p>仅当检测到 ViaFabricPlus 的 Bedrock 会话时才会启用（见 {@link ViaFabricPlusDetector}）。</p>
 */
public final class BedrockLocatorBarHud {

    // ---- 原版定位栏常量（来自 LocatorBar / ContextualBar / WaypointStyle）----
    /** 背景条宽（原版 182）。 */
    private static final int BASE_WIDTH = 182;
    /** 背景条高（原版 5）。 */
    private static final int BASE_HEIGHT = 5;
    /** 玩家点图标尺寸（原版 9×9）。 */
    private static final int DOT_SIZE = 9;
    /** 上/下箭头尺寸（原版 7×5）。 */
    private static final int ARROW_WIDTH = 7;
    private static final int ARROW_HEIGHT = 5;
    /** 背景距屏幕底部边距（原版 24）。 */
    private static final int MARGIN_BOTTOM = 24;
    /** 可见偏航角范围 ±60 度（原版 VISIBLE_DEGREE_RANGE）。 */
    private static final int VISIBLE_DEGREE_RANGE = 60;
    /** 偏航角→像素 x 偏移系数（原版 floor(yaw * 173 / 2 / 60)）。 */
    private static final double X_OFFSET_RATIO = 173.0 / 2.0 / 60.0;
    /** 距离分级阈值（原版 WaypointStyle 默认值）。 */
    private static final int NEAR_DISTANCE = 128;
    private static final int FAR_DISTANCE = 332;

    // ---- 原版定位栏 sprite（直接引用原版资源，保证视觉完全一致）----
    private static final Identifier SPRITE_BACKGROUND = sprite("hud/locator_bar_background");
    private static final Identifier SPRITE_ARROW_UP = sprite("hud/locator_bar_arrow_up");
    private static final Identifier SPRITE_ARROW_DOWN = sprite("hud/locator_bar_arrow_down");
    /** 距离分级的 4 档圆点纹理（近→远）。 */
    private static final List<Identifier> SPRITE_DOTS = List.of(
            sprite("hud/locator_bar_dot/default_0"),
            sprite("hud/locator_bar_dot/default_1"),
            sprite("hud/locator_bar_dot/default_2"),
            sprite("hud/locator_bar_dot/default_3"));

    private BedrockLocatorBarHud() {
    }

    private static Identifier sprite(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    /** 注册 HUD 元素到聊天层之前，保证渲染在 HUD 最上层。 */
    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(BedrockPositioningBarMod.MOD_ID, "locator_bar"),
                BedrockLocatorBarHud::render
        );
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        BedrockPositioningBarConfig config = BedrockPositioningBarConfig.get();
        if (!config.showPlayers) {
            return;
        }
        // onlyOnBedrock=true 时，仅在 ViaFabricPlus 的 Bedrock 会话中显示；
        // 否则（false）作为通用定位条，在任何服务器都显示。
        if (config.onlyOnBedrock && !ViaFabricPlusDetector.shouldShowCustomBar(client)) {
            return;
        }

        List<AbstractClientPlayer> others = collectOtherPlayers(client);

        // ---- 诊断：玩家数变化时打印一次，用于定位「渲染未调用」还是「拿不到玩家」----
        // othersCount == 0 且背景条未出现 → 说明 BE 玩家未以 AbstractClientPlayer 出现在客户端
        // othersCount 变化说明渲染确实每帧在被调用
        logDiagnostic(config, others.size());

        // 即使没有玩家也绘制背景条（背景不依赖实体），便于区分渲染是否真的被调用
        renderBar(graphics, client, others, config);
    }

    /** 玩家数/渲染状态变化时打印诊断日志（带节流，避免每帧刷屏）。 */
    private static int lastLoggedCount = Integer.MIN_VALUE;

    private static void logDiagnostic(BedrockPositioningBarConfig config, int count) {
        if (count != lastLoggedCount) {
            lastLoggedCount = count;
            String line = String.format(
                    "[BPB][render] onlyOnBedrock=%s showPlayers=%s othersCount=%d",
                    config.onlyOnBedrock, config.showPlayers, count);
            BedrockPositioningBarMod.LOGGER.info(line);
            BedrockDebugLog.info(line);
        }
    }

    private static List<AbstractClientPlayer> collectOtherPlayers(Minecraft client) {
        List<AbstractClientPlayer> result = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof AbstractClientPlayer player) {
                if (player == client.player) {
                    continue;
                }
                // 过滤隐身玩家（如潜行/喝隐身药水）与旁观者
                if (player.isInvisible() || player.isSpectator()) {
                    continue;
                }
                result.add(player);
            }
        }
        // 按距离从近到远排序，近的玩家显示在更靠前（高优先级）的位置
        result.sort(Comparator.comparingDouble(p -> p.distanceToSqr(client.player)));
        return result;
    }

    private static void renderBar(
            GuiGraphicsExtractor graphics,
            Minecraft client,
            List<AbstractClientPlayer> others,
            BedrockPositioningBarConfig config
    ) {
        LocalPlayer self = client.player;
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();

        // 整体缩放（默认 1.0 时精确复刻原版布局）
        double scale = Mth.clamp(config.hudScale, 0.5, 2.0);
        int bgW = (int) Math.round(BASE_WIDTH * scale);
        int bgH = (int) Math.round(BASE_HEIGHT * scale);
        int dotSize = (int) Math.round(DOT_SIZE * scale);
        int arrowW = (int) Math.round(ARROW_WIDTH * scale);
        int arrowH = (int) Math.round(ARROW_HEIGHT * scale);
        int margin = (int) Math.round(MARGIN_BOTTOM * scale);

        // 原版定位栏：背景居中，位于屏幕底部经验条上方
        int left = (guiWidth - bgW) / 2;
        int top = guiHeight - margin - bgH;

        // 背景（原版九宫格纹理，拉伸到 bgW×bgH）
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE_BACKGROUND, left, top, bgW, bgH);

        // 玩家点基准：中心 x = ceil((guiWidth - dotSize)/2)，y = top - 2（点中心与背景中心对齐）
        int centerX = Mth.ceil((guiWidth - dotSize) / 2.0f);
        int dotY = top - (int) Math.round(2 * scale);

        for (AbstractClientPlayer player : others) {
            drawIndicator(graphics, self, player, centerX, dotY,
                    dotSize, arrowW, arrowH, scale, config);
        }
    }

    private static void drawIndicator(
            GuiGraphicsExtractor graphics,
            LocalPlayer self,
            AbstractClientPlayer target,
            int centerX,
            int dotY,
            int dotSize,
            int arrowW,
            int arrowH,
            double scale,
            BedrockPositioningBarConfig config
    ) {
        Vec3 diff = target.position().subtract(self.position());
        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

        // 超出原版最远距离则不再显示
        if (horizontalDist > FAR_DISTANCE) {
            return;
        }

        // 相对偏航角（以玩家朝向为基准），范围约 (-180,180]
        float yawToTarget = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float relativeYaw = Mth.wrapDegrees(yawToTarget - self.getYRot());

        // 原版定位栏只显示正前方 ±60 度视野内的玩家，视野外不显示
        if (Math.abs(relativeYaw) > VISIBLE_DEGREE_RANGE) {
            return;
        }

        // 相对俯仰角（用于判断是否需绘制上/下箭头）
        float pitchToTarget = (float) Math.toDegrees(Math.atan2(diff.y, horizontalDist));
        float relativePitch = Mth.wrapDegrees(pitchToTarget - self.getXRot());

        // 按距离选择对应档位的圆点纹理（复刻 WaypointStyle.sprite）
        Identifier sprite = spriteForDistance((float) horizontalDist);

        // 颜色：队伍颜色优先，否则用 UUID hash 稳定派生
        int color = getPlayerColor(target);
        color = ARGB.setBrightness(color, 0.9f);

        // x 偏移（复刻原版 floor(yaw * 173 / 2 / 60)），y 固定为背景中心
        int x = centerX + Mth.floor(relativeYaw * X_OFFSET_RATIO * scale);
        int y = dotY;

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, dotSize, dotSize, color);

        // 俯仰方向箭头（复刻原版 PitchDirection：UP 在上方、DOWN 在下方）
        if (config.showOffscreenArrows) {
            int arrowInsetX = (int) Math.round(1 * scale);
            int arrowOffsetY = (int) Math.round(6 * scale);
            if (relativePitch > 45.0f) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE_ARROW_DOWN,
                        x + arrowInsetX, y + arrowOffsetY, arrowW, arrowH);
            } else if (relativePitch < -45.0f) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE_ARROW_UP,
                        x + arrowInsetX, y - arrowOffsetY, arrowW, arrowH);
            }
        }
    }

    /**
     * 复刻原版 {@code WaypointStyle.sprite(float)} 的距离分级：
     * <ul>
     *   <li>distance &lt; 128 → default_0（满尺寸）</li>
     *   <li>distance ≥ 332 → default_3（最远）</li>
     *   <li>中间按比例在 default_1 ~ default_3 之间线性插值</li>
     * </ul>
     */
    private static Identifier spriteForDistance(float distance) {
        if (distance < NEAR_DISTANCE) {
            return SPRITE_DOTS.get(0);
        }
        if (distance >= FAR_DISTANCE) {
            return SPRITE_DOTS.get(3);
        }
        int idx = Mth.lerpInt(
                (distance - NEAR_DISTANCE) / (float) (FAR_DISTANCE - NEAR_DISTANCE),
                1, 3);
        return SPRITE_DOTS.get(idx);
    }

    /**
     * 获取玩家指示器颜色。
     *
     * <p>**队伍颜色覆盖**：若玩家属于带颜色的队伍，则使用队伍颜色
     * （（复刻原版从 LocatorBarPacket 读取的队伍颜色）。否则回退为基于
     * UUID hash 稳定派生的颜色，保证同一玩家颜色一致。</p>
     */
    private static int getPlayerColor(AbstractClientPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team != null && team.getColor().isPresent()) {
            return 0xFF000000 | team.getColor().get().rgb();
        }
        // 无队伍：复刻原版 ARGB.color(255, uuid.hashCode())
        return ARGB.color(255, player.getUUID().hashCode());
    }
}