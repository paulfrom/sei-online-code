const DETAIL_TYPES = [
  { code: 'QYJBXXBCB', tableName: 'reporting_qyjbxxbcb', label: '企业基本信息补充表' },
  {
    code: 'SSJGQYCGJRQYJBQK',
    tableName: 'reporting_ssjgqycgjrqyjbqk',
    label: '省属监管企业参股金融企业基本情况表',
  },
  { code: 'SSJGQYCGQKB', tableName: 'reporting_ssjgqycgqkb', label: '省属监管企业参股情况表' },
  { code: 'SSJGQYKGBKQQK', tableName: 'reporting_ssjgqykgbkqqk', label: '省属监管企业控股不控权情况' },
  {
    code: 'SSJGQYKGJRZQYJBQKB',
    tableName: 'reporting_ssjgqykgjrzqyjbqkb',
    label: '省属监管企业控股金融子企业基本情况表',
  },
  {
    code: 'SSJGQYQTJRTZHDKZQKB',
    tableName: 'reporting_ssjgqyqtjrtzhdkzqkb',
    label: '省属监管企业其他金融投资活动开展情况表',
  },
];

let planUid = 1;
let detailUid = 1;

const MOCK_FILL_USER_ID = 'mock-user-id';
const MOCK_FILL_USER_NAME = 'Mock用户';
const MOCK_FILL_USER_CODE = 'admin';

const mockPlans = [
  {
    id: 'rp_plan_1',
    name: '2025年第一季度资产监管填报计划',
    orgId: 'org1',
    orgCode: 'ORG1',
    orgName: '资产监管部',
    reportingCycleType: 'QUARTER',
    startDate: '2025-01-01',
    endDate: '2025-03-31',
    reportingDeadlineDate: '2025-04-15',
    remindTime: 'ONE_DAY_BEFORE',
    deliverOrgId: 'org_root',
    deliverOrgCode: 'GRP001',
    deliverOrgName: '测试集团',
    deliverOwnerId: MOCK_FILL_USER_ID,
    deliverOwnerAccount: MOCK_FILL_USER_CODE,
    deliverOwnerName: MOCK_FILL_USER_NAME,
    dataReportingPeriod: 'CURRENT',
    description: 'mock 计划',
    planStatus: 'DELIVERED',
    taskCount: 1,
    filledCount: 0,
    targetList: [
      {
        id: 'tgt1',
        orgId: 'org_a',
        orgCode: 'A',
        orgName: '子公司A',
        orgNodeLevel: 2,
        fillUserId: MOCK_FILL_USER_ID,
        fillUserCode: MOCK_FILL_USER_CODE,
        fillUserName: MOCK_FILL_USER_NAME,
      },
    ],
  },
  {
    id: 'rp_plan_2',
    name: '草稿计划示例',
    orgId: 'org1',
    orgCode: 'ORG1',
    orgName: '资产监管部',
    reportingCycleType: 'MONTH',
    startDate: '2025-05-01',
    endDate: '2025-05-31',
    reportingDeadlineDate: '2025-06-10',
    remindTime: 'THREE_DAYS_BEFORE',
    deliverOrgId: 'org_root',
    deliverOrgCode: 'GRP001',
    deliverOrgName: '测试集团',
    deliverOwnerId: MOCK_FILL_USER_ID,
    deliverOwnerAccount: MOCK_FILL_USER_CODE,
    deliverOwnerName: MOCK_FILL_USER_NAME,
    dataReportingPeriod: 'CURRENT',
    planStatus: 'DRAFT',
    taskCount: 0,
    filledCount: 0,
    targetList: [],
  },
];

const mockTasks = [
  {
    id: 'rp_task_1',
    code: 'TASK-001',
    name: '2025年第一季度资产监管数据报送',
    planId: 'rp_plan_1',
    orgId: 'org_a',
    orgCode: '91510000MA6XXXXXX',
    orgName: '子公司A',
    fillUserId: MOCK_FILL_USER_ID,
    fillUserCode: MOCK_FILL_USER_CODE,
    fillUserName: MOCK_FILL_USER_NAME,
    taskStatus: 'NOT_FILL',
    flowStatus: 'INIT',
    reportingCycleType: 'QUARTER',
    reportingDeadlineDate: '2025-04-15',
    deliverOrgId: 'org1',
    deliverOrgCode: 'ORG1',
    deliverOrgName: '测试集团',
    deliverOwnerId: MOCK_FILL_USER_ID,
    deliverOwnerAccount: MOCK_FILL_USER_CODE,
    deliverOwnerName: MOCK_FILL_USER_NAME,
    dataReportingPeriod: 'CURRENT',
    detailTabProgress: {},
  },
];

const mockDetailStores = DETAIL_TYPES.reduce((acc, item) => {
  acc[item.code] = {};
  return acc;
}, {});

mockDetailStores.QYJBXXBCB = {
  qy_1: {
    id: 'qy_1',
    planId: 'rp_plan_1',
    taskId: 'rp_task_1',
    enterpriseName: '子公司A',
    enterpriseCode: '91510000MA6XXXXXX',
    groupName: '测试集团',
    groupCode: 'GRP001',
  },
};

/**
 * 分页结果包装
 */
function pageResult(rows, req) {
  const body = req.body || {};
  const page = body.pageInfo?.page || 1;
  const rowsPerPage = body.pageInfo?.rows || 20;
  const start = (page - 1) * rowsPerPage;
  const pageRows = rows.slice(start, start + rowsPerPage);
  return {
    success: true,
    data: {
      rows: pageRows,
      records: rows.length,
      page,
      total: Math.ceil(rows.length / rowsPerPage) || 1,
    },
  };
}

/**
 * 为指定明细类型注册 CRUD mock 接口
 */
function registerDetailMockHandlers(handlers, detailType, store) {
  const prefix = detailType.toLowerCase();
  handlers[`POST /reportingDetail/findByPage/${detailType}`] = (req, res) => {
    const { planId, taskId } = req.body;
    const rows = Object.values(store).filter((r) => {
      if (r.planId !== planId) return false;
      if (taskId) return r.taskId === taskId;
      return true;
    });
    res.json(pageResult(rows, req));
  };
  handlers[`GET /reportingDetail/findByPlanIdAndTaskId/${detailType}`] = (req, res) => {
    const { planId, taskId } = req.query;
    const rows = Object.values(store).filter(
      (r) => r.planId === planId && (!taskId || r.taskId === taskId),
    );
    res.json({ success: true, data: rows });
  };
  handlers[`POST /reportingDetail/save/${detailType}`] = (req, res) => {
    const id = req.body.id || `${prefix}_${++detailUid}`;
    const row = { ...req.body, id };
    store[id] = row;
    res.json({ success: true, data: row });
  };
  handlers[`DELETE /reportingDetail/delete/${detailType}/:id`] = (req, res) => {
    delete store[req.params.id];
    res.json({ success: true });
  };
  handlers[`POST /reportingDetail/batchImport/${detailType}`] = (req, res) => {
    const { planId, taskId } = req.query;
    const items = req.body || [];
    items.forEach((item) => {
      const id = item.id || `${prefix}_${++detailUid}`;
      store[id] = { ...item, id, planId, taskId };
    });
    res.json({ success: true, data: items.length });
  };
  handlers[`POST /reportingDetail/batchOverwriteImport/${detailType}`] = (req, res) => {
    const { planId, taskId } = req.query;
    Object.keys(store).forEach((key) => {
      if (store[key].planId === planId && store[key].taskId === taskId) {
        delete store[key];
      }
    });
    const items = req.body || [];
    items.forEach((item) => {
      const id = item.id || `${prefix}_${++detailUid}`;
      store[id] = { ...item, id, planId, taskId };
    });
    res.json({ success: true, data: items.length });
  };
}

const handlers = {
  'POST /reportingPlan/findByPage': (req, res) => {
    const filters = req.body?.filters || [];
    let rows = [...mockPlans];
    filters.forEach((f) => {
      if (f.fieldName === 'orgId' && f.value) {
        rows = rows.filter((r) => r.orgId === f.value);
      }
    });
    res.json(pageResult(rows, req));
  },
  'GET /reportingPlan/findPlanDetail': (req, res) => {
    const plan = mockPlans.find((p) => p.id === req.query.planId);
    res.json(plan ? { success: true, data: plan } : { success: false, message: 'not found' });
  },
  'POST /reportingPlan/savePlan': (req, res) => {
    const dto = req.body;
    if (dto.id) {
      const idx = mockPlans.findIndex((p) => p.id === dto.id);
      if (idx >= 0) mockPlans[idx] = { ...mockPlans[idx], ...dto };
    } else {
      dto.id = `rp_plan_${++planUid}`;
      mockPlans.push(dto);
    }
    if (req.query.enable === 'true') {
      dto.planStatus = 'DELIVERED';
    } else if (!dto.planStatus) {
      dto.planStatus = 'DRAFT';
    }
    res.json({ success: true, data: dto });
  },
  'GET /reportingPlan/deletePlan': (req, res) => {
    const idx = mockPlans.findIndex((p) => p.id === req.query.planId);
    if (idx >= 0) mockPlans.splice(idx, 1);
    res.json({ success: true });
  },
  'POST /reportingPlan/deliverPlan': (req, res) => {
    res.json({ success: true });
  },
  'POST /reportingPlan/urgePlan': (req, res) => {
    res.json({ success: true, data: '催办成功' });
  },
  'GET /reportingPlan/withdrawPlan': (req, res) => {
    res.json({ success: true });
  },
  'POST /reportingPlanTask/findByPage': (req, res) => {
    const filters = req.body?.filters || [];
    let rows = [...mockTasks];
    filters.forEach((f) => {
      if (f.fieldName === 'planId') rows = rows.filter((r) => r.planId === f.value);
      if (f.fieldName === 'fillUserId') rows = rows.filter((r) => r.fillUserId === f.value);
      if (f.fieldName === 'flowStatus') rows = rows.filter((r) => r.flowStatus === f.value);
      if (f.fieldName === 'orgId') rows = rows.filter((r) => r.orgId === f.value);
      if (f.fieldName === 'taskStatus') rows = rows.filter((r) => r.taskStatus === f.value);
    });
    res.json(pageResult(rows, req));
  },
  'GET /reportingPlanTask/findOne': (req, res) => {
    const task = mockTasks.find((t) => t.id === req.query.id);
    res.json(task ? { success: true, data: task } : { success: false });
  },
  'POST /reportingPlanTask/sendUrgedInfoToBpm': (req, res) => {
    res.json({ success: true, data: 'ok' });
  },
  'GET /reportingDetail/listTypes': (req, res) => {
    res.json({ success: true, data: DETAIL_TYPES });
  },
};

DETAIL_TYPES.forEach((item) => {
  registerDetailMockHandlers(handlers, item.code, mockDetailStores[item.code]);
});

const exportMap = {};
const MOCK_PREFIXES = ['/mocker.api/sei-online-code', '/api-gateway/sei-online-code'];

Object.keys(handlers).forEach((key) => {
  const [method, path] = key.split(' ');
  MOCK_PREFIXES.forEach((prefix) => {
    exportMap[`${method} ${prefix}${path}`] = handlers[key];
  });
});

export default exportMap;
