import React from 'react'
import { MuiThemeProvider, createMuiTheme } from '@material-ui/core/styles'
import CssBaseline from '@material-ui/core/CssBaseline'

const theme = createMuiTheme({
  typography: {
    useNextVariants: true,
  },
  palette: {
    
  },
})

function Theme(props) {
  return (
    <MuiThemeProvider theme={theme}>
      <CssBaseline />
      { props.children }
    </MuiThemeProvider>
  );
}

export default Theme