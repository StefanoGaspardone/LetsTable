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

const mockedPush = expoRouter.push as jest.MockedFunction<typeof expoRouter.push>;
const mockedReplace = expoRouter.replace as jest.MockedFunction<typeof expoRouter.replace>;

const wrapper = ({ children }: { children: ReactNode }) => (
	<NavigationStackProvider>{children}</NavigationStackProvider>
);

describe('NavigationStackProvider / useNavigationStack', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('push', () => {
		it('calls expo-router push with the given href', async () => {
			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.push('/game/13');
			});

			expect(mockedPush).toHaveBeenCalledWith('/game/13');
		});

		it('accepts a pathname/params object', async () => {
			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.push({ pathname: '/(auth)/activate', params: { identifier: 'test@example.com' } });
			});

			expect(mockedPush).toHaveBeenCalledWith({ pathname: '/(auth)/activate', params: { identifier: 'test@example.com' } });
		});

		it('adds the href to the internal stack so a later back() returns to the previous entry', async () => {
			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.push('/game/1');
			});
			await act(async () => {
				result.current.push('/game/2');
			});
			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenCalledWith('/game/1');
		});
	});

	describe('replace', () => {
		it('calls expo-router replace with the given href', async () => {
			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.replace('/(auth)/login');
			});

			expect(mockedReplace).toHaveBeenCalledWith('/(auth)/login');
		});

		it('resets the internal stack to just the new href', async () => {
			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.push('/game/1');
			});
			await act(async () => {
				result.current.push('/game/2');
			});
			await act(async () => {
				result.current.replace('/home');
			});
			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenLastCalledWith('/(tabs)/home');
		});
	});

	describe('back', () => {
		it('pops the current entry and navigates to the previous one', async () => {
			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.push('/first');
			});
			await act(async () => {
				result.current.push('/second');
			});
			await act(async () => {
				result.current.push('/third');
			});
			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenCalledWith('/second');
		});

		it('navigates to home when there is only one entry left in the stack', async () => {
			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.push('/only-entry');
			});
			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenCalledWith('/(tabs)/home');
		});

		it('navigates to home when the stack is already empty', async () => {
			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenCalledWith('/(tabs)/home');
		});

		it('supports repeated back navigation through multiple pushes', async () => {
			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.push('/a');
			});
			await act(async () => {
				result.current.push('/b');
			});
			await act(async () => {
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
			expect(mockedReplace).toHaveBeenLastCalledWith('/(tabs)/home');
		});
	});

	describe('dismissAll', () => {
		it('clears the internal stack without navigating', async () => {
			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.push('/a');
			});
			await act(async () => {
				result.current.push('/b');
			});

			mockedPush.mockClear();
			mockedReplace.mockClear();

			await act(async () => {
				result.current.dismissAll();
			});

			expect(mockedPush).not.toHaveBeenCalled();
			expect(mockedReplace).not.toHaveBeenCalled();
		});

		it('leaves the stack empty so a later back() goes straight to home', async () => {
			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.push('/a');
			});
			await act(async () => {
				result.current.push('/b');
			});
			await act(async () => {
				result.current.dismissAll();
			});
			await act(async () => {
				result.current.back();
			});

			expect(mockedReplace).toHaveBeenCalledWith('/(tabs)/home');
		});
	});

	describe('hardware back button', () => {
		it('registers a hardwareBackPress listener on mount', async () => {
			const addEventListenerSpy = jest.spyOn(BackHandler, 'addEventListener');

			await renderHook(() => useNavigationStack(), { wrapper });

			expect(addEventListenerSpy).toHaveBeenCalledWith('hardwareBackPress', expect.any(Function));
		});

		it('calls back() when the hardware back button is pressed', async () => {
			let capturedHandler: (() => boolean) | undefined;

			jest.spyOn(BackHandler, 'addEventListener').mockImplementation((_event, handler) => {
				capturedHandler = handler as () => boolean;
				return { remove: jest.fn() };
			});

			const { result } = await renderHook(() => useNavigationStack(), { wrapper });

			await act(async () => {
				result.current.push('/a');
			});
			await act(async () => {
				result.current.push('/b');
			});

			mockedReplace.mockClear();

			await act(async () => {
				capturedHandler?.();
			});

			expect(mockedReplace).toHaveBeenCalledWith('/a');
		});

        it('removes the listener on unmount', async () => {
            const removeMock = jest.fn();
            jest.spyOn(BackHandler, 'addEventListener').mockReturnValue({ remove: removeMock } as any);

            const { unmount } = await renderHook(() => useNavigationStack(), { wrapper });

            await act(async () => {
                unmount();
            });

            expect(removeMock).toHaveBeenCalled();
        });
	});

	describe('useNavigationStack outside provider', () => {
		it('throws when used without a provider', async () => {
			const { result } = await renderHook(() => {
				try {
					return useNavigationStack();
				} catch(error) {
					return error;
				}
			});

			expect(result.current).toBeInstanceOf(Error);
			expect((result.current as Error).message).toBe('useNavigationStack must be used within a NavigationStackProvider');
		});
	});
});