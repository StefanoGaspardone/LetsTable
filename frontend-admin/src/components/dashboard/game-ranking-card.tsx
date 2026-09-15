import { useNavigate } from 'react-router';

import type { GamePopularity } from '@/types/stats';

interface GameRankingCardProps {
	title: string;
	games: GamePopularity[];
	isLoading?: boolean;
}

const GameRankingCard = ({ title, games, isLoading }: GameRankingCardProps) => {
    const navigate = useNavigate();
    
	return (
		<div className = 'flex flex-col gap-3 rounded-lg border border-border bg-card p-5'>
			<span className = 'text-muted-foreground text-xs font-medium uppercase tracking-wide'>{title}</span>
			{isLoading ? (
				<div className = 'flex flex-col gap-2'>
					{Array.from({ length: 5 }).map((_, i) => (
						<div key = { i } className = 'bg-muted h-5 w-full animate-pulse rounded'/>
					))}
				</div>
			) : games.length === 0 ? (
				<span className = 'text-muted-foreground text-sm'>No data yet</span>
			) : (
				<ol className = 'flex flex-col gap-2'>
					{games.map((game, index) => (
						<li onClick = { () => navigate(`/games/${game.gameId}`) } key = { game.gameId } className = 'flex items-center justify-between gap-3 text-sm group cursor-pointer'>
							<div className = 'flex min-w-0 items-center gap-2'>
								<span className = 'text-muted-foreground w-4 shrink-0 font-mono text-sm'>{index + 1}</span>
								<div className = 'h-10 w-10 shrink-0 overflow-hidden rounded bg-muted'>
									{game.gameThumbnailUrl ? (
										<img src = { game.gameThumbnailUrl } alt = { game.gameName } className = 'h-full w-full object-cover'/>
									) : null}
								</div>
								<span className = 'truncate text-base font-medium group-hover:underline'>{game.gameName}</span>
							</div>
							<span className = 'text-muted-foreground shrink-0 font-mono text-sm'>{game.count}</span>
						</li>
					))}
				</ol>
			)}
		</div>
	)
}

export default GameRankingCard;