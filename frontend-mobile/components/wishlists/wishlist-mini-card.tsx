import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Heart, Users, Lock, Dices, ChevronRight, Check } from 'lucide-react-native';
import { useQuery } from '@tanstack/react-query';

import { Text } from '@/components/ui/text';

import { Wishlist } from '@/types/wishlist';

import { listWishlistItems } from '@/api/wishlist';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { useThemeColors } from '@/hooks/use-theme-colors';

interface WishlistMiniCardProps {
	wishlist: Wishlist;
	onPress?: () => void;
	selected?: boolean;
}

const THUMB_SIZE = 44;

const WishlistMiniCard = ({ wishlist, onPress, selected }: WishlistMiniCardProps) => {
	const router = useNavigationStack();
	const { colors } = useThemeColors();

	const { data } = useQuery({
		queryKey: ['wishlists', 'preview-items', wishlist.id],
		queryFn: () => listWishlistItems(wishlist.id, 0, 3),
	});

	const previewItems = data?.content ?? [];
	const remainingCount = Math.max(0, (data?.totalElements ?? 0) - previewItems.length);

	const handleOpen = () => router.push(`/wishlist/${wishlist.id}`);

	const isSelectable = selected !== undefined;

	return (
		<Pressable onPress = { onPress ?? handleOpen } className = { `flex-row items-center gap-3 rounded-2xl border p-2 active:scale-[0.98] active:opacity-75 ${selected ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
			<View className = 'h-11 w-11 items-center justify-center rounded-full bg-secondary'>
				{wishlist.isDefault ? (
					<Heart size = { 20 } color = { colors.primary }/>
				) : wishlist.isShared ? (
					<Users size = { 20 } color = { colors.primary }/>
				) : (
					<Lock size = { 20 } color = { colors.primary }/>
				)}
			</View>
			<View className = 'flex-1'>
				<Text className = 'text-base font-semibold text-foreground' numberOfLines = { 1 }>
					{wishlist.name}
				</Text>
				{previewItems.length > 0 ? (
					<View className = 'mt-1.5 flex-row'>
						{previewItems.map((item, index) => (
							<View key = { item.id } style = {{ width: THUMB_SIZE, height: THUMB_SIZE, marginLeft: index === 0 ? 0 : -12, zIndex: previewItems.length - index }} className = 'overflow-hidden rounded-xl border-2 border-card bg-secondary'>
								{item.game.thumbnailUrl ? (
									<Image source = {{ uri: item.game.thumbnailUrl }} style = {{ width: '100%', height: '100%' }} contentFit = 'cover'/>
								) : (
									<View className = 'h-full w-full items-center justify-center'>
										<Dices size = { 14 } className = 'text-muted-foreground'/>
									</View>
								)}
							</View>
						))}
						{remainingCount > 0 && (
							<View style = {{ zIndex: 0 }} className = 'items-center justify-center overflow-hidden px-1.5'>
								<Text className = 'text-base font-medium text-muted-foreground'>+{remainingCount}</Text>
							</View>
						)}
					</View>
				) : (
					<Text className = 'text-xs text-muted-foreground'>Nessun elemento</Text>
				)}
			</View>
			<View className = 'justify-center'>
				{isSelectable ? (
					<View className = { `mr-1 h-6 w-6 items-center justify-center rounded-md border ${selected ? 'border-primary bg-primary' : 'border-border bg-background'}` }>
						{selected && <Check size = { 16 } color = '#FFFFFF' strokeWidth = { 3 }/>}
					</View>
				) : (
					<ChevronRight size = { 18 } color = '#736E65'/>
				)}
			</View>
		</Pressable>
	)
}

export default WishlistMiniCard;