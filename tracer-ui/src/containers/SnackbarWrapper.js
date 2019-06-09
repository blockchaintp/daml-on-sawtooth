import React from 'react'
import { connect } from 'react-redux'

import selectors from 'store/selectors'
import snackbarActions from 'store/modules/snackbar'

import SnackbarWrapper from 'pages/SnackbarWrapper'

@connect(
  state => ({
    open: selectors.snackbar.open(state),
    text: selectors.snackbar.text(state),
    type: selectors.snackbar.type(state),
  }),
  {
    onClose: snackbarActions.onClose,
  },
)
class SnackbarWrapperContainer extends React.Component {

  render() {
    return (
      <SnackbarWrapper {...this.props} />
    )
  }
}

export default SnackbarWrapperContainer