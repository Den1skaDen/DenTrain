import template from './verify-email.html';
import resetTemplate from './reset-password.html';

const project = 'dentrain-9af07';
const identity = 'https://identitytoolkit.googleapis.com/v1';
const tokenEndpoint = 'https://oauth2.googleapis.com/token';
let cachedToken;

function base64Url(data) {
  return btoa(String.fromCharCode(...data)).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '');
}

async function getGoogleToken(env) {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken?.expiresAt > now + 60) return cachedToken.value;
  const { client_email, private_key } = JSON.parse(env.FIREBASE_EMAIL_SERVICE_ACCOUNT);
  const pem = private_key.replace(/-----[^-]+-----|\s/g, '');
  const key = await crypto.subtle.importKey(
    'pkcs8', Uint8Array.from(atob(pem), char => char.charCodeAt(0)),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign'],
  );
  const encoder = new TextEncoder();
  const encode = value => base64Url(encoder.encode(JSON.stringify(value)));
  const unsigned = [
    encode({ alg: 'RS256', typ: 'JWT' }),
    encode({
      iss: client_email, scope: 'https://www.googleapis.com/auth/identitytoolkit',
      aud: tokenEndpoint, iat: now, exp: now + 3600,
    }),
  ].join('.');
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, encoder.encode(unsigned));
  const response = await fetch(tokenEndpoint, {
    method: 'POST',
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: unsigned + '.' + base64Url(new Uint8Array(signature)),
    }),
    signal: AbortSignal.timeout(8000),
  });
  if (!response.ok) throw new Error('Google OAuth request failed');
  const { access_token, expires_in } = await response.json();
  if (!access_token) throw new Error('Google OAuth token missing');
  cachedToken = { value: access_token, expiresAt: now + expires_in };
  return access_token;
}

function post(url, data, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  return fetch(url, {
    method: 'POST', headers, body: JSON.stringify(data), signal: AbortSignal.timeout(8000),
  });
}

function failure(status, code) {
  return Response.json({ error: { status: code, message: code } }, { status });
}

async function sendResetEmail(request, env, ip) {
  if (!(await env.RESET_REQUEST_LIMIT.limit({ key: ip })).success) {
    return failure(429, 'RESOURCE_EXHAUSTED');
  }
  const body = await request.json().catch(() => null);
  const email = body?.data?.email;
  if (typeof email !== 'string' || email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return failure(400, 'INVALID_ARGUMENT');
  }
  const accepted = () => Response.json({ result: { accepted: true } });
  if (!(await env.RESET_RECIPIENT_LIMIT.limit({ key: email.toLowerCase() })).success) {
    return accepted();
  }
  const action = await post(identity + '/projects/' + project + '/accounts:sendOobCode', {
    requestType: 'PASSWORD_RESET', email, returnOobLink: true,
    continueUrl: 'https://dentrain-9af07.firebaseapp.com/',
    androidPackageName: 'com.dentrain', canHandleCodeInApp: true, userIp: ip,
  }, await getGoogleToken(env));
  const payload = await action.json();
  if (!action.ok) {
    if (['EMAIL_NOT_FOUND', 'USER_NOT_FOUND', 'USER_DISABLED'].includes(payload.error?.message)) {
      return accepted();
    }
    throw new Error('Firebase reset link generation failed');
  }
  if (!payload.oobLink && payload.email) return accepted();
  const link = new URL(payload.oobLink);
  const actionLink = new URL(link.searchParams.get('link') ?? link.href);
  if (link.origin !== 'https://dentrain-9af07.firebaseapp.com' ||
      link.pathname !== '/__/auth/links' ||
      actionLink.searchParams.get('mode') !== 'resetPassword' ||
      !actionLink.searchParams.get('oobCode')) {
    throw new Error('Invalid password reset link');
  }
  const delivery = await post('https://api.resend.com/emails', {
    from: 'Команда DenTrain <noreply@mail.dentrain.ru>',
    to: [email],
    subject: 'Сброс пароля · DenTrain',
    html: resetTemplate.replace('%LINK%', link.href.replaceAll('&', '&amp;')),
    text: `Создайте новый пароль для входа в DenTrain:\n${link.href}\n\nЕсли вы не запрашивали сброс пароля, просто проигнорируйте это письмо.`,
  }, env.RESEND_API_KEY);
  if (!delivery.ok) throw new Error('Resend reset delivery failed');
  return accepted();
}

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (request.method !== 'POST' || !['/sendVerificationEmail', '/sendPasswordResetEmail'].includes(path)) {
      return failure(404, 'NOT_FOUND');
    }
    try {
      const ip = request.headers.get('CF-Connecting-IP') ?? 'unknown';
      if (path === '/sendPasswordResetEmail') return await sendResetEmail(request, env, ip);
      const idToken = request.headers.get('Authorization')?.match(/^Bearer ([\w.-]+)$/i)?.[1];
      if (!idToken || idToken.length > 8192) return failure(401, 'UNAUTHENTICATED');
      if (!(await env.REQUEST_LIMIT.limit({ key: ip })).success) {
        return failure(429, 'RESOURCE_EXHAUSTED');
      }
      const lookup = await post(identity + '/accounts:lookup?key=' + encodeURIComponent(env.FIREBASE_WEB_API_KEY), { idToken });
      if (lookup.status === 400 || lookup.status === 401) return failure(401, 'UNAUTHENTICATED');
      if (!lookup.ok) throw new Error('Firebase account lookup failed');
      const user = (await lookup.json()).users?.[0];
      if (!user?.email || user.disabled) return failure(401, 'UNAUTHENTICATED');
      if (user.emailVerified) return Response.json({ result: { alreadyVerified: true } });
      if (!(await env.RECIPIENT_LIMIT.limit({ key: user.email.toLowerCase() })).success) {
        return failure(429, 'RESOURCE_EXHAUSTED');
      }
      const action = await post(identity + '/projects/' + project + '/accounts:sendOobCode', {
        requestType: 'VERIFY_EMAIL', email: user.email, returnOobLink: true,
      }, await getGoogleToken(env));
      if (!action.ok) throw new Error('Firebase link generation failed');
      const firebaseLink = new URL((await action.json()).oobLink);
      const code = firebaseLink.searchParams.get('oobCode');
      if (!code || firebaseLink.searchParams.get('mode') !== 'verifyEmail') {
        throw new Error('Invalid verification link');
      }
      const link = new URL('https://auth.dentrain.ru/verify/');
      link.searchParams.set('mode', 'verifyEmail');
      link.searchParams.set('oobCode', code);
      const delivery = await post('https://api.resend.com/emails', {
        from: 'Команда DenTrain <noreply@mail.dentrain.ru>',
        to: [user.email],
        subject: 'Подтвердите почту · DenTrain',
        html: template.replace('%LINK%', link.href.replaceAll('&', '&amp;')),
        text: `Подтвердите почту, чтобы завершить регистрацию в DenTrain:\n${link.href}\n\nЕсли вы не регистрировались в DenTrain, просто проигнорируйте это письмо.`,
      }, env.RESEND_API_KEY);
      if (!delivery.ok) throw new Error('Resend delivery failed');
      return Response.json({ result: { alreadyVerified: false } });
    } catch {
      return failure(503, 'UNAVAILABLE');
    }
  },
};
