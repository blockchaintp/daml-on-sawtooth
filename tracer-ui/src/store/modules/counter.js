import CreateReducer from '../utils/createReducer'
import CreateActions from '../utils/createActions'

const prefix = 'counter'
const initialState = {
  count: 0,
}

const reducers = {
  increment: (state, action) => {
    state.count += action.payload
  },
  decrement: (state, action) => {
    state.count -= action.payload
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