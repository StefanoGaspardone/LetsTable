import { useQuery } from '@tanstack/react-query';

import { searchUser } from '@/api/user';

export const useUserSearch = (query: string) => {
	return useQuery({
		queryKey: ['users', 'search', query],
		queryFn: () => searchUser(query),
		enabled: query.length > 0,
	});
}