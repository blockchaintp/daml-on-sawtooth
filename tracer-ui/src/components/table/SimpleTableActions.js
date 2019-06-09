import React from 'react'
import PropTypes from 'prop-types'
import { withStyles } from '@material-ui/core/styles'

import IconButton from '@material-ui/core/IconButton'
import Tooltip from '@material-ui/core/Tooltip'

const styles = theme => {
  return {
    
  }
}

class SimpleTableActions extends React.Component {
  
  render() {
    const { classes, item, actions } = this.props
    return (
      <div>
        {
          actions
            .filter(action => {
              if(action.shouldDisplay) return action.shouldDisplay(item)
              return true
            })
            .map((action, i) => {
              const IconClass = action.getIcon ? action.getIcon(item) : action.icon
              const title = action.getTitle ? action.getTitle(item) : action.title

              const button = (
                <IconButton onClick={ (event) => {
                  event.stopPropagation()
                  if(action.handler) {
                    action.handler(item)  
                  }
                }}>
                  <IconClass />
                </IconButton>
              )

              const renderButton = action.wrapButton ? action.wrapButton(button, item) : button
              return (
                <Tooltip disableFocusListener key={ i } title={ title }>
                  { renderButton }
                </Tooltip>
              )
            })
        }
      </div>
    )
  }
}

SimpleTableActions.propTypes = {
  classes: PropTypes.object.isRequired,
}

export default withStyles(styles)(SimpleTableActions)