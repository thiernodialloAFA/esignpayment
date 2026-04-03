import apiClient from './client';
import { ApiResponse, CreatePaymentRequest, Payment, PaymentConfig } from '../types';

export const paymentsApi = {
  create: (data: CreatePaymentRequest) =>
    apiClient.post<ApiResponse<Payment>>('/api/payments', data),

  confirm: (paymentIntentId: string) =>
    apiClient.post<ApiResponse<Payment>>('/api/payments/confirm', { paymentIntentId }),

  getConfig: () =>
    apiClient.get<ApiResponse<PaymentConfig>>('/api/payments/config'),

  list: () => apiClient.get<ApiResponse<Payment[]>>('/api/payments'),

  get: (id: string) => apiClient.get<ApiResponse<Payment>>(`/api/payments/${id}`),

  cancel: (id: string) =>
    apiClient.post<ApiResponse<Payment>>(`/api/payments/${id}/cancel`),
};
