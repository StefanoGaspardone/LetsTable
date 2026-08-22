import { useState } from 'react';
import { View } from 'react-native';
import { Plus } from 'lucide-react-native';

import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

interface CreatePlaylistDialogProps {
	onCreate: (name: string) => Promise<void>;
}

const CreatePlaylistDialog = ({ onCreate }: CreatePlaylistDialogProps) => {
	const [name, setName] = useState('');
	const [isOpen, setIsOpen] = useState(false);
	const [isSubmitting, setIsSubmitting] = useState(false);

	const handleCreate = async () => {
		if(!name.trim()) return;
		
        setIsSubmitting(true);
		
        try {
			await onCreate(name.trim());
			
            setName('');
			setIsOpen(false);
		} finally {
			setIsSubmitting(false);
		}
	}

	return (
		<Dialog open = { isOpen } onOpenChange = { setIsOpen }>
			<DialogTrigger asChild>
				<Button variant = 'outline' size = 'icon'>
					<Plus size = { 20 } className = 'text-foreground'/>
				</Button>
			</DialogTrigger>
			<DialogContent>
				<DialogHeader>
					<DialogTitle>
						<Text>Nuova playlist</Text>
					</DialogTitle>
				</DialogHeader>
				<View className = 'gap-4 py-2'>
					<Input placeholder = 'Nome playlist' value = { name } onChangeText = { setName } autoFocus/>
					<Button onPress = { handleCreate } disabled = { !name.trim() || isSubmitting }>
						<Text>{isSubmitting ? 'Creazione...' : 'Crea e aggiungi il gioco'}</Text>
					</Button>
				</View>
			</DialogContent>
		</Dialog>
	)
}

export default CreatePlaylistDialog;