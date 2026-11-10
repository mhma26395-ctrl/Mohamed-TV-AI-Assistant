package com.tvai.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
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
import java.util.ArrayList;
import java.util.Locale;

/** First TV shell. AI provider adapters and phone pairing are planned for later milestones. */
public class MainActivity extends Activity {
    private TextView status, transcript, response;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String speechLanguage = "ar-EG";

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
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(56, 34, 56, 34); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(8,17,31)); scroll.addView(root); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Mohamed TV AI Assistant"); title.setTextColor(Color.WHITE); title.setTextSize(34); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        TextView subtitle = new TextView(this); subtitle.setText("Your smart TV voice companion · مساعدك الذكي للتلفزيون"); subtitle.setTextColor(Color.LTGRAY); subtitle.setTextSize(17); root.addView(subtitle);
        status = label("Ready · جاهز للاستماع"); root.addView(status);
        Button mic = button("🎙  Speak / تحدث"); mic.setOnClickListener(v -> startListening()); root.addView(mic);
        Button lang = button("Language: العربية / English"); lang.setOnClickListener(v -> { speechLanguage = speechLanguage.equals("ar-EG") ? "en-US" : "ar-EG"; lang.setText(speechLanguage.equals("ar-EG") ? "Speech: العربية" : "Speech: English"); setStatus("Speech language changed / تم تغيير لغة التعرف على الصوت"); }); root.addView(lang);
        transcript = label("You said / قلت: —"); root.addView(transcript);
        response = label("Assistant / المساعد: Try: Open YouTube, open settings, volume up."); root.addView(response);
        TextView quick = label("Quick actions / أوامر سريعة"); root.addView(quick);
        addAction(root, "Open YouTube", "open youtube");
        addAction(root, "Open Google Play", "open play store");
        addAction(root, "Open Settings", "open settings");
        addAction(root, "Volume up", "volume up");
        addAction(root, "Volume down", "volume down");
        addAction(root, "Help / مساعدة", "help");
        TextView foot = label("Version 0.1.0 · Basic commands work locally. AI cloud connection and phone remote are planned next."); foot.setTextSize(13); root.addView(foot);
    }

    private TextView label(String text) { TextView v = new TextView(this); v.setText(text); v.setTextColor(Color.WHITE); v.setTextSize(18); v.setPadding(12,18,12,18); return v; }
    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setTextSize(19); b.setAllCaps(false); b.setTextColor(Color.WHITE); GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(25,92,145)); bg.setCornerRadius(18); b.setBackground(bg); b.setFocusable(true); b.setPadding(28,10,28,10); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,8,0,8); b.setLayoutParams(p); return b; }
    private void addAction(LinearLayout root, String label, String command) { Button b = button(label); b.setOnClickListener(v -> handleCommand(command)); root.addView(b); }

    private void requestMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestMicPermission(); setStatus("Microphone permission required / نحتاج إذن الميكروفون"); return; }
        if (recognizer == null) { setStatus("Speech recognition is unavailable on this device."); return; }
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) { setStatus("Listening… / أستمع إليك…"); }
            public void onBeginningOfSpeech() {} public void onRmsChanged(float r) {} public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() { setStatus("Processing… / جاري المعالجة…"); }
            public void onError(int e) { setStatus("Could not recognize speech. Try again / لم أتمكن من فهم الصوت، حاول مجددًا."); }
            public void onResults(Bundle b) { ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (list != null && !list.isEmpty()) { transcript.setText("You said / قلت: " + list.get(0)); handleCommand(list.get(0)); } }
            public void onPartialResults(Bundle b) {} public void onEvent(int e, Bundle p) {}
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguage);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLanguage);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak / تحدث الآن");
        recognizer.startListening(i);
    }

    private void handleCommand(String raw) {
        String c = raw.toLowerCase(Locale.ROOT).trim();
        if (c.contains("youtube") || c.contains("يوتيوب")) { launchPackage("com.google.android.youtube.tv", "YouTube"); return; }
        if (c.contains("play store") || c.contains("google play") || c.contains("جوجل بلاي") || c.contains("متجر")) { launchPackage("com.android.vending", "Google Play"); return; }
        if (c.contains("settings") || c.contains("الإعدادات") || c.contains("اعدادات") || c.contains("الضبط")) { launchIntent(new Intent(android.provider.Settings.ACTION_SETTINGS), "Settings"); return; }
        AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        if (c.contains("volume up") || c.contains("ارفع الصوت") || c.contains("علي الصوت")) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); reply("Volume increased / تم رفع الصوت"); return; }
        if (c.contains("volume down") || c.contains("اخفض الصوت") || c.contains("وطي الصوت")) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); reply("Volume decreased / تم خفض الصوت"); return; }
        if (c.equals("help") || c.contains("مساعدة") || c.contains("ساعدني")) { reply("Try saying: Open YouTube, open settings, volume up, or volume down. جرّب: افتح يوتيوب، افتح الإعدادات، ارفع الصوت."); return; }
        reply("This command is not supported yet. AI understanding will be added in the next version. هذا الأمر غير مدعوم حاليًا.");
    }

    private void launchPackage(String pkg, String name) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null && pkg.equals("com.google.android.youtube.tv")) i = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
        if (i == null) { reply(name + " is not installed or cannot be launched / التطبيق غير مثبت أو لا يمكن تشغيله"); return; }
        try { startActivity(i); reply("Opening " + name + " / جاري فتح " + name); } catch (Exception e) { reply("Unable to open " + name); }
    }
    private void launchIntent(Intent i, String name) { try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); reply("Opening " + name); } catch (ActivityNotFoundException e) { reply("This settings page is unavailable on this TV."); } }
    private void setStatus(String s) { if (status != null) status.setText(s); }
    private void reply(String s) { response.setText("Assistant / المساعد: " + s); setStatus("Ready · جاهز"); if (ttsReady) { Locale l = s.matches(".*[\\u0600-\\u06FF].*") ? new Locale("ar") : Locale.US; tts.setLanguage(l); tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "tvai-reply"); } }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) { if (keyCode == KeyEvent.KEYCODE_MENU) { startListening(); return true; } return super.onKeyDown(keyCode,event); }
    @Override protected void onDestroy() { if (recognizer != null) { recognizer.destroy(); recognizer = null; } if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }
}
