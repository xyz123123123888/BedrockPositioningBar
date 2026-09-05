package com.moxi.bedrockpositioningbar.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.moxi.bedrockpositioningbar.BedrockPositioningBarMod;
import com.moxi.bedrockpositioningbar.util.BedrockDebugLog;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组配置。
 *
 * <p>当前采用极简 JSON 配置 + 内存缓存，便于后续接入 Cloth Config 或命令系统。</p>
 */
public final class BedrockPositioningBarConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("bedrockpositioningbar.json");

    private static BedrockPositioningBarConfig INSTANCE = new BedrockPositioningBarConfig();

    /** 是否仅在 Bedrock 服务器（ViaFabricPlus）时启用自渲染定位条。 */
    public boolean onlyOnBedrock = true;

    /** 是否渲染其他玩家指示器。 */
    public boolean showPlayers = true;

    /** 是否显示屏幕上方/下方的越界箭头。 */
    public boolean showOffscreenArrows = true;

    private BedrockPositioningBarConfig() {
    }

    /** 获取当前生效配置（线程安全读）。 */
    public static BedrockPositioningBarConfig get() {
        return INSTANCE;
    }

    /** 从磁盘加载配置，若不存在则写回默认值。 */
    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            BedrockPositioningBarConfig loaded = GSON.fromJson(reader, BedrockPositioningBarConfig.class);
            if (loaded != null) {
                INSTANCE = loaded;
            }
        } catch (IOException | JsonParseException e) {
            BedrockPositioningBarMod.LOGGER.warn(
                    "[BedrockPositioningBar] 配置读取失败，使用默认配置", e);
            BedrockDebugLog.warn("配置读取失败，使用默认配置: " + e.getMessage());
        }
    }

    /** 将当前配置写入磁盘。 */
    public static void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            BedrockPositioningBarMod.LOGGER.warn(
                    "[BedrockPositioningBar] 配置保存失败", e);
            BedrockDebugLog.warn("配置保存失败: " + e.getMessage());
        }
    }
}
