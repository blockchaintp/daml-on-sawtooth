import CreateReducer from '../utils/createReducer'
import CreateActions from '../utils/createActions'

const prefix = 'network'
const initialState = {
  loading: {},
  errors: {},
}

const reducers = {
  setLoading: (state, action) => {
    const {
      name,
      value,
    } = action.payload
    state.loading[name] = value
  },
  startLoading: (state, action) => {
    state.loading[action.payload] = true
  },
  stopLoading: (state, action) => {
    state.loading[action.payload] = false
  },
  setError: (state, action) => {
    const {
      name,
      value,
    } = action.payload
    state.errors[name] = value
  },
  clearError: (state, action) => {
    state.errors[action.payload] = null
  },
}

const reducer = CreateReducer({
  initialState,
  reducers,
  prefix,
})

const actions = CreateActions({
  reducers,
  prefix,
})

export { actions, reducer }
export default actions