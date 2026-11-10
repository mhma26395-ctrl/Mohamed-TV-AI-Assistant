package com.tvai.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

/** TV-first assistant. Version 0.2.0 adds normalized Arabic/English command aliases. */
public class MainActivity extends Activity {
    private TextView status, transcript, response;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String speechLanguage = "ar-EG";
    private Button micButton;
    private EditText tokenInput, messageInput;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private static final String API_URL = "https://mohamed-tv-ai-backend.mhma26395.workers.dev/v1/assistant";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildUi();
        tokenInput.setText(getPreferences(MODE_PRIVATE).getString("access_token", ""));
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) { ttsReady = true; tts.setLanguage(new Locale("ar")); }
        });
        if (SpeechRecognizer.isRecognitionAvailable(this)) recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        requestMicPermission();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(56, 34, 56, 34);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(8,17,31));
        root.setFocusable(false);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("Mohamed TV AI Assistant");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your smart TV voice companion · مساعدك الذكي للتلفزيون");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(17);
        root.addView(subtitle);

        status = label("Ready · جاهز للاستماع");
        root.addView(status);

        tokenInput = new EditText(this);
        tokenInput.setId(View.generateViewId());
        tokenInput.setFocusable(true);
        tokenInput.setFocusableInTouchMode(true);
        tokenInput.setSingleLine(true);
        tokenInput.setHint("Access Token (مرة واحدة)");
        tokenInput.setTextColor(Color.WHITE);
        tokenInput.setHintTextColor(Color.LTGRAY);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(tokenInput);
        Button saveToken = button("Save connection token / حفظ مفتاح الاتصال");
        saveToken.setId(View.generateViewId());
        saveToken.setNextFocusDownId(messageInput.getId());
        saveToken.setOnClickListener(v -> {
            String value = tokenInput.getText().toString().trim();
            if (value.isEmpty()) { reply("أدخل Access Token أولاً."); return; }
            getPreferences(MODE_PRIVATE).edit().putString("access_token", value).apply();
            reply("تم حفظ مفتاح الاتصال على هذا الجهاز.");
        });
        root.addView(saveToken);
        messageInput = new EditText(this);
        messageInput.setId(View.generateViewId());
        messageInput.setFocusable(true);
        messageInput.setFocusableInTouchMode(true);
        messageInput.setNextFocusUpId(saveToken.getId());
        messageInput.setSingleLine(true);
        messageInput.setHint("اكتب أمرًا للتلفزيون / Type a command");
        messageInput.setTextColor(Color.WHITE);
        messageInput.setHintTextColor(Color.LTGRAY);
        root.addView(messageInput);
        Button sendButton = button("Send to AI / إرسال للمساعد");
        sendButton.setId(View.generateViewId());
        messageInput.setNextFocusDownId(sendButton.getId());
        sendButton.setNextFocusUpId(messageInput.getId());
        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty()) { transcript.setText("You said / قلت: " + message); sendToAi(message); }
        });
        root.addView(sendButton);
        micButton = button("🎙  Speak / تحدث");
        micButton.setOnClickListener(v -> startListening());
        root.addView(micButton);

        Button lang = button("Language: العربية / English");
        lang.setOnClickListener(v -> {
            speechLanguage = speechLanguage.equals("ar-EG") ? "en-US" : "ar-EG";
            lang.setText(speechLanguage.equals("ar-EG") ? "Speech: العربية" : "Speech: English");
            setStatus("Speech language changed / تم تغيير لغة التعرف على الصوت");
        });
        root.addView(lang);

        transcript = label("You said / قلت: —");
        root.addView(transcript);
        response = label("Assistant / المساعد: Try: افتح يوتيوب، افتح إعدادات VPN، افتح داونلودر.");
        root.addView(response);
        root.addView(label("Quick actions / أوامر سريعة"));

        addAction(root, "Open YouTube", "open youtube");
        addAction(root, "Open Google Play", "open play store");
        addAction(root, "Open Settings", "open settings");
        addAction(root, "VPN settings", "open vpn");
        addAction(root, "Language settings", "open language settings");
        addAction(root, "Open Downloader", "open downloader");
        addAction(root, "Volume up", "volume up");
        addAction(root, "Volume down", "volume down");
        addAction(root, "Help / مساعدة", "help");

        TextView foot = label("Version 0.2.2 · Use the remote arrows to move between highlighted buttons. System changes may require your confirmation.");
        foot.setTextSize(13);
        root.addView(foot);
        micButton.requestFocus();
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextColor(Color.WHITE); v.setTextSize(18);
        v.setPadding(12,18,12,18);
        return v;
    }

    private GradientDrawable buttonBackground(boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(focused ? Color.rgb(0,190,235) : Color.rgb(25,92,145));
        bg.setCornerRadius(18);
        if (focused) bg.setStroke(4, Color.WHITE);
        return bg;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(19); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackground(buttonBackground(false));
        b.setFocusable(true); b.setFocusableInTouchMode(true);
        b.setPadding(28,10,28,10);
        b.setOnFocusChangeListener((v, focused) -> {
            b.setBackground(buttonBackground(focused));
            b.setTextColor(Color.WHITE);
            b.setScaleX(focused ? 1.025f : 1.0f);
            b.setScaleY(focused ? 1.025f : 1.0f);
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,8,0,8);
        b.setLayoutParams(p);
        return b;
    }

    private void addAction(LinearLayout root, String label, String command) {
        Button b = button(label);
        b.setOnClickListener(v -> handleCommand(command));
        root.addView(b);
    }

    private void requestMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicPermission(); setStatus("Microphone permission required / نحتاج إذن الميكروفون"); return;
        }
        if (recognizer == null) { setStatus("Speech recognition is unavailable on this device."); return; }
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) { setStatus("Listening… / أستمع إليك…"); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float r) {}
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() { setStatus("Processing… / جاري المعالجة…"); }
            public void onError(int e) { setStatus("Could not recognize speech. Try again / لم أتمكن من فهم الصوت، حاول مجددًا."); }
            public void onResults(Bundle b) {
                ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    transcript.setText("You said / قلت: " + list.get(0));
                    sendToAi(list.get(0));
                }
            }
            public void onPartialResults(Bundle b) {}
            public void onEvent(int e, Bundle p) {}
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguage);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLanguage);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak / تحدث الآن");
        recognizer.startListening(i);
    }

    private String normalize(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).trim();
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replace("أ","ا").replace("إ","ا").replace("آ","ا")
                .replace("ى","ي").replace("ة","ه").replace("ـ","");
        // Arabic speech engines often transcribe the English acronym VPN phonetically.
        s = s.replace("في بي ان", " vpn ").replace("في بي اين", " vpn ")
                .replace("vee pee en", " vpn ").replace("v p n", " vpn ")
                .replace("داون لودر", "داونلودر");
        return s.replaceAll("\\s+", " ").trim();
    }

    private boolean has(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private void handleCommand(String raw) {
        String c = normalize(raw);

        if (has(c, "help", "مساعده", "ساعدني", "الاوامر")) {
            reply("Try: افتح يوتيوب، افتح داونلودر، افتح اعدادات VPN، افتح اعدادات اللغه، افتح اعدادات الشبكه، ارفع الصوت، اخفض الصوت. / Try: open YouTube, Downloader, VPN settings, language settings, network settings, volume up or down.");
            return;
        }
        if (has(c, "vpn", "في بي ان", "الفي بي ان", "في بي اين", "الفي بي اين", "شبكه افتراضيه", "برنامج vpn", "اعدادات vpn")) {
            launchIntentWithFallback(new Intent(Settings.ACTION_VPN_SETTINGS), new Intent(Settings.ACTION_WIRELESS_SETTINGS),
                    "VPN settings", "تم فتح إعدادات الشبكة أو VPN. اختر تطبيق VPN ووافق على الاتصال يدويًا؛ لا يمكن للتطبيق تفعيل VPN دون موافقتك. / VPN or network settings opened. Select your VPN and approve the connection; Android requires your consent.");
            return;
        }
        if (has(c, "اللغه", "لغه الجهاز", "حول لغه الجهاز", "غير لغه الجهاز", "غير اللغه", "تغيير اللغه", "اللغه الانجليزيه", "للانجليزيه", "انجليزي", "خلي الجهاز انجليزي", "english language", "device language", "language settings")) {
            launchIntentWithFallback(new Intent(Settings.ACTION_LOCALE_SETTINGS), new Intent(Settings.ACTION_SETTINGS),
                    "Language settings", "إذا لم تفتح صفحة اللغة مباشرة، افتح إعدادات الجهاز ثم النظام ثم اللغة واختر English. / If the language page is unavailable, open Settings, then System, then Language and choose English.");
            return;
        }
        if (has(c, "downloader", "داونلودر", "داونلودر")) {
            launchPackage("com.esaba.downloader", "Downloader");
            return;
        }
        if (has(c, "youtube", "يوتيوب")) { launchYouTube(); return; }
        if (has(c, "play store", "google play", "جوجل بلاي", "متجر")) {
            launchPackage("com.android.vending", "Google Play"); return;
        }
        if (has(c, "wifi", "wi-fi", "واي فاي", "الشبكه", "اعدادات الشبكه", "network settings", "internet settings")) {
            launchIntent(new Intent(Settings.ACTION_WIFI_SETTINGS), "Network settings"); return;
        }
        if (has(c, "developer options", "وضع المطورين", "خيارات المطور", "اعدادات المطور")) {
            launchIntent(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), "Developer options",
                    "تم فتح خيارات المطورين. بعض الخيارات تحتاج تفعيلًا يدويًا. / Developer options opened. Some options must be enabled manually.");
            return;
        }
        if (has(c, "settings", "الاعدادات", "اعدادات", "الضبط", "الاعداد")) {
            launchIntent(new Intent(Settings.ACTION_SETTINGS), "Settings"); return;
        }
        if (has(c, "volume up", "ارفع الصوت", "علي الصوت", "زود الصوت", "اعلي الصوت")) {
            adjustVolume(AudioManager.ADJUST_RAISE, "Volume increased / تم رفع الصوت"); return;
        }
        if (has(c, "volume down", "اخفض الصوت", "وطي الصوت", "خفض الصوت", "قلل الصوت")) {
            adjustVolume(AudioManager.ADJUST_LOWER, "Volume decreased / تم خفض الصوت"); return;
        }
        if (has(c, "مؤقت النوم", "اضبط مؤقت النوم", "وقت النوم", "sleep timer", "turn off tv in", "اطفي التلفزيون بعد", "اقفل التلفزيون بعد", "اطفاء التلفزيون بعد")) {
            launchIntentWithFallback(new Intent("android.settings.SLEEP_SETTINGS"), new Intent(Settings.ACTION_SETTINGS),
                    "Sleep Timer", "فتحت إعدادات مؤقت النوم إن كانت مدعومة. اختر المدة المطلوبة، مثل 30 دقيقة، ثم أكّد الاختيار. إذا لم تظهر الصفحة، ابحث عن مؤقت النوم ضمن إعدادات الطاقة أو الجهاز. / Opened Sleep Timer settings if supported. Choose the duration, such as 30 minutes, and confirm. If unavailable, look under Power or Device settings.");
            return;
        }
        if (has(c, "خفض الاضاءه", "قلل الاضاءه", "قلل السطوع", "خفض السطوع", "brightness")) {
            launchIntent(new Intent(Settings.ACTION_DISPLAY_SETTINGS), "Display settings"); return;
        }
        if (has(c, "شاشه الاستراحه", "شاشه التوقف", "screensaver", "screen saver")) {
            launchIntent(new Intent(Settings.ACTION_DREAM_SETTINGS), "Screen saver settings"); return;
        }
        if (has(c, "اخر تطبيق", "التطبيق السابق", "last app", "previous app", "ارجع للتطبيق")) {
            reply("Android does not allow this app to reopen the previous app directly. Use the TV remote's Recent Apps button / لا يسمح أندرويد بفتح آخر تطبيق مباشرة من هذا التطبيق؛ استخدم زر التطبيقات الأخيرة في الريموت.");
            return;
        }
        if (has(c, "اعاده تشغيل", "اعد تشغيل التلفزيون", "restart tv", "reboot tv")) {
            reply("For safety, this app cannot restart the TV. Please use the TV power menu or remote / لأسباب تتعلق بصلاحيات النظام، لا يستطيع التطبيق إعادة تشغيل التلفزيون. استخدم قائمة الطاقة أو الريموت.");
            return;
        }
        reply("I did not recognize that command yet. Try: open YouTube, Downloader, VPN settings, language settings or network settings. / لم أفهم الأمر بعد. جرّب أمرًا من الأوامر المدعومة أو افتح المساعدة.");
    }


    private void sendToAi(String message) {
        String token = getPreferences(MODE_PRIVATE).getString("access_token", "").trim();
        if (token.isEmpty()) { reply("احفظ Access Token أولاً من خانة الاتصال."); return; }
        setStatus("Connecting to AI… / جاري الاتصال…");
        network.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(45000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                JSONObject payload = new JSONObject();
                payload.put("message", message);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                java.io.InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[2048]; int count;
                while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count);
                JSONObject result = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                if (code != 200) {
                    String error = result.optString("error", "http_" + code);
                    runOnUiThread(() -> reply("تعذر الاتصال بالمساعد: " + error + " (" + code + ")"));
                    return;
                }
                String answer = result.optString("text", "");
                JSONArray calls = result.optJSONArray("toolCalls");
                runOnUiThread(() -> {
                    if (!answer.isEmpty()) reply(answer);
                    else if (calls == null || calls.length() == 0) reply("وصل الرد من الذكاء الاصطناعي دون أمر.");
                    if (calls != null) executeAiTools(calls);
                });
            } catch (Exception e) {
                runOnUiThread(() -> reply("فشل الاتصال بالخادم. تحقق من الإنترنت ومفتاح الاتصال."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void executeAiTools(JSONArray calls) {
        for (int i = 0; i < calls.length(); i++) {
            JSONObject call = calls.optJSONObject(i);
            if (call == null) continue;
            String name = call.optString("name", "");
            JSONObject args = call.optJSONObject("args");
            if (args == null) args = new JSONObject();
            try {
                if ("launch_app".equals(name)) {
                    String pkg = args.optString("packageName", "");
                    if (!pkg.isEmpty()) launchPackage(pkg, args.optString("appName", pkg));
                } else if ("adjust_volume".equals(name)) {
                    int steps = Math.max(1, Math.min(5, args.optInt("steps", 1)));
                    int dir = "down".equals(args.optString("direction")) ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE;
                    AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
                    for (int n = 0; n < steps; n++) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0);
                    reply("تم تنفيذ أمر الصوت.");
                } else if ("open_settings".equals(name)) {
                    String page = args.optString("page", "general");
                    Intent intent;
                    if ("wifi".equals(page)) intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                    else if ("vpn".equals(page)) intent = new Intent(Settings.ACTION_VPN_SETTINGS);
                    else if ("language".equals(page)) intent = new Intent(Settings.ACTION_LOCALE_SETTINGS);
                    else if ("display".equals(page)) intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                    else if ("screensaver".equals(page)) intent = new Intent(Settings.ACTION_DREAM_SETTINGS);
                    else if ("developer".equals(page)) intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    else intent = new Intent(Settings.ACTION_SETTINGS);
                    launchIntent(intent, "Settings");
                } else if ("get_device_info".equals(name)) {
                    reply("الجهاز: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE);
                } else if ("list_installed_apps".equals(name)) {
                    reply("قائمة التطبيقات المثبتة لم تُربط بعد.");
                }
            } catch (Exception e) { reply("تعذر تنفيذ الأمر على هذا التلفزيون."); }
        }
    }

    private void adjustVolume(int direction, String message) {
        AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        reply(message);
    }

    private void launchYouTube() {
        String[] packages = {"com.google.android.youtube.tv", "com.google.android.youtube", "com.google.android.youtube.tvunplugged"};
        for (String pkg : packages) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                try { startActivity(launch); reply("Opening YouTube / جاري فتح يوتيوب"); return; }
                catch (Exception ignored) {}
            }
        }
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/tv"));
            startActivity(view);
            reply("Trying to open YouTube / أحاول فتح يوتيوب");
        } catch (Exception e) {
            reply("YouTube could not be opened. Check that it is installed on this TV / تعذر فتح يوتيوب؛ تأكد من تثبيته على التلفزيون.");
        }
    }

    private void launchPackage(String pkg, String name) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) { reply(name + " is not installed or cannot be launched / التطبيق غير مثبت أو لا يمكن تشغيله"); return; }
        try { startActivity(i); reply("Opening " + name + " / جاري فتح " + name); }
        catch (Exception e) { reply("Unable to open " + name + " / تعذر فتح التطبيق"); }
    }

    private void launchIntent(Intent i, String name) {
        launchIntent(i, name, "Opening " + name + " / جاري فتح " + name);
    }

    private void launchIntent(Intent i, String name, String message) {
        try { startActivity(i); reply(message); }
        catch (ActivityNotFoundException e) { reply("This settings page is unavailable on this TV / صفحة الإعدادات غير متاحة على هذا التلفزيون."); }
    }


    private void launchIntentWithFallback(Intent primary, Intent fallback, String name, String message) {
        try {
            primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(primary);
            reply(message);
        } catch (Exception first) {
            try {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
                reply("تعذر فتح صفحة " + name + " مباشرة؛ فتحت الإعدادات العامة. ابحث عن الخيار يدويًا. / The " + name + " page is unavailable; general settings opened. Find the option manually.");
            } catch (Exception second) {
                reply("صفحة " + name + " غير متاحة على هذا التلفزيون. / " + name + " is unavailable on this TV.");
            }
        }
    }

    private void setStatus(String s) { if (status != null) status.setText(s); }

    private void reply(String s) {
        response.setText("Assistant / المساعد: " + s);
        setStatus("Ready · جاهز");
        if (ttsReady) {
            Locale l = s.matches(".*[\\u0600-\\u06FF].*") ? new Locale("ar") : Locale.US;
            tts.setLanguage(l);
            tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "tvai-reply");
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) { startListening(); return true; }
        return super.onKeyDown(keyCode,event);
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
