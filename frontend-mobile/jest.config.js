module.exports = {
	preset: 'jest-expo',
	testMatch: ['<rootDir>/test/**/*.test.{ts,tsx}'],
	transformIgnorePatterns: [
		'node_modules/(?!(\\.pnpm|standard-navigation|(jest-)?react-native|@react-native(-community)?|@react-native/.*|expo(nent)?|@expo(nent)?/.*|@expo-google-fonts/.*|expo-router|react-navigation|@react-navigation/.*|@unimodules/.*|unimodules|sentry-expo|native-base|react-native-svg|@rn-primitives/.*|lucide-react-native))',
	],
	setupFilesAfterEnv: ['<rootDir>/test/jest.setup.ts'],
	moduleNameMapper: {
		'^@/(.*)$': '<rootDir>/$1',
		'^lucide-react-native$': '<rootDir>/node_modules/lucide-react-native/dist/cjs/lucide-react-native.js',
	},
	collectCoverageFrom: [
		'**/*.{ts,tsx}',
		'!**/coverage/**',
		'!**/node_modules/**',
		'!**/babel.config.js',
		'!**/jest.setup.js',
		'!**/*.config.{js,ts}',
		'!**/*.d.ts',
		'!app/**',
		'!components/**',
		'!test/**',
		'!types/**'
	],
	coverageReporters: ['lcov', 'text', 'html'],
}