/* eslint-disable react-hooks/exhaustive-deps */

import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { RefreshCw, Loader2, Puzzle, Upload } from 'lucide-react';

import { listGames, forceRefreshGame, type ListGamesParams, uploadRankIndex } from '@/apis/game';

import { useInfiniteList } from '@/hooks/use-infinite-list';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { useSort } from '@/hooks/use-sort';

import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import { Button } from '@/components/ui/button';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import LoadMoreButton from '@/components/common/load-more-button';
import SortableTableHead from '@/components/common/sortable-table-head';

import type { AdminGame } from '@/types/game';

type SortField = 'name' | 'isExpansion' | 'bggRank' | 'lastSyncedAt';

const GamesPage = () => {
	const [search, setSearch] = useState('');
	const [expansionsOnly, setExpansionsOnly] = useState(false);
	const [refreshingBggId, setRefreshingBggId] = useState<number | null>(null);
    const [isUploadingRankIndex, setIsUploadingRankIndex] = useState(false);

	const fileInputRef = useRef<HTMLInputElement>(null);

	const { field: sortField, direction: sortDirection, handleSort, sortParam } = useSort<SortField>('name');
    
    const navigate = useNavigate();

	const debouncedSearch = useDebouncedValue(search, 400);

	const params: ListGamesParams = useMemo(() => ({
		search: debouncedSearch || undefined,
		isExpansion: expansionsOnly ? true : undefined,
		sort: sortParam,
	}), [debouncedSearch, expansionsOnly, sortParam]);

	const { content: games, isLoading, isLoadingMore, isLast, loadMore, reload, updateItem } = useInfiniteList<AdminGame, ListGamesParams>({
		fetchPage: listGames,
		params,
	});

	useEffect(() => {
		reload();
	}, [debouncedSearch, expansionsOnly, sortParam]);

	const handleRefresh = async (game: AdminGame) => {
		setRefreshingBggId(game.bggId);

		try {
			const updated = await forceRefreshGame(game.bggId);
			updateItem(g => g.id === game.id, () => updated);
		} catch {
			// swallow catch
		} finally {
			setRefreshingBggId(null);
		}
	}

    
	const handleRankIndexFileSelected = async (event: React.ChangeEvent<HTMLInputElement>) => {
		const file = event.target.files?.[0];
		if(!file) return;

		setIsUploadingRankIndex(true);

		try {
			await uploadRankIndex(file);
		} catch {
			// swallow catch
		} finally {
			setIsUploadingRankIndex(false);
			if(fileInputRef.current) fileInputRef.current.value = '';
		}
	}

	return (
		<div className = 'flex h-screen flex-col gap-8 p-8'>
			<h1 className = 'font-heading shrink-0 text-2xl font-bold'>Giochi</h1>
			<div className = 'flex shrink-0 items-center justify-between gap-4'>
				<div className = 'flex items-center gap-4'>
					<Input placeholder = 'Cerca per nome...' value = { search } onChange = { e => setSearch(e.target.value) } className = 'max-w-sm bg-card py-4.5'/>
					<div className = 'flex items-center gap-2 cursor-pointer'>
						<Switch checked = { expansionsOnly } onCheckedChange = { setExpansionsOnly } id = 'expansions-only' className = '**:data-[slot=switch-thumb]:bg-white'/>
						<label htmlFor = 'expansions-only' className = 'text-sm font-medium cursor-pointer'>Solo espansioni</label>
					</div>
				</div>
				<div>
					<input ref = { fileInputRef } type = 'file' accept = '.csv' className = 'hidden' onChange = { handleRankIndexFileSelected }/>
					<Button className = 'bg-card cursor-pointer' variant = 'outline' onClick = { () => fileInputRef.current?.click() } disabled = { isUploadingRankIndex }>
						{isUploadingRankIndex ? <Loader2 className = 'h-4 w-4 animate-spin'/> : <Upload className = 'h-4 w-4'/>}
						Carica rank index
					</Button>
				</div>
			</div>
			<div className = 'min-h-0 flex-1 overflow-y-auto rounded-lg border border-border bg-card'>
                <Table noWrapper>
                    <TableHeader className = 'bg-card sticky top-0 z-50 shadow-[0_1px_0_0_var(--border)]'>
                        <TableRow>
                            <SortableTableHead label = 'Gioco' field = 'name' activeField = { sortField } direction = { sortDirection } onSort = { handleSort }/>
                            <TableHead>BGG ID</TableHead>
                            <SortableTableHead label = 'Tipo' field = 'isExpansion' activeField = { sortField } direction = { sortDirection } onSort = { handleSort }/>
                            <SortableTableHead label = 'Rank' field = 'bggRank' activeField = { sortField } direction = { sortDirection } onSort = { handleSort }/>
                            <SortableTableHead label = 'Ultima sync' field = 'lastSyncedAt' activeField = { sortField } direction = { sortDirection } onSort = { handleSort }/>
                            <TableHead className = 'text-right'>Azioni</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {isLoading ? (
                            <TableRow>
                                <TableCell colSpan = { 6 } className = 'py-8 text-center'>
                                    <Loader2 className = 'text-muted-foreground mx-auto h-5 w-5 animate-spin'/>
                                </TableCell>
                            </TableRow>
                        ) : games.length === 0 ? (
                            <TableRow>
                                <TableCell colSpan = { 6 } className = 'text-muted-foreground py-8 text-center text-sm'>
                                    Nessun gioco trovato
                                </TableCell>
                            </TableRow>
                        ) : (
                            games.map(game => (
                                <TableRow key = { game.id } className = 'group cursor-pointer' onClick = { () => navigate(`/games/${game.id}`) }>
                                    <TableCell>
                                        <div className = 'flex items-center gap-3'>
                                            <div className = 'bg-muted h-9 w-9 shrink-0 overflow-hidden rounded'>
                                                {game.thumbnailUrl ? (
                                                    <img src = { game.thumbnailUrl } alt = { game.name } className = 'h-full w-full object-cover'/>
                                                ) : null}
                                            </div>
                                            <span className = 'font-medium group-hover:underline'>{game.name}</span>
                                        </div>
                                    </TableCell>
                                    <TableCell className = 'font-mono text-xs'>{game.bggId}</TableCell>
                                    <TableCell>
                                        {game.isExpansion ? (
                                            <span className = 'bg-primary/10 text-primary inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium'>
                                                <Puzzle className = 'h-3 w-3'/>
                                                Espansione
                                            </span>
                                        ) : (
                                            <span className = 'text-muted-foreground text-xs'>Base</span>
                                        )}
                                    </TableCell>
                                    <TableCell className = 'text-muted-foreground text-sm'>{game.bggRank ?? 'N/A'}</TableCell>
                                    <TableCell className = 'text-muted-foreground text-sm'>
                                        {new Date(game.lastSyncedAt).toLocaleDateString()}
                                    </TableCell>
                                    <TableCell className = 'text-right'>
                                        <Button variant = 'ghost' size = 'icon' className = 'h-8 w-8 cursor-pointer' onClick = { e => { e.preventDefault(); e.stopPropagation(); handleRefresh(game); } } disabled = { refreshingBggId === game.bggId }>
                                            <RefreshCw className = { `h-4 w-4 ${refreshingBggId === game.bggId ? 'animate-spin' : ''}` }/>
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            ))
                        )}
                    </TableBody>
                </Table>
			</div>

			{!isLoading && games.length > 0 && (
				<LoadMoreButton isLast = { isLast } isLoading = { isLoadingMore } onLoadMore = { loadMore }/>
			)}
		</div>
	)
}

export default GamesPage;