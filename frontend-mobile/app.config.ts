import { ConfigContext, ExpoConfig } from 'expo/config';

export default ({ config }: ConfigContext): ExpoConfig => ({
	...config,
	name: "Let's Table",
	slug: 'lets-table',
	extra: {
		apiUrl: process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080',
	},
});