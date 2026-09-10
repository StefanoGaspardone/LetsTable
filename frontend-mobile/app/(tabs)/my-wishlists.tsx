import { View } from 'react-native';

import ScreenHeader from '@/components/common/screen-header';
import ComingSoon from '@/components/common/cooming-soon';
import BackButton from '@/components/common/back-button';

const MyWishlistsScreen = () => {
	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Le Mie Wishlist' leftElement = { <BackButton/> }/>
			<ComingSoon title = 'Wishlist in arrivo' subtitle = 'Presto potrai creare e gestire tutte le tue liste dei desideri da qui.'/>
		</View>
	)
}

export default MyWishlistsScreen;