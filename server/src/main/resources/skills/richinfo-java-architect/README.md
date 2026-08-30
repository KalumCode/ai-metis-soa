# java-architect

从业务视角生成标准化 Java 后端技术方案的技能。

## 用途

用户描述业务需求后，技能按三步流程生成完整技术方案：

1. **需求澄清** — 先推断理解，再请用户确认
2. **方案大纲** — 生成模块拆分与核心步骤，等用户确认
3. **完整方案** — 输出标准化 Markdown 技术文档

## 核心原则

- **业务优先**：以业务事件、业务动作描述流程，禁止出现 Controller/Service/DAO/SQL 等代码
- **数据流驱动**：每个模块必须讲清楚数据从哪来、怎么处理、写到哪去
- **规范约束**：严格遵循《彩讯科技软件架构设计规范》
- **推断先于询问**：必须先给出自己的理解，再请用户修正

## 文件结构

```
java-architect/
├── SKILL.md                          # 主技能规范（含触发词、版本、强制规范）
├── README.md                         # 本文件
└── templates/
    ├── speckit-step1.md              # 第1步：需求澄清输出模板
    ├── speckit-step2.md              # 第2步：方案大纲输出模板
    ├── standard-template.md          # 第3步：完整方案文档模板
    └── reference/
        └── architecture-rules.md     # 架构设计规则参考
```



## 触发词

根据 PRD.md 文档，调用 `java-architect` 技能生成技术方案文档，输出到 `example/迭代功能例子/ARCHITECT.md

