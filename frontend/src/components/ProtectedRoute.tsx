import React, { useEffect, useRef } from 'react';
import { useAuth } from '../context/AuthContext';

interface ProtectedRouteProps {
  children: React.ReactNode;
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children }) => {
  const { isAuthenticated, isLoading, login } = useAuth();
  const loginTriggered = useRef(false);

  useEffect(() => {
    if (!isLoading && !isAuthenticated && !loginTriggered.current) {
      loginTriggered.current = true;
      login();
    }
  }, [isLoading, isAuthenticated, login]);

  if (isLoading || !isAuthenticated) {
    return (
      <div className="loading-screen">
        <div className="spinner" />
      </div>
    );
  }

  return <>{children}</>;
};

export default ProtectedRoute;
