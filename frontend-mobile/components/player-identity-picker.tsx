import { useState } from 'react';
import { View, Modal, FlatList, Pressable } from 'react-native';
import { X, UserRound } from 'lucide-react-native';
import { useQuery } from '@tanstack/react-query';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

import { useDebounce } from '@/hooks/use-debounce';

import { searchUser } from '@/api/user';

import { User } from '@/types/user';

export interface PlayerIdentity {
	userId: string | null;
	guestName: string | null;
	displayName: string;
}

interface PlayerIdentityPickerProps {
	value: PlayerIdentity | null;
	onChange: (identity: PlayerIdentity) => void;
}

const PlayerIdentityPicker = ({ value, onChange }: PlayerIdentityPickerProps) => {
	const [isOpen, setIsOpen] = useState(false);
	const [mode, setMode] = useState<'search' | 'guest'>('search');
	const [query, setQuery] = useState('');

	const debouncedQuery = useDebounce(query);

	const { data: results } = useQuery({
		queryKey: ['users', 'search', debouncedQuery],
		queryFn: () => searchUser(debouncedQuery),
		enabled: mode === 'search' && debouncedQuery.length > 0,
	});

	const selectUser = (user: User) => {
		onChange({ userId: user.id, guestName: null, displayName: user.username });
		setIsOpen(false);
		setQuery('');
	}

	const confirmGuest = () => {
		if(!query.trim()) return;
		
        onChange({ userId: null, guestName: query.trim(), displayName: query.trim() });
		
        setIsOpen(false);
		setQuery('');
	}

	return (
		<>
			<Pressable onPress = { () => setIsOpen(true) } className = 'flex-row items-center gap-2 rounded-lg border border-border bg-card px-3 py-2'>
				<UserRound size = { 16 } className = 'text-muted-foreground'/>
				<Text className = { value ? 'text-foreground' : 'text-muted-foreground' }>
					{value?.displayName ?? 'Seleziona giocatore'}
				</Text>
			</Pressable>
			<Modal visible = { isOpen } transparent animationType = 'slide' onRequestClose = { () => setIsOpen(false) }>
				<View className = 'flex-1 justify-end bg-black/40'>
					<View className = 'max-h-[70%] rounded-t-2xl bg-background px-4 pb-8 pt-4'>
						<View className = 'mb-3 flex-row items-center justify-between'>
							<Text className = 'font-display text-lg text-foreground'>Aggiungi giocatore</Text>
							<Pressable onPress = { () => setIsOpen(false) } hitSlop = { 8 }>
								<X size = { 20 } className = 'text-foreground'/>
							</Pressable>
						</View>
						<View className = 'mb-3 flex-row gap-2'>
							<Button variant = { mode === 'search' ? 'default' : 'outline' } size = 'sm' className = 'flex-1' onPress = { () => setMode('search') }>
								<Text>Utente registrato</Text>
							</Button>
							<Button variant = { mode === 'guest' ? 'default' : 'outline' } size = 'sm' className = 'flex-1' onPress = { () => setMode('guest') }>
								<Text>Ospite</Text>
							</Button>
						</View>
						<Input placeholder = { mode === 'search' ? 'Cerca per username' : 'Nome ospite' } value = { query } onChangeText = { setQuery } autoCapitalize = 'none' autoFocus/>
						{mode === 'search' ? (
							<FlatList data = { results ?? [] } keyExtractor = { item => item.id } style = {{ marginTop: 12, maxHeight: 240 }}
								renderItem = { ({ item }) => (
									<Pressable onPress = { () => selectUser(item) } className = 'border-b border-border py-3'>
										<Text className = 'text-foreground'>{item.username}</Text>
									</Pressable>
								)}
							/>
						) : (
							<Button className = 'mt-3' onPress = { confirmGuest } disabled = { !query.trim() }>
								<Text>Conferma</Text>
							</Button>
						)}
					</View>
				</View>
			</Modal>
		</>
	)
}

export default PlayerIdentityPicker;