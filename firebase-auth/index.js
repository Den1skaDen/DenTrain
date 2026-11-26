const { createHash } = require('node:crypto');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore } = require('firebase-admin/firestore');
const { defineSecret } = require('firebase-functions/params');
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const logger = require('firebase-functions/logger');

initializeApp();

const resendApiKey = defineSecret('RESEND_API_KEY');
const template = readFileSync(join(__dirname, 'verify-email.html'), 'utf8');
const verificationPage = 'https://auth.dentrain.ru/verify/';
const cooldownMs = 60_000;

async function reserveSend(email) {
  const emailKey = createHash('sha256').update(email.toLowerCase()).digest('hex');
  const db = getFirestore();
  const ref = db.collection('_emailVerificationLimits').doc(emailKey);
  await db.runTransaction(async (transaction) => {
    const state = await transaction.get(ref);
    const now = Date.now();
    const remainingMs = (state.get('nextSendAtMs') ?? 0) - now;
    if (remainingMs > 0) {
      throw new HttpsError('resource-exhausted', 'Try again later.', {
        retryAfterSeconds: Math.ceil(remainingMs / 1000),
      });
    }
    transaction.set(ref, { nextSendAtMs: now + cooldownMs });
  });
}

function buildVerificationUrl(firebaseLink) {
  const { searchParams } = new URL(firebaseLink);
  const code = searchParams.get('oobCode');
  if (searchParams.get('mode') !== 'verifyEmail' || !code) {
    throw new Error('Unexpected Firebase email action link');
  }
  const url = new URL(verificationPage);
  url.searchParams.set('mode', 'verifyEmail');
  url.searchParams.set('oobCode', code);
  return url.href;
}

exports.sendVerificationEmail = onCall({
  region: 'us-central1',
  secrets: [resendApiKey],
  timeoutSeconds: 30,
  maxInstances: 3,
}, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Sign in first.');

  const auth = getAuth();
  try {
    const user = await auth.getUser(request.auth.uid);
    if (user.disabled || !user.email || user.email !== request.auth.token.email) {
      throw new HttpsError('unauthenticated', 'Sign in again.');
    }
    if (user.emailVerified) return { alreadyVerified: true };

    await reserveSend(user.email);
    const firebaseLink = await auth.generateEmailVerificationLink(user.email);
    const link = buildVerificationUrl(firebaseLink);
    const response = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${resendApiKey.value()}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        from: 'Команда DenTrain <noreply@mail.dentrain.ru>',
        to: [user.email],
        subject: 'Подтвердите почту · DenTrain',
        html: template.replace('%LINK%', link.replaceAll('&', '&amp;')),
        text: `Подтвердите почту, чтобы завершить регистрацию в DenTrain:\n${link}\n\nЕсли вы не регистрировались в DenTrain, просто проигнорируйте это письмо.`,
      }),
      signal: AbortSignal.timeout(10_000),
    });
    if (!response.ok) {
      logger.error('Resend rejected verification email', { status: response.status });
      throw new HttpsError('unavailable', 'Email delivery is temporarily unavailable.');
    }
    return { alreadyVerified: false };
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    if (error.code === 'auth/user-not-found') {
      throw new HttpsError('unauthenticated', 'Sign in again.');
    }
    logger.error('Verification email request failed', { code: error.code });
    throw new HttpsError('unavailable', 'Email delivery is temporarily unavailable.');
  }
});
