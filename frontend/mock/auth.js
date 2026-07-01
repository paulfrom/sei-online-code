/**
 * 本地 mock：登录、验证码、开发环境权限功能项
 * 配合 yarn start:mock（MOCK=yes，SERVER_PATH=/mocker.api）使用
 */

/** mock 验证码图片（SVG data URL） */
const MOCK_VERIFY_CODE_IMAGE = `data:image/svg+xml,${encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="36"><rect width="100%" height="100%" fill="#f0f0f0"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" font-size="14" fill="#333">MOCK</text></svg>',
)}`;

/** mock 登录用户（与 Login 页 setCurrentUser 字段对齐） */
const MOCK_LOGIN_USER = {
  loginStatus: 'success',
  sessionId: 'mock-session-id',
  userId: 'mock-user-id',
  userName: 'Mock用户',
  account: 'admin',
  organizationId: 'org_root',
  organizationCode: 'GRP001',
  organizationName: '测试集团',
  authorityPolicy: {},
};

/** mock 权限功能项 */
const MOCK_AUTHORIZED_FEATURES = {
  'sei-online-code-web': {
    CREATE: true,
    EDIT: true,
    DELETE: true,
  },
};

/**
 * mock 公司组织树（与 reporting.js 中 org_a / 测试集团 等数据对齐）
 * 节点字段需包含 name、creditCode、code、nodeLevel，供 ComboTree 与任务预填使用
 */
const MOCK_COMPANY_ORG_TREE = [
  {
    id: 'org_root',
    name: '测试集团',
    code: 'GRP001',
    creditCode: '91510000MA6ROOTXXXX',
    nodeLevel: 1,
    children: [
      {
        id: 'org_a',
        name: '子公司A',
        code: 'A',
        creditCode: '91510000MA6XXXXXX',
        nodeLevel: 2,
      },
      {
        id: 'org_b',
        name: '子公司B',
        code: 'B',
        creditCode: '91510000MA6YYYYYY',
        nodeLevel: 2,
      },
    ],
  },
];

const handlers = {
  /**
   * 获取图形验证码
   */
  'GET /verifyCode/generate': (req, res) => {
    res.json({
      success: true,
      data: MOCK_VERIFY_CODE_IMAGE,
      message: 'mock verify code',
    });
  },

  /**
   * 用户登录（mock 模式下任意账号/密码/验证码均可）
   */
  'POST /auth/login': (req, res) => {
    const body = req.body || {};
    res.json({
      success: true,
      data: {
        ...MOCK_LOGIN_USER,
        account: body.account || MOCK_LOGIN_USER.account,
      },
      message: 'mock login success',
    });
  },

  /**
   * 退出登录
   */
  'POST /auth/logout': (req, res) => {
    res.json({ success: true, data: true, message: 'mock logout' });
  },

  /**
   * 获取当前用户有权限的功能项（非生产环境登录后调用）
   */
  'GET /user/getUserAuthorizedFeatureMaps': (req, res) => {
    res.json({
      success: true,
      data: MOCK_AUTHORIZED_FEATURES,
      message: 'mock authorized features',
    });
  },

  /**
   * 当前用户有权限的公司组织树（填报明细企业名称 ComboTree、任务预填）
   */
  'GET /organizationCust/getUserAuthorizedCompanyTreeEntities': (req, res) => {
    res.json({
      success: true,
      data: MOCK_COMPANY_ORG_TREE,
      message: 'mock company org tree',
    });
  },

  /** 全量公司组织树（填报进度页填报单位筛选） */
  'GET /organizationCust/findCompanyOrgTreeWithoutFrozen': (req, res) => {
    res.json({
      success: true,
      data: MOCK_COMPANY_ORG_TREE,
      message: 'mock company org tree without frozen',
    });
  },
};

const exportMap = {};
const MOCK_PREFIXES = ['/mocker.api/sei-basic', '/api-gateway/sei-basic', '/sei-basic'];

Object.keys(handlers).forEach((key) => {
  const [method, path] = key.split(' ');
  MOCK_PREFIXES.forEach((prefix) => {
    exportMap[`${method} ${prefix}${path}`] = handlers[key];
  });
});

export default exportMap;
