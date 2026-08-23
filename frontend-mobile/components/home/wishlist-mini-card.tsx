import { Pressable, View } from 'react-native';
import { router } from 'expo-router';
import { Heart, Users, Lock } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Wishlist } from '@/types/wishlist';

interface WishlistMiniCardProps {
	wishlist: Wishlist;
}

const WishlistMiniCard = ({ wishlist }: WishlistMiniCardProps) => {
	return (
		<Pressable
			onPress = { () => router.push(`/wishlist/${wishlist.id}`) }
			className = 'mr-3 w-36 rounded-2xl border border-border bg-card p-4'> 
			<View className = 'mb-3 h-9 w-9 items-center justify-center rounded-full bg-secondary'>
				{wishlist.isDefault ? (
					<Heart size = { 16 } color = '#C45135'/>
				) : wishlist.isShared ? (
					<Users size = { 16 } color = '#C45135'/>
				) : (
					<Lock size = { 16 } color = '#C45135'/>
				)}
			</View>
			<Text className = 'text-sm font-semibold text-foreground' numberOfLines = { 2 }>
				{wishlist.name}
			</Text>
		</Pressable>
	)
}

export default WishlistMiniCard;