import { describe, it, expect } from '@jest/globals';
import * as DnsSd from 'react-native-dns-sd';
import type { Service } from 'react-native-dns-sd';

describe('NSD Discover', () => {
  it('discovers a service from another emulator', async () => {
    const resolved = await new Promise<Service>((resolve, reject) => {
      const timeout = setTimeout(() => {
        sub.remove();
        errSub.remove();
        reject(new Error('Discovery timed out after 120s'));
      }, 120_000);

      const sub = DnsSd.addListener('onServiceFound', (service) => {
        if (service.name.includes('Emu2')) {
          clearTimeout(timeout);
          sub.remove();
          errSub.remove();
          resolve(service);
        }
      });

      const errSub = DnsSd.addListener('onError', (err) => {
        clearTimeout(timeout);
        sub.remove();
        errSub.remove();
        reject(new Error(err.error));
      });

      DnsSd.startDiscovery('_nsdtest._tcp.');
    });

    expect(resolved).toBeDefined();
    expect(resolved.name).toContain('Emu2');
    expect(resolved.port).toBe(12345);
    expect(resolved.addresses.length).toBeGreaterThan(0);
    expect(
      resolved.addresses.some((a: string) => a.startsWith('192.168.77.'))
    ).toBe(true);

    DnsSd.stopDiscovery();
  }, 180_000);
});
