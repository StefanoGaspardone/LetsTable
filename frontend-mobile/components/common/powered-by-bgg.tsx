import { Linking, Pressable } from 'react-native';
import { Image } from 'expo-image';

const BGG_URL = 'https://boardgamegeek.com';

const LOGO = require('@/assets/images/powered-by-bgg.png');

interface PoweredByBggProps {
	url?: string;
	width?: number;
	height?: number;
}

const PoweredByBgg = ({ url = BGG_URL, width = 160, height = 40 }: PoweredByBggProps) => (
	<Pressable onPress = { () => Linking.openURL(url) } hitSlop = { 8 } accessibilityRole = 'link' accessibilityLabel = 'Powered by BoardGameGeek' className = 'self-center rounded-xl bg-white px-3 py-2 active:opacity-70'>
		<Image source = { LOGO } style = {{ width, height }} contentFit = 'contain'/>
	</Pressable>
)

export default PoweredByBgg;