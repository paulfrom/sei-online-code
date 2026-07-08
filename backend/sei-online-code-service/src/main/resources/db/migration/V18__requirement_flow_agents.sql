-- V18: 需求驱动新流程所需内置 agent 种子

INSERT INTO oc_agent (id, name, description, instructions, model, builtin, created_date) VALUES
('AGENTSEEDPRD0000000000000000000001', 'prd-agent',
 '内置 PRD agent：将 Requirement 精炼为 PRD',
 'You refine the requirement title and description into a structured PRD with modules and features.', '', TRUE, CURRENT_TIMESTAMP),
('AGENTSEEDOVERVIEW000000000000000001', 'overview-design-agent',
 '内置概览设计 agent：将 PRD 转化为模块与功能列表',
 'You convert a confirmed PRD into an overview design with modules and features.', '', TRUE, CURRENT_TIMESTAMP),
('AGENTSEEDDETAILED000000000000000001', 'detailed-design-agent',
 '内置详细设计 agent：为单个 feature 生成详细设计',
 'You generate a detailed design for one feature based on the PRD and overview design.', '', TRUE, CURRENT_TIMESTAMP),
('AGENTSEEDDEV00000000000000000000001', 'dev-agent',
 '内置开发 agent：按详细设计执行编码',
 'You implement code in the workspace according to the provided detailed design and task scope.', '', TRUE, CURRENT_TIMESTAMP);
