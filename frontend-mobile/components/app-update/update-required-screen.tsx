import { ScrollView, View } from 'react-native';
import { Image } from 'expo-image';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Text } from '@/components/ui/text';
import ReleaseNotes from '@/components/app-update/release-notes';
import UpdateButton from '@/components/app-update/update-button';

import { AppVersionInfo } from '@/types/app-version';

const APP_ICON = require('@/assets/images/icon.png');

interface UpdateRequiredScreenProps {
	info: AppVersionInfo;
	currentVersion: string;
}

const UpdateRequiredScreen = ({ info, currentVersion }: UpdateRequiredScreenProps) => {
	const insets = useSafeAreaInsets();

	return (
		<View className = 'flex-1 bg-background' style = {{ paddingTop: insets.top, paddingBottom: insets.bottom + 16 }}>
			<ScrollView contentContainerStyle = {{ padding: 24, gap: 28 }}>
				<View className = 'items-center gap-3 pt-10'>
					<Image source = { APP_ICON } style = {{ width: 88, height: 88, borderRadius: 22 }} contentFit = 'cover'/>
					<Text className = 'mt-2 text-center font-display text-2xl text-foreground'>Aggiornamento richiesto</Text>
					<Text className = 'text-center text-sm leading-5 text-muted-foreground'>
						La versione {currentVersion} non è più supportata. Installa la versione {info.latestVersion} per continuare a usare Let's Table.
					</Text>
				</View>
				{!!info.releaseNotes && (
					<View className = 'gap-3'>
						<Text className = 'text-xs font-semibold uppercase tracking-wide text-muted-foreground'>Cosa c'è di nuovo</Text>
						<ReleaseNotes markdown = { info.releaseNotes }/>
					</View>
				)}
			</ScrollView>
			<View className = 'px-6 pt-3'>
				<UpdateButton info = { info }/>
			</View>
		</View>
	)
}

export default UpdateRequiredScreen;