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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

/** TV starter assistant. Cloud AI and phone pairing are future milestones. */
public class MainActivity extends Activity {
    private TextView status, transcript, response;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String speechLanguage = "ar-EG";
    private Button micButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildUi();
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
        response = label("Assistant / المساعد: Try: Open YouTube, open settings, volume up.");
        root.addView(response);
        root.addView(label("Quick actions / أوامر سريعة"));

        addAction(root, "Open YouTube", "open youtube");
        addAction(root, "Open Google Play", "open play store");
        addAction(root, "Open Settings", "open settings");
        addAction(root, "Volume up", "volume up");
        addAction(root, "Volume down", "volume down");
        addAction(root, "Help / مساعدة", "help");

        TextView foot = label("Version 0.1.1 · Use the remote arrows to move between highlighted buttons. Cloud AI is not connected yet.");
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
                    handleCommand(list.get(0));
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
        String s = raw.toLowerCase(new Locale("ar")).trim();
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replace("أ","ا").replace("إ","ا").replace("آ","ا")
                .replace("ى","ي").replace("ة","ه").replace("ـ","")
                .replaceAll("\\s+", " ");
    }

    private boolean has(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private void handleCommand(String raw) {
        String c = normalize(raw);

        if (has(c, "youtube", "يوتيوب")) { launchYouTube(); return; }
        if (has(c, "play store", "google play", "جوجل بلاي", "متجر")) {
            launchPackage("com.android.vending", "Google Play"); return;
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
        if (has(c, "خفض الاضاءه", "قلل الاضاءه", "قلل السطوع", "خفض السطوع", "brightness")) {
            launchIntent(new Intent(Settings.ACTION_DISPLAY_SETTINGS), "Display settings");
            return;
        }
        if (has(c, "شاشه الاستراحه", "شاشه التوقف", "شاشه الاستراحه", "screensaver", "screen saver")) {
            launchIntent(new Intent(Settings.ACTION_DREAM_SETTINGS), "Screen saver settings");
            return;
        }
        if (has(c, "لغه الجهاز", "غير اللغه", "تغيير اللغه", "اللغه الانجليزيه", "english language", "device language")) {
            launchIntent(new Intent(Settings.ACTION_LOCALE_SETTINGS), "Language settings");
            return;
        }
        if (has(c, "اخر تطبيق", "التطبيق السابق", "last app", "previous app", "ارجع للتطبيق")) {
            reply("Android does not allow this app to reopen the previous app directly. Use the TV remote's Recent Apps button / لا يسمح أندرويد بفتح آخر تطبيق مباشرة من هذا التطبيق؛ استخدم زر التطبيقات الأخيرة في الريموت.");
            return;
        }
        if (has(c, "help", "مساعده", "ساعدني")) {
            reply("Try: افتح يوتيوب، افتح الاعدادات، ارفع الصوت، اخفض الصوت، خفض الاضاءة، شاشة الاستراحة، لغة الجهاز.");
            return;
        }
        reply("I did not recognize a supported command. Cloud AI is not connected yet / لم أفهم الأمر. الاتصال بالذكاء الاصطناعي السحابي لم يُضف بعد.");
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
        catch (Exception e) { reply("Unable to open " + name); }
    }

    private void launchIntent(Intent i, String name) {
        try { startActivity(i); reply("Opening " + name + ". Choose the setting on your TV if needed / جاري فتح " + name); }
        catch (ActivityNotFoundException e) { reply("This settings page is unavailable on this TV / صفحة الإعدادات غير متاحة على هذا التلفزيون."); }
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
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
