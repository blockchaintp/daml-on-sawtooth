import React from 'react'
import PropTypes from 'prop-types'
import classNames from 'classnames'
import { withStyles } from '@material-ui/core/styles'
import Snackbar from '@material-ui/core/Snackbar'
import SnackbarContent from '@material-ui/core/SnackbarContent'
import IconButton from '@material-ui/core/IconButton'

import CloseIcon from '@material-ui/icons/Close'
import CheckCircleIcon from '@material-ui/icons/CheckCircle'
import WarningIcon from '@material-ui/icons/Warning'
import ErrorIcon from '@material-ui/icons/Error'
import InfoIcon from '@material-ui/icons/Info'

import green from '@material-ui/core/colors/green'
import amber from '@material-ui/core/colors/amber'

import settings from 'settings'

const variantIcon = {
  success: CheckCircleIcon,
  warning: WarningIcon,
  error: ErrorIcon,
  info: InfoIcon,
}

const styles = theme => ({
  fullHeight: {
    height: '100%'
  },
  icon: {
    fontSize: 20,
  },
  iconVariant: {
    opacity: 0.9,
    marginRight: theme.spacing.unit,
  },
  message: {
    display: 'flex',
    alignItems: 'center',
  },
  close: {
    padding: theme.spacing.unit / 2,
  },
  success: {
    backgroundColor: green[600],
  },
  warning: {
    backgroundColor: amber[700],
  },
  error: {
    backgroundColor: theme.palette.error.dark,
  },
  info: {
    backgroundColor: theme.palette.primary.dark,
  },
  margin: {
    margin: theme.spacing.unit,
  },
})

class SnackbarWrapper extends React.Component {

  render() {
    const { 
      classes,
      open,
      text,
      type,
      onClose,
      children,
    } = this.props

    const Icon = variantIcon[type]

    return (
      <div className={ classes.fullHeight }>
        { children }
        <Snackbar
          anchorOrigin={{
            vertical: 'bottom',
            horizontal: 'center',
          }}
          open={ open }
          autoHideDuration={ settings.snackbarAutoHide }
          onClose={ onClose }
        >
          <SnackbarContent
            className={ classNames(classes[type], classes.margin) }
            aria-describedby="client-snackbar"
            message={
              <span id="client-snackbar" className={ classes.message }>
                { 
                  Icon && (
                    <Icon 
                      className={ classNames(classes.icon, classes.iconVariant) }
                    />
                  )
                }
                { text }
              </span>
            }
            action={[
              <IconButton
                key="close"
                aria-label="Close"
                color="inherit"
                className={ classes.close }
                onClick={ onClose }
              >
                <CloseIcon />
              </IconButton>,
            ]}
          />
        </Snackbar>
      </div>
    )
  }
}

SnackbarWrapper.propTypes = {
  classes: PropTypes.object.isRequired,
  open: PropTypes.bool.isRequired,
  text: PropTypes.string.isRequired,
  type: PropTypes.string.isRequired,
  onClose: PropTypes.func.isRequired,
}

export default withStyles(styles)(SnackbarWrapper)