import apiClient from './client';
import {
  ApiResponse,
  CreateDocumentRequest,
  Document,
  DocumentSigner,
  OtpResponse,
} from '../types';

export const documentsApi = {
  create: (data: CreateDocumentRequest) =>
    apiClient.post<ApiResponse<Document>>('/api/documents', data),

  list: () => apiClient.get<ApiResponse<Document[]>>('/api/documents'),

  get: (id: string) => apiClient.get<ApiResponse<Document>>(`/api/documents/${id}`),

  sendForSignature: (id: string) =>
    apiClient.post<ApiResponse<Document>>(`/api/documents/${id}/send`),

  resendForSignature: (id: string) =>
    apiClient.post<ApiResponse<Document>>(`/api/documents/${id}/resend`),

  delete: (id: string) => apiClient.delete<ApiResponse<void>>(`/api/documents/${id}`),

  download: (id: string) =>
    apiClient.get(`/api/documents/${id}/download`, { responseType: 'blob' }),


  liveSign: (id: string, signerId: string, signatureData: string) =>
    apiClient.post<ApiResponse<Document>>(`/api/documents/${id}/live-sign/${signerId}`, { signatureData }),

  verifyToken: (token: string) =>
    apiClient.get<ApiResponse<DocumentSigner>>(`/api/sign/verify/${token}`),

  sendOtp: (token: string, phoneNumber: string) =>
    apiClient.post<ApiResponse<OtpResponse>>(`/api/sign/${token}/send-otp`, { phoneNumber }),

  verifyOtp: (token: string, otpCode: string) =>
    apiClient.post<ApiResponse<OtpResponse>>(`/api/sign/${token}/verify-otp`, { otpCode }),

  sign: (token: string, signatureData: string) =>
    apiClient.post<ApiResponse<Document>>(`/api/sign/${token}`, { signatureData }),
};
