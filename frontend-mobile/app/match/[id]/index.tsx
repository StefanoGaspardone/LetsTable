import { View, ScrollView, ActivityIndicator, Alert } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, Pencil, Trash2, MapPin, Clock, FileText, Flag } from 'lucide-react-native';
import { Image } from 'expo-image';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import PlayerBadge from '@/components/player-badge';
import TeamSection from '@/components/team-section';

import { useAuth } from '@/contexts/auth-context';

import { deleteMatch, getMatchById } from '@/api/match';

const MatchDetailScreen = () => {
	const { id } = useLocalSearchParams<{ id: string }>();
	const { user } = useAuth();
	const queryClient = useQueryClient();

	const { data: match, isLoading } = useQuery({
		queryKey: ['matches', 'detail', id],
		queryFn: () => getMatchById(id),
	});

	const isCreator = match?.createdBy.id === user?.id;
	const isInProgress = match?.durationMinutes == null;

	const handleDelete = async () => {
		Alert.alert('Elimina partita', 'Vuoi eliminare questa partita? L\'azione è irreversibile.', [
			{ text: 'Annulla', style: 'cancel' },
			{
				text: 'Elimina',
				style: 'destructive',
				onPress: async () => {
					try {
						await deleteMatch(id);
						queryClient.invalidateQueries({ queryKey: ['matches'] });
						
						router.back();
					} catch(error: any) {
						const message = error?.response?.data?.message ?? 'Errore durante l\'eliminazione';
						Alert.alert('Errore', message);
					}
				},
			},
		]);
	}

	if(isLoading || !match) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator/>
			</View>
		)
	}

	const formattedDate = new Date(match.playedAt).toLocaleDateString('it-IT', {
		day: 'numeric',
		month: 'long',
		year: 'numeric',
	});

	return (
		<View className = 'flex-1 bg-background'>
			<ScrollView contentContainerStyle = {{ paddingBottom: 32 }}>
				<View className = 'relative'>
					<Image source = {{ uri: match.game.thumbnailUrl ?? undefined }} style = {{ width: '100%', height: 180 }} contentFit = 'cover'/>
					<Button variant = 'secondary' size = 'icon' onPress = { () => router.back() } className = 'absolute left-4 top-14 rounded-full'>
						<ChevronLeft size = { 22 } className = 'text-foreground'/>
					</Button>
				</View>
				<View className = 'px-4 pt-4'>
					<Text className = 'font-display text-2xl text-foreground'>{match.game.name}</Text>
					<Text className = 'mb-4 text-muted-foreground'>{formattedDate}</Text>
					{isInProgress ? (
						<View className = 'mb-6 items-center rounded-lg bg-secondary py-6'>
							<Text className = 'mb-1 text-sm text-muted-foreground'>Partita in corso</Text>
						</View>
					) : (
						<View className = 'mb-4 gap-2'>
							{match.place && (
								<View className = 'flex-row items-center gap-2'>
									<MapPin size = { 16 } className = 'text-muted-foreground'/>
									<Text className = 'text-sm text-foreground'>{match.place}</Text>
								</View>
							)}
							{match.durationMinutes != null && (
								<View className = 'flex-row items-center gap-2'>
									<Clock size = { 16 } className = 'text-muted-foreground'/>
									<Text className = 'text-sm text-foreground'>{match.durationMinutes} minuti</Text>
								</View>
							)}
							{match.notes && (
								<View className = 'flex-row items-start gap-2'>
									<FileText size = { 16 } className = 'mt-0.5 text-muted-foreground'/>
									<Text className = 'flex-1 text-sm text-foreground'>{match.notes}</Text>
								</View>
							)}
						</View>
					)}
					{isCreator && (
						<View className = 'mb-4 flex-row gap-3'>
							{isInProgress ? (
								<Button className  ='flex-1 flex-row items-center gap-2' onPress = { () => router.push(`/match/${match.id}/finish`) }>
									<Flag size = { 16 } className = 'text-primary-foreground'/>
									<Text>Termina partita</Text>
								</Button>
							) : (
								<Button variant = 'outline' className = 'flex-1 flex-row items-center gap-2' onPress = { () => router.push(`/match/${match.id}/edit`) }>
									<Pencil size = { 16 } className = 'text-foreground'/>
									<Text>Modifica</Text>
								</Button>
							)}
							<Button variant = 'outline' className = 'flex-1 flex-row items-center gap-2 border-destructive' onPress = { handleDelete }>
								<Trash2 size = { 16 } className = 'text-destructive'/>
								<Text className = 'text-destructive'>Elimina</Text>
							</Button>
						</View>
					)}
				</View>
				<View className = 'mt-2'>
					{match.isTeamBased
						? match.teams?.map(team => <TeamSection key=  { team.id } team = { team }/>)
						: match.players?.map(player => (
								<PlayerBadge key = { player.id } name = { player.user?.username ?? player.guestName ?? 'Sconosciuto' } color = { player.color } score = { isInProgress ? null : player.score } isWinner = { isInProgress ? null : player.isWinner } startingPosition = { player.startingPosition }/>
							))}
				</View>
			</ScrollView>
		</View>
	)
}

export default MatchDetailScreen;