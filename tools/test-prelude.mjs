#!/usr/bin/env node
/**
 * lx-prelude.js 契约自测（T2 · BREAKDOWN §3.2）
 * ================================================
 * 用 Node 的 vm 沙箱模拟 JsEngineRuntime 环境（globalThis + 端口绑定 + 宿主回灌），
 * 验证胶水的协议行为：握手 / request 派发 / reject 透传 / 双签名 callback / 计时器。
 *
 * 运行：node tools/test-prelude.mjs [prelude路径] [标本脚本路径(可选)]
 * 退出码：0 = 全部断言通过；1 = 有失败（逐条打印）。
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import vm from 'node:vm';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const preludePath = process.argv[2] || join(root, 'core/js/src/main/assets/lx-prelude.js');
const specimenPath = process.argv[3] || '';

const PRELUDE = readFileSync(preludePath, 'utf8');

// ---------- 测试基建：双端假端口 ----------
class FakeChannel {
  constructor() {
    this.jsToHost = [];   // 脚本 → 宿主 消息队列
    this.hostListeners = [];
    this.closed = false;
  }
  makeJsPort() {
    const ch = this;
    return {
      postMessage(s) { ch.jsToHost.push(s); },
      set onmessage(fn) { ch._onHostMsg = fn; },
    };
  }
  hostToJs(s) { this._onHostMsg && this._onHostMsg({ data: s }); }
}

function newSandbox(extraGlobals = {}) {
  const ch = new FakeChannel();
  const sandbox = {
    ...extraGlobals,
    __lxHost: { version: '0.2.0-test', scriptInfo: { name: '测试脚本', version: 'v1' } },
    __lxHostBind: null, // prelude 定义后再绑
    setTimeout: undefined, clearTimeout: undefined, // 胶水应自行覆盖
    console: undefined,
  };
  const ctx = vm.createContext(sandbox);
  vm.runInContext(PRELUDE, ctx);            // 2. prelude（同步）
  sandbox.__lxHostBind(ch.makeJsPort());     // 3. 端口绑定（真实环境为 async，此同步即可）
  return { ctx, sandbox, ch };
}

let pass = 0, fail = 0;
function ok(cond, name) {
  if (cond) { pass++; console.log('  ✓', name); }
  else { fail++; console.error('  ✗', name); }
}
function runUserCode(ctx, code) {
  vm.runInContext(code, ctx); // 4. 用户脚本
}
async function drain(ch) {
  for (let i = 0; i < 50 && ch.jsToHost.length === 0; i++) await new Promise(r => setTimeout(r, 5));
  return JSON.parse(ch.jsToHost.shift());
}
async function drainN(ch, n) {
  const out = [];
  for (let i = 0; i < n; i++) out.push(await drain(ch));
  return out;
}

// ---------- T1: 握手 ----------
console.log('\n[T1] inited 握手');
{
  const { ctx, ch } = newSandbox();
  runUserCode(ctx, `
    const { EVENT_NAMES, on, send, env, version } = globalThis.lx;
    send(EVENT_NAMES.inited, { status: true, sources: { kw: { name:'kw', type:'music', actions:['musicUrl','lyric','pic'], qualitys:['128k','flac'] } } });
  `);
  const m = await drain(ch);
  ok(m.t === 'inited' && m.status === true && m.sources && m.sources.kw.actions.length === 3, 'inited 消息结构（status/sources）');
  ok(m.sources.kw.qualitys[1] === 'flac', 'qualitys 透传');
  // 面向脚本的 env/version/currentScriptInfo
  const v = vm.runInContext('({env: globalThis.lx.env, ver: globalThis.lx.version, info: globalThis.lx.currentScriptInfo.name})', ctx);
  ok(v.env === 'mobile' && v.ver === '0.2.0-test' && v.info === '测试脚本', 'env/version/currentScriptInfo 注入');
}

// ---------- T2: status:false 与重复握手 ----------
console.log('\n[T2] 握手容错');
{
  const { ctx, ch } = newSandbox();
  runUserCode(ctx, `
    globalThis.lx.send(globalThis.lx.EVENT_NAMES.inited, { status: false });
    globalThis.lx.send(globalThis.lx.EVENT_NAMES.inited, { status: true }); // 第二次应被吞
  `);
  const m = await drain(ch);
  ok(m.t === 'inited' && m.status === false, 'status:false 透传（宿主应禁用该源）');
  await new Promise(r => setTimeout(r, 30));
  ok(ch.jsToHost.length === 0, '重复 inited 被吞（握手只发一次）');
}

// ---------- T3: request 派发 → resolve ----------
console.log('\n[T3] request 事件：resolve 路径');
{
  const { ctx, ch } = newSandbox();
  runUserCode(ctx, `
    globalThis.lx.on(globalThis.lx.EVENT_NAMES.request, ({ source, action, info }) => {
      return new Promise(resolve => {
        setTimeout(() => resolve('https://cdn.example/song.flac?token=abc'), 10);
      });
    });
  `);
  // 宿主先收 setTimer（计时期），再发 evt
  ch.hostToJs(JSON.stringify({ t: 'evt', callId: 7, source: 'kw', action: 'musicUrl', info: { type: '320k', musicInfo: { title: '晴天' } } }));
  const timerMsg = await drain(ch);
  ok(timerMsg.t === 'setTimer' && timerMsg.ms === 10, 'setTimeout 走宿主计时器');
  ch.hostToJs(JSON.stringify({ t: 'timerFired', timerId: timerMsg.timerId }));
  const resp = await drain(ch);
  ok(resp.t === 'resp' && resp.callId === 7 && resp.ok === true && resp.data === 'https://cdn.example/song.flac?token=abc', 'Promise resolve 数据回传');
}

// ---------- T4: reject 文案透传 ----------
console.log('\n[T4] request 事件：reject 路径');
{
  const { ctx, ch } = newSandbox();
  runUserCode(ctx, `
    globalThis.lx.on(globalThis.lx.EVENT_NAMES.request, () => Promise.reject(new Error('鉴权失败')));
  `);
  ch.hostToJs(JSON.stringify({ t: 'evt', callId: 9, source: 'wy', action: 'musicUrl', info: {} }));
  const resp = await drain(ch);
  ok(resp.t === 'resp' && resp.callId === 9 && resp.ok === false && resp.message === '鉴权失败', 'Error.message 原样透传');
}

// ---------- T5: lx.request 双签名 callback ----------
console.log('\n[T5] 网络桥：双签名 + JSON 语义');
{
  const { ctx, ch } = newSandbox();
  let captured = null;
  runUserCode(ctx, `
    globalThis.__cbResult = null;
    globalThis.lx.request('https://api.example/song', { method: 'GET', headers: { 'X-Card-Key': '公益版' } }, (err, resp, body) => {
      globalThis.__cbResult = { err, statusCode: resp && resp.statusCode, bodyIsObj: typeof resp?.body === 'object', bodyEq: resp?.body === body, code: resp?.body?.code };
    });
  `);
  const http = await drain(ch);
  ok(http.t === 'http' && http.url === 'https://api.example/song' && http.options.headers['X-Card-Key'] === '公益版', '脚本 HTTP 请求结构（url/options/headers）');
  // 宿主回灌：JSON 语义 body（对象）
  ch.hostToJs(JSON.stringify({ t: 'httpResult', callId: http.callId, resp: { statusCode: 200, headers: { 'content-type': 'application/json' }, body: { code: 0, data: 'ok' } } }));
  await new Promise(r => setTimeout(r, 20));
  const res = vm.runInContext('globalThis.__cbResult', ctx);
  ok(res && res.err === null, 'callback err=null');
  ok(res.statusCode === 200, 'resp.statusCode');
  ok(res.bodyIsObj === true && res.code === 0, 'JSON 语义：resp.body 为对象');
  ok(res.bodyEq === true, '双签名兜底：resp.body === body');
}

// ---------- T6: HTTP 错误与字符串 body ----------
console.log('\n[T6] 网络桥：err 与字符串 body');
{
  const { ctx, ch } = newSandbox();
  runUserCode(ctx, `
    globalThis.__r1 = null; globalThis.__r2 = null;
    globalThis.lx.request('https://dead.example', {}, (err, resp, body) => { globalThis.__r1 = { err, body }; });
    globalThis.lx.request('https://plain.example', {}, (err, resp, body) => { globalThis.__r2 = { err, body, bodyIsStr: typeof body === 'string' }; });
  `);
  const [h1, h2] = await drainN(ch, 2);
  ch.hostToJs(JSON.stringify({ t: 'httpResult', callId: h1.callId, err: 'connect ETIMEDOUT' }));
  ch.hostToJs(JSON.stringify({ t: 'httpResult', callId: h2.callId, resp: { statusCode: 200, headers: {}, body: '<html>plain</html>' } }));
  await new Promise(r => setTimeout(r, 20));
  const r1 = vm.runInContext('globalThis.__r1', ctx);
  const r2 = vm.runInContext('globalThis.__r2', ctx);
  ok(r1.err === 'connect ETIMEDOUT' && r1.body === null, '网络错误走第一参 err');
  ok(r2.err === null && r2.bodyIsStr === true && r2.body === '<html>plain</html>', '非 JSON body 原样字符串');
}

// ---------- T7: console 捕获 + 1024 截断 ----------
console.log('\n[T7] console 捕获');
{
  const { ctx, ch } = newSandbox();
  runUserCode(ctx, `
    console.log('hello'); console.error('boom'); 
    console.log('x'.repeat(2000));
  `);
  const [l1, l2, l3] = await drainN(ch, 3);
  ok(l1.t === 'log' && l1.level === 'log' && l1.message === '["hello"]', 'console.log 捕获');
  ok(l2.level === 'error' && l2.message === '["boom"]', 'console.error 捕获');
  ok(l3.message.length <= 1024 + 2, '1024 截断（含 JSON 括号）');
}

// ---------- T8: 标本脚本真实握手 ----------
if (specimenPath) {
  console.log('\n[T8] 标本脚本握手（' + specimenPath + '）');
  try {
    const specimen = readFileSync(specimenPath, 'utf8');
    const { ctx, ch } = newSandbox();
    runUserCode(ctx, specimen);
    const m = await drain(ch);
    ok(m.t === 'inited' && m.status === true, '标本握手 status:true');
    ok(m.sources && ['kw','kg','tx','wy','mg'].every(k => m.sources[k]), '五大平台 sources 齐全');
    ok(m.sources.kw.actions.includes('musicUrl') && m.sources.kw.actions.includes('lyric'), 'actions 全开（协议 §8-1 生态事实）');
    ok(m.sources.kw.qualitys.includes('hires'), '生态扩展音质 hires 透传');
    // 标本 console 调用（L~700+）：inited 完成日志
    const next = ch.jsToHost.length ? JSON.parse(ch.jsToHost[0]) : null;
    if (next && next.t === 'log') ok(true, '标本 console 日志被捕获');
  } catch (e) {
    ok(false, '标本执行异常：' + e.message);
  }
}

console.log(`\n结果：${pass} 通过，${fail} 失败`);
process.exit(fail === 0 ? 0 : 1);
