export type UserRole = 'ROLE_USER' | 'ROLE_ADMIN';

export type DocumentStatus =
  | 'DRAFT'
  | 'PENDING_SIGNATURE'
  | 'PARTIALLY_SIGNED'
  | 'COMPLETED'
  | 'CANCELLED';

export type SignerStatus = 'PENDING' | 'SIGNED' | 'DECLINED';

export type PaymentStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'REFUNDED';

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  createdAt: string;
}

export interface DocumentSigner {
  id: string;
  email: string;
  name: string;
  status: SignerStatus;
  otpVerified: boolean;
  signedAt: string | null;
  createdAt: string;
}

export interface Document {
  id: string;
  title: string;
  description: string | null;
  fileName: string;
  contentType: string;
  status: DocumentStatus;
  owner: User;
  signers: DocumentSigner[];
  createdAt: string;
  updatedAt: string;
}

export interface Payment {
  id: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  description: string | null;
  providerReference: string | null;
  clientSecret: string | null;
  stripePublishableKey: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string | null;
  data: T;
}

export interface CreateDocumentRequest {
  title: string;
  description?: string;
  fileName: string;
  contentType: string;
  fileContent: string;
  signers: { email: string; name: string; phone?: string }[];
}

export interface CreatePaymentRequest {
  amount: number;
  currency: string;
  description?: string;
}

export interface OtpResponse {
  sent: boolean;
  verified: boolean;
  message: string;
}

export interface PaymentConfig {
  publishableKey: string;
}

// ── Account Opening ──

export type ApplicationStatus =
  | 'DRAFT' | 'SUBMITTED' | 'KYC_PENDING' | 'KYC_VERIFIED'
  | 'CONTRACT_PENDING' | 'CONTRACT_SIGNED' | 'APPROVED' | 'ACTIVE';

export type KycDocumentType = 'ID_CARD' | 'PASSPORT' | 'PROOF_OF_ADDRESS' | 'INCOME_PROOF';
export type KycDocumentStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type EmploymentStatusType = 'EMPLOYED' | 'SELF_EMPLOYED' | 'UNEMPLOYED' | 'STUDENT' | 'RETIRED';
export type OcrVerificationStatus = 'PENDING' | 'VERIFIED' | 'MISMATCH' | 'FAILED' | 'NOT_AVAILABLE';

export interface OcrVerificationDetail {
  fieldName: string;
  fieldLabel: string;
  declaredValue: string;
  extractedValue: string;
  matchScore: number;
  matched: boolean;
}

export interface AccountType {
  id: string;
  code: string;
  label: string;
  description: string | null;
  monthlyFee: number;
}

export interface KycDocumentItem {
  id: string;
  documentType: KycDocumentType;
  fileName: string;
  status: KycDocumentStatus;
  rejectionReason: string | null;
  createdAt: string;
  ocrStatus: OcrVerificationStatus | null;
  ocrMatchScore: number | null;
  documentTypeValid: boolean | null;
  ocrDetails: OcrVerificationDetail[] | null;
  ocrWarnings: string[] | null;
}

export interface StatusHistoryItem {
  fromStatus: string | null;
  toStatus: string;
  comment: string | null;
  changedAt: string;
}

export interface AccountApplication {
  id: string;
  referenceNumber: string;
  status: ApplicationStatus;
  accountType: AccountType;
  dateOfBirth: string | null;
  phoneNumber: string | null;
  nationality: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  postalCode: string | null;
  country: string | null;
  employmentStatus: EmploymentStatusType | null;
  employerName: string | null;
  jobTitle: string | null;
  monthlyIncome: number | null;
  contractDocumentId: string | null;
  kycDocuments: KycDocumentItem[];
  statusHistory: StatusHistoryItem[];
  submittedAt: string | null;
  approvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountApplicationRequest {
  accountTypeCode: string;
  dateOfBirth?: string;
  phoneNumber?: string;
  nationality?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  postalCode?: string;
  country?: string;
  employmentStatus?: string;
  employerName?: string;
  jobTitle?: string;
  monthlyIncome?: number;
}

