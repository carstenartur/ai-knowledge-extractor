export async function load() {
  return await fetch('/remote/items');
}
