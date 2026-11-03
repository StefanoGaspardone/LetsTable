import { apiClient } from '@/api/client';
import { getMyAchievements, getUnseenAchievements, getUserAchievements, markAchievementsSeen } from '@/api/achievement';

jest.mock('@/api/client', () => ({
	apiClient: {
		get: jest.fn(),
		post: jest.fn(),
	},
}));

const mockedGet = apiClient.get as jest.MockedFunction<typeof apiClient.get>;
const mockedPost = apiClient.post as jest.MockedFunction<typeof apiClient.post>;

const ACHIEVEMENTS = [{ code: 'FIRST_MATCH' }, { code: 'TEN_GAMES' }] as any;

describe('achievement API', () => {
	beforeEach(() => {
		jest.clearAllMocks();
		mockedGet.mockReset();
		mockedPost.mockReset();
	});

	it('fetches my achievements', async () => {
		mockedGet.mockResolvedValue({ data: ACHIEVEMENTS });

		await expect(getMyAchievements()).resolves.toEqual(ACHIEVEMENTS);
		expect(mockedGet).toHaveBeenCalledWith('/achievements/me');
	});

	it('fetches the unseen achievements', async () => {
		mockedGet.mockResolvedValue({ data: ACHIEVEMENTS });

		await expect(getUnseenAchievements()).resolves.toEqual(ACHIEVEMENTS);
		expect(mockedGet).toHaveBeenCalledWith('/achievements/unseen');
	});

	it('fetches the achievements of another user', async () => {
		mockedGet.mockResolvedValue({ data: ACHIEVEMENTS });

		await expect(getUserAchievements('user-42')).resolves.toEqual(ACHIEVEMENTS);
		expect(mockedGet).toHaveBeenCalledWith('/users/user-42/achievements');
	});

	it('marks the given achievements as seen', async () => {
		mockedPost.mockResolvedValue({ data: undefined });

		await expect(markAchievementsSeen(['FIRST_MATCH', 'TEN_GAMES'])).resolves.toBeUndefined();
		expect(mockedPost).toHaveBeenCalledWith('/achievements/seen', { codes: ['FIRST_MATCH', 'TEN_GAMES'] });
	});

	it('propagates API errors', async () => {
		mockedGet.mockRejectedValue(new Error('Network Error'));

		await expect(getMyAchievements()).rejects.toThrow('Network Error');
	});
});