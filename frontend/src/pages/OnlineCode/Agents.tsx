/**
 * Track F15 + F16 — Agents page.
 * F15: ExtTable list (ep #21 findByPage); built-in agent rows show a read-only
 *      badge and no edit/delete action.
 * F16: create/edit dialog — Form with name/description/instructions/model +
 *      skill multi-select. Two-step multica flow: ep #20 save (create/update the
 *      agent) then ep #24 skills (attach/replace its bound skills).
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import {
  ActionButton,
  Button,
  ExtModal,
  ExtTable,
  Form,
  Input,
  Popconfirm,
  Select,
  Tag,
  message,
} from '@ead/suid';
import type { ExtTableProps, ExtTableRef } from '@ead/suid';
import { DeleteOutlined, EditOutlined, LockOutlined, PlusOutlined } from '@ead/suid-icons';
import {
  AGENT_FIND_BY_PAGE_URL,
  BUILTIN_SKILLS,
  attachAgentSkills,
  deleteAgent,
  findSkillsByPage,
  saveAgent,
} from '@/services/onlineCode';
import type { AgentDto, SkillDto } from '@/services/onlineCode';

const useStyles = createStyles(({ css }) => ({
  page: css`
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
  `,
}));

interface AgentForm {
  name: string;
  description: string;
  instructions: string;
  model: string;
  skillIds: string[];
}

const Agents: React.FC = () => {
  const { styles } = useStyles();
  const tableRef = useRef<ExtTableRef>(null);
  const [form] = Form.useForm<AgentForm>();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<AgentDto | null>(null);
  const [skillOptions, setSkillOptions] = useState<Array<{ value: string; label: string }>>([]);

  /** load the skill options for the multi-select (ep #17) — user skills + builtins */
  const loadSkills = useCallback(async () => {
    const res = await findSkillsByPage({ pageInfo: { page: 1, rows: 200 } });
    const userOptions =
      res.success && res.data
        ? res.data.rows.map((s: SkillDto) => ({ value: s.id, label: `${s.name} — ${s.description}` }))
        : [];
    // builtins are not oc_skill rows (multica dim g); merge so agents can bind builtin:<name>
    const builtinOptions = BUILTIN_SKILLS.map((s) => ({ value: s.id, label: `${s.name} — ${s.description}` }));
    setSkillOptions([...userOptions, ...builtinOptions]);
  }, []);

  useEffect(() => {
    loadSkills();
  }, [loadSkills]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    loadSkills();
    setModalOpen(true);
  };

  const openEdit = (record: AgentDto) => {
    setEditing(record);
    loadSkills();
    form.setFieldsValue({
      name: record.name,
      description: record.description,
      instructions: record.instructions,
      model: record.model,
      skillIds: record.skillIds ?? [],
    });
    setModalOpen(true);
  };

  const handleDelete = async (record: AgentDto) => {
    const res = await deleteAgent(record.id);
    if (!res.success) {
      message.error(res.message ?? '删除失败');
      return;
    }
    message.success(res.message ?? '删除成功');
    tableRef.current?.reloadData();
  };

  /** two-step create→attach flow: ep #20 save, then ep #24 skills */
  const handleSave = async (values: AgentForm) => {
    setSaving(true);
    try {
      const saveRes = await saveAgent({
        id: editing?.id,
        name: values.name,
        description: values.description ?? '',
        instructions: values.instructions ?? '',
        model: values.model ?? '',
      });
      if (!saveRes.success || !saveRes.data) {
        message.error(saveRes.message ?? '保存失败');
        return;
      }
      const attachRes = await attachAgentSkills({
        agentId: saveRes.data.id,
        skillIds: values.skillIds ?? [],
      });
      if (!attachRes.success) {
        message.error(attachRes.message ?? '技能绑定失败');
        return;
      }
      message.success('保存成功');
      setModalOpen(false);
      setEditing(null);
      form.resetFields();
      tableRef.current?.reloadData();
    } finally {
      setSaving(false);
    }
  };

  const columns: ExtTableProps<AgentDto>['columns'] = [
    {
      title: '操作',
      dataIndex: 'id',
      width: 120,
      render: (_id: string, record: AgentDto) =>
        record.builtin ? (
          <span>-</span>
        ) : (
          <>
            <ActionButton title="编辑" icon={<EditOutlined />} onClick={() => openEdit(record)} />
            <Popconfirm title="确认删除该 Agent？" onConfirm={() => handleDelete(record)}>
              <ActionButton title="删除" color="danger" icon={<DeleteOutlined />} />
            </Popconfirm>
          </>
        ),
    },
    {
      title: '名称',
      dataIndex: 'name',
      width: 200,
      render: (name: string, record: AgentDto) =>
        record.builtin ? (
          <span>
            {name}{' '}
            <Tag color="blue" icon={<LockOutlined />}>
              内置
            </Tag>
          </span>
        ) : (
          <span>{name}</span>
        ),
    },
    { title: '描述', dataIndex: 'description', expandUnusedSpace: true },
    {
      title: '模型',
      dataIndex: 'model',
      width: 140,
      render: (m: string) => m || <Tag>CLI 默认</Tag>,
    },
    {
      title: '绑定技能',
      dataIndex: 'skillIds',
      width: 100,
      render: (ids: string[]) => <Tag>{(ids ?? []).length}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createdDate', width: 170, dataType: 'datetime' },
  ];

  return (
    <div className={styles.page}>
      <ExtTable
        ref={tableRef}
        rowKey="id"
        columns={columns}
        store={{ url: AGENT_FIND_BY_PAGE_URL, type: 'POST' }}
        remotePaging
        showQuickSearch
        quickSearchFields={[{ field: 'name', title: '名称' }]}
        quickSearchPlaceHolder="请输入 Agent 名称"
        toolbar={{
          left: (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建 Agent
            </Button>
          ),
        }}
      />

      <ExtModal
        open={modalOpen}
        title={editing ? '编辑 Agent' : '新建 Agent'}
        confirmLoading={saving}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form form={form} onFinish={handleSave} layout="vertical">
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: '请输入 Agent 名称' }]}
          >
            <Input placeholder="例如：suid-dev" allowClear />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="Agent 用途简述" allowClear />
          </Form.Item>
          <Form.Item name="instructions" label="指令（prepended to Task.description）">
            <Input.TextArea rows={5} placeholder="你负责实现单个页面…" />
          </Form.Item>
          <Form.Item name="model" label="模型" tooltip="留空则由 CLI 解析默认模型">
            <Input placeholder="留空 = CLI 默认" allowClear />
          </Form.Item>
          <Form.Item name="skillIds" label="绑定技能">
            <Select
              mode="multiple"
              options={skillOptions}
              placeholder="选择要绑定的技能"
              allowClear
              optionFilterProp="label"
            />
          </Form.Item>
        </Form>
      </ExtModal>
    </div>
  );
};

export default Agents;
