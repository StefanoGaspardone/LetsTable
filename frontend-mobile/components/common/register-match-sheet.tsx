import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { View, Pressable } from 'react-native';
import { BottomSheetModal, BottomSheetScrollView } from '@gorhom/bottom-sheet';
import { router } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { Plus, X, Dices, User, Trash2, UserCheck } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import AppBottomSheet from '@/components/common/app-bottom-sheet';
import DatePickerField from '@/components/common/date-picker-field';
import GamePickerSheet, { GamePickerSheetRef, PickedGame } from '@/components/common/game-picker-sheet';
import SegmentedControl from '@/components/common/segmented-control';
import PlayerIdentityPickerSheet, { PickedIdentity, PlayerIdentityPickerSheetRef } from '@/components/common/player-identity-picker-sheet';
import ColorSwatchPicker from '@/components/common/color-swatch-picker';
import FingerOrderPicker from '@/components/common/finger-order-picker';

import { createMatch } from '@/api/match';

import { getPlayerColor } from '@/lib/colors';

import { useAuth } from '@/contexts/auth-context';
import { useToast } from '@/contexts/toast-context';

interface PresetGame {
	id: string;
	name: string;
	thumbnailUrl: string | null;
}

interface LocalPlayer {
	userId: string | null;
	guestName: string | null;
	displayName: string;
	avatarUrl: string | null;
	color: string;
}

interface TeamPlayer {
	userId: string | null;
	guestName: string | null;
	displayName: string;
	avatarUrl: string | null;
}

interface LocalTeam {
	name: string;
	color: string;
	players: TeamPlayer[];
}

export interface RegisterMatchSheetRef {
	present: (game?: PresetGame) => void;
	dismiss: () => void;
}

const MODE_OPTIONS = [
	{ value: 'individual', label: 'Singolo' },
	{ value: 'team', label: 'Squadre' },
]

const RegisterMatchSheet = forwardRef<RegisterMatchSheetRef>((_, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);
    const gamePickerRef = useRef<GamePickerSheetRef>(null);
	const identityPickerRef = useRef<PlayerIdentityPickerSheetRef>(null);

	const { user } = useAuth();
	const { showToast } = useToast();
	const queryClient = useQueryClient();

	const [presetGame, setPresetGame] = useState<PresetGame | null>(null);
	const [playedAt, setPlayedAt] = useState(new Date().toISOString().slice(0, 10));
	const [players, setPlayers] = useState<LocalPlayer[]>([]);
	const [isSubmitting, setIsSubmitting] = useState(false);
    const [mode, setMode] = useState('individual');
    const [teams, setTeams] = useState<LocalTeam[]>([]);
    const [identityPickerTargetTeamIndex, setIdentityPickerTargetTeamIndex] = useState<number | null>(null);
    const [isFingerPickerOpen, setIsFingerPickerOpen] = useState(false);
    const [place, setPlace] = useState('');

    useImperativeHandle(ref, () => ({
		present: game => {
			setPresetGame(game ?? null);
			setPlayedAt(new Date().toISOString().slice(0, 10));
			setMode('individual');
			setPlayers(
				user
					? [{ userId: user.id, guestName: null, displayName: user.username, avatarUrl: user.avatarUrl ?? null, color: getPlayerColor(0) }]
					: []
			);
            setTeams([]);
            setIdentityPickerTargetTeamIndex(null);
            setPlace('');

			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const handleOpenGamePicker = () => {
		sheetRef.current?.dismiss();
		gamePickerRef.current?.present();
	}

	const handleGameSelected = (game: PickedGame) => {
		setPresetGame(game);
		sheetRef.current?.present();
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
                    userId: identity.userId,
                    guestName: identity.guestName,
                    displayName: identity.displayName,
                    avatarUrl: identity.avatarUrl,
                    color: getPlayerColor(prev.length + index),
                })),
            ]);
        } else {
            const targetIndex = identityPickerTargetTeamIndex;
            setTeams(prev =>
                prev.map((t, i) =>
                    i === targetIndex
                        ? {
                                ...t,
                                players: [
                                    ...t.players,
                                    ...identities.map((identity) => ({
                                        userId: identity.userId,
                                        guestName: identity.guestName,
                                        displayName: identity.displayName,
                                        avatarUrl: identity.avatarUrl,
                                    })),
                                ],
                            }
                        : t
                )
            );
        }

        sheetRef.current?.present();
    }

	const handleRemovePlayer = (index: number) => {
		setPlayers(prev => prev.filter((_, i) => i !== index));
	}

	const handleColorChange = (index: number, color: string) => {
		setPlayers(prev => prev.map((p, i) => (i === index ? { ...p, color } : p)));
	}

    const handleAddTeam = () => {
        setTeams(prev => [...prev, { name: '', color: getPlayerColor(prev.length), players: [] }]);
    }

    const handleRemoveTeam = (index: number) => {
        setTeams(prev => prev.filter((_, i) => i !== index));
    }

    const handleTeamNameChange = (index: number, name: string) => {
        setTeams(prev => prev.map((t, i) => (i === index ? { ...t, name } : t)));
    }

    const handleTeamColorChange = (index: number, color: string) => {
        setTeams(prev => prev.map((t, i) => (i === index ? { ...t, color } : t)));
    }

    const handleRemoveTeamPlayer = (teamIndex: number, playerIndex: number) => {
        setTeams(prev =>prev.map((t, i) => (i === teamIndex ? { ...t, players: t.players.filter((_, pi) => pi !== playerIndex) } : t)));
    }

    const isSelfInTeam = (teamIndex: number): boolean => {
        if(!user) return false;
        return teams[teamIndex].players.some((p) => p.userId === user.id);
    }

    const handleToggleSelfInTeam = (teamIndex: number) => {
        if(!user) return;

        setTeams(prev =>
            prev.map((t, i) => {
                const alreadyIn = t.players.some(p => p.userId === user.id);

                if(i === teamIndex) {
                    return {
                        ...t,
                        players: alreadyIn
                            ? t.players.filter(p => p.userId !== user.id)
                            : [...t.players, { userId: user.id, guestName: null, displayName: user.username, avatarUrl: user.avatarUrl ?? null }],
                    };
                }

                return { ...t, players: t.players.filter(p => p.userId !== user.id) };
            })
        );
    }

	const handleSubmit = async () => {
        if(!presetGame) {
            showToast('Seleziona un gioco per continuare', 'error');
            return;
        }

        if(mode === 'individual' && players.length === 0) {
            showToast('Aggiungi almeno un giocatore', 'error');
            return;
        }

        if(mode === 'team') {
            if(teams.length < 2) {
                showToast('Aggiungi almeno due squadre', 'error');
                return;
            }

            const emptyTeam = teams.find(t => t.players.length === 0);
            if(emptyTeam) {
                showToast(`La squadra '${emptyTeam.name || 'senza nome'}' non ha giocatori`, 'error');
                return;
            }
        }

        setIsSubmitting(true);
        
        try {
            const created = await createMatch({
                gameId: presetGame.id,
                playedAt,
                place: place.trim() || null,
                notes: null,
                durationMinutes: null,
                isTeamBased: mode === 'team',
                teams:
                    mode === 'team'
                        ? teams.map((t) => ({
                                name: t.name.trim() || null,
                                color: t.color,
                                score: 0,
                                isWinner: false,
                                players: t.players.map((p) => ({ userId: p.userId, guestName: p.guestName })),
                            }))
                        : null,
                players:
                    mode === 'individual'
                        ? players.map((p) => ({
                                userId: p.userId,
                                guestName: p.guestName,
                                color: p.color,
                                score: 0,
                                isWinner: false,
                            }))
                        : null,
            });

            queryClient.invalidateQueries({ queryKey: ['matches'] });
            sheetRef.current?.dismiss();
            
            router.push(`/match/${created.id}`);
        } catch(error: any) {
            const message = error?.response?.data?.message ?? 'Errore durante la creazione';
            showToast(message, 'error');
        } finally {
            setIsSubmitting(false);
        }
    }

	const excludeUserIds =
        identityPickerTargetTeamIndex === null
            ? players.map(p => p.userId).filter((id): id is string => id !== null)
            : teams.flatMap(t => t.players.map(p => p.userId)).filter((id): id is string => id !== null);

    const excludeGuestNames =
        identityPickerTargetTeamIndex === null
            ? players.map(p => p.guestName).filter((n): n is string => n !== null)
            : teams.flatMap(t => t.players.map(p => p.guestName)).filter((n): n is string => n !== null);

    const isValid =
        presetGame !== null &&
        (mode === 'individual'
            ? players.length > 0
            : teams.length >= 2 && teams.every(t => t.players.length > 0));

	return (
        <>
            <AppBottomSheet ref = { sheetRef }>
                <BottomSheetScrollView contentContainerStyle = {{ padding: 16, paddingBottom: 32 }}>
                    <Text className = 'mb-4 font-display text-xl text-foreground'>Nuova Partita</Text>
                    {presetGame ? (
                        <Pressable onPress = { handleOpenGamePicker } className = 'mb-4 flex-row items-center gap-3 rounded-2xl border border-border bg-card p-2 active:bg-[#DDD8CE]'>
                            <View style = {{ width: 52, height: 52 }} className = 'overflow-hidden rounded-xl bg-secondary'>
                                {presetGame.thumbnailUrl ? (
                                    <Image source = {{ uri: presetGame.thumbnailUrl }} style = {{ width: 52, height: 52 }}/>
                                ) : (
                                    <View className = 'h-full w-full items-center justify-center'>
                                        <Dices size = { 18 } color = '#736E65'/>
                                    </View>
                                )}
                            </View>
                            <Text className = 'flex-1 font-medium text-sm text-foreground' numberOfLines = { 1 }>
                                {presetGame.name}
                            </Text>
                            <Text className = 'text-xs text-card rounded-lg py-2 px-3 bg-[#C45135]'>Cambia</Text>
                        </Pressable>
                    ) : (
                        <Pressable onPress = { handleOpenGamePicker } className = 'mb-4 flex-row items-center gap-2 rounded-xl border border-dashed border-border px-3 py-3 active:border-solid active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: '#C45135', borderColor: '#C45135' }] }>
                            {({ pressed }) => (
                                <>
                                    <Dices size = { 16 } color = { pressed ? '#FFFFFF' : '#736E65' }/>
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
                        <Input value = { place } onChangeText = { setPlace } placeholder = 'Es. Casa di Ale' className = 'h-11'/>
                    </View>
                    <Text className = 'mb-2 text-xs uppercase tracking-wide text-muted-foreground font-semibold'>Giocatori</Text>
					<View className = 'mb-4'>
						<SegmentedControl options = { MODE_OPTIONS } selected = { mode } onSelect = { setMode }/>
					</View>
					{mode === 'individual' && (
                        <View className = 'mb-4 gap-2'>
                            {players.map((player, index) => (
                                <View key = { index } className = 'flex-row items-center gap-2.5 rounded-xl border border-border bg-card px-3 py-2.5'>
                                    <View style = {{ width: 32, height: 32 }} className = 'overflow-hidden rounded-full bg-secondary'>
                                        {player.avatarUrl ? (
                                            <Image source = {{ uri: player.avatarUrl }} style = {{ width: 32, height: 32 }}/>
                                        ) : (
                                            <View className = 'h-full w-full items-center justify-center'>
                                                <User size = { 14 } color = '#736E65'/>
                                            </View>
                                        )}
                                    </View>
                                    <Text className = 'flex-1 text-sm text-foreground' numberOfLines = { 1 }>
                                        {player.displayName}
                                        {player.userId === user?.id && <Text className = 'text-xs text-primary font-semibold'> (io)</Text>}
                                    </Text>
                                    <ColorSwatchPicker value = { player.color } onChange = { color => handleColorChange(index, color) }/>
                                    {player.userId !== user?.id && (
                                        <Pressable onPress = { () => handleRemovePlayer(index) } hitSlop = { 8 } className = 'active:rounded-full active:bg-primary/90 p-2'>
                                            {({ pressed }) => (
                                                <X size = { 16 } color = { pressed ? '#FFFFFF' : '#736E65'}/>
                                            )}
                                        </Pressable>
                                        
                                    )}
                                </View>
                            ))}
                            <Pressable onPress = { () => handleOpenIdentityPicker(null) } className = 'flex-row items-center justify-center gap-2 rounded-xl border border-dashed active:border-solid border-border px-3 py-2.5 active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: '#C45135', borderColor: '#C45135' }] }>
                                {({ pressed }) => (
                                    <>
                                        <Plus size = { 16 } color = { pressed ? '#FFFFFF' : '#736E65' }/>
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
                                        <Pressable onPress = { () => handleRemoveTeam(teamIndex) } hitSlop = { 8 } className = 'active:rounded-full active:bg-primary/90 p-2'>
                                            {({ pressed }) => (
                                                <Trash2 size = { 16 } color = { pressed ? '#FFFFFF' : '#736E65'}/>
                                            )}
                                        </Pressable>
                                    </View>
                                    <View className = 'gap-1.5'>
                                        {team.players.map((player, playerIndex) => (
                                            <View key = { playerIndex } className = 'flex-row items-center gap-2 rounded-lg bg-secondary px-2.5 py-1.5'>
                                                <View style = {{ width: 24, height: 24 }} className = 'overflow-hidden rounded-full bg-card'>
                                                    {player.avatarUrl ? (
                                                        <Image source = {{ uri: player.avatarUrl }} style = {{ width: 24, height: 24 }}/>
                                                    ) : (
                                                        <View className = 'h-full w-full items-center justify-center'>
                                                            <User size = { 12 } color = '#736E65'/>
                                                        </View>
                                                    )}
                                                </View>
                                                <Text className = 'flex-1 text-xs text-foreground' numberOfLines = { 1 }>
                                                    {player.displayName}
                                                </Text>
                                                <Pressable onPress = { () => handleRemoveTeamPlayer(teamIndex, playerIndex) } hitSlop = { 8 }>
                                                    <X size = { 14 } color = '#736E65'/>
                                                </Pressable>
                                            </View>
                                        ))}
                                    </View>
                                    <View className='mt-2 flex-row gap-1.5'>
                                        <Pressable onPress = { () => handleToggleSelfInTeam(teamIndex) } className = { `flex-1 flex-row items-center justify-center gap-1.5 rounded-xl px-3 py-2.5 border ${isSelfInTeam(teamIndex) ? 'border-solid bg-[#C45135]' : 'border-dashed border-border'} active:border-solid active:bg-primary/90 active:border-primary/90` }>
                                            {({ pressed }) => (
                                                <>
                                                    <UserCheck size={14} color = { pressed ? '#FFFFFF' : isSelfInTeam(teamIndex) ? '#FFFFFF' : '#736E65' }/>
                                                    <Text className = { `text-sm ${pressed ? 'text-white' : isSelfInTeam(teamIndex) ? 'text-white' : 'text-muted-foreground'}` }>
                                                        {isSelfInTeam(teamIndex) ? 'Sei in questa squadra' : 'Sono qui'}
                                                    </Text>
                                                </>
                                            )}
                                        </Pressable>
                                       <Pressable onPress = { () => handleOpenIdentityPicker(teamIndex) } className = 'flex-row flex-1 items-center justify-center gap-1.5 rounded-xl border border-dashed active:border-solid border-border px-3 py-2.5 active:bg-primary/90 active:border-primary/90'>
                                            {({ pressed }) => (
                                                <>
                                                    <Plus size = { 14 } color = { pressed ? '#FFFFFF' : '#736E65' }/>
                                                    <Text className = { `text-sm ${pressed ? 'text-white' : 'text-muted-foreground'}` }>
                                                        Aggiungi giocatore
                                                    </Text>
                                                </>
                                            )}
                                        </Pressable>
                                    </View>
                                </View>
                            ))}
                            <Pressable onPress = { handleAddTeam } className = 'flex-row items-center justify-center gap-2 rounded-xl border border-dashed active:border-solid border-border px-3 py-2.5 active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: '#C45135', borderColor: '#C45135' }] }>
                                {({ pressed }) => (
                                    <>
                                        <Plus size = { 16 } color = { pressed ? '#FFFFFF' : '#736E65' }/>
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
                    <Text onPress = { () => setIsFingerPickerOpen(true) } className = 'text-sm text-center active:underline text-[#C45135] font-medium mt-2'>
                        {mode === 'team' ? 'Scegli la squadra iniziale' : 'Scegli giocatore iniziale'}
                    </Text>
                </BottomSheetScrollView>
            </AppBottomSheet>
            <GamePickerSheet ref = { gamePickerRef } onSelect = { handleGameSelected } onBack = { () => { gamePickerRef.current?.dismiss(); sheetRef.current?.present(); } }/>
            <PlayerIdentityPickerSheet ref = { identityPickerRef } excludeUserIds = { excludeUserIds } excludeGuestNames = { excludeGuestNames } onConfirm = { handleIdentitiesConfirmed } onBack = { () => { identityPickerRef.current?.dismiss(); sheetRef.current?.present(); } }/>
            <FingerOrderPicker visible = { isFingerPickerOpen } onClose = { () => setIsFingerPickerOpen(false) }/>
        </>
	)
});

RegisterMatchSheet.displayName = 'RegisterMatchSheet';

export default RegisterMatchSheet;