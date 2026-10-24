/*
 * Product image resolver.
 *
 * Any <img data-pname="Product name"> on the page is handled automatically:
 *   1. a real image URL stored for the product is used as-is;
 *   2. if it is missing, is one of the bundled placeholders, or fails to load, a photo matching the
 *      product name is looked up on Wikipedia (free, no API key) and cached in the browser;
 *   3. if no photo is found, the bundled illustration (or /images/products/default.svg) is shown.
 */
(function () {
  'use strict';

  var CACHE_KEY = 'pimg_v1';
  var BUNDLED = '/images/products/';
  var DEFAULT_IMG = BUNDLED + 'default.svg';
  var cache = loadCache();
  var pending = {};

  function loadCache() {
    try { return JSON.parse(localStorage.getItem(CACHE_KEY) || '{}') || {}; } catch (e) { return {}; }
  }
  function saveCache() {
    try { localStorage.setItem(CACHE_KEY, JSON.stringify(cache)); } catch (e) { /* storage full / disabled */ }
  }

  /** "Fresh Organic Milk (1L)" -> "Milk" : the words that matter for a search. */
  function searchTerm(name) {
    return String(name)
      .replace(/\(.*?\)/g, ' ')
      .replace(/\b(fresh|organic|farm|pack|packet|bunch|pcs|pc)\b/ig, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function wikipediaPhoto(term) {
    var url = 'https://en.wikipedia.org/w/api.php?action=query&format=json&origin=*' +
      '&generator=search&gsrlimit=6&gsrsearch=' + encodeURIComponent(term) +
      '&prop=pageimages&piprop=thumbnail&pithumbsize=500';
    return fetch(url)
      .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
      .then(function (d) {
        var pages = Object.keys((d.query && d.query.pages) || {}).map(function (k) { return d.query.pages[k]; });
        pages.sort(function (a, b) { return a.index - b.index; });       // best search match first
        for (var i = 0; i < pages.length; i++) {
          var t = pages[i].thumbnail;
          if (t && t.source && !/\.svg/i.test(t.source)) return t.source;   // skip logos / flags / maps
        }
        return '';
      });
  }

  /** Resolves to an image URL, or '' when nothing was found. Results are cached per product name. */
  function lookup(name) {
    if (Object.prototype.hasOwnProperty.call(cache, name)) return Promise.resolve(cache[name]);
    if (pending[name]) return pending[name];
    pending[name] = wikipediaPhoto(searchTerm(name)).then(
      function (url) { cache[name] = url; saveCache(); delete pending[name]; return url; },
      function () { delete pending[name]; return ''; }          // offline: don't cache, try again next time
    );
    return pending[name];
  }

  function show(img, url, fallback) {
    img.onerror = function () { img.onerror = null; img.src = fallback; };
    img.src = url || fallback;
  }

  function process(img) {
    if (!img || img.nodeType !== 1 || img.tagName !== 'IMG' || img.getAttribute('data-pimg')) return;
    var name = img.getAttribute('data-pname');
    if (!name) return;
    img.setAttribute('data-pimg', '1');

    var src = img.getAttribute('src') || '';
    var bundled = src.indexOf(BUNDLED) === 0;
    var fallback = bundled ? src : DEFAULT_IMG;

    if (!src || bundled) {
      // nothing real stored: show the placeholder right away, swap in the photo when found
      if (!src) img.src = fallback;
      img.onerror = function () { img.onerror = null; img.src = DEFAULT_IMG; };
      lookup(name).then(function (url) { if (url) show(img, url, fallback); });
      return;
    }

    // a real URL is stored: keep it, but look for a replacement if it cannot be loaded
    var recover = function () {
      img.onerror = null;
      lookup(name).then(function (url) { show(img, url, fallback); });
    };
    img.onerror = recover;
    if (img.complete && img.naturalWidth === 0) recover();       // it already failed before we got here
  }

  function scan(root) {
    if (!root) return;
    if (root.tagName === 'IMG') { process(root); return; }
    if (root.querySelectorAll) {
      var imgs = root.querySelectorAll('img[data-pname]');
      for (var i = 0; i < imgs.length; i++) process(imgs[i]);
    }
  }

  function start() {
    scan(document);
    new MutationObserver(function (mutations) {
      mutations.forEach(function (m) {
        for (var i = 0; i < m.addedNodes.length; i++) scan(m.addedNodes[i]);
      });
    }).observe(document.body, { childList: true, subtree: true });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();

  window.ProductImages = { lookup: lookup, searchTerm: searchTerm };   // handy for debugging in the console
})();
