import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { accountApi } from '../api/accountApplications';
import { AccountType, KycDocumentItem } from '../types';
import './Accounts.css';

/* ── Helpers pour l'affichage simplifié du statut KYC ── */

const kycStatusConfig: Record<string, { icon: string; label: string; className: string; hint: string }> = {
  VERIFIED: { icon: '✅', label: 'Vérifié', className: 'kyc-status-ok', hint: 'Le document correspond aux informations déclarées.' },
  MISMATCH: { icon: '⚠️', label: 'À vérifier', className: 'kyc-status-warn', hint: 'Des différences ont été détectées. Votre dossier sera vérifié manuellement.' },
  FAILED: { icon: '❌', label: 'Échec', className: 'kyc-status-error', hint: 'Le document n\'a pas pu être analysé. Veuillez réessayer avec une meilleure image.' },
  NOT_AVAILABLE: { icon: 'ℹ️', label: 'Non analysé', className: 'kyc-status-na', hint: 'La vérification automatique n\'est pas disponible.' },
  PENDING: { icon: '⏳', label: 'En attente', className: 'kyc-status-pending', hint: 'Analyse en cours...' },
};

const getKycStatus = (doc: KycDocumentItem) => {
  const status = doc.ocrStatus || 'PENDING';
  return kycStatusConfig[status] || kycStatusConfig.PENDING;
};

const KycResultCard: React.FC<{ doc: KycDocumentItem }> = ({ doc }) => {
  const cfg = getKycStatus(doc);
  const hasIssues = doc.ocrDetails?.some(d => !d.matched);

  return (
    <div className={`kyc-result-card ${cfg.className}`}>
      <div className="kyc-result-main">
        <span className="kyc-result-icon">{cfg.icon}</span>
        <div className="kyc-result-info">
          <div className="kyc-result-title">
            {cfg.label}
            {doc.ocrMatchScore != null && <span className="kyc-result-score">({doc.ocrMatchScore}%)</span>}
          </div>
          <div className="kyc-result-hint">{cfg.hint}</div>
        </div>
      </div>

      {/* Show mismatched fields as simple list */}
      {hasIssues && doc.ocrDetails && (
        <div className="kyc-issues-list">
          {doc.ocrDetails.filter(d => !d.matched).map((d, i) => (
            <div key={i} className="kyc-issue-item">
              <span className="kyc-issue-field">{d.fieldLabel} :</span>
              <span className="kyc-issue-detail">
                déclaré «{d.declaredValue}» — extrait «{d.extractedValue}»
              </span>
            </div>
          ))}
        </div>
      )}

      {doc.documentTypeValid === false && (
        <div className="kyc-issue-item kyc-issue-type">
          🚫 Le document ne semble pas correspondre au type attendu
        </div>
      )}
    </div>
  );
};

const NewAccount: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const editId = searchParams.get('edit');

  const [step, setStep] = useState(1);
  const [types, setTypes] = useState<AccountType[]>([]);
  const [appId, setAppId] = useState<string | null>(editId);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Form data
  const [form, setForm] = useState({
    accountTypeCode: '', dateOfBirth: '', phoneNumber: '', nationality: '',
    addressLine1: '', addressLine2: '', city: '', postalCode: '', country: 'France',
    employmentStatus: '', employerName: '', jobTitle: '', monthlyIncome: '' as string,
  });

  // KYC uploads with OCR results
  const [kycFiles, setKycFiles] = useState<{ type: string; file: File | null }[]>([
    { type: 'ID_CARD', file: null },
    { type: 'PROOF_OF_ADDRESS', file: null },
  ]);
  const [uploadedKyc, setUploadedKyc] = useState<KycDocumentItem[]>([]);
  const [uploadingIdx, setUploadingIdx] = useState<number | null>(null);

  useEffect(() => {
    accountApi.getTypes().then(res => setTypes(res.data.data));
    if (editId) {
      accountApi.get(editId).then(res => {
        const a = res.data.data;
        setForm({
          accountTypeCode: a.accountType.code,
          dateOfBirth: a.dateOfBirth || '',
          phoneNumber: a.phoneNumber || '',
          nationality: a.nationality || '',
          addressLine1: a.addressLine1 || '',
          addressLine2: a.addressLine2 || '',
          city: a.city || '',
          postalCode: a.postalCode || '',
          country: a.country || 'France',
          employmentStatus: a.employmentStatus || '',
          employerName: a.employerName || '',
          jobTitle: a.jobTitle || '',
          monthlyIncome: a.monthlyIncome?.toString() || '',
        });
        if (a.kycDocuments && a.kycDocuments.length > 0) {
          setUploadedKyc(a.kycDocuments);
        }
      });
    }
  }, [editId]);

  const set = (field: string, value: string) => setForm(prev => ({ ...prev, [field]: value }));

  const readFileAsBase64 = (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve((reader.result as string).split(',')[1]);
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });

  const saveOrCreate = useCallback(async () => {
    const payload = {
      ...form,
      monthlyIncome: form.monthlyIncome ? parseFloat(form.monthlyIncome) : undefined,
    };
    if (appId) {
      await accountApi.update(appId, payload);
    } else {
      const res = await accountApi.create(payload as any);
      setAppId(res.data.data.id);
    }
  }, [appId, form]);

  // Upload a single KYC document immediately and get OCR results
  const handleKycUpload = async (index: number, file: File) => {
    if (!appId) {
      setError('Veuillez d\'abord sauvegarder les étapes précédentes.');
      return;
    }

    const files = [...kycFiles];
    files[index] = { type: kycFiles[index].type, file };
    setKycFiles(files);

    setUploadingIdx(index);
    setError('');
    try {
      const content = await readFileAsBase64(file);
      const res = await accountApi.uploadKyc(appId, {
        documentType: kycFiles[index].type,
        fileName: file.name,
        contentType: file.type,
        fileContent: content,
      });
      // Add or replace in uploaded list
      setUploadedKyc(prev => {
        const filtered = prev.filter(k => k.documentType !== kycFiles[index].type);
        return [...filtered, res.data.data];
      });
    } catch (e: any) {
      setError(e.response?.data?.message || 'Erreur lors du téléversement');
    } finally {
      setUploadingIdx(null);
    }
  };

  const nextStep = async () => {
    setError('');
    try {
      if (step === 1 && !form.accountTypeCode) { setError('Choisissez un type de compte'); return; }
      if (step >= 1 && step <= 4) await saveOrCreate();
      setStep(s => s + 1);
    } catch (e: any) {
      setError(e.response?.data?.message || 'Erreur');
    }
  };

  const handleSubmit = async () => {
    if (!appId) return;
    setSubmitting(true);
    setError('');
    try {
      await accountApi.submit(appId);
      navigate('/accounts');
    } catch (e: any) {
      setError(e.response?.data?.message || 'Erreur lors de la soumission');
    } finally {
      setSubmitting(false);
    }
  };

  // ── KYC checklist helpers ──
  const kycChecklist = [
    { type: 'ID_CARD', label: "Pièce d'identité (CNI ou Passeport)", icon: '🪪', desc: 'Nom, prénom et date de naissance seront vérifiés' },
    { type: 'PROOF_OF_ADDRESS', label: 'Justificatif de domicile', icon: '🏠', desc: 'Adresse, ville et code postal seront vérifiés' },
  ];

  const getDocForType = (type: string) => uploadedKyc.find(k => k.documentType === type);
  const allDocsUploaded = kycChecklist.every(c => getDocForType(c.type));
  const allDocsVerified = kycChecklist.every(c => {
    const doc = getDocForType(c.type);
    return doc && doc.ocrStatus === 'VERIFIED';
  });
  const hasOcrMismatch = uploadedKyc.some(k => k.ocrStatus === 'MISMATCH');

  const totalSteps = 6;

  return (
    <div className="new-account-page">
      <h1>Ouvrir un Compte Bancaire</h1>

      {/* Progress */}
      <div className="steps-bar">
        {['Type', 'Identité', 'Adresse', 'Emploi', 'Documents', 'Confirmation'].map((label, i) => (
          <div key={i} className={`step-item ${i + 1 <= step ? 'active' : ''} ${i + 1 === step ? 'current' : ''}`}>
            <div className="step-circle">{i + 1}</div>
            <span className="step-label">{label}</span>
          </div>
        ))}
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="step-content">
        {step === 1 && (
          <div className="step-card">
            <h2>Choisissez votre compte</h2>
            <div className="type-grid">
              {types.map(t => (
                <div key={t.code}
                  className={`type-option ${form.accountTypeCode === t.code ? 'selected' : ''}`}
                  onClick={() => set('accountTypeCode', t.code)}>
                  <h3>{t.label}</h3>
                  <p>{t.description}</p>
                  <div className="type-price">{t.monthlyFee > 0 ? `${t.monthlyFee}€/mois` : 'Gratuit'}</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="step-card">
            <h2>Informations personnelles</h2>
            <div className="form-grid">
              <div className="form-group">
                <label>Date de naissance *</label>
                <input type="date" className="form-input" value={form.dateOfBirth} onChange={e => set('dateOfBirth', e.target.value)} />
              </div>
              <div className="form-group">
                <label>Téléphone</label>
                <input type="tel" className="form-input" placeholder="+33..." value={form.phoneNumber} onChange={e => set('phoneNumber', e.target.value)} />
              </div>
              <div className="form-group">
                <label>Nationalité</label>
                <input type="text" className="form-input" placeholder="Française" value={form.nationality} onChange={e => set('nationality', e.target.value)} />
              </div>
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="step-card">
            <h2>Adresse</h2>
            <div className="form-grid">
              <div className="form-group full-width">
                <label>Adresse *</label>
                <input type="text" className="form-input" value={form.addressLine1} onChange={e => set('addressLine1', e.target.value)} />
              </div>
              <div className="form-group full-width">
                <label>Complément</label>
                <input type="text" className="form-input" value={form.addressLine2} onChange={e => set('addressLine2', e.target.value)} />
              </div>
              <div className="form-group">
                <label>Ville *</label>
                <input type="text" className="form-input" value={form.city} onChange={e => set('city', e.target.value)} />
              </div>
              <div className="form-group">
                <label>Code postal *</label>
                <input type="text" className="form-input" value={form.postalCode} onChange={e => set('postalCode', e.target.value)} />
              </div>
              <div className="form-group">
                <label>Pays *</label>
                <input type="text" className="form-input" value={form.country} onChange={e => set('country', e.target.value)} />
              </div>
            </div>
          </div>
        )}

        {step === 4 && (
          <div className="step-card">
            <h2>Situation professionnelle</h2>
            <div className="form-grid">
              <div className="form-group">
                <label>Statut *</label>
                <select className="form-input" value={form.employmentStatus} onChange={e => set('employmentStatus', e.target.value)}>
                  <option value="">Sélectionner</option>
                  <option value="EMPLOYED">Salarié(e)</option>
                  <option value="SELF_EMPLOYED">Indépendant(e)</option>
                  <option value="UNEMPLOYED">Sans emploi</option>
                  <option value="STUDENT">Étudiant(e)</option>
                  <option value="RETIRED">Retraité(e)</option>
                </select>
              </div>
              <div className="form-group">
                <label>Employeur</label>
                <input type="text" className="form-input" value={form.employerName} onChange={e => set('employerName', e.target.value)} />
              </div>
              <div className="form-group">
                <label>Poste</label>
                <input type="text" className="form-input" value={form.jobTitle} onChange={e => set('jobTitle', e.target.value)} />
              </div>
              <div className="form-group">
                <label>Revenu mensuel (€)</label>
                <input type="number" className="form-input" placeholder="0" value={form.monthlyIncome} onChange={e => set('monthlyIncome', e.target.value)} />
              </div>
            </div>
          </div>
        )}

        {step === 5 && (
          <div className="step-card">
            <h2>Documents justificatifs</h2>
            <p className="step-desc">
              Téléversez les documents ci-dessous. Ils seront vérifiés automatiquement.
            </p>

            {/* Checklist */}
            <div className="kyc-checklist">
              {kycChecklist.map((item, i) => {
                const doc = getDocForType(item.type);
                const isUploading = uploadingIdx === i;
                const statusCfg = doc ? getKycStatus(doc) : null;

                return (
                  <div key={item.type} className={`kyc-checklist-item ${doc ? 'has-doc' : ''}`}>
                    <div className="kyc-checklist-header">
                      <span className="kyc-checklist-icon">{item.icon}</span>
                      <div className="kyc-checklist-label">
                        <strong>{item.label} *</strong>
                        <span className="kyc-checklist-desc">{item.desc}</span>
                      </div>
                      {doc && statusCfg && (
                        <span className={`kyc-checklist-badge ${statusCfg.className}`}>
                          {statusCfg.icon} {statusCfg.label}
                        </span>
                      )}
                      {!doc && !isUploading && (
                        <span className="kyc-checklist-badge kyc-status-missing">📎 Requis</span>
                      )}
                    </div>

                    <input
                      type="file"
                      className="form-input"
                      accept=".pdf,.jpg,.jpeg,.png"
                      disabled={isUploading}
                      onChange={e => {
                        const file = e.target.files?.[0];
                        if (file) handleKycUpload(i, file);
                      }}
                    />

                    {isUploading && (
                      <div className="ocr-loading">
                        <div className="spinner-sm" /> Vérification du document...
                      </div>
                    )}

                    {doc && !isUploading && <KycResultCard doc={doc} />}
                  </div>
                );
              })}
            </div>

            {/* Summary status */}
            {allDocsUploaded && (
              <div className={`kyc-summary-banner ${allDocsVerified ? 'banner-success' : hasOcrMismatch ? 'banner-warn' : 'banner-info'}`}>
                {allDocsVerified
                  ? '✅ Tous les documents sont vérifiés. Vous pouvez soumettre votre demande.'
                  : hasOcrMismatch
                    ? '⚠️ Des différences ont été détectées. Votre demande sera vérifiée manuellement après soumission.'
                    : 'ℹ️ Documents téléversés. Vous pouvez continuer.'}
              </div>
            )}
          </div>
        )}

        {step === 6 && (
          <div className="step-card">
            <h2>Récapitulatif</h2>

            {hasOcrMismatch && (
              <div className="alert alert-warning" style={{ marginBottom: 20 }}>
                ⚠️ Des incohérences ont été détectées. Votre demande sera vérifiée manuellement.
              </div>
            )}

            {!allDocsUploaded && (
              <div className="alert alert-error" style={{ marginBottom: 20 }}>
                ❌ Veuillez retourner à l'étape 5 et téléverser tous les documents requis.
              </div>
            )}

            <div className="summary">
              <div className="summary-section">
                <h3>Compte</h3>
                <p>{types.find(t => t.code === form.accountTypeCode)?.label}</p>
              </div>
              <div className="summary-section">
                <h3>Identité</h3>
                <p>Né(e) le {form.dateOfBirth} · {form.nationality} · {form.phoneNumber}</p>
              </div>
              <div className="summary-section">
                <h3>Adresse</h3>
                <p>{form.addressLine1}{form.addressLine2 ? `, ${form.addressLine2}` : ''}<br/>{form.postalCode} {form.city}, {form.country}</p>
              </div>
              <div className="summary-section">
                <h3>Emploi</h3>
                <p>{form.employmentStatus} {form.employerName ? `· ${form.employerName}` : ''} {form.monthlyIncome ? `· ${form.monthlyIncome}€/mois` : ''}</p>
              </div>
              <div className="summary-section summary-section-full">
                <h3>Documents KYC</h3>
                <div className="summary-kyc-list">
                  {kycChecklist.map(item => {
                    const doc = getDocForType(item.type);
                    const cfg = doc ? getKycStatus(doc) : null;
                    return (
                      <div key={item.type} className="summary-kyc-item">
                        <span>{item.icon} {item.label}</span>
                        {cfg
                          ? <span className={`kyc-checklist-badge ${cfg.className}`}>{cfg.icon} {cfg.label}</span>
                          : <span className="kyc-checklist-badge kyc-status-missing">Non fourni</span>
                        }
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="step-actions">
        {step > 1 && <button className="btn btn-outline" onClick={() => setStep(s => s - 1)}>← Précédent</button>}
        <div style={{ flex: 1 }} />
        {step < totalSteps ? (
          <button className="btn btn-primary" onClick={nextStep}>Suivant →</button>
        ) : (
          <button className="btn btn-primary" onClick={handleSubmit} disabled={submitting || !allDocsUploaded}>
            {submitting ? 'Envoi...' : '✓ Soumettre la demande'}
          </button>
        )}
      </div>
    </div>
  );
};

export default NewAccount;

