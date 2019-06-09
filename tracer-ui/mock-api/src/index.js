const express = require('express')
const bodyParser = require('body-parser')

const pino = require('pino')({
  name: 'app',
})


const PORT = process.env.PORT || 80
const fixtures = require('./fixtures')

const app = express()
app.use(bodyParser.json())

app.get('/api/v1/transactions/:type', (req, res, next) => {
  const type = req.params.type
  const data = fixtures.transactions[type]

  if(!data) return next(`no transactions found of type: ${type}`)

  const mappedData = data.map((item, i) => {
    return Object.assign({}, item, {
      id: i,
      created: new Date().getTime(),
    })
  })

  res.json(mappedData)
})

app.use((req, res, next) => {
  const error = `url ${req.url} not found`
  pino.error({
    action: 'error',
    error,
    code: 404,
  })
  res.status(res._code || 404)
  res.json({ error })
})

/*

  error handler - any route that calls the err handler will end up here
  always prefer a JSON response
  
*/
app.use((err, req, res, next) => {
  pino.error({
    action: 'error',
    error: err.error ? err.error.toString() : err.toString(),
    stack: err.stack,
    code: res._code || 500
  })
  res.status(res._code || 500)
  res.json({ error: err.toString() })
})

app.listen(PORT, () => {
  pino.info({
    action: 'webserver.start',
    message: `webserver started on port ${PORT}`,
  })
})
