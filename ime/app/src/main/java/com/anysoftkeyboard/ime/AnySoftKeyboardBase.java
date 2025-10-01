/*
 * Copyright (c) 2016 Menny Even-Danan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.anysoftkeyboard.ime;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.anysoftkeyboard.base.utils.GCUtils;
import com.anysoftkeyboard.base.utils.Logger;
import com.anysoftkeyboard.keyboards.views.KeyboardViewContainerView;
import com.anysoftkeyboard.keyboards.views.OnKeyboardActionListener;
import com.anysoftkeyboard.ui.dev.DeveloperUtils;
import com.anysoftkeyboard.utils.ModifierKeyState;
import com.menny.android.anysoftkeyboard.AnyApplication;
import com.menny.android.anysoftkeyboard.BuildConfig;
import com.menny.android.anysoftkeyboard.R;
import io.reactivex.disposables.CompositeDisposable;

import java.util.List;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Pair;

public abstract class AnySoftKeyboardBase extends InputMethodService
        implements OnKeyboardActionListener, ClipboardManager.OnPrimaryClipChangedListener {
  protected static final String TAG = "ASK";
  protected static final String AI_RESULTS_TAG = "ASK_AI_RESULTS";

  protected static final long ONE_FRAME_DELAY = 1000L / 60L;

  private static final ExtractedTextRequest EXTRACTED_TEXT_REQUEST = new ExtractedTextRequest();

  private KeyboardViewContainerView mInputViewContainer;
  private InputViewBinder mInputView;
  private InputMethodManager mInputMethodManager;
  private ClipboardManager mClipboardManager;
  private ClipboardTextClassifier mTextClassifier;
  private ViewGroup mAppSuggestionsContainer;
  private View mCandidateView;
  private Handler mHandler; // Handler to post UI updates to the main thread

  protected int mGlobalCursorPositionDangerous = 0;
  protected int mGlobalSelectionStartPositionDangerous = 0;
  protected int mGlobalCandidateStartPositionDangerous = 0;
  protected int mGlobalCandidateEndPositionDangerous = 0;

  protected final ModifierKeyState mShiftKeyState =
          new ModifierKeyState(true /*supports locked state*/);
  protected final ModifierKeyState mControlKeyState =
          new ModifierKeyState(false /*does not support locked state*/);

  @NonNull protected final CompositeDisposable mInputSessionDisposables = new CompositeDisposable();
  private int mOrientation;

  @Override
  @CallSuper
  public void onCreate() {
    Logger.i(
            TAG,
            "****** AnySoftKeyboard v%s (%d) service started.",
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE);
    super.onCreate();
    mHandler = new Handler(Looper.getMainLooper()); // Initialize the handler
    mOrientation = getResources().getConfiguration().orientation;
    if (!BuildConfig.DEBUG && DeveloperUtils.hasTracingRequested(getApplicationContext())) {
      try {
        DeveloperUtils.startTracing();
        Toast.makeText(getApplicationContext(), R.string.debug_tracing_starting, Toast.LENGTH_SHORT)
                .show();
      } catch (Exception e) {
        e.printStackTrace();
        Toast.makeText(
                        getApplicationContext(), R.string.debug_tracing_starting_failed, Toast.LENGTH_LONG)
                .show();
      }
    }

    mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    Logger.d(TAG, "Initializing ClipboardManager and listener.");
    mClipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    if (mClipboardManager!= null) {
      mClipboardManager.addPrimaryClipChangedListener(this);
    }

    mTextClassifier = new ClipboardTextClassifier(this, "distilbert_metadata_please_work.tflite", results -> {
      // This is the callback that receives the AI results.
      // We now pass these results to our UI method on the main UI thread.
      if (mHandler!= null) {
        mHandler.post(() -> showAppSuggestions(results));
      }
    });
  }

  @Override
  public void onPrimaryClipChanged() {
    if (mClipboardManager!= null && mClipboardManager.hasPrimaryClip()) {
      ClipData clipData = mClipboardManager.getPrimaryClip();
      if (clipData!= null && clipData.getItemCount() > 0) {
        CharSequence copiedText = clipData.getItemAt(0).getText();
        if (copiedText!= null && mTextClassifier!= null) {
          Logger.d(TAG, "New primary clip detected. Text: '" + copiedText + "'. Classifying...");
          mTextClassifier.classify(copiedText.toString());
        }
      }
    }
  }

  private void showAppSuggestions(List<Pair<String, Float>> results) {
    if (results == null || results.isEmpty()) {
      hideAppSuggestions();
      return;
    }

    Pair<String, Float> topResult = results.get(0);
    // We'll only show suggestions if the confidence is high (e.g., > 80%)
    // and it's not just plain text.
    boolean shouldShow = topResult.second > 0.8 &&!topResult.first.equals("plain_text");

    if (shouldShow) {
      // Hide the normal word suggestions and show our app suggestions
      if (mCandidateView!= null) mCandidateView.setVisibility(View.GONE);
      if (mAppSuggestionsContainer!= null) mAppSuggestionsContainer.setVisibility(View.VISIBLE);

      // --- Placeholder for Phase 3 ---
      // In the next phase, we will add code here to:
      // 1. Get the top category (e.g., "address").
      // 2. Find apps on the device that can handle an address.
      // 3. Create ImageViews for each app and add them to mAppSuggestionsContainer.
      // For now, we are just making the container visible.

    } else {
      hideAppSuggestions();
    }
  }

  private void hideAppSuggestions() {
    // Show the normal word suggestions and hide our app suggestions
    if (mAppSuggestionsContainer!= null) mAppSuggestionsContainer.setVisibility(View.GONE);
    if (mCandidateView!= null) mCandidateView.setVisibility(View.VISIBLE);
  }

  @Nullable
  public final InputViewBinder getInputView() {
    return mInputView;
  }

  @Nullable
  public KeyboardViewContainerView getInputViewContainer() {
    return mInputViewContainer;
  }

  protected abstract String getSettingsInputMethodId();

  protected InputMethodManager getInputMethodManager() {
    return mInputMethodManager;
  }

  @Override
  public void onComputeInsets(@NonNull Insets outInsets) {
    super.onComputeInsets(outInsets);
    if (!isFullscreenMode()) {
      outInsets.contentTopInsets = outInsets.visibleTopInsets;
    }
  }

  public void sendDownUpKeyEvents(int keyEventCode, int metaState) {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    long eventTime = SystemClock.uptimeMillis();
    ic.sendKeyEvent(
            new KeyEvent(
                    eventTime,
                    eventTime,
                    KeyEvent.ACTION_DOWN,
                    keyEventCode,
                    0,
                    metaState,
                    KeyCharacterMap.VIRTUAL_KEYBOARD,
                    0,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    ic.sendKeyEvent(
            new KeyEvent(
                    eventTime,
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP,
                    keyEventCode,
                    0,
                    metaState,
                    KeyCharacterMap.VIRTUAL_KEYBOARD,
                    0,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
  }

  public abstract void deleteLastCharactersFromInput(int countToDelete);

  @CallSuper
  public void onAddOnsCriticalChange() {
    hideWindow();
  }

  @Override
  public View onCreateInputView() {
    if (mInputView!= null) mInputView.onViewNotRequired();
    mInputView = null;

    GCUtils.getInstance()
            .performOperationWithMemRetry(
                    TAG,
                    () -> {
                      mInputViewContainer = createInputViewContainer();
                      mInputViewContainer.setBackgroundResource(R.drawable.ask_wallpaper);
                    });

    mInputView = mInputViewContainer.getStandardKeyboardView();
    mInputViewContainer.setOnKeyboardActionListener(this);
    setupInputViewWatermark();
    mAppSuggestionsContainer = mInputViewContainer.findViewById(R.id.app_suggestions_container);
    mCandidateView = mInputViewContainer.findViewById(R.id.candidate_view);
    return mInputViewContainer;
  }

  @Override
  public void setInputView(View view) {
    super.setInputView(view);
    updateSoftInputWindowLayoutParameters();
  }

  @Override
  public void updateFullscreenMode() {
    super.updateFullscreenMode();
    updateSoftInputWindowLayoutParameters();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    ((AnyApplication) getApplication()).setNewConfigurationToAllAddOns(newConfig);
    super.onConfigurationChanged(newConfig);
    if (newConfig.orientation!= mOrientation) {
      var lastOrientation = mOrientation;
      mOrientation = newConfig.orientation;
      onOrientationChanged(lastOrientation, mOrientation);
    }
  }

  protected int getCurrentOrientation() {
    // must use the current configuration, since mOrientation may lag a bit.
    return getResources().getConfiguration().orientation;
  }

  @CallSuper
  protected void onOrientationChanged(int oldOrientation, int newOrientation) {}

  private void updateSoftInputWindowLayoutParameters() {
    final Window window = getWindow().getWindow();
    updateLayoutHeightOf(window, ViewGroup.LayoutParams.MATCH_PARENT);
    if (mInputViewContainer!= null) {
      final View inputArea = window.findViewById(android.R.id.inputArea);

      updateLayoutHeightOf(
              (View) inputArea.getParent(),
              isFullscreenMode()
                      ? ViewGroup.LayoutParams.MATCH_PARENT
                      : ViewGroup.LayoutParams.WRAP_CONTENT);
      updateLayoutGravityOf((View) inputArea.getParent(), Gravity.BOTTOM);
    }
  }

  private static void updateLayoutHeightOf(final Window window, final int layoutHeight) {
    final WindowManager.LayoutParams params = window.getAttributes();
    if (params!= null && params.height!= layoutHeight) {
      params.height = layoutHeight;
      window.setAttributes(params);
    }
  }

  private static void updateLayoutHeightOf(final View view, final int layoutHeight) {
    final ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params!= null && params.height!= layoutHeight) {
      params.height = layoutHeight;
      view.setLayoutParams(params);
    }
  }

  private static void updateLayoutGravityOf(final View view, final int layoutGravity) {
    final ViewGroup.LayoutParams lp = view.getLayoutParams();
    if (lp instanceof LinearLayout.LayoutParams) {
      final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) lp;
      if (params.gravity!= layoutGravity) {
        params.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
    } else if (lp instanceof FrameLayout.LayoutParams) {
      final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) lp;
      if (params.gravity!= layoutGravity) {
        params.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
    } else {
      throw new IllegalArgumentException(
              "Layout parameter doesn't have gravity: " + lp.getClass().getName());
    }
  }

  @CallSuper
  @NonNull
  protected List<Drawable> generateWatermark() {
    return ((AnyApplication) getApplication()).getInitialWatermarksList();
  }

  protected final void setupInputViewWatermark() {
    final InputViewBinder inputView = getInputView();
    if (inputView!= null) {
      inputView.setWatermark(generateWatermark());
    }
  }

  @SuppressLint("InflateParams")
  protected KeyboardViewContainerView createInputViewContainer() {
    return (KeyboardViewContainerView)
            getLayoutInflater().inflate(R.layout.main_keyboard_layout, null);
  }

  @CallSuper
  protected boolean handleCloseRequest() {
    return false;
  }

  @Override
  public void hideWindow() {
    while (handleCloseRequest()) {
      Logger.i(TAG, "Still have stuff to close. Trying handleCloseRequest again.");
    }
    super.hideWindow();
  }

  @Override
  public void onDestroy() {
    mInputSessionDisposables.dispose();
    if (getInputView()!= null) getInputView().onViewNotRequired();
    mInputView = null;
    if (mClipboardManager!= null) {
      mClipboardManager.removePrimaryClipChangedListener(this);
      Logger.d(TAG, "ClipboardManager listener removed.");
    }
    if (mTextClassifier!= null) {
      mTextClassifier.close();
      mTextClassifier = null;
      Logger.d(TAG, "ClipboardTextClassifier closed.");
    }
    super.onDestroy();
  }

  @Override
  @CallSuper
  public void onFinishInput() {
    super.onFinishInput();
    mInputSessionDisposables.clear();
    mGlobalCursorPositionDangerous = 0;
    mGlobalSelectionStartPositionDangerous = 0;
    mGlobalCandidateStartPositionDangerous = 0;
    mGlobalCandidateEndPositionDangerous = 0;
  }

  protected abstract boolean isSelectionUpdateDelayed();

  @Nullable
  protected ExtractedText getExtractedText() {
    final InputConnection connection = getCurrentInputConnection();
    if (connection == null) {
      return null;
    }
    return connection.getExtractedText(EXTRACTED_TEXT_REQUEST, 0);
  }

  protected int getCursorPosition() {
    if (isSelectionUpdateDelayed()) {
      ExtractedText extracted = getExtractedText();
      if (extracted == null) {
        return 0;
      }
      mGlobalCursorPositionDangerous = extracted.startOffset + extracted.selectionEnd;
      mGlobalSelectionStartPositionDangerous = extracted.startOffset + extracted.selectionStart;
    }
    return mGlobalCursorPositionDangerous;
  }

  @Override
  public void onUpdateSelection(
          int oldSelStart,
          int oldSelEnd,
          int newSelStart,
          int newSelEnd,
          int candidatesStart,
          int candidatesEnd) {
    if (BuildConfig.DEBUG) {
      Logger.d(
              TAG,
              "onUpdateSelection: oss=%d, ose=%d, nss=%d, nse=%d, cs=%d, ce=%d",
              oldSelStart,
              oldSelEnd,
              newSelStart,
              newSelEnd,
              candidatesStart,
              candidatesEnd);
    }
    mGlobalCursorPositionDangerous = newSelEnd;
    mGlobalSelectionStartPositionDangerous = newSelStart;
    mGlobalCandidateStartPositionDangerous = candidatesStart;
    mGlobalCandidateEndPositionDangerous = candidatesEnd;
  }

  @Override
  public void onPress(int primaryCode) {
    // This method is part of the OnKeyboardActionListener interface.
    // We hide our suggestions as soon as the user touches a key.
    if (mAppSuggestionsContainer!= null && mAppSuggestionsContainer.getVisibility() == View.VISIBLE) {
      hideAppSuggestions();
    }
  }

  @Override
  public void onCancel() {
    // the user released their finger outside of any key... okay. I have nothing to do about
    // that.
  }
}