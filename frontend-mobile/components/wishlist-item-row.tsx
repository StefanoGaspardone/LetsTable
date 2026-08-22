import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Dices, X } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { WishlistItem } from '@/types/wishlist';

interface WishlistItemRowProps {
	item: WishlistItem;
	onPress?: () => void;
	onRemove?: () => void;
	canRemove: boolean;
}

const WishlistItemRow = ({ item, onPress, onRemove, canRemove }: WishlistItemRowProps) => {
	return (
		<Pressable onPress = { onPress } className = 'flex-row items-center gap-3 border-b border-border px-4 py-3'>
			{item.game.thumbnailUrl ? (
				<Image source = {{ uri: item.game.thumbnailUrl }} style = {{ width: 48, height: 48, borderRadius: 8 }} contentFit = 'cover'/>
			) : (
				<View className = 'h-12 w-12 items-center justify-center rounded-lg bg-secondary'>
					<Dices size = { 20 } className = 'text-muted-foreground'/>
				</View>
			)}
			<View className = 'flex-1'>
				<Text className = 'text-base text-foreground' numberOfLines = { 1 }>
					{item.game.name}
				</Text>
				<Text className = 'text-sm text-muted-foreground'>
					Aggiunto da {item.addedBy.username}
				</Text>
			</View>
			{canRemove && onRemove && (
				<Pressable onPress = { onRemove } hitSlop = { 8 } className = 'p-1'>
					<X size = { 18 } className = 'text-muted-foreground'/>
				</Pressable>
			)}
		</Pressable>
	)
}

export default WishlistItemRow;