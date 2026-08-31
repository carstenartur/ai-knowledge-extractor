export interface UserView {
  displayName: string;
}

export async function loadUser(id: string): Promise<UserView> {
  const response = await fetch(`/api/users/${id}`);
  if (!response.ok) {
    throw new Error(`backend status ${response.status}`);
  }
  const dto = await response.json();
  return { displayName: dto.name };
}
