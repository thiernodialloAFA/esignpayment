import apiClient from './client';
import { ApiResponse, User } from '../types';

export const authApi = {
  me: () => apiClient.get<ApiResponse<User>>('/api/auth/me'),
};
