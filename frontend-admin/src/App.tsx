import { Route, Routes } from 'react-router';

import { Toaster } from '@/components/ui/toast';
import ProtectedRoute from '@/components/routing/protected-route';

import AdminLayout from '@/layouts/admin-layout';

import RootPage from '@/pages/root-page';
import UsersPage from '@/pages/users-page';
import UserDetailPage from '@/pages/user-detail-page';
import GamesPage from '@/pages/games-page';
import GameDetailPage from '@/pages/game-detail-page';

const App = () => {
	return (
		<>
			<Toaster/>
			<Routes>
				<Route index element = { <RootPage/> }/>
				<Route element = { <ProtectedRoute/> }>
					<Route element = { <AdminLayout/> }>
						<Route path = 'users' element = { <UsersPage/> }/>
						<Route path = 'users/:id' element = { <UserDetailPage/> }/>
						<Route path = 'games' element = { <GamesPage/> }/>
						<Route path = 'games/:id' element = { <GameDetailPage/> }/>
					</Route>
				</Route>
			</Routes>
		</>
	)
}
 
export default App;