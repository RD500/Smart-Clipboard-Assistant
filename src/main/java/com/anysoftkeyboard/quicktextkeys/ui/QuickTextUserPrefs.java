package com.anysoftkeyboard.quicktextkeys.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.anysoftkeyboard.quicktextkeys.QuickTextKey;
import com.anysoftkeyboard.utils.Log;
import com.menny.android.anysoftkeyboard.R;

import java.util.List;

/*package*/ class QuickTextUserPrefs {
	/*package*/ static final String KEY_QUICK_TEXT_PREF_LAST_SELECTED_TAB_ADD_ON_ID = "KEY_QUICK_TEXT_PREF_LAST_SELECTED_TAB_ADD_ON_ID";
	/*package*/ static final String PREF_VALUE_INITIAL_TAB_ALWAYS_FIRST = "always_first";
	/*package*/ static final String PREF_VALUE_INITIAL_TAB_LAST_USED = "last_used";

	private final SharedPreferences mSharedPreferences;

	private final String mStartUpTypePrefKey;
	private final String mStartUpTypePrefDefault;

	public QuickTextUserPrefs(Context context) {
		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
		mStartUpTypePrefKey = context.getString(R.string.settings_key_initial_quick_text_tab);
		mStartUpTypePrefDefault = context.getString(R.string.settings_default_initial_quick_text_tab);
	}

	public int getStartPageIndex(List<QuickTextKey> allAddOns) {
		final String startupType = mSharedPreferences.getString(mStartUpTypePrefKey, mStartUpTypePrefDefault);
		return getTabIndexByStartUpType(allAddOns, startupType);
	}

	private int getTabIndexByStartUpType(List<QuickTextKey> allAddOns, String startupType) {
		switch (startupType) {
			case PREF_VALUE_INITIAL_TAB_LAST_USED:
				return getPositionForAddOnId(allAddOns, getLastSelectedAddOnId());
			case PREF_VALUE_INITIAL_TAB_ALWAYS_FIRST:
				return 0;
			default:
				Log.d("QuickTextUserPrefs", "Unrecognized %s value: %s. Defaulting to %s", mStartUpTypePrefKey, startupType, mStartUpTypePrefDefault);
				return getTabIndexByStartUpType(allAddOns, mStartUpTypePrefDefault);
		}
	}

	@Nullable
	private String getLastSelectedAddOnId() {
		return mSharedPreferences.getString(KEY_QUICK_TEXT_PREF_LAST_SELECTED_TAB_ADD_ON_ID, "");
	}

	public void setLastSelectedAddOnId(@Nullable String addOnId) {
		mSharedPreferences.edit().putString(KEY_QUICK_TEXT_PREF_LAST_SELECTED_TAB_ADD_ON_ID, addOnId).commit();
	}

	private static int getPositionForAddOnId(List<QuickTextKey> list, @Nullable String initialAddOnId) {
		if (TextUtils.isEmpty(initialAddOnId)) {
			return 0;
		}

		for (int addOnIndex = 0; addOnIndex < list.size(); addOnIndex++) {
			final QuickTextKey quickTextKey = list.get(addOnIndex);
			if (quickTextKey.getId().equals(initialAddOnId)) {
				return addOnIndex;
			}
		}

		return 0;
	}
}
