import tape from 'tape'
import createActions from './createActions'

tape('createActions -> standard', (t) => {
  const actions = createActions({
    reducers: {
      toggleOpen: (state, action) => {
        state.open = action.payload
      },
      setData: (state, action) => {
        state.data = action.payload
      }
    }
  })
  t.deepEqual(actions.toggleOpen(true), {
    type: 'toggleOpen',
    payload: true,
  }, `the toggleOpen action is correct`)
  t.deepEqual(actions.setData(10), {
    type: 'setData',
    payload: 10,
  }, `the setData action is correct`)
  t.end()
})

tape('createActions -> with prefix', (t) => {
  const actions = createActions({
    prefix: 'apples',
    reducers: {
      toggleOpen: (state, action) => {
        state.open = action.payload
      },
      setData: (state, action) => {
        state.data = action.payload
      }
    }
  })
  t.deepEqual(actions.toggleOpen(true), {
    type: 'apples/toggleOpen',
    payload: true,
  }, `the toggleOpen action is correct`)
  t.deepEqual(actions.setData(10), {
    type: 'apples/setData',
    payload: 10,
  }, `the setData action is correct`)
  t.end()
})
