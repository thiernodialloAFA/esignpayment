import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { accountApi } from '../api/accountApplications';
import { AccountApplication, KycDocumentItem } from '../types';
import './Accounts.css';

const statusLabels: Record<string, string> = {
  DRAFT: 'Brouillon', SUBMITTED: 'Soumise', KYC_PENDING: 'KYC en attente',
  KYC_VERIFIED: 'KYC vérifié', CONTRACT_PENDING: 'Contrat en attente',
  CONTRACT_SIGNED: 'Contrat signé', APPROVED: 'Approuvée', ACTIVE: 'Active',
};

const allStatuses = ['DRAFT','SUBMITTED','KYC_PENDING','KYC_VERIFIED','CONTRACT_PENDING','CONTRACT_SIGNED','APPROVED','ACTIVE'];

const kycStatusMap: Record<string, { icon: string; label: string; className: string }> = {
  VERIFIED: { icon: '✅', label: 'Vérifié', className: 'kyc-status-ok' },
  MISMATCH: { icon: '⚠️', label: 'À vérifier', className: 'kyc-status-warn' },
  FAILED: { icon: '❌', label: 'Échec', className: 'kyc-status-error' },
  NOT_AVAILABLE: { icon: 'ℹ️', label: 'N/A', className: 'kyc-status-na' },
  PENDING: { icon: '⏳', label: 'En attente', className: 'kyc-status-pending' },
};

const KycDocCard: React.FC<{ kyc: KycDocumentItem }> = ({ kyc }) => {
  const [expanded, setExpanded] = useState(false);
  const cfg = kycStatusMap[kyc.ocrStatus || 'PENDING'] || kycStatusMap.PENDING;
  const docIcon = kyc.documentType === 'ID_CARD' ? '🪪' : kyc.documentType === 'PASSPORT' ? '🛂' : '🏠';
  const docLabel = kyc.documentType === 'ID_CARD' ? "Pièce d'identité"
    : kyc.documentType === 'PASSPORT' ? 'Passeport' : 'Justificatif de domicile';
  const hasDetails = kyc.ocrDetails && kyc.ocrDetails.length > 0;

  return (
    <div className="kyc-doc-card">
      <div className="kyc-doc-header" onClick={() => hasDetails && setExpanded(!expanded)} style={{ cursor: hasDetails ? 'pointer' : 'default' }}>
        <div className="kyc-doc-left">
          <span className="kyc-doc-icon">{docIcon}</span>
          <div>
            <div className="kyc-doc-label">{docLabel}</div>
            <div className="kyc-doc-file">{kyc.fileName}</div>
          </div>
        </div>
        <div className="kyc-doc-right">
          <span className={`badge badge-${kyc.status.toLowerCase()}`}>{kyc.status}</span>
          <span className={`kyc-checklist-badge ${cfg.className}`}>
            {cfg.icon} {cfg.label} {kyc.ocrMatchScore != null && `(${kyc.ocrMatchScore}%)`}
          </span>
          {hasDetails && <span className="expand-icon">{expanded ? '▲' : '▼'}</span>}
        </div>
      </div>

      {expanded && hasDetails && (
        <div className="kyc-doc-body">
          {kyc.ocrWarnings && kyc.ocrWarnings.length > 0 && (
            <div className="kyc-doc-warnings">
              {kyc.ocrWarnings.map((w, i) => <div key={i} className="kyc-issue-item">⚠️ {w}</div>)}
            </div>
          )}
          <div className="kyc-detail-grid">
            {kyc.ocrDetails!.map((d, i) => (
              <div key={i} className={`kyc-detail-row ${d.matched ? 'row-ok' : 'row-warn'}`}>
                <span className="kyc-detail-label">{d.fieldLabel}</span>
                <span className="kyc-detail-declared">{d.declaredValue}</span>
                <span className="kyc-detail-arrow">→</span>
                <span className="kyc-detail-extracted">{d.extractedValue}</span>
                <span className={`kyc-detail-score ${d.matchScore >= 70 ? 'score-good' : d.matchScore >= 40 ? 'score-warn' : 'score-bad'}`}>
                  {d.matchScore}%
                </span>
              </div>
            ))}
          </div>
          {kyc.documentTypeValid === false && (
            <div className="kyc-issue-item kyc-issue-type">
              🚫 Le document ne correspond pas au type attendu
            </div>
          )}
        </div>
      )}
    </div>
  );
};

const AccountDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [app, setApp] = useState<AccountApplication | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [regenerating, setRegenerating] = useState(false);

  useEffect(() => {
    if (!id) return;
    accountApi.get(id).then(res => setApp(res.data.data)).catch(() => {}).finally(() => setLoading(false));
  }, [id]);

  const handleGenerateContract = async () => {
    if (!id) return;
    setGenerating(true);
    try {
      const res = await accountApi.generateContract(id);
      setApp(res.data.data);
      alert('Contrat généré ! Un email avec le lien de signature a été envoyé.');
    } catch (e: any) {
      alert(e.response?.data?.message || 'Erreur');
    } finally {
      setGenerating(false);
    }
  };

  const handleRegenerateContract = async () => {
    if (!id) return;
    if (!window.confirm('Voulez-vous régénérer le contrat ? L\'ancien contrat sera supprimé et un nouveau lien de signature sera envoyé.')) return;
    setRegenerating(true);
    try {
      const res = await accountApi.regenerateContract(id);
      setApp(res.data.data);
      alert('Contrat régénéré ! Un nouveau lien de signature a été envoyé par email.');
    } catch (e: any) {
      alert(e.response?.data?.message || 'Erreur lors de la régénération');
    } finally {
      setRegenerating(false);
    }
  };

  if (loading) return <div className="page-loading"><div className="spinner" /></div>;
  if (!app) return <div className="accounts-page"><p>Demande introuvable.</p></div>;

  const currentIdx = allStatuses.indexOf(app.status);

  return (
    <div className="accounts-page">
      <button className="btn btn-outline btn-sm" onClick={() => navigate('/accounts')} style={{ marginBottom: 24 }}>
        ← Retour
      </button>

      <div className="detail-header">
        <div>
          <h1>{app.referenceNumber}</h1>
          <p className="page-subtitle">{app.accountType.label}</p>
        </div>
        <span className={`badge badge-${app.status.toLowerCase()} badge-lg`}>
          {statusLabels[app.status]}
        </span>
      </div>

      {/* Progress */}
      <div className="detail-progress">
        {allStatuses.map((s, i) => (
          <div key={s} className={`progress-step ${i <= currentIdx ? 'done' : ''} ${i === currentIdx ? 'current' : ''}`}>
            <div className="progress-circle">{i <= currentIdx ? '✓' : i + 1}</div>
            <span>{statusLabels[s]}</span>
          </div>
        ))}
      </div>

      {/* Actions */}
      {(app.status === 'KYC_PENDING' || app.status === 'KYC_VERIFIED') && (
        <div className="detail-actions">
          <button className="btn btn-primary" onClick={handleGenerateContract} disabled={generating}>
            {generating ? 'Génération...' : '📄 Générer le contrat & envoyer pour signature'}
          </button>
        </div>
      )}

      {app.status === 'CONTRACT_PENDING' && (
        <div className="detail-actions">
          <button className="btn btn-outline" onClick={handleRegenerateContract} disabled={regenerating}>
            {regenerating ? 'Régénération...' : '🔄 Régénérer le contrat & renvoyer'}
          </button>
          {app.contractDocumentId && (
            <button className="btn btn-primary" onClick={() => navigate(`/documents?liveSign=${app.contractDocumentId}`)}>
              ✍️ Faire signer en direct
            </button>
          )}
        </div>
      )}

      {/* Info sections */}
      <div className="detail-grid">
        <div className="detail-card">
          <h3>Informations personnelles</h3>
          <dl>
            <dt>Date de naissance</dt><dd>{app.dateOfBirth || '—'}</dd>
            <dt>Téléphone</dt><dd>{app.phoneNumber || '—'}</dd>
            <dt>Nationalité</dt><dd>{app.nationality || '—'}</dd>
          </dl>
        </div>
        <div className="detail-card">
          <h3>Adresse</h3>
          <dl>
            <dt>Adresse</dt><dd>{app.addressLine1 || '—'}</dd>
            <dt>Ville</dt><dd>{app.city} {app.postalCode}</dd>
            <dt>Pays</dt><dd>{app.country || '—'}</dd>
          </dl>
        </div>
        <div className="detail-card">
          <h3>Situation professionnelle</h3>
          <dl>
            <dt>Statut</dt><dd>{app.employmentStatus || '—'}</dd>
            <dt>Employeur</dt><dd>{app.employerName || '—'}</dd>
            <dt>Revenu mensuel</dt><dd>{app.monthlyIncome ? `${app.monthlyIncome}€` : '—'}</dd>
          </dl>
        </div>
      </div>

      {/* KYC Documents */}
      <div className="detail-card" style={{ marginTop: 24 }}>
        <h3>📋 Vérification des documents</h3>
        {app.kycDocuments.length === 0 ? (
          <p className="text-muted">Aucun document KYC soumis</p>
        ) : (
          <div className="kyc-doc-list">
            {app.kycDocuments.map(k => (
              <KycDocCard key={k.id} kyc={k} />
            ))}
          </div>
        )}
      </div>

      {/* Timeline */}
      {app.statusHistory.length > 0 && (
        <div className="detail-card" style={{ marginTop: 24 }}>
          <h3>Historique</h3>
          <div className="timeline">
            {app.statusHistory.map((h, i) => (
              <div key={i} className="timeline-item">
                <div className="timeline-dot" />
                <div className="timeline-content">
                  <strong>{h.toStatus}</strong>
                  {h.comment && <span> — {h.comment}</span>}
                  <div className="text-muted">{new Date(h.changedAt).toLocaleString()}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default AccountDetail;

