# 阶段 9：测试与交付 Prompt

```text
你现在是资深测试架构师和交付负责人，请基于当前项目文档，为心理测评系统制定测试和交付方案。

重点参考：
- doc/04-data-model-design.md
- doc/07-data-privacy-and-security.md
- doc/08-acceptance-test-matrix.md
- doc/10-database-table-design.md
- doc/13-api-design-detailed.md
- doc/15-openapi-draft.yaml

你的任务：
1. 基于现有验收矩阵，补充更完整的测试分层方案。
2. 区分单元测试、集成测试、接口测试、UI 测试、性能测试、安全测试。
3. 给出主链路测试优先级。
4. 设计联调顺序，说明前后端和移动端如何协同联调。
5. 补充多端兼容性测试要求，包括 Android、iOS、小程序基础库差异。
6. 输出一份发布前检查清单。
7. 输出 CI/CD 流水线建议和自动化测试集成方案。

输出要求：
- 以项目交付为导向
- 主链路优先
- 重点覆盖权限、预警、报告、预约、通知

预期产出物：
- 测试分层方案
- 联调顺序
- 兼容性测试建议
- CI/CD 建议
- 发布前检查清单

上下文传入方式：
- 必传本 Prompt
- 建议附上阶段 3~8 的实现方案摘要
```
