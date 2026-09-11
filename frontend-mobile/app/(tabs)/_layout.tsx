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
			<Tabs.Screen name = 'profile/index'/>
			<Tabs.Screen name = 'browse' options = {{ href: null }}/>
            <Tabs.Screen name = 'game/[bggId]' options = {{ href: null }}/>
            <Tabs.Screen name = 'match/[id]' options = {{ href: null }}/>
            <Tabs.Screen name = 'match/[id]/edit' options = {{ href: null }}/>
            <Tabs.Screen name = 'match/[id]/finish' options = {{ href: null }}/>
            <Tabs.Screen name = 'my-wishlists' options = {{ href: null }}/>
            <Tabs.Screen name = 'wishlist/[id]' options = {{ href: null }}/>
			<Tabs.Screen name = 'profile/account' options = {{ href: null }}/>
			<Tabs.Screen name = 'profile/security' options = {{ href: null }}/>
		</Tabs>
	)
}

export default TabsLayout;