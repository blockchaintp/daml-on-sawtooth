/*

  createAction(actionName) => (payload)

  * actionName    the 'type' field of the action


  const actionFactory = createAction('toggleOpen')

  const action = actionFactory(true)

  action:
  
  {
    type: 'toggleOpen',
    payload: true,
  }

*/
const CreateAction = (type) => {
  const actionCreator = (payload) => ({
    type,
    payload,
  })
  actionCreator.type = type
  return actionCreator
}

export default CreateAction