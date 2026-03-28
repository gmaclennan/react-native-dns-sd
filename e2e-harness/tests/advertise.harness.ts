import { describe, it, expect } from '@jest/globals';
import * as DnsSd from 'react-native-dns-sd';

describe('NSD Advertise', () => {
  it('registers a discoverable service and keeps it alive', async () => {
    const published = new Promise<DnsSd.Service>((resolve, reject) => {
      const sub = DnsSd.addListener('onServiceRegistered', (service) => {
        sub.remove();
        resolve(service);
      });
      const errSub = DnsSd.addListener('onError', (err) => {
        errSub.remove();
        reject(new Error(err.error));
      });
    });

    DnsSd.registerService({
      name: 'TestService-Emu2',
      type: '_nsdtest._tcp.',
      port: 12345,
      txt: { version: '1' },
    });

    const service = await published;
    expect(service).toBeDefined();
    expect(service.name).toContain('TestService-Emu2');

    // Keep the service alive long enough for the discover test to find it.
    await new Promise((resolve) => setTimeout(resolve, 90_000));

    DnsSd.unregisterService();
  }, 120_000);
});
