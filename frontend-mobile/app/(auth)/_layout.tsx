import { Stack } from 'expo-router';

const AuthLayout = () => {
    return (
        <Stack screenOptions = {{ headerShown: false }}>
            <Stack.Screen name = 'welcome'/>
            <Stack.Screen name = 'login'/>
            <Stack.Screen name = 'signup'/>
            <Stack.Screen name = 'activate'/>
        </Stack>
    )
}

export default AuthLayout;