import { useState } from 'react';

import type { SortDirection } from '@/components/common/sortable-table-head';

export const useSort = <TField extends string>(initialField: TField, initialDirection: SortDirection = 'asc') => {
	const [field, setField] = useState<TField>(initialField);
	const [direction, setDirection] = useState<SortDirection>(initialDirection);

	const handleSort = (nextField: TField) => {
		if(nextField === field) {
			setDirection(prev => (prev === 'asc' ? 'desc' : 'asc'));
		} else {
			setField(nextField);
			setDirection('asc');
		}
	}

	const sortParam = `${field}-${direction}`;

	return { field, direction, handleSort, sortParam };
}