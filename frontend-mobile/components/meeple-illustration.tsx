import Svg, { Path } from 'react-native-svg';

interface MeepleIllustrationProps {
	size?: number;
	color?: string;
}

const MeepleIllustration = ({ size = 160, color = '#1F5C4C' }: MeepleIllustrationProps) => {
	return (
		<Svg width = { size } height = { size } viewBox = '0 0 100 100' fill = 'none'>
			<Path d = 'M50 8c-9 0-16 7-16 16 0 6 3 11 8 14-10 3-18 11-22 21L8 84c-2 5 2 8 6 8h14l3-9v9c0 3 2 5 5 5h28c3 0 5-2 5-5v-9l3 9h14c4 0 8-3 6-8l-12-25c-4-10-12-18-22-21 5-3 8-8 8-14 0-9-7-16-16-16z' fill = { color }/>
		</Svg>
	)
}

export default MeepleIllustration;