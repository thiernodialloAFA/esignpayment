import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useForm, useFieldArray } from 'react-hook-form';
import { useSearchParams } from 'react-router-dom';
import SignatureCanvas from 'react-signature-canvas';
import { documentsApi } from '../api/documents';
import { CreateDocumentRequest, Document, DocumentSigner } from '../types';
import './Documents.css';

const statusLabel: Record<string, string> = {
  DRAFT: 'Draft',
  PENDING_SIGNATURE: 'Pending Signature',
  PARTIALLY_SIGNED: 'Partially Signed',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

const Documents: React.FC = () => {
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [resendingId, setResendingId] = useState<string | null>(null);
  const [viewingPdf, setViewingPdf] = useState<{ url: string; title: string } | null>(null);
  const [loadingPdfId, setLoadingPdfId] = useState<string | null>(null);

  // Live signing state
  const [liveSignDoc, setLiveSignDoc] = useState<Document | null>(null);
  const [liveSignSigner, setLiveSignSigner] = useState<DocumentSigner | null>(null);
  const [liveSignSubmitting, setLiveSignSubmitting] = useState(false);
  const [liveSignError, setLiveSignError] = useState('');
  const sigCanvas = useRef<SignatureCanvas>(null);

  const [searchParams, setSearchParams] = useSearchParams();

  const { register, handleSubmit, control, reset, formState: { errors } } =
    useForm<CreateDocumentRequest & { file: FileList }>({
      defaultValues: { signers: [{ email: '', name: '', phone: '' }] },
    });

  const { fields, append, remove } = useFieldArray({ control, name: 'signers' });

  const loadDocuments = useCallback(async () => {
    try {
      const res = await documentsApi.list();
      setDocuments(res.data.data);
      return res.data.data;
    } catch (err) {
      console.error('Failed to load documents', err);
      return [];
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDocuments().then((docs) => {
      // Auto-open live sign modal if redirected from AccountDetail
      const liveSignId = searchParams.get('liveSign');
      if (liveSignId && docs.length > 0) {
        const doc = docs.find((d: Document) => d.id === liveSignId);
        if (doc) {
          const pendingSigner = doc.signers.find((s: DocumentSigner) => s.status === 'PENDING');
          if (pendingSigner) {
            setLiveSignDoc(doc);
            setLiveSignSigner(pendingSigner);
          }
        }
        setSearchParams({}, { replace: true });
      }
    });
  }, [loadDocuments, searchParams, setSearchParams]);

  const readFileAsBase64 = (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result as string;
        resolve(result.split(',')[1]);
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });

  const onSubmit = async (data: CreateDocumentRequest & { file: FileList }) => {
    setFormError('');
    setSubmitting(true);
    try {
      const file = data.file[0];
      const fileContent = await readFileAsBase64(file);
      const payload: CreateDocumentRequest = {
        title: data.title,
        description: data.description,
        fileName: file.name,
        contentType: file.type,
        fileContent,
        signers: data.signers.filter((s) => s.email && s.name),
      };
      await documentsApi.create(payload);
      reset();
      setShowForm(false);
      await loadDocuments();
    } catch (err: any) {
      setFormError(err.response?.data?.message || 'Failed to create document.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSend = async (id: string) => {
    try {
      await documentsApi.sendForSignature(id);
      await loadDocuments();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to send document.');
    }
  };

  const handleResend = async (id: string) => {
    setResendingId(id);
    try {
      await documentsApi.resendForSignature(id);
      await loadDocuments();
      alert('Signing request resent to pending signers.');
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to resend signing request.');
    } finally {
      setResendingId(null);
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this document?')) return;
    setDeletingId(id);
    try {
      await documentsApi.delete(id);
      setDocuments((prev) => prev.filter((d) => d.id !== id));
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to delete document.');
    } finally {
      setDeletingId(null);
    }
  };

  const handleView = async (doc: Document) => {
    setLoadingPdfId(doc.id);
    try {
      const res = await documentsApi.download(doc.id);
      const blob = new Blob([res.data], { type: doc.contentType || 'application/pdf' });
      const url = URL.createObjectURL(blob);
      setViewingPdf({ url, title: doc.title });
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to load document.');
    } finally {
      setLoadingPdfId(null);
    }
  };


  const closePdfViewer = () => {
    if (viewingPdf) {
      URL.revokeObjectURL(viewingPdf.url);
      setViewingPdf(null);
    }
  };

  // ── Live Sign handlers ──

  const openLiveSign = (doc: Document, signer: DocumentSigner) => {
    setLiveSignDoc(doc);
    setLiveSignSigner(signer);
    setLiveSignError('');
  };

  const closeLiveSign = () => {
    setLiveSignDoc(null);
    setLiveSignSigner(null);
    setLiveSignError('');
    sigCanvas.current?.clear();
  };

  const handleLiveSign = async () => {
    if (!liveSignDoc || !liveSignSigner) return;
    if (sigCanvas.current?.isEmpty()) {
      setLiveSignError('Veuillez dessiner la signature avant de valider.');
      return;
    }
    const signatureData = sigCanvas.current?.toDataURL('image/png') || '';
    setLiveSignSubmitting(true);
    setLiveSignError('');
    try {
      await documentsApi.liveSign(liveSignDoc.id, liveSignSigner.id, signatureData);
      closeLiveSign();
      await loadDocuments();
      alert('✅ Document signé en direct avec succès !');
    } catch (err: any) {
      setLiveSignError(err.response?.data?.message || 'Erreur lors de la signature.');
    } finally {
      setLiveSignSubmitting(false);
    }
  };

  const hasPendingSigners = (doc: Document) =>
    doc.signers.some((s) => s.status === 'PENDING') &&
    doc.status !== 'COMPLETED' && doc.status !== 'CANCELLED';

  return (
    <div className="documents-page">
      <div className="page-header">
        <div>
          <h1>Documents</h1>
          <p className="page-subtitle">Upload and manage your documents for signature</p>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => setShowForm(!showForm)}
        >
          {showForm ? 'Cancel' : '+ New Document'}
        </button>
      </div>

      {showForm && (
        <div className="form-card">
          <h2>Upload New Document</h2>
          {formError && <div className="alert alert-error">{formError}</div>}
          <form onSubmit={handleSubmit(onSubmit)} className="document-form">
            <div className="form-group">
              <label>Title *</label>
              <input
                type="text"
                className={`form-input ${errors.title ? 'error' : ''}`}
                {...register('title', { required: 'Title is required' })}
              />
              {errors.title && <span className="form-error">{errors.title.message}</span>}
            </div>

            <div className="form-group">
              <label>Description</label>
              <textarea className="form-input" rows={3} {...register('description')} />
            </div>

            <div className="form-group">
              <label>File *</label>
              <input
                type="file"
                className={`form-input ${errors.file ? 'error' : ''}`}
                accept=".pdf,.doc,.docx,.png,.jpg"
                {...register('file', { required: 'File is required' })}
              />
              {errors.file && <span className="form-error">{errors.file.message}</span>}
            </div>

            <div className="signers-section">
              <div className="signers-header">
                <label>Signers</label>
                <button
                  type="button"
                  className="btn btn-outline btn-sm"
                  onClick={() => append({ email: '', name: '', phone: '' })}
                >
                  + Add Signer
                </button>
              </div>
              {fields.map((field, index) => (
                <div key={field.id} className="signer-row">
                  <input
                    type="text"
                    className="form-input"
                    placeholder="Name"
                    {...register(`signers.${index}.name`)}
                  />
                  <input
                    type="email"
                    className="form-input"
                    placeholder="Email"
                    {...register(`signers.${index}.email`)}
                  />
                  <input
                    type="tel"
                    className="form-input"
                    placeholder="Phone (+33...)"
                    {...register(`signers.${index}.phone`)}
                  />
                  {fields.length > 1 && (
                    <button
                      type="button"
                      className="btn btn-danger btn-sm"
                      onClick={() => remove(index)}
                    >
                      ✕
                    </button>
                  )}
                </div>
              ))}
            </div>

            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Uploading...' : 'Upload Document'}
            </button>
          </form>
        </div>
      )}

      {loading ? (
        <div className="page-loading">
          <div className="spinner" />
          <p>Loading documents...</p>
        </div>
      ) : documents.length === 0 ? (
        <div className="empty-state">
          <span style={{ fontSize: 48 }}>📄</span>
          <p>No documents yet. Upload your first document to get started.</p>
        </div>
      ) : (
        <div className="documents-table-wrapper">
          <table className="documents-table">
            <thead>
              <tr>
                <th>Title</th>
                <th>File</th>
                <th>Status</th>
                <th>Signers</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => (
                <tr key={doc.id}>
                  <td className="doc-title">{doc.title}</td>
                  <td className="doc-file">{doc.fileName}</td>
                  <td>
                    <span className={`badge badge-${doc.status.toLowerCase()}`}>
                      {statusLabel[doc.status] || doc.status}
                    </span>
                  </td>
                  <td>
                    <div className="signers-info">
                      {doc.signers.length === 0 ? (
                        <span className="text-muted">—</span>
                      ) : (
                        doc.signers.map((s) => (
                          <div key={s.id} className="signer-chip">
                            <span
                              className={`dot dot-${s.status.toLowerCase()}`}
                            />
                            {s.name}
                            {s.status === 'PENDING' && hasPendingSigners(doc) && (
                              <button
                                className="btn-live-sign-inline"
                                title="Signer en direct"
                                onClick={() => openLiveSign(doc, s)}
                              >
                                ✍️
                              </button>
                            )}
                          </div>
                        ))
                      )}
                    </div>
                  </td>
                  <td className="text-muted">
                    {new Date(doc.createdAt).toLocaleDateString()}
                  </td>
                  <td>
                    <div className="action-buttons">
                      <button
                        className="btn btn-outline btn-sm"
                        onClick={() => handleView(doc)}
                        disabled={loadingPdfId === doc.id}
                      >
                        {loadingPdfId === doc.id ? '...' : '👁 View'}
                      </button>
                      {doc.status === 'DRAFT' && doc.signers.length > 0 && (
                        <button
                          className="btn btn-primary btn-sm"
                          onClick={() => handleSend(doc.id)}
                        >
                          Send
                        </button>
                      )}
                      {hasPendingSigners(doc) && (
                        <button
                          className="btn btn-success btn-sm"
                          onClick={() => {
                            const pending = doc.signers.find(s => s.status === 'PENDING');
                            if (pending) openLiveSign(doc, pending);
                          }}
                        >
                          ✍️ Signer en direct
                        </button>
                      )}
                      {doc.status !== 'COMPLETED' && doc.status !== 'DRAFT' && doc.status !== 'CANCELLED' && (
                        <button
                          className="btn btn-outline btn-sm"
                          onClick={() => handleResend(doc.id)}
                          disabled={resendingId === doc.id}
                        >
                          {resendingId === doc.id ? '...' : '🔄 Resend'}
                        </button>
                      )}
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => handleDelete(doc.id)}
                        disabled={deletingId === doc.id}
                      >
                        {deletingId === doc.id ? '...' : 'Delete'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* PDF Viewer Modal */}
      {viewingPdf && (
        <div className="pdf-overlay" onClick={closePdfViewer}>
          <div className="pdf-modal" onClick={(e) => e.stopPropagation()}>
            <div className="pdf-modal-header">
              <h3>{viewingPdf.title}</h3>
              <button className="btn btn-danger btn-sm" onClick={closePdfViewer}>
                ✕ Close
              </button>
            </div>
            <div className="pdf-modal-body">
              <iframe
                src={viewingPdf.url}
                title={viewingPdf.title}
                className="pdf-iframe"
              />
            </div>
          </div>
        </div>
      )}

      {/* Live Sign Modal */}
      {liveSignDoc && liveSignSigner && (
        <div className="pdf-overlay" onClick={closeLiveSign}>
          <div className="live-sign-modal" onClick={(e) => e.stopPropagation()}>
            <div className="live-sign-header">
              <div>
                <h3>✍️ Signature en direct</h3>
                <p className="live-sign-subtitle">
                  {liveSignDoc.title} — Signataire : <strong>{liveSignSigner.name}</strong>
                </p>
              </div>
              <button className="btn btn-danger btn-sm" onClick={closeLiveSign}>
                ✕
              </button>
            </div>

            <div className="live-sign-body">
              {liveSignError && <div className="alert alert-error">{liveSignError}</div>}

              <div className="live-sign-info">
                <div className="live-sign-info-item">
                  <span className="live-sign-label">📧 Email</span>
                  <span>{liveSignSigner.email}</span>
                </div>
                <div className="live-sign-info-item">
                  <span className="live-sign-label">📄 Document</span>
                  <span>{liveSignDoc.fileName}</span>
                </div>
              </div>

              <div className="live-sign-notice">
                ℹ️ Le client est physiquement présent. La vérification OTP par SMS n'est pas requise.
              </div>

              <div className="live-sign-canvas-section">
                <label>Signature du client :</label>
                <div className="canvas-wrapper">
                  <SignatureCanvas
                    ref={sigCanvas}
                    canvasProps={{
                      className: 'sig-canvas',
                      width: 500,
                      height: 200,
                    }}
                    backgroundColor="white"
                  />
                </div>
                <button
                  type="button"
                  className="btn btn-outline btn-sm"
                  onClick={() => sigCanvas.current?.clear()}
                >
                  Effacer
                </button>
              </div>

              <div className="live-sign-actions">
                <button className="btn btn-outline" onClick={closeLiveSign}>
                  Annuler
                </button>
                <button
                  className="btn btn-primary"
                  onClick={handleLiveSign}
                  disabled={liveSignSubmitting}
                >
                  {liveSignSubmitting ? 'Signature en cours...' : '✅ Valider la signature'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Documents;
