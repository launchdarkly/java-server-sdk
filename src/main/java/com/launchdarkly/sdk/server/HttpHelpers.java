package com.launchdarkly.sdk.server;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class HttpHelpers {
    public static URI addQueryParamToUri(URI uri, String paramKey, String paramValue) {

        // first try encoding params
        String encodedKey;
        String encodedValue;
        try {
            encodedKey = URLEncoder.encode(paramKey, StandardCharsets.UTF_8.toString());
            encodedValue = URLEncoder.encode(paramValue, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            // TODO: discuss options here, throw runtime (bad), ignore (probably best we can
            // do)
            return uri;
        }

        // Modify the query and append the new param if necessary
        StringBuilder sb = new StringBuilder(uri.getQuery() == null ? "" : uri.getQuery());
        if (sb.length() > 0) {
            sb.append('&');
        }
        sb.append(encodedKey);
        sb.append('=');
        sb.append(encodedValue);
        
        return URI.create(uri.toString() + "?" + sb.toString());

        // Build the new url with the modified query:
        // try {
        //     return URI.create(uri.toString() + "?" + sb.toString());

        //     // return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), sb.toString(), uri.getFragment());
        // } catch (URISyntaxException e) {
        //     // TODO: discuss options here
        //     return uri;
        // }
    }
}