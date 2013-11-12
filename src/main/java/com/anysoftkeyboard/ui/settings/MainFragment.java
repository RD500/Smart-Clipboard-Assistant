package com.anysoftkeyboard.ui.settings;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.anysoftkeyboard.theme.KeyboardTheme;
import com.anysoftkeyboard.theme.KeyboardThemeFactory;
import com.anysoftkeyboard.ui.tutorials.ChangeLogFragment;
import com.anysoftkeyboard.ui.tutorials.TipsFragment;
import com.menny.android.anysoftkeyboard.R;

import net.evendanan.pushingpixels.Banner;
import net.evendanan.pushingpixels.FragmentChauffeurActivity;

public class MainFragment extends Fragment {

    private static final String TAG = "MainFragment";
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.main_fragment, container, false);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        //I'm doing the setup of the link in onViewStateRestored, since the links will be restored too
        //and they will probably refer to a different scoop (Fragment).
        //setting up the underline and click handler in the keyboard_not_configured_box layout
        TextView clickHere = (TextView) getView().findViewById(R.id.not_configured_click_here);
        String fullText = getString(R.string.not_configured_with_click_here);
        String justClickHereText = getString(R.string.not_configured_with_just_click_here);
        SpannableStringBuilder sb = new SpannableStringBuilder(fullText);
        // Get the index of "click here" string.
        int start = fullText.indexOf(justClickHereText);
        int length = justClickHereText.length();
        ClickableSpan csp = new ClickableSpan() {
            @Override
            public void onClick(View v) {
                //TODO: start the how-to-activate fragment
            }
        };
        sb.setSpan(csp, start, start + length, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        clickHere.setMovementMethod(LinkMovementMethod.getInstance());
        clickHere.setText(sb);

        //setting up change_log
        setupLink(getView(), R.id.read_more_change_log,
                new ClickableSpan() {
                    @Override
                    public void onClick(View v) {
                        FragmentChauffeurActivity activity = (FragmentChauffeurActivity)getActivity();
                        activity.addFragmentToUi(ChangeLogFragment.createFragment(ChangeLogFragment.SHOW_ALL_CHANGELOG, true),
                                    FragmentChauffeurActivity.FragmentUiContext.ExpandedItem,
                                    getView().findViewById(R.id.change_log_card));
                        }
                    });
        //setting up tips
        setupLink(getView(), R.id.show_more_tips,
                new ClickableSpan() {
                    @Override
                    public void onClick(View v) {
                        FragmentChauffeurActivity activity = (FragmentChauffeurActivity)getActivity();
                        activity.addFragmentToUi(TipsFragment.createFragment(TipsFragment.SHOW_ALL_TIPS),
                                FragmentChauffeurActivity.FragmentUiContext.ExpandedItem,
                                getView().findViewById(R.id.tips_card));
                    }
                });

    }

    private void setupLink(View root, int showMoreLinkId, ClickableSpan clickableSpan) {
        TextView clickHere = (TextView) root.findViewById(showMoreLinkId);
        SpannableStringBuilder sb = new SpannableStringBuilder(clickHere.getText());
        sb.clearSpans();//removing any previously (from instance-state) set click spans.
        sb.setSpan(clickableSpan, 0, clickHere.getText().length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        clickHere.setMovementMethod(LinkMovementMethod.getInstance());
        clickHere.setText(sb);
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle(getString(R.string.how_to_pointer_title));

        View notConfiguredBox = getView().findViewById(R.id.keyboard_not_configured_box);
        //checking if the IME is configured
        final Context context = getActivity().getApplicationContext();
        //checking the default IME
        final String defaultIME = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (TextUtils.isEmpty(defaultIME) || !defaultIME.startsWith(context.getPackageName())) {
            //I'm going to show the warning dialog
            //whenever AnySoftKeyboard is not marked as the default.
            notConfiguredBox.setVisibility(View.VISIBLE);
        } else {
            notConfiguredBox.setVisibility(View.GONE);
        }

        //updating the keyboard layout to the current theme screenshot (if exists).
        KeyboardTheme theme = KeyboardThemeFactory.getCurrentKeyboardTheme(getActivity().getApplicationContext());
        if (theme == null)
            theme = KeyboardThemeFactory.getFallbackTheme(getActivity().getApplicationContext());
        Drawable screenshot = theme.getScreenshot();

        Banner screenshotHolder = (Banner) getView().findViewById(R.id.keyboard_screen_shot);
        if (screenshot == null)
            screenshotHolder.setBackgroundResource(R.drawable.lean_dark_theme_screenshot);
        else
            screenshotHolder.setImageDrawable(screenshot);
    }
}