const merge = require('webpack-merge')
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin
const VisualizerPlugin = require('webpack-visualizer-plugin')

const prod = require('./webpack.prod')

module.exports = merge(prod, {
  plugins: [
    new BundleAnalyzerPlugin({
      analyzerMode: 'static',
      reportFilename: './analyze/webpack-bundle-analyzer.html',
      openAnalyzer: false,
    }),
    new VisualizerPlugin({
      filename: './analyze/webpack-visualizer-plugin.html'
    }),
  ],
})