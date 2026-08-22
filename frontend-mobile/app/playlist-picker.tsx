import { View, FlatList, ActivityIndicator, Alert, Pressable } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { X, Users, Lock } from 'lucide-react-native';
import { useQueryClient } from '@tanstack/react-query';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import CreatePlaylistDialog from '@/components/create-playlist-dialog';

import { useMyWishlists } from '@/hooks/use-wishlist';

import { addItemToWishlist, createWishlist } from '@/api/wishlist';

export default function PlaylistPickerScreen() {
	const { gameId } = useLocalSearchParams<{ gameId: string }>();
	const { data: wishlists, isLoading } = useMyWishlists();
	const queryClient = useQueryClient();

	const playlists = wishlists?.filter(w => !w.isDefault) ?? [];

	const handleAddToPlaylist = async (wishlistId: string, name: string) => {
		try {
			await addItemToWishlist(wishlistId, gameId);
			queryClient.invalidateQueries({ queryKey: ['wishlists'] });
			
            Alert.alert('Aggiunto', `Gioco aggiunto a '${name}'`);
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante l\'aggiunta';
			Alert.alert('Errore', message);
		}
	}

	const handleCreatePlaylist = async (name: string) => {
		try {
			const created = await createWishlist(name, false);
			await addItemToWishlist(created.id, gameId);
			
            queryClient.invalidateQueries({ queryKey: ['wishlists'] });
			Alert.alert('Creata', `Playlist '${name}' creata e gioco aggiunto`);
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante la creazione';
			Alert.alert('Errore', message);
		}
	}

	return (
		<View className = 'flex-1 bg-background'>
			<View className = 'flex-row items-center justify-between px-4 pb-3 pt-14'>
				<Text className = 'font-display text-xl text-foreground'>Aggiungi a una playlist</Text>
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
			) : playlists.length === 0 ? (
				<View className = 'flex-1 items-center justify-center px-6'>
					<Text className = 'text-center text-muted-foreground'>
						Non hai ancora nessuna playlist. Creane una con il pulsante +.
					</Text>
				</View>
			) : (
				<FlatList data = { playlists } keyExtractor = { item => item.id} renderItem = { ({ item }) => (
                    <Pressable onPress = { () => handleAddToPlaylist(item.id, item.name) } className = 'flex-row items-center gap-3 border-b border-border px-4 py-4'>
                        {item.isShared ? (
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