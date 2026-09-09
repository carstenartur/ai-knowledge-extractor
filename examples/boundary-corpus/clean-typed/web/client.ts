interface View { name: string; }
export async function load(): Promise<View> {
  const response = await fetch('/api/view');
  return response.json();
}
