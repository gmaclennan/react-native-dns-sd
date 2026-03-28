import { EventEmitter, type Subscription } from "expo-modules-core";

import DnsSdModule from "./DnsSdModule";
import type { DnsSdEvents, RegisterServiceOptions, Service } from "./DnsSd.types";

export type { Service, DnsSdEvents, RegisterServiceOptions };

const emitter = new EventEmitter(DnsSdModule);

/**
 * Start discovering services of the given type.
 *
 * @param type - The service type to discover (e.g., "_http._tcp.")
 */
export function startDiscovery(type: string): void {
  DnsSdModule.startDiscovery(type);
}

/**
 * Stop the current discovery session.
 */
export function stopDiscovery(): void {
  DnsSdModule.stopDiscovery();
}

/**
 * Register (advertise) a service on the network.
 *
 * @param options - The service registration options
 */
export function registerService(options: RegisterServiceOptions): void {
  DnsSdModule.registerService(
    options.name,
    options.type,
    options.port,
    options.txt ?? {}
  );
}

/**
 * Unregister (stop advertising) the currently registered service.
 */
export function unregisterService(): void {
  DnsSdModule.unregisterService();
}

/**
 * Add a listener for DNS-SD events.
 *
 * @param eventName - The event to listen for
 * @param listener - The callback function
 * @returns A subscription that can be removed
 */
export function addListener<K extends keyof DnsSdEvents>(
  eventName: K,
  listener: (event: DnsSdEvents[K]) => void
): Subscription {
  return emitter.addListener(eventName, listener);
}
