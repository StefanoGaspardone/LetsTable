import { Linking } from 'react-native';
import Constants from 'expo-constants';
import * as Application from 'expo-application';
import * as FileSystem from 'expo-file-system/legacy';
import * as IntentLauncher from 'expo-intent-launcher';

export const compareVersions = (left: string, right: string): number => {
	const leftParts = left.split('.').map(part => Number.parseInt(part, 10) || 0);
	const rightParts = right.split('.').map(part => Number.parseInt(part, 10) || 0);

	for(let index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
		const diff = (leftParts[index] ?? 0) - (rightParts[index] ?? 0);
		if(diff !== 0) return Math.sign(diff);
	}

	return 0;
}

export interface ReleaseNoteItem {
	title: string | null;
	description: string;
}

export interface ReleaseNoteSection {
	title: string;
	items: ReleaseNoteItem[];
}

const ITEM_PATTERN = /^\*\*(.+?)\*\*\s*:?\s*(.*)$/;

export const parseReleaseNotes = (markdown: string): ReleaseNoteSection[] => {
	const sections: ReleaseNoteSection[] = [];
	let current: ReleaseNoteSection | null = null;

	markdown.split(/\r?\n/).forEach(rawLine => {
		const line = rawLine.trim();

		if(line.startsWith('## ')) {
			current = { title: line.slice(3).trim(), items: [] };
			sections.push(current);
			return;
		}

		if(!current || !line.startsWith('- ')) return;

		const content = line.slice(2).trim();
		const match = new RegExp(ITEM_PATTERN).exec(content);

		current.items.push(match ? { title: match[1], description: match[2] } : { title: null, description: content });
	});

	return sections.filter(section => section.items.length > 0);
}

export const isPlayDistribution = () => Constants.expoConfig?.extra?.distribution === 'play';

export const openStorePage = async () => {
	const packageName = Application.applicationId;

	try {
		await Linking.openURL(`market://details?id=${packageName}`);
	} catch {
		await Linking.openURL(`https://play.google.com/store/apps/details?id=${packageName}`);
	}
}

const APK_MIME_TYPE = 'application/vnd.android.package-archive';
const FLAG_GRANT_READ_URI_PERMISSION = 1;

export const downloadAndInstallApk = async (url: string, version: string, onProgress: (progress: number) => void) => {
	const target = `${FileSystem.cacheDirectory}lets-table-${version}.apk`;

	await FileSystem.deleteAsync(target, { idempotent: true });

	const download = FileSystem.createDownloadResumable(url, target, {}, ({ totalBytesWritten, totalBytesExpectedToWrite }) => {
		if(totalBytesExpectedToWrite > 0) onProgress(totalBytesWritten / totalBytesExpectedToWrite);
	});

	const result = await download.downloadAsync();

	if(result?.status !== 200) {
		throw new Error(`APK download failed with status ${result?.status}`);
	}

	const contentUri = await FileSystem.getContentUriAsync(result.uri);

	await IntentLauncher.startActivityAsync('android.intent.action.VIEW', {
		data: contentUri,
		flags: FLAG_GRANT_READ_URI_PERMISSION,
		type: APK_MIME_TYPE,
	});
}