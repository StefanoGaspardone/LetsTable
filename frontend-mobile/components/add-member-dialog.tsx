import { useState } from 'react';
import { View, FlatList, Pressable } from 'react-native';
import { UserPlus } from 'lucide-react-native';
import { useQuery } from '@tanstack/react-query';

import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

import { useDebounce } from '@/hooks/use-debounce';

import { searchUser } from '@/api/user';

import { User } from '@/types/user';

interface AddMemberDialogProps {
	onAdd: (userId: string) => Promise<void>;
}

const AddMemberDialog = ({ onAdd }: AddMemberDialogProps) => {
	const [query, setQuery] = useState('');
	const [isOpen, setIsOpen] = useState(false);

	const debouncedQuery = useDebounce(query);

	const { data: results } = useQuery({
		queryKey: ['users', 'search', debouncedQuery],
		queryFn: () => searchUser(debouncedQuery),
		enabled: debouncedQuery.length > 0,
	});

	const handleAdd = async (user: User) => {
		await onAdd(user.id);

		setQuery('');
		setIsOpen(false);
	}

	return (
		<Dialog open = { isOpen } onOpenChange = { setIsOpen }>
			<DialogTrigger asChild>
				<Button variant = 'outline' size = 'icon'>
					<UserPlus size = { 20 } className = 'text-foreground'/>
				</Button>
			</DialogTrigger>
			<DialogContent>
				<DialogHeader>
					<DialogTitle>
						<Text>Aggiungi membro</Text>
					</DialogTitle>
				</DialogHeader>
				<View className = 'gap-3 py-2'>
					<Input placeholder = 'Cerca per username' value = { query } onChangeText = { setQuery } autoCapitalize = 'none' autoFocus/>
					<FlatList data = { results ?? [] } keyExtractor = { item => item.id } style = {{ maxHeight: 240 }}
                        renderItem = {({ item }) => (
							<Pressable onPress = { () => handleAdd(item) } className = 'border-b border-border py-3'>
								<Text className = 'text-foreground'>{item.username}</Text>
							</Pressable>
						)}
						ListEmptyComponent = {
							debouncedQuery.length > 0 ? (
								<Text className = 'py-4 text-center text-sm text-muted-foreground'>
									Nessun utente trovato
								</Text>
							) : null
						}
					/>
				</View>
			</DialogContent>
		</Dialog>
	)
}

export default AddMemberDialog;