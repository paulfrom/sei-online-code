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
        workspacePath: project.workspacePath,
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
        <Form.Item name="workspacePath" label="工作区路径">
          <Input placeholder="留空则自动生成" />
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
