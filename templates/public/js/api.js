// Every call to the backend goes through api(): JSON in and out, the session cookie, and the CSRF header.
// The CSP allows no inline script, so all JS lives in files like this one.
(function () {
  let csrfReady = null;

  function readCookie(name) {
    const hit = document.cookie.split('; ').find((c) => c.startsWith(name + '='));
    return hit ? decodeURIComponent(hit.slice(name.length + 1)) : null;
  }

  async function ensureCsrf() {
    if (readCookie('XSRF-TOKEN')) return;
    csrfReady = csrfReady || fetch('/api/auth/csrf', { credentials: 'same-origin' });
    await csrfReady;
  }

  async function api(path, { method = 'GET', body } = {}) {
    const headers = { Accept: 'application/json' };
    if (method !== 'GET') {
      await ensureCsrf();
      headers['X-XSRF-TOKEN'] = readCookie('XSRF-TOKEN');
    }
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    const res = await fetch('/api' + path, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const data = res.status === 204 ? null : await res.json().catch(() => null);
    if (!res.ok) throw Object.assign(new Error((data && data.error) || res.statusText), { status: res.status, data });
    return data;
  }

  window.api = api;
})();
