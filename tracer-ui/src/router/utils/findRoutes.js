/*

  calls find route over an array of route names

*/

import findRoute from './findRoute'

const findRoutes = (routes, names) => names.map(name => findRoute(routes, name))

export default findRoutes