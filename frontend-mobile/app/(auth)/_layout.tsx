import { Stack } from 'expo-router';

const AuthLayout = () => {
    return (
        <Stack screenOptions = {{ headerShown: false }}>
            <Stack.Screen name = 'welcome'/>
            <Stack.Screen name = 'login'/>
            <Stack.Screen name = 'signup'/>
            <Stack.Screen name = 'activate'/>
            <Stack.Screen name = 'forgot-password'/>
        </Stack>
    )
}

export default AuthLayout;