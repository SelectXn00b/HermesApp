/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android service layer.
 */
package com.xiaomo.androidforclaw.service;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import com.draco.ladb.R;

public class AdbIME extends InputMethodService {
    private String IME_MESSAGE = "ADB_INPUT_TEXT";
    private String IME_CHARS = "ADB_INPUT_CHARS";
    private String IME_KEYCODE = "ADB_INPUT_CODE";
    private String IME_META_KEYCODE = "ADB_INPUT_MCODE";
    private String IME_EDITORCODE = "ADB_EDITOR_CODE";
    private String IME_MESSAGE_B64 = "ADB_INPUT_B64";
    private String IME_CLEAR_TEXT = "ADB_CLEAR_TEXT";
    private String IME_SEND_MESSAGE = "ADB_SEND_MESSAGE"; // 新增：专门用于发送消息
    private BroadcastReceiver mReceiver = null;

    @Override
    public View onCreateInputView() {
        View mInputView = getLayoutInflater().inflate(R.layout.adb_keyboard, null);

        // Register this instance to AdbIMEManager
        AdbIMEManager.INSTANCE.registerInstance(this);

        if (mReceiver == null) {
            IntentFilter filter = new IntentFilter(IME_MESSAGE);
            filter.addAction(IME_CHARS);
            filter.addAction(IME_KEYCODE);
            filter.addAction(IME_MESSAGE); // IME_META_KEYCODE // Change IME_MESSAGE to get more values.
            filter.addAction(IME_EDITORCODE);
            filter.addAction(IME_MESSAGE_B64);
            filter.addAction(IME_CLEAR_TEXT);
            filter.addAction(IME_SEND_MESSAGE); // 新增：注册发送消息的action
            mReceiver = new AdbReceiver();

            // Android 14+ 需要显式指定 RECEIVER_NOT_EXPORTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(mReceiver, filter);
            }
        }

        // 绑定切换按钮点击事件
        Button switchBtn = mInputView.findViewById(R.id.btn_switch_normal_ime);
        if (switchBtn != null) {
            switchBtn.setOnClickListener(v -> switchToNormalIme());
        }

        return mInputView;
    }

    public void onDestroy() {
        // Unregister from AdbIMEManager
        AdbIMEManager.INSTANCE.unregisterInstance();

        if (mReceiver != null)
            unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    /**
     * 切换到普通输入法
     */
    private void switchToNormalIme() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        // 显示输入法选择器，让用户手动选择
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            imm.showInputMethodPicker();
        }, 300);
    }

    class AdbReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("AdbIME", "onReceive: " + intent.getAction());
            if (intent.getAction().equals(IME_MESSAGE)) {
                // normal message
                String msg = intent.getStringExtra("msg");
                if (msg != null) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null)
                        ic.commitText(msg, 1);
                }
                // meta codes
                String metaCodes = intent.getStringExtra("mcode"); // Get message.
                Log.d("AdbIME", "onReceive: metaCodes = " + metaCodes);
                if (metaCodes != null) {
                    String[] mcodes = metaCodes.split(","); // Get mcodes in string.
                    if (mcodes != null) {
                        int i;
                        InputConnection ic = getCurrentInputConnection();
                        for (i = 0; i < mcodes.length - 1; i = i + 2) {
                            if (ic != null) {
                                KeyEvent ke;
                                if (mcodes[i].contains("+")) { // Check metaState if more than one. Use '+' as delimiter
                                    String[] arrCode = mcodes[i].split("\\+"); // Get metaState if more than one.
                                    ke = new KeyEvent(
                                            0,
                                            0,
                                            KeyEvent.ACTION_DOWN, // Action code.
                                            Integer.parseInt(mcodes[i + 1].toString()), // Key code.
                                            0, // Repeat. // -1
                                            Integer.parseInt(arrCode[0].toString()) | Integer.parseInt(arrCode[1].toString()), // Flag
                                            0, // The device ID that generated the key event.
                                            0, // Raw device scan code of the event.
                                            KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, // The flags for this key event.
                                            InputDevice.SOURCE_KEYBOARD // The input source such as SOURCE_KEYBOARD.
                                    );
                                } else { // Only one metaState.
                                    ke = new KeyEvent(
                                            0,
                                            0,
                                            KeyEvent.ACTION_DOWN, // Action code.
                                            Integer.parseInt(mcodes[i + 1].toString()), // Key code.
                                            0, // Repeat.
                                            Integer.parseInt(mcodes[i].toString()), // Flag
                                            0, // The device ID that generated the key event.
                                            0, // Raw device scan code of the event.
                                            KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, // The flags for this key event.
                                            InputDevice.SOURCE_KEYBOARD // The input source such as SOURCE_KEYBOARD.
                                    );
                                }
                                ic.sendKeyEvent(ke);
                                // 补发一个 ACTION_UP 事件，确保按键事件完整
                                KeyEvent keUp = KeyEvent.changeAction(ke, KeyEvent.ACTION_UP);
                                ic.sendKeyEvent(keUp);
                                Log.d("SendClick", "onReceive: ");
                            }
                        }
                    }
                }
            }

            if (intent.getAction().equals(IME_MESSAGE_B64)) {
                String data = intent.getStringExtra("msg");

                byte[] b64 = Base64.decode(data, Base64.DEFAULT);
                String msg = "NOT SUPPORTED";
                try {
                    msg = new String(b64, "UTF-8");
                } catch (Exception e) {

                }

                if (msg != null) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null)
                        ic.commitText(msg, 1);
                }
            }

            if (intent.getAction().equals(IME_CHARS)) {
                int[] chars = intent.getIntArrayExtra("chars");
                if (chars != null) {
                    String msg = new String(chars, 0, chars.length);
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null)
                        ic.commitText(msg, 1);
                }
            }

            if (intent.getAction().equals(IME_KEYCODE)) {
                int code = intent.getIntExtra("code", -1);
                if (code != -1) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
                        // 补发一个 ACTION_UP 事件，确保按键事件完整
                        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
                    }
                }
            }

            if (intent.getAction().equals(IME_EDITORCODE)) {
                int code = intent.getIntExtra("code", -1);
                if (code != -1) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null)
                        ic.performEditorAction(code);
                }
            }

            if (intent.getAction().equals(IME_CLEAR_TEXT)) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    //REF: stackoverflow/33082004 author: Maxime Epain
                    CharSequence curPos = ic.getExtractedText(new ExtractedTextRequest(), 0).text;
                    CharSequence beforePos = ic.getTextBeforeCursor(curPos.length(), 0);
                    CharSequence afterPos = ic.getTextAfterCursor(curPos.length(), 0);
                    ic.deleteSurroundingText(beforePos.length(), afterPos.length());
                }
            }

            // 新增：处理发送消息的广播
            if (intent.getAction().equals(IME_SEND_MESSAGE)) {
                Log.d("AdbIME", "onReceive: IME_SEND_MESSAGE");
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    // 先尝试 IME_ACTION_SEND
                    boolean sent = ic.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
                    Log.d("AdbIME", "performEditorAction IME_ACTION_SEND: " + sent);

                    // 如果失败，再尝试其他常见的发送动作
                    if (!sent) {
                        sent = ic.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
                        Log.d("AdbIME", "performEditorAction IME_ACTION_GO: " + sent);
                    }

                    // 如果还是失败，尝试发送回车键
                    if (!sent) {
                        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                        Log.d("AdbIME", "sendKeyEvent KEYCODE_ENTER as fallback");
                    }
                }
            }
        }
    }
}
