import { renderHook, waitFor, act } from '@testing-library/react-native';

import { getMyAchievements, getUnseenAchievements, getUserAchievements, markAchievementsSeen } from '@/api/achievement';
import { useMarkAchievementsSeen, useMyAchievements, useUnseenAchievements, useUserAchievements } from '@/hooks/use-achievement';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/achievement', () => ({
	getMyAchievements: jest.fn(),
	getUnseenAchievements: jest.fn(),
	getUserAchievements: jest.fn(),
	markAchievementsSeen: jest.fn(),
}));

const mockedGetMyAchievements = jest.mocked(getMyAchievements);
const mockedGetUnseenAchievements = jest.mocked(getUnseenAchievements);
const mockedGetUserAchievements = jest.mocked(getUserAchievements);
const mockedMarkAchievementsSeen = jest.mocked(markAchievementsSeen);

const ACHIEVEMENTS = [{ code: 'FIRST_MATCH' }, { code: 'TEN_GAMES' }] as any;

describe('use-achievement', () => {
	let wrapper: ReturnType<typeof createWrapper>['wrapper'];
	let queryClient: ReturnType<typeof createWrapper>['queryClient'];

	beforeEach(() => {
		jest.resetAllMocks();
		({ wrapper, queryClient } = createWrapper());
	});

	afterEach(() => {
		queryClient.clear();
	});

	describe('useMyAchievements', () => {
		it('loads my achievements under the [achievements, me] key', async () => {
			mockedGetMyAchievements.mockResolvedValue(ACHIEVEMENTS);

			const { result } = await renderHook(() => useMyAchievements(), { wrapper });

			await waitFor(() => expect(result.current.isSuccess).toBe(true));
			expect(result.current.data).toEqual(ACHIEVEMENTS);
			expect(queryClient.getQueryData(['achievements', 'me'])).toEqual(ACHIEVEMENTS);
		});

		it('exposes the error when the request fails', async () => {
			mockedGetMyAchievements.mockRejectedValue(new Error('boom'));

			const { result } = await renderHook(() => useMyAchievements(), { wrapper });

			await waitFor(() => expect(result.current.isError).toBe(true));
			expect(result.current.error).toEqual(new Error('boom'));
		});
	});

	describe('useUserAchievements', () => {
		it('loads the achievements of the given user', async () => {
			mockedGetUserAchievements.mockResolvedValue(ACHIEVEMENTS);

			const { result } = await renderHook(() => useUserAchievements('user-42'), { wrapper });

			await waitFor(() => expect(result.current.isSuccess).toBe(true));
			expect(mockedGetUserAchievements).toHaveBeenCalledWith('user-42');
			expect(queryClient.getQueryData(['achievements', 'user', 'user-42'])).toEqual(ACHIEVEMENTS);
		});

		it('does not fetch when disabled', async () => {
			const { result } = await renderHook(() => useUserAchievements('user-42', false), { wrapper });

			expect(result.current.fetchStatus).toBe('idle');
			expect(mockedGetUserAchievements).not.toHaveBeenCalled();
		});
	});

	describe('useUnseenAchievements', () => {
		it('loads the unseen achievements when enabled', async () => {
			mockedGetUnseenAchievements.mockResolvedValue(ACHIEVEMENTS);

			const { result } = await renderHook(() => useUnseenAchievements(true), { wrapper });

			await waitFor(() => expect(result.current.isSuccess).toBe(true));
			expect(queryClient.getQueryData(['achievements', 'unseen'])).toEqual(ACHIEVEMENTS);
		});

		it('waits until it gets enabled', async () => {
			mockedGetUnseenAchievements.mockResolvedValue(ACHIEVEMENTS);

			const { result, rerender } = await renderHook(({ enabled }: { enabled: boolean }) => useUnseenAchievements(enabled), {
				wrapper,
				initialProps: { enabled: false },
			});

			expect(mockedGetUnseenAchievements).not.toHaveBeenCalled();

			await rerender({ enabled: true });

			await waitFor(() => expect(result.current.isSuccess).toBe(true));
			expect(mockedGetUnseenAchievements).toHaveBeenCalledTimes(1);
		});
	});

	describe('useMarkAchievementsSeen', () => {
		it('sends the codes to the API', async () => {
			mockedMarkAchievementsSeen.mockResolvedValue(undefined);

			const { result } = await renderHook(() => useMarkAchievementsSeen(), { wrapper });

			await act(async () => {
				await result.current.mutateAsync(['FIRST_MATCH', 'TEN_GAMES']);
			});

			expect(mockedMarkAchievementsSeen).toHaveBeenCalledWith(['FIRST_MATCH', 'TEN_GAMES']);
		});

		it('rejects when the API fails', async () => {
			mockedMarkAchievementsSeen.mockRejectedValue(new Error('boom'));

			const { result } = await renderHook(() => useMarkAchievementsSeen(), { wrapper });

			await act(async () => {
				await expect(result.current.mutateAsync(['FIRST_MATCH'])).rejects.toThrow('boom');
			});
		});
	});
});