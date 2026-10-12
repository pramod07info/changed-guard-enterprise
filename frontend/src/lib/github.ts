import { parseDependencies } from './input';

export function parseGitHubPr(pull: string, contractPath: string, dependencies: string) {
  const pullNumber = Number(pull);
  if (!/^[0-9]+$/.test(pull) || !Number.isSafeInteger(pullNumber) || pullNumber < 1 || pullNumber > 2147483647)
    throw new Error('PR number must be a positive integer.');
  validateContractPath(contractPath);
  return { pullNumber, contractPath, dependencies: parseDependencies(dependencies) };
}

export function validateContractPath(contractPath: string): string {
  if (contractPath.length > 500 || !/^(?:[A-Za-z0-9_.-]+\/)*[A-Za-z0-9_.-]+\.json$/.test(contractPath)
      || contractPath.split('/').some(segment => segment === '.' || segment === '..'))
    throw new Error('Contract path must be a relative .json path without traversal segments.');
  return contractPath;
}
