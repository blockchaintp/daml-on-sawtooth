import axios from 'axios'
import CreateReducer from '../utils/createReducer'
import CreateActions from '../utils/createActions'
import api from '../utils/api'

const prefix = 'transactions'

const initialState = {
  transactions: {
    read: [],
    write: [],
  },
  loops: {
    read: null,
    write: null,
  },
}

const reducers = {
  setTransactions: (state, action) => {
    const {
      type,
      data,
    } = action.payload
    state.transactions[type] = data
  },
  setWriteTransactions: (state, action) => {
    state.writeTransactions = action.payload
  },
  setLoop: (state, action) => {
    const {
      name,
      value,
    } = action.payload
    state.loops[name] = value
  },
}

const loaders = {

  loadTransactions: (type) => axios.get(api.url(`/transactions/${type}`))
    .then(api.process),
    
}

const sideEffects = {

  loadTransactions: (type) => (dispatch, getState) => api.loaderSideEffect({
    dispatch,
    loader: () => loaders.loadTransactions(type),
    prefix,
    name: `loadTransactions.${type}`,
    dataAction: (data) => actions.setTransactions({
      type,
      data,
    }),
    snackbarError: true,
  }),
  startLoadTransactionLoop: (type) => async (dispatch, getState) => {
    await dispatch(actions.loadTransactions(type))
    const intervalTaskId = setInterval(() => {
      dispatch(actions.loadTransactions(type))
    }, 1000)
    dispatch(actions.setLoop({
      name: type,
      value: intervalTaskId,
    }))
  },
  stopLoadTransactionLoop: (type) => (dispatch, getState) => {
    const intervalTaskId = getState().transactions.loops[type]
    clearInterval(intervalTaskId)
    dispatch(actions.setLoop({
      name: type,
      value: null,
    }))
  },
}

const reducer = CreateReducer({
  initialState,
  reducers,
  prefix,
})

const actions = CreateActions({
  reducers,
  sideEffects,
  prefix,
})

export {
  actions,
  reducer,
}

export default actions