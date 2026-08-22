import { Redirect, Tabs } from 'expo-router';
import { Dices, Trophy, Users, User } from 'lucide-react-native';

import { useAuth } from '@/contexts/auth-context';

const TabsLayout = () => {
	const { isLoading, isAuthenticated } = useAuth();

	if(isLoading) {
		return null;
	}

	if(!isAuthenticated) {
		return <Redirect href = '/(auth)/welcome'/>;
	}

	return (
		<Tabs screenOptions = {{ headerShown: false }}>
			<Tabs.Screen name = 'collection' options = {{ title: 'Giochi', tabBarIcon: ({ color, size }) => <Dices color = { color } size = { size }/> }}/>
			<Tabs.Screen name = 'matches' options = {{ title: 'Partite', tabBarIcon: ({ color, size }) => <Trophy color = { color } size = { size }/> }}/>
			<Tabs.Screen name = 'friends' options = {{ title: 'Amici', tabBarIcon: ({ color, size }) => <Users color = { color } size = { size }/> }}/>
			<Tabs.Screen name = 'profile' options = {{ title: 'Profilo', tabBarIcon: ({ color, size }) => <User color = { color } size = { size }/> }}/>
		</Tabs>
	)
}

export default TabsLayout;