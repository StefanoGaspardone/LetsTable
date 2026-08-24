import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { router } from 'expo-router';
import { ChevronRight, Plus, Heart, Users, Lock, Dices } from 'lucide-react-native';
import { useQuery } from '@tanstack/react-query';

import { Text } from '@/components/ui/text';

import { Wishlist } from '@/types/wishlist';
import { listWishlistItems } from '@/api/wishlist';

interface WishlistMiniCardProps {
	wishlist: Wishlist;
}

const THUMB_SIZE = 64;

const WishlistMiniCard = ({ wishlist }: WishlistMiniCardProps) => {
	const { data } = useQuery({
		queryKey: ['wishlists', 'preview-items', wishlist.id],
		queryFn: () => listWishlistItems(wishlist.id, 0, 3),
	});

	const previewItems = data?.content ?? [];

	const handleOpen = () => router.push(`/wishlist/${wishlist.id}`);

	return (
		<Pressable onPress = { handleOpen } className = 'mr-3 w-52 rounded-2xl border border-border bg-card p-4'>
			<View className = 'flex-row items-center gap-2'>
				<View className = 'h-8 w-8 items-center justify-center rounded-full bg-secondary'>
					{wishlist.isDefault ? (
						<Heart size= { 16 } color = '#C45135'/>
					) : wishlist.isShared ? (
						<Users size = { 16 } color = '#C45135'/>
					) : (
						<Lock size = { 16 } color = '#C45135'/>
					)}
				</View>
				<Text className = 'flex-1 text-base font-semibold text-foreground' numberOfLines = { 1 }>
					{wishlist.name}
				</Text>
				<ChevronRight size = { 16 } className = 'text-muted-foreground'/>
			</View>
			<View className = 'mt-4 flex-row items-center justify-between'>
				<View className = 'flex-row'>
					{previewItems.map((item, index) => (
						<View key = { item.id } style = {{ width: THUMB_SIZE, height: THUMB_SIZE, marginLeft: index === 0 ? 0 : -14, zIndex: previewItems.length - index }} className = 'overflow-hidden rounded-xl border-2 border-card bg-secondary'>
							{item.game.thumbnailUrl ? (
								<Image source = {{ uri: item.game.thumbnailUrl }} style = {{ width: '100%', height: '100%' }} contentFit = 'cover'/>
							) : (
								<View className = 'h-full w-full items-center justify-center'>
									<Dices size = { 16 } className = 'text-muted-foreground'/>
								</View>
							)}
						</View>
					))}
				</View>
				<Pressable onPress = { handleOpen } className = 'h-9 w-9 items-center justify-center rounded-full bg-secondary'>
					<Plus size = { 18 } color = '#C45135'/>
				</Pressable>
			</View>
		</Pressable>
	)
}

export default WishlistMiniCard;