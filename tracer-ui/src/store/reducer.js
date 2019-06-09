import { combineReducers } from 'redux'

import { reducer as router } from './modules/router'
import { reducer as snackbar } from './modules/snackbar'
import { reducer as network } from './modules/network'
import { reducer as counter } from './modules/counter'
import { reducer as transactions } from './modules/transactions'

const reducers = {
  router,
  snackbar,
  network,
  counter,
  transactions,
}

export default combineReducers(reducers)
