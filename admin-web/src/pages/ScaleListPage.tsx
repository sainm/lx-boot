import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert, Button, Descriptions, Divider, Drawer, Form, Input,
  InputNumber, Modal, Pagination, Select, Space, Table, Tag, Typography, message
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { Permission } from "../components/Permission";
import {
  batchCreateDimensions, batchCreateQuestions, batchCreateResultRules,
  createScale, fetchScaleDetail, fetchScalePage,
  type CreateDimensionItem, type CreateQuestionItem, type CreateResultRuleItem,
  type ScaleDimension, type ScaleQuestion, type ScaleResultRule
} from "../features/scales/api";

const PAGE_SIZE = 20;

export function ScaleListPage() {
  const [createOpen, setCreateOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [dimOpen, setDimOpen] = useState(false);
  const [questionOpen, setQuestionOpen] = useState(false);
  const [ruleOpen, setRuleOpen] = useState(false);
  const [selectedScaleId, setSelectedScaleId] = useState<number | null>(null);

  // filters
  const [nameInput, setNameInput] = useState("");
  const [nameFilter, setNameFilter] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(1);

  const [createForm] = Form.useForm();
  const [dimForm] = Form.useForm<{ dimensions: CreateDimensionItem[] }>();
  const [questionForm] = Form.useForm<{ questions: CreateQuestionItem[] }>();
  const [ruleForm] = Form.useForm<{ resultRules: CreateResultRuleItem[] }>();
  const queryClient = useQueryClient();

  const queryParams = { scaleName: nameFilter, page, size: PAGE_SIZE };

  const scaleQuery = useQuery({
    queryKey: ["scales", queryParams],
    queryFn: () => fetchScalePage(queryParams)
  });

  const detailQuery = useQuery({
    queryKey: ["scales", "detail", selectedScaleId],
    queryFn: () => fetchScaleDetail(selectedScaleId!),
    enabled: selectedScaleId != null && detailOpen
  });

  const createScaleMutation = useMutation({
    mutationFn: createScale,
    onSuccess: async () => {
      message.success("量表创建成功");
      setCreateOpen(false);
      createForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["scales"] });
    }
  });

  const batchDimMutation = useMutation({
    mutationFn: ({ scaleId, dimensions }: { scaleId: number; dimensions: CreateDimensionItem[] }) =>
      batchCreateDimensions(scaleId, dimensions),
    onSuccess: async () => {
      message.success("维度添加成功");
      setDimOpen(false);
      dimForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", selectedScaleId] });
    }
  });

  const batchQuestionMutation = useMutation({
    mutationFn: ({ scaleId, questions }: { scaleId: number; questions: CreateQuestionItem[] }) =>
      batchCreateQuestions(scaleId, questions),
    onSuccess: async () => {
      message.success("题目添加成功");
      setQuestionOpen(false);
      questionForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", selectedScaleId] });
    }
  });

  const batchRuleMutation = useMutation({
    mutationFn: ({ scaleId, resultRules }: { scaleId: number; resultRules: CreateResultRuleItem[] }) =>
      batchCreateResultRules(scaleId, resultRules),
    onSuccess: async () => {
      message.success("结果规则添加成功");
      setRuleOpen(false);
      ruleForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", selectedScaleId] });
    }
  });

  const handleSearch = () => {
    setNameFilter(nameInput.trim() || undefined);
    setPage(1);
  };

  const handleReset = () => {
    setNameInput("");
    setNameFilter(undefined);
    setPage(1);
  };

  const handleCreate = async () => {
    const values = await createForm.validateFields();
    await createScaleMutation.mutateAsync(values);
  };

  const handleAddDimensions = async () => {
    if (selectedScaleId == null) return;
    const values = await dimForm.validateFields();
    await batchDimMutation.mutateAsync({
      scaleId: selectedScaleId,
      dimensions: values.dimensions
    });
  };

  const handleAddQuestions = async () => {
    if (selectedScaleId == null) return;
    const values = await questionForm.validateFields();
    await batchQuestionMutation.mutateAsync({
      scaleId: selectedScaleId,
      questions: values.questions
    });
  };

  const handleAddRules = async () => {
    if (selectedScaleId == null) return;
    const values = await ruleForm.validateFields();
    await batchRuleMutation.mutateAsync({
      scaleId: selectedScaleId,
      resultRules: values.resultRules
    });
  };

  const openDetail = (id: number) => {
    setSelectedScaleId(id);
    setDetailOpen(true);
  };

  const detail = detailQuery.data;

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 16 }}>
        <div>
          <Typography.Title level={4}>量表管理</Typography.Title>
          <Typography.Text type="secondary">这里管理量表基础信息，创建后可以继续维护维度、题目和结果规则。</Typography.Text>
        </div>
        <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            新建量表
          </Button>
        </Permission>
      </div>

      <Space>
        <Input
          placeholder="按量表名称搜索"
          style={{ width: 260 }}
          value={nameInput}
          onChange={(e) => setNameInput(e.target.value)}
          onPressEnter={handleSearch}
          allowClear
          onClear={handleReset}
        />
        <Button type="primary" onClick={handleSearch}>查询</Button>
        <Button onClick={handleReset}>重置</Button>
      </Space>

      {scaleQuery.isError ? (
        <Alert type="warning" showIcon message="当前暂时无法获取量表数据，后端接口可用后会自动恢复。" />
      ) : null}

      <Table
        rowKey="id"
        loading={scaleQuery.isLoading}
        dataSource={scaleQuery.data?.list ?? []}
        pagination={false}
        columns={[
          { title: "量表编码", dataIndex: "scaleCode", width: 160 },
          { title: "量表名称", dataIndex: "scaleName" },
          { title: "适用对象", dataIndex: "applicableTarget" },
          { title: "版本", dataIndex: "versionNo", width: 80 },
          { title: "计分方式", dataIndex: "scoreMethod", width: 120 },
          {
            title: "状态",
            dataIndex: "status",
            width: 100,
            render: (value: string) => <Tag color="gold">{value}</Tag>
          },
          {
            title: "操作",
            width: 120,
            render: (_, record) => (
              <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                <Button type="link" onClick={() => openDetail(record.id)}>查看详情</Button>
              </Permission>
            )
          }
        ]}
      />

      {(scaleQuery.data?.total ?? 0) > PAGE_SIZE ? (
        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <Pagination
            current={page}
            pageSize={PAGE_SIZE}
            total={scaleQuery.data?.total ?? 0}
            showTotal={(total) => `共 ${total} 条`}
            onChange={(p) => setPage(p)}
            showSizeChanger={false}
          />
        </div>
      ) : null}

      {/* ── Scale Detail Drawer ─────────────────────────────────────────────── */}
      <Drawer
        title={detail ? `${detail.scaleName}（${detail.scaleCode}）` : "量表详情"}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={640}
        loading={detailQuery.isLoading}
        extra={
          <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
            <Space>
              <Button icon={<PlusOutlined />} onClick={() => setDimOpen(true)}>
                添加维度
              </Button>
              <Button icon={<PlusOutlined />} onClick={() => setQuestionOpen(true)}>
                添加题目
              </Button>
              <Button icon={<PlusOutlined />} onClick={() => setRuleOpen(true)}>
                结果规则
              </Button>
            </Space>
          </Permission>
        }
      >
        {detailQuery.isError ? (
          <Alert type="error" showIcon message="无法加载量表详情，请稍后重试。" />
        ) : null}

        {detail ? (
          <>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="量表编码">{detail.scaleCode}</Descriptions.Item>
              <Descriptions.Item label="量表名称">{detail.scaleName}</Descriptions.Item>
              <Descriptions.Item label="版本号">{detail.versionNo ?? "—"}</Descriptions.Item>
              <Descriptions.Item label="适用对象">{detail.applicableTarget ?? "—"}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color="gold">{detail.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="计分方式">{detail.scoreMethod}</Descriptions.Item>
              <Descriptions.Item label="换算系数">{detail.scoreCoefficient}</Descriptions.Item>
              <Descriptions.Item label="支持匿名">{detail.anonymousSupported ? "是" : "否"}</Descriptions.Item>
              {detail.description ? (
                <Descriptions.Item label="描述">{detail.description}</Descriptions.Item>
              ) : null}
            </Descriptions>

            <Divider orientation="left" plain>
              维度列表（{detail.dimensions.length}）
            </Divider>

            {detail.dimensions.length === 0 ? (
              <Typography.Text type="secondary">暂无维度，请点击"添加维度"开始配置。</Typography.Text>
            ) : (
              <Table<ScaleDimension>
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={detail.dimensions}
                columns={[
                  { title: "排序", dataIndex: "sortNo", width: 60 },
                  { title: "维度编码", dataIndex: "dimensionCode", width: 140 },
                  { title: "维度名称", dataIndex: "dimensionName" },
                  { title: "描述", dataIndex: "description" }
                ]}
              />
            )}

            <Divider orientation="left" plain>
              题目列表（{detail.questions.length}）
            </Divider>

            {detail.questions.length === 0 ? (
              <Typography.Text type="secondary">暂无题目，请点击"添加题目"开始配置。</Typography.Text>
            ) : (
              <Table<ScaleQuestion>
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={detail.questions}
                expandable={{
                  expandedRowRender: (q) => (
                    <Table
                      rowKey="id"
                      size="small"
                      pagination={false}
                      dataSource={q.options}
                      columns={[
                        { title: "编码", dataIndex: "optionCode", width: 80 },
                        { title: "选项内容", dataIndex: "optionLabel" },
                        { title: "分值", dataIndex: "scoreValue", width: 80 }
                      ]}
                    />
                  ),
                  rowExpandable: (q) => q.options.length > 0
                }}
                columns={[
                  { title: "题号", dataIndex: "questionNo", width: 60 },
                  { title: "题干", dataIndex: "questionTitle" },
                  { title: "类型", dataIndex: "questionType", width: 90 },
                  { title: "维度ID", dataIndex: "dimensionId", width: 80 },
                  { title: "必填", dataIndex: "requiredFlag", width: 60, render: (v: boolean) => v ? "是" : "否" }
                ]}
              />
            )}

            <Divider orientation="left" plain>
              结果规则（{detail.resultRules.length}）
            </Divider>

            {detail.resultRules.length === 0 ? (
              <Typography.Text type="secondary">暂无结果规则，请点击"结果规则"开始配置。</Typography.Text>
            ) : (
              <Table<ScaleResultRule>
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={detail.resultRules}
                columns={[
                  { title: "风险等级", dataIndex: "riskLevel", width: 100 },
                  { title: "最低分", dataIndex: "scoreMin", width: 80 },
                  { title: "最高分", dataIndex: "scoreMax", width: 80 },
                  { title: "结果标题", dataIndex: "resultTitle" },
                  { title: "维度ID", dataIndex: "dimensionId", width: 80 }
                ]}
              />
            )}
          </>
        ) : null}
      </Drawer>

      {/* ── Batch Add Dimensions Modal ──────────────────────────────────────── */}
      <Modal
        title="批量添加维度"
        open={dimOpen}
        onCancel={() => {
          setDimOpen(false);
          dimForm.resetFields();
        }}
        onOk={() => void handleAddDimensions()}
        confirmLoading={batchDimMutation.isPending}
        width={600}
        destroyOnClose
      >
        <Form form={dimForm} layout="vertical">
          <Form.List name="dimensions" initialValue={[{ dimensionCode: "", dimensionName: "", sortNo: 0 }]}>
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <Space key={key} align="start" style={{ display: "flex", marginBottom: 8 }} wrap>
                    <Form.Item
                      {...restField}
                      name={[name, "dimensionCode"]}
                      rules={[{ required: true, message: "请输入维度编码" }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Input placeholder="维度编码" style={{ width: 130 }} />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, "dimensionName"]}
                      rules={[{ required: true, message: "请输入维度名称" }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Input placeholder="维度名称" style={{ width: 150 }} />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, "sortNo"]} style={{ marginBottom: 0 }}>
                      <InputNumber placeholder="排序" style={{ width: 80 }} min={0} />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, "description"]} style={{ marginBottom: 0 }}>
                      <Input placeholder="描述（可选）" style={{ width: 160 }} />
                    </Form.Item>
                    {fields.length > 1 ? (
                      <Button type="link" danger onClick={() => remove(name)}>
                        删除
                      </Button>
                    ) : null}
                  </Space>
                ))}
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={() => add({ dimensionCode: "", dimensionName: "", sortNo: fields.length })}
                  style={{ width: "100%" }}
                >
                  添加一行
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>

      {/* ── Batch Add Questions Modal ───────────────────────────────────────── */}
      <Modal
        title="批量添加题目"
        open={questionOpen}
        onCancel={() => { setQuestionOpen(false); questionForm.resetFields(); }}
        onOk={() => void handleAddQuestions()}
        confirmLoading={batchQuestionMutation.isPending}
        width={780}
        destroyOnClose
      >
        <Form form={questionForm} layout="vertical">
          <Form.List name="questions" initialValue={[{ questionNo: 1, questionTitle: "", questionType: "CHOICE", requiredFlag: true, options: [{ optionCode: "A", optionLabel: "", scoreValue: 0, sortNo: 0 }] }]}>
            {(qFields, { add: addQ, remove: removeQ }) => (
              <>
                {qFields.map(({ key: qKey, name: qName }) => (
                  <div key={qKey} style={{ border: "1px solid #f0f0f0", borderRadius: 6, padding: 12, marginBottom: 12 }}>
                    <Space style={{ marginBottom: 8 }} wrap>
                      <Form.Item name={[qName, "questionNo"]} label="题号" style={{ marginBottom: 0 }} rules={[{ required: true }]}>
                        <InputNumber min={1} style={{ width: 70 }} />
                      </Form.Item>
                      <Form.Item name={[qName, "questionTitle"]} label="题干" style={{ marginBottom: 0 }} rules={[{ required: true, message: "请输入题干" }]}>
                        <Input placeholder="请输入题干" style={{ width: 260 }} />
                      </Form.Item>
                      <Form.Item name={[qName, "questionType"]} label="类型" style={{ marginBottom: 0 }}>
                        <Select style={{ width: 100 }} options={[{ label: "选择题", value: "CHOICE" }, { label: "文本题", value: "TEXT" }]} />
                      </Form.Item>
                      <Form.Item name={[qName, "dimensionId"]} label="维度ID" style={{ marginBottom: 0 }}>
                        <InputNumber min={1} placeholder="可选" style={{ width: 90 }} />
                      </Form.Item>
                      {qFields.length > 1 ? (
                        <Button type="link" danger onClick={() => removeQ(qName)} style={{ marginTop: 22 }}>删除题目</Button>
                      ) : null}
                    </Space>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>选项：</Typography.Text>
                    <Form.List name={[qName, "options"]}>
                      {(oFields, { add: addO, remove: removeO }) => (
                        <>
                          {oFields.map(({ key: oKey, name: oName }) => (
                            <Space key={oKey} style={{ display: "flex", marginBottom: 4 }} wrap>
                              <Form.Item name={[oName, "optionCode"]} style={{ marginBottom: 0 }} rules={[{ required: true, message: "编码" }]}>
                                <Input placeholder="编码 A/B/C" style={{ width: 80 }} />
                              </Form.Item>
                              <Form.Item name={[oName, "optionLabel"]} style={{ marginBottom: 0 }} rules={[{ required: true, message: "内容" }]}>
                                <Input placeholder="选项内容" style={{ width: 200 }} />
                              </Form.Item>
                              <Form.Item name={[oName, "scoreValue"]} style={{ marginBottom: 0 }} rules={[{ required: true }]}>
                                <InputNumber placeholder="分值" style={{ width: 80 }} />
                              </Form.Item>
                              {oFields.length > 1 ? (
                                <Button type="link" danger size="small" onClick={() => removeO(oName)}>删除</Button>
                              ) : null}
                            </Space>
                          ))}
                          <Button type="dashed" size="small" icon={<PlusOutlined />}
                            onClick={() => addO({ optionCode: "", optionLabel: "", scoreValue: 0, sortNo: oFields.length })}
                          >
                            添加选项
                          </Button>
                        </>
                      )}
                    </Form.List>
                  </div>
                ))}
                <Button type="dashed" icon={<PlusOutlined />}
                  onClick={() => addQ({ questionNo: qFields.length + 1, questionTitle: "", questionType: "CHOICE", requiredFlag: true, options: [{ optionCode: "A", optionLabel: "", scoreValue: 0, sortNo: 0 }] })}
                  style={{ width: "100%" }}
                >
                  添加题目
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>

      {/* ── Batch Add Result Rules Modal ────────────────────────────────────── */}
      <Modal
        title="批量添加结果规则"
        open={ruleOpen}
        onCancel={() => { setRuleOpen(false); ruleForm.resetFields(); }}
        onOk={() => void handleAddRules()}
        confirmLoading={batchRuleMutation.isPending}
        width={700}
        destroyOnClose
      >
        <Form form={ruleForm} layout="vertical">
          <Form.List name="resultRules" initialValue={[{ riskLevel: "NORMAL", scoreMin: 0, scoreMax: 100 }]}>
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name }) => (
                  <Space key={key} align="start" style={{ display: "flex", marginBottom: 8 }} wrap>
                    <Form.Item name={[name, "riskLevel"]} label="风险等级" style={{ marginBottom: 0 }} rules={[{ required: true }]}>
                      <Select style={{ width: 110 }} options={[
                        { label: "正常", value: "NORMAL" },
                        { label: "低风险", value: "LOW" },
                        { label: "中风险", value: "MODERATE" },
                        { label: "高风险", value: "HIGH" }
                      ]} />
                    </Form.Item>
                    <Form.Item name={[name, "scoreMin"]} label="最低分" style={{ marginBottom: 0 }} rules={[{ required: true }]}>
                      <InputNumber style={{ width: 90 }} />
                    </Form.Item>
                    <Form.Item name={[name, "scoreMax"]} label="最高分" style={{ marginBottom: 0 }} rules={[{ required: true }]}>
                      <InputNumber style={{ width: 90 }} />
                    </Form.Item>
                    <Form.Item name={[name, "resultTitle"]} label="结果标题" style={{ marginBottom: 0 }}>
                      <Input placeholder="可选" style={{ width: 140 }} />
                    </Form.Item>
                    <Form.Item name={[name, "dimensionId"]} label="维度ID" style={{ marginBottom: 0 }}>
                      <InputNumber min={1} placeholder="可选" style={{ width: 80 }} />
                    </Form.Item>
                    {fields.length > 1 ? (
                      <Button type="link" danger onClick={() => remove(name)} style={{ marginTop: 22 }}>删除</Button>
                    ) : null}
                  </Space>
                ))}
                <Button type="dashed" icon={<PlusOutlined />}
                  onClick={() => add({ riskLevel: "NORMAL", scoreMin: 0, scoreMax: 100 })}
                  style={{ width: "100%" }}
                >
                  添加规则
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>

      {/* ── Create Scale Modal ──────────────────────────────────────────────── */}      <Modal
        title="新建量表"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => void handleCreate()}
        confirmLoading={createScaleMutation.isPending}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" initialValues={{ versionNo: "v1", scoreMethod: "SIMPLE_SUM", scoreCoefficient: 1, anonymousSupported: false }}>
          <Form.Item label="量表编码" name="scaleCode" rules={[{ required: true, message: "请输入量表编码" }]}>
            <Input placeholder="例如：SCL-STRESS-01" />
          </Form.Item>
          <Form.Item label="量表名称" name="scaleName" rules={[{ required: true, message: "请输入量表名称" }]}>
            <Input placeholder="请输入量表名称" />
          </Form.Item>
          <Form.Item label="适用对象" name="applicableTarget">
            <Input placeholder="例如：student / employee" />
          </Form.Item>
          <Form.Item label="版本号" name="versionNo">
            <Input placeholder="v1" />
          </Form.Item>
          <Form.Item label="计分方式" name="scoreMethod" rules={[{ required: true }]}>
            <Select options={[
              { label: "简单求和（SIMPLE_SUM）", value: "SIMPLE_SUM" },
              { label: "反向计分求和（REVERSE_SUM）", value: "REVERSE_SUM" },
              { label: "加权求和（WEIGHTED_SUM）", value: "WEIGHTED_SUM" }
            ]} />
          </Form.Item>
          <Form.Item
            label="换算系数"
            name="scoreCoefficient"
            tooltip="粗分乘以该系数得到最终总分，大多数量表填 1。SAS/SDS 填 1.25。"
          >
            <InputNumber min={0.0001} step={0.01} precision={4} style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label="量表描述" name="description">
            <Input.TextArea rows={3} placeholder="请输入量表简介" />
          </Form.Item>
          <Form.Item label="报告模板" name="reportTemplate">
            <Input.TextArea rows={4} placeholder="可选：系统报告模板说明" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
