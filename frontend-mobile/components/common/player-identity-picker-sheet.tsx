import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { View, Pressable, TextInput } from 'react-native';
import { BottomSheetModal, BottomSheetFlatList } from '@gorhom/bottom-sheet';
import { Image } from 'expo-image';
import { Search, User as UserIcon, Plus, ChevronLeft, X } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

import { useDebounce } from '@/hooks/use-debounce';
import { useUserSearch } from '@/hooks/use-user';

export interface PickedIdentity {
	userId: string | null;
	guestName: string | null;
	displayName: string;
	avatarUrl: string | null;
}

export interface PlayerIdentityPickerSheetRef {
	present: () => void;
	dismiss: () => void;
}

interface PlayerIdentityPickerSheetProps {
	excludeUserIds: string[];
	excludeGuestNames: string[];
	onConfirm: (identities: PickedIdentity[]) => void;
    onBack: () => void;
}

const PlayerIdentityPickerSheet = forwardRef<PlayerIdentityPickerSheetRef, PlayerIdentityPickerSheetProps>(({ excludeUserIds, excludeGuestNames, onConfirm, onBack }, ref) => {
    const sheetRef = useRef<BottomSheetModal>(null);

    const [search, setSearch] = useState('');
    const [selected, setSelected] = useState<PickedIdentity[]>([]);
    const [guestNameInput, setGuestNameInput] = useState('');
    const [isAddingGuest, setIsAddingGuest] = useState(false);

    const debouncedSearch = useDebounce(search);

    useImperativeHandle(ref, () => ({
        present: () => {
            setSearch('');
            setSelected([]);
            setGuestNameInput('');
            setIsAddingGuest(false);
            sheetRef.current?.present();
        },
        dismiss: () => sheetRef.current?.dismiss(),
    }));

    const { data: users, isLoading } = useUserSearch(debouncedSearch);

    const visibleUsers = (users ?? []).filter(u => !excludeUserIds.includes(u.id));

    const isSelected = (userId: string) => selected.some(s => s.userId === userId);

    const toggleUser = (userId: string, username: string, avatarUrl: string | null) => {
        setSelected(prev =>
            prev.some(s => s.userId === userId)
                ? prev.filter(s => s.userId !== userId)
                : [...prev, { userId, guestName: null, displayName: username, avatarUrl }]
        );
    }

    const removeSelected = (identity: PickedIdentity) => {
        setSelected(prev =>
            identity.userId
                ? prev.filter(s => s.userId !== identity.userId)
                : prev.filter(s => s.guestName !== identity.guestName)
        );
    }

    const handleAddGuest = () => {
        const trimmed = guestNameInput.trim();
        if(!trimmed) return;

        const isDuplicate = excludeGuestNames.some(n => n.toLowerCase() === trimmed.toLowerCase()) || selected.some(s => s.guestName?.toLowerCase() === trimmed.toLowerCase());

        if(isDuplicate) return;

        setSelected(prev => [
            ...prev,
            { userId: null, guestName: trimmed, displayName: trimmed, avatarUrl: null },
        ]);
        setGuestNameInput('');
        setIsAddingGuest(false);
    }

    const handleConfirm = () => {
        onConfirm(selected);
        sheetRef.current?.dismiss();
    }

    return (
        <AppBottomSheet ref = { sheetRef }>
            <View className = 'px-4 pt-2'>
                <View className = 'mb-3 flex-row items-center gap-2'>
                    <Pressable onPress = { onBack } hitSlop = { 10 } className = 'h-10 w-10 items-center justify-center rounded-full bg-secondary active:bg-[#DDD8CE]'>
                        <ChevronLeft size = { 24 } color = '#736E65'/>
                    </Pressable>
                    <Text className = 'font-display text-lg text-foreground'>Aggiungi giocatori</Text>
                </View>
                <View className = 'relative mb-3'>
                    <TextInput value = { search } onChangeText = { setSearch } placeholder = 'Cerca...' autoCapitalize = 'none' className = 'h-11 rounded-2xl bg-secondary pl-10 pr-3 text-sm text-foreground'/>
                    <View className = 'pointer-events-none absolute left-3 top-0 h-full justify-center'>
                        <Search size = { 16 } color = '#736E65'/>
                    </View>
                </View>
                {selected.length > 0 && (
                    <View className = 'mb-3 flex-row flex-wrap'>
                        {selected.map((identity) => (
                            <View key = { identity.userId } style = {{ width: '25%', padding: 4 }}>
                                <Pressable onPress = { () => removeSelected(identity) } className = 'items-center gap-1.5 rounded-2xl border border-[#C45135]/40 bg-[#C45135]/5 py-3 active:scale-[0.98] active:opacity-75'>
                                    <View className = 'relative'>
                                        {identity.avatarUrl ? (
                                            <Image source = {{ uri: identity.avatarUrl }} style = {{ width: 56, height: 56, borderRadius: 28 }}/>
                                        ) : (
                                            <View className = 'h-14 w-14 items-center justify-center rounded-full bg-secondary border border-primary'>
                                                <UserIcon size = { 22 } color = '#736E65'/>
                                            </View>
                                        )}
                                        <View className = 'absolute -right-1 -top-1 h-5 w-5 items-center justify-center rounded-full bg-[#C45135]'>
                                            <X size = { 12 } color = '#FFFFFF' strokeWidth = { 3 }/>
                                        </View>
                                    </View>
                                    <Text className = 'text-center text-sm font-medium text-foreground' numberOfLines = { 1 }>
                                        {identity.displayName}
                                    </Text>
                                </Pressable>
                            </View>
                        ))}
                    </View>
                )}
                <BottomSheetFlatList data = { visibleUsers } keyExtractor = { item => item.id } numColumns = { 4 }
                    renderItem  = { ({ item }) => {
                        const selectedState = isSelected(item.id);

                        return (
                            <View style = {{ width: '25%', padding: 4 }}>
                                <Pressable onPress = { () => toggleUser(item.id, item.username, item.avatarUrl) } className = { `items-center gap-1.5 rounded-2xl border py-3 active:scale-[0.98] active:opacity-75 ${selectedState ? 'border-[#C45135] bg-[#C45135]/5' : 'border-border bg-card'}` }>
                                    {item.avatarUrl ? (
                                        <Image source = {{ uri: item.avatarUrl }} style = {{ width: 56, height: 56, borderRadius: 28 }}/>
                                    ) : (
                                        <View className = 'h-14 w-14 items-center justify-center rounded-full bg-secondary'>
                                            <UserIcon size = { 22 } color = '#736E65'/>
                                        </View>
                                    )}
                                    <Text className = 'text-center text-sm font-medium text-foreground' numberOfLines = { 1 }>
                                        {item.username}
                                    </Text>
                                </Pressable>
                            </View>
                        )
                    }}
                    contentContainerStyle = {{ paddingBottom: 12 }} ListEmptyComponent = { !isLoading && debouncedSearch.length > 0 ? (<Text className = 'py-4 text-center text-sm text-muted-foreground'>Nessun utente trovato</Text> ) : null }
                />
                {isAddingGuest ? (
                    <View className = 'mb-3 flex-row items-center gap-2'>
                        <TextInput value = { guestNameInput } onChangeText = { setGuestNameInput } placeholder = 'Nome ospite' autoFocus onSubmitEditing = { handleAddGuest } className = 'h-10 flex-1 rounded-xl border border-border bg-secondary px-3 text-sm text-foreground'/>
                        <Button size = 'sm' onPress = { handleAddGuest }>
                            <Text className = 'text-primary-foreground'>Aggiungi</Text>
                        </Button>
                    </View>
                ) : (
                    <Pressable onPress = { () => setIsAddingGuest(true) } className = 'flex-row items-center justify-center gap-2 rounded-xl border border-dashed active:border-solid border-border px-3 py-2.5 active:bg-primary/90 active:border-primary/90 mb-4' style = { ({ pressed }) => [pressed && { backgroundColor: '#C45135', borderColor: '#C45135' }] }>
                        {({ pressed }) => (
                            <>
                                <Plus size = { 16 } color = { pressed ? '#FFFFFF' : '#736E65' }/>
                                <Text className = { `text-sm ${pressed ? 'text-white' : 'text-muted-foreground'}` }>
                                    Aggiungi ospite
                                </Text>
                            </>
                        )}
                    </Pressable>
                )}
                <Button className = 'mb-4 h-12 rounded-full' onPress = { handleConfirm } disabled = { selected.length === 0 }>
                    <Text className = 'text-sm font-semibold text-primary-foreground'>
                        Conferma {selected.length > 0 ? `(${selected.length})` : ''}
                    </Text>
                </Button>
            </View>
        </AppBottomSheet>
    )
});

PlayerIdentityPickerSheet.displayName = 'PlayerIdentityPickerSheet';

export default PlayerIdentityPickerSheet;