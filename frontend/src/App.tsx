import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import LoginRedirect from './pages/LoginRedirect';
import Dashboard from './pages/Dashboard';
import Documents from './pages/Documents';
import Payments from './pages/Payments';
import Accounts from './pages/Accounts';
import NewAccount from './pages/NewAccount';
import AccountDetail from './pages/AccountDetail';
import DocumentSign from './pages/DocumentSign';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public sign route (no navbar) */}
          <Route path="/sign/:token" element={<DocumentSign />} />

          {/* Login redirect - triggers Keycloak login */}
          <Route path="/login" element={<LoginRedirect />} />

          {/* Protected routes with navbar */}
          <Route
            path="/*"
            element={
              <ProtectedRoute>
                <Navbar />
                <main>
                  <Routes>
                    <Route path="/dashboard" element={<Dashboard />} />
                    <Route path="/documents" element={<Documents />} />
                    <Route path="/accounts" element={<Accounts />} />
                    <Route path="/accounts/new" element={<NewAccount />} />
                    <Route path="/accounts/:id" element={<AccountDetail />} />
                    <Route path="/payments" element={<Payments />} />
                    <Route path="/" element={<Navigate to="/dashboard" replace />} />
                  </Routes>
                </main>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
