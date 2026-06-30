export interface CreateSusuForm {
  name: string;
  contributionCedis: string;
  frequency: 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';
  targetMemberCount: number;
}

export interface CreateSusuErrors {
  name?: string;
  contributionCedis?: string;
  targetMemberCount?: string;
}

export function validateCreateSusuForm(form: CreateSusuForm): CreateSusuErrors {
  const errors: CreateSusuErrors = {};

  if (!form.name.trim()) {
    errors.name = 'Group name is required.';
  } else if (form.name.trim().length > 100) {
    errors.name = 'Group name must be 100 characters or fewer.';
  }

  const cedis = parseFloat(form.contributionCedis);
  if (!form.contributionCedis || isNaN(cedis) || cedis <= 0) {
    errors.contributionCedis = 'Enter a contribution amount greater than zero.';
  }

  if (form.targetMemberCount < 4 || form.targetMemberCount > 20) {
    errors.targetMemberCount = 'Member count must be between 4 and 20.';
  }

  return errors;
}

export function cedisToPesewas(cedisStr: string): number {
  return Math.round(parseFloat(cedisStr) * 100);
}
