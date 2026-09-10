import { View } from 'react-native';

import ScreenHeader from '@/components/common/screen-header';
import ComingSoon from '@/components/common/cooming-soon';

const ProfileScreen = () => {
	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Profilo'/>
			<ComingSoon title = 'Profilo in arrivo' subtitle = "Qui potrai gestire le tue informazioni, le impostazioni e le preferenze dell'account."/>
		</View>
	)
}

export default ProfileScreen;