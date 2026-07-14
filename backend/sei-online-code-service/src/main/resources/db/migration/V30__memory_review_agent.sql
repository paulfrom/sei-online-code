-- 记忆审阅使用独立 agent，避免 PRD 生成指令与结构化审阅输出互相冲突。
INSERT INTO oc_agent (id, name, description, instructions, model, builtin, created_date)
SELECT 'AGENTSEEDMEMORYREVIEW000000000001', 'memory-review-agent',
       '内置记忆审阅 agent：异步对照项目记忆提出设计提醒和后续沉淀建议',
       'You review design documents against project memory to surface non-blocking differences and memory accumulation suggestions. Differences are advisory and must never gate the workflow. Return only the requested structured JSON.',
       '', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM oc_agent WHERE name = 'memory-review-agent');
