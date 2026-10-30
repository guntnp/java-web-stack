(function () {
  const $ = (id) => document.getElementById(id);

  // textContent, never innerHTML, for anything that came from the server or a user (prevents XSS).
  function renderProducts(items) {
    const list = $('products');
    list.replaceChildren();
    for (const p of items) {
      const li = document.createElement('li');
      li.textContent = `${p.name} — ${Number(p.price).toFixed(2)} บาท`;
      list.append(li);
    }
    if (!items.length) list.textContent = 'ยังไม่มีสินค้า';
  }

  async function refresh() {
    renderProducts(await window.api('/products'));
    try {
      const me = await window.api('/auth/me');
      $('who').textContent = `${me.email} (${me.role})`;
    } catch {
      $('who').textContent = 'ยังไม่ได้เข้าสู่ระบบ';
    }
  }

  $('login').addEventListener('submit', async (e) => {
    e.preventDefault();
    $('message').textContent = '';
    try {
      await window.api('/auth/login', { method: 'POST', body: { email: $('email').value, password: $('password').value } });
      await refresh();
    } catch (err) {
      $('message').textContent = err.status === 429 ? 'ลองบ่อยเกินไป รอสักครู่' : 'อีเมลหรือรหัสผ่านไม่ถูกต้อง';
    }
  });

  $('logout').addEventListener('click', async () => {
    await window.api('/auth/logout', { method: 'POST' });
    await refresh();
  });

  refresh();
})();
