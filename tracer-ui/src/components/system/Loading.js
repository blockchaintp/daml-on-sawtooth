import React from 'react'
import PropTypes from 'prop-types'

import { withStyles } from '@material-ui/core/styles'
import CircularProgress from '@material-ui/core/CircularProgress'
import Typography from '@material-ui/core/Typography'

const styles = theme => ({
  root: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '100%',
  },
  container: {
    maxWidth: '50%'
  },
  item: {
    textAlign: 'center',
    display: 'inline-block',
  },
})

class Loading extends React.Component {

  render() {
    const {
      classes,
      color,
      message,
    } = this.props

    return (
      <div className={classes.root}>
        <div className={classes.container}>
          <div className={classes.item}>
            <CircularProgress 
              color={ color }
            />
            { 
              message && (
                <Typography
                  variant='subtitle1'
                  color={ color }
                >
                  { message }
                </Typography>
              )
            }
          </div>
          
        </div>
      </div>
    )
  }
}

Loading.propTypes = {
  classes: PropTypes.object.isRequired,
  variant: PropTypes.string,
  color: PropTypes.string,
  message: PropTypes.string,
}

Loading.defaultProps = {
  variant: 'subheading',
  color: 'primary',
  message: 'loading',
}

export default withStyles(styles)(Loading)