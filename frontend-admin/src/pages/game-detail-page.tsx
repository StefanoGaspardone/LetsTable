/* eslint-disable react-hooks/exhaustive-deps */
/* eslint-disable react-hooks/set-state-in-effect */

import { useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { RefreshCw, Loader2, Puzzle, Users, Clock, Calendar, Gauge, Star, Trophy, FileText, Trash2, ExternalLink } from 'lucide-react';

import { getGame, forceRefreshGame, listRuleFiles, deleteRuleFile } from '@/apis/game';

import { Button } from '@/components/ui/button';
import StatsCard from '@/components/dashboard/stats-card';

import type { AdminGame, AdminUploadedFile } from '@/types/game';

const formatBytes = (bytes: number): string => {
	if(bytes < 1024) return `${bytes} B`;
	if(bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
	return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const GameDetailPage = () => {
	const { id } = useParams<{ id: string }>();

	const [game, setGame] = useState<AdminGame | null>(null);
	const [ruleFiles, setRuleFiles] = useState<AdminUploadedFile[]>([]);
	const [isLoading, setIsLoading] = useState(true);
	const [isRefreshing, setIsRefreshing] = useState(false);
	const [deletingFileId, setDeletingFileId] = useState<string | null>(null);

	const load = async () => {
		if(!id) return;

		setIsLoading(true);

		try {
			const [gameData, filesData] = await Promise.all([getGame(id), listRuleFiles(id)]);
			if(gameData) setGame(gameData);
			if(filesData) setRuleFiles(filesData);
		} finally {
			setIsLoading(false);
		}
	}

	useEffect(() => {
		load();
	}, [id]);

	const handleRefresh = async () => {
		if(!game) return;

		setIsRefreshing(true);

		try {
			const updated = await forceRefreshGame(game.bggId);
			if(updated) setGame(updated);
		} finally {
			setIsRefreshing(false);
		}
	}

	const handleDeleteFile = async (fileId: string) => {
		if(!id) return;

		setDeletingFileId(fileId);

		try {
			await deleteRuleFile(id, fileId);
			setRuleFiles(prev => prev.filter(f => f.id !== fileId));
		} finally {
			setDeletingFileId(null);
		}
	}

	if(isLoading) {
		return (
			<div className = 'flex h-screen items-center justify-center'>
				<Loader2 className = 'text-muted-foreground h-6 w-6 animate-spin'/>
			</div>
		)
	}

	if(!game) {
		return (
			<div className = 'flex h-screen items-center justify-center'>
				<p className = 'text-muted-foreground text-sm'>Gioco non trovato</p>
			</div>
		)
	}

	return (
		<div className = 'flex h-screen flex-col gap-8 overflow-y-auto p-8'>
			<div className = 'flex items-start justify-between gap-4'>
				<div className = 'flex items-center gap-4'>
					<div className = 'bg-muted h-24 w-24 shrink-0 overflow-hidden rounded-lg border border-border'>
						{game.thumbnailUrl ? (
							<img src = { game.thumbnailUrl } alt = { game.name } className = 'h-full w-full object-cover'/>
						) : null}
					</div>
					<div className = 'flex flex-col gap-1.5'>
						<div className = 'flex items-center gap-2'>
							<h1 className = 'font-heading text-2xl font-bold'>{game.name}</h1>
							{game.isExpansion && (
								<span className = 'bg-primary/10 text-primary inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium'>
									<Puzzle className = 'h-3 w-3'/>
									Espansione
								</span>
							)}
						</div>
						<p className = 'text-muted-foreground text-sm'>BGG ID: {game.bggId}</p>
						<a href = { `https://boardgamegeek.com/boardgame/${game.bggId}` } target = '_blank' rel = 'noreferrer' className = 'text-primary inline-flex items-center gap-1 text-sm hover:underline'>
							Visita su BoardGameGeek
							<ExternalLink className = 'h-3.5 w-3.5'/>
						</a>
					</div>
				</div>
				<Button variant = 'outline' className = 'bg-card cursor-pointer' onClick = { handleRefresh } disabled = { isRefreshing }>
					{isRefreshing ? <Loader2 className = 'h-4 w-4 animate-spin'/> : <RefreshCw className = 'h-4 w-4'/>}
					Forza refresh
				</Button>
			</div>
			<div className = 'grid grid-cols-2 gap-4 sm:grid-cols-4'>
				<StatsCard label = 'Rank Hotness' value = { game.rank ?? 'N/A' } icon = { Trophy }/>
				<StatsCard label = 'Rank BGG' value = { game.bggRank ?? 'N/A' } icon = { Star }/>
				<StatsCard label = 'Voto medio' value = { game.avgRating != null ? game.avgRating.toFixed(2) : 'N/A' } icon = { Gauge }/>
				<StatsCard label = 'Difficoltà' value = { game.difficulty != null ? `${game.difficulty.toFixed(1)} / 5` : 'N/A' } icon = { Gauge }/>
			</div>
			<div className = 'rounded-lg border border-border bg-card p-5'>
				<h2 className = 'mb-4 font-heading text-lg font-semibold'>Dettagli</h2>
				<div className = 'grid grid-cols-2 gap-4 sm:grid-cols-4'>
					<div className = 'flex flex-col gap-1.5 rounded-md bg-background px-3 py-2.5'>
						<div className = 'text-muted-foreground flex items-center gap-1.5 text-xs uppercase tracking-wide'>
							<Users className = 'h-3.5 w-3.5'/>
							Giocatori
						</div>
						<span className = 'text-sm font-medium'>
							{game.minPlayers != null && game.maxPlayers != null
								? (game.minPlayers === game.maxPlayers ? game.minPlayers : `${game.minPlayers}-${game.maxPlayers}`)
								: 'N/A'}
						</span>
					</div>
					<div className = 'flex flex-col gap-1.5 rounded-md bg-background px-3 py-2.5'>
						<div className = 'text-muted-foreground flex items-center gap-1.5 text-xs uppercase tracking-wide'>
							<Clock className = 'h-3.5 w-3.5'/>
							Durata
						</div>
						<span className = 'text-sm font-medium'>{game.playingTimeMinutes != null ? `${game.playingTimeMinutes} min` : 'N/A'}</span>
					</div>
					<div className = 'flex flex-col gap-1.5 rounded-md bg-background px-3 py-2.5'>
						<div className = 'text-muted-foreground flex items-center gap-1.5 text-xs uppercase tracking-wide'>
							<Calendar className = 'h-3.5 w-3.5'/>
							Anno
						</div>
						<span className = 'text-sm font-medium'>{game.yearPublished ?? 'N/A'}</span>
					</div>
					<div className = 'flex flex-col gap-1.5 rounded-md bg-background px-3 py-2.5'>
						<div className = 'text-muted-foreground flex items-center gap-1.5 text-xs uppercase tracking-wide'>
							<Puzzle className = 'h-3.5 w-3.5'/>
							Espansioni
						</div>
						<span className = 'text-sm font-medium'>{game.expansions ?? 'N/A'}</span>
					</div>
				</div>
			</div>
			{game.description && (
				<div className = 'rounded-lg border border-border bg-card p-5'>
					<h2 className = 'mb-3 font-heading text-lg font-semibold'>Descrizione</h2>
					<p className = 'text-muted-foreground text-sm leading-relaxed whitespace-pre-line'>{game.description}</p>
				</div>
			)}
			{(game.designers.length > 0 || game.artists.length > 0 || game.publishers.length > 0) && (
				<div className = 'rounded-lg border border-border bg-card p-5'>
					<h2 className = 'mb-3 font-heading text-lg font-semibold'>Crediti</h2>
					<div className = 'flex flex-col gap-2 text-sm'>
						{game.designers.length > 0 && (
							<div><span className = 'text-muted-foreground'>Design: </span>{game.designers.join(', ')}</div>
						)}
						{game.artists.length > 0 && (
							<div><span className = 'text-muted-foreground'>Illustrazioni: </span>{game.artists.join(', ')}</div>
						)}
						{game.publishers.length > 0 && (
							<div><span className = 'text-muted-foreground'>Editori: </span>{game.publishers.join(', ')}</div>
						)}
					</div>
				</div>
			)}
			{game.sleeves.length > 0 && (
				<div className = 'rounded-lg border border-border bg-card p-5'>
					<h2 className = 'mb-3 font-heading text-lg font-semibold'>Sleeve/Componenti</h2>
					<div className = 'flex flex-col gap-2'>
						{game.sleeves.map(sleeve => (
							<div key = { sleeve.id } className = 'flex items-center justify-between rounded-md bg-secondary px-3 py-2 text-sm'>
								<span>{sleeve.name ?? 'Componente'}</span>
								<span className = 'text-muted-foreground'>
									{sleeve.width != null && sleeve.height != null ? `${sleeve.width}×${sleeve.height} mm` : ''}
									{sleeve.quantity != null && ` · ${sleeve.quantity} pz ${sleeve.quantityNote !== null ? ` (${sleeve.quantityNote})` : ''}`}
								</span>
							</div>
						))}
					</div>
				</div>
			)}
			<div className = 'rounded-lg border border-border bg-card p-5'>
				<h2 className = 'mb-3 font-heading text-lg font-semibold'>File Regolamento</h2>
				{ruleFiles.length === 0 ? (
					<p className = 'text-muted-foreground text-sm'>Nessun file caricato per questo gioco</p>
				) : (
					<div className = 'flex flex-col gap-2'>
						{ruleFiles.map(file => (
							<div key = { file.id } className = 'flex items-center gap-3 rounded-md bg-secondary px-3 py-2'>
								<FileText className = 'text-muted-foreground h-4 w-4 shrink-0'/>
								<div className = 'flex-1'>
									<p className = 'text-sm font-medium'>{file.fileName}</p>
									<p className = 'text-muted-foreground text-xs'>
										{formatBytes(file.sizeBytes)}
										{file.uploadedByUsername && ` · caricato da ${file.uploadedByUsername}`}
									</p>
								</div>
								<Button variant = 'ghost' size = 'icon' className = 'h-8 w-8 cursor-pointer' onClick = { () => handleDeleteFile(file.id) } disabled = { deletingFileId === file.id }>
									{deletingFileId === file.id ? <Loader2 className = 'h-4 w-4 animate-spin'/> : <Trash2 className = 'h-4 w-4'/>}
								</Button>
							</div>
						))}
					</div>
				)}
			</div>
		</div>
	)
}

export default GameDetailPage;