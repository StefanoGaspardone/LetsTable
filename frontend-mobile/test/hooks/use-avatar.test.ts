import { renderHook, act } from '@testing-library/react-native';
import * as ImagePicker from 'expo-image-picker';

import { deleteAvatar, uploadAvatar } from '@/api/avatar';
import { useUpdateMe } from '@/hooks/use-user';
import { useAvatarActions } from '@/hooks/use-avatar';

jest.mock('expo-image-picker', () => ({
	launchImageLibraryAsync: jest.fn(),
}));

jest.mock('@/api/avatar', () => ({
	uploadAvatar: jest.fn(),
	deleteAvatar: jest.fn(),
}));

let mockUser: { avatarId?: string | null } | null = { avatarId: 'avatar-1' };
const mockUpdateUser = jest.fn();
jest.mock('@/contexts/auth-context', () => ({
	useAuth: () => ({ user: mockUser, updateUser: mockUpdateUser }),
}));

const mockShowToast = jest.fn();
jest.mock('@/contexts/toast-context', () => ({
	useToast: () => ({ showToast: mockShowToast }),
}));

const mockConfirm = jest.fn();
jest.mock('@/contexts/confirm-dialog-context', () => ({
	useConfirmDialog: () => ({ confirm: mockConfirm }),
}));

const mockMutateAsync = jest.fn();
jest.mock('@/hooks/use-user', () => ({
	useUpdateMe: jest.fn(),
}));

const mockedLaunchImageLibraryAsync = jest.mocked(ImagePicker.launchImageLibraryAsync);
const mockedUploadAvatar = jest.mocked(uploadAvatar);
const mockedDeleteAvatar = jest.mocked(deleteAvatar);
const mockedUseUpdateMe = jest.mocked(useUpdateMe);

const pickedImage = (asset: Partial<ImagePicker.ImagePickerAsset>) => ({
	canceled: false,
	assets: [{ uri: 'file:///picked.png', fileName: 'picked.png', mimeType: 'image/png', ...asset }],
}) as any;

const axiosError = (message: string) => ({ response: { data: { message } } });

describe('useAvatarActions', () => {
	beforeEach(() => {
		jest.clearAllMocks();
		mockedLaunchImageLibraryAsync.mockReset();
		mockedUploadAvatar.mockReset().mockResolvedValue({ id: 'avatar-2', fileName: 'picked.png', contentType: 'image/png', size: 10 });
		mockedDeleteAvatar.mockReset().mockResolvedValue(undefined);
		mockMutateAsync.mockReset().mockResolvedValue(undefined);
		mockConfirm.mockReset().mockResolvedValue(true);
		mockedUseUpdateMe.mockReset().mockReturnValue({ mutateAsync: mockMutateAsync } as any);
		mockUser = { avatarId: 'avatar-1' };
	});

	it('passes updateUser to useUpdateMe', async () => {
		await renderHook(() => useAvatarActions());

		expect(mockedUseUpdateMe).toHaveBeenCalledWith(mockUpdateUser);
	});

	it('starts idle', async () => {
		const { result } = await renderHook(() => useAvatarActions());

		expect(result.current.isUploading).toBe(false);
		expect(result.current.isRemoving).toBe(false);
	});

	describe('changeAvatar', () => {
		it('opens the picker with a square crop', async () => {
			mockedLaunchImageLibraryAsync.mockResolvedValue({ canceled: true, assets: null });
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.changeAvatar());

			expect(mockedLaunchImageLibraryAsync).toHaveBeenCalledWith({
				mediaTypes: ['images'],
				allowsEditing: true,
				aspect: [1, 1],
				quality: 0.8,
			});
		});

		it('does nothing when the user cancels the picker', async () => {
			mockedLaunchImageLibraryAsync.mockResolvedValue({ canceled: true, assets: null });
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.changeAvatar());

			expect(mockedUploadAvatar).not.toHaveBeenCalled();
			expect(mockMutateAsync).not.toHaveBeenCalled();
			expect(mockShowToast).not.toHaveBeenCalled();
		});

		it('uploads the picked image, links it to the user and shows a success toast', async () => {
			mockedLaunchImageLibraryAsync.mockResolvedValue(pickedImage({}));
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.changeAvatar());

			expect(mockedUploadAvatar).toHaveBeenCalledWith('file:///picked.png', 'picked.png', 'image/png');
			expect(mockMutateAsync).toHaveBeenCalledWith({ avatarId: 'avatar-2' });
			expect(mockShowToast).toHaveBeenCalledWith('Avatar aggiornato', 'success');
			expect(result.current.isUploading).toBe(false);
		});

		it('falls back to a default file name and mime type', async () => {
			mockedLaunchImageLibraryAsync.mockResolvedValue(pickedImage({ fileName: null, mimeType: undefined }));
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.changeAvatar());

			expect(mockedUploadAvatar).toHaveBeenCalledWith('file:///picked.png', 'avatar.jpg', 'image/jpeg');
		});

		it('is uploading while the request is in flight', async () => {
			let resolveUpload: (value: any) => void = () => {};
			mockedUploadAvatar.mockReturnValue(new Promise(resolve => { resolveUpload = resolve; }));
			mockedLaunchImageLibraryAsync.mockResolvedValue(pickedImage({}));
			const { result } = await renderHook(() => useAvatarActions());

			let pending: Promise<void> = Promise.resolve();
			await act(async () => {
				pending = result.current.changeAvatar();
			});

			expect(result.current.isUploading).toBe(true);

			await act(async () => {
				resolveUpload({ id: 'avatar-2' });
				await pending;
			});

			expect(result.current.isUploading).toBe(false);
		});

		it('shows the server message when the upload fails', async () => {
			mockedLaunchImageLibraryAsync.mockResolvedValue(pickedImage({}));
			mockedUploadAvatar.mockRejectedValue(axiosError('File troppo grande'));
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.changeAvatar());

			expect(mockMutateAsync).not.toHaveBeenCalled();
			expect(mockShowToast).toHaveBeenCalledWith('File troppo grande', 'error');
			expect(result.current.isUploading).toBe(false);
		});

		it('shows a generic message when the user update fails without a server message', async () => {
			mockedLaunchImageLibraryAsync.mockResolvedValue(pickedImage({}));
			mockMutateAsync.mockRejectedValue(new Error('Network Error'));
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.changeAvatar());

			expect(mockShowToast).toHaveBeenCalledWith('Errore durante il caricamento dell\'immagine', 'error');
			expect(result.current.isUploading).toBe(false);
		});
	});

	describe('removeAvatar', () => {
		it.each([
			['the user has no avatar', { avatarId: null }],
			['there is no user', null],
		])('does nothing when %s', async (_label, user) => {
			mockUser = user;
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.removeAvatar());

			expect(mockConfirm).not.toHaveBeenCalled();
			expect(mockedDeleteAvatar).not.toHaveBeenCalled();
		});

		it('asks for a destructive confirmation', async () => {
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.removeAvatar());

			expect(mockConfirm).toHaveBeenCalledWith({
				title: 'Rimuovi avatar',
				message: 'Vuoi rimuovere la tua immagine del profilo?',
				confirmLabel: 'Rimuovi',
				destructive: true,
			});
		});

		it('does nothing when the user does not confirm', async () => {
			mockConfirm.mockResolvedValue(false);
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.removeAvatar());

			expect(mockedDeleteAvatar).not.toHaveBeenCalled();
			expect(mockMutateAsync).not.toHaveBeenCalled();
		});

		it('deletes the file, clears the avatar and shows a success toast', async () => {
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.removeAvatar());

			expect(mockedDeleteAvatar).toHaveBeenCalledWith('avatar-1');
			expect(mockMutateAsync).toHaveBeenCalledWith({ removeAvatar: true });
			expect(mockShowToast).toHaveBeenCalledWith('Avatar rimosso', 'success');
			expect(result.current.isRemoving).toBe(false);
		});

		it('is removing while the request is in flight', async () => {
			let resolveDelete: () => void = () => {};
			mockedDeleteAvatar.mockReturnValue(new Promise<void>(resolve => { resolveDelete = resolve; }));
			const { result } = await renderHook(() => useAvatarActions());

			let pending: Promise<void> = Promise.resolve();
			await act(async () => {
				pending = result.current.removeAvatar();
			});

			expect(result.current.isRemoving).toBe(true);

			await act(async () => {
				resolveDelete();
				await pending;
			});

			expect(result.current.isRemoving).toBe(false);
		});

		it('shows the server message when the deletion fails', async () => {
			mockedDeleteAvatar.mockRejectedValue(axiosError('Avatar non trovato'));
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.removeAvatar());

			expect(mockMutateAsync).not.toHaveBeenCalled();
			expect(mockShowToast).toHaveBeenCalledWith('Avatar non trovato', 'error');
			expect(result.current.isRemoving).toBe(false);
		});

		it('shows a generic message when the deletion fails without a server message', async () => {
			mockedDeleteAvatar.mockRejectedValue(new Error('Network Error'));
			const { result } = await renderHook(() => useAvatarActions());

			await act(() => result.current.removeAvatar());

			expect(mockShowToast).toHaveBeenCalledWith('Errore durante la rimozione dell\'avatar', 'error');
		});
	});
});