import { View } from 'react-native';

import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import ComingSoon from '@/components/common/cooming-soon';

const AccountScreen = () => {
	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Modifica Profilo' leftElement = { <BackButton/> }/>
			<ComingSoon title = 'In arrivo' subtitle = 'Qui potrai modificare username, avatar ed email.'/>
		</View>
	)
}

export default AccountScreen;