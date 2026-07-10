const routes: any[] = [
  {
    path: '/',
    component: '@/layouts/AuthLayout',
    title: '开发菜单',
    name: '开发菜单',
    routes: [
      { path: '/', redirect: '/dashboard' },
      { path: '/dashboard', component: './Dashboard' },
      { path: '/demo', name: 'demo', title: 'demo', component: './Demo' },
    ],
  },
];

if (process.env.NODE_ENV !== 'production') {
  routes.unshift({
    path: '/user',
    name: '用户登录',
    component: '@/layouts/LoginLayout',
    routes: [
      { path: '/user', redirect: '/user/login' },
      { path: '/user/login', component: './Login' },
    ],
  });
}

export default routes;
