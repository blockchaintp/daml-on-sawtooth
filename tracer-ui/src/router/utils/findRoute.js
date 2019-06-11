/*

  given a route structure with children e.g.

  [
    {
      name: 'home',
      path: '/'
    },
    {
      name: 'login',
      path: '/login',
      apples: 10,
      children: [
        {
          name: 'test',
          path: '/test',
          oranges: 11,
        },
      ],
    },
  ]

  this will find a route based on the given name e.g.

  login.test will return:

  {
    name: 'test',
    path: '/test',
    oranges: 11,
  }

*/

const findRoute = (routes, name) => {
  if(!name) return null
  return name.split('.').reduce((currentRoute, part) => {
    if(!currentRoute || !currentRoute.children) return null
    return currentRoute.children.find(route => route.name == part)
  }, {
    children: routes,
  })
}

export default findRoute