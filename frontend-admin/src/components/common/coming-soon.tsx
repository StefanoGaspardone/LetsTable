import { Construction } from 'lucide-react';

interface ComingSoonProps {
	title?: string;
	description?: string;
}

const ComingSoon = ({ title = 'Presto disponibile', description = 'Questa sezione è in fase di sviluppo e sarà disponibile a breve.' }: ComingSoonProps) => {
	return (
		<div className = 'flex flex-1 flex-col items-center justify-center gap-4 py-20'>
			<div className = 'bg-primary/10 flex h-32 w-32 items-center justify-center rounded-full'>
				<Construction className = 'text-primary h-20 w-20'/>
			</div>
			<div className = 'flex flex-col items-center gap-1 text-center'>
				<h2 className = 'font-heading text-4xl font-semibold'>{title}</h2>
				<p className = 'text-muted-foreground text-lg'>{description}</p>
			</div>
		</div>
	)
}

export default ComingSoon;