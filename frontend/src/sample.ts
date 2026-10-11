const contract = (properties: Record<string, unknown>, required: string[]) => ({
  openapi: '3.0.3', info: { title: 'Payments', version: '1.0.0' },
  paths: { '/payments': { get: { responses: { '200': { description: 'Payment', content: {
    'application/json': { schema: { type: 'object', properties, required } }
  } } } } } }
});
export const sample = {
  service: 'payments',
  before: JSON.stringify(contract({ id: { type: 'string' }, amount: { type: 'number' } }, ['id', 'amount']), null, 2),
  after: JSON.stringify(contract({ id: { type: 'string' } }, ['id']), null, 2),
  dependencies: JSON.stringify({ orders: ['payments'], checkout: ['orders'] }, null, 2)
};
