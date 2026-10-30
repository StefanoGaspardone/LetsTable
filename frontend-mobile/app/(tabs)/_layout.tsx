import { Redirect, Tabs } from 'expo-router';

import { useAuth } from '@/contexts/auth-context';

import TabBar from '@/components/common/tab-bar';

const HIDDEN = { href: null } as const;

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
			<Tabs.Screen name = 'profile/index'/>
			<Tabs.Screen name = 'browse' options = { HIDDEN }/>
			<Tabs.Screen name = 'game/[bggId]' options = { HIDDEN }/>
			<Tabs.Screen name = 'match/[id]/index' options = { HIDDEN }/>
			<Tabs.Screen name = 'match/[id]/edit' options = { HIDDEN }/>
			<Tabs.Screen name = 'match/[id]/finish' options = { HIDDEN }/>
			<Tabs.Screen name = 'my-wishlists' options = { HIDDEN }/>
			<Tabs.Screen name = 'wishlist/[id]' options = { HIDDEN }/>
			<Tabs.Screen name = 'profile/account' options = { HIDDEN }/>
			<Tabs.Screen name = 'profile/achievements' options = { HIDDEN }/>
			<Tabs.Screen name = 'profile/reset-password' options = { HIDDEN }/>
			<Tabs.Screen name = 'profile/security' options = { HIDDEN }/>
			<Tabs.Screen name = 'user/[id]' options = { HIDDEN }/>
		</Tabs>
	)
}

export default TabsLayout;