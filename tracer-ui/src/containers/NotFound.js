import React from 'react'
import { connect } from 'react-redux'

import routerActions from 'store/modules/router'

import NotFound from 'pages/NotFound'

@connect(
  state => ({
    
  }),
  {
    navigateTo: routerActions.navigateTo,
  },
)
class NotFoundContainer extends React.Component {
  render() {
    return (
      <NotFound {...this.props} />
    )    
  }
}

export default NotFoundContainer