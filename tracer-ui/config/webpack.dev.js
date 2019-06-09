const webpack = require('webpack')
const merge = require('webpack-merge')
const ErrorOverlayPlugin = require('error-overlay-webpack-plugin')

const common = require('./webpack.common')

module.exports = merge(common, {
  mode: 'development',
  devtool: 'cheap-module-source-map', 
  plugins: [
    new webpack.DefinePlugin({ 'process.env.NODE_ENV': JSON.stringify('development') }),
    new ErrorOverlayPlugin(),
  ],
  devServer: {
    host: '0.0.0.0',
    hot: true,
    historyApiFallback: true,
    disableHostCheck: true,
    overlay: true,
  },
})