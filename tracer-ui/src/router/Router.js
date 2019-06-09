import React, { Suspense, lazy } from 'react'
import { connect } from 'react-redux'

import selectors from 'store/selectors'
import Loading from 'components/system/Loading'

import LayoutMain from 'containers/Layout'

import Route from './Route'
import RouteContext from './RouteContext'

import NotFound from 'containers/NotFound'

import HomePage from 'containers/Home'

@connect(
  state => {
    return {
      route: selectors.router.route(state),
    }
  },
  {

  }
)
class Router extends React.Component {

  render() {
    const {
      route,
    } = this.props

    if(!route) return <Loading />

    return (
      <RouteContext.Provider value={ route.name }>
        <LayoutMain>
          <Route segment="notfound" exact>
            <NotFound />
          </Route>
          <Route segment="home" exact>
            <HomePage />
          </Route>
        </LayoutMain>
      </RouteContext.Provider>
    )
  }
}

export default Router