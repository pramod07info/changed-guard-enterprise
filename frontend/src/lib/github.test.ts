import { expect, test } from 'vitest';
import { parseGitHubPr } from './github';

test('valid PR request contains only user-selected number, path and graph', () => {
  expect(parseGitHubPr('7', '.github/openapi.json', '{"orders":["payments"]}')).toEqual({
    pullNumber: 7, contractPath: '.github/openapi.json', dependencies: { orders: ['payments'] }
  });
});
test('invalid PR numbers are rejected', () => {
  for (const number of ['0', '-1', '1.5', 'Infinity', '2147483648', 'abc'])
    expect(() => parseGitHubPr(number, 'openapi.json', '{}')).toThrow('positive integer');
});
test('paths cannot contain traversal or query parameters', () => {
  for (const path of ['../openapi.json', '/openapi.json', 'api/../openapi.json', 'openapi.json?ref=main', 'openapi.yaml'])
    expect(() => parseGitHubPr('1', path, '{}')).toThrow('relative .json path');
});
test('dependency shape remains validated', () => {
  expect(() => parseGitHubPr('1', 'openapi.json', '{"orders":"payments"}')).toThrow('provider name lists');
});
