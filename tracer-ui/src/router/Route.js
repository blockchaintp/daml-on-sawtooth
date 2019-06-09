import React from 'react'
import ErrorBoundary from 'components/system/ErrorBoundary'

import RouteContext from './RouteContext'

const MATCH_ROUTE_HANDLERS = {
  exact: (route, segment) => route == segment,
  startsWith: (route, segment) => route.indexOf(segment) == 0,
}

const stripRoute = (route, segment) => {
  if(!route) return ''
  const routeParts = route.split('.')
  const segmentParts = segment.split('.')
  return routeParts.slice(segmentParts.length).join('.')
}

class Route extends React.Component {

  /*
  
    get a route based on if the current route matches the given segment
    if the route matches, return the Component wrapped in an Error Boundary
    otherwise return null

     * segment - the segment to match (e.g. content.books)
     * exact - how to match the segment, possible values:
       * true - must match exactly (default)
       * false - the route starts with the segment
     * default - if true, render if there is no route present
     * 
  */

  doesMatch() {
    const {
      segment,
      exact,
    } = this.props

    const route = this.context

    if(this.props.default && !route) return true

    const handler = exact ? MATCH_ROUTE_HANDLERS.exact : MATCH_ROUTE_HANDLERS.startsWith

    return handler(route, segment)
  }

  render() {
    const {
      segment,
      exact,
      children,
    } = this.props

    const route = this.context

    if(!this.doesMatch()) return null

    const newRoute = exact ? '' : stripRoute(route, segment)

    return (
      <RouteContext.Provider value={ newRoute }>
        <ErrorBoundary>
          { children }
        </ErrorBoundary>
      </RouteContext.Provider>
    )
  }
}

Route.contextType = RouteContext

export default Route