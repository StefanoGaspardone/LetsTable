import { Linking } from 'react-native';
import * as FileSystem from 'expo-file-system/legacy';
import * as IntentLauncher from 'expo-intent-launcher';

import { compareVersions, downloadAndInstallApk, isPlayDistribution, openStorePage, parseReleaseNotes } from '@/lib/app-version';

let mockDistribution: string | undefined = 'apk';

jest.mock('expo-constants', () => ({
	__esModule: true,
	default: {
		get expoConfig() {
			return { extra: { distribution: mockDistribution } };
		},
	},
}));

jest.mock('expo-application', () => ({
	applicationId: 'com.letstable.app',
}));

jest.mock('expo-file-system/legacy', () => ({
	cacheDirectory: 'file:///cache/',
	deleteAsync: jest.fn(),
	createDownloadResumable: jest.fn(),
	getContentUriAsync: jest.fn(),
}));

jest.mock('expo-intent-launcher', () => ({
	startActivityAsync: jest.fn(),
}));

const mockedDeleteAsync = jest.mocked(FileSystem.deleteAsync);
const mockedCreateDownloadResumable = jest.mocked(FileSystem.createDownloadResumable);
const mockedGetContentUriAsync = jest.mocked(FileSystem.getContentUriAsync);
const mockedStartActivityAsync = jest.mocked(IntentLauncher.startActivityAsync);

const APK_URL = 'https://github.com/StefanoGaspardone/LetsTable/releases/download/v2.4.0/lets-table-2.4.0.apk';
const TARGET = 'file:///cache/lets-table-2.4.0.apk';

describe('compareVersions', () => {
	it.each([
		['2.3.0', '2.3.0', 0],
		['2.3.1', '2.3.0', 1],
		['2.3.0', '2.4.0', -1],
		['3.0.0', '2.9.9', 1],
		['2.10.0', '2.9.0', 1],
		['2.3', '2.3.0', 0],
		['2.3.0.1', '2.3.0', 1],
		['2.x.0', '2.0.0', 0],
	])('compares %s with %s as %i', (left, right, expected) => {
		expect(compareVersions(left, right)).toBe(expected);
	});
});

describe('parseReleaseNotes', () => {
	it('parses sections and "**Title**: description" items, ignoring the header lines', () => {
		const markdown = [
			'<!-- min-supported: 2.3.0 -->',
			'# Let\'s Table - v2.4.0',
			'',
			'The version **2.4.0** is here!',
			'',
			'---',
			'',
			'## New Features',
			'',
			'- **Widgets**: Four new widgets',
			'- **Updates**: In-app updates',
			'',
			'## Fixes & Improvements',
			'- **Sorting**: Sort by winner score',
		].join('\n');

		expect(parseReleaseNotes(markdown)).toEqual([
			{
				title: 'New Features',
				items: [
					{ title: 'Widgets', description: 'Four new widgets' },
					{ title: 'Updates', description: 'In-app updates' },
				],
			},
			{
				title: 'Fixes & Improvements',
				items: [{ title: 'Sorting', description: 'Sort by winner score' }],
			},
		]);
	});

	it('handles Windows line endings coming from GitHub', () => {
		const markdown = '## Novità\r\n- **Prova**: Release di test\r\n';

		expect(parseReleaseNotes(markdown)).toEqual([
			{ title: 'Novità', items: [{ title: 'Prova', description: 'Release di test' }] },
		]);
	});

	it('keeps bullets without a bold title as plain descriptions', () => {
		expect(parseReleaseNotes('## Fixes\n- Minor bug fixes')).toEqual([
			{ title: 'Fixes', items: [{ title: null, description: 'Minor bug fixes' }] },
		]);
	});

	it('accepts a bold title without colon', () => {
		expect(parseReleaseNotes('## Fixes\n- **Crash** on startup')).toEqual([
			{ title: 'Fixes', items: [{ title: 'Crash', description: 'on startup' }] },
		]);
	});

	it('ignores bullets before the first section and drops empty sections', () => {
		const markdown = '- **Orphan**: ignored\n## Empty\n## Filled\n- **Item**: kept';

		expect(parseReleaseNotes(markdown)).toEqual([
			{ title: 'Filled', items: [{ title: 'Item', description: 'kept' }] },
		]);
	});

	it('returns an empty list for empty notes', () => {
		expect(parseReleaseNotes('')).toEqual([]);
	});
});

describe('isPlayDistribution', () => {
	afterEach(() => {
		mockDistribution = 'apk';
	});

	it.each([
		['play', true],
		['apk', false],
		[undefined, false],
	])('returns %s → %s', (distribution, expected) => {
		mockDistribution = distribution;

		expect(isPlayDistribution()).toBe(expected);
	});
});

describe('openStorePage', () => {
	afterEach(() => {
		jest.restoreAllMocks();
	});

	it('opens the Play Store app on the app page', async () => {
		const openURL = jest.spyOn(Linking, 'openURL').mockResolvedValue(true);

		await openStorePage();

		expect(openURL).toHaveBeenCalledWith('market://details?id=com.letstable.app');
	});

	it('falls back to the Play Store website when the store app is not available', async () => {
		const openURL = jest.spyOn(Linking, 'openURL')
			.mockRejectedValueOnce(new Error('No activity'))
			.mockResolvedValueOnce(true);

		await openStorePage();

		expect(openURL).toHaveBeenLastCalledWith('https://play.google.com/store/apps/details?id=com.letstable.app');
	});
});

describe('downloadAndInstallApk', () => {
	let progressCallback: ((event: { totalBytesWritten: number; totalBytesExpectedToWrite: number }) => void) | undefined;
	const mockDownloadAsync = jest.fn();

	beforeEach(() => {
		jest.clearAllMocks();
		progressCallback = undefined;

		mockedDeleteAsync.mockResolvedValue(undefined);
		mockedGetContentUriAsync.mockResolvedValue('content://letstable/cache/lets-table-2.4.0.apk');
		mockedStartActivityAsync.mockResolvedValue({ resultCode: 0 } as any);
		mockedCreateDownloadResumable.mockImplementation((_url, _target, _options, callback) => {
			progressCallback = callback as typeof progressCallback;
			return { downloadAsync: mockDownloadAsync } as any;
		});
		mockDownloadAsync.mockImplementation(async () => {
			progressCallback?.({ totalBytesWritten: 50, totalBytesExpectedToWrite: 100 });
			progressCallback?.({ totalBytesWritten: 100, totalBytesExpectedToWrite: 100 });
			return { status: 200, uri: TARGET };
		});
	});

	it('removes a previous download, downloads the APK into the cache and reports progress', async () => {
		const onProgress = jest.fn();

		await downloadAndInstallApk(APK_URL, '2.4.0', onProgress);

		expect(mockedDeleteAsync).toHaveBeenCalledWith(TARGET, { idempotent: true });
		expect(mockedCreateDownloadResumable).toHaveBeenCalledWith(APK_URL, TARGET, {}, expect.any(Function));
		expect(onProgress.mock.calls).toEqual([[0.5], [1]]);
	});

	it('opens the Android installer with a content URI and read permission', async () => {
		await downloadAndInstallApk(APK_URL, '2.4.0', jest.fn());

		expect(mockedGetContentUriAsync).toHaveBeenCalledWith(TARGET);
		expect(mockedStartActivityAsync).toHaveBeenCalledWith('android.intent.action.VIEW', {
			data: 'content://letstable/cache/lets-table-2.4.0.apk',
			flags: 1,
			type: 'application/vnd.android.package-archive',
		});
	});

	it('does not report progress when the total size is unknown', async () => {
		mockDownloadAsync.mockImplementationOnce(async () => {
			progressCallback?.({ totalBytesWritten: 50, totalBytesExpectedToWrite: -1 });
			return { status: 200, uri: TARGET };
		});
		const onProgress = jest.fn();

		await downloadAndInstallApk(APK_URL, '2.4.0', onProgress);

		expect(onProgress).not.toHaveBeenCalled();
	});

	it('throws without opening the installer when the server does not return the APK', async () => {
		mockDownloadAsync.mockResolvedValueOnce({ status: 404, uri: TARGET });

		await expect(downloadAndInstallApk(APK_URL, '2.4.0', jest.fn())).rejects.toThrow('APK download failed with status 404');

		expect(mockedStartActivityAsync).not.toHaveBeenCalled();
	});

	it('throws when the download is interrupted', async () => {
		mockDownloadAsync.mockResolvedValueOnce(undefined);

		await expect(downloadAndInstallApk(APK_URL, '2.4.0', jest.fn())).rejects.toThrow('APK download failed with status undefined');
	});
});