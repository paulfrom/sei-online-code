const routes: any[] = [
  {
    path: '/',
    component: '@/layouts/AuthLayout',
    name: 'dev',
    title: 'menu.dev',
    routes: [
      { path: '/', redirect: '/dashboard' },
      {
        path: '/dashboard',
        name: 'dashboard',
        title: 'menu.dashboard',
        component: './Dashboard',
      },
      { path: '/demo', name: 'demo', title: 'menu.demo', component: './Demo' },
      {
        path: '/asset-entry',
        name: 'assetEntry',
        title: 'menu.assetEntry',
        routes: [
          { path: '/asset-entry', redirect: '/asset-entry/list' },
          { path: '/asset-entry/list', name: 'assetEntryList', title: 'menu.assetEntryList', component: './AssetEntry/list' },
          { path: '/asset-entry/form', name: 'assetEntryForm', title: 'menu.assetEntryForm', component: './AssetEntry/components/form' },
          { path: '/asset-entry/detail', name: 'assetEntryDetail', title: 'menu.assetEntryDetail', component: './AssetEntry/detail' },
          { path: '/asset-entry/approveEdit', name: 'assetEntryApproveEdit', title: 'menu.assetEntryApproveEdit', component: './AssetEntry/approve/approveEdit' },
          { path: '/asset-entry/approveDetail', name: 'assetEntryApproveDetail', title: 'menu.assetEntryApproveDetail', component: './AssetEntry/approve/approveDetail' },
        ],
      },
      {
        path: '/asset-change',
        name: 'assetChange',
        title: 'menu.assetChange',
        routes: [
          { path: '/asset-change', redirect: '/asset-change/list' },
          { path: '/asset-change/list', name: 'assetChangeList', title: 'menu.assetChangeList', component: './AssetChange/list' },
          { path: '/asset-change/detail', name: 'assetChangeDetail', title: 'menu.assetChangeDetail', component: './AssetChange/detail' },
          { path: '/asset-change/approveEdit', name: 'assetChangeApproveEdit', title: 'menu.assetChangeApproveEdit', component: './AssetChange/approve/approveEdit' },
          { path: '/asset-change/approveDetail', name: 'assetChangeApproveDetail', title: 'menu.assetChangeApproveDetail', component: './AssetChange/approve/approveDetail' },
        ],
      },
      {
        path: '/operations',
        name: 'assetOperation',
        title: 'menu.assetOperation',
        routes: [
          { path: '/operations', redirect: '/operations/list' },
          { path: '/operations/list', name: 'assetOperationList', title: 'menu.assetOperationList', component: './AssetOperation/list' },
          { path: '/operations/detail', name: 'assetOperationDetail', title: 'menu.assetOperationDetail', component: './AssetOperation/detail' },
          { path: '/operations/approveEdit', name: 'assetOperationApproveEdit', title: 'menu.assetOperationApproveEdit', component: './AssetOperation/approve/approveEdit' },
          { path: '/operations/approveDetail', name: 'assetOperationApproveDetail', title: 'menu.assetOperationApproveDetail', component: './AssetOperation/approve/approveDetail' },
        ],
      },
    ],
  },
];

if (process.env.NODE_ENV !== 'production') {
  routes.unshift({
    path: '/user',
    name: 'userLogin',
    title: 'menu.userLogin',
    component: '@/layouts/LoginLayout',
    routes: [
      { path: '/user', redirect: '/user/login' },
      {
        path: '/user/login',
        name: 'login',
        component: './Login',
      },
    ],
  });
}

export default routes;
