import { ConfigContext, ExpoConfig } from 'expo/config';

type AppVariant = 'development' | 'preview' | 'production';

const APP_VARIANT = (process.env.APP_VARIANT ?? 'production') as AppVariant;

const BASE_PACKAGE = 'com.letstable';
const BASE_NAME = "Let's Table";
const BASE_SCHEME = 'letstable';

const VARIANTS: Record<AppVariant, { suffix: string; nameSuffix: string }> = {
	development: { suffix: '.dev', nameSuffix: ' (Dev)' },
	preview: { suffix: '.preview', nameSuffix: ' (Preview)' },
	production: { suffix: '', nameSuffix: '' },
}

const variant = VARIANTS[APP_VARIANT];

const APP_NAME = `${BASE_NAME}${variant.nameSuffix}`;
const PACKAGE_NAME = `${BASE_PACKAGE}${variant.suffix}`;
const SCHEME = `${BASE_SCHEME}${variant.suffix.replace('.', '-')}`;

export default ({ config }: ConfigContext): ExpoConfig => ({
	...config,
	name: APP_NAME,
	slug: "lets-table",
	owner: "stegaspadev2002",
	version: "2.1.0",
	orientation: "portrait",
	icon: "./assets/images/icon.png",
	scheme: SCHEME,
	userInterfaceStyle: "automatic",
	ios: {
		bundleIdentifier: PACKAGE_NAME,
		supportsTablet: false,
		infoPlist: {
			ITSAppUsesNonExemptEncryption: false
		}
	},
	android: {
		package: PACKAGE_NAME,
		googleServicesFile: process.env.GOOGLE_SERVICES_JSON ?? "./google-services.json",
		adaptiveIcon: {
			foregroundImage: './assets/images/adaptive-icon.png',
			monochromeImage: './assets/images/adaptive-icon-monochrome.png',
			backgroundColor: '#F2EFE9',
		},
	},
	plugins: [
		"expo-router",
		"expo-status-bar",
		"expo-secure-store",
		"expo-image",
		[
			"expo-splash-screen",
			{
				image: "./assets/images/splash-icon.png",
				imageWidth: 200,
				resizeMode: "contain",
				backgroundColor: "#F2EFE9",
				dark: {
					image: "./assets/images/splash-icon-dark.png",
					backgroundColor: "#17212B",
				},
			},
		],
		[
			"expo-notifications",
			{
				icon: './assets/images/notification-icon.png',
				color: '#C45135',
			},
		],
		[
			"expo-image-picker",
			{
				photosPermission: "Let's Table usa le tue foto per scegliere l'immagine del profilo.",
				microphonePermission: false
			}
		],
		[
			'react-native-android-widget',
			{
				fonts: [
					'./node_modules/@expo-google-fonts/playfair-display/700Bold/PlayfairDisplay_700Bold.ttf',
					'./node_modules/@expo-google-fonts/plus-jakarta-sans/500Medium/PlusJakartaSans_500Medium.ttf',
					'./node_modules/@expo-google-fonts/plus-jakarta-sans/700Bold/PlusJakartaSans_700Bold.ttf',
				],
				widgets: [
					{
						name: 'StatsOverview',
						label: 'Statistiche',
						description: 'Vittorie, collezione e amici',
						minWidth: '250dp',
						minHeight: '40dp',
						targetCellWidth: 4,
						targetCellHeight: 1,
						updatePeriodMillis: 1800000,
					},
					{
						name: 'WinRate',
						label: 'Vittorie',
						description: 'Percentuale di partite vinte',
						minWidth: '110dp',
						minHeight: '40dp',
						targetCellWidth: 2,
						targetCellHeight: 1,
						updatePeriodMillis: 1800000,
					},
					{
						name: 'Collection',
						label: 'Collezione',
						description: 'Giochi posseduti',
						minWidth: '110dp',
						minHeight: '40dp',
						targetCellWidth: 2,
						targetCellHeight: 1,
						updatePeriodMillis: 1800000,
					},
					{
						name: 'Friends',
						label: 'Amici',
						description: 'Numero di amici',
						minWidth: '110dp',
						minHeight: '40dp',
						targetCellWidth: 2,
						targetCellHeight: 1,
						updatePeriodMillis: 1800000,
					},
				],
			},
		],
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