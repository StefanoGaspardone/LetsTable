import { useAuth } from '@/contexts/auth-context';

import LoginForm from '@/components/auth/login-form';
import DashboardOverview from '@/components/dashboard/dashboard-overview';

import AdminLayout from '@/layouts/admin-layout';

const RootPage = () => {
	const { user, isLoading } = useAuth();

	if(isLoading) return null;

	if(!user) {
		return <LoginForm/>;
	}

	return (
		<AdminLayout>
			<DashboardOverview/>
		</AdminLayout>
	)
}

export default RootPage;