const createdKey = (name) => `allshare:created:${name}`;
const accessKey = (name) => `allshare:access:${name}`;

/** Share names cannot contain dots, so "vivi.pdf" belongs to the share "vivi". */
export function nameFromSlug(slug) {
  return slug.split('.')[0].toLowerCase();
}

export function saveCreated(created) {
  sessionStorage.setItem(createdKey(created.name), JSON.stringify(created));
}

export function loadCreated(name) {
  try {
    return JSON.parse(sessionStorage.getItem(createdKey(name)) ?? 'null');
  } catch {
    return null;
  }
}

export function clearCreated(name) {
  sessionStorage.removeItem(createdKey(name));
}

export function saveAccess(name, accessToken) {
  sessionStorage.setItem(accessKey(name), accessToken);
}

export function loadAccess(name) {
  return sessionStorage.getItem(accessKey(name));
}

export function clearAccess(name) {
  sessionStorage.removeItem(accessKey(name));
}
