import React from 'react'
import { connect } from 'react-redux'

import counterActions from 'store/modules/counter'
import selectors from 'store/selectors'
import Home from 'pages/Home'

@connect(
  state => ({
    count: selectors.counter.count(state),
    readTransactions: selectors.transactions.read(state),
    writeTransactions: selectors.transactions.write(state),
  }),
  {
    increment: counterActions.increment,
    decrement: counterActions.decrement,
  },
)
class HomeContainer extends React.Component {

  render() {
    return (
      <Home {...this.props} />
    )    
  }
}

export default HomeContainer