import { apiClient } from '@/api/client';

import { PageDTO } from '@/types/page';

import { Wishlist, WishlistItem, WishlistItemStatus, WishlistMember } from '@/types/wishlist';

export const listMyWishlists = async(): Promise<Wishlist[]> => {
    const { data } = await apiClient.get<Wishlist[]>('/wishlists');
    return data;
}

export const getWishlistById = async (wishlistId: string): Promise<Wishlist> => {
    const { data } = await apiClient.get<Wishlist>(`/wishlists/${wishlistId}`);
    return data;
}

export const createWishlist = async (name: string, isShared: boolean): Promise<Wishlist> => {
    const { data } = await apiClient.post<Wishlist>('/wishlists', { name, isShared });
    return data;
}

export const deleteWishlist = async (wishlistId: string): Promise<void> => {
    await apiClient.delete(`/wishlists/${wishlistId}`);
}

export const listWishlistItems = async (wishlistId: string, page: number, size = 20): Promise<PageDTO<WishlistItem>> => {
    const { data } = await apiClient.get<PageDTO<WishlistItem>>(`/wishlists/${wishlistId}/items`, {
        params: { page, size },
    });
    return data;
}

export const addItemToWishlist = async (wishlistId: string, gameId: string): Promise<WishlistItem> => {
    const { data } = await apiClient.post<WishlistItem>(`/wishlists/${wishlistId}/items`, { gameId });
    return data;
}

export const removeItemFromWishlist = async (wishlistId: string, itemId: string): Promise<void> => {
    await apiClient.delete(`/wishlists/${wishlistId}/items/${itemId}`);
}

export const getItemStatusInWishlist = async (wishlistId: string, gameId: string): Promise<WishlistItemStatus> => {
    const { data } = await apiClient.get<WishlistItemStatus>(`/wishlists/${wishlistId}/items/status`, {
        params: { gameId },
    });
    return data;
}

export const listWishlistMembers = async (wishlistId: string): Promise<WishlistMember[]> => {
    const { data } = await apiClient.get<WishlistMember[]>(`/wishlists/${wishlistId}/members`);
    return data;
}

export const addMemberToWishlist = async (wishlistId: string, userId: string): Promise<WishlistMember> => {
    const { data } = await apiClient.post<WishlistMember>(`/wishlists/${wishlistId}/members`, { userId });
    return data;
}

export const removeMemberFromWishlist = async (wishlistId: string, memberUserId: string): Promise<void> => {
    await apiClient.delete(`/wishlists/${wishlistId}/members/${memberUserId}`);
}

export const leaveWishlist = async (wishlistId: string): Promise<void> => {
    await apiClient.post(`/wishlists/${wishlistId}/leave`);
}