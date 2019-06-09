import React from 'react'
import PropTypes from 'prop-types'
import { withStyles } from '@material-ui/core/styles'
import Button from '@material-ui/core/Button'

const styles = theme => ({
 
})

class NotFound extends React.Component {

  constructor(props) {
    super(props)
    this.visitHome = () => props.navigateTo('home')
  }

  render() {
    return (
      <div>
        <div>page not found</div>
        <Button
          variant="contained"
          color="primary"
          onClick={ this.visitHome }
        >
          Home
        </Button>
      </div>
      
    )
  }
}

NotFound.propTypes = {
  classes: PropTypes.object.isRequired,
  navigateTo: PropTypes.func.isRequired,
}

export default withStyles(styles)(NotFound)