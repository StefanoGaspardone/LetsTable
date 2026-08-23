import { Redirect, Tabs } from 'expo-router';

import { useAuth } from '@/contexts/auth-context';

import TabBar from '@/components/common/tab-bar';

const TabsLayout = () => {
	const { isLoading, isAuthenticated } = useAuth();

	if(isLoading) return null;
	if(!isAuthenticated) return <Redirect href = '/(auth)/welcome'/>;

	return (
		<Tabs screenOptions = {{ headerShown: false }} tabBar = { (props: any) => <TabBar {...props}/> }>
			<Tabs.Screen name = 'home'/>
			<Tabs.Screen name = 'collection'/>
			<Tabs.Screen name = 'matches'/>
			<Tabs.Screen name = 'friends'/>
			<Tabs.Screen name = 'profile'/>
		</Tabs>
	)
}

export default TabsLayout;