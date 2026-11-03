import { View } from 'react-native';
import { Image } from 'expo-image';

import { Text } from '@/components/ui/text';

const MAX_VISIBLE_AVATARS = 4;
const AVATAR_SIZE = 26;
const AVATAR_OVERLAP = 8;

interface AvatarStackProps {
	avatars: { key: string; uri: string }[];
	ringColor: string;
	remainingClassName: string;
	remainingTextClassName: string;
}

const AvatarStack = ({ avatars, ringColor, remainingClassName, remainingTextClassName }: AvatarStackProps) => {
	const visible = avatars.slice(0, MAX_VISIBLE_AVATARS);
	const remainingCount = avatars.length - visible.length;

	return (
		<View className = 'flex-row items-center'>
			{visible.map((avatar, index) => (
				<View key = { avatar.key } style = {{ marginLeft: index === 0 ? 0 : -AVATAR_OVERLAP, zIndex: visible.length - index }}>
					<Image source = {{ uri: avatar.uri }} style = {{ width: AVATAR_SIZE, height: AVATAR_SIZE, borderRadius: AVATAR_SIZE / 2, borderWidth: 1, borderColor: ringColor }} contentFit = 'cover'/>
				</View>
			))}
			{remainingCount > 0 && (
				<View style = {{ marginLeft: -AVATAR_OVERLAP }} className = { remainingClassName }>
					<Text className = { remainingTextClassName }>+{remainingCount}</Text>
				</View>
			)}
		</View>
	)
}

export default AvatarStack;