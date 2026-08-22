import { View, Pressable } from 'react-native';
import { Trash2, Crown } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import PlayerIdentityPicker, { PlayerIdentity } from '@/components/player-identity-picker';
import ColorSwatchPicker from '@/components/color-switch-picker';

export interface IndividualPlayerFormValue {
	identity: PlayerIdentity | null;
	color: string;
	score: string;
	isWinner: boolean;
}

interface IndividualPlayerEditorProps {
	value: IndividualPlayerFormValue;
	onChange: (value: IndividualPlayerFormValue) => void;
	onRemove: () => void;
	showScoring?: boolean;
}

const IndividualPlayerEditor = ({ value, onChange, onRemove, showScoring = true }: IndividualPlayerEditorProps) => {
	return (
		<View className = 'mb-3 rounded-lg border border-border p-3'>
			<View className = 'mb-2 flex-row items-center justify-between'>
				<PlayerIdentityPicker value = { value.identity } onChange = { identity => onChange({ ...value, identity }) }/>
				<Pressable onPress = { onRemove } hitSlop = { 8 }>
					<Trash2 size = { 18 } className = 'text-destructive'/>
				</Pressable>
			</View>
			<ColorSwatchPicker value = { value.color } onChange = { color => onChange({ ...value, color }) }/>
			{showScoring && (
				<View className = 'mt-3 flex-row items-center gap-3'>
					<Input placeholder = 'Punteggio' keyboardType = 'number-pad' value = { value.score } onChangeText = { score => onChange({ ...value, score }) } className = 'flex-1'/>
					<Pressable onPress = { () => onChange({ ...value, isWinner: !value.isWinner }) } className = 'flex-row items-center gap-1.5 rounded-lg border border-border px-3 py-2'>
						<Crown size = { 16 } className = { value.isWinner ? 'text-accent' : 'text-muted-foreground' }/>
						<Text className = 'text-sm text-foreground'>Vincitore</Text>
					</Pressable>
				</View>
			)}
		</View>
	)
}

export default IndividualPlayerEditor;