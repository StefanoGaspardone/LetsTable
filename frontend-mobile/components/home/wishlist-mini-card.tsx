import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { router } from 'expo-router';
import { Heart, Users, Lock, Dices, ChevronRight } from 'lucide-react-native';
import { useQuery } from '@tanstack/react-query';

import { Text } from '@/components/ui/text';

import { Wishlist } from '@/types/wishlist';
import { listWishlistItems } from '@/api/wishlist';

interface WishlistMiniCardProps {
	wishlist: Wishlist;
}

const THUMB_SIZE = 44;

const WishlistMiniCard = ({ wishlist }: WishlistMiniCardProps) => {
	const { data } = useQuery({
		queryKey: ['wishlists', 'preview-items', wishlist.id],
		queryFn: () => listWishlistItems(wishlist.id, 0, 3),
	});

	const previewItems = data?.content ?? [];

	const handleOpen = () => router.push(`/wishlist/${wishlist.id}`);

	return (
		<Pressable onPress = { handleOpen } className = 'flex-row items-center gap-3 rounded-2xl border border-border bg-card p-2 active:scale-[0.98] active:opacity-75'>
			<View className = 'h-11 w-11 items-center justify-center rounded-full bg-secondary'>
				{wishlist.isDefault ? (
					<Heart size = { 20 } color = '#C45135'/>
				) : wishlist.isShared ? (
					<Users size = { 20 } color = '#C45135'/>
				) : (
					<Lock size = { 20 } color = '#C45135'/>
				)}
			</View>
			<View className = 'flex-1'>
				<Text className = 'text-base font-semibold text-foreground' numberOfLines = { 1 }>
					{wishlist.name}
				</Text>
				<View className = 'mt-1.5 flex-row'>
					{previewItems.map((item, index) => (
						<View key = { item.id } style = {{ width: THUMB_SIZE, height: THUMB_SIZE, marginLeft: index === 0 ? 0 : -12, zIndex: previewItems.length - index }} className = 'overflow-hidden rounded-lg border-2 border-card bg-secondary'>
							{item.game.thumbnailUrl ? (
								<Image source = {{ uri: item.game.thumbnailUrl }} style = {{ width: '100%', height: '100%' }} contentFit = 'cover'/>
							) : (
								<View className = 'h-full w-full items-center justify-center'>
									<Dices size = { 14 } className = 'text-muted-foreground'/>
								</View>
							)}
						</View>
					))}
				</View>
			</View>
			<View className = 'h-9 w-9 items-center justify-center rounded-full bg-secondary'>
				<ChevronRight size = { 18 } color = '#C45135'/>
			</View>
		</Pressable>
	)
}

export default WishlistMiniCard;