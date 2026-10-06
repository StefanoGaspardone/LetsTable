export type AchievementCategory = 'MATCHES' | 'WINS' | 'STREAK' | 'COLLECTION' | 'SOCIAL';
export type AchievementTier = 'BRONZE' | 'SILVER' | 'GOLD';

export interface Achievement {
	code: string;
	category: AchievementCategory;
	tier: AchievementTier | null;
	title: string;
	description: string;
	icon: string;
	target: number;
	progress: number;
	unlocked: boolean;
	unlockedAt: string | null;
	seen: boolean;
}