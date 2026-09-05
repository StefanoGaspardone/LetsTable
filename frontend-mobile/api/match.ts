import { apiClient } from '@/api/client';

import { CreateMatchPayload, Match, MatchDayCount, UpdateMatchPayload } from '@/types/match';
import { PageDTO } from '@/types/page';

export interface ListMatchesParams {
	page: number;
	size?: number;
	gameId?: string;
	fromDate?: string;
	toDate?: string;
	sort?: string;
}

export const listMatches = async (params: ListMatchesParams): Promise<PageDTO<Match>> => {
    const { data } = await apiClient.get<PageDTO<Match>>('/matches', {
        params: {
            page: params.page,
            size: params.size ?? 20,
            gameId: params.gameId || undefined,
            fromDate: params.fromDate || undefined,
            toDate: params.toDate || undefined,
            sort: params.sort || undefined,
        },
    });
    return data;
}

export const getMatchById = async (matchId: string): Promise<Match> => {
    const { data } = await apiClient.get<Match>(`/matches/${matchId}`);
    return data;
}

export const getCalendarMatch = async (year: number, month: number): Promise<MatchDayCount[]> => {
    const { data } = await apiClient.get<MatchDayCount[]>('/matches/calendar', {
        params: { year, month },
    });
    return data;
}

export const deleteMatch = async (matchId: string): Promise<void> => {
    await apiClient.delete(`/matches/${matchId}`);
}

export const createMatch = async (payload: CreateMatchPayload): Promise<Match> => {
    const { data } = await apiClient.post<Match>('/matches', payload);
    return data;
}

export const updateMatch = async (matchId: string, payload: UpdateMatchPayload): Promise<Match> => {
    const { data } = await apiClient.put<Match>(`/matches/${matchId}`, payload);
    return data;
}