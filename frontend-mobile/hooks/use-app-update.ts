import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { Linking } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Application from 'expo-application';
import { useQuery, useQueryClient } from '@tanstack/react-query';

import { getLatestAppVersion } from '@/api/app-version';

import { compareVersions, downloadAndInstallApk, isPlayDistribution, openStorePage } from '@/lib/app-version';

import { useToast } from '@/contexts/toast-context';

import { AppVersionInfo } from '@/types/app-version';

export type AppUpdateStatus = 'none' | 'available' | 'required';

interface DismissedUpdate {
	version: string;
	at: number;
}

const DISMISSED_KEY = '@app_update_dismissed';
const REMIND_AFTER_MS = 3 * 24 * 60 * 60 * 1000;
const STALE_TIME_MS = 15 * 60 * 1000;

export const APP_VERSION_KEY = ['app', 'version'];
export const CURRENT_APP_VERSION = Application.nativeApplicationVersion ?? '0.0.0';

let isPromptForced = false;
const forcedPromptListeners = new Set<() => void>();

const setPromptForced = (value: boolean) => {
	isPromptForced = value;
	forcedPromptListeners.forEach(listener => listener());
}

const subscribeForcedPrompt = (listener: () => void) => {
	forcedPromptListeners.add(listener);
	return () => {
		forcedPromptListeners.delete(listener);
	}
}

const getForcedPrompt = () => isPromptForced;

export const useAppUpdate = (enabled: boolean) => {
	const { data: info } = useQuery({
		queryKey: APP_VERSION_KEY,
		queryFn: getLatestAppVersion,
		staleTime: STALE_TIME_MS,
		enabled,
	});
    const isForced = useSyncExternalStore(subscribeForcedPrompt, getForcedPrompt);

	const [dismissed, setDismissed] = useState<DismissedUpdate | null>(null);
	const [isDismissedLoaded, setIsDismissedLoaded] = useState(false);

	useEffect(() => {
		AsyncStorage.getItem(DISMISSED_KEY)
			.then(value => setDismissed(value ? JSON.parse(value) as DismissedUpdate : null))
			.catch(() => setDismissed(null))
			.finally(() => setIsDismissedLoaded(true));
	}, []);

	const status = useMemo<AppUpdateStatus>(() => {
		if(!info) return 'none';
		if(compareVersions(CURRENT_APP_VERSION, info.minSupportedVersion) < 0) return 'required';
		if(compareVersions(CURRENT_APP_VERSION, info.latestVersion) >= 0) return 'none';

		if(isForced) return 'available';
		if(!isDismissedLoaded) return 'none';

		const isSnoozed = dismissed?.version === info.latestVersion && Date.now() - dismissed.at < REMIND_AFTER_MS;
		return isSnoozed ? 'none' : 'available';
	}, [info, dismissed, isDismissedLoaded, isForced]);

	const dismiss = useCallback(() => {
        setPromptForced(false);
		
        if(!info) return;

		const value: DismissedUpdate = { version: info.latestVersion, at: Date.now() };

		setDismissed(value);
		AsyncStorage.setItem(DISMISSED_KEY, JSON.stringify(value)).catch(error => console.log('Failed to persist dismissed update:', error));
	}, [info]);

	return { status, info: info ?? null, currentVersion: CURRENT_APP_VERSION, dismiss };
}

export const useUpdateInstaller = (info: AppVersionInfo) => {
	const { showToast } = useToast();

	const [progress, setProgress] = useState<number | null>(null);
	const lastPercentRef = useRef(-1);

	const handleProgress = (value: number) => {
		const percent = Math.floor(value * 100);
		if(percent === lastPercentRef.current) return;

		lastPercentRef.current = percent;
		setProgress(value);
	}

	const start = async () => {
		if(progress !== null) return;

		if(isPlayDistribution()) {
			await openStorePage();
			return;
		}

		lastPercentRef.current = -1;
		setProgress(0);

		try {
			await downloadAndInstallApk(info.downloadUrl, info.latestVersion, handleProgress);
		} catch(error) {
			console.log('In-app update failed:', error);
			showToast('Download non riuscito, apro il browser', 'error');
			Linking.openURL(info.downloadUrl).catch(() => undefined);
		} finally {
			setProgress(null);
		}
	}

	return { start, progress, isDownloading: progress !== null };
}

export type UpdateCheckResult = 'available' | 'up-to-date' | 'error';

export const useCheckForUpdates = () => {
	const queryClient = useQueryClient();
	const [isChecking, setIsChecking] = useState(false);

	const check = useCallback(async (): Promise<UpdateCheckResult> => {
		setIsChecking(true);

		try {
			const info = await queryClient.fetchQuery({ queryKey: APP_VERSION_KEY, queryFn: getLatestAppVersion, staleTime: 0 });

			if(info && compareVersions(CURRENT_APP_VERSION, info.latestVersion) < 0) {
				setPromptForced(true);
				return 'available';
			}

			return 'up-to-date';
		} catch {
			return 'error';
		} finally {
			setIsChecking(false);
		}
	}, [queryClient]);

	return { check, isChecking };
}