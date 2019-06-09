const read = [{
  name: 'a',
  type: 'read',
},{
  name: 'b',
  type: 'read',
},{
  name: 'c',
  type: 'read',
}]

const write = [{
  name: 'd',
  type: 'write',
},{
  name: 'e',
  type: 'write',
},{
  name: 'f',
  type: 'write',
}]

module.exports = {
  transactions: {
    read,
    write,
  }
}

