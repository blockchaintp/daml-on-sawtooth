const isRgb = string => /#?([0-9a-f]{6})/i.test(string)

const utils = {
  isRgb,
}

export default utils