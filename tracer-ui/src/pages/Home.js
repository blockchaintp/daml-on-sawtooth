import React from 'react'
import PropTypes from 'prop-types'
import { withStyles } from '@material-ui/core/styles'
import Tabs from '@material-ui/core/Tabs';
import Tab from '@material-ui/core/Tab';

import SimpleTable from 'components/table/SimpleTable'

const transactionFields =[{
  title: 'Event Type/Transaction Batch',
},{
  title: 'Attributes/Transactions',
}]

const styles = theme => ({
  
})

class HomePage extends React.Component {

  constructor(props){
    super(props)
    this.state = { value: 0}
  }

  render() {
    const { 
      readTransactions,
      writeTransactions,
    } = this.props
    return (
      <div>
        <Tabs value={this.state.value} onChange={(event, newValue)=>{
          this.state.value = newValue
        }}>
          <Tab label="Events" />
          <Tab label="Transactions" />
        </Tabs>
        {this.state.value === 0 && <SimpleTable
          data={ readTransactions }
          fields={ transactionFields }
        />}
        {this.state.value === 1 && <SimpleTable
          data={ writeTransactions }
          fields={ transactionFields }
        />}
      </div>
    )
  }
}

HomePage.propTypes = {
  classes: PropTypes.object.isRequired,
}

export default withStyles(styles)(HomePage)