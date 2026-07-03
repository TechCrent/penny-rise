export interface SusuGroupListResponse {
  group_id: string;
  name: string;
  status: 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | 'FROZEN';
  organiser_user_id: string;
  is_organiser: boolean;
  contribution_amount: number;
  contribution_amount_cedis: string;
  frequency: 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';
  target_member_count: number;
  current_member_count: number;
  current_round_number: number | null;
  total_rounds: number | null;
  next_due_date: string | null;
  caller_rotation_position: number | null;
  caller_is_next_recipient: boolean;
  join_code: string;
  created_at: string;
}

export interface SusuMemberSummary {
  user_id: string;
  display_name: string;
  rotation_position: number | null;
  membership_status: string;
  joined_at: string;
  is_organiser: boolean;
}

export interface ContributionStatus {
  member_user_id: string;
  display_name: string;
  status: 'PENDING' | 'PAID' | 'LATE' | 'MISSED' | 'WAIVED';
  is_late: boolean;
  penalty_amount: number;
  paid_at: string | null;
}

export interface CurrentRoundSummary {
  id: string;
  round_number: number;
  total_rounds: number;
  status: 'PENDING' | 'COLLECTING' | 'DISBURSING' | 'DISBURSED' | 'COMPLETED' | 'SKIPPED';
  recipient_user_id: string;
  recipient_display_name: string;
  scheduled_collection_at: string | null;
  expected_pot_amount: number;
  expected_pot_amount_cedis: string;
  actual_pot_amount: number | null;
  contributions: ContributionStatus[];
}

export interface CallerMembership {
  id: string;
  rotation_position: number | null;
  status: string;
  joined_at: string;
}

export interface SusuGroupDetailResponse {
  id: string;
  name: string;
  status: 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | 'FROZEN';
  organiser_user_id: string;
  is_caller_organiser: boolean;
  contribution_amount: number;
  contribution_amount_cedis: string;
  frequency: 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';
  target_member_count: number;
  join_code: string;
  start_date: string | null;
  created_at: string;
  current_round: CurrentRoundSummary | null;
  members: SusuMemberSummary[];
  caller_membership: CallerMembership;
}

export interface SusuActivationResponse {
  id: string;
  status: string;
  start_date: string;
  current_round_number: number;
}

export interface SusuContributionResponse {
  id: string;
  status: string;
  round_fully_collected: boolean;
}

export interface WalletBalance {
  accountId: string;
  balancePesewas: number;
  balanceCedis: string;
}
