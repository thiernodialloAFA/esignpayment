import axios from 'axios';
import keycloak from '../keycloak';

const API_BASE_URL = process.env.REACT_APP_API_URL ?? '';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(async (config) => {
  if (keycloak.authenticated) {
    try {
      await keycloak.updateToken(30);
    } catch {
      keycloak.login();
      return config;
    }
    config.headers.Authorization = `Bearer ${keycloak.token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Don't redirect to login for auth-related endpoints to avoid loop
      const url = error.config?.url || '';
      if (!url.includes('/api/auth/')) {
        keycloak.login();
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
