import axios from 'axios';
export async function loadItem() {
    return await axios.get('/api/items');
}
export async function loadDynamic(url: string) {
    return await fetch(url);
}
