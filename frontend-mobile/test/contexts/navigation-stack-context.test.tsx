import { ReactNode } from 'react';
import { renderHook, act } from '@testing-library/react-native';
import { BackHandler } from 'react-native';
import { router as expoRouter } from 'expo-router';

import { NavigationStackProvider, useNavigationStack } from '@/contexts/navigation-stack-context';

jest.mock('expo-router', () => ({
	router: {
		push: jest.fn(),
		replace: jest.fn(),
	},
}));

let mockIsAuthenticated = true;

jest.mock('@/contexts/auth-context', () => ({
	useAuth: () => ({ isAuthenticated: mockIsAuthenticated }),
}));

const mockedPush = jest.mocked(expoRouter.push);
const mockedReplace = jest.mocked(expoRouter.replace);

const HOME_ROUTE = '/(tabs)/home';
const WELCOME_ROUTE = '/(auth)/welcome';

const wrapper = ({ children }: { children: ReactNode }) => (
	<NavigationStackProvider>{children}</NavigationStackProvider>
);

const renderNavigation = () => renderHook(() => useNavigationStack(), { wrapper });

describe('NavigationStackProvider / useNavigationStack', () => {
	beforeEach(() => {
		jest.clearAllMocks();
		jest.restoreAllMocks();
		mockIsAuthenticated = true;
	});

	describe('push', () => {
		it('calls expo-router push with the given href', async () => {
			const { result } = await renderNavigation();

			await act(async () => {
				result.current.push('/game/13');
			});

			expect(mockedPush).toHaveBeenCalledWith('/game/13');
		});

		it('accepts a pathname/params object', async () => {
			const { result } = await renderNavigation();
			const href = { pathname: '/(auth)/activate', params: { identifier: 'test@example.com' } };

			await act(async () => {
				result.current.push(href);
			});

			expect(mockedPush).toHaveBeenCalledWith(href);
		});
	});

	describe('back', () => {
		it('pops the current entry and navigates to the previous one', async () => {
			const { result } = await renderNavigation();

			await act(async () => {
				result.current.push('/first');
				result.current.push('/second');
				result.current.push('/third');
			});
			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenCalledWith('/second');
		});

		it('supports repeated back navigation down to home', async () => {
			const { result } = await renderNavigation();

			await act(async () => {
				result.current.push('/a');
				result.current.push('/b');
				result.current.push('/c');
			});

			await act(async () => {
				result.current.back();
			});
			expect(mockedReplace).toHaveBeenLastCalledWith('/b');

			await act(async () => {
				result.current.back();
			});
			expect(mockedReplace).toHaveBeenLastCalledWith('/a');

			await act(async () => {
				result.current.back();
			});
			expect(mockedReplace).toHaveBeenLastCalledWith(HOME_ROUTE);
		});

		it('falls back to home when the stack is empty and the user is authenticated', async () => {
			const { result } = await renderNavigation();

			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenCalledWith(HOME_ROUTE);
		});

		it('falls back to welcome when the stack is empty and the user is logged out', async () => {
			mockIsAuthenticated = false;
			const { result } = await renderNavigation();

			await act(async () => {
				result.current.push('/(auth)/login');
			});
			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenCalledWith(WELCOME_ROUTE);
		});

		it('uses the latest authentication state for the fallback', async () => {
			const { result, rerender } = await renderNavigation();

			mockIsAuthenticated = false;
			await rerender({});

			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenCalledWith(WELCOME_ROUTE);
		});
	});

	describe('replace', () => {
		it('calls expo-router replace with the given href', async () => {
			const { result } = await renderNavigation();

			await act(async () => {
				result.current.replace('/(auth)/login');
			});

			expect(mockedReplace).toHaveBeenCalledWith('/(auth)/login');
		});

		it('resets the stack to just the new href, so back goes to the fallback', async () => {
			const { result } = await renderNavigation();

			await act(async () => {
				result.current.push('/game/1');
				result.current.push('/game/2');
				result.current.replace('/wishlist/1');
			});
			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenLastCalledWith(HOME_ROUTE);
		});

		it.each([HOME_ROUTE, WELCOME_ROUTE])('empties the stack when replacing with the root route %s', async (route) => {
			let capturedHandler: (() => boolean) | undefined;
			jest.spyOn(BackHandler, 'addEventListener').mockImplementation((_event, handler) => {
				capturedHandler = handler as () => boolean;
				return { remove: jest.fn() };
			});

			const { result } = await renderNavigation();

			await act(async () => {
				result.current.push('/game/1');
				result.current.replace(route);
			});

			expect(capturedHandler?.()).toBe(false);
		});
	});

	describe('dismissAll', () => {
		it('clears the stack without navigating, so a later back() goes straight to home', async () => {
			const { result } = await renderNavigation();

			await act(async () => {
				result.current.push('/a');
				result.current.push('/b');
			});

			mockedPush.mockClear();
			mockedReplace.mockClear();

			await act(async () => {
				result.current.dismissAll();
			});

			expect(mockedPush).not.toHaveBeenCalled();
			expect(mockedReplace).not.toHaveBeenCalled();

			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenCalledWith(HOME_ROUTE);
		});
	});

	describe('hardware back button', () => {
		let capturedHandler: (() => boolean) | undefined;
		const removeMock = jest.fn();

		beforeEach(() => {
			capturedHandler = undefined;
			jest.spyOn(BackHandler, 'addEventListener').mockImplementation((_event, handler) => {
				capturedHandler = handler as () => boolean;
				return { remove: removeMock };
			});
		});

		it('registers a hardwareBackPress listener on mount', async () => {
			await renderNavigation();

			expect(BackHandler.addEventListener).toHaveBeenCalledWith('hardwareBackPress', expect.any(Function));
		});

		it('navigates back and consumes the event when the stack is not empty', async () => {
			const { result } = await renderNavigation();

			await act(async () => {
				result.current.push('/a');
				result.current.push('/b');
			});

			mockedReplace.mockClear();

			let handled: boolean | undefined;
			await act(async () => {
				handled = capturedHandler?.();
			});

			expect(handled).toBe(true);
			expect(mockedReplace).toHaveBeenCalledWith('/a');
		});

		it('lets the system handle the event when the stack is empty', async () => {
			await renderNavigation();

			expect(capturedHandler?.()).toBe(false);
			expect(mockedReplace).not.toHaveBeenCalled();
		});

		it('removes the listener on unmount', async () => {
			const { unmount } = await renderNavigation();

			await act(async () => {
				await unmount();
			});

			expect(removeMock).toHaveBeenCalled();
		});
	});

	describe('useNavigationStack outside provider', () => {
		it('throws when used without a provider', async () => {
			await expect(renderHook(() => useNavigationStack())).rejects.toThrow(
				'useNavigationStack must be used within a NavigationStackProvider'
			);
		});
	});
});