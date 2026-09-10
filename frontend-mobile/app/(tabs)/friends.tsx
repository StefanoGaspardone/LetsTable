import { View } from 'react-native';

import ScreenHeader from '@/components/common/screen-header';
import ComingSoon from '@/components/common/cooming-soon';

const FriendsScreen = () => {
	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Amici'/>
			<ComingSoon title = 'Amici in arrivo' subtitle = 'Presto potrai aggiungere amici, vedere le loro collezioni e sfidarli a nuove partite.'/>
		</View>
	)
}

export default FriendsScreen;