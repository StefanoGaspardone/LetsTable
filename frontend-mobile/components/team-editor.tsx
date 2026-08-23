import { View, Pressable, Alert } from 'react-native';
import { Trash2, Crown, X, UserCheck } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import PlayerIdentityPicker, { PlayerIdentity } from '@/components/player-identity-picker';
import ColorSwatchPicker from '@/components/color-swatch-picker';

import { identityKey } from '@/lib/identity';

export interface TeamFormValue {
	name: string;
	color: string;
	score: string;
	isWinner: boolean;
	players: PlayerIdentity[];
}

interface TeamEditorProps {
	value: TeamFormValue;
	onChange: (value: TeamFormValue) => void;
	onRemove: () => void;
	showScoring?: boolean;
	existingKeys: string[];
	isSelfInTeam: boolean;
	onToggleSelf: () => void;
}

const TeamEditor = ({ value, onChange, onRemove, showScoring = true, existingKeys, isSelfInTeam, onToggleSelf }: TeamEditorProps) => {
	function addPlayer(identity: PlayerIdentity) {
		const key = identityKey(identity);
		
		if(existingKeys.includes(key)) {
			Alert.alert('Giocatore già presente', 'Questo giocatore è già in una squadra.');
			return;
		}
		
		onChange({ ...value, players: [...value.players, identity] });
	}

	const removePlayer = (index: number) => {
		onChange({ ...value, players: value.players.filter((_, i) => i !== index) });
	}

	return (
		<View className = 'mb-4 rounded-lg border border-border p-3'>
			<View className = 'mb-2 flex-row items-center justify-between'>
				<Input placeholder = 'Nome squadra' value = { value.name } onChangeText = { name => onChange({ ...value, name }) } className = 'flex-1'/>
				<Pressable onPress = { onRemove } hitSlop = { 8 } className = 'ml-2'>
					<Trash2 size = { 18 } className = 'text-destructive'/>
				</Pressable>
			</View>
			<ColorSwatchPicker value = { value.color } onChange = { color => onChange({ ...value, color }) }/>
			{showScoring && (
				<View className = 'mt-3 flex-row items-center gap-3'>
					<Input placeholder = 'Punteggio' keyboardType = 'number-pad' value = { value.score } onChangeText = { score => onChange({ ...value, score }) } className = 'flex-1'/>
					<Pressable onPress = { () => onChange({ ...value, isWinner: !value.isWinner }) } className = 'flex-row items-center gap-1.5 rounded-lg border border-border px-3 py-2'>
						<Crown size = { 16 } className = { value.isWinner ? 'text-accent' : 'text-muted-foreground' }/>
						<Text className = 'text-sm text-foreground'>Vincitrice</Text>
					</Pressable>
				</View>
			)}
			<Pressable
				onPress = { onToggleSelf }
				className = { `mt-3 flex-row items-center gap-2 self-start rounded-full border px-3 py-1.5 ${isSelfInTeam ? 'border-primary bg-primary' : 'border-border'}` }>
				<UserCheck size = { 14 } className = { isSelfInTeam ? 'text-primary-foreground' : 'text-muted-foreground' }/>
				<Text className = { `text-xs ${isSelfInTeam ? 'text-primary-foreground' : 'text-muted-foreground'}` }>
					Appartengo a questa squadra
				</Text>
			</Pressable>
			<View className = 'mt-3'>
				{value.players.map((player, index) => (
					<View key = { index } className = 'mb-1.5 flex-row items-center justify-between rounded-md bg-secondary px-3 py-2'>
						<Text className = 'text-sm text-foreground'>{player.displayName}</Text>
						<Pressable onPress = { () => removePlayer(index) } hitSlop = { 8 }>
							<X size = { 14 } className = 'text-muted-foreground'/>
						</Pressable>
					</View>
				))}
				<PlayerIdentityPicker value = { null } onChange = { addPlayer }/>
			</View>
		</View>
	)
} 

export default TeamEditor;