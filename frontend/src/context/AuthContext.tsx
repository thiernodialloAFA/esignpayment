import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import keycloak from '../keycloak';
import { authApi } from '../api/auth';
import { User } from '../types';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  token: string | undefined;
  login: () => void;
  register: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const initialized = useRef(false);

  useEffect(() => {
    if (initialized.current) return;
    initialized.current = true;

    const initKeycloak = async () => {
      try {
        const authenticated = await keycloak.init({
          onLoad: 'check-sso',
          silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
          checkLoginIframe: false,
        });

        setIsAuthenticated(authenticated);

        if (authenticated) {
          // Clean up Keycloak URL parameters to prevent re-trigger
          const url = new URL(window.location.href);
          if (url.searchParams.has('code') || url.searchParams.has('session_state')) {
            url.searchParams.delete('code');
            url.searchParams.delete('session_state');
            url.searchParams.delete('iss');
            window.history.replaceState({}, '', url.pathname + url.hash);
          }

          try {
            const response = await authApi.me();
            setUser(response.data.data);
          } catch (err) {
            console.error('Failed to fetch user', err);
          }
        }
      } catch (err) {
        console.error('Keycloak init failed', err);
      } finally {
        setIsLoading(false);
      }
    };

    initKeycloak();

    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).catch(() => {
        setIsAuthenticated(false);
        setUser(null);
      });
    };
  }, []);

  const login = useCallback(() => {
    keycloak.login();
  }, []);

  const register = useCallback(() => {
    keycloak.register();
  }, []);

  const logout = useCallback(() => {
    keycloak.logout({ redirectUri: window.location.origin + '/login' });
    setUser(null);
    setIsAuthenticated(false);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated,
        isLoading,
        token: keycloak.token,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
