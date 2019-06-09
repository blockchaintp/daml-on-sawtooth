import createRouter from 'router5'
import browserPlugin from 'router5-plugin-browser'

import routes from './routes'

import logMiddleware from './middleware/log'
import redirectMiddleware from './middleware/redirect'
import triggerMiddleware from './middleware/trigger'

const Router = () => {
  const router = createRouter(routes, {
    defaultRoute: 'notfound',
    queryParamsMode: 'loose',
  })

  router.usePlugin(browserPlugin({}))
  router.useMiddleware(logMiddleware(routes))
  router.useMiddleware(redirectMiddleware(routes))
  router.useMiddleware(triggerMiddleware(routes))

  return router
}

export default Router
