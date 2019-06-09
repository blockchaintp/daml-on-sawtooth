import transitionPath from 'router5-transition-path'
import findRoutes from '../utils/findRoutes'

import settings from 'settings'

// log each route transition in development
const logRoute = (routes) => (router, dependencies) => (toState, fromState, done) => {
  if(!settings.devMode) return done()
  const { toActivate } = transitionPath(toState, fromState)
  const activeRoutes = findRoutes(routes, toActivate)
  console.log(`route:`, activeRoutes)
  done()
}

export default logRoute