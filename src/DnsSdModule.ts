import { requireNativeModule } from "expo-modules-core";

import type { DnsSdEvents } from "./DnsSd.types";

declare class DnsSdModuleType extends globalThis.ExpoModule<DnsSdEvents> {
  startDiscovery(type: string): void;
  stopDiscovery(): void;
  registerService(
    name: string,
    type: string,
    port: number,
    txt: Record<string, string>
  ): void;
  unregisterService(): void;
}

export default requireNativeModule<DnsSdModuleType>("DnsSd");
