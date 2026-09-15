import type { LucideIcon } from 'lucide-react';

interface StatCardProps {
	label: string;
	value: number | string;
	icon: LucideIcon;
	isLoading?: boolean;
}

const StatsCard = ({ label, value, icon: Icon, isLoading }: StatCardProps) => {
	return (
		<div className = 'flex items-center gap-4 rounded-lg border border-border bg-card p-5'>
			<div className = 'flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary'>
				<Icon className = 'h-5 w-5'/>
			</div>
			<div className = 'flex flex-col'>
				<span className = 'text-muted-foreground text-sm font-medium uppercase tracking-wide'>{label}</span>
				{isLoading ? (
					<div className = 'bg-muted mt-1 h-6 w-12 animate-pulse rounded' />
				) : (
					<span className = 'font-mono text-2xl font-semibold'>{value}</span>
				)}
			</div>
		</div>
	)
}

export default StatsCard;