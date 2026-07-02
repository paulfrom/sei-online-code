export default {
  '/mocker.api': {
    target: 'http://rddgit.changhong.com:7300/mock/5dd5efbdc239b926aeb04627/seid.api',
    changeOrigin: true,
    secure: false,
    pathRewrite: { '^/mocker.api': '' },
  },
  '/api-gateway/sei-online-code': {
    target: 'http://127.0.0.1:8091',
    changeOrigin: true,
    secure: false,
    headers: {
      Authorization:
        // 'eyJhbGciOiJIUzUxMiJ9.eyJyYW5kb21LZXkiOiI4QzQ3NURBMy0zMjdBLTExRUUtODBDQy0wMjQyQzBBODQ2MDMiLCJzdWIiOiIxMTAyMjgxMSIsImxvZ2luQWNjb3VudCI6ImVib2MiLCJhdXRob3JpdHlQb2xpY3kiOiJOb3JtYWxVc2VyIiwiaXAiOiJVbmtub3duIiwidXNlclR5cGUiOiJFbXBsb3llZSIsInVzZXJOYW1lIjoi546L6aOeIiwibG9jYWxlIjoiemhfQ04iLCJ1c2VySWQiOiIxOThGQkE2My00RDhBLTExRUYtQjU1Ny0wMjQyQzBBODQzMTEiLCJpYXQiOjE3MjQyMzEwOTAsInRlbmFudCI6IlJDU0lUIn0.oqxEoEZHylKrErUskp1dKymCtUqrSe_qSU5eDb1LKaOhwAH5qVhkA091TY8A7cR6fxNTF2vcWEK-xbbRcOL7Hw',
        // 'eyJhbGciOiJIUzUxMiJ9.eyJyYW5kb21LZXkiOiIwMTA3MkZBNS02RjExLTExRUYtODUwMC0wMjQyQzBBODQ2MTkiLCJzdWIiOiJhZG1pbiIsImxvZ2luQWNjb3VudCI6ImFkbWluIiwiYXV0aG9yaXR5UG9saWN5IjoiVGVuYW50QWRtaW4iLCJpcCI6IjEwLjguMTIuMiIsInVzZXJUeXBlIjoiRW1wbG95ZWUiLCJ1c2VyTmFtZSI6Iuezu-e7n-euoeeQhuWRmCIsImxvY2FsZSI6InpoX0NOIiwidXNlcklkIjoiQjU0RTg5NjQtRDE0RC0xMUU4LUE2NEItMDI0MkMwQTg0NDFCIiwiaWF0IjoxNzI1OTMwNDI5LCJ0ZW5hbnQiOiIxMDA0NCJ9.misvli-DEhNXhdTpw2YqmMiGdKD4ZmOtdy79lb4w7Ef0jzBAtJc3gyu_wGWI2RPP2Iq9vntlIExMvkLoVYCHfA',
      'eyJhbGciOiJIUzUxMiJ9.eyJyYW5kb21LZXkiOiIwMTA3MkZBNS02RjExLTExRUYtODUwMC0wMjQyQzBBODQ2MTkiLCJzdWIiOiJhZG1pbiIsImxvZ2luQWNjb3VudCI6ImFkbWluIiwiYXV0aG9yaXR5UG9saWN5IjoiVGVuYW50QWRtaW4iLCJpcCI6IjEwLjguMTIuMiIsInVzZXJUeXBlIjoiRW1wbG95ZWUiLCJ1c2VyTmFtZSI6Iuezu-e7n-euoeeQhuWRmCIsImxvY2FsZSI6InpoX0NOIiwidXNlcklkIjoiQjU0RTg5NjQtRDE0RC0xMUU4LUE2NEItMDI0MkMwQTg0NDFCIiwiaWF0IjoxNzMzODk1MTU3LCJ0ZW5hbnQiOiJSQ1NJVCIsIm9yZ0lkIjpudWxsfQ.Af1tAaH_yAfPkjKQVZKtFBIJsFxAdu0z_XOmrm2hS2jo15zNtbzB5HQ8UHqbWawrBL6bYUMiqwJLR17NL1Bcsw'
      // 'eyJhbGciOiJIUzUxMiJ9.eyJyYW5kb21LZXkiOiJGOEJGRDNGOS1GNjM3LTExRUYtQUYxMi0wMjQyQzBBODQ0MDciLCJzdWIiOiIyMDI4MjEzNCIsImxvZ2luQWNjb3VudCI6IjIwMjgyMTM0IiwiYXV0aG9yaXR5UG9saWN5IjoiTm9ybWFsVXNlciIsImlwIjoiMTAuOTkuMC4yNTIiLCJ1c2VyVHlwZSI6IkVtcGxveWVlIiwidXNlck5hbWUiOiLliJjmnb7mnpciLCJsb2NhbGUiOiJ6aF9DTiIsInVzZXJJZCI6IkM4NjI1N0Q3LTVCQkMtMTFFQy05QUZBLTAyNDJDMEE4NDMwRCIsImlhdCI6MTc0MDc5MDU3MiwidGVuYW50IjoiUkNTSVQiLCJvcmdJZCI6bnVsbH0.WXcSN4KTcEUVKT-QuIVmntQlK_lfzCxU2T3Zy9Whntqmvi3a0158WkvJwmZCTcA7KsQupfOSrzBzkJ8CP6MXwg'
    },
    pathRewrite: { '^/api-gateway/sei-online-code': '' },
  },
  '/api-gateway': {
    target: 'https://ead.changhong.com/api-gateway',
    changeOrigin: true,
    secure: false,
    pathRewrite: { '^/api-gateway': '' },
  },
};
