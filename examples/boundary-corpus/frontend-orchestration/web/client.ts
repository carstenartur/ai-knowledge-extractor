export async function workflow() {
  const user = await fetch('/api/user');
  const items = await fetch('/api/items');
  const actions = await fetch('/api/actions');
  return {user, items, actions};
}
