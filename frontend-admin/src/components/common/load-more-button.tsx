import { Loader2 } from 'lucide-react';

import { Button } from '@/components/ui/button';

interface LoadMoreButtonProps {
	isLast: boolean;
	isLoading: boolean;
	onLoadMore: () => void;
}

const LoadMoreButton = ({ isLast, isLoading, onLoadMore }: LoadMoreButtonProps) => {
	if(isLast) {
		return (
			<div className = 'flex justify-center py-4'>
				<span className = 'text-muted-foreground text-sm'>Hai raggiunto la fine</span>
			</div>
		)
	}

	return (
		<div className = 'flex justify-center py-4'>
			<Button variant = 'outline' className = 'bg-card cursor-pointer' onClick = { onLoadMore } disabled = { isLoading }>
				{isLoading ? <Loader2 className = 'h-4 w-4 animate-spin'/> : 'Carica altri'}
			</Button>
		</div>
	)
}

export default LoadMoreButton;