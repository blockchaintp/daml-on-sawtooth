import React from 'react'
import PropTypes from 'prop-types'

import { withStyles } from '@material-ui/core/styles'
import Button from '@material-ui/core/Button'

import Menu from '@material-ui/core/Menu'
import MenuItem from '@material-ui/core/MenuItem'
import ListItemIcon from '@material-ui/core/ListItemIcon'
import ListItemText from '@material-ui/core/ListItemText'
import Divider from '@material-ui/core/Divider'
import settings from 'settings'

const MoreIcon = settings.icons.more

const styles = (theme) => ({
  avatarWrapper: {
    display: 'flex',
  },
  avatarName: {
    flex: 1,
    margin: theme.spacing.unit,
  },
})

class AppBarMenu extends React.Component {
  state = {
    anchorEl: null,
  }

  handleMenu = event => {
    this.setState({ anchorEl: event.currentTarget })
  }

  handleClose = () => {
    this.setState({ anchorEl: null })
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
      throw new Error(`unknown AppBarMenu item handler for ${item.title}`)
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
        <MenuItem
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
        </MenuItem>
      )
    })
  }

  render() {
    const { 
      classes, 
    } = this.props

    const { 
      anchorEl,
    } = this.state

    const open = Boolean(anchorEl)

    return (
      <div className={ classes.root }>
        <Button
          aria-owns={ open ? 'appbar-menu' : null }
          aria-haspopup="true"
          onClick={ this.handleMenu }
          color="inherit"
        >
          <MoreIcon />
        </Button>
        <Menu
          id="appbar-menu"
          anchorEl={ anchorEl }
          anchorOrigin={{
            vertical: 'top',
            horizontal: 'right',
          }}
          transformOrigin={{
            vertical: 'top',
            horizontal: 'right',
          }}
          open={ open }
          onClose={ this.handleClose }
        >
          <MenuItem key="placeholder" style={{display: "none"}} />
          { this.getMenu() }
        </Menu>
      </div>
    )
  }
}

AppBarMenu.propTypes = {
  classes: PropTypes.object.isRequired,
  user: PropTypes.object,
  openPage: PropTypes.func.isRequired,
  items: PropTypes.array.isRequired,
}

export default withStyles(styles)(AppBarMenu)