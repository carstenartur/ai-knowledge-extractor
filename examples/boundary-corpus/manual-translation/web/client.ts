export async function load() {
  const response = await fetch('/api/items');
  const items = await response.json();
  return items.map(item => ({title: item.name, enabled: item.available}));
}
