import { ConfigContext, ExpoConfig } from 'expo/config';

export default ({ config }: ConfigContext): ExpoConfig => ({
	...config,
	name: "Let's Table",
	slug: "lets-table",
	owner: "stegaspadev2002",
	version: "2.0.0",
	orientation: "portrait",
	icon: "./assets/images/icon.png",
	scheme: "letstable",
	userInterfaceStyle: "automatic",
	ios: {
		bundleIdentifier: "com.letstable.app",
		supportsTablet: false,
		infoPlist: {
			ITSAppUsesNonExemptEncryption: false
		}
	},
	android: {
		package: "com.letstable.app",
		adaptiveIcon: {
			foregroundImage: "./assets/images/adaptive-icon.png",
			backgroundColor: "#ffffff"
		}
	},
	plugins: [
		"expo-router",
		"expo-status-bar",
		"expo-secure-store",
		"expo-image",
		[
			"expo-splash-screen",
			{
				image: "./assets/images/adaptive-icon.png",
				imageWidth: 200,
				resizeMode: "contain",
				backgroundColor: "#f1efe9"
			}
		],
		[
			"expo-notifications",
			{
				"color": "#C45135"
			}
		],
		[
			"expo-image-picker",
			{
				photosPermission: "Let's Table usa le tue foto per scegliere l'immagine del profilo.",
				microphonePermission: false
			}
		]
	],
	experiments: {
		typedRoutes: true
	},
	extra: {
		...config.extra,
		apiUrl: process.env.EXPO_PUBLIC_API_URL ?? "https://letstable-backend-1040192390713.europe-west1.run.app/api/v1",
		eas: {
			projectId: "051685f9-5f00-4569-b4f4-483ffefc6bc8"
		}
	}
});