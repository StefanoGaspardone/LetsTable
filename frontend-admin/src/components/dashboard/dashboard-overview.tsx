import { useEffect, useRef, useState } from 'react';
import { Users, UserCheck, Trophy, Percent } from 'lucide-react';

import { getStats } from '@/apis/stats';

import StatCard from '@/components/dashboard/stats-card';
import GameRankingCard from '@/components/dashboard/game-ranking-card';

import type { AdminStats } from '@/types/stats';

const DashboardOverview = () => {
	const [stats, setStats] = useState<AdminStats | null>(null);
	const [isLoading, setIsLoading] = useState(true);

	const firstRef = useRef<boolean>(true);
	useEffect(() => {
		const fetchStats = async () => {
			try {
				const data = await getStats();
				setStats(data);
			} catch {
				// swallow catch
			} finally {
				setIsLoading(false);
			}
		}

		if(firstRef.current) {
			fetchStats();
			firstRef.current = false;
		}
	}, []);

	const activationPercentage = stats ? Math.round(stats.activationRate * 100) : 0;

	return (
		<div className = 'flex flex-col gap-8 p-8'>
			<h1 className = 'font-heading text-2xl font-bold'>Dashboard</h1>
			<div className = 'grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4'>
				<StatCard label = 'Total Users' value = { stats?.totalUsers ?? 0 } icon = { Users } isLoading = { isLoading }/>
				<StatCard label = 'Active Users' value = { stats?.activeUsers ?? 0 } icon = { UserCheck } isLoading = { isLoading }/>
				<StatCard label = 'Activation Rate' value = { `${activationPercentage}%` } icon = { Percent } isLoading = { isLoading }/>
				<StatCard label = 'Total Matches' value = { stats?.totalMatches ?? 0 } icon = { Trophy } isLoading = { isLoading }/>
			</div>
			<div className = 'grid grid-cols-1 gap-4 lg:grid-cols-3'>
				<GameRankingCard title = 'Most Owned Games' games = { stats?.mostOwnedGames ?? [] } isLoading = { isLoading }/>
				<GameRankingCard title = 'Most Played Games' games = { stats?.mostPlayedGames ?? [] } isLoading = { isLoading }/>
				<GameRankingCard title = 'Most Wished Games' games = { stats?.mostWishedGames ?? [] } isLoading = { isLoading }/>
			</div>
		</div>
	)
}

export default DashboardOverview;