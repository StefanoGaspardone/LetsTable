import { ReactNode } from 'react';
import { renderHook, waitFor, act } from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { ThemeProvider, useTheme } from '@/contexts/theme-context';

jest.mock('@react-native-async-storage/async-storage', () => ({
	getItem: jest.fn(),
	setItem: jest.fn(),
}));

const mockedGetItem = AsyncStorage.getItem as jest.MockedFunction<typeof AsyncStorage.getItem>;
const mockedSetItem = AsyncStorage.setItem as jest.MockedFunction<typeof AsyncStorage.setItem>;

const wrapper = ({ children }: { children: ReactNode }) => (
	<ThemeProvider>{children}</ThemeProvider>
);

describe('ThemeProvider / useTheme', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	it('defaults to "system" while loading', async () => {
		mockedGetItem.mockImplementation(() => new Promise(() => {}));

		const { result } = await renderHook(() => useTheme(), { wrapper });

		expect(result.current.themePreference).toBe('system');
		expect(result.current.isLoading).toBe(true);
	});

	it('loads a previously stored preference from AsyncStorage', async () => {
		mockedGetItem.mockResolvedValueOnce('dark');

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current.themePreference).toBe('dark');
		expect(mockedGetItem).toHaveBeenCalledWith('@theme_preference');
	});

	it('defaults to "system" when nothing is stored', async () => {
		mockedGetItem.mockResolvedValueOnce(null);

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current.themePreference).toBe('system');
	});

	it('ignores an invalid stored value and falls back to "system"', async () => {
		mockedGetItem.mockResolvedValueOnce('invalid-value');

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current.themePreference).toBe('system');
	});

	it('accepts "light" as a valid stored preference', async () => {
		mockedGetItem.mockResolvedValueOnce('light');

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current.themePreference).toBe('light');
	});

	it('sets isLoading to false after the initial load, regardless of stored value', async () => {
        mockedGetItem.mockResolvedValueOnce(null);

        const { result } = await renderHook(() => useTheme(), { wrapper });

        await waitFor(() => expect(result.current.isLoading).toBe(false));
    });

	it('updates the preference and persists it to AsyncStorage', async () => {
		mockedGetItem.mockResolvedValueOnce('system');

		const { result } = await renderHook(() => useTheme(), { wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		await act(() => {
			result.current.setThemePreference('dark');
		});

		expect(result.current.themePreference).toBe('dark');
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

	it('throws when used without a provider', async () => {
		await expect(renderHook(() => useTheme())).rejects.toThrow(
			'useTheme must be used within a ThemeProvider'
		);
	});
});