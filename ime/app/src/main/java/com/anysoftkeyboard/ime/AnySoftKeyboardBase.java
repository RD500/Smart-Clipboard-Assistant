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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.anysoftkeyboard.base.utils.GCUtils;
import com.anysoftkeyboard.base.utils.Logger;
import com.anysoftkeyboard.keyboards.views.KeyboardViewContainerView;
import com.anysoftkeyboard.keyboards.views.OnKeyboardActionListener;
import com.anysoftkeyboard.utils.ModifierKeyState;
import com.menny.android.anysoftkeyboard.AnyApplication;
import com.menny.android.anysoftkeyboard.BuildConfig;
import com.menny.android.anysoftkeyboard.R;
import io.reactivex.disposables.CompositeDisposable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
  private Handler mHandler;
  private String mCopiedText;

  // Phase 4: Personalization components
  private AppDatabase mDatabase;
  private ExecutorService mDbExecutor;

  protected int mGlobalCursorPositionDangerous = 0;
  protected int mGlobalSelectionStartPositionDangerous = 0;
  protected int mGlobalCandidateStartPositionDangerous = 0;
  protected int mGlobalCandidateEndPositionDangerous = 0;

  protected final ModifierKeyState mShiftKeyState = new ModifierKeyState(true /*supports locked state*/);
  protected final ModifierKeyState mControlKeyState = new ModifierKeyState(false /*does not support locked state*/);

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
    mHandler = new Handler(Looper.getMainLooper());
    mOrientation = getResources().getConfiguration().orientation;

    mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    Logger.d(TAG, "Initializing ClipboardManager and listener.");
    mClipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    if (mClipboardManager != null) {
      mClipboardManager.addPrimaryClipChangedListener(this);
    }

    mTextClassifier = new ClipboardTextClassifier(this, "model.tflite", this::showAppSuggestions);

    // Phase 4: Initialize database and executor
    mDatabase = AppDatabase.getDatabase(this);
    mDbExecutor = Executors.newSingleThreadExecutor();
  }

  @Override
  public void onPrimaryClipChanged() {
    if (mClipboardManager != null && mClipboardManager.hasPrimaryClip()) {
      ClipData clipData = mClipboardManager.getPrimaryClip();
      if (clipData != null && clipData.getItemCount() > 0) {
        CharSequence copiedSequence = clipData.getItemAt(0).getText();
        if (copiedSequence != null) {
          mCopiedText = copiedSequence.toString();
          Logger.d(TAG, "New primary clip detected. Text: '" + mCopiedText + "'. Classifying...");
          if (mTextClassifier != null) {
            mTextClassifier.classify(mCopiedText);
          }
        }
      }
    }
  }

  private void showAppSuggestions(List<Pair<String, Float>> results) {
    if (results == null || results.isEmpty() || mCopiedText == null) {
      hideAppSuggestions();
      return;
    }

    Pair<String, Float> topResult = null;
    float maxScore = -1.0f;
    for (Pair<String, Float> result : results) {
      if (result.second > maxScore) {
        maxScore = result.second;
        topResult = result;
      }
    }

    if (topResult == null) {
      hideAppSuggestions();
      return;
    }

    boolean shouldShow = topResult.second > 0.8 && !topResult.first.equals("plain_text");
    if (!shouldShow) {
      if (topResult.second <= 0.8) {
        Logger.d(AI_RESULTS_TAG, "Top result '" + topResult.first + "' was below threshold (" + topResult.second + "). Hiding suggestions.");
      }
      hideAppSuggestions();
      return;
    }

    Logger.d(AI_RESULTS_TAG, "Top result '" + topResult.first + "' is over threshold. Finding and ranking apps.");

    final Pair<String, Float> finalTopResult = topResult;
    // Run database and package manager queries on a background thread
    mDbExecutor.execute(() -> {
      // Get ranked package names from DB
      List<String> rankedPackages = mDatabase.usageDao().getRankedAppsForCategory(finalTopResult.first);

      // Your existing logic to get all relevant apps
      PackageManager pm = getPackageManager();
      HashSet<String> addedPackages = new HashSet<>();
      ArrayList<ResolveInfo> allActivities = new ArrayList<>();

      Intent primaryIntent = IntentMapper.mapCategoryToIntent(finalTopResult.first, mCopiedText);
      if (primaryIntent != null) {
        List<ResolveInfo> primaryActivities = pm.queryIntentActivities(primaryIntent, 0);
        for (ResolveInfo info : primaryActivities) {
          if (addedPackages.add(info.activityInfo.packageName)) {
            allActivities.add(info);
          }
        }
      }
      Intent messagingIntent = null;
      if (finalTopResult.first.equals("phone")) {
        messagingIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + mCopiedText));
        List<ResolveInfo> messagingActivities = pm.queryIntentActivities(messagingIntent, 0);
        for (ResolveInfo info : messagingActivities) {
          if (addedPackages.add(info.activityInfo.packageName)) {
            allActivities.add(info);
          }
        }
      }

      if (!allActivities.isEmpty()) {
        // Sort the combined list of activities based on our database ranking
        Collections.sort(allActivities, (a, b) -> {
          int indexA = rankedPackages.indexOf(a.activityInfo.packageName);
          int indexB = rankedPackages.indexOf(b.activityInfo.packageName);
          // If a package is not in the list, it gets a lower priority (higher index)
          if (indexA == -1) indexA = Integer.MAX_VALUE;
          if (indexB == -1) indexB = Integer.MAX_VALUE;
          return Integer.compare(indexA, indexB);
        });
        final Intent finalMessagingIntent = messagingIntent;

        // Update the UI on the main thread with the sorted list
        mHandler.post(() -> {
          if (mCandidateView != null) mCandidateView.setVisibility(View.GONE);
          if (mAppSuggestionsContainer != null) {
            mAppSuggestionsContainer.removeAllViews();
            mAppSuggestionsContainer.setVisibility(View.VISIBLE);

            LayoutInflater inflater = LayoutInflater.from(this);
            for (ResolveInfo resolveInfo : allActivities) {
              if (mAppSuggestionsContainer.getChildCount() >= 5) break;

              // Determine the correct intent for this specific app
              Intent intentToLaunch;
              String pkg = resolveInfo.activityInfo.packageName.toLowerCase();

              // If it's a known messaging app AND we have a messaging intent, use it.
              if (finalMessagingIntent != null &&
                      (pkg.contains("mms") || pkg.contains("messaging") || pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("signal"))) {
                intentToLaunch = finalMessagingIntent;
              } else {
                // Otherwise, use the primary intent (for the Dialer, Browser, etc.).
                intentToLaunch = primaryIntent;
              }

              ImageView iconView = (ImageView) inflater.inflate(R.layout.suggestion_app_icon, mAppSuggestionsContainer, false);
              iconView.setImageDrawable(resolveInfo.loadIcon(pm));
              iconView.setOnClickListener(v -> launchApp(resolveInfo,intentToLaunch, finalTopResult.first));
              mAppSuggestionsContainer.addView(iconView);
            }
          }
        });
      } else {
        mHandler.post(this::hideAppSuggestions);
      }
    });
  }

  // Add the 'final' keyword to the category parameter to fix the lambda error.
  private void launchApp(ResolveInfo appInfo, Intent intent, final String category) {
    // Phase 4: Log the user's choice to the database in the background
    mDbExecutor.execute(() -> {
      UsageStat stat = new UsageStat();
      stat.textCategory = category;
      stat.chosenAppPackage = appInfo.activityInfo.packageName;
      mDatabase.usageDao().insert(stat);
      Logger.d(TAG, "Logged usage: " + category + " -> " + appInfo.activityInfo.packageName);
    });

    Intent launchIntent = new Intent(intent);
    launchIntent.setClassName(appInfo.activityInfo.packageName, appInfo.activityInfo.name);
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      startActivity(launchIntent);
    } catch (Exception e) {
      Logger.e(TAG, "Failed to launch app: " + appInfo.activityInfo.packageName, e);
      Toast.makeText(this, "Could not launch app", Toast.LENGTH_SHORT).show();
    }
    hideAppSuggestions();
  }

  private void hideAppSuggestions() {
    if (mAppSuggestionsContainer != null) mAppSuggestionsContainer.setVisibility(View.GONE);
    if (mCandidateView != null) mCandidateView.setVisibility(View.VISIBLE);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    mInputSessionDisposables.dispose();
    if (getInputView() != null) getInputView().onViewNotRequired();
    mInputView = null;
    if (mClipboardManager != null) {
      mClipboardManager.removePrimaryClipChangedListener(this);
      Logger.d(TAG, "ClipboardManager listener removed.");
    }
    if (mTextClassifier != null) {
      mTextClassifier.close();
      mTextClassifier = null;
      Logger.d(TAG, "ClipboardTextClassifier closed.");
    }
    // Phase 4: Shutdown the database executor
    if (mDbExecutor != null && !mDbExecutor.isShutdown()) {
      mDbExecutor.shutdown();
    }
  }

  @Nullable
  public final InputViewBinder getInputView() { return mInputView; }

  @Nullable
  public KeyboardViewContainerView getInputViewContainer() { return mInputViewContainer; }

  protected abstract String getSettingsInputMethodId();

  protected InputMethodManager getInputMethodManager() { return mInputMethodManager; }

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
    if (mAppSuggestionsContainer!= null && mAppSuggestionsContainer.getVisibility() == View.VISIBLE) {
      hideAppSuggestions();
    }
  }

  @Override
  public void onCancel() {
    // the user released their finger outside of any key... okay. I have nothing to do about that.
  }
}
