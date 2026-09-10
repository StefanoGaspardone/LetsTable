import { useQuery } from '@tanstack/react-query';

import { listFriends } from '@/api/friend';

export const useFriends = () => {
	return useQuery({
		queryKey: ['friends', 'mine'],
		queryFn: () => listFriends(),
	});
}