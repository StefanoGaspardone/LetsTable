import { useState } from 'react';
import { Pressable } from 'react-native';
import { router } from 'expo-router';
import { ChevronLeft } from 'lucide-react-native';

const BackButton = () => {
	const [isPressed, setIsPressed] = useState(false);

	return (
		<Pressable
			onPress={() => router.back()}
			onPressIn={() => setIsPressed(true)}
			onPressOut={() => setIsPressed(false)}
			hitSlop={8}
			style={{
				height: 36,
				width: 36,
				alignItems: 'center',
				justifyContent: 'center',
				borderRadius: 999,
				backgroundColor: isPressed ? 'rgba(0,0,0,0.12)' : 'rgba(0,0,0,0.06)',
			}}
		>
			<ChevronLeft size={20} color="#736E65" />
		</Pressable>
	);
};

export default BackButton;