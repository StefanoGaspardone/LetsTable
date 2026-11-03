export interface AppVersionInfo {
	latestVersion: string;
	minSupportedVersion: string;
	downloadUrl: string;
	releaseNotes: string | null;
	publishedAt: string;
}