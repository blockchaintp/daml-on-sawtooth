import React from 'react'
import PropTypes from 'prop-types'

import { withStyles } from '@material-ui/core/styles'
import SwipeableDrawer from '@material-ui/core/SwipeableDrawer'
import IconButton from '@material-ui/core/IconButton'
import List from '@material-ui/core/List'
import ListItem from '@material-ui/core/ListItem'
import ListItemIcon from '@material-ui/core/ListItemIcon'
import ListItemText from '@material-ui/core/ListItemText'
import Divider from '@material-ui/core/Divider'
import MenuIcon from '@material-ui/icons/Menu'

import settings from 'settings'

const styles = (theme) => ({
  menuButton: {
    marginLeft: -12,
    marginRight: 20,
  },
  list: {
    width: settings.sideMenuWidth,
  },
})

class SideMenu extends React.Component {
  state = {
    drawerOpen: false,
  }

  handleOpen = event => {
    this.setState({ drawerOpen: true })
  }

  handleClose = () => {
    this.setState({ drawerOpen: false })
  }

  clickItem(item) {
    const {
      openPage,
    } = this.props

    if(typeof(item.handler) === 'string') {
      openPage(item.handler)
      this.handleClose()
    }
    else if(typeof(item.handler) === 'function') {
      item.handler()
      this.handleClose()
    }
    else {
      throw new Error(`unknown SideMenu item handler for ${item.title}`)
    }
  }

  getMenu() {
    const { 
      items,
    } = this.props

    return items.map((item, i) => {
      if(item === '-') {
        return (
          <Divider key={ i } />
        )
      }

      return (
        <ListItem 
          button 
          key={ i }
          onClick={ () => this.clickItem(item) }
        >
          {
            item.icon && (
              <ListItemIcon>
                <item.icon />
              </ListItemIcon>
            )
          }
          <ListItemText 
            primary={ item.title }
          />
        </ListItem>
      )
    })
  }

  render() {
    const { classes } = this.props
    const { drawerOpen } = this.state

    return (
      <div>
        <IconButton 
          className={classes.menuButton} 
          color="inherit" 
          aria-label="Menu" 
          onClick={this.handleOpen}
        >
          <MenuIcon />
        </IconButton>
        <SwipeableDrawer
          open={ drawerOpen }
          onClose={ this.handleClose }
          onOpen={ this.handleOpen }
        >
          <div
            tabIndex={0}
            role="button"
            onClick={ this.handleClose }
            onKeyDown={ this.handleClose }
          >
            <div className={classes.list}>
              <List component="nav">
                { this.getMenu() }
              </List>
            </div>
          </div>
        </SwipeableDrawer>
      </div>
    )
  }
}

SideMenu.propTypes = {
  classes: PropTypes.object.isRequired,
  openPage: PropTypes.func.isRequired,
  items: PropTypes.array.isRequired,
}

export default withStyles(styles)(SideMenu)