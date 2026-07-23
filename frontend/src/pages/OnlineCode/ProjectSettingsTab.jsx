/**
 * Project settings tab.
 */
import React, { useEffect } from 'react';
import {
  Button,
  Checkbox,
  Form,
  Input,
  message,
} from '@ead/suid';
import { saveProject } from '@/services/onlineCode';

const ProjectSettingsTab = ({ project }) => {
  const [form] = Form.useForm();

  useEffect(() => {
    if (project) {
      form.setFieldsValue({
        id: project.id,
        name: project.name,
        design: project.design,
        gitUrl: project.gitUrl,
        projectCode: project.projectCode,
        projectVersion: project.projectVersion,
        packageName: project.packageName,
        workspacePath: project.workspacePath,
        workspaceBaseBranch: project.workspaceBaseBranch || 'main',
        deliveryTargetBranch: project.deliveryTargetBranch,
        autoRunCodingTask: project.autoRunCodingTask,
      });
    }
  }, [project, form]);

  const handleSave = async (values) => {
    const res = await saveProject(values);
    if (res.success) {
      message.success('保存成功');
    } else {
      message.error(res.message ?? '保存失败');
    }
  };

  return (
    <div style={{ padding: 16, maxWidth: 640 }}>
      <Form form={form} onFinish={handleSave} layout="vertical">
        <Form.Item name="id" hidden>
          <Input />
        </Form.Item>
        <Form.Item name="name" label="项目名称" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name="design" label="项目描述" rules={[{ required: true }]}>
          <Input.TextArea rows={6} />
        </Form.Item>
        <Form.Item name="gitUrl" label="Git 地址">
          <Input placeholder="https://gitlab.example.com/group/project.git" />
        </Form.Item>
        <Form.Item name="projectCode" label="项目编码">
          <Input placeholder="留空则按 Git 地址/项目名称推导" />
        </Form.Item>
        <Form.Item name="projectVersion" label="项目版本">
          <Input placeholder="留空则默认 1.0.0-SNAPSHOT" />
        </Form.Item>
        <Form.Item name="packageName" label="后端包名">
          <Input placeholder="mono 模板 backend/ 目录使用；留空则自动推导" />
        </Form.Item>
        <Form.Item name="workspacePath" label="工作区路径">
          <Input placeholder="留空则自动生成" />
        </Form.Item>
        <Form.Item
          name="workspaceBaseBranch"
          label="工作区基线分支"
          tooltip="记录需求工作区更新时应对照的基线分支；刷新状态不会自动覆盖本地成果"
        >
          <Input placeholder="main" />
        </Form.Item>
        <Form.Item
          name="deliveryTargetBranch"
          label="交付目标分支"
          tooltip="手动或自动创建 MR 时使用；留空则使用平台 GitLab 目标分支"
        >
          <Input placeholder="main" />
        </Form.Item>
        <Form.Item name="autoRunCodingTask" valuePropName="checked">
          <Checkbox>确认详细设计后自动执行编码任务</Checkbox>
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit">保存</Button>
        </Form.Item>
      </Form>
    </div>
  );
};

export default ProjectSettingsTab;
