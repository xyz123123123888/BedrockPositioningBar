package com.moxi.bedrockpositioningbar.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Bedrock 定位条配置界面（纯 MIT 自绘，不依赖 Cloth Config / ModMenu）。
 *
 * <p>基于原版 {@link Screen} / {@link Button} / {@link CycleButton} 实现，每个选项即时生效并落盘，
 * 与现有命令系统共享同一份 {@link BedrockPositioningBarConfig}。</p>
 */
public final class BedrockPositioningBarConfigScreen extends Screen {

    /** HUD 缩放可选档位（0.5 ~ 2.0，覆盖命令允许范围）。 */
    private static final double[] HUD_SCALES = {0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0};

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_SPACING = 26;

    private final BedrockPositioningBarConfig config;
    private Button hudScaleButton;

    public BedrockPositioningBarConfigScreen() {
        super(Component.literal("Bedrock 定位条配置"));
        this.config = BedrockPositioningBarConfig.get();
    }

    @Override
    protected void init() {
        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = 60;

        addRenderableWidget(CycleButton.onOffBuilder(config.onlyOnBedrock)
                .create(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                        Component.literal("仅限 Bedrock 服务器"),
                        (btn, value) -> {
                            config.onlyOnBedrock = value;
                            BedrockPositioningBarConfig.save();
                        }));
        y += ROW_SPACING;

        addRenderableWidget(CycleButton.onOffBuilder(config.showPlayers)
                .create(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                        Component.literal("显示玩家"),
                        (btn, value) -> {
                            config.showPlayers = value;
                            BedrockPositioningBarConfig.save();
                        }));
        y += ROW_SPACING;

        addRenderableWidget(CycleButton.onOffBuilder(config.showOffscreenArrows)
                .create(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                        Component.literal("显示边界箭头"),
                        (btn, value) -> {
                            config.showOffscreenArrows = value;
                            BedrockPositioningBarConfig.save();
                        }));
        y += ROW_SPACING;

        hudScaleButton = Button.builder(Component.empty(), button -> cycleHudScale())
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        updateHudScaleButton();
        addRenderableWidget(hudScaleButton);
        y += ROW_SPACING;

        addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
                .bounds(x, this.height - 40, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    /** 点击缩放按钮：切换到下一档位并即时生效。 */
    private void cycleHudScale() {
        int next = (nearestScaleIndex() + 1) % HUD_SCALES.length;
        config.hudScale = HUD_SCALES[next];
        BedrockPositioningBarConfig.save();
        updateHudScaleButton();
    }

    /** 找到当前配置值最接近的档位下标。 */
    private int nearestScaleIndex() {
        double wanted = config.hudScale;
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < HUD_SCALES.length; i++) {
            double diff = Math.abs(HUD_SCALES[i] - wanted);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    private void updateHudScaleButton() {
        hudScaleButton.setMessage(Component.literal("HUD 缩放: " + config.hudScale));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // 原版 Screen 默认不绘制标题，这里居中绘制
        graphics.centeredText(font, this.title, this.width / 2, 15, 0xFFFFFF);
    }
}