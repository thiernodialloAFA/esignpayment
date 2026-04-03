import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { accountApi } from '../api/accountApplications';
import { AccountApplication } from '../types';
import './Accounts.css';

const statusLabels: Record<string, string> = {
  DRAFT: 'Brouillon',
  SUBMITTED: 'Soumise',
  KYC_PENDING: 'KYC en attente',
  KYC_VERIFIED: 'KYC vérifié',
  CONTRACT_PENDING: 'Contrat en attente',
  CONTRACT_SIGNED: 'Contrat signé',
  APPROVED: 'Approuvée',
  ACTIVE: 'Active',
};

const Accounts: React.FC = () => {
  const [apps, setApps] = useState<AccountApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [regeneratingId, setRegeneratingId] = useState<string | null>(null);
  const navigate = useNavigate();

  const load = useCallback(async () => {
    try {
      const res = await accountApi.list();
      setApps(res.data.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleDelete = async (id: string) => {
    if (!window.confirm('Supprimer cette demande ?')) return;
    try {
      await accountApi.delete(id);
      setApps(prev => prev.filter(a => a.id !== id));
    } catch (e: any) {
      alert(e.response?.data?.message || 'Erreur');
    }
  };

  const handleRegenerate = async (id: string) => {
    if (!window.confirm('Régénérer le contrat et renvoyer le lien de signature ?')) return;
    setRegeneratingId(id);
    try {
      const res = await accountApi.regenerateContract(id);
      setApps(prev => prev.map(a => a.id === id ? res.data.data : a));
      alert('✅ Contrat régénéré ! Un nouveau lien de signature a été envoyé.');
    } catch (e: any) {
      alert(e.response?.data?.message || 'Erreur lors de la régénération');
    } finally {
      setRegeneratingId(null);
    }
  };

  return (
    <div className="accounts-page">
      <div className="page-header">
        <div>
          <h1>Ouverture de Compte</h1>
          <p className="page-subtitle">Gérez vos demandes d'ouverture de compte bancaire</p>
        </div>
        <button className="btn btn-primary" onClick={() => navigate('/accounts/new')}>
          + Nouvelle Demande
        </button>
      </div>

      {loading ? (
        <div className="page-loading"><div className="spinner" /><p>Chargement...</p></div>
      ) : apps.length === 0 ? (
        <div className="empty-state">
          <span style={{ fontSize: 48 }}>🏦</span>
          <p>Aucune demande. Ouvrez votre premier compte bancaire.</p>
        </div>
      ) : (
        <div className="accounts-grid">
          {apps.map(app => (
            <div key={app.id} className="account-card" onClick={() => navigate(`/accounts/${app.id}`)}>
              <div className="account-card-header">
                <span className="ref-number">{app.referenceNumber}</span>
                <span className={`badge badge-${app.status.toLowerCase()}`}>
                  {statusLabels[app.status] || app.status}
                </span>
              </div>
              <div className="account-card-body">
                <div className="account-type-label">{app.accountType.label}</div>
                <div className="account-meta">
                  {app.accountType.monthlyFee > 0
                    ? `${app.accountType.monthlyFee}€/mois`
                    : 'Gratuit'}
                </div>
              </div>
              <div className="account-card-footer">
                <span className="text-muted">{new Date(app.createdAt).toLocaleDateString()}</span>
                <div className="action-buttons" onClick={e => e.stopPropagation()}>
                  {app.status === 'DRAFT' && (
                    <>
                      <button className="btn btn-primary btn-sm" onClick={() => navigate(`/accounts/new?edit=${app.id}`)}>
                        Reprendre
                      </button>
                      <button className="btn btn-danger btn-sm" onClick={() => handleDelete(app.id)}>
                        Supprimer
                      </button>
                    </>
                  )}
                  {app.status === 'CONTRACT_PENDING' && (
                    <button
                      className="btn btn-regenerate btn-sm"
                      disabled={regeneratingId === app.id}
                      onClick={() => handleRegenerate(app.id)}
                    >
                      {regeneratingId === app.id ? '⏳ Régénération...' : '🔄 Régénérer & renvoyer'}
                    </button>
                  )}
                </div>
              </div>

              {/* Progress bar */}
              <div className="status-progress">
                {['DRAFT','SUBMITTED','KYC_PENDING','KYC_VERIFIED','CONTRACT_PENDING','CONTRACT_SIGNED','APPROVED','ACTIVE'].map((s, i, arr) => {
                  const currentIdx = arr.indexOf(app.status);
                  return <div key={s} className={`progress-dot ${i <= currentIdx ? 'done' : ''}`} />;
                })}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default Accounts;

