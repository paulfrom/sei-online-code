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

test('resume automation is gated to the current developing plan and calls the requirement endpoint', () => {
  const service = read('src/services/requirement.js');
  const hook = read('src/pages/OnlineCode/components/RequirementWorkspace/useRequirementWorkspace.js');
  const container = read('src/pages/OnlineCode/components/RequirementWorkspace/index.tsx');
  const overview = read('src/pages/OnlineCode/components/RequirementWorkspace/OverviewPanel.jsx');
  assert.match(service, /requirement\/\$\{id\}\/resume/);
  assert.match(hook, /resumeRequirementAutomation\(requirementId\)/);
  assert.match(container, /requirement\.status === 'PRD_CONFIRMED'/);
  assert.match(container, /requirement\.automationStatus === 'DEVELOPING'/);
  assert.match(container, /\['READY', 'DEVELOPING'\]\.includes\(executionPlan\?\.status\)/);
  assert.match(overview, /恢复执行计划/);
  assert.match(overview, /loading=\{resuming\}/);
  assert.match(overview, /onClick=\{onResume\}/);
});

test('frontend contracts no longer expose the retired detailed-design flow or manual task run', () => {
  const shared = read('src/services/onlineCodeTypes.ts');
  const local = read('src/pages/OnlineCode/components/RequirementWorkspace/types.ts');
  const hook = read('src/pages/OnlineCode/components/RequirementWorkspace/useRequirementWorkspace.js');
  assert.doesNotMatch(shared, /Detailed Design|detailedDesignId|detailedDesignVersion/);
  assert.doesNotMatch(local, /onRun:\s*\(t: CodingTaskDto\)/);
  assert.doesNotMatch(hook, /async\s+runTask\s*\(/);
});

test('active-loop comments describe incremental revision instead of interrupting all automation', () => {
  const composer = read('src/pages/OnlineCode/components/RequirementWorkspace/CommentComposer.jsx');
  assert.match(composer, /评论将在当前 Loop 内增量调整计划/);
  assert.match(composer, /completed: false,[\s\S]*dangerous: false/);
  assert.doesNotMatch(composer, /发送评论将中断当前自动化并触发 PM 重规划/);
  assert.match(composer, /COMPLETED:[\s\S]*创建变更请求 loop/);
});

test('revision progress is displayed from authoritative fields and failed revision can be retried once', () => {
  const sharedTypes = read('src/services/onlineCodeTypes.ts');
  const localTypes = read('src/pages/OnlineCode/components/RequirementWorkspace/types.ts');
  const service = read('src/services/requirement.js');
  const hook = read('src/pages/OnlineCode/components/RequirementWorkspace/useRequirementWorkspace.js');
  const overview = read('src/pages/OnlineCode/components/RequirementWorkspace/OverviewPanel.jsx');
  const container = read('src/pages/OnlineCode/components/RequirementWorkspace/index.tsx');

  for (const source of [sharedTypes, localTypes]) {
    assert.match(source, /revisionSeq/);
    assert.match(source, /appliedRevisionSeq/);
    assert.match(source, /revisionState/);
    assert.match(source, /revisionFailureReason/);
  }
  assert.match(service, /requirement\/\$\{id\}\/revision\/retry/);
  assert.match(hook, /revisionRetryInFlightRef\.current/);
  assert.match(hook, /retryRequirementRevision\(requirementId\)/);
  assert.match(overview, /SNAPSHOTTING:[\s\S]*正在保存执行现场/);
  assert.match(overview, /PLANNING:[\s\S]*PM 正在调整计划/);
  assert.match(overview, /APPLYING:[\s\S]*正在应用计划调整/);
  assert.match(overview, /revisionState === 'FAILED'[\s\S]*重试修订/);
  assert.match(overview, /已应用，当前 Loop 继续执行/);
  assert.match(container, /onRetryRevision=\{actions\.retryRevision\}/);
});

test('websocket revision events merge forward only and still trigger authoritative refresh', () => {
  const socket = read('src/utils/requirement-progress-socket.ts');
  const hook = read('src/pages/OnlineCode/components/RequirementWorkspace/useRequirementWorkspace.js');
  assert.match(socket, /loopId\?: string \| null/);
  assert.match(socket, /revisionSeq\?: number \| null/);
  assert.match(socket, /revisionState\?:/);
  assert.match(socket, /revisionFailureReason\?: string \| null/);
  assert.match(hook, /incomingSeq < currentSeq/);
  assert.match(hook, /snapshot\.activeLoopId !== event\.loopId/);
  assert.match(hook, /event\.revisionState !== 'FAILED'[\s\S]*revisionFailureReason = null/);
  assert.match(hook, /mergeRevisionProgressEvent\(current, evt\)/);
  assert.match(hook, /maybeRefreshOnVersion\(evt && evt\.snapshotVersion\)/);
  assert.match(hook, /shouldApplyRevisionProgressEvent\(revisionEventCursorRef\.current, evt\)/);
  assert.match(hook, /currentState === 'NONE' \|\| currentState === 'FAILED'/);
  assert.match(hook, /currentState === 'FAILED' && incomingState === 'PENDING'/);
  assert.match(hook, /incomingOrder < currentOrder/);
  assert.match(hook, /incomingTime < currentTime/);
});

test('accepted delivery can be submitted manually after refreshing workspace facts', () => {
  const service = read('src/services/requirement.js');
  const hook = read('src/pages/OnlineCode/components/RequirementWorkspace/useRequirementWorkspace.js');
  const container = read('src/pages/OnlineCode/components/RequirementWorkspace/index.tsx');
  const delivery = read('src/pages/OnlineCode/components/RequirementWorkspace/DeliveryTab.jsx');

  assert.match(service, /requirement\/\$\{id\}\/workspace\/refresh/);
  assert.match(service, /requirement\/\$\{id\}\/workspace\/sync/);
  assert.match(service, /requirement\/\$\{id\}\/mr\/submit/);
  assert.match(hook, /refreshRequirementWorkspace\(requirementId\)/);
  assert.match(hook, /syncRequirementWorkspace\(requirementId\)/);
  assert.match(hook, /submitMr\(requirementId\)/);
  assert.match(container, /Boolean\(workspaceStatus\?\.dirty\)/);
  assert.match(container, /requirement\.automationStatus !== 'DELIVERING'/);
  assert.match(container, /requirement\.status !== 'COMPLETED'/);
  assert.match(hook, /safeSet\(setWorkspaceStatus, res\.data\);[\s\S]*await refresh\(\)/);
  assert.match(delivery, /刷新工作区/);
  assert.match(delivery, /手动提交交付物/);
  assert.match(delivery, /工作区当前分支上的修改/);
  assert.match(delivery, /不会切换分支/);
  assert.match(delivery, /同步主分支/);
  assert.match(delivery, /发生合并冲突时不会启动新的 Loop/);
  assert.match(delivery, /workspaceStatus\.changedFiles\?\.length/);
  assert.match(delivery, /当前工作区没有未提交修改/);
});

test('project settings persist workspace base and delivery target branches', () => {
  const serviceTypes = read('src/services/onlineCode.ts');
  const settings = read('src/pages/OnlineCode/ProjectSettingsTab.jsx');

  for (const source of [serviceTypes, settings]) {
    assert.match(source, /workspaceBaseBranch/);
    assert.match(source, /deliveryTargetBranch/);
  }
});

test('requirement completion is explicit, merge-gated, and reversible before the next loop', () => {
  const service = read('src/services/requirement.js');
  const hook = read('src/pages/OnlineCode/components/RequirementWorkspace/useRequirementWorkspace.js');
  const container = read('src/pages/OnlineCode/components/RequirementWorkspace/index.tsx');
  const delivery = read('src/pages/OnlineCode/components/RequirementWorkspace/DeliveryTab.jsx');
  const composer = read('src/pages/OnlineCode/components/RequirementWorkspace/CommentComposer.jsx');
  const types = read('src/services/onlineCodeTypes.ts');

  assert.match(service, /requirement\/\$\{id\}\/confirmCompletion/);
  assert.match(service, /requirement\/\$\{id\}\/reopen/);
  assert.match(hook, /confirmRequirementCompletion\(requirementId\)/);
  assert.match(hook, /reopenRequirementRequest\(requirementId\)/);
  assert.match(container, /onConfirmCompletion=\{actions\.confirmCompletion\}/);
  assert.match(container, /onReopenRequirement=\{actions\.reopenRequirement\}/);
  assert.match(delivery, /完成需求/);
  assert.match(delivery, /重新打开需求/);
  assert.match(delivery, /校验 MR 已合并、没有运行中任务或计划修订/);
  assert.match(composer, /requirement\.status === 'COMPLETED'/);
  assert.match(composer, /请先在交付页重新打开需求/);
  assert.match(types, /WAITING_FEEDBACK/);
  assert.match(types, /COMPLETED/);
});
