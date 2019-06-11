import tape from 'tape'
import createReducer from './createReducer'

const initialState = {
  open: false,
}

const reducers = {
  toggleOpen: (state, action) => {
    state.open = action.payload
  }, 
}

const getReducer = (prefix) => createReducer({
  initialState,
  reducers,
  prefix,
})

tape('createReducer -> initial state', (t) => {
  const reducer = getReducer()
  t.deepEqual(reducer(undefined, {}), initialState, `the initial state is correct`)
  t.end()
})

tape('createReducer -> toggle action', (t) => {
  const reducer = getReducer()
  const newState = reducer(undefined, {
    type: 'toggleOpen',
    payload: true,
  })
  t.deepEqual(newState, {
    open: true,
  }, `the new state is correct`)
  t.end()
})

tape('createReducer -> with prefix', (t) => {
  const reducer = getReducer('apples')
  const newState = reducer(undefined, {
    type: 'apples/toggleOpen',
    payload: true,
  })
  t.deepEqual(newState, {
    open: true,
  }, `the new state is correct`)
  t.end()
})

tape('createReducer -> with no matching action', (t) => {
  const reducer = createReducer({
    initialState: {
      fruit: 10,
    },
    reducers: {
      apples: (state, action) => {
        state.fruit = action.payload
      }
    }
  })

  const newState = reducer(undefined, {
    type: 'oranges',
    payload: 11,
  })
  t.deepEqual(newState, {
    fruit: 10,
  }, `the new state is correct`)
  t.end()
})