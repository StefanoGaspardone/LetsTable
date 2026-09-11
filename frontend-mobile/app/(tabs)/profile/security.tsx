import { View } from 'react-native';

import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import ComingSoon from '@/components/common/cooming-soon';

const SecurityScreen = () => {
	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Sicurezza & Password' leftElement = { <BackButton/> }/>
			<ComingSoon title = 'In arrivo' subtitle = 'Qui potrai cambiare la tua password.'/>
		</View>
	)
}

export default SecurityScreen;