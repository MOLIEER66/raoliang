/**
 * lx-prelude.js · 绕梁宿主胶水（T2，ADR-0003 §4）
 * =====================================================
 *
 * 职责：在洛雪脚本执行前构造唯一的协议全局对象 `globalThis.lx`，并把脚本与
 * 宿主（绕梁 App）之间的全部通信收敛到一条 MessagePort 信道（JSON 文本）。
 *
 * 装载顺序（JsEngineRuntime 保证，缺一不可）：
 *   1. evaluate: `globalThis.__lxHost = {version, scriptInfo}`   // 宿主身份注入（同步）
 *   2. evaluate: 本文件                                           // lx 骨架 + __lxHostBind（同步）
 *   3. evaluate: `android.getNamedPort('lx-bridge').then(__lxHostBind)`  // 端口绑定（异步）
 *   4. evaluate: 用户脚本                                         // 顶层注册 handler 并 send('inited')
 *
 * 端口就绪前的所有 send 进 pending 队列，绑定后 flush——脚本顶层握手不等端口。
 *
 * 协议容错要点（协议剖析 §6/§8）：
 *   - callback 恒三参 (err, resp, body)，且保证 resp.body === body（双签名兜底）；
 *   - JSON 语义由宿主完成（needle 行为），脚本两头写法（resp.body.code / JSON.parse(body)）都能活；
 *   - reject 的 Error 文案原样透传给宿主（"鉴权失败"/"请求过速"等可读错误）；
 *   - console 捕获 1024 截断；setTimeout/clearTimeout 走宿主计时器。
 */
(function () {
  'use strict';

  if (typeof globalThis.lx !== 'undefined') return; // 防重入

  var PORT_NAME = 'lx-bridge';

  // ---- 信道状态 ----
  var port = null;
  var pendingSends = [];
  var nextCallId = 1;
  var httpCallbacks = Object.create(null); // callId -> function(err, resp, body)
  var eventHandlers = Object.create(null); // 事件名 -> handler
  var timers = Object.create(null);        // timerId -> fn
  var nextTimerId = 1;
  var initedSent = false;

  function post(obj) {
    var s = JSON.stringify(obj);
    if (port) { port.postMessage(s); } else { pendingSends.push(s); }
  }

  // ---- 宿主消息入口（绑端口后由 MessagePort.onmessage 调用） ----
  function onHostMessage(event) {
    var msg;
    try { msg = JSON.parse(event.data); } catch (e) { return; }
    if (!msg || typeof msg !== 'object') return;
    switch (msg.t) {
      case 'evt': onHostEvent(msg); break;
      case 'httpResult': onHttpResult(msg); break;
      case 'timerFired': onTimerFired(msg.timerId); break;
      default: break; // 未知消息忽略（前向兼容）
    }
  }

  // 宿主派发 request 事件：handler 返回 Promise，结果回传
  function onHostEvent(msg) {
    var handler = eventHandlers.request;
    if (typeof handler !== 'function') {
      post({ t: 'resp', callId: msg.callId, ok: false, message: '脚本未注册 request 处理器' });
      return;
    }
    Promise.resolve()
      .then(function () { return handler({ source: msg.source, action: msg.action, info: msg.info }); })
      .then(function (data) { post({ t: 'resp', callId: msg.callId, ok: true, data: data === undefined ? null : data }); })
      .catch(function (err) {
        // Error 文案原样透出（协议 §5.1：宿主把 reject 消息透出到 UI）
        var text = (err && err.message) ? err.message : String(err);
        post({ t: 'resp', callId: msg.callId, ok: false, message: text });
      });
  }

  // 宿主代发 HTTP 回灌：双签名 (err, resp, body)，resp.body === body
  function onHttpResult(msg) {
    var cb = httpCallbacks[msg.callId];
    delete httpCallbacks[msg.callId];
    if (typeof cb !== 'function') return; // 已取消/超时
    if (msg.err) { cb(msg.err, null, null); return; }
    var resp = msg.resp || { statusCode: 0, headers: {}, body: null };
    cb(null, resp, resp.body);
  }

  function onTimerFired(timerId) {
    var fn = timers[timerId];
    delete timers[timerId];
    if (typeof fn === 'function') fn();
  }

  // ---- globalThis.lx ----
  var lx = {
    env: 'mobile',
    version: (typeof globalThis.__lxHost !== 'undefined' && globalThis.__lxHost.version) || '1.0.0',
    currentScriptInfo:
      (typeof globalThis.__lxHost !== 'undefined' && globalThis.__lxHost.scriptInfo) || null,

    EVENT_NAMES: {
      request: 'request',
      inited: 'inited',
      updateAlert: 'updateAlert',
    },

    /** 注册事件处理器（脚本主入口：on(EVENT_NAMES.request, handler)）。 */
    on: function (name, handler) { eventHandlers[name] = handler; },

    /** 脚本主动上报（inited / updateAlert / 生态自定义事件原样转发）。 */
    send: function (name, datas) {
      if (name === lx.EVENT_NAMES.inited) {
        if (initedSent) return; // 握手只发一次（防脚本重复注册）
        initedSent = true;
        var payload = (datas && typeof datas === 'object') ? datas : {};
        payload.status = payload.status !== false; // 缺省视为 true（协议 §4）
        post({ t: 'inited', status: payload.status, sources: payload.sources || null,
               openDevTools: payload.openDevTools });
        return;
      }
      if (name === lx.EVENT_NAMES.updateAlert) {
        var d = (datas && typeof datas === 'object') ? datas : {};
        post({ t: 'updateAlert', name: d.name, version: d.version, message: d.message, updateUrl: d.updateUrl });
        return;
      }
      post({ t: 'rawEvent', name: name, datas: datas === undefined ? null : datas });
    },

    /**
     * 网络请求（宿主代发）。callback 双签名兜底：恒三参 (err, resp, body)。
     * 返回取消句柄对象（P1-4：宿主侧尽力取消）。
     */
    request: function (url, options, callback) {
      var callId = nextCallId++;
      httpCallbacks[callId] = callback || function () {};
      post({ t: 'http', callId: callId, url: url, options: options || {} });
      return {
        cancel: function () {
          delete httpCallbacks[callId];
          post({ t: 'httpCancel', callId: callId });
        },
      };
    },

    /** 生态长尾（P1-1/2，T4 后按需实现；先给可读错误不静默）。 */
    utils: {
      buffer: {
        from: notImplemented('lx.utils.buffer.from'),
        bufToString: notImplemented('lx.utils.buffer.bufToString'),
      },
      crypto: {
        md5: notImplemented('lx.utils.crypto.md5'),
        randomBytes: notImplemented('lx.utils.crypto.randomBytes'),
        aesEncrypt: notImplemented('lx.utils.crypto.aesEncrypt'),
        rsaEncrypt: notImplemented('lx.utils.crypto.rsaEncrypt'),
      },
    },
  };

  function notImplemented(api) {
    return function () { throw new Error(api + ' 暂未实现（绕梁 M2）'); };
  }

  globalThis.lx = lx;

  // ---- 计时器（P0-10：宿主侧 Handler 计时，V8 isolate 内无原生 setTimeout） ----
  globalThis.setTimeout = function (fn, ms) {
    if (typeof fn !== 'function') return 0;
    var id = nextTimerId++;
    timers[id] = fn;
    post({ t: 'setTimer', timerId: id, ms: Math.max(0, Number(ms) | 0) });
    return id;
  };
  globalThis.clearTimeout = function (id) {
    delete timers[id];
    post({ t: 'clearTimer', timerId: id });
  };

  // ---- console 捕获（P0-11：1024 截断；统一走端口信道，不依赖引擎特性门控） ----
  function trunc(v) {
    try {
      var s = typeof v === 'string' ? v : JSON.stringify(v);
      return s === undefined ? String(v) : (s.length > 1024 ? s.slice(0, 1024) : s);
    } catch (e) { return String(v); }
  }
  globalThis.console = {
    log: function () { post({ t: 'log', level: 'log', message: trunc(Array.prototype.slice.call(arguments)) }); },
    warn: function () { post({ t: 'log', level: 'warn', message: trunc(Array.prototype.slice.call(arguments)) }); },
    error: function () { post({ t: 'log', level: 'error', message: trunc(Array.prototype.slice.call(arguments)) }); },
  };

  // ---- 端口绑定入口（JsEngineRuntime 经 getNamedPort 后调用；Node 自测用假端口） ----
  globalThis.__lxHostBind = function (p) {
    if (port) return;
    port = p;
    port.onmessage = onHostMessage;
    var flushed = pendingSends; pendingSends = [];
    for (var i = 0; i < flushed.length; i++) port.postMessage(flushed[i]);
  };
})();
