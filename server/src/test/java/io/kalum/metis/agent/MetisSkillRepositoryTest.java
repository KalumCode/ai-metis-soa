package io.kalum.metis.agent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import org.junit.jupiter.api.Test;

/**
 * 验证 classpath 技能仓库能发现随工程发布的技能（{@code resources/skills/}）。
 *
 * <p>技能随 JAR 打包，是"重装后默认初始化"的保障，这里确保目录结构与
 * {@code ClasspathSkillRepository} 的扫描约定一致。
 */
class MetisSkillRepositoryTest {

    @Test
    void shouldDiscoverBundledSkill() throws Exception {
        try (ClasspathSkillRepository repository = MetisAgentFactory.buildSkillRepository()) {
            assertTrue(
                    repository.getAllSkillNames().contains("richinfo-java-architect"),
                    "resources/skills/ 下应包含 richinfo-java-architect 技能");
            assertNotNull(repository.getSkill("richinfo-java-architect"));
        }
    }

    @Test
    void skillDescriptionShouldNotBeBlank() throws Exception {
        try (ClasspathSkillRepository repository = MetisAgentFactory.buildSkillRepository()) {
            String description = repository.getSkill("richinfo-java-architect").getDescription();
            assertTrue(description != null && !description.isBlank(), "SKILL.md 需要 description");
        }
    }

    @Test
    void skillResourceShouldBeReadable() throws Exception {
        try (ClasspathSkillRepository repository = MetisAgentFactory.buildSkillRepository()) {
            String template =
                    repository.getSkill("richinfo-java-architect")
                            .getResource("templates/standard-template.md");
            assertTrue(template != null && !template.isBlank(), "技能模板资源应可读取");
        }
    }
}
