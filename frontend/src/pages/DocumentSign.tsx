import React, { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import SignatureCanvas from 'react-signature-canvas';
import { documentsApi } from '../api/documents';
import { DocumentSigner } from '../types';
import './DocumentSign.css';

const DocumentSign: React.FC = () => {
  const { token } = useParams<{ token: string }>();
  const sigCanvas = useRef<SignatureCanvas>(null);

  const [signer, setSigner] = useState<DocumentSigner | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  // OTP state
  const [phoneNumber, setPhoneNumber] = useState('');
  const [otpCode, setOtpCode] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [otpVerified, setOtpVerified] = useState(false);
  const [otpSending, setOtpSending] = useState(false);
  const [otpVerifying, setOtpVerifying] = useState(false);
  const [otpError, setOtpError] = useState('');

  useEffect(() => {
    const verify = async () => {
      if (!token) return;
      try {
        const res = await documentsApi.verifyToken(token);
        setSigner(res.data.data);
        if (res.data.data.otpVerified) {
          setOtpVerified(true);
        }
      } catch (err: any) {
        setError('Invalid or expired signature link.');
      } finally {
        setLoading(false);
      }
    };
    verify();
  }, [token]);

  const handleSendOtp = async () => {
    if (!token) return;
    if (!phoneNumber.trim()) {
      setOtpError('Please enter your phone number.');
      return;
    }

    setOtpError('');
    setOtpSending(true);
    try {
      await documentsApi.sendOtp(token, phoneNumber);
      setOtpSent(true);
    } catch (err: any) {
      setOtpError(err.response?.data?.message || 'Failed to send OTP. Please try again.');
    } finally {
      setOtpSending(false);
    }
  };

  const handleVerifyOtp = async () => {
    if (!token) return;
    if (!otpCode.trim()) {
      setOtpError('Please enter the verification code.');
      return;
    }

    setOtpError('');
    setOtpVerifying(true);
    try {
      const res = await documentsApi.verifyOtp(token, otpCode);
      if (res.data.data.verified) {
        setOtpVerified(true);
        setOtpError('');
      } else {
        setOtpError('Invalid verification code. Please try again.');
      }
    } catch (err: any) {
      setOtpError(err.response?.data?.message || 'Invalid verification code. Please try again.');
    } finally {
      setOtpVerifying(false);
    }
  };

  const clearSignature = () => {
    sigCanvas.current?.clear();
  };

  const handleSign = async () => {
    if (!token) return;
    if (!otpVerified) {
      setError('Please verify your phone number before signing.');
      return;
    }
    if (sigCanvas.current?.isEmpty()) {
      setError('Please draw your signature before submitting.');
      return;
    }

    const signatureData = sigCanvas.current?.toDataURL('image/png') || '';
    setSubmitting(true);
    setError('');
    try {
      await documentsApi.sign(token, signatureData);
      setSuccess(true);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to sign document. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="sign-container">
        <div className="sign-card">
          <div className="page-loading">
            <div className="spinner" />
            <p>Verifying your signature link...</p>
          </div>
        </div>
      </div>
    );
  }

  if (success) {
    return (
      <div className="sign-container">
        <div className="sign-card">
          <div className="sign-success">
            <span className="success-icon">✅</span>
            <h2>Document Signed!</h2>
            <p>You have successfully signed the document. All parties will be notified.</p>
          </div>
        </div>
      </div>
    );
  }

  if (error && !signer) {
    return (
      <div className="sign-container">
        <div className="sign-card">
          <div className="sign-error">
            <span className="error-icon">❌</span>
            <h2>Invalid Link</h2>
            <p>{error}</p>
          </div>
        </div>
      </div>
    );
  }

  if (signer?.status === 'SIGNED') {
    return (
      <div className="sign-container">
        <div className="sign-card">
          <div className="sign-success">
            <span className="success-icon">✅</span>
            <h2>Already Signed</h2>
            <p>You have already signed this document.</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="sign-container">
      <div className="sign-card">
        <div className="sign-header">
          <span className="sign-logo">✍️</span>
          <h1>Sign Document</h1>
          {signer && (
            <p>
              Hello <strong>{signer.name}</strong>, please verify your identity and sign below to complete the
              process.
            </p>
          )}
        </div>

        {error && <div className="alert alert-error">{error}</div>}

        {/* Step 1: SMS OTP Verification */}
        <div className={`otp-section ${otpVerified ? 'otp-verified' : ''}`}>
          <div className="otp-header">
            <span className="step-number">{otpVerified ? '✅' : '1'}</span>
            <div>
              <h3>Phone Verification</h3>
              <p>A verification code will be sent to your phone via SMS</p>
            </div>
          </div>

          {otpVerified ? (
            <div className="otp-success-message">
              <span>✅ Phone number verified successfully</span>
            </div>
          ) : !otpSent ? (
            <div className="otp-form">
              {otpError && <div className="alert alert-error alert-sm">{otpError}</div>}
              <div className="otp-input-row">
                <input
                  type="tel"
                  className="form-input"
                  placeholder="+33 6 12 34 56 78"
                  value={phoneNumber}
                  onChange={(e) => setPhoneNumber(e.target.value)}
                />
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleSendOtp}
                  disabled={otpSending}
                >
                  {otpSending ? 'Sending...' : 'Send Code'}
                </button>
              </div>
            </div>
          ) : (
            <div className="otp-form">
              {otpError && <div className="alert alert-error alert-sm">{otpError}</div>}
              <p className="otp-info">A 6-digit code has been sent to <strong>{phoneNumber}</strong></p>
              <div className="otp-input-row">
                <input
                  type="text"
                  className="form-input otp-code-input"
                  placeholder="000000"
                  maxLength={6}
                  value={otpCode}
                  onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                />
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleVerifyOtp}
                  disabled={otpVerifying || otpCode.length !== 6}
                >
                  {otpVerifying ? 'Verifying...' : 'Verify'}
                </button>
              </div>
              <button
                type="button"
                className="btn btn-link"
                onClick={() => { setOtpSent(false); setOtpCode(''); setOtpError(''); }}
              >
                Resend code
              </button>
            </div>
          )}
        </div>

        {/* Step 2: Draw Signature */}
        <div className={`signature-section ${!otpVerified ? 'section-disabled' : ''}`}>
          <div className="otp-header">
            <span className="step-number">2</span>
            <div>
              <h3>Draw Your Signature</h3>
              <p>Sign in the box below to finalize</p>
            </div>
          </div>

          <div className="signature-area">
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
              onClick={clearSignature}
              className="btn btn-outline btn-sm"
              disabled={!otpVerified}
            >
              Clear
            </button>
          </div>

          <button
            type="button"
            className="btn btn-primary btn-block"
            onClick={handleSign}
            disabled={submitting || !otpVerified}
          >
            {submitting ? 'Signing...' : 'Submit Signature'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default DocumentSign;
