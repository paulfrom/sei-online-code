const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const root = path.resolve(__dirname, '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');

test('TaskTab exposes only failed-task rerun and delegates it once', () => {
  const source = read('src/pages/OnlineCode/components/RequirementWorkspace/TaskTab.jsx');
  assert.doesNotMatch(source, /import\s*\{[^}]*runCodingTask[^}]*\}\s*from\s*['"]@\/services\/codingTask['"]/s);
  assert.doesNotMatch(source, /await\s+runCodingTask\(/);
  assert.doesNotMatch(source, /await\s+rerunCodingTask\(/);
  assert.match(source, /await\s+onRerun\(task, prompt\)/);
  assert.doesNotMatch(source, /record\.status === 'PENDING'[\s\S]*运行/);
  assert.match(source, /s === 'FAILED'\s*\|\|\s*s === 'VALIDATION_FAILED'/);
});

test('plan parsing preserves structured validation commands', () => {
  const source = read('src/pages/OnlineCode/components/RequirementWorkspace/parsePlanJson.js');
  assert.match(source, /JSON\.stringify\(parsed\.validation/);
});

test('task view receives result comments and filters run history by task', () => {
  const tabs = read('src/pages/OnlineCode/components/RequirementWorkspace/RightTabs.jsx');
  const taskTab = read('src/pages/OnlineCode/components/RequirementWorkspace/TaskTab.jsx');
  assert.match(tabs, /<TaskTab[\s\S]*comments=\{comments\}/);
  assert.match(tabs, /<RunTab[\s\S]*taskFilterId=/);
  assert.match(taskTab, /DEV_RESULT/);
  assert.match(taskTab, /VALIDATION_RESULT/);
});

test('workspace loads all runs by requirement instead of only task-bound runs', () => {
  const hook = read('src/pages/OnlineCode/components/RequirementWorkspace/useRequirementWorkspace.js');
  const service = read('src/services/run.js');
  assert.match(hook, /findRunsByRequirement\(requirementId\)/);
  assert.doesNotMatch(hook, /tasks\.map\(async \(task\).*findRunsByCodingTask/s);
  assert.match(service, /export async function findRunsByRequirement/);
});

test('comment failures reject and the composer receives a real sending state', () => {
  const hook = read('src/pages/OnlineCode/components/RequirementWorkspace/useRequirementWorkspace.js');
  const container = read('src/pages/OnlineCode/components/RequirementWorkspace/index.tsx');
  assert.match(hook, /throw new Error\(/);
  assert.match(hook, /发送失败/);
  assert.doesNotMatch(container, /sending=\{false\}/);
  assert.match(container, /sending=\{sendingComment\}/);
});

test('RunTab uses the public FilterView data and controlled-selection props', () => {
  const source = read('src/pages/OnlineCode/components/RequirementWorkspace/RunTab.jsx');
  assert.match(source, /dataSource=\{RUN_TYPE_OPTIONS\}/);
  assert.match(source, /selectedKeys=\{/);
  assert.match(source, /selectedItems\.map\(\(item\) => item\.key\)/);
  assert.doesNotMatch(source, /<FilterView[\s\S]*?\sdata=\{/);
  assert.doesNotMatch(source, /<FilterView[\s\S]*?\svalue=\{/);
});

test('stop automation uses the requirement-level cancellation endpoint', () => {
  const hook = read('src/pages/OnlineCode/components/RequirementWorkspace/useRequirementWorkspace.js');
  const service = read('src/services/requirement.js');
  assert.match(service, /requirement\/\$\{id\}\/stop/);
  assert.match(hook, /stopRequirementAutomation\(requirementId\)/);
  assert.doesNotMatch(hook, /running\.map\(\(t\) => cancelCodingTask/);
});

test('frontend contracts no longer expose the retired detailed-design flow or manual task run', () => {
  const shared = read('src/services/onlineCodeTypes.ts');
  const local = read('src/pages/OnlineCode/components/RequirementWorkspace/types.ts');
  const hook = read('src/pages/OnlineCode/components/RequirementWorkspace/useRequirementWorkspace.js');
  assert.doesNotMatch(shared, /Detailed Design|detailedDesignId|detailedDesignVersion/);
  assert.doesNotMatch(local, /onRun:\s*\(t: CodingTaskDto\)/);
  assert.doesNotMatch(hook, /async\s+runTask\s*\(/);
});
