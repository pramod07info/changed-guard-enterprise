import { featureNames, type Features } from '../src/lib/features';
export const enabledFeatures = Object.fromEntries(featureNames.map(name => [name, true])) as Features;
