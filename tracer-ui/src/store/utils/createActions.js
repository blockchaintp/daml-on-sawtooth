/*

  createActions({
    prefix,
    reducers,
    sideEffects,
  }) => map[string][function]
  
  const actions = createActions({
    reducers: {
      toggleOpen: (state, action) => {
        state.open = action.payload
      },
      setData: (state, action) => {
        state.data = action.payload
      }
    }
    sideEffects: {
      loadStatus: () => (dispatch, getState) => {
        console.log('--------------------------------------------')
        console.log('we are in a thunk')
      },
      testSaga: 'testSaga',
    }
  })

  reducers is the map of reducer functions - for these we create a simple
  payload based action based on the object key + prefix

  sideEffects is a map of either function (for a thunk) or string (for a saga trigger)

  in the case of a thunk - we include the thunk function as is
  in the case of a saga trigger - we include a payload based action based
  on the object key + prefix

*/

import CreateAction from './createAction'

const CreateActions = ({
  prefix,
  reducers,
  sideEffects,
}) => {
  const reducerActions = Object.keys(reducers || {}).reduce((all, key) => {
    all[key] = CreateAction(prefix ? `${prefix}/${key}` : key)
    return all
  }, {})

  const sideEffectActions = Object.keys(sideEffects || {}).reduce((all, key) => {
    const handler = sideEffects[key]

    if(typeof(handler) === 'string') {
      all[key] = CreateAction(prefix ? `${prefix}/${handler}` : handler)
    }
    else if(typeof(handler) === 'function') {
      all[key] = handler
    }
    else {
      throw new Error(`unknown sideEffect type for ${key} of type ${typeof(handler)}`)
    }
    
    return all
  }, {})

  return Object.assign({}, reducerActions, sideEffectActions)
}

export default CreateActions