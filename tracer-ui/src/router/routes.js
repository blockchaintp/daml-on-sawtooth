import transactionActions from 'store/modules/transactions'

const routes = [
  {
    name: 'home',
    path: '/',
    trigger: {
      activate: (store) => {
        store.dispatch(transactionActions.startLoadTransactionLoop('read'))
        store.dispatch(transactionActions.startLoadTransactionLoop('write'))
      },
      deactivate: (store) => {
        store.dispatch(transactionActions.stopLoadTransactionLoop('read'))
        store.dispatch(transactionActions.stopLoadTransactionLoop('write'))
      },
    },
  },
  {
    name: 'notfound',
    path: '/notfound',
  },
]


export default routes