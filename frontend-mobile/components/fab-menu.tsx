import { useState } from 'react';
import { View, Modal, Pressable } from 'react-native';
import { Plus, X } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';

export interface FabMenuAction {
	label: string;
	icon: React.ReactNode;
	onPress: () => void;
}

interface FabMenuProps {
	actions: FabMenuAction[];
}

const FabMenu = ({ actions }: FabMenuProps) => {
	const [isOpen, setIsOpen] = useState(false);

	function handleActionPress(action: FabMenuAction) {
		setIsOpen(false);
		action.onPress();
	}

	return (
		<>
			<Button size = 'icon' onPress = { () => setIsOpen(true) } className = 'absolute bottom-6 right-6 h-14 w-14 rounded-full bg-accent shadow-lg'>
				<Plus size = { 26 } className = 'text-accent-foreground'/>
			</Button>
			<Modal visible = { isOpen } transparent animationType = 'fade' onRequestClose = { () => setIsOpen(false) }>
				<Pressable className = 'flex-1 bg-black/40' onPress = { () => setIsOpen(false) }>
					<View className = 'flex-1 justify-end'>
						<View className = 'items-end gap-3 px-6 pb-28'>
							{actions.map((action, index) => (
								<Pressable key = { index } onPress = { () => handleActionPress(action) } className = 'flex-row items-center gap-3 rounded-full bg-card px-4 py-3 shadow-md'>
									<Text className = 'text-sm text-foreground'>{action.label}</Text>
									{action.icon}
								</Pressable>
							))}
						</View>
					</View>
					<Button size = 'icon' onPress = { () => setIsOpen(false) } className = 'absolute bottom-6 right-6 h-14 w-14 rounded-full bg-accent shadow-lg'>
						<X size = { 26 } className = 'text-accent-foreground'/>
					</Button>
				</Pressable>
			</Modal>
		</>
	)
}

export default FabMenu; 