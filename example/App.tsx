import React, { useEffect, useState, useCallback } from 'react';
import { View, Text, ScrollView, Button, StyleSheet } from 'react-native';
import * as DnsSd from 'react-native-dns-sd';

export default function App() {
  const [services, setServices] = useState<Record<string, DnsSd.Service>>({});
  const [status, setStatus] = useState<string>('idle');
  const [logs, setLogs] = useState<string[]>([]);

  const addLog = useCallback((msg: string) => {
    setLogs((prev) => [...prev.slice(-50), `${new Date().toISOString().slice(11, 23)} ${msg}`]);
  }, []);

  useEffect(() => {
    const subs = [
      DnsSd.addListener('onDiscoveryStarted', (e) => {
        setStatus('discovering');
        addLog(`Discovery started: ${e.type}`);
      }),
      DnsSd.addListener('onDiscoveryStopped', (e) => {
        setStatus('idle');
        addLog(`Discovery stopped: ${e.type}`);
      }),
      DnsSd.addListener('onServiceFound', (service) => {
        addLog(`Found: ${service.name} at ${service.host}:${service.port}`);
        setServices((prev) => ({ ...prev, [service.name]: service }));
      }),
      DnsSd.addListener('onServiceLost', (e) => {
        addLog(`Lost: ${e.name}`);
        setServices((prev) => {
          const next = { ...prev };
          delete next[e.name];
          return next;
        });
      }),
      DnsSd.addListener('onServiceRegistered', (service) => {
        addLog(`Registered: ${service.name}`);
      }),
      DnsSd.addListener('onServiceUnregistered', (e) => {
        addLog(`Unregistered: ${e.name}`);
      }),
      DnsSd.addListener('onError', (e) => {
        addLog(`Error: ${e.error}`);
      }),
    ];
    return () => subs.forEach((s) => s.remove());
  }, [addLog]);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>DNS-SD Example</Text>
      <Text>Status: {status}</Text>
      <View style={styles.buttons}>
        <Button title="Discover _http._tcp." onPress={() => DnsSd.startDiscovery('_http._tcp.')} />
        <Button title="Stop" onPress={() => DnsSd.stopDiscovery()} />
        <Button
          title="Register"
          onPress={() =>
            DnsSd.registerService({
              name: 'TestService',
              type: '_http._tcp.',
              port: 8080,
              txt: { path: '/index.html' },
            })
          }
        />
        <Button title="Unregister" onPress={() => DnsSd.unregisterService()} />
      </View>
      <Text style={styles.subtitle}>Services ({Object.keys(services).length})</Text>
      {Object.values(services).map((s) => (
        <Text key={s.name} style={styles.service}>
          {s.name} - {s.host}:{s.port}
        </Text>
      ))}
      <Text style={styles.subtitle}>Logs</Text>
      <ScrollView style={styles.logs}>
        {logs.map((l, i) => (
          <Text key={i} style={styles.log}>{l}</Text>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, paddingTop: 60 },
  title: { fontSize: 24, fontWeight: 'bold', marginBottom: 10 },
  subtitle: { fontSize: 18, fontWeight: 'bold', marginTop: 15, marginBottom: 5 },
  buttons: { gap: 8, marginVertical: 10 },
  service: { fontSize: 14, paddingVertical: 2 },
  logs: { flex: 1, marginTop: 5 },
  log: { fontSize: 11, fontFamily: 'monospace', color: '#666' },
});
