import { useEffect, useState } from 'react';

export const useDebounce = <T>(value: T, delayMs = 400): T => {
	const [debounced, setDebounced] = useState(value);

	useEffect(() => {
		const timeout = setTimeout(() => setDebounced(value), delayMs);
		return () => clearTimeout(timeout);
	}, [value, delayMs]);

	return debounced;
}