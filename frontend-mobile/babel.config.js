module.exports = function (api) {
	const isTest = api.env('test');
	api.cache.using(() => process.env.NODE_ENV);

	return {
		presets: [['babel-preset-expo', { jsxImportSource: 'nativewind' }], 'nativewind/babel'],
		plugins: isTest ? ['babel-plugin-dynamic-import-node'] : [],
	}
}