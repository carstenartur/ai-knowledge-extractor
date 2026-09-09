export async function load() {
  const response = await fetch('/api/order');
  const order = await response.json();
  if (order.status === 'READY') return 'start';
  if (order.state === 'BLOCKED') return 'wait';
  return 'unknown';
}
