'use strict'
 
const http = require('http')
 
const ecstatic = require('ecstatic')({
  root: `${__dirname}/dist/analyze`,
  showDir: true,
  autoIndex: true,
})
 
const PORT = process.env.PORT || 8080
http.createServer(ecstatic).listen(PORT)
console.log(`Listening on ${PORT}`)