import tape from 'tape'
import createAction from './createAction'

tape('createAction -> standard', (t) => {
  const actionFactory = createAction('toggleOpen')
  const action = actionFactory(true)
  t.deepEqual(action, {
    type: 'toggleOpen',
    payload: true,
  }, `the action is correct`)
  t.end()
})

tape('createAction -> action has type field', (t) => {
  const actionFactory = createAction('toggleOpen')
  t.equal(actionFactory.type, 'toggleOpen', `the type field is set on the action`)
  t.end()
})
