import { ArrowUp, ArrowDown, ArrowUpDown } from 'lucide-react';

import { TableHead } from '@/components/ui/table';

export type SortDirection = 'asc' | 'desc';

interface SortableTableHeadProps<TField extends string> {
	label: string;
	field: TField;
	activeField: TField;
	direction: SortDirection;
	onSort: (field: TField) => void;
	className?: string;
}

const SortableTableHead = <TField extends string>({ label, field, activeField, direction, onSort, className }: SortableTableHeadProps<TField>) => {
	const isActive = field === activeField;

	return (
		<TableHead className = { `cursor-pointer select-none ${className ?? ''}` } onClick = { () => onSort(field) }>
			<div className = 'flex items-center gap-1'>
				{label}
				{isActive ? (
					direction === 'asc' ? <ArrowUp className = 'h-3.5 w-3.5'/> : <ArrowDown className = 'h-3.5 w-3.5'/>
				) : (
					<ArrowUpDown className = 'text-muted-foreground/50 h-3.5 w-3.5'/>
				)}
			</div>
		</TableHead>
	)
}

export default SortableTableHead;