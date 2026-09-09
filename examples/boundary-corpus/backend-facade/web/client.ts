export async function workflow() {
  const response = await fetch('/api/workflow-view');
  return response.json();
}
