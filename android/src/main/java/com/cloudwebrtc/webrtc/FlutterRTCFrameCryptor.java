package com.cloudwebrtc.webrtc;

import android.util.Log;

import androidx.annotation.NonNull;

import org.webrtc.FrameCryptor;
import org.webrtc.FrameCryptorAlgorithm;
import org.webrtc.FrameCryptorFactory;
import org.webrtc.FrameCryptorKeyManager;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import com.cloudwebrtc.webrtc.utils.AnyThreadSink;
import com.cloudwebrtc.webrtc.utils.ConstraintsMap;
import com.cloudwebrtc.webrtc.utils.ConstraintsArray;

public class FlutterRTCFrameCryptor {

    class FrameCryptorStateObserver  implements FrameCryptor.Observer, EventChannel.StreamHandler {
        public FrameCryptorStateObserver(BinaryMessenger messenger, String frameCryptorId){
            this.frameCryptorId = frameCryptorId;
            eventChannel = new EventChannel(messenger, "FlutterWebRTC/frameCryptorEvent" + frameCryptorId);
            eventChannel.setStreamHandler(new EventChannel.StreamHandler() {
                @Override
                public void onListen(Object o, EventChannel.EventSink sink) {
                    eventSink = new AnyThreadSink(sink);
                    for(Object event : eventQueue) {
                        eventSink.success(event);
                    }
                    eventQueue.clear();
                }
                @Override
                public void onCancel(Object o) {
                    eventSink = null;
                }
            });
        }
        private EventChannel eventChannel;
        private EventChannel.EventSink eventSink;
        private ArrayList eventQueue = new ArrayList();
        private String frameCryptorId;

        @Override
        public void onListen(Object arguments, EventChannel.EventSink events) {
            eventSink = new AnyThreadSink(events);
            for(Object event : eventQueue) {
                eventSink.success(event);
            }
            eventQueue.clear();
        }

        @Override
        public void onCancel(Object arguments) {
            eventSink = null;
        }

        private String  frameCryptorErrorStateToString( FrameCryptor.FrameCryptorErrorState state) {
            switch (state) {
                case NEW:
                    return "new";
                case OK:
                    return "ok";
                case DECRYPTIONFAILED:
                    return "decryptionFailed";
                case ENCRYPTIONFAILED:
                    return "encryptionFailed";
                case INTERNALERROR:
                    return "internalError";
                case MISSINGKEY:
                    return "missingKey";
                default:
                    throw new IllegalArgumentException("Unknown FrameCryptorErrorState: " + state);
            }
        }

        @Override
        public void onFrameCryptorErrorState(String participantId, FrameCryptor.FrameCryptorErrorState state) {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "frameCryptionStateChanged");
            event.put("participantId", participantId);
            event.put("state",frameCryptorErrorStateToString(state));
            if (eventSink != null) {
                eventSink.success(event);
            } else {
                eventQueue.add(event);
            }
        }
    }

    private static final String TAG = "FlutterRTCFrameCryptor";
    private final Map<String, FrameCryptor> frameCryptos = new HashMap<>();
    private final Map<String, FrameCryptorStateObserver> frameCryptoObservers = new HashMap<>();
    private final Map<String, FrameCryptorKeyManager> keyManagers = new HashMap<>();
    private StateProvider stateProvider;
    public FlutterRTCFrameCryptor(StateProvider stateProvider) {
        this.stateProvider = stateProvider;
    }
    public boolean handleMethodCall(MethodCall call, @NonNull Result result) {
        String method_name = call.method;
        Map<String, Object> params = (Map<String, Object>) call.arguments;
        if (method_name.equals("frameCryptorFactoryCreateFrameCryptor")) {
            frameCryptorFactoryCreateFrameCryptor(params, result);
            return true;
          } else if (method_name.equals("frameCryptorSetKeyIndex")) {
            frameCryptorSetKeyIndex(params, result);
            return true;
          } else if (method_name.equals("frameCryptorGetKeyIndex")) {
            frameCryptorGetKeyIndex(params, result);
            return true;
          } else if (method_name.equals("frameCryptorSetEnabled")) {
            frameCryptorSetEnabled(params, result);
            return true;
          } else if (method_name.equals("frameCryptorGetEnabled")) {
            frameCryptorGetEnabled(params, result);
            return true;
          } else if (method_name.equals("frameCryptorDispose")) {
            frameCryptorDispose(params, result);
            return true;
          } else if (method_name.equals("frameCryptorFactoryCreateKeyManager")) {
            frameCryptorFactoryCreateKeyManager(params, result);
            return true;
          } else if (method_name.equals("keyManagerSetKey")) {
            keyManagerSetKey(params, result);
            return true;
          } else if (method_name.equals("keyManagerSetKeys")) {
            keyManagerSetKeys(params, result);
            return true;
          } else if (method_name.equals("keyManagerGetKeys")) {
            keyManagerGetKeys(params, result);
            return true;
          } else if (method_name.equals("keyManagerDispose")) {
            keyManagerDispose(params, result);
            return true;
          }
        return false;
    }

    private FrameCryptorAlgorithm frameCryptorAlgorithmFromInt(int algorithm) {
        switch (algorithm) {
            case 0:
                return FrameCryptorAlgorithm.AES_GCM;
            case 1:
                return FrameCryptorAlgorithm.AES_CBC;
            default:
                return FrameCryptorAlgorithm.AES_GCM;
        }
    }

    private void frameCryptorFactoryCreateFrameCryptor(Map<String, Object> params, @NonNull Result result) {
        String keyManagerId = (String) params.get("keyManagerId");
        FrameCryptorKeyManager keyManager = keyManagers.get(keyManagerId);
        if (keyManager == null) {
            result.error("frameCryptorFactoryCreateFrameCryptorFailed", "keyManager not found", null);
            return;
        }
        String peerConnectionId = (String) params.get("peerConnectionId");
        PeerConnectionObserver pco = stateProvider.getPeerConnectionObserver(peerConnectionId);
        if (pco == null) {
            result.error("frameCryptorFactoryCreateFrameCryptorFailed", "peerConnection not found", null);
            return;
        }
        String participantId = (String) params.get("participantId");
        String type = (String) params.get("type");
        int algorithm = (int) params.get("algorithm");
        String rtpSenderId = (String) params.get("rtpSenderId");
        String rtpReceiverId = (String) params.get("rtpReceiverId");

        if(type.equals("sender")) {
            RtpSender rtpSender = pco.getRtpSenderById(rtpSenderId);

            FrameCryptor frameCryptor = FrameCryptorFactory.createFrameCryptorForRtpSender(rtpSender,
                    participantId,
                    frameCryptorAlgorithmFromInt(algorithm),
                    keyManager);
            String frameCryptorId = UUID.randomUUID().toString();
            frameCryptos.put(frameCryptorId, frameCryptor);
            FrameCryptorStateObserver observer = new FrameCryptorStateObserver(stateProvider.getMessenger(), frameCryptorId);
            frameCryptor.setObserver(observer);
            frameCryptoObservers.put(frameCryptorId, observer);
            ConstraintsMap paramsResult = new ConstraintsMap();
            paramsResult.putString("frameCryptorId", frameCryptorId);
            result.success(paramsResult.toMap());
        } else if(type.equals("receiver")) {
            RtpReceiver rtpReceiver = pco.getRtpReceiverById(rtpReceiverId);

            FrameCryptor frameCryptor = FrameCryptorFactory.createFrameCryptorForRtpReceiver(rtpReceiver,
                    participantId,
                    frameCryptorAlgorithmFromInt(algorithm),
                    keyManager);
            String frameCryptorId = UUID.randomUUID().toString();
            frameCryptos.put(frameCryptorId, frameCryptor);
            FrameCryptorStateObserver observer = new FrameCryptorStateObserver(stateProvider.getMessenger(), frameCryptorId);
            frameCryptor.setObserver(observer);
            frameCryptoObservers.put(frameCryptorId, observer);
            ConstraintsMap paramsResult = new ConstraintsMap();
            paramsResult.putString("frameCryptorId", frameCryptorId);
            result.success(paramsResult.toMap());
        } else {
            result.error("frameCryptorFactoryCreateFrameCryptorFailed", "type must be sender or receiver", null);
            return;
        }
    }

    private void frameCryptorSetKeyIndex(Map<String, Object> params, @NonNull Result result) {
        String frameCryptorId = (String) params.get("frameCryptorId");
        FrameCryptor frameCryptor = frameCryptos.get(frameCryptorId);
        if (frameCryptor == null) {
            result.error("frameCryptorSetKeyIndexFailed", "frameCryptor not found", null);
            return;
        }
        int keyIndex = (int) params.get("keyIndex");
        frameCryptor.setKeyIndex(keyIndex);
        ConstraintsMap paramsResult = new ConstraintsMap();
        paramsResult.putBoolean("result", true);
        result.success(paramsResult.toMap());
    }

    private void frameCryptorGetKeyIndex(Map<String, Object> params, @NonNull Result result) {
        String frameCryptorId = (String) params.get("frameCryptorId");
        FrameCryptor frameCryptor = frameCryptos.get(frameCryptorId);
        if (frameCryptor == null) {
            result.error("frameCryptorGetKeyIndexFailed", "frameCryptor not found", null);
            return;
        }
        int keyIndex = frameCryptor.getKeyIndex();
        ConstraintsMap paramsResult = new ConstraintsMap();
        paramsResult.putInt("keyIndex", keyIndex);
        result.success(paramsResult.toMap());
    }

    private void frameCryptorSetEnabled(Map<String, Object> params, @NonNull Result result) {
        String frameCryptorId = (String) params.get("frameCryptorId");
        FrameCryptor frameCryptor = frameCryptos.get(frameCryptorId);
        if (frameCryptor == null) {
            result.error("frameCryptorSetEnabledFailed", "frameCryptor not found", null);
            return;
        }
        boolean enabled = (boolean) params.get("enabled");
        frameCryptor.setEnabled(enabled);

        ConstraintsMap paramsResult = new ConstraintsMap();
        paramsResult.putBoolean("result", enabled);
        result.success(paramsResult.toMap());
    }

    private void frameCryptorGetEnabled(Map<String, Object> params, @NonNull Result result) {
        String frameCryptorId = (String) params.get("frameCryptorId");
        FrameCryptor frameCryptor = frameCryptos.get(frameCryptorId);
        if (frameCryptor == null) {
            result.error("frameCryptorGetEnabledFailed", "frameCryptor not found", null);
            return;
        }
        boolean enabled = frameCryptor.isEnabled();
        ConstraintsMap paramsResult = new ConstraintsMap();
        paramsResult.putBoolean("enabled", enabled);
        result.success(paramsResult.toMap());
    }

    private void frameCryptorDispose(Map<String, Object> params, @NonNull Result result) {
        String frameCryptorId = (String) params.get("frameCryptorId");
        FrameCryptor frameCryptor = frameCryptos.get(frameCryptorId);
        if (frameCryptor == null) {
            result.error("frameCryptorDisposeFailed", "frameCryptor not found", null);
            return;
        }
        frameCryptor.dispose();
        frameCryptos.remove(frameCryptorId);
        frameCryptoObservers.remove(frameCryptorId);
        ConstraintsMap paramsResult = new ConstraintsMap();
        paramsResult.putString("result", "success");
        result.success(paramsResult.toMap());
    }

    private void frameCryptorFactoryCreateKeyManager(Map<String, Object> params, @NonNull Result result) {
        String keyManagerId = UUID.randomUUID().toString();
        FrameCryptorKeyManager keyManager = FrameCryptorFactory.createFrameCryptorKeyManager();
        keyManagers.put(keyManagerId, keyManager);

        ConstraintsMap paramsResult = new ConstraintsMap();
        paramsResult.putString("keyManagerId", keyManagerId);
        result.success(paramsResult.toMap());
    }

    private void keyManagerSetKey(Map<String, Object> params, @NonNull Result result) {
        String keyManagerId = (String) params.get("keyManagerId");
        FrameCryptorKeyManager keyManager = keyManagers.get(keyManagerId);
        if (keyManager == null) {
            result.error("keyManagerSetKeyFailed", "keyManager not found", null);
            return;
        }
        int keyIndex = (int) params.get("keyIndex");
        String participantId = (String) params.get("participantId");
        byte[] key = ( byte[]) params.get("key");
        keyManager.setKey(participantId, keyIndex, key);

        ConstraintsMap paramsResult = new ConstraintsMap();
        paramsResult.putBoolean("result", true);
        result.success(paramsResult.toMap());
    }

    private void keyManagerSetKeys(Map<String, Object> params, @NonNull Result result) {
        String keyManagerId = (String) params.get("keyManagerId");
        FrameCryptorKeyManager keyManager = keyManagers.get(keyManagerId);
        if (keyManager == null) {
            result.error("keyManagerSetKeysFailed", "keyManager not found", null);
            return;
        }
        String participantId = (String) params.get("participantId");
        ArrayList<byte[]> keys = ( ArrayList<byte[]>) params.get("keys");
        keyManager.setKeys(participantId, keys);

        ConstraintsMap paramsResult = new ConstraintsMap();
        paramsResult.putBoolean("result", true);
        result.success(paramsResult.toMap());
    }

    private void keyManagerGetKeys(Map<String, Object> params, @NonNull Result result) {
        String keyManagerId = (String) params.get("keyManagerId");
        FrameCryptorKeyManager keyManager = keyManagers.get(keyManagerId);
        if (keyManager == null) {
            result.error("keyManagerGetKeyFailed", "keyManager not found", null);
            return;
        }
        int keyIndex = (int) params.get("keyIndex");
        String participantId = (String) params.get("participantId");
        ArrayList<byte[]> keys = keyManager.getKeys(participantId);

        ConstraintsMap paramsResult = new ConstraintsMap();
        ConstraintsArray keysArray = new ConstraintsArray();
        for (byte[] key : keys) {
            keysArray.pushByte(key);
        }
        paramsResult.putArray("keys", keysArray.toArrayList());
        result.success(paramsResult.toMap());
    }

    private void keyManagerDispose(Map<String, Object> params, @NonNull Result result) {
        String keyManagerId = (String) params.get("keyManagerId");
        FrameCryptorKeyManager keyManager = keyManagers.get(keyManagerId);
        if (keyManager == null) {
            result.error("keyManagerDisposeFailed", "keyManager not found", null);
            return;
        }
        keyManager.dispose();
        keyManagers.remove(keyManagerId);
        ConstraintsMap paramsResult = new ConstraintsMap();
        paramsResult.putString("result", "success");
        result.success(paramsResult.toMap());
    }
}