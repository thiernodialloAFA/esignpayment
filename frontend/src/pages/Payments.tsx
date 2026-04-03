import React, { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { loadStripe, Stripe, StripeCardElement } from '@stripe/stripe-js';
import { Elements, CardElement, useStripe, useElements } from '@stripe/react-stripe-js';
import { paymentsApi } from '../api/payments';
import { CreatePaymentRequest, Payment } from '../types';
import './Payments.css';

const statusLabel: Record<string, string> = {
  PENDING: 'Pending',
  PROCESSING: 'Processing',
  SUCCEEDED: 'Succeeded',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
  REFUNDED: 'Refunded',
};

const CARD_ELEMENT_OPTIONS = {
  style: {
    base: {
      fontSize: '16px',
      color: '#374151',
      fontFamily: 'inherit',
      '::placeholder': {
        color: '#9ca3af',
      },
    },
    invalid: {
      color: '#ef4444',
    },
  },
};

/** Inner form that has access to Stripe hooks (must be inside <Elements>) */
const PaymentForm: React.FC<{
  onSuccess: () => void;
  onCancel: () => void;
}> = ({ onSuccess, onCancel }) => {
  const stripe = useStripe();
  const elements = useElements();
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');
  const [cardComplete, setCardComplete] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CreatePaymentRequest>();

  const onSubmit = async (data: CreatePaymentRequest) => {
    if (!stripe || !elements) {
      setFormError('Stripe is not loaded yet. Please try again.');
      return;
    }

    const cardElement = elements.getElement(CardElement);
    if (!cardElement) {
      setFormError('Card element not found.');
      return;
    }

    setFormError('');
    setSubmitting(true);

    try {
      // Step 1: Create PaymentIntent on the backend
      const res = await paymentsApi.create({
        ...data,
        amount: Number(data.amount),
        currency: data.currency.toUpperCase(),
      });

      const clientSecret = res.data.data.clientSecret;
      if (!clientSecret) {
        setFormError('Failed to initialize payment. Please try again.');
        setSubmitting(false);
        return;
      }

      // Step 2: Confirm card payment with 3D Secure support
      const { error, paymentIntent } = await stripe.confirmCardPayment(clientSecret, {
        payment_method: {
          card: cardElement as StripeCardElement,
        },
      });

      if (error) {
        setFormError(error.message || 'Payment failed. Please try again.');
        setSubmitting(false);
        return;
      }

      if (paymentIntent && paymentIntent.status === 'succeeded') {
        // Step 3: Confirm on the backend
        await paymentsApi.confirm(paymentIntent.id);
        onSuccess();
      } else {
        setFormError('Payment was not completed. Please try again.');
      }
    } catch (err: any) {
      setFormError(err.response?.data?.message || 'Failed to process payment.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="form-card">
      <h2>New Card Payment</h2>
      <p className="form-subtitle">Pay securely with your credit or debit card (3D Secure enabled)</p>
      {formError && <div className="alert alert-error">{formError}</div>}
      <form onSubmit={handleSubmit(onSubmit)} className="payment-form">
        <div className="form-row-3">
          <div className="form-group">
            <label>Amount *</label>
            <input
              type="number"
              step="0.01"
              min="0.01"
              className={`form-input ${errors.amount ? 'error' : ''}`}
              placeholder="0.00"
              {...register('amount', {
                required: 'Amount is required',
                min: { value: 0.01, message: 'Amount must be greater than 0' },
              })}
            />
            {errors.amount && (
              <span className="form-error">{errors.amount.message}</span>
            )}
          </div>

          <div className="form-group">
            <label>Currency *</label>
            <select
              className={`form-input ${errors.currency ? 'error' : ''}`}
              {...register('currency', { required: 'Currency is required' })}
            >
              <option value="EUR">EUR</option>
              <option value="USD">USD</option>
              <option value="GBP">GBP</option>
              <option value="CHF">CHF</option>
            </select>
            {errors.currency && (
              <span className="form-error">{errors.currency.message}</span>
            )}
          </div>

          <div className="form-group">
            <label>Description</label>
            <input
              type="text"
              className="form-input"
              placeholder="Optional description"
              {...register('description')}
            />
          </div>
        </div>

        <div className="form-group">
          <label>Card Details *</label>
          <div className="stripe-card-element">
            <CardElement
              options={CARD_ELEMENT_OPTIONS}
              onChange={(e) => setCardComplete(e.complete)}
            />
          </div>
          <span className="card-hint">🔒 Secured by Stripe — 3D Secure authentication may be required</span>
        </div>

        <div className="form-actions">
          <button
            type="submit"
            className="btn btn-primary"
            disabled={submitting || !stripe || !cardComplete}
          >
            {submitting ? 'Processing payment...' : 'Pay Now'}
          </button>
          <button
            type="button"
            className="btn btn-outline"
            onClick={onCancel}
            disabled={submitting}
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

const Payments: React.FC = () => {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [stripePromise, setStripePromise] = useState<Promise<Stripe | null> | null>(null);

  const loadPayments = useCallback(async () => {
    try {
      const res = await paymentsApi.list();
      setPayments(res.data.data);
    } catch (err) {
      console.error('Failed to load payments', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPayments();
  }, [loadPayments]);

  // Load Stripe publishable key from backend
  useEffect(() => {
    const loadConfig = async () => {
      try {
        const res = await paymentsApi.getConfig();
        const key = res.data.data.publishableKey;
        if (key) {
          setStripePromise(loadStripe(key));
        }
      } catch (err) {
        console.error('Failed to load Stripe config', err);
      }
    };
    loadConfig();
  }, []);

  const handleCancel = async (id: string) => {
    if (!window.confirm('Cancel this payment?')) return;
    try {
      await paymentsApi.cancel(id);
      await loadPayments();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to cancel payment.');
    }
  };

  const handlePaymentSuccess = async () => {
    setShowForm(false);
    await loadPayments();
  };

  const totalSucceeded = payments
    .filter((p) => p.status === 'SUCCEEDED')
    .reduce((sum, p) => sum + Number(p.amount), 0);

  return (
    <div className="payments-page">
      <div className="page-header">
        <div>
          <h1>Payments</h1>
          <p className="page-subtitle">Manage your card payment transactions</p>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => setShowForm(!showForm)}
        >
          {showForm ? 'Cancel' : '+ New Payment'}
        </button>
      </div>

      <div className="payment-summary">
        <div className="summary-item">
          <span className="summary-value">{payments.length}</span>
          <span className="summary-label">Total Transactions</span>
        </div>
        <div className="summary-item">
          <span className="summary-value">
            {payments.filter((p) => p.status === 'SUCCEEDED').length}
          </span>
          <span className="summary-label">Succeeded</span>
        </div>
        <div className="summary-item">
          <span className="summary-value">{totalSucceeded.toFixed(2)}€</span>
          <span className="summary-label">Total Paid</span>
        </div>
      </div>

      {showForm && stripePromise && (
        <Elements stripe={stripePromise}>
          <PaymentForm
            onSuccess={handlePaymentSuccess}
            onCancel={() => setShowForm(false)}
          />
        </Elements>
      )}

      {showForm && !stripePromise && (
        <div className="form-card">
          <div className="page-loading">
            <div className="spinner" />
            <p>Loading payment form...</p>
          </div>
        </div>
      )}

      {loading ? (
        <div className="page-loading">
          <div className="spinner" />
          <p>Loading payments...</p>
        </div>
      ) : payments.length === 0 ? (
        <div className="empty-state">
          <span style={{ fontSize: 48 }}>💳</span>
          <p>No payments yet. Create your first payment to get started.</p>
        </div>
      ) : (
        <div className="payments-table-wrapper">
          <table className="payments-table">
            <thead>
              <tr>
                <th>Reference</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Description</th>
                <th>Date</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((pay) => (
                <tr key={pay.id}>
                  <td className="pay-ref">
                    {pay.providerReference || pay.id.substring(0, 8).toUpperCase()}
                  </td>
                  <td className="pay-amount">
                    <strong>
                      {Number(pay.amount).toFixed(2)} {pay.currency}
                    </strong>
                  </td>
                  <td>
                    <span className={`badge badge-${pay.status.toLowerCase()}`}>
                      {statusLabel[pay.status] || pay.status}
                    </span>
                  </td>
                  <td className="text-muted">{pay.description || '—'}</td>
                  <td className="text-muted">
                    {new Date(pay.createdAt).toLocaleString()}
                  </td>
                  <td>
                    {pay.status === 'PENDING' && (
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => handleCancel(pay.id)}
                      >
                        Cancel
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default Payments;
