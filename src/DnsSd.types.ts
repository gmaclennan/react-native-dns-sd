/**
 * Represents a resolved DNS-SD service.
 */
export interface Service {
  /** The name of the service */
  name: string;
  /** The full name of the service (e.g., "My Service._http._tcp.local.") */
  fullName: string;
  /** The host address of the service */
  host: string;
  /** The port number of the service */
  port: number;
  /** TXT record key-value pairs */
  txt: Record<string, string>;
  /** IP addresses associated with the service */
  addresses: string[];
}

/**
 * Events emitted by the DNS-SD module.
 */
export type DnsSdEvents = {
  /** Fired when a new service is found during discovery */
  onServiceFound: Service;
  /** Fired when a previously found service is lost */
  onServiceLost: { name: string; type: string };
  /** Fired when an error occurs */
  onError: { error: string; domain?: string };
  /** Fired when discovery starts */
  onDiscoveryStarted: { type: string };
  /** Fired when discovery stops */
  onDiscoveryStopped: { type: string };
  /** Fired when a service is successfully registered */
  onServiceRegistered: Service;
  /** Fired when a service is unregistered */
  onServiceUnregistered: { name: string; type: string };
};

/**
 * Options for registering a service.
 */
export interface RegisterServiceOptions {
  /** The name of the service to register */
  name: string;
  /** The service type (e.g., "_http._tcp.") */
  type: string;
  /** The port to advertise */
  port: number;
  /** Optional TXT record key-value pairs */
  txt?: Record<string, string>;
}
