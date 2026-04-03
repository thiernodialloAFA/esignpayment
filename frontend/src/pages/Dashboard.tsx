import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { documentsApi } from '../api/documents';
import { paymentsApi } from '../api/payments';
import { accountApi } from '../api/accountApplications';
import { AccountApplication, Document, Payment } from '../types';
import './Dashboard.css';

const Dashboard: React.FC = () => {
  const { user } = useAuth();
  const [documents, setDocuments] = useState<Document[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [accounts, setAccounts] = useState<AccountApplication[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [docsRes, paysRes, accRes] = await Promise.all([
          documentsApi.list(),
          paymentsApi.list(),
          accountApi.list(),
        ]);
        setDocuments(docsRes.data.data);
        setPayments(paysRes.data.data);
        setAccounts(accRes.data.data);
      } catch (err) {
        console.error('Failed to load dashboard data', err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const stats = {
    totalDocs: documents.length,
    completedDocs: documents.filter((d) => d.status === 'COMPLETED').length,
    pendingDocs: documents.filter(
      (d) => d.status === 'PENDING_SIGNATURE' || d.status === 'PARTIALLY_SIGNED'
    ).length,
    totalPayments: payments.length,
    succeededPayments: payments.filter((p) => p.status === 'SUCCEEDED').length,
    totalAmount: payments
      .filter((p) => p.status === 'SUCCEEDED')
      .reduce((sum, p) => sum + Number(p.amount), 0),
  };

  if (loading) {
    return (
      <div className="page-loading">
        <div className="spinner" />
        <p>Loading dashboard...</p>
      </div>
    );
  }

  return (
    <div className="dashboard">
      <div className="page-header">
        <h1>
          Welcome back, {user?.firstName}! 👋
        </h1>
        <p className="page-subtitle">Here's an overview of your activity</p>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon stat-blue">📄</div>
          <div className="stat-info">
            <span className="stat-value">{stats.totalDocs}</span>
            <span className="stat-label">Total Documents</span>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon stat-green">✅</div>
          <div className="stat-info">
            <span className="stat-value">{stats.completedDocs}</span>
            <span className="stat-label">Completed</span>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon stat-yellow">⏳</div>
          <div className="stat-info">
            <span className="stat-value">{stats.pendingDocs}</span>
            <span className="stat-label">Pending Signatures</span>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon stat-purple">💳</div>
          <div className="stat-info">
            <span className="stat-value">
              {stats.totalAmount.toFixed(2)}€
            </span>
            <span className="stat-label">Total Paid</span>
          </div>
        </div>
      </div>

      <div className="dashboard-grid">
        <div className="section-card">
          <div className="section-header">
            <h2>Recent Documents</h2>
            <Link to="/documents" className="link-more">
              View all →
            </Link>
          </div>
          {documents.length === 0 ? (
            <div className="empty-state">
              <p>No documents yet.</p>
              <Link to="/documents" className="btn btn-primary btn-sm">
                Upload a document
              </Link>
            </div>
          ) : (
            <ul className="item-list">
              {documents.slice(0, 5).map((doc) => (
                <li key={doc.id} className="item-row">
                  <div className="item-info">
                    <span className="item-title">{doc.title}</span>
                    <span className="item-sub">{doc.fileName}</span>
                  </div>
                  <span className={`badge badge-${doc.status.toLowerCase()}`}>
                    {doc.status.replace('_', ' ')}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="section-card">
          <div className="section-header">
            <h2>Recent Payments</h2>
            <Link to="/payments" className="link-more">
              View all →
            </Link>
          </div>
          {payments.length === 0 ? (
            <div className="empty-state">
              <p>No payments yet.</p>
              <Link to="/payments" className="btn btn-primary btn-sm">
                Make a payment
              </Link>
            </div>
          ) : (
            <ul className="item-list">
              {payments.slice(0, 5).map((pay) => (
                <li key={pay.id} className="item-row">
                  <div className="item-info">
                    <span className="item-title">
                      {Number(pay.amount).toFixed(2)} {pay.currency}
                    </span>
                    <span className="item-sub">{pay.description || '—'}</span>
                  </div>
                  <span className={`badge badge-${pay.status.toLowerCase()}`}>
                    {pay.status}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="section-card">
          <div className="section-header">
            <h2>Ouverture de Compte</h2>
            <Link to="/accounts" className="link-more">
              View all →
            </Link>
          </div>
          {accounts.length === 0 ? (
            <div className="empty-state">
              <p>Aucune demande en cours.</p>
              <Link to="/accounts/new" className="btn btn-primary btn-sm">
                🏦 Ouvrir un compte
              </Link>
            </div>
          ) : (
            <ul className="item-list">
              {accounts.slice(0, 5).map((acc) => (
                <li key={acc.id} className="item-row">
                  <div className="item-info">
                    <span className="item-title">{acc.referenceNumber}</span>
                    <span className="item-sub">{acc.accountType.label}</span>
                  </div>
                  <span className={`badge badge-${acc.status.toLowerCase()}`}>
                    {acc.status.replace('_', ' ')}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
