package io.kalum.metis.model;

import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * 模型配置管理接口：供桌面端"模型配置"界面调用。
 *
 * <p>配置持久化在 {@link ModelConfigStore} 的配置文件中，apiKey 明文往返（本地单机部署约定）。
 * 连通测试用请求体直接测，新增前未保存的草稿也能验证。
 */
@RestController
@RequestMapping("/api/v1/model-configs")
public class ModelConfigController {

    private final ModelConfigStore store;
    private final ModelConnectivityTester tester;

    public ModelConfigController(ModelConfigStore store, ModelConnectivityTester tester) {
        this.store = store;
        this.tester = tester;
    }

    @GetMapping
    public List<ModelConfig> list() {
        return store.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModelConfig create(@RequestBody ModelConfigRequest request) {
        ModelConfigRequest normalized = validate(request);
        return store.create(normalized.baseUrl(), normalized.apiKey(), normalized.modelName());
    }

    @PutMapping("/{id}")
    public ModelConfig update(@PathVariable String id, @RequestBody ModelConfigRequest request) {
        ModelConfigRequest normalized = validate(request);
        return store
                .update(id, normalized.baseUrl(), normalized.apiKey(), normalized.modelName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型配置不存在"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!store.delete(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模型配置不存在");
        }
    }

    /** 连通性测试：连通失败返回 200 + success=false（业务结果），仅参数非法才 400。 */
    @PostMapping("/test")
    public Mono<ModelTestResult> test(@RequestBody ModelConfigRequest request) {
        ModelConfigRequest normalized = validate(request);
        return tester.test(normalized);
    }

    /**
     * 手写参数校验（项目未引入 jakarta.validation）：baseUrl 必须以 http:// 或 https:// 开头，
     * 模型名称非空；apiKey 允许为空（本地网关常无鉴权）。返回 trim 归一化后的请求体。
     */
    private ModelConfigRequest validate(ModelConfigRequest request) {
        Objects.requireNonNull(request, "请求体不能为空");
        String baseUrl = request.baseUrl() != null ? request.baseUrl().trim() : "";
        String apiKey = request.apiKey() != null ? request.apiKey().trim() : "";
        String modelName = request.modelName() != null ? request.modelName().trim() : "";

        if (baseUrl.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUrl 不能为空");
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "baseUrl 必须以 http:// 或 https:// 开头");
        }
        if (modelName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型名称不能为空");
        }
        return new ModelConfigRequest(baseUrl, apiKey, modelName);
    }

    /** 新增 / 修改 / 测试共用的请求体。 */
    public record ModelConfigRequest(String baseUrl, String apiKey, String modelName) {}

    /** 连通性测试结果。 */
    public record ModelTestResult(boolean success, String message) {}
}
