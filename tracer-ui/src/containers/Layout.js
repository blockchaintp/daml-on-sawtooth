import React from 'react'
import { connect } from 'react-redux'

import selectors from 'store/selectors'
import routerActions from 'store/modules/router'

import settings from 'settings'

import Layout from 'pages/Layout'

@connect(
  state => ({
    title: settings.title,
  }),
  {
    openPage: routerActions.navigateTo,
  },
)
class LayoutContainer extends React.Component {

  render() {
    const sideMenuItems = settings.sideMenu({
      handlers: {}
    })

    const appBarMenuItems = settings.appbarMenu({
      handlers: {},
    })

    const layoutProps = {
      sideMenuItems,
      appBarMenuItems,
      ...this.props,
    }

    return (
      <Layout {...layoutProps} />
    )    
  }
}

export default LayoutContainer