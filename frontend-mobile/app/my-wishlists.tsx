import { View, FlatList, ActivityIndicator, Pressable } from 'react-native';
import { router } from 'expo-router';
import { X, Users, Lock, Heart } from 'lucide-react-native';
import { useQueryClient } from '@tanstack/react-query';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import CreatePlaylistDialog from '@/components/create-playlist-dialog';

import { useMyWishlists } from '@/hooks/use-wishlist';

import { createWishlist } from '@/api/wishlist';

export default function MyWishlistsScreen() {
	const { data: wishlists, isLoading } = useMyWishlists();
	const queryClient = useQueryClient();

	const handleCreatePlaylist = async (name: string) => {
		await createWishlist(name, false);
		queryClient.invalidateQueries({ queryKey: ['wishlists'] });
	}

	return (
		<View className = 'flex-1 bg-background'>
			<View className = 'flex-row items-center justify-between px-4 pb-3 pt-14'>
				<Text className = 'font-display text-xl text-foreground'>Le mie wishlist</Text>
				<View className = 'flex-row items-center gap-2'>
					<CreatePlaylistDialog onCreate = { handleCreatePlaylist }/>
					<Button variant = 'ghost' size = 'icon' onPress = { () => router.back() }>
						<X size = { 22 } className = 'text-foreground'/>
					</Button>
				</View>
			</View>
			{isLoading ? (
				<View className = 'flex-1 items-center justify-center'>
					<ActivityIndicator/>
				</View>
			) : (
				<FlatList data = { wishlists ?? [] } keyExtractor = { item => item.id } renderItem = { ({ item }) => (
                    <Pressable onPress = { () => router.push(`/wishlist/${item.id}`) } className = 'flex-row items-center gap-3 border-b border-border px-4 py-4'>
                        {item.isDefault ? (
                            <Heart size = { 18 } className = 'text-accent'/>
                        ) : item.isShared ? (
                            <Users size = { 18 } className = 'text-muted-foreground'/>
                        ) : (
                            <Lock size = { 18 } className = 'text-muted-foreground'/>
                        )}
                        <Text className = 'flex-1 text-base text-foreground'>{item.name}</Text>
                    </Pressable>
                ) }/>
			)}
		</View>
	)
}