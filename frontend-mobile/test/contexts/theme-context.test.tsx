import { ReactNode } from 'react';
import { renderHook, waitFor, act } from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { ThemeProvider, useTheme } from '@/contexts/theme-context';

const mockSetColorScheme = jest.fn();

jest.mock('@react-native-async-storage/async-storage', () => ({
	getItem: jest.fn(),
	setItem: jest.fn(() => Promise.resolve()),
}));

jest.mock('nativewind', () => ({
	useColorScheme: () => ({ setColorScheme: mockSetColorScheme }),
}));

const mockedGetItem = AsyncStorage.getItem as jest.MockedFunction<typeof AsyncStorage.getItem>;
const mockedSetItem = AsyncStorage.setItem as jest.MockedFunction<typeof AsyncStorage.setItem>;

const wrapper = ({ children }: { children: ReactNode }) => (
	<ThemeProvider>{children}</ThemeProvider>
);

describe('ThemeProvider / useTheme', () => {
	beforeEach(() => {
		jest.clearAllMocks();
		mockedSetItem.mockResolvedValue(undefined);
	});

	it('defaults to "light" while loading', async () => {
		mockedGetItem.mockImplementation(() => new Promise(() => {}));

		const { result } = await renderHook(() => useTheme(), { wrapper });

		expect(result.current.themePreference).toBe('light');
		expect(result.current.isLoading).toBe(true);
	});

	it('loads a previously stored preference from AsyncStorage', async () => {
		mockedGetItem.mockResolvedValueOnce('dark');

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current.themePreference).toBe('dark');
		expect(mockedGetItem).toHaveBeenCalledWith('@theme_preference');
	});

	it('applies the stored preference to NativeWind', async () => {
		mockedGetItem.mockResolvedValueOnce('dark');

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(mockSetColorScheme).toHaveBeenCalledWith('dark');
	});

	it('defaults to "light" when nothing is stored', async () => {
		mockedGetItem.mockResolvedValueOnce(null);

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current.themePreference).toBe('light');
	});

	it('ignores an invalid stored value and falls back to "light"', async () => {
		mockedGetItem.mockResolvedValueOnce('invalid-value');

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current.themePreference).toBe('light');
	});

	it('falls back to "light" and stops loading when reading the preference fails', async () => {
		const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});
		mockedGetItem.mockRejectedValueOnce(new Error('Storage unavailable'));

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current.themePreference).toBe('light');
		expect(consoleLog).toHaveBeenCalledWith('Failed to load theme preference:', expect.any(Error));
		consoleLog.mockRestore();
	});

	it('updates the preference, applies it and persists it to AsyncStorage', async () => {
		mockedGetItem.mockResolvedValueOnce('system');

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		await act(() => {
			result.current.setThemePreference('dark');
		});

		expect(result.current.themePreference).toBe('dark');
		expect(mockSetColorScheme).toHaveBeenLastCalledWith('dark');
		expect(mockedSetItem).toHaveBeenCalledWith('@theme_preference', 'dark');
	});

	it('updates the preference to "light"', async () => {
		mockedGetItem.mockResolvedValueOnce('system');

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		await act(() => {
			result.current.setThemePreference('light');
		});

		expect(result.current.themePreference).toBe('light');
		expect(mockedSetItem).toHaveBeenCalledWith('@theme_preference', 'light');
	});

	it('keeps the new preference even when saving it fails', async () => {
		const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});
		mockedGetItem.mockResolvedValueOnce('light');
		mockedSetItem.mockRejectedValueOnce(new Error('Disk full'));

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		await act(async () => {
			result.current.setThemePreference('dark');
		});

		expect(result.current.themePreference).toBe('dark');
		await waitFor(() => expect(consoleLog).toHaveBeenCalledWith('Failed to save theme preference:', expect.any(Error)));
		consoleLog.mockRestore();
	});

	it('throws when used without a provider', async () => {
		await expect(renderHook(() => useTheme())).rejects.toThrow(
			'useTheme must be used within a ThemeProvider'
		);
	});
});