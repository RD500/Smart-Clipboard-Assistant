package com.anysoftkeyboard;

import android.app.AlertDialog;
import android.view.View;

import com.anysoftkeyboard.api.KeyCodes;
import com.anysoftkeyboard.keyboards.views.AnyKeyboardView;
import com.anysoftkeyboard.keyboards.views.KeyboardViewContainerView;
import com.menny.android.anysoftkeyboard.R;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(AnySoftKeyboardTestRunner.class)
public class AnySoftKeyboardViewRelatedTest extends AnySoftKeyboardBaseTest {

    @Test
    public void testOnCreateInputView() throws Exception {
        View mainKeyboardView = mAnySoftKeyboardUnderTest.getInputViewContainer();
        Assert.assertNotNull(mainKeyboardView);
        Assert.assertTrue(mainKeyboardView instanceof KeyboardViewContainerView);
        KeyboardViewContainerView containerView = (KeyboardViewContainerView) mainKeyboardView;
        Assert.assertEquals(1, containerView.getChildCount());
        final View inputView = containerView.getChildAt(0);
        Assert.assertNotNull(inputView);
        Assert.assertTrue(inputView instanceof AnyKeyboardView);
        Assert.assertSame(inputView, containerView.getStandardKeyboardView());
        Mockito.verify(containerView.getStandardKeyboardView()).setWatermark("α\uD83D\uDD25");
    }

    @Test
    public void testSettingsBasic() throws Exception {
        Assert.assertNull(ShadowAlertDialog.getLatestAlertDialog());
        mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.SETTINGS);
        final AlertDialog latestAlertDialog = ShadowAlertDialog.getLatestAlertDialog();
        Assert.assertNotNull(latestAlertDialog);

        final ShadowAlertDialog shadowAlertDialog = Shadows.shadowOf(latestAlertDialog);
        Assert.assertEquals("AnySoftKeyboard", shadowAlertDialog.getTitle());
        Assert.assertEquals(4, shadowAlertDialog.getItems().length);

    }

    @Test
    public void testSettingsIncognito() throws Exception {
        Assert.assertNull(ShadowAlertDialog.getLatestAlertDialog());
        mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.SETTINGS);
        final AlertDialog latestAlertDialog = ShadowAlertDialog.getLatestAlertDialog();
        final ShadowAlertDialog shadowAlertDialog = Shadows.shadowOf(latestAlertDialog);

        Assert.assertEquals(RuntimeEnvironment.application.getText(R.string.switch_incognito), shadowAlertDialog.getItems()[3]);

        Assert.assertFalse(mAnySoftKeyboardUnderTest.getSpiedSuggest().isIncognitoMode());

        shadowAlertDialog.clickOnItem(3);

        Assert.assertTrue(mAnySoftKeyboardUnderTest.getSpiedSuggest().isIncognitoMode());
        Mockito.verify(mAnySoftKeyboardUnderTest.getInputView()).setWatermark("α\uD83D\uDD25\uD83D\uDD75");
    }
}