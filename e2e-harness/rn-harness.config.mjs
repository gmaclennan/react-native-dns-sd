import { androidPlatform, androidEmulator } from '@react-native-harness/platform-android';

export default {
  entryPoint: './src/main.tsx',
  appRegistryComponentName: 'NsdTestApp',
  runners: [
    androidPlatform({
      name: 'emu1',
      device: androidEmulator('avd1'),
      bundleId: 'com.nsdtest',
    }),
    androidPlatform({
      name: 'emu2',
      device: androidEmulator('avd2'),
      bundleId: 'com.nsdtest',
    }),
  ],
  bridgeTimeout: 120_000,
  forwardClientLogs: true,
};
