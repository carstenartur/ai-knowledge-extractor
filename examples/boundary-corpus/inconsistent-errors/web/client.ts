import axios from 'axios';
export async function guarded() {
  try {
    return await axios.get('/api/items');
  } catch (error) { return []; }
}
export async function unguarded() {
  return await axios.get('/api/items');
}
