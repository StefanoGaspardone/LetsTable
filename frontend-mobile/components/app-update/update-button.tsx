import { View } from 'react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';

import { useUpdateInstaller } from '@/hooks/use-app-update';

import { isPlayDistribution } from '@/lib/app-version';

import { AppVersionInfo } from '@/types/app-version';

interface UpdateButtonProps {
	info: AppVersionInfo;
}

const UpdateButton = ({ info }: UpdateButtonProps) => {
	const { start, progress, isDownloading } = useUpdateInstaller(info);

	const percent = Math.round((progress ?? 0) * 100);
	const label = isDownloading ? `Download in corso... ${percent}%` : isPlayDistribution() ? 'Apri il Play Store' : 'Aggiorna ora';

	return (
		<View className = 'gap-2'>
			<Button className = 'h-14 rounded-full' onPress = { start } disabled = { isDownloading }>
				<Text className = 'text-base font-semibold text-primary-foreground'>{label}</Text>
			</Button>
			{isDownloading && (
				<View className = 'h-1.5 overflow-hidden rounded-full bg-secondary'>
					<View className = 'h-full rounded-full bg-primary' style = {{ width: `${percent}%` }}/>
				</View>
			)}
		</View>
	)
}

export default UpdateButton;