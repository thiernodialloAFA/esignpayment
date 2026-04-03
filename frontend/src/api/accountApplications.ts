import apiClient from './client';
import {
  AccountApplication,
  AccountType,
  ApiResponse,
  CreateAccountApplicationRequest,
  KycDocumentItem,
} from '../types';

export const accountApi = {
  getTypes: () =>
    apiClient.get<ApiResponse<AccountType[]>>('/api/account-types'),

  create: (data: CreateAccountApplicationRequest) =>
    apiClient.post<ApiResponse<AccountApplication>>('/api/account-applications', data),

  update: (id: string, data: Partial<CreateAccountApplicationRequest>) =>
    apiClient.put<ApiResponse<AccountApplication>>(`/api/account-applications/${id}`, data),

  list: () =>
    apiClient.get<ApiResponse<AccountApplication[]>>('/api/account-applications'),

  get: (id: string) =>
    apiClient.get<ApiResponse<AccountApplication>>(`/api/account-applications/${id}`),

  submit: (id: string) =>
    apiClient.post<ApiResponse<AccountApplication>>(`/api/account-applications/${id}/submit`),

  uploadKyc: (id: string, data: { documentType: string; fileName: string; contentType: string; fileContent: string }) =>
    apiClient.post<ApiResponse<KycDocumentItem>>(`/api/account-applications/${id}/kyc`, data),

  deleteKyc: (id: string, kycId: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/account-applications/${id}/kyc/${kycId}`),

  generateContract: (id: string) =>
    apiClient.post<ApiResponse<AccountApplication>>(`/api/account-applications/${id}/generate-contract`),

  regenerateContract: (id: string) =>
    apiClient.post<ApiResponse<AccountApplication>>(`/api/account-applications/${id}/regenerate-contract`),

  delete: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/account-applications/${id}`),
};

