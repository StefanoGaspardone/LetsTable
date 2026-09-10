import { View } from 'react-native';

import BackButton from '@/components/common/back-button';
import ComingSoon from '@/components/common/cooming-soon';
import ScreenHeader from '@/components/common/screen-header';

const WishlistDetailScreen = () => {
	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Dettaglio Wishlist' leftElement = { <BackButton/> }/>
			<ComingSoon title = 'Dettaglio in arrivo' subtitle = 'Qui potrai vedere e gestire i giochi di questa wishlist.'/>
		</View>
	)
}

export default WishlistDetailScreen;