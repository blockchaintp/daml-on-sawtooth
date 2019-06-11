import React from 'react'
import PropTypes from 'prop-types'
import { withStyles } from '@material-ui/core/styles'

import Dialog from '@material-ui/core/Dialog'
import DialogActions from '@material-ui/core/DialogActions'
import DialogContent from '@material-ui/core/DialogContent'
import DialogContentText from '@material-ui/core/DialogContentText'
import DialogTitle from '@material-ui/core/DialogTitle'
import Button from '@material-ui/core/Button'

const styles = theme => {
  return {
  }
}

class SimpleTableDeleteDialog extends React.Component {
  render() {
    const { classes, open, onCancel, onConfirm, title } = this.props

    return (
      <Dialog
        open={ open }
        onClose={ onCancel }
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogTitle id="alert-dialog-title">Delete { title }?</DialogTitle>
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            Are you sure you want to delete { title }?
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={ onCancel }>
            Cancel
          </Button>
          <Button onClick={ onConfirm } variant="contained" color="secondary" autoFocus>
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    )
  }
}

SimpleTableDeleteDialog.propTypes = {
  classes: PropTypes.object.isRequired,
}

export default withStyles(styles)(SimpleTableDeleteDialog)