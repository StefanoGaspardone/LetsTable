export const formatDuration = (minutes: number | null | undefined): string => {
    if(minutes == null || minutes <= 0) return '';

    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;

    if(hours === 0) return `${remainingMinutes}m`;
    if(remainingMinutes === 0) return `${hours}h`;

    return `${hours}h ${remainingMinutes}m`;
}