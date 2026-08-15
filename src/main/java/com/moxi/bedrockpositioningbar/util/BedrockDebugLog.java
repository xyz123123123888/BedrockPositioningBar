package com.moxi.bedrockpositioningbar.util;

import com.moxi.bedrockpositioningbar.BedrockPositioningBarMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 模组独立日志写入器。
 *
 * <p>将模组关键运行日志单独写入 <code>config/bedrockpositioningbar/debug.log</code>，
 * 与游戏主日志（latest.log）分离，方便后续排查定位条不显示、会话检测失败等问题。</p>
 *
 * <p>写入采用「追加」模式，跨会话保留历史；每次启动追加一条分隔线便于区分。</p>
 */
public final class BedrockDebugLog {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static Path logPath;

    private BedrockDebugLog() {
    }

    /** 初始化：创建日志目录，记录启动分隔线。 */
    public static void init() {
        logPath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(BedrockPositioningBarMod.MOD_ID)
                .resolve("debug.log");
        try {
            Path parent = logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writeRaw("========== 模组启动 / 会话开始 ==========");
        } catch (IOException e) {
            // 独立日志不可用时静默降级，不影响模组主流程
            BedrockPositioningBarMod.LOGGER.debug("[BPB] 独立日志初始化失败", e);
        }
    }

    /** 记录 INFO 级别日志。 */
    public static void info(String msg) {
        log("INFO", msg);
    }

    /** 记录 WARN 级别日志。 */
    public static void warn(String msg) {
        log("WARN", msg);
    }

    /** 记录 ERROR 级别日志并附带堆栈。 */
    public static void error(String msg, Throwable t) {
        log("ERROR", msg);
        if (logPath != null && t != null) {
            try (BufferedWriter w = newBufferedWriter()) {
                t.printStackTrace(new java.io.PrintWriter(w));
            } catch (IOException ignored) {
                // 忽略，日志系统尽力而为
            }
        }
    }

    private static void log(String level, String msg) {
        if (logPath == null) {
            return;
        }
        try {
            writeRaw(LocalDateTime.now().format(TS) + " [" + level + "] " + msg);
        } catch (IOException e) {
            BedrockPositioningBarMod.LOGGER.debug("[BPB] 独立日志写入失败", e);
        }
    }

    private static void writeRaw(String line) throws IOException {
        try (BufferedWriter w = newBufferedWriter()) {
            w.write(line);
            w.newLine();
        }
    }

    private static BufferedWriter newBufferedWriter() throws IOException {
        return Files.newBufferedWriter(
                logPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }
}