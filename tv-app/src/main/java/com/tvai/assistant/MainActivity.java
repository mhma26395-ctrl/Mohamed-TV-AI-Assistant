package com.tvai.assistant;

import android.Manifest;
import rikka.shizuku.Shizuku;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import android.os.Build;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.Environment;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PermissionInfo;
import java.io.File;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** TV-first assistant. Version 0.2.0 adds normalized Arabic/English command aliases. */
public class MainActivity extends Activity {
    protected TextView status, transcript, response;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    protected String speechLanguage = "ar-EG";
    private Button micButton;
    private Button ttsToggleButton;
    private EditText messageInput;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    // Prevent duplicate AI requests from piling up when the user presses Send or the mic repeatedly.
    private volatile boolean aiRequestInProgress = false;
    private static final String API_URL = BuildConfig.BACKEND_BASE_URL + "/v1/assistant";
    private static final String INFO_API_URL = BuildConfig.BACKEND_BASE_URL + "/v1/info";
    private static final int SHIZUKU_PERMISSION_REQUEST = 7301;
    private final Shizuku.OnBinderReceivedListener shizukuBinderReceived = this::refreshShizukuState;
    private final Shizuku.OnBinderDeadListener shizukuBinderDead = this::refreshShizukuState;
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionResult =
            (requestCode, grantResult) -> {
                if (requestCode == SHIZUKU_PERMISSION_REQUEST) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        reply("تم منح Shizuku صلاحية لهذا التطبيق. لم يتم تنفيذ أي أمر بعد؛ الصلاحية جاهزة للاستخدام بعد اختبار كل عملية.");
                    } else {
                        reply("لم يتم منح صلاحية Shizuku. ستظل وظائف التطبيق العادية تعمل دونها.");
                    }
                    refreshShizukuState();
                }
            };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildUi();
        getSharedPreferences("assistant_settings", MODE_PRIVATE).edit().remove("access_token").apply();
        getSharedPreferences("MainActivity", MODE_PRIVATE).edit().remove("access_token").apply();
        try { Shizuku.addBinderReceivedListener(shizukuBinderReceived); Shizuku.addBinderDeadListener(shizukuBinderDead); Shizuku.addRequestPermissionResultListener(shizukuPermissionResult); } catch (Exception ignored) {}
        if (getIntent() != null && getIntent().getBooleanExtra("start_voice", false)) new Handler(Looper.getMainLooper()).postDelayed(() -> startListening(), 700);
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) { ttsReady = true; tts.setLanguage(new Locale("ar")); }
        });
        if (SpeechRecognizer.isRecognitionAvailable(this)) recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        requestMicPermission();
    }

    protected void buildUi() {
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

        transcript = label("You said / قلت: —");
        root.addView(transcript);
        response = label("ستظهر إجابات المساعد هنا في أعلى الشاشة. / Assistant answers will appear here.");
        response.setBackgroundColor(Color.rgb(15,48,78));
        root.addView(response);

        TextView secureConnection = label("Secure device connection · اتصال آمن تلقائيًا");
        secureConnection.setTextColor(Color.LTGRAY);
        secureConnection.setTextSize(14);
        root.addView(secureConnection);
        messageInput = new EditText(this);
        messageInput.setId(View.generateViewId());
        messageInput.setFocusable(true);
        messageInput.setFocusableInTouchMode(true);
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

        ttsToggleButton = button("");
        updateTtsToggleLabel();
        ttsToggleButton.setOnClickListener(v -> {
            boolean enabled = getSharedPreferences("assistant_settings", MODE_PRIVATE)
                    .getBoolean("tts_enabled", false);
            getSharedPreferences("assistant_settings", MODE_PRIVATE).edit()
                    .putBoolean("tts_enabled", !enabled).apply();
            if (tts != null && enabled) tts.stop();
            updateTtsToggleLabel();
            setStatus(!enabled ? "Voice replies enabled / تم تفعيل الرد الصوتي" :
                    "Voice replies disabled / تم إيقاف الرد الصوتي");
        });
        root.addView(ttsToggleButton);

        Button lang = button("Language: العربية / English");
        lang.setOnClickListener(v -> {
            speechLanguage = speechLanguage.equals("ar-EG") ? "en-US" : "ar-EG";
            lang.setText(speechLanguage.equals("ar-EG") ? "Speech: العربية" : "Speech: English");
            setStatus("Speech language changed / تم تغيير لغة التعرف على الصوت");
        });
        root.addView(lang);
        Button remoteSettings = button("Remote button customization / تخصيص أزرار الريموت");
        remoteSettings.setOnClickListener(v -> showRemoteButtonSettings());
        root.addView(remoteSettings);
        Button usage = button("Check Usage Access / فحص إذن التطبيقات");
        usage.setOnClickListener(v -> { if (hasUsageAccess()) reply("إذن Usage Access مفعّل بالفعل."); else openUsageAccessSettings(); });
        root.addView(usage);

        TextView foot = label("Version 0.6.4 · Voice replies are optional. Remote key mapping uses Accessibility key filtering when supported by the TV.");
        foot.setTextSize(13);
        root.addView(foot);
        if (micButton != null) micButton.requestFocus();
    }

    private void updateTtsToggleLabel() {
        if (ttsToggleButton == null) return;
        boolean enabled = getSharedPreferences("assistant_settings", MODE_PRIVATE)
                .getBoolean("tts_enabled", false);
        ttsToggleButton.setText(enabled
                ? "🔊 إيقاف قراءة الإجابات صوتيًا / Mute replies"
                : "🔇 تفعيل قراءة الإجابات صوتيًا / Enable voice replies");
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

    protected void startListening() {
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
            openVpnSettingsOrApp();
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
        if (has(c, "mute", "اكتم الصوت", "كتم الصوت", "اسكت الصوت")) {
            AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
            if (audio != null) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
            reply("تم إرسال أمر كتم الصوت."); return;
        }
        if (has(c, "unmute", "الغاء كتم الصوت", "الغاء الصامت", "شغل الصوت", "افتح الصوت")) {
            AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
            if (audio != null) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
            reply("تم إرسال أمر إلغاء كتم الصوت."); return;
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



    private String detectInfoCity(String normalized) {
        String[][] cities = {
            {"القاهرة","القاهره","cairo"}, {"لندن","london"}, {"لوس انجلوس","los angeles"},
            {"نيويورك","new york"}, {"باريس","paris"}, {"الرياض","riyadh"}, {"دبي","dubai"},
            {"مكه","مكة","mecca"}, {"طوكيو","tokyo"}, {"اسطنبول","istanbul"}
        };
        for (String[] group : cities) for (String city : group) {
            if (normalized.contains(city)) {
                if ("القاهره".equals(city)) return "Cairo";
                if ("لندن".equals(city)) return "London";
                if ("لوس انجلوس".equals(city)) return "Los Angeles";
                if ("نيويورك".equals(city)) return "New York";
                if ("باريس".equals(city)) return "Paris";
                if ("الرياض".equals(city)) return "Riyadh";
                if ("دبي".equals(city)) return "Dubai";
                if ("مكه".equals(city) || "مكة".equals(city)) return "Mecca";
                if ("طوكيو".equals(city)) return "Tokyo";
                if ("اسطنبول".equals(city)) return "Istanbul";
                return city;
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:في|بمدينه|بمدينة|in)\\s+([\\p{L}][\\p{L} .'-]{1,40})").matcher(normalized);
        if (m.find()) return m.group(1).trim();
        return "";
    }

    private boolean tryHandleLiveInfo(String raw, String normalized) {
        boolean asksTime = has(normalized, "الوقت", "الساعه كام", "كم الساعه", "time now", "current time", "what time");
        boolean asksWeather = has(normalized, "الطقس", "الجو", "درجه الحراره", "حاله الطقس", "weather", "temperature", "forecast");
        boolean asksGold = has(normalized, "الذهب", "سعر الذهب", "gold price", "gold rate");
        boolean asksCurrency = has(normalized, "سعر الدولار", "الدولار اليوم", "سعر العمله", "سعر العملات", "exchange rate", "currency rate", "usd egp", "dollar today");
        boolean asksNews = has(normalized, "الاخبار", "اخبار", "اخر الاخبار", "news", "headlines");
        boolean asksSports = has(normalized, "مباريات", "مباراه", "مباراة", "جدول مباريات",
                "مواعيد المباريات", "ماتشات", "ماتش", "الدوري المصري", "الدوري السعودي",
                "الدوري الانجليزي", "الدوري الإنجليزي", "الدوري الاسباني", "الدوري الإسباني",
                "premier league", "la liga", "saudi pro league", "fixtures", "football matches");
        if (!asksTime && !asksWeather && !asksGold && !asksCurrency && !asksNews && !asksSports) return false;

        String city = detectInfoCity(normalized);
        if (asksNews && city.isEmpty()) {
            if (has(normalized, "العالم العربي", "الوطن العربي", "arab world")) city = "Arab world";
            else if (has(normalized, "العالم", "world")) city = "World";
            else if (has(normalized, "القاهره", "القاهرة")) city = "Cairo";
            else city = "Egypt";
        }
        if ((asksTime || asksWeather) && city.isEmpty()) {
            reply("حدد المدينة من فضلك، مثل القاهرة أو لندن أو لوس أنجلوس.");
            return true;
        }

        String selectedLeague = "";
        if (asksSports) {
            if (has(normalized, "المصري", "egyptian")) selectedLeague = "egyptian-premier-league";
            else if (has(normalized, "السعودي", "saudi")) selectedLeague = "saudi-pro-league";
            else if (has(normalized, "الاسباني", "الإسباني", "la liga", "laliga")) selectedLeague = "spanish-la-liga";
            else if (has(normalized, "الانجليزي", "الإنجليزي", "premier league", "epl")) selectedLeague = "english-premier-league";
            else {
                reply("حدد الدوري المطلوب: المصري أو السعودي أو الإنجليزي أو الإسباني.");
                return true;
            }
        }
        final String type = asksSports ? "sports" : asksWeather ? "weather" : asksTime ? "time" : asksGold ? "gold" : asksCurrency ? "currency" : "news";
        final String queryLocation = city;
        final String sportsLeague = selectedLeague;
        setStatus("جاري جلب المعلومات من مصدر مباشر…");
        network.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(INFO_API_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(25000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                String sessionToken = DeviceAuth.getSessionToken(this);
                connection.setRequestProperty("Authorization", "Bearer " + sessionToken);
                JSONObject payload = new JSONObject();
                payload.put("type", type);
                payload.put("locale", speechLanguage);
                if ("sports".equals(type)) {
                    payload.put("league", sportsLeague);
                    java.text.SimpleDateFormat dayFormat = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US);
                    payload.put("date", dayFormat.format(new java.util.Date()));
                }
                if ("time".equals(type) || "weather".equals(type) || "news".equals(type)) payload.put("location", queryLocation);
                if ("currency".equals(type)) { payload.put("from", "USD"); payload.put("to", "EGP"); }
                if ("gold".equals(type)) payload.put("currency", "EGP");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                java.io.InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[2048]; int count;
                while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count);
                JSONObject result = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                String answer;
                if (code != 200 || !result.optBoolean("ok", false)) {
                    String error = result.optString("error", "");
                    if ("location_not_found".equals(error) || "unknown_city_or_timezone".equals(error)) {
                        answer = "لم أتعرف على اسم المدينة أو المنطقة الزمنية. اذكر مدينة معروفة مثل القاهرة أو لندن.";
                    } else if ("gold_providers_unavailable".equals(error) || ("gold".equals(type) && code >= 500)) {
                        answer = "تعذر الوصول إلى مصادر أسعار الذهب الآن. لم أستخدم سعراً قديماً أو تقديرياً غير موثوق. حاول مرة أخرى لاحقاً.";
                    } else if ("authentication_failed".equals(error)) {
                        answer = "انتهت جلسة اتصال التلفزيون الآمنة. أعد فتح المساعد وحاول مرة أخرى.";
                    } else if ("rate_limited".equals(error)) {
                        answer = "تم تجاوز حد الطلبات المؤقت. انتظر قليلاً ثم أعد المحاولة.";
                    } else {
                        answer = "تعذر جلب المعلومات المباشرة حاليًا (رمز " + code + "). تحقق من اتصال التلفزيون بالخادم وحاول مرة أخرى.";
                    }
                } else if ("time".equals(type)) {
                    answer = "الوقت الآن في " + queryLocation + ": " + result.optString("localDateTime") +
                            "\nالمنطقة الزمنية: " + result.optString("timezone");
                } else if ("sports".equals(type)) {
                    JSONArray matches = result.optJSONArray("matches");
                    if (matches == null || matches.length() == 0) {
                        answer = "لم يعرض مصدر النتائج مباريات لهذا الدوري في تاريخ اليوم. قد تكون لا توجد مباريات أو أن تغطية المصدر لهذا الدوري غير مكتملة. المصدر: ESPN.";
                    } else {
                        StringBuilder sportsText = new StringBuilder("مباريات " + result.optString("league") + " اليوم:\\n");
                        for (int i = 0; i < matches.length() && i < 15; i++) {
                            JSONObject match = matches.optJSONObject(i);
                            if (match == null) continue;
                            String kickoff = match.optString("dateTime", "");
                            try {
                                java.time.Instant instant = java.time.Instant.parse(kickoff);
                                kickoff = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                                        .withZone(java.time.ZoneId.systemDefault()).format(instant);
                            } catch (Exception ignored) { kickoff = "موعد غير متاح"; }
                            String score = match.isNull("homeScore") || match.isNull("awayScore")
                                    ? "" : " (" + match.optString("homeScore") + " - " + match.optString("awayScore") + ")";
                            sportsText.append("• ").append(match.optString("homeTeam")).append(" × ")
                                    .append(match.optString("awayTeam")).append(score)
                                    .append(" · ").append(kickoff).append(" · ")
                                    .append(match.optString("status")).append("\\n");
                        }
                        sportsText.append("المصدر: ESPN. قد تختلف التغطية حسب الدوري.");
                        answer = sportsText.toString().trim();
                    }
                } else if ("currency".equals(type)) {
                    answer = "سعر الصرف المرجعي: 1 " + result.optString("from") + " = " + result.optString("rate") + " " + result.optString("to") +
                            "\nتاريخ السعر: " + result.optString("date") + "\nالمصدر: Frankfurter. قد تختلف أسعار البنوك والسوق النقدية.";
                } else if ("gold".equals(type)) {
                    JSONObject prices = result.optJSONObject("pricePerGram");
                    answer = "أسعار الذهب الاسترشادية بالجنيه المصري للجرام:" +
                            "\nعيار 24: " + (prices == null ? "—" : prices.optString("24K")) +
                            "\nعيار 21: " + (prices == null ? "—" : prices.optString("21K")) +
                            "\nعيار 18: " + (prices == null ? "—" : prices.optString("18K")) +
                            "\nالمصدر: " + result.optString("source") +
                            (result.optBoolean("stale", false) ? "\nتنبيه: المصدر يعرض آخر سعر حقيقي مخزّن، وليس سعراً لحظياً." : "") +
                            "\nتنبيه: سعر استرشادي لا يشمل المصنعية أو الضرائب أو هامش محلات الذهب.";
                } else if ("news".equals(type)) {
                    StringBuilder news = new StringBuilder("أحدث الأخبار خلال آخر 24 ساعة عن ").append(result.optString("query")).append(":");
                    JSONArray articles = result.optJSONArray("articles");
                    if (articles == null || articles.length() == 0) news.append("\nلم يعثر المصدر على عناوين مناسبة حاليًا.");
                    else for (int i = 0; i < articles.length(); i++) {
                        JSONObject item = articles.optJSONObject(i);
                        if (item != null) news.append("\n").append(i + 1).append(". ").append(item.optString("title"))
                                .append(" — ").append(item.optString("source")).append(" (").append(item.optString("publishedAt")).append(")")
                                .append("\n").append(item.optString("url"));
                    }
                    news.append("\nالمصدر: GDELT Project");
                    answer = news.toString();
                } else {
                    JSONObject current = result.optJSONObject("current");
                    StringBuilder report = new StringBuilder("الطقس الآن في ").append(result.optString("location"))
                            .append("\nالحالة: ").append(current == null ? "غير متاحة" : current.optString("condition"))
                            .append("\nدرجة الحرارة: ").append(current == null ? "—" : current.optString("temperatureC")).append("°C")
                            .append("\nالمحسوسة: ").append(current == null ? "—" : current.optString("feelsLikeC")).append("°C")
                            .append("\nالرطوبة: ").append(current == null ? "—" : current.optString("humidityPercent")).append("%")
                            .append("\nسرعة الرياح: ").append(current == null ? "—" : current.optString("windSpeedKmh")).append(" كم/س");
                    JSONArray forecast = result.optJSONArray("forecast");
                    if (forecast != null) for (int i = 0; i < forecast.length(); i++) {
                        JSONObject day = forecast.optJSONObject(i);
                        if (day != null) report.append("\n").append(day.optString("date")).append(": ")
                                .append(day.optString("condition")).append("، العظمى ")
                                .append(day.optString("highC")).append("° والصغرى ")
                                .append(day.optString("lowC")).append("°");
                    }
                    report.append("\nالمصدر: Open-Meteo · التحديث: ").append(result.optString("observedAt"));
                    answer = report.toString();
                }
                final String finalAnswer = answer;
                runOnUiThread(() -> reply(finalAnswer));
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().startsWith("device_pending_approval:")) {
                    String deviceId = e.getMessage().substring("device_pending_approval:".length());
                    runOnUiThread(() -> reply("تم تسجيل هذا التلفزيون، لكنه ينتظر موافقة المسؤول قبل عرض المعلومات المباشرة. رقم الجهاز: " + deviceId));
                } else {
                    runOnUiThread(() -> reply("تعذر الاتصال بمصدر المعلومات المباشرة. تحقق من الإنترنت وحاول مرة أخرى."));
                }
            } finally { if (connection != null) connection.disconnect(); }
        });
        return true;
    }


    private boolean tryHandleSleepTimerRequest(String raw, String normalized) {
        boolean asksSleepTimer = has(normalized,
                "مؤقت النوم", "مؤقت ايقاف التشغيل", "مؤقت اطفاء التشغيل",
                "مؤقت اطفاء التلفزيون", "مؤقت اغلاق التلفزيون", "وقت النوم",
                "sleep timer", "turn off tv in", "power off timer",
                "اطفي التلفزيون بعد", "اقفل التلفزيون بعد", "اطفاء التلفزيون بعد",
                "اغلق التلفزيون بعد", "خلي التلفزيون يقفل بعد");
        if (!asksSleepTimer) return false;

        // Android has no universal public API for vendor-specific TV sleep timers.
        // Do not claim activation unless the TV confirms it.
        try {
            Intent settings = new Intent(Settings.ACTION_SETTINGS);
            startActivity(settings);
            reply("أمر مؤقت إيقاف التشغيل مفهوم. فتحت إعدادات التلفزيون، لكن أندرويد لا يوفر أمرًا عامًا يسمح للتطبيق بضبط مؤقت الشركة المصنّعة تلقائيًا. افتح قسم النظام أو الطاقة، ثم مؤقت إيقاف التشغيل، واختر 30 دقيقة وأكّد. إذا ظهر المؤقت ضمن لوحة الإعدادات السريعة في تلفزيونك، يمكنك فتحه من هناك. لم يتم تفعيل المؤقت تلقائيًا.");
        } catch (Exception e) {
            reply("تعذر فتح إعدادات التلفزيون. استخدم زر الإعدادات في الريموت، ثم قسم النظام أو الطاقة، ثم مؤقت إيقاف التشغيل واختر المدة المطلوبة. لم يتم تفعيل المؤقت.");
        }
        return true;
    }

    private int parseArabicVolumeNumber(String text) {
        String[] words = {"صفر", "واحد", "واحده", "اثنين", "اتنين", "اثنان",
                "ثلاثه", "ثلاث", "اربعه", "اربع", "خمسه", "خمس",
                "سته", "ست", "سبعه", "سبع", "تمانيه", "ثمانيه", "ثمان",
                "تسعه", "تسع", "عشره", "عشر", "حداشر", "احداشر",
                "اتناشر", "اثناشر", "تلتاشر", "ثلاثتاشر", "اربعتاشر",
                "خمستاشر", "ستاشر", "سبعتاشر", "تمانطاشر", "تمنتاشر",
                "تسعتاشر", "عشرين"};
        int[] values = {0,1,1,2,2,2,3,3,4,4,5,5,6,6,7,7,8,8,8,9,9,10,10,11,11,12,12,13,13,14,15,16,17,18,18,19,19,20};
        Matcher digits = Pattern.compile("(?:الى|لحد|لغاية|الي)\\s*(\\d{1,2})").matcher(text);
        if (digits.find()) return Integer.parseInt(digits.group(1));
        for (int i=0;i<words.length;i++) {
            if (text.contains("الى " + words[i]) || text.contains("لحد " + words[i])
                    || text.contains("لغاية " + words[i]) || text.contains("الي " + words[i])) return values[i];
        }
        return -1;
    }

    private boolean tryHandleLocalAudioCommand(String normalized) {
        AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        if (audio == null) {
            reply("تعذر الوصول إلى التحكم في الصوت على هذا التلفزيون.");
            return true;
        }
        try {
            if (has(normalized, "الغاء كتم الصوت", "الغاء الصامت", "شغل الصوت", "افتح الصوت", "unmute")) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
                reply("تم إرسال أمر إلغاء كتم الصوت إلى نظام التلفزيون.");
                return true;
            }
            if (has(normalized, "اكتم الصوت", "كتم الصوت", "اجعل الصوت صامت", "صامت", "mute", "silence")) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                reply("تم إرسال أمر كتم الصوت إلى نظام التلفزيون.");
                return true;
            }

            boolean up = has(normalized, "ارفع الصوت", "علي الصوت", "زود الصوت", "اعلي الصوت", "volume up", "increase volume");
            boolean down = has(normalized, "اخفض الصوت", "وطي الصوت", "خفض الصوت", "قلل الصوت", "volume down", "decrease volume", "lower volume");
            if (!up && !down) return false;

            if (audio.isVolumeFixed()) {
                reply("هذا الجهاز يفرض مستوى صوت ثابتًا ولا يسمح للتطبيق بتغييره مباشرة.");
                return true;
            }
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) {
                reply("تعذر قراءة مستوى الصوت من نظام التلفزيون.");
                return true;
            }

            if (has(normalized, "الصوت لصفر", "الصوت الى صفر", "الصوت صفر",
                    "خفض الصوت للصفر", "خفض الصوت الى الصفر", "خفض الصوت الى صفر",
                    "volume to zero", "set volume to zero", "zero volume")) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI);
                reply("تم ضبط مستوى الصوت على صفر.");
                return true;
            }

            int requestedTarget = parseArabicVolumeNumber(normalized);
            if (requestedTarget >= 0) {
                int target = Math.max(0, Math.min(max, requestedTarget));
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
                int actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                reply("تم ضبط مستوى الصوت على " + actual + " من " + max + ".");
                return true;
            }

            String numericCommand = normalized
                    .replace('٠', '0').replace('١', '1').replace('٢', '2')
                    .replace('٣', '3').replace('٤', '4').replace('٥', '5')
                    .replace('٦', '6').replace('٧', '7').replace('٨', '8')
                    .replace('٩', '9')
                    .replace(" بالمية", "%").replace(" في الميه", "%");
            Matcher percent = Pattern.compile("(\\d{1,3})\\s*%").matcher(numericCommand);
            if (percent.find() && has(numericCommand, "الصوت", "volume", "مستوى")) {
                int requested = Math.min(100, Integer.parseInt(percent.group(1)));
                int target = Math.round(max * (requested / 100.0f));
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
                int actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                reply("تم ضبط مستوى الصوت على " + Math.round(actual * 100.0f / max) + "% تقريبًا.");
                return true;
            }

            // Natural "volume up/down" changes by three notches; explicit one-step wording stays one.
            int steps = has(normalized, "درجه واحده", "درجة واحدة", "خطوه واحده", "one step", "one notch") ? 1 : 3;
            int target = up ? Math.min(max, current + steps) : Math.max(0, current - steps);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
            int actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            reply((up ? "تم رفع الصوت إلى " : "تم خفض الصوت إلى ") + actual + " من " + max + ".");
            return true;
        } catch (SecurityException e) {
            reply("النظام منع التطبيق من تغيير مستوى الصوت على هذا الجهاز.");
            return true;
        } catch (Exception e) {
            reply("تعذر تنفيذ أمر الصوت على هذا التلفزيون.");
            return true;
        }
    }

    private boolean isContinuationRequest(String normalized) {
        return has(normalized, "كمل", "اكمل", "تابع", "باقي الاجابه", "باقي الاجابة",
                "كمل الاجابه", "كمل الإجابة", "تابع الاجابه", "تابع الإجابة",
                "المزيد", "استكمل", "continue", "go on", "keep going", "more");
    }

    private String buildConversationContext(String message) {
        if (!isContinuationRequest(normalize(message))) return message;
        android.content.SharedPreferences history =
                getSharedPreferences("assistant_conversation", MODE_PRIVATE);
        String previousQuestion = history.getString("last_question", "");
        String previousAnswer = history.getString("last_answer", "");
        if (previousQuestion.trim().isEmpty() || previousAnswer.trim().isEmpty()) return message;
        if (previousAnswer.length() > 6500)
            previousAnswer = previousAnswer.substring(previousAnswer.length() - 6500);
        return "FOLLOW_UP_TO_PREVIOUS_TURN. The user is asking you to continue the previous answer, NOT starting an unrelated new topic.\\n"
                + "Previous user question: " + previousQuestion + "\\n"
                + "Previous assistant answer so far: " + previousAnswer + "\\n"
                + "Current user follow-up: " + message + "\\n"
                + "Continue with the missing remaining information. Avoid repeating what was already answered unless needed for continuity.";
    }

    // Handle explicit, unambiguous launch/settings commands locally before contacting the AI provider.
    // This keeps basic TV controls available even when the provider quota is exhausted.
    private boolean tryHandleLocalOpenCommand(String raw, String normalized) {
        boolean hasOpenVerb = has(normalized, "افتح", "فتح", "شغل", "شغللي", "شغل لي",
                "launch", "open", "start", "run", "go to");
        if (!hasOpenVerb) return false;

        if (has(normalized, "اعدادات vpn", "اعدادات ال vpn", "vpn settings", "افتح vpn", "شغل vpn")) {
            openVpnSettingsOrApp();
            return true;
        }
        if (has(normalized, "اعدادات الواي فاي", "اعدادات الشبكه", "اعدادات الشبكة",
                "wifi settings", "wi-fi settings", "network settings", "internet settings")) {
            launchIntentWithFallback(new Intent(Settings.ACTION_WIFI_SETTINGS),
                    new Intent(Settings.ACTION_SETTINGS), "Network settings",
                    "فتحت إعدادات الشبكة. / Network settings opened.");
            return true;
        }
        if (has(normalized, "خيارات المطور", "اعدادات المطور", "developer options")) {
            launchIntentWithFallback(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                    new Intent(Settings.ACTION_SETTINGS), "Developer options",
                    "فتحت خيارات المطورين إن كانت متاحة. / Developer options opened if available.");
            return true;
        }
        if (has(normalized, "اعدادات اللغه", "اعدادات اللغة", "تغيير اللغه", "تغيير اللغة",
                "language settings")) {
            launchIntentWithFallback(new Intent(Settings.ACTION_LOCALE_SETTINGS),
                    new Intent(Settings.ACTION_SETTINGS), "Language settings",
                    "فتحت إعدادات اللغة إن كانت متاحة. / Language settings opened if available.");
            return true;
        }
        if (has(normalized, "اعدادات الشاشه", "اعدادات الشاشة", "اعدادات العرض",
                "display settings", "screen settings")) {
            launchIntentWithFallback(new Intent(Settings.ACTION_DISPLAY_SETTINGS),
                    new Intent(Settings.ACTION_SETTINGS), "Display settings",
                    "فتحت إعدادات الشاشة إن كانت متاحة. / Display settings opened if available.");
            return true;
        }
        if (has(normalized, "اعدادات", "الاعدادات", "الإعدادات", "الضبط", "settings")) {
            launchIntent(new Intent(Settings.ACTION_SETTINGS), "Settings");
            return true;
        }

        if (has(normalized, "يوتيوب", "youtube")) {
            // Preserve the existing dedicated YouTube search flow for content queries.
            if (has(normalized, "ابحث", "دور على", "فيديو", "اغنيه", "اغاني", "قناه", "search")) return false;
            launchYouTube();
            return true;
        }

        String target = normalized;
        String[] remove = {"من فضلك", "لو سمحت", "من فضلك", "تطبيق", "برنامج",
                "افتح", "فتح", "شغل لي", "شغللي", "شغل", "launch", "open", "start", "run", "go to"};
        for (String word : remove) target = target.replace(word, " ");
        target = target.replaceAll("\\s+", " ").trim();
        if (target.isEmpty()) return false;

        if (has(target, "داونلودر", "داون لودر", "دانلودر", "داندودا", "downloader")) {
            launchPackage("com.esaba.downloader", "Downloader");
            return true;
        }
        if (has(target, "جوجل بلاي", "google play", "play store")) {
            launchPackage("com.android.vending", "Google Play");
            return true;
        }

        JSONArray apps = getInstalledLaunchableAppsJson();
        String matchedPackage = null;
        String matchedName = null;
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app == null) continue;
            String appName = normalize(app.optString("name", ""));
            String packageName = app.optString("packageName", "");
            if (appName.length() < 3 || packageName.isEmpty()) continue;
            if (target.equals(appName) || (" " + target + " ").contains(" " + appName + " ")) {
                if (matchedPackage != null && !matchedPackage.equals(packageName)) {
                    reply("وجدت أكثر من تطبيق يطابق الاسم. اكتب الاسم الكامل للتطبيق كما يظهر في قائمة التطبيقات.");
                    return true;
                }
                matchedPackage = packageName;
                matchedName = app.optString("name", packageName);
            }
        }
        if (matchedPackage != null) {
            launchPackage(matchedPackage, matchedName);
            return true;
        }
        return false;
    }

    protected void sendToAi(String message) {
        if (aiRequestInProgress) {
            reply("هناك طلب جارٍ بالفعل. انتظر الرد الحالي قبل إرسال سؤال آخر / Please wait for the current answer before sending another request.");
            return;
        }
        aiRequestInProgress = true;
        String normalizedRequest = normalize(message);
        final String originalMessage = message;
        final String contextualMessage = buildConversationContext(message);
        if (tryHandleLocalOpenCommand(message, normalizedRequest)) { aiRequestInProgress = false; return; }
        if (tryHandleSleepTimerRequest(message, normalizedRequest)) { aiRequestInProgress = false; return; }
        if (tryHandleLocalAudioCommand(normalizedRequest)) { aiRequestInProgress = false; return; }
        if (tryHandleLiveInfo(message, normalizedRequest)) { aiRequestInProgress = false; return; }
        if (tryHandleYouTubeSearch(message, normalizedRequest)) { aiRequestInProgress = false; return; }
        if (has(normalizedRequest,
                "التطبيقات اللي شغاله في الخلفيه", "التطبيقات الشغاله في الخلفيه",
                "تطبيقات الخلفيه", "ايه التطبيقات المفتوحه", "التطبيقات المفتوحه",
                "background apps", "apps running in background", "which apps are running")) {
            reply(getRecentAppsReport());
            aiRequestInProgress = false;
            return;
        }
        setStatus("Preparing request… / تجهيز الطلب…");
        network.execute(() -> {
            HttpURLConnection connection = null;
            String stage = "opening_connection";
            try {
                runOnUiThread(() -> setStatus("Connecting to server… / الاتصال بالخادم…"));
                connection = (HttpURLConnection) new URL(API_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(45000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                String sessionToken = DeviceAuth.getSessionToken(this);
                connection.setRequestProperty("Authorization", "Bearer " + sessionToken);
                JSONObject payload = new JSONObject();
                // App inventory is useful for app-specific commands, but wastes input tokens on unrelated questions.
                String normalizedContextRequest = normalize(contextualMessage);
                boolean needsAppInventory = has(normalizedContextRequest,
                        "افتح", "شغل", "تشغيل", "تطبيق", "التطبيقات", "برنامج", "برامج",
                        "مثبت", "مثبته", "صلاحيات", "اذونات", "فحص التطبيقات", "مراجعه التطبيقات",
                        "app", "apps", "installed", "launch", "package", "audit");
                String messageWithContext = contextualMessage;
                if (needsAppInventory) {
                    JSONArray installedApps = getInstalledLaunchableAppsJson();
                    StringBuilder appContext = new StringBuilder("\n\nACTUAL_INSTALLED_APPS_ON_THIS_TV. Use only exact packageName values below for launch_app; never guess package names. If the requested app is not listed, say so.\n");
                    int contextLimit = Math.max(0, 2200 - contextualMessage.length());
                    for (int i = 0; i < installedApps.length() && i < 60; i++) {
                        JSONObject app = installedApps.optJSONObject(i);
                        if (app == null) continue;
                        String line = app.optString("name") + " | " + app.optString("packageName") + "\n";
                        if (appContext.length() + line.length() > contextLimit) break;
                        appContext.append(line);
                    }
                    messageWithContext += appContext.toString();
                }
                payload.put("message", messageWithContext);
                stage = "sending_request";
                runOnUiThread(() -> setStatus("Sending command… / إرسال الأمر…"));
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                stage = "waiting_for_server";
                runOnUiThread(() -> setStatus("Waiting for AI response… / انتظار رد المساعد…"));
                int code = connection.getResponseCode();
                java.io.InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[2048]; int count;
                while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count);
                JSONObject result = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                if (code != 200) {
                    String error = result.optString("error", "http_" + code);
                    String retryAfter = result.optString("retryAfter", "");
                    String providerMessage = result.optString("providerMessage", "");
                    String remainingRequests = result.optString("remainingRequests", "");
                    String remainingTokens = result.optString("remainingTokens", "");
                    String resetRequests = result.optString("resetRequests", "");
                    String resetTokens = result.optString("resetTokens", "");
                    String messageText = "تعذر الاتصال بالمساعد: " + error + " (" + code + ")";
                    if (code == 429 && "provider_rate_limited".equals(error)) {
                        messageText = "Groq رفض الطلب بسبب أحد حدود الاستخدام. ";
                        if (!providerMessage.isEmpty() && !"null".equals(providerMessage)) messageText += providerMessage + " ";
                        if (!retryAfter.isEmpty() && !"null".equals(retryAfter)) messageText += "وقت الانتظار: " + retryAfter + ". ";
                        if (!remainingRequests.isEmpty() && !"null".equals(remainingRequests)) messageText += "الطلبات المتبقية: " + remainingRequests + ". ";
                        if (!remainingTokens.isEmpty() && !"null".equals(remainingTokens)) messageText += "التوكنات المتبقية: " + remainingTokens + ". ";
                        if (!resetRequests.isEmpty() && !"null".equals(resetRequests)) messageText += "إعادة ضبط الطلبات: " + resetRequests + ". ";
                        if (!resetTokens.isEmpty() && !"null".equals(resetTokens)) messageText += "إعادة ضبط التوكنات: " + resetTokens + ". ";
                    } else if (code == 429 && "rate_limited".equals(error)) {
                        messageText = "تم إرسال طلبات متقاربة جدًا. انتظر قليلًا ثم حاول مرة أخرى.";
                    }
                    final String userMessage = messageText;
                    runOnUiThread(() -> reply(userMessage));
                    return;
                }
                String answer = result.optString("text", "");
                JSONArray calls = result.optJSONArray("toolCalls");
                runOnUiThread(() -> {
                    if (!answer.isEmpty()) {
                        android.content.SharedPreferences history =
                                getSharedPreferences("assistant_conversation", MODE_PRIVATE);
                        boolean continuation = isContinuationRequest(normalize(originalMessage));
                        String previousQuestion = history.getString("last_question", "");
                        String previousAnswer = history.getString("last_answer", "");
                        String savedQuestion = continuation && !previousQuestion.isEmpty()
                                ? previousQuestion : originalMessage;
                        String savedAnswer = continuation && !previousAnswer.isEmpty()
                                ? previousAnswer + "\\n\\n" + answer : answer;
                        if (savedAnswer.length() > 9000)
                            savedAnswer = savedAnswer.substring(savedAnswer.length() - 9000);
                        history.edit().putString("last_question", savedQuestion)
                                .putString("last_answer", savedAnswer).apply();
                        reply(answer);
                    } else if (calls == null || calls.length() == 0) {
                        reply("وصل الرد من الذكاء الاصطناعي دون أمر.");
                    }
                    if (calls != null) executeAiTools(calls);
                });
            } catch (Exception e) {
                String detail = stage + ": " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage());
                if (e.getMessage() != null && e.getMessage().startsWith("device_pending_approval:")) {
                    String deviceId = e.getMessage().substring("device_pending_approval:".length());
                    runOnUiThread(() -> reply("تم تسجيل هذا التلفزيون بأمان، لكنه ينتظر موافقة المسؤول قبل تفعيل المساعد. رقم الجهاز: " + deviceId));
                } else {
                    runOnUiThread(() -> reply("خطأ اتصال: " + detail));
                }
            } finally {
                if (connection != null) connection.disconnect();
                aiRequestInProgress = false;
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
                    if ("vpn".equals(page)) {
                        openVpnSettingsOrApp();
                    } else {
                        Intent intent;
                        if ("wifi".equals(page)) intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                        else if ("language".equals(page)) intent = new Intent(Settings.ACTION_LOCALE_SETTINGS);
                        else if ("display".equals(page)) intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                        else if ("screensaver".equals(page)) intent = new Intent(Settings.ACTION_DREAM_SETTINGS);
                        else if ("developer".equals(page)) intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        else intent = new Intent(Settings.ACTION_SETTINGS);
                        launchIntent(intent, "Settings");
                    }
                } else if ("list_installed_apps".equals(name)) {
                    showInstalledApps();
                } else if ("get_storage_info".equals(name)) {
                    reply(getStorageReport());
                } else if ("audit_apps".equals(name)) {
                    reply(auditInstalledApps());
                } else if ("open_usage_access".equals(name)) {
                    openUsageAccessSettings();
                } else if ("open_app_details".equals(name)) {
                    String pkg = args.optString("packageName", "");
                    if (isInstalledPackage(pkg)) {
                        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
                        launchIntent(details, "App details");
                    } else reply("التطبيق غير موجود ضمن التطبيقات المثبتة التي أستطيع التحقق منها.");
                } else if ("open_app_management".equals(name)) {
                    launchIntent(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), "App management");
                } else if ("clear_own_cache".equals(name)) {
                    long before = directorySize(getCacheDir());
                    clearDirectory(getCacheDir());
                    long after = directorySize(getCacheDir());
                    reply("تم تنظيف كاش المساعد فقط. المساحة التي تم تحريرها تقريبًا: " + formatBytes(Math.max(0, before - after)) + ". لا يشمل ذلك كاش التطبيقات الأخرى.");
                } else if ("get_device_info".equals(name)) {
                    reply("الجهاز: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE);
                } else if ("get_memory_info".equals(name)) {
                    reply(getMemoryReport());
                } else if ("check_app_installed".equals(name)) {
                    reply(checkAppInstalled(args.optString("appName", "")));
                } else if ("get_last_used_app".equals(name)) {
                    reply(getLastUsedAppReport());
                } else if ("launch_last_used_app".equals(name)) {
                    launchLastUsedApp();
                } else if ("get_foreground_app".equals(name)) {
                    reply(getForegroundAppReport());
                }
            } catch (Exception e) { reply("تعذر تنفيذ الأمر على هذا التلفزيون."); }
        }
    }


    private JSONArray getInstalledLaunchableAppsJson() {
        JSONArray apps = new JSONArray();
        Map<String, String> found = new LinkedHashMap<>();
        PackageManager pm = getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory("android.intent.category.LEANBACK_LAUNCHER");
        for (ResolveInfo info : pm.queryIntentActivities(leanback, 0)) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            CharSequence label = info.loadLabel(pm);
            if (pkg != null && label != null) found.put(pkg, label.toString());
        }
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            CharSequence label = info.loadLabel(pm);
            if (pkg != null && label != null && !found.containsKey(pkg)) found.put(pkg, label.toString());
        }
        for (Map.Entry<String, String> entry : found.entrySet()) {
            JSONObject item = new JSONObject();
            try {
                item.put("name", entry.getValue());
                item.put("packageName", entry.getKey());
                apps.put(item);
            } catch (Exception ignored) {}
        }
        return apps;
    }

    private void showInstalledApps() {
        JSONArray apps = getInstalledLaunchableAppsJson();
        if (apps.length() == 0) {
            reply("لم أجد تطبيقات قابلة للتشغيل في قائمة التلفزيون.");
            return;
        }
        StringBuilder list = new StringBuilder("التطبيقات المتاحة على هذا التلفزيون (")
                .append(apps.length()).append("):\n");
        int limit = Math.min(apps.length(), 45);
        for (int i = 0; i < limit; i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app != null) list.append("• ").append(app.optString("name"))
                    .append("  [").append(app.optString("packageName")).append("]\n");
        }
        if (apps.length() > limit) list.append("وباقي التطبيقات: ").append(apps.length() - limit);
        reply(list.toString());
    }


    private boolean isInstalledPackage(String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return false;
        try { getPackageManager().getPackageInfo(pkg, 0); return true; }
        catch (Exception e) { return false; }
    }

    private String getStorageReport() {
        try {
            StatFs stats = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = stats.getTotalBytes();
            long available = stats.getAvailableBytes();
            long used = Math.max(0, total - available);
            return "تحليل مساحة التخزين الداخلية:\nالمستخدم: " + formatBytes(used)
                    + "\nالمتاح: " + formatBytes(available)
                    + "\nالإجمالي: " + formatBytes(total)
                    + "\nالنسبة المستخدمة: " + (total > 0 ? (used * 100 / total) : 0) + "%"
                    + "\nهذا قياس للمساحة فقط، وليس فحصًا لمحتويات كل الملفات.";
        } catch (Exception e) {
            return "تعذر قراءة إحصاءات مساحة التخزين على هذا الجهاز.";
        }
    }

    private String auditInstalledApps() {
        PackageManager pm = getPackageManager();
        StringBuilder report = new StringBuilder("مراجعة أولية للتطبيقات المثبتة القابلة للتشغيل:\n");
        int scanned = 0, flagged = 0;
        try {
            JSONArray visibleApps = getInstalledLaunchableAppsJson();
            for (int i = 0; i < visibleApps.length(); i++) {
                JSONObject app = visibleApps.optJSONObject(i);
                if (app == null) continue;
                String packageName = app.optString("packageName", "");
                if (packageName.isEmpty()) continue;
                PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
                if (pi.applicationInfo == null || (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                scanned++;
                ArrayList<String> risky = new ArrayList<>();
                if (pi.requestedPermissions != null) {
                    for (String permission : pi.requestedPermissions) {
                        try {
                            PermissionInfo info = pm.getPermissionInfo(permission, 0);
                            if ((info.protectionLevel & PermissionInfo.PROTECTION_DANGEROUS) != 0) risky.add(permission);
                        } catch (Exception ignored) {}
                    }
                }
                if (risky.size() >= 4) {
                    flagged++;
                    report.append("مراجعة إضافية مطلوبة: ").append(pi.applicationInfo.loadLabel(pm)).append(" — أذونات حساسة متعددة: ");
                    for (int j = 0; j < Math.min(4, risky.size()); j++) {
                        if (j > 0) report.append(", ");
                        report.append(risky.get(j));
                    }
                    report.append("\n");
                }
            }
        } catch (Exception e) {
            return "تعذر إكمال مراجعة التطبيقات القابلة للتشغيل. قد يقيّد إصدار Android الوصول إلى بعض التطبيقات.";
        }
        report.append("عدد تطبيقات المستخدم القابلة للتشغيل التي تمت مراجعتها: ").append(scanned)
                .append("\nتطبيقات تحتاج مراجعة إضافية حسب الأذونات: ").append(flagged)
                .append("\nتنبيه: تتم مراجعة التطبيقات التي يعرضها Android كقابلة للتشغيل فقط. الأذونات الحساسة لا تثبت وجود فيروس؛ هذا تدقيق مبدئي وليس مضاد فيروسات أو فحصًا للملفات.");
        return report.toString();
    }

    private void openUsageAccessSettings() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("pending_usage_check", true).apply();
        try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); reply("افتح Mohamed TV AI Assistant وفعّل السماح بالوصول إلى بيانات الاستخدام، ثم ارجع إلى التطبيق."); }
        catch (Exception e) { getPreferences(MODE_PRIVATE).edit().putBoolean("pending_usage_check", false).apply(); reply("تعذر فتح صفحة Usage Access. افتح إعدادات الجهاز ثم التطبيقات ثم Special app access ثم Usage access إن كانت متاحة."); }
    }
    private void showRemoteButtonSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("remote_key_probe", MODE_PRIVATE);
        String mappedName = prefs.getString("mapped_key_name", "لم يتم اختيار زر");
        boolean enabled = prefs.getBoolean("mapping_enabled", false);
        String state = enabled ? "التخصيص مفعّل" : "التخصيص متوقف";

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(28, 12, 28, 12);

        TextView summary = label("الزر المختار: " + mappedName + "\nالحالة: " + state
                + "\n\nالخطوة 1: اضغط «اكتشاف زر» ثم اضغط الزر المطلوب في الريموت مرة واحدة.\nالخطوة 2: ارجع لهذه القائمة واضغط «تفعيل الزر المختار للمساعد».\n\nاكتشاف الزر لا يفعّله تلقائيًا. بعض أزرار الريموت قد يمنع نظام التلفزيون رصدها.");
        menu.addView(summary);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("تخصيص أزرار الريموت")
                .setView(menu)
                .setNegativeButton("إغلاق", (d, w) -> d.dismiss())
                .create();

        Button discover = button("1- اكتشاف زر من الريموت / Discover Key");
        discover.setOnClickListener(v -> { dialog.dismiss(); beginRemoteKeyCapture(); });
        menu.addView(discover);

        Button enable = button("2- تفعيل الزر المختار للمساعد / Enable");
        enable.setOnClickListener(v -> {
            int key = prefs.getInt("captured_key_code", -1);
            if (key < 0) {
                reply("لم يتم اكتشاف زر بعد. اختر اكتشاف زر أولاً ثم اضغط الزر المطلوب في الريموت.");
                return;
            }
            if (isProtectedRemoteSystemKey(key)) {
                prefs.edit().putBoolean("mapping_enabled", false).putInt("mapped_key_code", -1)
                        .putString("mapped_key_name", "لم يتم اختيار زر").apply();
                reply("هذا زر أساسي في التلفزيون ولا يمكن تخصيصه للمساعد حفاظًا على التحكم الطبيعي. اختر زرًا آخر غير أساسي.");
                return;
            }
            String name = prefs.getString("captured_key_name", "KEYCODE_" + key);
            prefs.edit().putInt("mapped_key_code", key)
                    .putString("mapped_key_name", name)
                    .putBoolean("mapping_enabled", true).apply();
            dialog.dismiss();
            reply("تم تفعيل ربط " + name + " بالمساعد. أزرار التنقل والتأكيد والرجوع والرئيسية والصوت والمساعد الصوتي محمية ولا يمكن اعتراضها.");
        });
        menu.addView(enable);

        Button disable = button("3- إيقاف التخصيص واستعادة الوظيفة الأصلية / Disable");
        disable.setOnClickListener(v -> {
            prefs.edit().putBoolean("mapping_enabled", false).apply();
            dialog.dismiss();
            reply("تم إيقاف اعتراض الزر. سيُترك للريموت والنظام تنفيذ وظيفتهما الأصلية.");
        });
        menu.addView(disable);

        Button accessibility = button("4- فتح إعدادات Accessibility");
        accessibility.setOnClickListener(v -> { dialog.dismiss(); openRemoteAccessibilitySettings(); });
        menu.addView(accessibility);

        Button last = button("5- عرض آخر زر تم رصده / Last Key");
        last.setOnClickListener(v -> {
            String name = prefs.getString("last_key_name", "لم يتم رصد زر بعد");
            int code = prefs.getInt("last_key_code", -1);
            reply("آخر زر مرصود: " + name + " · KEYCODE " + code);
        });
        menu.addView(last);

        dialog.show();
        dialog.getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.82),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private boolean isProtectedRemoteSystemKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_ASSIST
                || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST
                || keyCode == KeyEvent.KEYCODE_SEARCH
                || keyCode == KeyEvent.KEYCODE_APP_SWITCH
                || keyCode == KeyEvent.KEYCODE_TV_INPUT
                || keyCode == KeyEvent.KEYCODE_GUIDE
                || keyCode == KeyEvent.KEYCODE_SETTINGS;
    }

    private boolean isRemoteKeyAccessibilityEnabled() {
        // Read Android's enabled-service registry directly. Some TV firmware
        // filters AccessibilityManager's feedback list and hides valid services.
        android.content.ComponentName expected =
                new android.content.ComponentName(this, RemoteKeyProbeService.class);
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) return false;

        android.text.TextUtils.SimpleStringSplitter splitter =
                new android.text.TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        String expectedShort = expected.flattenToShortString();
        String expectedFull = expected.flattenToString();
        for (String item : splitter) {
            if (expectedShort.equalsIgnoreCase(item)
                    || expectedFull.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
    }

    private void beginRemoteKeyCapture() {
        if (!isRemoteKeyAccessibilityEnabled()) {
            getSharedPreferences("remote_key_probe", MODE_PRIVATE).edit()
                    .putBoolean("capture_armed", false).apply();
            reply("خدمة اكتشاف أزرار الريموت غير مفعّلة بعد. فعّل Mohamed TV AI Remote Key Test من إعدادات تسهيل الاستخدام، ثم ارجع للتطبيق واضغط اكتشاف زر مرة أخرى.");
            openRemoteAccessibilitySettings();
            return;
        }
        getSharedPreferences("remote_key_probe", MODE_PRIVATE).edit()
            .putBoolean("capture_armed", true).remove("captured_key_code").remove("captured_key_name").apply();
        reply("خدمة اكتشاف الأزرار مفعّلة. اضغط الزر المطلوب من الريموت مرة واحدة، ثم ارجع للتطبيق لعرض النتيجة. لن نفتح إعدادات تسهيل الاستخدام مرة أخرى. إذا لم يظهر شيء فقد يمنع النظام رصد هذا الزر.");
    }

    private void openRemoteAccessibilitySettings() {
        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch (Exception e) { reply("تعذر فتح إعدادات Accessibility. افتح إعدادات الجهاز ثم Accessibility يدويًا."); }
    }

    private void enableFloatingAssistant() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("pending_overlay_enable", true).apply();
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); reply("اسمح بالظهور فوق التطبيقات الأخرى، ثم ارجع إلى المساعد."); }
            catch (Exception e) { reply("إعداد الظهور فوق التطبيقات غير متاح على هذا التلفزيون."); }
            return;
        }
        startFloatingService();
    }
    private void startFloatingService() {
        try {
            getSharedPreferences("floating_assistant", MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
            Intent serviceIntent = new Intent(this, FloatingBubbleService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent);
            else startService(serviceIntent);
            reply("أرسلت طلب تشغيل الزر العائم. إذا لم يظهر، فقد يمنع نظام التلفزيون خدمة الظهور فوق التطبيقات أو الخدمة الأمامية.");
        } catch (SecurityException | IllegalStateException e) {
            reply("رفض Android تشغيل الخدمة العائمة. تحقق من إذن الظهور فوق التطبيقات وإعدادات النظام.");
        } catch (Exception e) {
            reply("تعذر إرسال طلب تشغيل الزر العائم على هذا الجهاز.");
        }
    }
    @Override protected void onResume() {
        super.onResume();
        refreshShizukuState();
        android.content.SharedPreferences probe = getSharedPreferences("remote_key_probe", MODE_PRIVATE);
        int lastKeyCode = probe.getInt("last_key_code", -1);
        if (lastKeyCode >= 0) {
            String keyName = probe.getString("last_key_name", "UNKNOWN");
            setStatus("آخر زر مرصود: " + keyName + " (KEYCODE " + lastKeyCode + ")");
        }
        if (probe.getBoolean("capture_notice_pending", false) && probe.getInt("captured_key_code", -1) >= 0) {
            probe.edit().putBoolean("capture_notice_pending", false).apply();
            reply("تم اكتشاف الزر: " + probe.getString("captured_key_name", "UNKNOWN") + " · KEYCODE " + probe.getInt("captured_key_code", -1) + ". يمكنك الآن تفعيله من تخصيص أزرار الريموت.");
        }
        if (getPreferences(MODE_PRIVATE).getBoolean("pending_usage_check", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("pending_usage_check", false).apply();
            if (hasUsageAccess()) reply("تم منح Usage Access بنجاح. يمكنك الآن طلب التطبيقات التي استخدمها النظام مؤخرًا.");
            else reply("ما زال Usage Access غير مفعّل. لم يتم منح الإذن بعد؛ فعّل السماح لهذا التطبيق في صفحة Usage Access.");
        }
        if (getPreferences(MODE_PRIVATE).getBoolean("pending_overlay_enable", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("pending_overlay_enable", false).apply();
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) startFloatingService();
            else reply("لم يتم منح إذن الظهور فوق التطبيقات؛ لم يتم تشغيل الزر العائم.");
        }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && "com.tvai.assistant.REMOTE_KEY_CAPTURED".equals(intent.getAction())) {
            int keyCode = intent.getIntExtra("captured_key_code", -1);
            String keyName = intent.getStringExtra("captured_key_name");
            if (keyCode >= 0 && keyName != null) {
                reply("تم رصد زر الريموت: " + keyName + " · KEYCODE " + keyCode
                        + ". ارجع إلى تخصيص أزرار الريموت واضغط تفعيل الزر المختار.");
            }
            return;
        }
        if (intent != null && intent.getBooleanExtra("start_voice", false)) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> startListening(), 500);
        }
    }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager ops = (AppOpsManager)getSystemService(Context.APP_OPS_SERVICE);
            int uid = getPackageManager().getApplicationInfo(getPackageName(), 0).uid;
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, getPackageName());
            if (mode == AppOpsManager.MODE_ALLOWED) return true;
            if (Build.VERSION.SDK_INT >= 29) {
                return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, getPackageName())
                        == AppOpsManager.MODE_ALLOWED;
            }
            return false;
        } catch (Exception e) { return false; }
    }
    private String getRecentAppsReport() {
        if (!hasUsageAccess()) {
            return "تعذر على النظام تأكيد Usage Access لهذا التطبيق. افتح صفحة الإذن، وتأكد أن المفتاح الخاص بـ Mohamed TV AI Assistant مفعّل، ثم ارجع وأعد المحاولة.";
        }
        try {
            UsageStatsManager manager = (UsageStatsManager)getSystemService(Context.USAGE_STATS_SERVICE);
            long end = System.currentTimeMillis();
            long start = end - 7L * 24L * 60L * 60L * 1000L;
            java.util.List<UsageStats> list = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
            if (list == null || list.isEmpty()) {
                return "صلاحية Usage Access مفعّلة، لكن النظام لم يُرجع سجل استخدام خلال آخر 7 أيام.";
            }
            Map<String, UsageStats> latestByPackage = new LinkedHashMap<>();
            for (UsageStats item : list) {
                if (item == null || item.getLastTimeUsed() <= 0 || getPackageName().equals(item.getPackageName())) continue;
                UsageStats old = latestByPackage.get(item.getPackageName());
                if (old == null || item.getLastTimeUsed() > old.getLastTimeUsed()) latestByPackage.put(item.getPackageName(), item);
            }
            java.util.List<UsageStats> recent = new ArrayList<>(latestByPackage.values());
            java.util.Collections.sort(recent, (a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));
            if (recent.isEmpty()) {
                return "صلاحية Usage Access مفعّلة، لكن لا توجد بيانات استخدام متاحة حتى الآن.";
            }
            StringBuilder report = new StringBuilder("صلاحية Usage Access مفعّلة. هذه التطبيقات التي سُجّل استخدامها مؤخرًا خلال آخر 7 أيام، مرتبة من الأحدث:\n");
            int count = Math.min(10, recent.size());
            for (int i = 0; i < count; i++) {
                UsageStats item = recent.get(i);
                String label = item.getPackageName();
                try {
                    label = getPackageManager().getApplicationLabel(
                        getPackageManager().getApplicationInfo(item.getPackageName(), 0)).toString();
                } catch (Exception ignored) {}
                long age = Math.max(0, end - item.getLastTimeUsed());
                String when;
                if (age < 60L * 1000L) when = "منذ أقل من دقيقة";
                else if (age < 60L * 60L * 1000L) when = "منذ " + (age / (60L * 1000L)) + " دقيقة";
                else if (age < 24L * 60L * 60L * 1000L) when = "منذ " + (age / (60L * 60L * 1000L)) + " ساعة";
                else when = "منذ " + (age / (24L * 60L * 60L * 1000L)) + " يوم";
                report.append(i + 1).append(". ").append(label).append(" — ").append(when).append("\n");
            }
            report.append("\nمهم: هذه قائمة بسجل الاستخدام الأخير، وليست كشفًا حيًا بكل التطبيقات التي تعمل في الخلفية. أندرويد لا يتيح للتطبيق العادي معرفة كل العمليات النشطة أو إيقافها. لهذه المهام نحتاج تكامل ADB أو Shizuku بعد إعداد منفصل.");
            return report.toString();
        } catch (Exception e) {
            return "صلاحية Usage Access مفعّلة، لكن تعذرت قراءة سجل الاستخدام من هذا التلفزيون.";
        }
    }

    private JSONObject findLastUsedApp() {
        JSONObject out = new JSONObject();
        if (!hasUsageAccess()) { try { out.put("error", "usage_access_unconfirmed"); } catch (Exception ignored) {} return out; }
        try {
            UsageStatsManager manager = (UsageStatsManager)getSystemService(Context.USAGE_STATS_SERVICE);
            long end = System.currentTimeMillis(), start = end - 7L * 24L * 60L * 60L * 1000L;
            java.util.List<UsageStats> list = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
            UsageStats latest = null;
            if (list != null) for (UsageStats item : list) if (item != null && !getPackageName().equals(item.getPackageName()) && (latest == null || item.getLastTimeUsed() > latest.getLastTimeUsed())) latest = item;
            if (latest == null || latest.getLastTimeUsed() <= 0) { out.put("error", "no_recent_usage"); return out; }
            String label = latest.getPackageName();
            try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(latest.getPackageName(), 0)).toString(); } catch (Exception ignored) {}
            out.put("packageName", latest.getPackageName()); out.put("name", label); out.put("lastTimeUsed", latest.getLastTimeUsed());
        } catch (Exception e) { try { out.put("error", "usage_query_failed"); } catch (Exception ignored) {} }
        return out;
    }
    private String getLastUsedAppReport() {
        JSONObject app = findLastUsedApp(); String error = app.optString("error", "");
        if ("usage_access_unconfirmed".equals(error)) return "تعذر تأكيد صلاحية Usage Access من النظام. راجع الإذن الخاص بهذا التطبيق ثم أعد المحاولة.";
        if (!error.isEmpty()) return "لم أتمكن من تحديد تطبيق استُخدم خلال آخر 7 أيام.";
        return "آخر تطبيق مستخدم خلال آخر 7 أيام: " + app.optString("name") + " (" + app.optString("packageName") + ").";
    }
    private void launchLastUsedApp() {
        JSONObject app = findLastUsedApp(); String error = app.optString("error", "");
        if ("usage_access_unconfirmed".equals(error)) { launchIntent(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), "Usage access"); return; }
        if (!error.isEmpty()) { reply("لم أجد تطبيقًا سابقًا يمكن تشغيله خلال آخر 7 أيام."); return; }
        String pkg = app.optString("packageName", "");
        if (!isInstalledPackage(pkg)) { reply("آخر تطبيق مسجل لم يعد مثبتًا على الجهاز."); return; }
        launchPackage(pkg, app.optString("name", pkg));
    }
    private String getForegroundAppReport() {
        if (!hasUsageAccess()) return "معرفة التطبيق النشط تحتاج صلاحية Usage Access.";
        try {
            UsageStatsManager manager = (UsageStatsManager)getSystemService(Context.USAGE_STATS_SERVICE);
            long end = System.currentTimeMillis();
            android.app.usage.UsageEvents events = manager.queryEvents(end - 30L * 60L * 1000L, end);
            android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            String pkg = null; long newest = 0;
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event); int type = event.getEventType();
                if ((type == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND || (Build.VERSION.SDK_INT >= 29 && type == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED)) && event.getTimeStamp() >= newest) { newest = event.getTimeStamp(); pkg = event.getPackageName(); }
            }
            if (pkg == null) return "لا تتوفر بيانات كافية لتحديد التطبيق النشط الآن.";
            String label = pkg; try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) {}
            return "آخر تطبيق ظهر في الواجهة خلال آخر 30 دقيقة: " + label + " (" + pkg + "). قد تختلف النتيجة حسب نظام التلفزيون.";
        } catch (Exception e) { return "تعذر تحديد التطبيق النشط على هذا الجهاز."; }
    }
    private String checkAppInstalled(String query) {
        if (query == null || query.trim().isEmpty()) return "اذكر اسم التطبيق.";
        String wanted = normalize(query); JSONArray apps = getInstalledLaunchableAppsJson();
        for (int i=0;i<apps.length();i++) { JSONObject app=apps.optJSONObject(i); if (app==null) continue; String name=normalize(app.optString("name")), pkg=normalize(app.optString("packageName")); if (name.equals(wanted)||pkg.equals(wanted)||name.contains(wanted)||wanted.contains(name)) return "نعم، التطبيق موجود وقابل للتشغيل: " + app.optString("name") + " (" + app.optString("packageName") + ")."; }
        return "لم أجد تطبيقًا قابلًا للتشغيل بهذا الاسم في قائمة الجهاز؛ قد يكون مثبتًا دون أيقونة تشغيل.";
    }
    private String getMemoryReport() {
        try { ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE); ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo(); manager.getMemoryInfo(info); long total=info.totalMem, free=info.availMem, used=Math.max(0,total-free);
            return "حالة RAM (تقدير النظام):\nالإجمالي: "+formatBytes(total)+"\nالمستخدم تقريبًا: "+formatBytes(used)+"\nالمتاح: "+formatBytes(free)+"\nنسبة الاستخدام: "+(total>0?used*100/total:0)+"%. هذه قراءة للذاكرة وليست تنظيفًا قسريًا.";
        } catch(Exception e) { return "تعذر قراءة معلومات RAM."; }
    }
    private long directorySize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        long total = 0;
        File[] children = dir.listFiles();
        if (children != null) for (File child : children) total += directorySize(child);
        return total;
    }

    private void clearDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) for (File child : children) {
            if (child.isDirectory()) clearDirectory(child);
            try { child.delete(); } catch (Exception ignored) {}
        }
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        if (bytes >= 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
    }

    private void adjustVolume(int direction, String message) {
        AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        reply(message);
    }


    private void openVpnSettingsOrApp() {
        try {
            Intent settings = new Intent(Settings.ACTION_VPN_SETTINGS);
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settings);
            reply("تم فتح صفحة إعدادات VPN في النظام. إذا لم تعرض التطبيقات المثبتة، استخدم أمر «افتح تطبيق VPN».");
            return;
        } catch (Exception ignored) {}

        JSONArray apps = getInstalledLaunchableAppsJson();
        ArrayList<JSONObject> vpnApps = new ArrayList<>();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app == null) continue;
            String name = normalize(app.optString("name", ""));
            String pkg = normalize(app.optString("packageName", ""));
            if (name.contains("vpn") || pkg.contains("vpn")) vpnApps.add(app);
        }
        if (vpnApps.size() == 1) {
            JSONObject app = vpnApps.get(0);
            launchPackage(app.optString("packageName"), app.optString("name"));
        } else if (vpnApps.size() > 1) {
            String[] labels = new String[vpnApps.size()];
            for (int i = 0; i < vpnApps.size(); i++) labels[i] = vpnApps.get(i).optString("name");
            new AlertDialog.Builder(this).setTitle("اختر تطبيق VPN")
                    .setItems(labels, (dialog, which) -> {
                        JSONObject app = vpnApps.get(which);
                        launchPackage(app.optString("packageName"), app.optString("name"));
                    }).setNegativeButton("إلغاء", null).show();
        } else {
            launchIntentWithFallback(new Intent(Settings.ACTION_WIRELESS_SETTINGS), new Intent(Settings.ACTION_SETTINGS),
                    "VPN settings", "صفحة VPN غير متاحة مباشرة على هذا التلفزيون، ولم أجد تطبيق VPN قابلاً للتشغيل باسمه. افتح إعدادات الشبكة أو شغّل تطبيق VPN من قائمة التطبيقات.");
        }
    }

    private boolean tryHandleYouTubeSearch(String raw, String normalized) {
        boolean mentionsYouTube = has(normalized, "يوتيوب", "youtube");
        boolean contentRequest = has(normalized, "اغنيه", "اغاني", "قناه", "فيديو", "ابحث", "دور على", "شغل", "اعرض", "search");
        if (!mentionsYouTube || !contentRequest) return false;

        String query = normalized;
        String[] remove = {"من اليوتيوب", "على اليوتيوب", "في اليوتيوب", "باليوتيوب", "على youtube", "in youtube", "on youtube",
                "ابحث لي عن", "ابحث عن", "ابحث", "دور على", "دورلي على", "شغل لي", "شغل", "افتح", "اعرض لي", "اعرض",
                "اغنيه", "اغاني", "قناه", "فيديو", "youtube", "يوتيوب", "من فضلك", "لو سمحت"};
        for (String part : remove) query = query.replace(part, " ");
        query = query.replaceAll("\\s+", " ").trim();
        if (query.isEmpty()) {
            launchYouTube();
            return true;
        }

        String url = "https://www.youtube.com/results?search_query=" +
                Uri.encode(query);
        String[] packages = {"com.google.android.youtube.tv", "com.google.android.youtube", "com.google.android.youtube.tvunplugged"};
        for (String pkg : packages) {
            try {
                Intent search = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                search.setPackage(pkg);
                search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(search);
                reply("فتحت نتائج بحث يوتيوب عن: " + query + ". اختر النتيجة المطلوبة؛ تشغيل المحتوى يعتمد على التطبيق والنتائج المتاحة.");
                return true;
            } catch (Exception ignored) {}
        }
        try {
            Intent search = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(search);
            reply("فتحت رابط نتائج البحث عن: " + query + ". إذا لم يفتح داخل تطبيق YouTube، افتح التطبيق وابحث عن العبارة نفسها.");
        } catch (Exception e) {
            reply("تعذر فتح نتائج YouTube على هذا التلفزيون. جرّب فتح YouTube والبحث يدويًا عن: " + query);
        }
        return true;
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

    protected void setStatus(String s) { if (status != null) status.setText(s); }

    protected void reply(String s) {
        String clean = s == null ? "" : s.replace("\\\\n", "\n").replace("\\\\r", "");
        boolean arabic = clean.matches("(?s).*?[\\u0600-\\u06FF].*");
        response.setText(android.text.BidiFormatter.getInstance(arabic).unicodeWrap(clean));
        response.setTextDirection(arabic ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        response.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT);
        response.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        setStatus("Ready · جاهز");
        boolean voiceReplies = getSharedPreferences("assistant_settings", MODE_PRIVATE)
                .getBoolean("tts_enabled", false);
        if (ttsReady && voiceReplies) {
            Locale l = clean.matches("(?s).*?[\\u0600-\\u06FF].*") ? new Locale("ar") : Locale.US;
            tts.setLanguage(l);
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "tvai-reply");
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        android.content.SharedPreferences probe = getSharedPreferences("remote_key_probe", MODE_PRIVATE);
        if (probe.getBoolean("capture_armed", false) && !isProtectedRemoteSystemKey(keyCode)) {
            String keyName = KeyEvent.keyCodeToString(keyCode);
            probe.edit().putInt("captured_key_code", keyCode)
                    .putString("captured_key_name", keyName)
                    .putBoolean("capture_armed", false)
                    .putBoolean("capture_notice_pending", false)
                    .putInt("last_key_code", keyCode)
                    .putString("last_key_name", keyName)
                    .apply();
            reply("تم رصد زر الريموت: " + keyName + " · KEYCODE " + keyCode
                    + ". ارجع إلى تخصيص أزرار الريموت واضغط تفعيل الزر المختار.");
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) { startListening(); return true; }
        return super.onKeyDown(keyCode,event);
    }

    private void refreshShizukuState() {
        runOnUiThread(() -> {
            if (status == null) return;
            try {
                if (!Shizuku.pingBinder()) status.setText("Shizuku غير متصل أو لم يبدأ · ADB المباشر غير مضاف بعد");
                else if (Shizuku.isPreV11()) status.setText("Shizuku يعمل لكن إصداره أقدم من v11 وغير مدعوم");
                else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    int uid = Shizuku.getUid();
                    status.setText("Shizuku مفوض · " + (uid == 0 ? "Root" : uid == 2000 ? "ADB/Shell" : "UID " + uid) + " · ADB المباشر غير مضاف بعد");
                } else status.setText("Shizuku يعمل لكن التفويض غير ممنوح · ADB المباشر غير مضاف بعد");
            } catch (Exception e) { status.setText("تعذر التحقق من Shizuku حاليًا."); }
        });
    }

    private void showShizukuStatus() {
        try {
            if (!Shizuku.pingBinder()) {
                reply("Shizuku غير متصل. ثبّت تطبيق Shizuku وافتحه وابدأ الخدمة. على Android 11 فأحدث يمكن تشغيله من Wireless debugging. ثم ارجع إلى هنا.");
            } else if (Shizuku.isPreV11()) reply("إصدار Shizuku أقدم من v11 وغير مدعوم.");
            else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                int uid = Shizuku.getUid();
                reply("Shizuku متصل والتفويض ممنوح. نوع الصلاحية: " + (uid == 0 ? "Root" : uid == 2000 ? "ADB/Shell" : "UID " + uid) + ". لا يعني ذلك أن كل أوامر النظام مدعومة.");
            } else reply("Shizuku يعمل، لكن التطبيق لم يحصل على التفويض بعد. استخدم زر طلب التفويض.");
        } catch (Exception e) { reply("تعذر قراءة حالة Shizuku."); }
    }

    private void requestShizukuAuthorization() {
        try {
            if (!Shizuku.pingBinder()) { reply("ابدأ خدمة Shizuku أولًا ثم أعد المحاولة."); return; }
            if (Shizuku.isPreV11()) { reply("حدّث Shizuku إلى إصدار v11 أو أحدث."); return; }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { reply("التفويض ممنوح بالفعل."); return; }
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
            setStatus("في انتظار موافقة Shizuku…");
        } catch (Exception e) { reply("تعذر طلب تفويض Shizuku. تأكد أن الخدمة تعمل."); }
    }

    @Override protected void onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(shizukuBinderReceived);
            Shizuku.removeBinderDeadListener(shizukuBinderDead);
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionResult);
        } catch (Exception ignored) {}
        network.shutdownNow();
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
