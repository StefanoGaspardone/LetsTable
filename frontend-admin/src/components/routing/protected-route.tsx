import { Navigate, Outlet } from 'react-router';

import { useAuth } from '@/contexts/auth-context';

const ProtectedRoute = () => {
	const { user, isLoading } = useAuth();

	if(isLoading) return null;
	if(!user) return <Navigate to = '/' replace/>;
    
	return <Outlet/>;
}

export default ProtectedRoute;