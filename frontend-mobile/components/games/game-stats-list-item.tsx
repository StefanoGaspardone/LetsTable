import { memo, ReactNode } from 'react';
import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Clock, Dices, Trophy, Hourglass, Play } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { useNavigationStack } from '@/contexts/navigation-stack-context';
import { useThemeColors } from '@/hooks/use-theme-colors';
import { formatDuration } from '@/lib/time';
import { GameMatchStats } from '@/types/match';

interface GameStatsListItemProps {
    stats: GameMatchStats;
}

const Metric = ({ icon, label }: { icon: ReactNode; label: string }) => (
    <View className="flex-row items-center gap-1">
        {icon}
        <Text className="text-xs font-medium text-muted-foreground">{label}</Text>
    </View>
);

const GameStatsListItem = ({ stats }: GameStatsListItemProps) => {
    const router = useNavigationStack();
    const { colors } = useThemeColors();

    const winRatePercent = Math.round(stats.winRate * 100);
    const avgMinutes = Math.round(stats.avgMinutes);

    return (
        <Pressable
            onPress={() => router.push(`/game/${stats.bggId}`)}
            accessibilityRole="button"
            accessibilityLabel={`${stats.name}, ${stats.matchCount} partite, ${winRatePercent}% di vittorie`}
            className="flex-row items-center gap-3 rounded-2xl border border-border bg-card p-2 active:scale-[0.98] active:opacity-75"
        >
            {/* Copertina del gioco */}
            <View style={{ width: 56, height: 56 }} className="shrink-0 overflow-hidden rounded-xl bg-secondary">
                {stats.thumbnailUrl ? (
                    <Image
                        source={{ uri: stats.thumbnailUrl }}
                        style={{ width: 56, height: 56 }}
                        contentFit="cover"
                        transition={200}
                    />
                ) : (
                    <View className="h-full w-full items-center justify-center">
                        <Dices size={22} color={colors.mutedForeground} />
                    </View>
                )}
            </View>

            {/* Dettagli Centrali */}
            <View className="min-w-0 flex-1 justify-center gap-1">
                <Text className="text-base font-semibold text-foreground" numberOfLines={1}>
                    {stats.name}
                </Text>

                {/* Riga Metriche: dimensione icone e testo leggermente ridotta (12px / text-xs) per stare perfettamente in una sola riga */}
                <View className="flex-row items-center gap-2">
                    <Metric
                        icon={<Play size={12} color={colors.mutedForeground} strokeWidth={2.5} />}
                        label={`${stats.matchCount} ${stats.matchCount === 1 ? 'partita' : 'partite'}`}
                    />

                    <Text className="text-xs text-muted-foreground/30">•</Text>

                    <Metric
                        icon={<Clock size={12} color={colors.mutedForeground} strokeWidth={2.5} />}
                        label={formatDuration(avgMinutes)}
                    />

                    <Text className="text-xs text-muted-foreground/30">•</Text>

                    <Metric
                        icon={<Hourglass size={12} color={colors.mutedForeground} strokeWidth={2.5} />}
                        label={formatDuration(stats.totalMinutes)}
                    />
                </View>
            </View>

            {/* Badge Win Rate e Numero Vittorie */}
            <View className="shrink-0 items-end justify-center gap-0.5 pl-1">
                <View className="flex-row items-center gap-1.5 rounded-full border border-primary/20 bg-primary/10 px-2.5 py-1.5">
                    <Trophy size={14} color={colors.primary} />
                    <Text className="text-sm font-bold text-primary">{winRatePercent}%</Text>
                </View>
                <Text className="pr-0.5 text-[11px] font-medium text-muted-foreground">
                    {stats.wins} {stats.wins === 1 ? 'vittoria' : 'vittorie'}
                </Text>
            </View>
        </Pressable>
    );
};

export default memo(GameStatsListItem);