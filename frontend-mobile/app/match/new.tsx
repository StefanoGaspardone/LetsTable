import { View } from 'react-native';
import { router } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';

import { CreateMatchPayload } from '@/types/match';

import { createMatch } from '@/api/match';

import MatchForm from '@/components/match-form';

const NewMatchScreen = () => {
	const queryClient = useQueryClient();

    const handleSubmit = async (payload: CreateMatchPayload) => {
		const created = await createMatch(payload);
		queryClient.invalidateQueries({ queryKey: ['matches'] });
		
        router.replace(`/match/${created.id}`);
	}

	return (
		<View className = 'flex-1 bg-background pt-14'>
			<MatchForm mode = 'start' onSubmit = { handleSubmit } submitLabel = 'Inizia partita'/>
		</View>
	)
}

export default NewMatchScreen;