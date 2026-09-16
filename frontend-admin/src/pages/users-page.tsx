import ComingSoon from '@/components/common/coming-soon';

const UsersPage = () => {
    return (
        <div className = 'flex h-screen flex-col gap-8 p-8'>
			<h1 className = 'font-heading shrink-0 text-2xl font-bold'>Utenti</h1>
            <ComingSoon title = 'Gestione utenti' description = 'La gestione degli utenti sarà presto disponibile qui.'/>
        </div>
    )
}
 
export default UsersPage;