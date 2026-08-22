import { View, ScrollView, ActivityIndicator, Alert } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { Image } from 'expo-image';
import { ChevronLeft, Users, Clock, Heart, ListPlus, Dices } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import FabMenu from '@/components/fab-menu';

import { useCollectionStatus, useDefaultWishlistStatus, useGameDetail, useToggleCollection, useToggleDefaultWishlist } from '@/hooks/use-game';

export default function GameDetailScreen() {
	const { bggId } = useLocalSearchParams<{ bggId: string }>();
	const { data: game, isLoading } = useGameDetail(Number(bggId));

	const collectionStatus = useCollectionStatus(game?.id);
	const wishlistStatus = useDefaultWishlistStatus(game?.id);
	const toggleCollection = useToggleCollection();
	const toggleWishlist = useToggleDefaultWishlist();

	const onToggleCollection = async () => {
		if(!game) return;
		
        try {
			await toggleCollection.mutateAsync({
				gameId: game.id,
				itemId: collectionStatus.data?.itemId ?? null,
			});
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore';
			Alert.alert('Errore', message);
		}
	}

	const onToggleWishlist = async () => {
		if(!game) return;
		
        try {
			await toggleWishlist.mutateAsync({
				gameId: game.id,
				itemId: wishlistStatus.data?.itemId ?? null,
			});
		} catch (error: any) {
			const message = error?.response?.data?.message ?? 'Errore';
			Alert.alert('Errore', message);
		}
	}

	const onGoToPlaylistPicker = () => {
		if(!game) return;
		
        router.push({ pathname: '/playlist-picker', params: { gameId: game.id } });
	}

	if(isLoading) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator/>
			</View>
		)
	}

	if(!game) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<Text className = 'text-muted-foreground'>Gioco non trovato</Text>
			</View>
		)
	}

	const isInCollection = collectionStatus.data?.inCollection ?? false;
	const isInWishlist = wishlistStatus.data?.inWishlist ?? false;

	return (
		<View className = 'flex-1 bg-background'>
			<ScrollView contentContainerStyle = {{ paddingBottom: 32 }}>
				<View className = 'relative'>
					<Image source = {{ uri: game.imageUrl ?? game.thumbnailUrl ?? undefined }} style = {{ width: '100%', height: 280 }} contentFit = 'cover'/>
					<Button variant = 'secondary' size = 'icon' onPress = { () => router.back() } className = 'absolute left-4 top-14 rounded-full'>
						<ChevronLeft size = { 22 } className = 'text-foreground'/>
					</Button>
				</View>
				<View className = 'px-4 pt-4'>
					<Text className = 'font-display text-2xl text-foreground'>{game.name}</Text>
					<Text className = 'mb-4 text-muted-foreground'>{game.yearPublished ?? '-'}</Text>
					<View className = 'mb-6 flex-row gap-4'>
						{game.minPlayers && game.maxPlayers && (
							<View className = 'flex-row items-center gap-1.5'>
								<Users size = { 16 } className = 'text-muted-foreground'/>
								<Text className = 'text-sm text-foreground'>
									{game.minPlayers}-{game.maxPlayers}
								</Text>
							</View>
						)}
						{game.playingTimeMinutes && (
							<View className = 'flex-row items-center gap-1.5'>
								<Clock size = { 16 } className = 'text-muted-foreground'/>
								<Text className = 'text-sm text-foreground'>{game.playingTimeMinutes} min</Text>
							</View>
						)}
					</View>
					{game.description && (
						<Text className = 'text-sm leading-6 text-foreground'>{game.description}</Text>
					)}
				</View>
			</ScrollView>
			<FabMenu actions = { [
                {
                    label: isInCollection ? 'Rimuovi dalla collezione' : 'Aggiungi alla collezione',
                    icon: <Dices size = { 18 } className = 'text-foreground'/>,
                    onPress: onToggleCollection,
                },
                {
                    label: isInWishlist ? 'Rimuovi dalla wishlist' : 'Aggiungi alla wishlist',
                    icon: <Heart size = { 18 } className = 'text-foreground'/>,
                    onPress: onToggleWishlist,
                },
                {
                    label: 'Aggiungi a una playlist',
                    icon: <ListPlus size = { 18 } className = 'text-foreground'/>,
                    onPress: onGoToPlaylistPicker,
                },
            ] }/>
		</View>
	)
}