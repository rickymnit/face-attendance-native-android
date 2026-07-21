export function validateEnvironment(config: Record<string, unknown>): Record<string, unknown> {
  const nodeEnv = String(config.NODE_ENV ?? 'development');
  const errors: string[] = [];
  const jwtSecret = String(config.JWT_SECRET ?? '');
  if (!config.DATABASE_URL) errors.push('DATABASE_URL is required');
  if (!jwtSecret || jwtSecret === 'replace-with-a-long-random-secret') {
    if (nodeEnv === 'production') errors.push('JWT_SECRET must be set to a production secret');
  }
  if (!config.DEVICE_SETUP_TOKEN && nodeEnv === 'production') {
    errors.push('DEVICE_SETUP_TOKEN is required in production');
  }
  const adapter = String(config.ERP_ADAPTER ?? 'mock');
  if (!['mock', 'http'].includes(adapter)) errors.push('ERP_ADAPTER must be mock or http');
  if (adapter === 'http') {
    if (!config.ERP_BASE_URL) errors.push('ERP_BASE_URL is required when ERP_ADAPTER=http');
    if (!config.ERP_API_KEY) errors.push('ERP_API_KEY is required when ERP_ADAPTER=http');
  }
  if (errors.length) throw new Error(`Invalid environment: ${errors.join('; ')}`);
  return config;
}
