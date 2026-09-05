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
 * Bedrock 定位条 HUD 渲染器（自渲染，精确复刻原版 Locator Bar 布局与纹理）。
 *
 * <p>默认固定在**经验条原本紧贴物品栏上方的位置**渲染一条类似原版的指示条，
 * 并让经验条向上让位（见 {@code mixin/ContextualBarTopMixin}）。</p>
 *
 * <p>仅有玩家可定位（且通过 Bedrock 会话检测）时才会出现；无玩家时自动隐藏，
 * 经验条自然回落到原版位置。</p>
 */
public final class BedrockLocatorBarHud {

    // ---- 原版定位栏常量（来自 ContextualBar / LocatorBar / WaypointStyle）----
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int DOT_SIZE = 9;
    private static final int ARROW_WIDTH = 7;
    private static final int ARROW_HEIGHT = 5;
    /** 经验条上移量：让出定位条与物品栏之间的空间（≈ 一个圆点高度 + 边距）。 */
    private static final int EXPERIENCE_BAR_RAISE = 14;
    /** 可见偏航角范围 ±60 度。 */
    private static final int VISIBLE_DEGREE_RANGE = 60;
    /** 偏航角→像素 x 偏移系数（floor(yaw * 173 / 2 / 60)）。 */
    private static final double X_OFFSET_RATIO = 173.0 / 2.0 / 60.0;
    private static final int NEAR_DISTANCE = 128;
    private static final int FAR_DISTANCE = 332;

    // ---- 原版定位栏 sprite ----
    private static final Identifier SPRITE_BACKGROUND = sprite("hud/locator_bar_background");
    private static final Identifier SPRITE_ARROW_UP = sprite("hud/locator_bar_arrow_up");
    private static final Identifier SPRITE_ARROW_DOWN = sprite("hud/locator_bar_arrow_down");
    private static final List<Identifier> SPRITE_DOTS = List.of(
            sprite("hud/locator_bar_dot/default_0"),
            sprite("hud/locator_bar_dot/default_1"),
            sprite("hud/locator_bar_dot/default_2"),
            sprite("hud/locator_bar_dot/default_3"));

    /** 定位条是否处于激活状态（是否有玩家可定位），供 mixin 决定是否抬高经验条。 */
    private static volatile boolean active;

    private BedrockLocatorBarHud() {
    }

    private static Identifier sprite(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    /** 定位条是否处于激活状态。 */
    public static boolean isActive() {
        return active;
    }

    /** 是否需要抬高经验条（由 {@code ContextualBarTopMixin} 调用）。 */
    public static boolean shouldRaiseExperienceBar() {
        return active;
    }

    /** 经验条需要上移的像素数。 */
    public static int getExperienceBarRaise() {
        return EXPERIENCE_BAR_RAISE;
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
            active = false;
            return;
        }

        BedrockPositioningBarConfig config = BedrockPositioningBarConfig.get();
        if (!config.showPlayers
                || (config.onlyOnBedrock && !ViaFabricPlusDetector.shouldShowCustomBar(client))) {
            active = false;
            return;
        }

        List<AbstractClientPlayer> others = collectOtherPlayers(client);

        // 无玩家可定位 → 隐藏定位条，经验条回落
        if (others.isEmpty()) {
            active = false;
            return;
        }
        active = true;

        renderBar(graphics, client, others, config);
    }

    private static List<AbstractClientPlayer> collectOtherPlayers(Minecraft client) {
        List<AbstractClientPlayer> result = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof AbstractClientPlayer player) {
                if (player == client.player) {
                    continue;
                }
                // 过滤隐身玩家（潜行/喝隐身药水）与旁观者
                if (player.isInvisible() || player.isSpectator()) {
                    continue;
                }
                result.add(player);
            }
        }
        // 按距离从近到远排序，近的玩家显示在更靠前的位置
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

        // 定位条固定落在经验条原本的位置（贴近物品栏上方）
        int left = (guiWidth - BAR_WIDTH) / 2;
        int top = guiHeight - 29;

        // 背景（原版九宫格纹理）
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE_BACKGROUND, left, top, BAR_WIDTH, BAR_HEIGHT);

        // 玩家点基准：中心 x，y 与背景中心对齐（点中心 = 背景 top - 2)
        int centerX = Mth.ceil((guiWidth - DOT_SIZE) / 2.0f);
        int dotY = top - 2;

        for (AbstractClientPlayer player : others) {
            drawIndicator(graphics, self, player, centerX, dotY, config);
        }
    }

    private static void drawIndicator(
            GuiGraphicsExtractor graphics,
            LocalPlayer self,
            AbstractClientPlayer target,
            int centerX,
            int dotY,
            BedrockPositioningBarConfig config
    ) {
        Vec3 diff = target.position().subtract(self.position());
        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

        // 超出原版最远距离则不再显示
        if (horizontalDist > FAR_DISTANCE) {
            return;
        }

        // 相对偏航角，范围约 (-180,180]
        float yawToTarget = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float relativeYaw = Mth.wrapDegrees(yawToTarget - self.getYRot());

        // 只显示正前方 ±60 度视野内的玩家
        if (Math.abs(relativeYaw) > VISIBLE_DEGREE_RANGE) {
            return;
        }

        // 相对俯仰角（判断是否需绘制上/下箭头）
        float pitchToTarget = (float) Math.toDegrees(Math.atan2(diff.y, horizontalDist));
        float relativePitch = Mth.wrapDegrees(pitchToTarget - self.getXRot());

        Identifier sprite = spriteForDistance((float) horizontalDist);

        // 颜色：队伍颜色优先，否则用 UUID hash 稳定派生
        int color = getPlayerColor(target);
        color = ARGB.setBrightness(color, 0.9f);

        // x 偏移复刻原版，y 固定为背景中心
        int x = centerX + Mth.floor(relativeYaw * X_OFFSET_RATIO);
        int y = dotY;

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, DOT_SIZE, DOT_SIZE, color);

        // 俯仰方向箭头（UP 在上方、DOWN 在下方）
        if (config.showOffscreenArrows) {
            if (relativePitch > 45.0f) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE_ARROW_DOWN,
                        x + 1, y + 6, ARROW_WIDTH, ARROW_HEIGHT);
            } else if (relativePitch < -45.0f) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE_ARROW_UP,
                        x + 1, y - 6, ARROW_WIDTH, ARROW_HEIGHT);
            }
        }
    }

    /**
     * 复刻原版 {@code WaypointStyle.sprite(float)} 的距离分级：
     * <ul>
     *   <li>distance &lt; 128 → default_0</li>
     *   <li>distance ≥ 332 → default_3</li>
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

    /** 玩家指示器颜色：队伍颜色优先，否则基于 UUID hash 稳定派生。 */
    private static int getPlayerColor(AbstractClientPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team != null && team.getColor().isPresent()) {
            return 0xFF000000 | team.getColor().get().rgb();
        }
        return ARGB.color(255, player.getUUID().hashCode());
    }
}