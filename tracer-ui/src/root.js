import React from 'react'
import { Provider } from 'react-redux'

import Theme from './theme'
import Home from './containers/Home'

class Root extends React.Component {
  render() {

    const {
      store,
      router,
    } = this.props

    return (
      <Provider store={ store }>
        <Theme>
          <Home/>
        </Theme>
      </Provider>
    )
  }
}

export default Root
