package com.moxi.bedrockpositioningbar.mixin;

import com.mojang.blaze3d.platform.Window;
import com.moxi.bedrockpositioningbar.hud.BedrockLocatorBarHud;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让经验条（ExperienceBar）在定位条显示时上移，为定位条让出空间。
 *
 * <p>原版 {@code ContextualBar.top(Window)} 返回 {@code guiHeight - 24 - 5}，
 * 经验条与物品栏之间仅约 7px，容纳不下定位条。定位条渲染时（有玩家可定位）将
 * 经验条整体上移 {@link BedrockLocatorBarHud#getExperienceBarRaise()} px，让定位条
 * 落在物品栏上方（经验条原本紧贴物品栏的位置）；无玩家时不偏移，经验条自然回落。</p>
 */
@Mixin(ContextualBar.class)
public interface ContextualBarTopMixin {

    @Inject(method = "top", at = @At("RETURN"), cancellable = true)
    private void bedrockpositioningbar$raiseTop(Window window, CallbackInfoReturnable<Integer> cir) {
        if (BedrockLocatorBarHud.shouldRaiseExperienceBar()) {
            cir.setReturnValue(cir.getReturnValue() - BedrockLocatorBarHud.getExperienceBarRaise());
        }
    }
}