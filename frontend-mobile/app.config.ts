import { ConfigContext, ExpoConfig } from 'expo/config';

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: "Let's Table",
  slug: "lets-table",
  version: "1.0.0",
  orientation: "portrait",
  icon: "./assets/images/icon.png",
  scheme: "letstable",
  userInterfaceStyle: "automatic",
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
    "expo-image"
  ],
  experiments: {
    typedRoutes: true
  },
  extra: {
    ...config.extra,
    apiUrl: process.env.EXPO_PUBLIC_API_URL ?? "https://letstable.onrender.com/api/v1",
    eas: {
      projectId: "fc865124-d0eb-474c-b0bc-f8d83486aba6"
    }
  }
});