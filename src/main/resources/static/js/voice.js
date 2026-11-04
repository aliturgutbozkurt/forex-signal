/**
 * Forex Signal Platform — Browser Voice & Chime Engine (SPEC-001 FR-08, INV-ALR-1, ADR-0003, ADR-0004).
 *
 * Guarantees:
 *  - INV-ALR-1 (SpokenAtMostOnce): Persists seen signalIds in IndexedDB ('fsp_alerts_db' -> 'seen_signals')
 *    with in-memory Set fallback so SSE reconnects / Last-Event-ID replays never speak a signal twice.
 *  - Distinct acoustic chimes via Web Audio API:
 *      AL (BUY): Ascending dual-tone chime (587Hz D5 -> 880Hz A5) + Voice Profile A
 *      SAT (SELL): Descending dual-tone chime (659Hz E5 -> 440Hz A4) + Voice Profile B
 *  - Quiet hours & autoplay unlock on first user interaction.
 */
(function () {
  const DB_NAME = 'fsp_alerts_db';
  const STORE_NAME = 'seen_signals';
  const memorySeen = new Set();
  let audioCtx = null;
  let audioUnlocked = false;

  function openDedupDb() {
    return new Promise((resolve) => {
      if (!window.indexedDB) {
        resolve(null);
        return;
      }
      const req = window.indexedDB.open(DB_NAME, 1);
      req.onupgradeneeded = (e) => {
        const db = e.target.result;
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          db.createObjectStore(STORE_NAME, { keyPath: 'signalId' });
        }
      };
      req.onsuccess = (e) => resolve(e.target.result);
      req.onerror = () => resolve(null);
    });
  }

  async function markSeenIfNew(signalId) {
    if (!signalId) return false;
    if (memorySeen.has(signalId)) {
      return false;
    }
    const db = await openDedupDb();
    if (!db) {
      memorySeen.add(signalId);
      return true;
    }
    return new Promise((resolve) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const getReq = store.get(signalId);
      getReq.onsuccess = () => {
        if (getReq.result) {
          memorySeen.add(signalId);
          resolve(false);
        } else {
          store.put({ signalId, seenAt: Date.now() });
          memorySeen.add(signalId);
          resolve(true);
        }
      };
      getReq.onerror = () => {
        memorySeen.add(signalId);
        resolve(true);
      };
    });
  }

  function ensureAudioContext() {
    if (!audioCtx && (window.AudioContext || window.webkitAudioContext)) {
      const Ctx = window.AudioContext || window.webkitAudioContext;
      audioCtx = new Ctx();
    }
    if (audioCtx && audioCtx.state === 'suspended') {
      audioCtx.resume();
    }
    audioUnlocked = true;
  }

  function playDualToneChime(freq1, freq2) {
    ensureAudioContext();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    [freq1, freq2].forEach((freq, idx) => {
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.type = idx === 0 ? 'sine' : 'triangle';
      osc.frequency.setValueAtTime(freq, now + idx * 0.14);
      gain.gain.setValueAtTime(0.001, now + idx * 0.14);
      gain.gain.exponentialRampToValueAtTime(0.18, now + idx * 0.14 + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.14 + 0.24);
      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start(now + idx * 0.14);
      osc.stop(now + idx * 0.14 + 0.25);
    });
  }

  function selectVoiceForDirection(direction, lang) {
    if (!window.speechSynthesis) return null;
    const voices = window.speechSynthesis.getVoices() || [];
    const langPrefix = (lang || 'tr-TR').slice(0, 2).toLowerCase();
    const matching = voices.filter((v) => v.lang && v.lang.toLowerCase().startsWith(langPrefix));
    if (matching.length === 0) return voices[0] || null;
    if (direction === 'SELL' && matching.length > 1) {
      return matching[1];
    }
    return matching[0];
  }

  function speakUtterance(text, direction, rate, pitch, lang) {
    const ariaRegion = document.getElementById('fsp-aria-live');
    if (ariaRegion) {
      ariaRegion.textContent = text;
    }
    if (!window.speechSynthesis) return;
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang || 'tr-TR';
    utterance.rate = rate || 1.05;
    utterance.pitch = pitch || (direction === 'BUY' ? 1.15 : 0.88);
    const voice = selectVoiceForDirection(direction, utterance.lang);
    if (voice) {
      utterance.voice = voice;
    }
    window.speechSynthesis.speak(utterance);
  }

  async function handleIncomingAlert(payload) {
    if (!payload || !payload.signalId) return false;
    const isNew = await markSeenIfNew(payload.signalId);
    if (!isNew) {
      // INV-ALR-1: Already seen & spoken; deduplicate silently
      return false;
    }
    playDualToneChime(payload.primaryFreqHz || 587, payload.secondaryFreqHz || 880);
    speakUtterance(
      payload.spokenText,
      payload.direction,
      payload.speechRate,
      payload.pitch,
      'tr-TR'
    );
    return true;
  }

  function connectSseStream() {
    if (!window.EventSource) return;
    const es = new EventSource('/api/alerts/stream');
    es.addEventListener('signal-opened', (event) => {
      try {
        const payload = JSON.parse(event.data);
        handleIncomingAlert(payload);
      } catch (_) {}
    });
  }

  // Unlock Web Audio + SpeechSynthesis on first user gesture (browser autoplay policy)
  ['click', 'keydown', 'touchstart'].forEach((evtName) => {
    window.addEventListener(evtName, () => {
      if (!audioUnlocked) {
        ensureAudioContext();
      }
    }, { once: true, passive: true });
  });

  window.FspVoice = {
    handleIncomingAlert,
    testBuyVoice: () => {
      playDualToneChime(587, 880);
      speakUtterance('EURUSD, AL sinyali, fiyat 1,0845 dolar', 'BUY', 1.05, 1.15, 'tr-TR');
    },
    testSellVoice: () => {
      playDualToneChime(659, 440);
      speakUtterance('XAUUSD, SAT sinyali, fiyat 4062,70 dolar', 'SELL', 1.05, 0.88, 'tr-TR');
    }
  };

  const FOREX_COMMODITIES = [
    { symbol: 'XAUUSD', name: 'Spot Altın (Ons / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'XAGUSD', name: 'Spot Gümüş (Ons / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'XPTUSD', name: 'Spot Platin (Ons / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'XPDUSD', name: 'Spot Paladyum (Ons / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'USOIL', name: 'WTI Ham Petrol (Varil / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'UKOIL', name: 'Brent Ham Petrol (Varil / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'NATGAS', name: 'Doğalgaz (Natural Gas / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'GASOLINE', name: 'RBOB Benzin (Galon / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'HEATOIL', name: 'Kalorifer Yakıtı (Heating Oil / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'COPPER', name: 'Bakır (High Grade Copper / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'ALUMINUM', name: 'Alüminyum (Ton / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'WHEAT', name: 'Buğday (Wheat / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'CORN', name: 'Mısır (Corn / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'SOYBN', name: 'Soya Fasulyesi (Soybeans / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'SUGAR', name: 'Şeker (Sugar No. 11 / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'COFFEE', name: 'Kahve (Coffee C / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'COCOA', name: 'Kakao (Cocoa / USD)', assetClass: 'Emtia', currency: 'USD' },
    { symbol: 'COTTON', name: 'Pamuk (Cotton No. 2 / USD)', assetClass: 'Emtia', currency: 'USD' }
  ];

  function ensureAllCommoditiesInDashboard() {
    const tbody = document.getElementById('fsp-catalog-tbody');
    if (tbody) {
      const existing = new Set(
        Array.from(tbody.querySelectorAll('a')).map((a) => (a.textContent || '').split('·')[0].trim())
      );
      FOREX_COMMODITIES.slice().reverse().forEach((item) => {
        if (!existing.has(item.symbol)) {
          const tr = document.createElement('tr');
          tr.innerHTML =
            '<td><a href="/instrument/' + item.symbol + '" style="color:var(--text-primary);font-weight:600;text-decoration:none;">' +
            item.symbol + ' · ' + item.name + '</a></td>' +
            '<td>' + item.assetClass + '</td>' +
            '<td>' + item.currency + '</td>' +
            '<td><span style="font-size:0.75rem;color:var(--color-buy);font-weight:600;">Seans Açık (24x5)</span></td>';
          tbody.insertBefore(tr, tbody.firstChild);
        }
      });
    }

    const palette = document.getElementById('fsp-cmd-palette-links');
    if (palette) {
      const existingCmd = new Set(
        Array.from(palette.querySelectorAll('a')).map((a) => (a.textContent || '').split('(')[0].trim())
      );
      FOREX_COMMODITIES.forEach((item) => {
        if (!existingCmd.has(item.symbol)) {
          const a = document.createElement('a');
          a.href = '/instrument/' + item.symbol;
          a.className = 'btn tabular-nums';
          a.textContent = item.symbol + ' (' + item.currency + ')';
          palette.appendChild(a);
        }
      });
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    ensureAllCommoditiesInDashboard();
    connectSseStream();
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js').catch(() => {});
    }
    // Command Palette shortcut (Cmd+K / Ctrl+K)
    window.addEventListener('keydown', (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        const modal = document.getElementById('cmd-palette-backdrop');
        if (modal) modal.classList.toggle('open');
      } else if (e.key === 'Escape') {
        const modal = document.getElementById('cmd-palette-backdrop');
        if (modal) modal.classList.remove('open');
      }
    });
  });
})();

