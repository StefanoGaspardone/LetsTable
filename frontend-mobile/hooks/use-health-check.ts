import { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';

const HEALTH_URL = `${process.env.EXPO_PUBLIC_API_URL ?? 'https://letstable.onrender.com/api/v1'}/health`;

const POLL_INTERVAL_MS = 10000;
const CHECK_TIMEOUT_MS = 8000;

export function useHealthCheck() {
	const [isHealthy, setIsHealthy] = useState(true);
	const [isChecking, setIsChecking] = useState(false);
	const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

	const checkHealth = useCallback(async () => {
		setIsChecking(true);
		
        try {
			await axios.get(HEALTH_URL, { timeout: CHECK_TIMEOUT_MS });
			setIsHealthy(true);
		} catch {
			setIsHealthy(false);
		} finally {
			setIsChecking(false);
		}
	}, []);

	useEffect(() => {
		checkHealth();

		intervalRef.current = setInterval(checkHealth, POLL_INTERVAL_MS);

		return () => {
			if(intervalRef.current) clearInterval(intervalRef.current);
		}
	}, [checkHealth]);

	return { isHealthy, isChecking, retryNow: checkHealth };
}