import { applyMiddleware, createStore, compose } from 'redux'
import thunk from 'redux-thunk'
import { router5Middleware } from 'redux-router5'

import reducer from './reducer'

const Store = (router, initialState = {}) => {

  const middleware = [
    router5Middleware(router),
    thunk,
  ]

  const storeEnhancers = [
    applyMiddleware(...middleware),
  ]

  if(window.__REDUX_DEVTOOLS_EXTENSION__) storeEnhancers.push(window.__REDUX_DEVTOOLS_EXTENSION__({
    shouldHotReload: false,
  }))

  const store = createStore(
    reducer,
    initialState,
    compose(...storeEnhancers)
  )

  router.setDependency('store', store)
  router.start()

  if (module.hot) {
    module.hot.accept('./reducer', () => {
      store.replaceReducer(require('./reducer').default)
    })
  }

  return store
}

export default Store