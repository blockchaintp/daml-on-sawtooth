import createNextState from 'immer'

/*

  createReducer({
    initialState,
    reducers,
  }) => (state, action)

  * initialState    the initialState for this reducer
  * reducers        a map of functions to reduce each action

  {
    initialState: {
      open: false,
    },
    reducers: {
      toggleOpen: (state, action) => {
        state.open = action.payload
      },
    },
  }

  you would emit an action like this:

  {
    type: 'toggleOpen',
    payload: {
      open: true,
    },
  }

*/
const CreateReducer = ({
  initialState,
  reducers,
  prefix,
}) => {
  if(!reducers) throw new Error(`reducers required for CreateReducer`)
  const useReducers = Object.keys(reducers).reduce((all, name) => {
    const key = prefix ? `${prefix}/${name}` : name
    all[key] = reducers[name]
    return all
  }, {})

  return (state = initialState, action) => {
    return createNextState(state, draft => {
      const caseReducer = useReducers[action.type]
      return caseReducer ? caseReducer(draft, action) : undefined
    })
  }
}

export default CreateReducer