import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { View, Pressable } from 'react-native';
import { BottomSheetModal, BottomSheetScrollView } from '@gorhom/bottom-sheet';
import { Image } from 'expo-image';
import { Plus, X, Dices, Trash2, UserCheck, Puzzle } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import AppBottomSheet from '@/components/common/app-bottom-sheet';
import DatePickerField from '@/components/common/date-picker-field';
import GamePickerSheet, { GamePickerSheetRef, PickedGame } from '@/components/games/game-picker-sheet';
import SegmentedControl from '@/components/common/segmented-control';
import PlayerIdentityPickerSheet, { PickedIdentity, PlayerIdentityPickerSheetRef } from '@/components/matches/player-identity-picker-sheet';
import ColorSwatchPicker from '@/components/common/color-swatch-picker';
import ExpansionPickerSheet, { ExpansionPickerSheetRef, PickedExpansion } from '@/components/common/expansion-picker-sheet';
import SpinWheel from '@/components/common/spin-wheel';

import { getPlayerColor } from '@/lib/colors';
import { getAvatarUrl } from '@/lib/file';
import { toLocalDateString } from '@/lib/date';

import { useAuth } from '@/contexts/auth-context';
import { useToast } from '@/contexts/toast-context';
import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { useCreateMatch } from '@/hooks/use-match';
import { useThemeColors } from '@/hooks/use-theme-colors';

import { CreateMatchPayload } from '@/types/match';

interface PresetGame {
	id: string;
	bggId: number;
	name: string;
	thumbnailUrl: string | null;
}

interface TeamPlayer {
	userId: string | null;
	guestName: string | null;
	displayName: string;
	avatarId: string | null;
}

interface LocalPlayer extends TeamPlayer {
	color: string;
}

interface LocalTeam {
	name: string;
	color: string;
	players: TeamPlayer[];
}

type MatchMode = 'individual' | 'team';

export interface RegisterMatchSheetRef {
	present: (game?: PresetGame, initialExpansions?: PickedExpansion[]) => void;
	dismiss: () => void;
}

const MODE_OPTIONS = [
	{ value: 'individual', label: 'Singolo' },
	{ value: 'team', label: 'Squadre' },
]

const toTeamPlayer = (identity: PickedIdentity): TeamPlayer => ({
	userId: identity.userId,
	guestName: identity.guestName,
	displayName: identity.displayName,
	avatarId: identity.avatarId,
});

const playerKey = (player: TeamPlayer, index: number) => `${player.userId ?? `guest:${player.guestName}`}-${index}`;

const shiftIndexAfterRemoval = (current: number | null, removed: number) => {
	if(current === removed) return null;
	if(current !== null && current > removed) return current - 1;

	return current;
}

const RegisterMatchSheet = forwardRef<RegisterMatchSheetRef>((_, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);
	const gamePickerRef = useRef<GamePickerSheetRef>(null);
	const identityPickerRef = useRef<PlayerIdentityPickerSheetRef>(null);
	const expansionPickerRef = useRef<ExpansionPickerSheetRef>(null);

	const { user } = useAuth();
	const { showToast } = useToast();
	const router = useNavigationStack();
	const { colors } = useThemeColors();

	const createMatchMutation = useCreateMatch();

	const [presetGame, setPresetGame] = useState<PresetGame | null>(null);
	const [playedAt, setPlayedAt] = useState(() => toLocalDateString(new Date()));
	const [players, setPlayers] = useState<LocalPlayer[]>([]);
	const [mode, setModeRaw] = useState<MatchMode>('individual');
	const [teams, setTeams] = useState<LocalTeam[]>([]);
	const [identityPickerTargetTeamIndex, setIdentityPickerTargetTeamIndex] = useState<number | null>(null);
	const [isWheelOpen, setIsWheelOpen] = useState(false);
	const [startingIndex, setStartingIndex] = useState<number | null>(null);
	const [place, setPlace] = useState('');
	const [selectedExpansions, setSelectedExpansions] = useState<PickedExpansion[]>([]);

	const isSubmitting = createMatchMutation.isPending;

	const setMode = (value: string) => {
		setModeRaw(value as MatchMode);
		setStartingIndex(null);
	}

	useImperativeHandle(ref, () => ({
		present: (game, initialExpansions) => {
			setPresetGame(game ?? null);
			setPlayedAt(toLocalDateString(new Date()));
			setMode('individual');
			setPlayers(
				user
					? [{ userId: user.id, guestName: null, displayName: user.username, avatarId: user.avatarId, color: getPlayerColor(0) }]
					: []
			);
			setTeams([]);
			setIdentityPickerTargetTeamIndex(null);
			setPlace('');
			setSelectedExpansions(initialExpansions ?? []);
			setStartingIndex(null);

			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const reopen = () => sheetRef.current?.present();

	const handleOpenGamePicker = () => {
		sheetRef.current?.dismiss();
		gamePickerRef.current?.present();
	}

	const handleGameSelected = (game: PickedGame) => {
		setPresetGame(game);
		setSelectedExpansions([]);
		reopen();
	}

	const handleOpenExpansionPicker = () => {
		if(!presetGame) return;

		sheetRef.current?.dismiss();
		expansionPickerRef.current?.present(presetGame.bggId, selectedExpansions);
	}

	const handleExpansionsConfirmed = (expansions: PickedExpansion[]) => {
		setSelectedExpansions(expansions);
		reopen();
	}

	const handleRemoveExpansion = (expansionId: string) => {
		setSelectedExpansions(prev => prev.filter(expansion => expansion.id !== expansionId));
	}

	const handleOpenIdentityPicker = (teamIndex: number | null = null) => {
		setIdentityPickerTargetTeamIndex(teamIndex);

		sheetRef.current?.dismiss();
		identityPickerRef.current?.present();
	}

	const handleIdentitiesConfirmed = (identities: PickedIdentity[]) => {
		if(identityPickerTargetTeamIndex === null) {
			setPlayers(prev => [
				...prev,
				...identities.map((identity, index) => ({
					...toTeamPlayer(identity),
					color: getPlayerColor(prev.length + index),
				})),
			]);
		} else {
			const targetIndex = identityPickerTargetTeamIndex;

			setTeams(prev => prev.map((team, index) =>
				index === targetIndex
					? { ...team, players: [...team.players, ...identities.map(toTeamPlayer)] }
					: team
			));
		}

		reopen();
	}

	const handleRemovePlayer = (index: number) => {
		setPlayers(prev => prev.filter((_, i) => i !== index));
		setStartingIndex(prev => shiftIndexAfterRemoval(prev, index));
	}

	const handleToggleStarting = (index: number) => {
		setStartingIndex(prev => (prev === index ? null : index));
	}

	const handleColorChange = (index: number, color: string) => {
		setPlayers(prev => prev.map((player, i) => (i === index ? { ...player, color } : player)));
	}

	const handleAddTeam = () => {
		setTeams(prev => [...prev, { name: '', color: getPlayerColor(prev.length), players: [] }]);
	}

	const handleRemoveTeam = (index: number) => {
		setTeams(prev => prev.filter((_, i) => i !== index));
		setStartingIndex(prev => shiftIndexAfterRemoval(prev, index));
	}

	const handleTeamNameChange = (index: number, name: string) => {
		setTeams(prev => prev.map((team, i) => (i === index ? { ...team, name } : team)));
	}

	const handleTeamColorChange = (index: number, color: string) => {
		setTeams(prev => prev.map((team, i) => (i === index ? { ...team, color } : team)));
	}

	const handleRemoveTeamPlayer = (teamIndex: number, playerIndex: number) => {
		setTeams(prev => prev.map((team, i) => (i === teamIndex ? { ...team, players: team.players.filter((_, pi) => pi !== playerIndex) } : team)));
	}

	const isSelfInTeam = (teamIndex: number): boolean => {
		if(!user) return false;
		return teams[teamIndex].players.some(player => player.userId === user.id);
	}

	const handleToggleSelfInTeam = (teamIndex: number) => {
		if(!user) return;

		setTeams(prev => prev.map((team, index) => {
			const alreadyIn = team.players.some(player => player.userId === user.id);

			if(index === teamIndex) {
				return {
					...team,
					players: alreadyIn
						? team.players.filter(player => player.userId !== user.id)
						: [...team.players, { userId: user.id, guestName: null, displayName: user.username, avatarId: user.avatarId }],
				};
			}

			return { ...team, players: team.players.filter(player => player.userId !== user.id) };
		}));
	}

	const buildPayload = (gameId: string): CreateMatchPayload => ({
		gameId,
		playedAt,
		place: place.trim() || null,
		notes: null,
		durationMinutes: null,
		isTeamBased: mode === 'team',
		expansionIds: selectedExpansions.map(expansion => expansion.id),
		teams: mode === 'team'
			? teams.map((team, index) => ({
				name: team.name.trim() || null,
				color: team.color,
				score: 0,
				isWinner: false,
				isStartingFirst: startingIndex === index,
				players: team.players.map(player => ({ userId: player.userId, guestName: player.guestName })),
			}))
			: null,
		players: mode === 'individual'
			? players.map((player, index) => ({
				userId: player.userId,
				guestName: player.guestName,
				color: player.color,
				score: 0,
				isWinner: false,
				isStartingFirst: startingIndex === index,
			}))
			: null,
	});

	const handleSubmit = () => {
		if(!presetGame) {
			showToast('Seleziona un gioco per continuare', 'error');
			return;
		}

		if(mode === 'individual' && players.length === 0) {
			showToast('Aggiungi almeno un giocatore', 'error');
			return;
		}

		if(mode === 'team') {
			if(teams.length < 1) {
				showToast('Aggiungi almeno una squadra', 'error');
				return;
			}

			const emptyTeam = teams.find(team => team.players.length === 0);

			if(emptyTeam) {
				showToast(`La squadra '${emptyTeam.name || 'senza nome'}' non ha giocatori`, 'error');
				return;
			}
		}

		createMatchMutation.mutate(buildPayload(presetGame.id), {
			onSuccess: (created) => {
				sheetRef.current?.dismiss();
				router.push(`/match/${created.id}`);
			},
			onError: (error: any) => {
				showToast(error?.response?.data?.message ?? 'Errore durante la creazione', 'error');
			},
		});
	}

	const pickerSource: TeamPlayer[] = identityPickerTargetTeamIndex === null ? players : teams.flatMap(team => team.players);

	const excludeUserIds = pickerSource.map(player => player.userId).filter((id): id is string => id !== null);
	const excludeGuestNames = pickerSource.map(player => player.guestName).filter((name): name is string => name !== null);

	const isValid =
		presetGame !== null &&
		(mode === 'individual'
			? players.length > 0
			: teams.length >= 1 && teams.every(team => team.players.length > 0));

	const wheelEntries = mode === 'team'
		? teams.map((team, index) => ({ id: index.toString(), label: team.name.trim() || `Squadra ${index + 1}`, color: team.color }))
		: players.map((player, index) => ({ id: index.toString(), label: player.displayName, color: player.color }));

	return (
		<>
			<AppBottomSheet ref = { sheetRef }>
				<BottomSheetScrollView contentContainerStyle = {{ padding: 16, paddingBottom: 96 }}>
					<Text className = 'mb-4 font-display text-xl text-foreground'>Nuova Partita</Text>
					{presetGame ? (
						<Pressable onPress = { handleOpenGamePicker } className = 'mb-4 flex-row items-center gap-3 rounded-2xl border border-border bg-secondary p-2 active:bg-card active:scale-[0.98]'>
							<View style = {{ width: 52, height: 52 }} className = 'overflow-hidden rounded-xl bg-secondary'>
								{presetGame.thumbnailUrl ? (
									<Image source = {{ uri: presetGame.thumbnailUrl }} style = {{ width: 52, height: 52 }}/>
								) : (
									<View className = 'h-full w-full items-center justify-center'>
										<Dices size = { 18 } color = { colors.mutedForeground }/>
									</View>
								)}
							</View>
							<Text className = 'flex-1 font-medium text-sm text-foreground' numberOfLines = { 1 }>
								{presetGame.name}
							</Text>
							<Text className = 'text-xs text-primary-foreground rounded-lg py-2 px-3 bg-primary'>Cambia</Text>
						</Pressable>
					) : (
						<Pressable onPress = { handleOpenGamePicker } className = 'mb-4 flex-row items-center gap-2 rounded-xl border border-dashed border-border px-3 py-3 active:border-solid active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: colors.primary, borderColor: colors.primary }] }>
							{({ pressed }) => (
								<>
									<Dices size = { 16 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
									<Text className = { `text-sm ${pressed ? 'text-white' : 'text-muted-foreground'}` }>
										Scegli un gioco
									</Text>
								</>
							)}
						</Pressable>
					)}
					<Text className = 'mb-1.5 text-xs uppercase tracking-wide text-muted-foreground font-semibold'>Data</Text>
					<View className = 'mb-4'>
						<DatePickerField value = { playedAt } onChange = { setPlayedAt }/>
					</View>
					<Text className = 'mb-1.5 text-xs uppercase tracking-wide text-muted-foreground font-semibold'>Luogo (opzionale)</Text>
					<View className = 'mb-4'>
						<Input value = { place } onChangeText = { setPlace } placeholder = 'Es. Casa di Ale' className = 'h-11 bg-secondary border-0'/>
					</View>
					{presetGame && (
						<>
							<Text className = 'mb-1.5 text-xs uppercase tracking-wide text-muted-foreground font-semibold'>Espansioni (opzionale)</Text>
							<View className = 'mb-4 gap-2'>
								{selectedExpansions.map(expansion => (
									<View key = { expansion.id } className = 'flex-row items-center gap-2.5 rounded-xl border border-border bg-card p-2'>
										<View style = {{ width: 32, height: 32 }} className = 'overflow-hidden rounded-xl bg-secondary'>
											{expansion.thumbnailUrl ? (
												<Image source = {{ uri: expansion.thumbnailUrl }} style = {{ width: 32, height: 32 }} contentFit = 'cover'/>
											) : (
												<View className = 'h-full w-full items-center justify-center'>
													<Puzzle size = { 14 } color = { colors.mutedForeground }/>
												</View>
											)}
										</View>
										<Text className = 'flex-1 text-sm text-foreground font-medium' numberOfLines = { 1 }>
											{expansion.name}
										</Text>
										<Pressable onPress = { () => handleRemoveExpansion(expansion.id) } hitSlop = { 8 } className = 'active:rounded-full active:bg-primary/90 p-2'>
											{({ pressed }) => (
												<X size = { 16 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
											)}
										</Pressable>
									</View>
								))}
								<Pressable onPress = { handleOpenExpansionPicker } className = 'flex-row items-center justify-center gap-2 rounded-xl border border-dashed active:border-solid border-border px-3 py-2.5 active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: colors.primary, borderColor: colors.primary }] }>
									{({ pressed }) => (
										<>
											<Puzzle size = { 16 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
											<Text className = { `text-sm ${pressed ? 'text-white' : 'text-muted-foreground'}` }>
												{selectedExpansions.length > 0 ? 'Modifica espansioni' : 'Aggiungi espansioni'}
											</Text>
										</>
									)}
								</Pressable>
							</View>
						</>
					)}
					<Text className = 'mb-2 text-xs uppercase tracking-wide text-muted-foreground font-semibold'>Giocatori</Text>
					<View className = 'mb-4'>
						<SegmentedControl options = { MODE_OPTIONS } selected = { mode } onSelect = { setMode }/>
					</View>
					{mode === 'individual' && (
						<View className = 'mb-4 gap-2'>
							{players.map((player, index) => (
								<View key = { playerKey(player, index) } className = 'flex-row items-center gap-2.5 rounded-xl border border-border bg-card px-3 py-2.5'>
									<View style = {{ width: 32, height: 32 }} className = 'overflow-hidden rounded-full bg-secondary'>
										<Image source = {{ uri: getAvatarUrl(player.avatarId ?? null, player.displayName ?? '') }} style = {{ width: 32, height: 32 }} contentFit = 'cover'/>
									</View>
									<Text className = 'flex-1 text-sm text-foreground' numberOfLines = { 1 }>
										{player.displayName}
										{player.userId === user?.id && <Text className = 'text-xs text-primary font-semibold'> (io)</Text>}
									</Text>
									<ColorSwatchPicker value = { player.color } onChange = { color => handleColorChange(index, color) }/>
									<Pressable onPress = { () => handleToggleStarting(index) } className = { `rounded-full px-2.5 py-1.5 ${startingIndex === index ? 'bg-primary' : 'bg-secondary'}` }>
										<Text className = { `text-[11px] font-bold ${startingIndex === index ? 'text-white' : 'text-muted-foreground'}` }>1st</Text>
									</Pressable>
									{player.userId !== user?.id && (
										<Pressable onPress = { () => handleRemovePlayer(index) } hitSlop = { 8 } className = 'active:rounded-full active:bg-primary/90 p-2'>
											{({ pressed }) => (
												<X size = { 16 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
											)}
										</Pressable>
									)}
								</View>
							))}
							<Pressable onPress = { () => handleOpenIdentityPicker(null) } className = 'flex-row items-center justify-center gap-2 rounded-xl border border-dashed active:border-solid border-border px-3 py-2.5 active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: colors.primary, borderColor: colors.primary }] }>
								{({ pressed }) => (
									<>
										<Plus size = { 16 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
										<Text className = { `text-sm ${pressed ? 'text-white' : 'text-muted-foreground'}` }>
											Aggiungi giocatore
										</Text>
									</>
								)}
							</Pressable>
						</View>
					)}
					{mode === 'team' && (
						<View className = 'mb-4 gap-3'>
							{teams.map((team, teamIndex) => (
								<View key = { teamIndex } className = 'rounded-xl border border-border bg-card p-3'>
									<View className = 'mb-2 flex-row items-center gap-2'>
										<ColorSwatchPicker value = { team.color } onChange = { color => handleTeamColorChange(teamIndex, color) }/>
										<Input value = { team.name } onChangeText = { name => handleTeamNameChange(teamIndex, name) } placeholder = { `Squadra ${teamIndex + 1}` } className = 'h-9 flex-1'/>
										<Pressable onPress = { () => handleToggleStarting(teamIndex) } className = { `rounded-full px-2.5 py-1.5 ${startingIndex === teamIndex ? 'bg-primary' : 'bg-secondary'}` }>
											<Text className = { `text-[11px] font-bold ${startingIndex === teamIndex ? 'text-white' : 'text-muted-foreground'}` }>1st</Text>
										</Pressable>
										<Pressable onPress = { () => handleRemoveTeam(teamIndex) } hitSlop = { 8 } className = 'active:rounded-full active:bg-primary/90 p-2'>
											{({ pressed }) => (
												<Trash2 size = { 16 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
											)}
										</Pressable>
									</View>
									<View className = 'gap-1.5'>
										{team.players.map((player, playerIndex) => (
											<View key = { playerKey(player, playerIndex) } className = 'flex-row items-center gap-2 rounded-lg bg-secondary px-2.5 py-1.5'>
												<View style = {{ width: 24, height: 24 }} className = 'overflow-hidden rounded-full bg-card'>
													<Image source = {{ uri: getAvatarUrl(player.avatarId ?? null, player.displayName ?? '') }} style = {{ width: 24, height: 24 }} contentFit = 'cover'/>
												</View>
												<Text className = 'flex-1 text-xs text-foreground' numberOfLines = { 1 }>
													{player.displayName}
												</Text>
												<Pressable onPress = { () => handleRemoveTeamPlayer(teamIndex, playerIndex) } hitSlop = { 8 }>
													<X size = { 14 } color = { colors.mutedForeground }/>
												</Pressable>
											</View>
										))}
									</View>
									<View className = 'mt-2 flex-row gap-1.5'>
										<Pressable onPress = { () => handleToggleSelfInTeam(teamIndex) } className = { `flex-1 flex-row items-center justify-center gap-1.5 rounded-xl px-3 py-2.5 border ${isSelfInTeam(teamIndex) ? 'border-solid bg-primary' : 'border-dashed border-border'} active:border-solid active:bg-primary/90 active:border-primary/90` }>
											{({ pressed }) => {
												const isInTeam = isSelfInTeam(teamIndex);
												const isHighlighted = pressed || isInTeam;

												return (
													<>
														<UserCheck size = { 14 } color = { isHighlighted ? '#FFFFFF' : colors.mutedForeground }/>
														<Text className = { `text-sm ${isHighlighted ? 'text-white' : 'text-muted-foreground'}` }>
															{isInTeam ? 'Sei in questa squadra' : 'Sono qui'}
														</Text>
													</>
												)
											}}
										</Pressable>
										<Pressable onPress = { () => handleOpenIdentityPicker(teamIndex) } className = 'flex-row flex-1 items-center justify-center gap-1.5 rounded-xl border border-dashed active:border-solid border-border px-3 py-2.5 active:bg-primary/90 active:border-primary/90'>
											{({ pressed }) => (
												<>
													<Plus size = { 14 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
													<Text className = { `text-sm ${pressed ? 'text-white' : 'text-muted-foreground'}` }>
														Aggiungi giocatore
													</Text>
												</>
											)}
										</Pressable>
									</View>
								</View>
							))}
							<Pressable onPress = { handleAddTeam } className = 'flex-row items-center justify-center gap-2 rounded-xl border border-dashed active:border-solid border-border px-3 py-2.5 active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: colors.primary, borderColor: colors.primary }] }>
								{({ pressed }) => (
									<>
										<Plus size = { 16 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
										<Text className = { `text-sm ${pressed ? 'text-white' : 'text-muted-foreground'}` }>
											Aggiungi Squadra
										</Text>
									</>
								)}
							</Pressable>
						</View>
					)}
					<Button className = 'h-14 rounded-full' onPress = { handleSubmit } disabled = { isSubmitting || !isValid }>
						<Text className = 'text-base font-semibold text-primary-foreground'>
							{isSubmitting ? 'Creazione...' : 'Inizia Partita'}
						</Text>
					</Button>
					<Text onPress = { () => setIsWheelOpen(true) } className = 'text-sm text-center active:underline text-primary font-medium mt-2'>
						{mode === 'team' ? 'Scegli la squadra iniziale' : 'Scegli il giocatore iniziale'}
					</Text>
				</BottomSheetScrollView>
			</AppBottomSheet>
			<GamePickerSheet ref = { gamePickerRef } onSelect = { handleGameSelected } onBack = { () => { gamePickerRef.current?.dismiss(); reopen(); } }/>
			<PlayerIdentityPickerSheet ref = { identityPickerRef } excludeUserIds = { excludeUserIds } excludeGuestNames = { excludeGuestNames } onConfirm = { handleIdentitiesConfirmed } onBack = { () => { identityPickerRef.current?.dismiss(); reopen(); } }/>
			<ExpansionPickerSheet ref = { expansionPickerRef } onConfirm = { handleExpansionsConfirmed } onBack = { () => { expansionPickerRef.current?.dismiss(); reopen(); } }/>
			<SpinWheel visible = { isWheelOpen } entries = { wheelEntries } onClose = { () => setIsWheelOpen(false) } onResult = { id => { setStartingIndex(Number(id)); setIsWheelOpen(false); } }/>
		</>
	)
});

RegisterMatchSheet.displayName = 'RegisterMatchSheet';

export default RegisterMatchSheet;