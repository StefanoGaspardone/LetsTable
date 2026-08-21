import { Redirect } from 'expo-router';
import { View, ActivityIndicator } from 'react-native';

import { useAuth } from '@/contexts/auth-context';

const Index = () => {
	const { isLoading, isAuthenticated } = useAuth();

	if(isLoading) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator/>
			</View>
		)
	}

	if(isAuthenticated) {
		return <Redirect href = '/(tabs)/collection'/>;
	}

	return <Redirect href = '/(auth)/welcome'/>;
}

export default Index;