package io.kalum.metis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 模型配置的文件式存储：唯一的配置状态持有处。
 *
 * <p>配置落在 {@code .metis/model-configs.json}（相对启动目录解析，与 agent 工作区同根，已 gitignore）。
 * 懒加载 + synchronized：首次访问读文件，写操作改内存后立即落盘（先写临时文件再原子替换）。
 *
 * <p>容错策略：文件不存在视为空列表（首次写操作才创建）；文件损坏时改名为 {@code .corrupt}
 * 备份并视为空列表，不 crash、不静默覆盖。
 */
@Component
public class ModelConfigStore {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigStore.class);

    static final Path DEFAULT_FILE = Paths.get(".metis/model-configs.json");

    private final Path file;
    private final ObjectMapper json = new ObjectMapper();

    /** 内存缓存，null 表示尚未从文件加载。 */
    private List<ModelConfig> configs;

    public ModelConfigStore() {
        this(DEFAULT_FILE);
    }

    /** 包私有构造：测试用 @TempDir 注入自定义文件路径。 */
    ModelConfigStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public synchronized List<ModelConfig> list() {
        ensureLoaded();
        return List.copyOf(configs);
    }

    /** 按 id 查找配置（chat.send 的 params.model 传配置 id，用于按配置整体切换模型）。 */
    public synchronized Optional<ModelConfig> get(String id) {
        ensureLoaded();
        return configs.stream().filter(config -> config.id().equals(id)).findFirst();
    }

    public synchronized ModelConfig create(String baseUrl, String apiKey, String modelName) {
        ensureLoaded();
        ModelConfig config =
                new ModelConfig(UUID.randomUUID().toString(), baseUrl, apiKey, modelName);
        configs = new ArrayList<>(configs);
        configs.add(config);
        persist();
        return config;
    }

    public synchronized Optional<ModelConfig> update(
            String id, String baseUrl, String apiKey, String modelName) {
        ensureLoaded();
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).id().equals(id)) {
                ModelConfig updated = new ModelConfig(id, baseUrl, apiKey, modelName);
                configs.set(i, updated);
                persist();
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }

    public synchronized boolean delete(String id) {
        ensureLoaded();
        boolean removed = configs.removeIf(config -> config.id().equals(id));
        if (removed) {
            persist();
        }
        return removed;
    }

    private void ensureLoaded() {
        if (configs != null) {
            return;
        }
        if (!Files.exists(file)) {
            configs = new ArrayList<>();
            return;
        }
        try {
            Payload payload = json.readValue(file.toFile(), Payload.class);
            configs = new ArrayList<>(payload.configs() != null ? payload.configs() : List.of());
        } catch (IOException e) {
            log.warn("模型配置文件损坏，已备份并视为空列表: {}", file, e);
            backupCorruptFile();
            configs = new ArrayList<>();
        }
    }

    /** 把损坏的配置文件改名为 .corrupt 备份（已存在则追加时间戳），避免下次加载再报错。 */
    private void backupCorruptFile() {
        Path backup = file.resolveSibling(file.getFileName() + ".corrupt");
        if (Files.exists(backup)) {
            backup = file.resolveSibling(file.getFileName() + "." + System.currentTimeMillis() + ".corrupt");
        }
        try {
            Files.move(file, backup);
        } catch (IOException e) {
            log.warn("备份损坏的模型配置文件失败: {} -> {}", file, backup, e);
        }
    }

    private void persist() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            json.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), new Payload(configs));
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Windows 部分文件系统不支持原子移动，降级为普通替换
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("写入模型配置文件失败: " + file, e);
        }
    }

    /** 配置文件顶层结构，包装对象便于未来扩展顶层字段。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Payload(List<ModelConfig> configs) {}
}
