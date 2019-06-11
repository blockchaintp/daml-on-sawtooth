import React from 'react'

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = {
      error: null,
      errorInfo: null,
    }
  }
  
  componentDidCatch(error, errorInfo) {
    this.setState({
      error: error,
      errorInfo: errorInfo,
    })
  }
  
  render() {
    if (this.state.errorInfo) {
      return (
        <div>
          <h4>Something went wrong:</h4>
          <details style={{ whiteSpace: 'pre-wrap' }}>
            {
              this.state.error && (
                <summary>{ this.state.error.toString() }</summary>
              )
            }
            <br />
            {this.state.errorInfo.componentStack}
          </details>
        </div>
      )
    }
    return this.props.children
  }  
}

export default ErrorBoundary