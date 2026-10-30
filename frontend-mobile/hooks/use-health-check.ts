import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import axios from 'axios';

import { API_URL, REQUEST_TIMEOUT_MS, setNetworkErrorHandler } from '@/api/client';

const HEALTH_URL = `${API_URL}/health`;
const POLL_INTERVAL_MS = 10000;

export const useHealthCheck = () => {
	const [isHealthy, setIsHealthy] = useState(true);
	const [isChecking, setIsChecking] = useState(false);
	const inFlightRef = useRef(false);

	const checkHealth = useCallback(async () => {
		if(inFlightRef.current) return;

		inFlightRef.current = true;
		setIsChecking(true);

		try {
			await axios.get(HEALTH_URL, { timeout: REQUEST_TIMEOUT_MS });
			setIsHealthy(true);
		} catch {
			setIsHealthy(false);
		} finally {
			inFlightRef.current = false;
			setIsChecking(false);
		}
	}, []);

	useEffect(() => {
		checkHealth();

		const subscription = AppState.addEventListener('change', status => {
			if(status === 'active') checkHealth();
		});

		setNetworkErrorHandler(checkHealth);

		return () => {
			subscription.remove();
			setNetworkErrorHandler(null);
		}
	}, [checkHealth]);

	useEffect(() => {
		if(isHealthy) return;

		const interval = setInterval(checkHealth, POLL_INTERVAL_MS);
		return () => clearInterval(interval);
	}, [isHealthy, checkHealth]);

	return { isHealthy, isChecking, retryNow: checkHealth };
}