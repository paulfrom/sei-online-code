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
        path: '/online-code',
        name: 'onlineCode',
        title: 'menu.onlineCode',
        routes: [
          { path: '/online-code', redirect: '/online-code/list' },
          {
            path: '/online-code/list',
            name: 'onlineCodeList',
            title: 'menu.onlineCodeList',
            component: './OnlineCode/List',
          },
          {
            path: '/online-code/spec',
            name: 'onlineCodeSpec',
            title: 'menu.onlineCodeSpec',
            component: './OnlineCode/Spec',
          },
          {
            path: '/online-code/dispatch',
            name: 'onlineCodeDispatch',
            title: 'menu.onlineCodeDispatch',
            component: './OnlineCode/Dispatch',
          },
          {
            path: '/online-code/preview',
            name: 'onlineCodePreview',
            title: 'menu.onlineCodePreview',
            component: './OnlineCode/Preview',
          },
          {
            path: '/online-code/timeline',
            name: 'onlineCodeTimeline',
            title: 'menu.onlineCodeTimeline',
            component: './OnlineCode/Timeline',
          },
          {
            path: '/online-code/skills',
            name: 'onlineCodeSkills',
            title: 'menu.onlineCodeSkills',
            component: './OnlineCode/Skills',
          },
          {
            path: '/online-code/agents',
            name: 'onlineCodeAgents',
            title: 'menu.onlineCodeAgents',
            component: './OnlineCode/Agents',
          },
          {
            path: '/online-code/settings',
            name: 'onlineCodeSettings',
            title: 'menu.onlineCodeSettings',
            component: './OnlineCode/Settings',
          },
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
