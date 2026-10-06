package br.com.thiaguinhosolucoes.smart24vision;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.libhttp.entity.LoginResult;
import com.libhttp.subscribers.SubscriberListener;
import com.p2p.core.BaseMonitorActivity;
import com.p2p.core.P2PHandler;
import com.p2p.core.P2PInterface.IP2P;
import com.p2p.core.P2PInterface.ISetting;
import com.p2p.core.P2PSpecial.HttpErrorCode;
import com.p2p.core.P2PSpecial.HttpSend;
import com.p2p.core.P2PSpecial.P2PSpecial;
import com.p2p.core.P2PView;

import java.lang.reflect.Proxy;

public class GwellP2PActivity extends BaseMonitorActivity {
    private static final String DEVICE_ID = "5646988473";

    private TextView status;
    private EditText accountInput;
    private EditText accountPasswordInput;
    private EditText devicePasswordInput;
    private Button connectButton;
    private String userId = "";
    private boolean p2pConnected = false;
    private IP2P p2pListener;
    private ISetting settingListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gwell_p2p);

        status = findViewById(R.id.p2pStatus);
        accountInput = findViewById(R.id.p2pAccount);
        accountPasswordInput = findViewById(R.id.p2pAccountPassword);
        devicePasswordInput = findViewById(R.id.p2pDevicePassword);
        connectButton = findViewById(R.id.p2pConnect);

        accountInput.setText(getSharedPreferences("smart24_p2p", MODE_PRIVATE).getString("account", ""));
        accountPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        devicePasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        pView = findViewById(R.id.p2pview);
        initP2PView(7, P2PView.LAYOUTTYPE_TOGGEDER);

        connectButton.setOnClickListener(v -> startRemoteConnection());
        status.setText("SMART24 P2P LAB • Sala " + DEVICE_ID + " • use dados móveis. A senha da câmera não é salva.");
    }

    private void startRemoteConnection() {
        final String account = accountInput.getText().toString().trim();
        final String accountPassword = accountPasswordInput.getText().toString();
        final String devicePassword = devicePasswordInput.getText().toString();

        if (account.isEmpty() || accountPassword.isEmpty() || devicePassword.isEmpty()) {
            status.setText("Informe a conta Yoosee, a senha da conta e a senha da câmera Sala. As senhas não são salvas.");
            return;
        }
        if (BuildConfig.YOOSEE_APP_ID.isEmpty() || BuildConfig.YOOSEE_APP_TOKEN.isEmpty() || BuildConfig.YOOSEE_APP_VERSION.isEmpty()) {
            status.setText("P2P bloqueado: AppID/AppToken/AppVersion do laboratório não foram configurados.");
            return;
        }

        connectButton.setEnabled(false);
        status.setText("Inicializando o núcleo Gwell P2P…");
        try {
            P2PSpecial.getInstance().init(
                getApplication(),
                BuildConfig.YOOSEE_APP_ID,
                BuildConfig.YOOSEE_APP_TOKEN,
                BuildConfig.YOOSEE_APP_VERSION
            );
            installListeners();
            P2PHandler.getInstance().p2pInit(this, p2pListener, settingListener);
            status.setText("Entrando na conta Yoosee para obter a sessão P2P…");

            HttpSend.getInstance().login(account, accountPassword, new SubscriberListener<LoginResult>() {
                @Override
                public void onStart() {
                    runOnUiThread(() -> status.setText("Autenticando no serviço Gwell/Yoosee…"));
                }

                @Override
                public void onNext(LoginResult result) {
                    runOnUiThread(() -> handleLoginResult(account, devicePassword, result));
                }

                @Override
                public void onError(String errorCode, Throwable throwable) {
                    runOnUiThread(() -> {
                        connectButton.setEnabled(true);
                        status.setText("Falha no login P2P: " + errorCode + (throwable == null ? "" : " • " + throwable.getClass().getSimpleName()));
                    });
                }
            });
        } catch (Throwable error) {
            connectButton.setEnabled(true);
            status.setText("Núcleo P2P não iniciou neste aparelho: " + error.getClass().getSimpleName() + " • " + safeMessage(error));
        }
    }

    private void handleLoginResult(String account, String devicePassword, LoginResult result) {
        try {
            if (!HttpErrorCode.ERROR_0.equals(result.getError_code())) {
                connectButton.setEnabled(true);
                status.setText("Login Yoosee recusado pelo serviço: " + result.getError_code());
                return;
            }

            int code1 = Integer.parseInt(result.getP2PVerifyCode1());
            int code2 = Integer.parseInt(result.getP2PVerifyCode2());
            int session1 = (int) Long.parseLong(result.getSessionID());
            int session2 = (int) Long.parseLong(result.getSessionID2());
            userId = result.getUserID();

            getSharedPreferences("smart24_p2p", MODE_PRIVATE).edit().putString("account", account).apply();

            status.setText("Sessão autenticada. Abrindo canal P2P da Sala…");
            p2pConnected = P2PHandler.getInstance().p2pConnect(userId, session1, session2, code1, code2, 0);
            if (!p2pConnected) {
                connectButton.setEnabled(true);
                status.setText("O serviço autenticou, mas não abriu a sessão P2P.");
                return;
            }

            String encodedDevicePassword = P2PHandler.getInstance().EntryPassword(devicePassword);
            P2PHandler.getInstance().call(
                userId,
                encodedDevicePassword,
                true,
                1,
                DEVICE_ID,
                "",
                "",
                2,
                DEVICE_ID
            );
            status.setText("Chamando câmera Sala " + DEVICE_ID + " pela internet…");
        } catch (Throwable error) {
            connectButton.setEnabled(true);
            status.setText("Falha ao abrir a câmera P2P: " + error.getClass().getSimpleName() + " • " + safeMessage(error));
        }
    }

    private void installListeners() {
        p2pListener = (IP2P) Proxy.newProxyInstance(
            IP2P.class.getClassLoader(),
            new Class<?>[]{IP2P.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if ("vAccept".equals(name) && args != null && args.length >= 2) {
                    int type = ((Number) args[0]).intValue();
                    int state = ((Number) args[1]).intValue();
                    runOnUiThread(() -> {
                        P2PView.type = type;
                        P2PView.scale = state;
                        status.setText("Câmera Sala aceitou a conexão P2P. Preparando vídeo…");
                        try {
                            P2PHandler.getInstance().openAudioAndStartPlaying(1);
                        } catch (Throwable error) {
                            status.setText("P2P aceito, mas o player falhou: " + error.getClass().getSimpleName());
                        }
                    });
                } else if ("vConnectReady".equals(name)) {
                    runOnUiThread(() -> {
                        status.setText("Canal P2P pronto. Iniciando vídeo ao vivo…");
                        try {
                            pView.sendStartBrod();
                        } catch (Throwable error) {
                            status.setText("Canal P2P pronto, mas o vídeo não iniciou: " + error.getClass().getSimpleName());
                        }
                    });
                } else if ("vRetPostFromeNative".equals(name) && args != null && args.length > 0 && ((Number) args[0]).intValue() == 10) {
                    runOnUiThread(() -> {
                        status.setText("VÍDEO P2P AO VIVO • OFICINA / SALA • 5G/4G");
                        connectButton.setEnabled(true);
                    });
                } else if ("vReject".equals(name)) {
                    int reason = args != null && args.length > 1 && args[1] instanceof Number ? ((Number) args[1]).intValue() : -1;
                    runOnUiThread(() -> {
                        connectButton.setEnabled(true);
                        status.setText("Câmera recusou/encerrou o P2P. Código: " + reason);
                    });
                }
                return defaultValue(method.getReturnType());
            }
        );

        settingListener = (ISetting) Proxy.newProxyInstance(
            ISetting.class.getClassLoader(),
            new Class<?>[]{ISetting.class},
            (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "sem detalhe adicional" : message;
    }

    @Override
    public void onDestroy() {
        try {
            P2PHandler.getInstance().finish();
            if (p2pConnected) P2PHandler.getInstance().p2pDisconnect();
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override protected void onP2PViewSingleTap() {}
    @Override protected void onP2PViewFilling() {}
    @Override protected void turnCamera() {}
    @Override protected void onCaptureScreenResult(boolean isSuccess, int prePoint) {}
    @Override protected void onVideoPTS(long videoPTS) {}
    @Override public int getActivityInfo() { return 0; }
    @Override protected void onGoBack() {}
    @Override protected void onGoFront() {}
    @Override protected void onExit() {}
}
