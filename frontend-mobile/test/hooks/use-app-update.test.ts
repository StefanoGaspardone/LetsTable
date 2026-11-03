import { Linking } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { renderHook, waitFor, act } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import { APP_VERSION_KEY, CURRENT_APP_VERSION, useAppUpdate, useCheckForUpdates, useUpdateInstaller } from '@/hooks/use-app-update';
import { getLatestAppVersion } from '@/api/app-version';
import { downloadAndInstallApk, isPlayDistribution, openStorePage } from '@/lib/app-version';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('expo-application', () => ({
	nativeApplicationVersion: '2.3.0',
}));

jest.mock('@/api/app-version', () => ({
	getLatestAppVersion: jest.fn(),
}));

jest.mock('@/lib/app-version', () => ({
	...jest.requireActual('@/lib/app-version'),
	downloadAndInstallApk: jest.fn(),
	openStorePage: jest.fn(),
	isPlayDistribution: jest.fn(),
}));

const mockShowToast = jest.fn();

jest.mock('@/contexts/toast-context', () => ({
	useToast: () => ({ showToast: mockShowToast }),
}));

const mockedGetLatestAppVersion = jest.mocked(getLatestAppVersion);
const mockedDownloadAndInstallApk = jest.mocked(downloadAndInstallApk);
const mockedOpenStorePage = jest.mocked(openStorePage);
const mockedIsPlayDistribution = jest.mocked(isPlayDistribution);

const DISMISSED_KEY = '@app_update_dismissed';
const DAY_MS = 24 * 60 * 60 * 1000;

const versionInfo = (latestVersion: string, minSupportedVersion = '2.0.0') => ({
	latestVersion,
	minSupportedVersion,
	downloadUrl: `https://example.com/lets-table-${latestVersion}.apk`,
	releaseNotes: '## Fixes & Improvements\n- **Fix**: Something',
	publishedAt: '2026-11-03T12:00:00Z',
});

const storeDismissed = (version: string, daysAgo: number) =>
	AsyncStorage.setItem(DISMISSED_KEY, JSON.stringify({ version, at: Date.now() - daysAgo * DAY_MS }));

let queryClient: QueryClient;
let wrapper: ReturnType<typeof createWrapper>['wrapper'];

const resetForcedPrompt = async () => {
	const { result } = await renderHook(() => useAppUpdate(false), { wrapper });

	await act(async () => {
		result.current.dismiss();
	});
}

beforeEach(async () => {
	jest.clearAllMocks();
	mockedGetLatestAppVersion.mockReset();
	mockedDownloadAndInstallApk.mockReset();
	mockedOpenStorePage.mockReset();
	mockedIsPlayDistribution.mockReset().mockReturnValue(false);
	await AsyncStorage.clear();

	({ wrapper, queryClient } = createWrapper());
});

afterEach(async () => {
	await resetForcedPrompt();
	queryClient.clear();
});

describe('useAppUpdate', () => {
	it('exposes the installed version', async () => {
		const { result } = await renderHook(() => useAppUpdate(false), { wrapper });

		expect(CURRENT_APP_VERSION).toBe('2.3.0');
		expect(result.current.currentVersion).toBe('2.3.0');
	});

	it('does not check for updates while disabled', async () => {
		const { result } = await renderHook(() => useAppUpdate(false), { wrapper });

		expect(mockedGetLatestAppVersion).not.toHaveBeenCalled();
		expect(result.current.status).toBe('none');
		expect(result.current.info).toBeNull();
	});

	it('reports no update when no version has been published', async () => {
		mockedGetLatestAppVersion.mockResolvedValueOnce(null);

		const { result } = await renderHook(() => useAppUpdate(true), { wrapper });

		await waitFor(() => expect(mockedGetLatestAppVersion).toHaveBeenCalled());
		expect(result.current.status).toBe('none');
	});

	it.each(['2.3.0', '2.2.0'])('reports no update when the latest version is %s', async (latest) => {
		mockedGetLatestAppVersion.mockResolvedValueOnce(versionInfo(latest));

		const { result } = await renderHook(() => useAppUpdate(true), { wrapper });

		await waitFor(() => expect(result.current.info).not.toBeNull());
		expect(result.current.status).toBe('none');
	});

	it('reports an available update when a newer version exists', async () => {
		mockedGetLatestAppVersion.mockResolvedValueOnce(versionInfo('2.4.0'));

		const { result } = await renderHook(() => useAppUpdate(true), { wrapper });

		await waitFor(() => expect(result.current.status).toBe('available'));
		expect(result.current.info?.latestVersion).toBe('2.4.0');
	});

	it('requires the update when the installed version is below the minimum supported one', async () => {
		await storeDismissed('2.5.0', 0);
		mockedGetLatestAppVersion.mockResolvedValueOnce(versionInfo('2.5.0', '2.4.0'));

		const { result } = await renderHook(() => useAppUpdate(true), { wrapper });

		await waitFor(() => expect(result.current.status).toBe('required'));
	});

	it('hides the prompt after dismissing it and remembers the choice', async () => {
		mockedGetLatestAppVersion.mockResolvedValueOnce(versionInfo('2.4.0'));

		const { result } = await renderHook(() => useAppUpdate(true), { wrapper });

		await waitFor(() => expect(result.current.status).toBe('available'));

		await act(async () => {
			result.current.dismiss();
		});

		expect(result.current.status).toBe('none');

		await waitFor(async () => {
			const stored = JSON.parse((await AsyncStorage.getItem(DISMISSED_KEY))!);
			expect(stored.version).toBe('2.4.0');
			expect(typeof stored.at).toBe('number');
		});
	});

	it('stays quiet for 3 days after the update was dismissed', async () => {
		await storeDismissed('2.4.0', 1);
		mockedGetLatestAppVersion.mockResolvedValueOnce(versionInfo('2.4.0'));

		const { result } = await renderHook(() => useAppUpdate(true), { wrapper });

		await waitFor(() => expect(result.current.info).not.toBeNull());
		expect(result.current.status).toBe('none');
	});

	it('reminds again once 3 days have passed', async () => {
		await storeDismissed('2.4.0', 4);
		mockedGetLatestAppVersion.mockResolvedValueOnce(versionInfo('2.4.0'));

		const { result } = await renderHook(() => useAppUpdate(true), { wrapper });

		await waitFor(() => expect(result.current.status).toBe('available'));
	});

	it('shows a newer version even if a previous one was dismissed', async () => {
		await storeDismissed('2.4.0', 0);
		mockedGetLatestAppVersion.mockResolvedValueOnce(versionInfo('2.5.0'));

		const { result } = await renderHook(() => useAppUpdate(true), { wrapper });

		await waitFor(() => expect(result.current.status).toBe('available'));
	});

	it('treats a corrupted dismissal as not dismissed', async () => {
		await AsyncStorage.setItem(DISMISSED_KEY, '{broken');
		mockedGetLatestAppVersion.mockResolvedValueOnce(versionInfo('2.4.0'));

		const { result } = await renderHook(() => useAppUpdate(true), { wrapper });

		await waitFor(() => expect(result.current.status).toBe('available'));
	});
});

describe('useCheckForUpdates', () => {
	const renderBoth = () => renderHook(() => ({ update: useAppUpdate(true), checker: useCheckForUpdates() }), { wrapper });

	it('reports an available update and shows the prompt even if it was dismissed', async () => {
		await storeDismissed('2.4.0', 0);
		mockedGetLatestAppVersion.mockResolvedValue(versionInfo('2.4.0'));

		const { result } = await renderBoth();

		await waitFor(() => expect(result.current.update.info).not.toBeNull());
		expect(result.current.update.status).toBe('none');

		let outcome: string | undefined;
		await act(async () => {
			outcome = await result.current.checker.check();
		});

		expect(outcome).toBe('available');
		await waitFor(() => expect(result.current.update.status).toBe('available'));
	});

	it('always asks the server, ignoring the cached version', async () => {
		queryClient.setQueryData(APP_VERSION_KEY, versionInfo('2.3.0'));
		mockedGetLatestAppVersion.mockResolvedValue(versionInfo('2.4.0'));

		const { result } = await renderHook(() => useCheckForUpdates(), { wrapper });

		let outcome: string | undefined;
		await act(async () => {
			outcome = await result.current.check();
		});

		expect(mockedGetLatestAppVersion).toHaveBeenCalled();
		expect(outcome).toBe('available');
	});

	it.each([
		['the latest version is installed', versionInfo('2.3.0')],
		['no version has been published', null],
	])('reports up-to-date when %s', async (_, info) => {
		mockedGetLatestAppVersion.mockResolvedValue(info);

		const { result } = await renderHook(() => useCheckForUpdates(), { wrapper });

		let outcome: string | undefined;
		await act(async () => {
			outcome = await result.current.check();
		});

		expect(outcome).toBe('up-to-date');
	});

	it('reports an error when the check fails', async () => {
		mockedGetLatestAppVersion.mockRejectedValue(new Error('Network error'));

		const { result } = await renderHook(() => useCheckForUpdates(), { wrapper });

		let outcome: string | undefined;
		await act(async () => {
			outcome = await result.current.check();
		});

		expect(outcome).toBe('error');
		expect(result.current.isChecking).toBe(false);
	});

	it('exposes isChecking while the request is in flight', async () => {
		let resolveRequest: (value: any) => void = () => undefined;
		mockedGetLatestAppVersion.mockReturnValue(new Promise(resolve => { resolveRequest = resolve; }));

		const { result } = await renderHook(() => useCheckForUpdates(), { wrapper });

		let pending: Promise<string> | undefined;
		await act(async () => {
			pending = result.current.check();
		});

		expect(result.current.isChecking).toBe(true);

		await act(async () => {
			resolveRequest(null);
			await pending;
		});

		expect(result.current.isChecking).toBe(false);
	});
});

describe('useUpdateInstaller', () => {
	const info = versionInfo('2.4.0');

	afterEach(() => {
		jest.restoreAllMocks();
	});

	it('downloads and installs the APK, exposing the progress while downloading', async () => {
		let reportProgress: (value: number) => void = () => undefined;
		let finishDownload: () => void = () => undefined;

		mockedDownloadAndInstallApk.mockImplementation((_url, _version, onProgress) => {
			reportProgress = onProgress;
			return new Promise(resolve => { finishDownload = () => resolve(); });
		});

		const { result } = await renderHook(() => useUpdateInstaller(info), { wrapper });

		let pending: Promise<void> | undefined;
		await act(async () => {
			pending = result.current.start();
		});

		expect(mockedDownloadAndInstallApk).toHaveBeenCalledWith(info.downloadUrl, '2.4.0', expect.any(Function));
		expect(result.current.isDownloading).toBe(true);
		expect(result.current.progress).toBe(0);

		await act(async () => {
			reportProgress(0.42);
		});

		expect(result.current.progress).toBe(0.42);

		await act(async () => {
			finishDownload();
			await pending;
		});

		expect(result.current.isDownloading).toBe(false);
		expect(result.current.progress).toBeNull();
	});

	it('ignores progress updates within the same percent', async () => {
		let reportProgress: (value: number) => void = () => undefined;
		mockedDownloadAndInstallApk.mockImplementation((_url, _version, onProgress) => {
			reportProgress = onProgress;
			return new Promise(() => {});
		});

		const { result } = await renderHook(() => useUpdateInstaller(info), { wrapper });

		await act(async () => {
			result.current.start();
		});

		await act(async () => {
			reportProgress(0.421);
			reportProgress(0.425);
		});

		expect(result.current.progress).toBe(0.421);
	});

	it('does not start a second download while one is in progress', async () => {
		mockedDownloadAndInstallApk.mockImplementation(() => new Promise(() => {}));

		const { result } = await renderHook(() => useUpdateInstaller(info), { wrapper });

		await act(async () => {
			result.current.start();
		});

		await act(async () => {
			result.current.start();
		});

		expect(mockedDownloadAndInstallApk).toHaveBeenCalledTimes(1);
	});

	it('shows a toast and opens the download link in the browser when the in-app update fails', async () => {
		const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});
		const openURL = jest.spyOn(Linking, 'openURL').mockResolvedValue(true);
		mockedDownloadAndInstallApk.mockRejectedValueOnce(new Error('Download failed'));

		const { result } = await renderHook(() => useUpdateInstaller(info), { wrapper });

		await act(async () => {
			await result.current.start();
		});

		expect(mockShowToast).toHaveBeenCalledWith('Download non riuscito, apro il browser', 'error');
		expect(openURL).toHaveBeenCalledWith(info.downloadUrl);
		expect(result.current.isDownloading).toBe(false);
		consoleLog.mockRestore();
	});

	it('opens the Play Store page instead of downloading in the Play build', async () => {
		mockedIsPlayDistribution.mockReturnValue(true);
		mockedOpenStorePage.mockResolvedValueOnce(undefined);

		const { result } = await renderHook(() => useUpdateInstaller(info), { wrapper });

		await act(async () => {
			await result.current.start();
		});

		expect(mockedOpenStorePage).toHaveBeenCalled();
		expect(mockedDownloadAndInstallApk).not.toHaveBeenCalled();
		expect(result.current.isDownloading).toBe(false);
	});
});