import { apiClient } from '@/api/client';
import {
	listMyWishlists,
	getWishlistById,
	createWishlist,
	deleteWishlist,
	listWishlistItems,
	addItemToWishlist,
	removeItemFromWishlist,
	getItemStatusInWishlist,
	listWishlistMembers,
	addMemberToWishlist,
	removeMemberFromWishlist,
	leaveWishlist,
} from '@/api/wishlist';

jest.mock('@/api/client', () => ({
	apiClient: {
		get: jest.fn(),
		post: jest.fn(),
		delete: jest.fn(),
	},
}));

const mockedGet = apiClient.get as jest.MockedFunction<typeof apiClient.get>;
const mockedPost = apiClient.post as jest.MockedFunction<typeof apiClient.post>;
const mockedDelete = apiClient.delete as jest.MockedFunction<typeof apiClient.delete>;

describe('wishlist API', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('listMyWishlists', () => {
		it('calls GET /wishlists and returns the response data', async () => {
			const responseData = [{ id: 'w-1', name: 'My wishlist' }];
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await listMyWishlists();

			expect(mockedGet).toHaveBeenCalledWith('/wishlists');
			expect(result).toEqual(responseData);
		});
	});

	describe('getWishlistById', () => {
		it('calls GET /wishlists/:wishlistId and returns the response data', async () => {
			const responseData = { id: 'w-1', name: 'My wishlist' };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getWishlistById('w-1');

			expect(mockedGet).toHaveBeenCalledWith('/wishlists/w-1');
			expect(result).toEqual(responseData);
		});
	});

	describe('createWishlist', () => {
		it('posts to /wishlists with name and isShared, and returns the response data', async () => {
			const responseData = { id: 'w-1', name: 'Compleanno', isShared: true };
			mockedPost.mockResolvedValueOnce({ data: responseData } as any);

			const result = await createWishlist('Compleanno', true);

			expect(mockedPost).toHaveBeenCalledWith('/wishlists', { name: 'Compleanno', isShared: true });
			expect(result).toEqual(responseData);
		});
	});

	describe('deleteWishlist', () => {
		it('calls DELETE on /wishlists/:wishlistId', async () => {
			mockedDelete.mockResolvedValueOnce({} as any);

			await deleteWishlist('w-1');

			expect(mockedDelete).toHaveBeenCalledWith('/wishlists/w-1');
		});
	});

	describe('listWishlistItems', () => {
		it('calls GET /wishlists/:wishlistId/items with page and default size', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await listWishlistItems('w-1', 0);

			expect(mockedGet).toHaveBeenCalledWith('/wishlists/w-1/items', {
				params: { page: 0, size: 20 },
			});
		});

		it('uses the given size instead of the default when provided', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await listWishlistItems('w-1', 1, 5);

			expect(mockedGet).toHaveBeenCalledWith('/wishlists/w-1/items', {
				params: { page: 1, size: 5 },
			});
		});
	});

	describe('addItemToWishlist', () => {
		it('posts to /wishlists/:wishlistId/items with gameId and returns the response data', async () => {
			const responseData = { id: 'item-1', gameId: 'game-1' };
			mockedPost.mockResolvedValueOnce({ data: responseData } as any);

			const result = await addItemToWishlist('w-1', 'game-1');

			expect(mockedPost).toHaveBeenCalledWith('/wishlists/w-1/items', { gameId: 'game-1' });
			expect(result).toEqual(responseData);
		});
	});

	describe('removeItemFromWishlist', () => {
		it('calls DELETE on /wishlists/:wishlistId/items/:itemId', async () => {
			mockedDelete.mockResolvedValueOnce({} as any);

			await removeItemFromWishlist('w-1', 'item-1');

			expect(mockedDelete).toHaveBeenCalledWith('/wishlists/w-1/items/item-1');
		});
	});

	describe('getItemStatusInWishlist', () => {
		it('calls GET /wishlists/:wishlistId/items/status with gameId param and returns the response data', async () => {
			const responseData = { inWishlist: true, itemId: 'item-1' };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getItemStatusInWishlist('w-1', 'game-1');

			expect(mockedGet).toHaveBeenCalledWith('/wishlists/w-1/items/status', {
				params: { gameId: 'game-1' },
			});
			expect(result).toEqual(responseData);
		});
	});

	describe('listWishlistMembers', () => {
		it('calls GET /wishlists/:wishlistId/members and returns the response data', async () => {
			const responseData = [{ id: 'member-1' }];
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await listWishlistMembers('w-1');

			expect(mockedGet).toHaveBeenCalledWith('/wishlists/w-1/members');
			expect(result).toEqual(responseData);
		});
	});

	describe('addMemberToWishlist', () => {
		it('posts to /wishlists/:wishlistId/members with userId and returns the response data', async () => {
			const responseData = { id: 'member-1', userId: 'user-2' };
			mockedPost.mockResolvedValueOnce({ data: responseData } as any);

			const result = await addMemberToWishlist('w-1', 'user-2');

			expect(mockedPost).toHaveBeenCalledWith('/wishlists/w-1/members', { userId: 'user-2' });
			expect(result).toEqual(responseData);
		});
	});

	describe('removeMemberFromWishlist', () => {
		it('calls DELETE on /wishlists/:wishlistId/members/:memberUserId', async () => {
			mockedDelete.mockResolvedValueOnce({} as any);

			await removeMemberFromWishlist('w-1', 'user-2');

			expect(mockedDelete).toHaveBeenCalledWith('/wishlists/w-1/members/user-2');
		});
	});

	describe('leaveWishlist', () => {
		it('posts to /wishlists/:wishlistId/leave', async () => {
			mockedPost.mockResolvedValueOnce({} as any);

			await leaveWishlist('w-1');

			expect(mockedPost).toHaveBeenCalledWith('/wishlists/w-1/leave');
		});
	});
});