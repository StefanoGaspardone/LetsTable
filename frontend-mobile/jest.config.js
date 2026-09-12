module.exports = {
	preset: 'jest-expo',
	testMatch: [
		'<rootDir>/test/**/*.test.{ts,tsx}',
	],
	transformIgnorePatterns: [
        String.raw`node_modules/(?!(\.pnpm|(jest-)?react-native|@react-native(-community)?|@react-native/.*|expo(nent)?|@expo(nent)?/.*|@expo-google-fonts/.*|react-navigation|@react-navigation/.*|@unimodules/.*|unimodules|sentry-expo|native-base|react-native-svg))`,
    ],
	collectCoverageFrom: [
		'**/*.{ts,tsx}',
		'!**/coverage/**',
		'!**/node_modules/**',
		'!**/babel.config.js',
		'!**/jest.setup.js',
		'!**/*.config.{js,ts}',
		'!app/_layout.tsx',
		'!test/**',
	],
	coverageReporters: [
		'lcov',
		'text',
		'html',
	],
}