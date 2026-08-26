export const PALETTE = ['#FF0000', '#FF8000', '#FFFF00', '#00FF00', '#06402B', '#A52A2A', '#0000FF', '#007FFF', '#6A0DAD', '#FFC0CB', '#FFFFFF', '#000000', '#808080'];

export const getPlayerColor = (index: number): string => PALETTE[index % PALETTE.length];