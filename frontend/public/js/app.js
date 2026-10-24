/* ── API ─── */
async function api(method, url, body) {
  const token = localStorage.getItem('qd_token');
  // API_BASE is '' (same origin / Netlify proxy) unless js/config.js points it at the Java backend.
  const res = await fetch((window.API_BASE || '') + url, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined
  });
  let data;
  try { data = await res.json(); }
  catch { throw new Error('Server is not responding. Please try again in a moment.'); }
  if (!res.ok) throw new Error(data.message || 'Request failed');
  return data;
}

/* ── AUTH ─── */
function getUser()  { try { return JSON.parse(localStorage.getItem('qd_user')); } catch { return null; } }
function getToken() { return localStorage.getItem('qd_token'); }
function setAuth(token, user) { localStorage.setItem('qd_token', token); localStorage.setItem('qd_user', JSON.stringify(user)); }
function clearAuth() { localStorage.removeItem('qd_token'); localStorage.removeItem('qd_user'); }

const ROLE_HOME = { admin: '/admin', agent: '/agent', customer: '/dashboard' };

function requireRole(allowed) {
  const user = getUser();
  if (!user) {
    const dest = location.pathname && location.pathname !== '/' ? location.pathname : '';
    window.location.href = '/login' + (dest ? '?r=' + encodeURIComponent(dest) : '');
    return null;
  }
  if (allowed && !allowed.includes(user.role)) {
    window.location.href = ROLE_HOME[user.role] || '/';
    return null;
  }
  return user;
}

function redirectIfLoggedIn() {
  const user = getUser();
  if (user) window.location.href = ROLE_HOME[user.role] || '/';
}

function doLogout() {
  clearAuth(); localStorage.removeItem('qd_cart');
  window.location.href = '/logout';
}

/* ── CART ─── */
function getCart()  { try { return JSON.parse(localStorage.getItem('qd_cart')) || []; } catch { return []; } }
function saveCart(c){ localStorage.setItem('qd_cart', JSON.stringify(c)); updateCartBadge(); }
function cartTotal(){ return getCart().reduce((s, i) => s + i.price * i.quantity, 0); }
function cartCount(){ return getCart().reduce((s, i) => s + i.quantity, 0); }
function addToCart(product) {
  if (!product || !product._id) return;
  const cart = getCart(), idx = cart.findIndex(i => i._id === product._id);
  if (idx >= 0) cart[idx].quantity++; else cart.push({ ...product, quantity: 1 });
  saveCart(cart);
}
function updateCartBadge() {
  const count = cartCount();
  document.querySelectorAll('.cart-badge').forEach(b => {
    b.textContent = count; b.style.display = count > 0 ? 'flex' : 'none';
  });
}

/* ── TOAST ─── */
function toast(msg, type = 'success') {
  document.querySelectorAll('.toast').forEach(t => t.remove());
  const icons = { success:'✅', error:'❌', warning:'⚠️', info:'ℹ️' };
  const t = document.createElement('div');
  t.className = `toast toast-${type}`;
  t.innerHTML = `<span>${icons[type]||'📢'}</span><span>${msg}</span>`;
  document.body.appendChild(t);
  setTimeout(() => t.classList.add('show'), 10);
  setTimeout(() => { t.classList.remove('show'); setTimeout(() => t.remove(), 300); }, 3500);
}

/* ── SIDEBAR ─── */
function buildSidebar(role) {
  const user = getUser();
  const menus = {
    customer: [
      { section: 'Main' },
      { icon: '🏠', label: 'Home',            href: '/' },
      { icon: '🛍️', label: 'Products',        href: '/products' },
      { icon: '🛒', label: 'My Cart',         href: '/cart', badge: 'cart' },
      { section: 'My Orders' },
      { icon: '📦', label: 'My Orders',       href: '/orders' },
      { icon: '🔔', label: 'Notifications',   href: '/dashboard?tab=notifications' },
      { section: 'AI & Insights' },
      { icon: '🤖', label: 'AI Analytics',    href: '/analytics' },
      { section: 'Account' },
      { icon: '👤', label: 'My Profile',      href: '/dashboard?tab=profile' },
      { icon: '🚪', label: 'Logout',          action: 'logout' }
    ],
    admin: [
      { section: 'Dashboard' },
      { icon: '📊', label: 'Overview',        href: '/admin' },
      { section: 'Manage' },
      { icon: '📦', label: 'All Orders',      href: '/admin?tab=orders' },
      { icon: '💳', label: 'Payments',        href: '/admin?tab=payments' },
      { icon: '⭐', label: 'Feedback',        href: '/admin?tab=feedback' },
      { icon: '🌧️', label: 'Agent Alerts',    href: '/admin?tab=alerts' },
      { section: 'AI & System' },
      { icon: '🤖', label: 'System Arch.',    href: '/admin?tab=system' },
      { icon: '📡', label: 'Predictive Data', href: '/admin?tab=predictive' },
      { section: 'Users' },
      { icon: '👥', label: 'Customers',       href: '/admin?tab=customers' },
      { icon: '🚴', label: 'Agents',          href: '/admin?tab=agents' },
      { section: 'Products' },
      { icon: '🛍️', label: 'Products',        href: '/admin?tab=products' },
      { section: 'Account' },
      { icon: '🚪', label: 'Logout',          action: 'logout' }
    ],
    agent: [
      { section: 'Dashboard' },
      { icon: '📊', label: 'Overview',        href: '/agent' },
      { section: 'Deliveries' },
      { icon: '📦', label: 'My Orders',       href: '/agent?tab=orders' },
      { icon: '📢', label: 'Send Alerts',     href: '/agent?tab=alerts' },
      { icon: '🔄', label: 'Reschedule',      href: '/agent?tab=reschedule' },
      { section: 'Account' },
      { icon: '👤', label: 'My Profile',      href: '/agent?tab=profile' },
      { icon: '🚪', label: 'Logout',          action: 'logout' }
    ]
  };

  const links = menus[role] || menus.customer;
  const cur   = location.pathname + location.search;

  let html = `<div class="sidebar-logo">
    <div class="logo-icon">🚀</div>
    <div>
      <div class="logo-text">QuickDeliver</div>
      <div class="logo-sub">${(role||'USER').toUpperCase()} PANEL</div>
    </div>
  </div>`;

  let open = false;
  links.forEach(item => {
    if (item.section) {
      if (open) { html += '</ul>'; open = false; }
      html += `<div class="sidebar-section">${item.section}</div><ul class="sidebar-nav">`;
      open = true;
    } else if (item.action === 'logout') {
      html += `<li><button class="sidebar-logout" onclick="doLogout()"><span class="nav-icon">🚪</span>Logout</button></li>`;
    } else {
      const base     = item.href.split('?')[0];
      const isActive = cur === item.href || (base !== '/' && cur === base && !item.href.includes('?')) || cur === item.href;
      const badgeHtml = item.badge === 'cart' ? `<span class="cart-badge nav-badge" style="display:none">0</span>` : '';
      html += `<li><a href="${item.href}" class="${isActive?'active':''}"><span class="nav-icon">${item.icon}</span>${item.label}${badgeHtml}</a></li>`;
    }
  });
  if (open) html += '</ul>';

  html += `<div class="sidebar-bottom">
    <div class="sidebar-user">
      <div class="user-av">${(user?.name?.[0]||'?').toUpperCase()}</div>
      <div>
        <div class="user-name">${user?.name||'User'}</div>
        <div class="user-role">${role||'user'}</div>
      </div>
    </div>
  </div>`;

  const sb = document.getElementById('sidebar');
  if (sb) sb.innerHTML = html;
  updateCartBadge();
}

function toggleSidebar() {
  document.getElementById('sidebar')?.classList.toggle('open');
  document.getElementById('sidebarOverlay')?.classList.toggle('show');
}

function initShell(role, pageTitle) {
  const user = getUser();
  if (!user) return;
  const tb = document.getElementById('topbar');
  if (tb) {
    tb.innerHTML = `
      <button class="hamburger topbar-icon-btn" onclick="toggleSidebar()">☰</button>
      <div class="topbar-title">${pageTitle||'QuickDeliver'}</div>
      <div class="topbar-actions">
        ${role==='customer'?`<a href="/cart" class="topbar-icon-btn" style="position:relative">🛒<span class="cart-badge" style="position:absolute;top:4px;right:4px;background:#ef4444;color:#fff;font-size:9px;font-weight:700;padding:1px 4px;border-radius:8px;display:none">0</span></a>`:''}
        <div class="topbar-icon-btn" title="${user.name}" style="font-weight:800;font-size:14px;cursor:default">${(user.name?.[0]||'?').toUpperCase()}</div>
      </div>`;
  }
  buildSidebar(role);
}

window.addEventListener('DOMContentLoaded', updateCartBadge);
