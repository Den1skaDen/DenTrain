const title = document.querySelector('#title');
const detail = document.querySelector('#detail');
const button = document.querySelector('#confirm');
const params = new URLSearchParams(location.search);
const mode = params.get('mode');
const oobCode = params.get('oobCode');
const firebaseHandler = 'https://dentrain-9af07.firebaseapp.com/__/auth/action';

function show(heading, message) {
  title.textContent = heading;
  detail.textContent = message;
}

async function requestFirebase(operation) {
    const config = await fetch('/__/firebase/init.json', { signal: AbortSignal.timeout(15000) });
    if (!config.ok) throw new Error('Firebase Hosting configuration unavailable');
    const { apiKey } = await config.json();
    if (!apiKey) throw new Error('Firebase API key unavailable');

    const endpoint = new URL('https://identitytoolkit.googleapis.com/v1/accounts:' + operation);
    endpoint.searchParams.set('key', apiKey);
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ oobCode }),
      signal: AbortSignal.timeout(15000)
    });
    return { ok: response.ok, result: await response.json() };
}

async function confirmEmail() {
  button.disabled = true;
  show('Подтверждаем почту', 'Это займёт несколько секунд.');

  try {
    const { ok, result } = await requestFirebase('update');
    if (ok && result.emailVerified === true) {
      history.replaceState(null, '', location.pathname);
      button.hidden = true;
      show('Почта подтверждена', 'Вернитесь в DenTrain, чтобы продолжить.');
    } else if (['INVALID_OOB_CODE', 'EXPIRED_OOB_CODE', 'EMAIL_NOT_FOUND', 'USER_DISABLED'].includes(result.error?.message)) {
      button.hidden = true;
      show('Ссылка больше не действует', 'Если DenTrain всё ещё просит подтвердить почту, запросите новое письмо в приложении.');
    } else {
      throw new Error('Firebase could not verify the email');
    }
  } catch {
    show('Не удалось подтвердить почту', 'Проверьте подключение к интернету и попробуйте ещё раз.');
    button.textContent = 'Повторить попытку';
  } finally {
    button.disabled = false;
  }
}

async function showEmail() {
  show('Подтвердите почту', 'Проверяем ссылку…');
  try {
    const { ok, result } = await requestFirebase('resetPassword');
    if (ok && result.requestType === 'VERIFY_EMAIL' && result.email) {
      show('Подтвердите почту', result.email);
      detail.style.overflowWrap = 'anywhere';
      button.hidden = false;
      button.addEventListener('click', confirmEmail);
    } else if (ok || ['INVALID_OOB_CODE', 'EXPIRED_OOB_CODE', 'EMAIL_NOT_FOUND', 'USER_DISABLED'].includes(result.error?.message)) {
      show('Ссылка больше не действует', 'Запросите новое письмо в приложении DenTrain.');
    } else {
      throw new Error('Firebase could not check the link');
    }
  } catch {
    show('Не удалось проверить ссылку', 'Проверьте подключение к интернету и обновите страницу.');
  }
}

if (mode && mode !== 'verifyEmail' && oobCode) {
  location.replace(firebaseHandler + location.search);
} else if (mode === 'verifyEmail' && oobCode) {
  // Wait for a user action so email scanners do not consume the one-time code.
  void showEmail();
} else {
  show('Подтверждение почты', 'Откройте ссылку из письма DenTrain, чтобы подтвердить адрес.');
}
