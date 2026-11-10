package com.tvai.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.UUID;

/**
 * Per-install device authentication. The private key remains inside Android Keystore.
 * No API key, shared secret, or bearer token is stored in the APK or preferences.
 */
final class DeviceAuth {
    private static final String BASE = "https://mohamed-tv-ai-backend.mhma26395.workers.dev";
    private static final String PREFS = "device_identity";
    private static final String ID_KEY = "install_id";
    private static final String ALIAS = "mohamed_tv_ai_device_signing_v1";

    private DeviceAuth() {}

    static String getSessionToken(Context context) throws Exception {
        String installId = getOrCreateInstallId(context);
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            // Never reuse an enrolled installation ID with a replacement key.
            installId = UUID.randomUUID().toString();
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(ID_KEY, installId).putBoolean("enrolled", false).apply();
            createKeyPair();
        }
        PrivateKey privateKey = (PrivateKey) ks.getKey(ALIAS, null);
        java.security.cert.Certificate cert = ks.getCertificate(ALIAS);
        if (privateKey == null || cert == null) throw new SecurityException("device_key_unavailable");
        String publicKey = Base64.encodeToString(cert.getPublicKey().getEncoded(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("enrolled", false)) {
            JSONObject challenge = post("/v1/auth/challenge", new JSONObject()
                    .put("installId", installId).put("purpose", "enroll"));
            String nonce = challenge.getString("nonce");
            String signature = sign(privateKey, "enroll|" + installId + "|" + nonce);
            JSONObject enrolled = post("/v1/auth/enroll", new JSONObject()
                    .put("installId", installId).put("nonce", nonce)
                    .put("publicKey", publicKey).put("signature", signature));
            if (!enrolled.optBoolean("enrolled", false)) throw new SecurityException("device_enrollment_failed");
            prefs.edit().putBoolean("enrolled", true).apply();
        }

        JSONObject challenge = post("/v1/auth/challenge", new JSONObject()
                .put("installId", installId).put("purpose", "session"));
        String nonce = challenge.getString("nonce");
        String signature = sign(privateKey, "session|" + installId + "|" + nonce);
        JSONObject session = post("/v1/auth/session", new JSONObject()
                .put("installId", installId).put("nonce", nonce).put("signature", signature));
        String token = session.optString("token", "");
        if (token.length() < 40) throw new SecurityException("device_session_failed");
        return token;
    }

    private static String getOrCreateInstallId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(ID_KEY, "");
        if (id.matches("(?i)[0-9a-f-]{36}")) return id;
        id = UUID.randomUUID().toString();
        prefs.edit().putString(ID_KEY, id).putBoolean("enrolled", false).apply();
        return id;
    }

    private static void createKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build();
        generator.initialize(spec);
        KeyPair pair = generator.generateKeyPair();
        if (pair.getPrivate() == null) throw new SecurityException("keystore_key_generation_failed");
    }

    private static String sign(PrivateKey key, String message) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(key);
        signer.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(signer.sign(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static JSONObject post(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE + path).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new java.io.IOException("auth_http_" + status);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int count;
            while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count);
            JSONObject result = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            if (status < 200 || status >= 300) {
                String error = result.optString("error", "authentication_failed");
                throw new java.io.IOException(error);
            }
            return result;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
