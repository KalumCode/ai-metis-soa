package io.kalum.metis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ModelConfigStore 文件式存储的行为验证：加载、增删改落盘、损坏文件容错。
 */
class ModelConfigStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyDirectoryShouldReturnEmptyListWithoutCreatingFile() {
        ModelConfigStore store = new ModelConfigStore(tempDir.resolve("model-configs.json"));

        assertTrue(store.list().isEmpty());
        assertFalse(Files.exists(tempDir.resolve("model-configs.json")));
    }

    @Test
    void createShouldPersistAndBeReadableByNewStore() {
        Path file = tempDir.resolve("model-configs.json");
        ModelConfigStore store = new ModelConfigStore(file);

        ModelConfig created = store.create("http://192.168.19.172:8888/v1", "sk-test", "glm-5.3-flash");

        assertFalse(created.id().isBlank());
        assertEquals("http://192.168.19.172:8888/v1", created.baseUrl());
        assertEquals("sk-test", created.apiKey());
        assertEquals("glm-5.3-flash", created.modelName());
        assertTrue(Files.exists(file), "create 后应立即落盘");

        // 模拟重启：新 Store 实例从文件读回
        List<ModelConfig> reloaded = new ModelConfigStore(file).list();
        assertEquals(1, reloaded.size());
        assertEquals(created, reloaded.get(0));
    }

    @Test
    void updateShouldReturnEmptyForUnknownId() {
        ModelConfigStore store = new ModelConfigStore(tempDir.resolve("model-configs.json"));

        Optional<ModelConfig> updated = store.update("no-such-id", "http://x/v1", "", "m");
        assertTrue(updated.isEmpty());
        assertFalse(Files.exists(tempDir.resolve("model-configs.json")), "未命中的 update 不应落盘");
    }

    @Test
    void updateAndDeleteShouldPersist() {
        Path file = tempDir.resolve("model-configs.json");
        ModelConfigStore store = new ModelConfigStore(file);
        ModelConfig created = store.create("http://a/v1", "key", "m1");

        Optional<ModelConfig> updated =
                store.update(created.id(), "http://b/v1", "key2", "m2");
        assertTrue(updated.isPresent());
        assertEquals("http://b/v1", updated.get().baseUrl());
        assertEquals("m2", updated.get().modelName());

        List<ModelConfig> afterUpdate = new ModelConfigStore(file).list();
        assertEquals(1, afterUpdate.size());
        assertEquals("m2", afterUpdate.get(0).modelName());

        assertTrue(store.delete(created.id()));
        assertTrue(new ModelConfigStore(file).list().isEmpty());
        assertFalse(store.delete(created.id()), "重复删除应返回 false");
    }

    @Test
    void corruptFileShouldBeBackedUpAndTreatedAsEmpty() throws IOException {
        Path file = tempDir.resolve("model-configs.json");
        Files.writeString(file, "{ not valid json");

        ModelConfigStore store = new ModelConfigStore(file);
        assertTrue(store.list().isEmpty());
        assertFalse(Files.exists(file), "损坏文件应被改名备份");
        assertTrue(Files.exists(tempDir.resolve("model-configs.json.corrupt")), "应生成 .corrupt 备份");

        // 备份后可正常继续写入
        ModelConfig created = store.create("http://a/v1", "", "m");
        assertEquals(created, new ModelConfigStore(file).list().get(0));
    }
}
