package com.anysoftkeyboard.ime;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

public class IntentMapper {    public static Intent mapCategoryToIntent(String category, String data) {
    if (TextUtils.isEmpty(category) || TextUtils.isEmpty(data)) {
        return null;
    }

    // Trim the data once to remove leading/trailing whitespace.
    String trimmedData = data.trim();

    switch (category.toLowerCase()) {
        case "address":
            // For a geo query parameter, encoding the data is CORRECT.
            return new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(trimmedData)));

        case "phone":
            // For a telephone number, DO NOT encode. The 'tel:' scheme needs the raw digits and symbols.
            return new Intent(Intent.ACTION_VIEW, Uri.parse("tel:" + trimmedData));

        case "url":
            // For a URL, let Uri.parse handle it. First, ensure it has a scheme.
            String url = trimmedData;
            if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
                url = "http://" + url;
            }
            return new Intent(Intent.ACTION_VIEW, Uri.parse(url));

        case "email":
            // For an email address, DO NOT encode. The 'mailto:' scheme needs the raw address with the '@' symbol.
            return new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + trimmedData));

        default:
            // This is the correct fallback.
            return null;
    }
}
}
