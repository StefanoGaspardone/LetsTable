import type { ReactNode } from 'react';
import { NavLink, Outlet } from 'react-router';
import { LayoutDashboard, Users, LogOut, Dices } from 'lucide-react';

import { useAuth } from '@/contexts/auth-context';

import Logo from '@/components/layout/logo';
import { Avatar, AvatarImage } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';

import { getAvatarUrl } from '@/lib/files';

const navItems = [
	{ to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
	{ to: '/games', label: 'Giochi', icon: Dices },
	{ to: '/users', label: 'Utenti', icon: Users },
]

interface AdminLayoutProps {
	children?: ReactNode;
}

const AdminLayout = ({ children }: AdminLayoutProps) => {
	const { user, logout } = useAuth();

	if(!user) return null;

	return (
		<div className = 'flex min-h-screen'>
			<aside className = 'flex w-60 shrink-0 flex-col border-r border-border bg-card'>
				<div className = 'flex h-16 items-center gap-2 border-b border-border px-4'>
					<Logo size = { 24 }/>
					<span className = 'font-heading text-lg font-bold tracking-wide'>LET'S TABLE</span>
					<span className = 'text-muted-foreground font-mono text-xs'>admin</span>
				</div>
				<nav className = 'flex flex-1 flex-col gap-1 p-3'>
					{navItems.map(item => (
						<NavLink key = { item.to } to = { item.to } end = { item.end } className = { ({ isActive }) => `flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors ${isActive ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted hover:text-foreground'}` }>
							<item.icon className = 'h-4 w-4'/>
							{item.label}
						</NavLink>
					))}
				</nav>
				<div className = 'flex items-center gap-2 border-t border-border p-3'>
					<Avatar className = 'h-8 w-8'>
						<AvatarImage src = { getAvatarUrl(user.avatarId, user.username) } alt = { user.username }/>
					</Avatar>
					<div className = 'flex min-w-0 flex-1 flex-col'>
						<span className = 'truncate text-xs font-medium'>{user.username}</span>
						<span className = 'text-muted-foreground truncate text-xs'>{user.email}</span>
					</div>
					<Button variant = 'ghost' size = 'icon' onClick = { logout } className = 'h-8 w-8 shrink-0 cursor-pointer'>
						<LogOut className = 'h-4 w-4'/>
					</Button>
				</div>
			</aside>

			<main className = 'flex-1 overflow-x-hidden'>
				{children ?? <Outlet/>}
			</main>
		</div>
	)
}

export default AdminLayout;