import React from 'react';
import { render, screen } from '@testing-library/react';

// Mock keycloak-js before importing App
jest.mock('./keycloak', () => {
  return {
    __esModule: true,
    default: {
      init: jest.fn().mockResolvedValue(false),
      login: jest.fn(),
      logout: jest.fn(),
      register: jest.fn(),
      authenticated: false,
      token: undefined,
      onTokenExpired: null,
      updateToken: jest.fn().mockResolvedValue(true),
    },
  };
});

import App from './App';

test('renders loading screen for unauthenticated users', () => {
  render(<App />);
  // Unauthenticated users should see a loading spinner while Keycloak redirects
  expect(document.querySelector('.spinner')).toBeInTheDocument();
});
