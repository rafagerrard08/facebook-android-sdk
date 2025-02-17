/*
 * Copyright (c) 2014-present, Facebook, Inc. All rights reserved.
 *
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Facebook.
 *
 * As with any software that integrates with the Facebook platform, your use of
 * this software is subject to the Facebook Developer Principles and Policies
 * [http://developers.facebook.com/policy/]. This copyright notice shall be
 * included in all copies or substantial portions of the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.facebook.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import android.content.Intent;
import android.os.Bundle;
import com.facebook.AccessToken;
import com.facebook.AccessTokenSource;
import com.facebook.AuthenticationToken;
import com.facebook.FacebookException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.FacebookSdk;
import com.facebook.TestUtils;
import com.facebook.internal.FacebookDialogFragment;
import com.facebook.internal.Utility;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PowerMockIgnore({"org.mockito.*", "org.robolectric.*"})
@PrepareForTest({AccessToken.class, FacebookSdk.class, LoginClient.class})
public class WebViewLoginMethodHandlerTest extends LoginHandlerTestCase {
  private static final String SIGNED_REQUEST_STR =
      "ggarbage.eyJhbGdvcml0aG0iOiJITUFDSEEyNTYiLCJ"
          + "jb2RlIjoid2h5bm90IiwiaXNzdWVkX2F0IjoxNDIyNTAyMDkyLCJ1c2VyX2lkIjoiMTIzIn0";

  @Test
  public void testWebViewHandlesSuccess() throws Exception {
    String authenticationTokenString = getEncodedAuthTokenString();
    mockTryAuthorize();
    Bundle bundle = new Bundle();
    bundle.putString("access_token", ACCESS_TOKEN);
    bundle.putString("authentication_token", authenticationTokenString);
    bundle.putString("expires_in", String.format("%d", EXPIRES_IN_DELTA));
    bundle.putString("code", "Something else");
    bundle.putString("signed_request", SIGNED_REQUEST_STR);

    WebViewLoginMethodHandler handler = new WebViewLoginMethodHandler(mockLoginClient);

    LoginClient.Request request = createRequest();
    handler.onWebDialogComplete(request, bundle, null);

    ArgumentCaptor<LoginClient.Result> resultArgumentCaptor =
        ArgumentCaptor.forClass(LoginClient.Result.class);
    verify(mockLoginClient, times(1)).completeAndValidate(resultArgumentCaptor.capture());

    LoginClient.Result result = resultArgumentCaptor.getValue();
    assertNotNull(result);
    assertEquals(LoginClient.Result.Code.SUCCESS, result.code);

    AccessToken token = result.token;
    assertNotNull(token);
    assertEquals(ACCESS_TOKEN, token.getToken());
    assertDateDiffersWithinDelta(new Date(), token.getExpires(), EXPIRES_IN_DELTA * 1000, 1000);
    TestUtils.assertSamePermissions(PERMISSIONS, token.getPermissions());

    AuthenticationToken authenticationToken = result.authenticationToken;
    assertEquals(authenticationTokenString, authenticationToken.getToken());
  }

  @Test
  public void testIGWebViewHandlesSuccess() throws Exception {
    mockTryAuthorize();
    Bundle bundle = new Bundle();
    bundle.putString("access_token", ACCESS_TOKEN);
    bundle.putString("graph_domain", "instagram");
    bundle.putString("signed_request", SIGNED_REQUEST_STR);

    WebViewLoginMethodHandler handler = new WebViewLoginMethodHandler(mockLoginClient);

    LoginClient.Request igRequest = createIGWebRequest();
    handler.tryAuthorize(igRequest);
    handler.onWebDialogComplete(igRequest, bundle, null);

    ArgumentCaptor<LoginClient.Result> resultArgumentCaptor =
        ArgumentCaptor.forClass(LoginClient.Result.class);
    verify(mockLoginClient, times(1)).completeAndValidate(resultArgumentCaptor.capture());

    LoginClient.Result result = resultArgumentCaptor.getValue();
    assertNotNull(result);
    assertEquals(LoginClient.Result.Code.SUCCESS, result.code);

    AccessToken token = result.token;
    assertNotNull(token);
    assertEquals(ACCESS_TOKEN, token.getToken());
    assertEquals(USER_ID, token.getUserId());
    assertEquals("instagram", token.getGraphDomain());
    assertEquals(AccessTokenSource.INSTAGRAM_WEB_VIEW, token.getSource());
    TestUtils.assertSamePermissions(PERMISSIONS, token.getPermissions());
  }

  @Test
  public void testWebViewHandlesCancel() {
    WebViewLoginMethodHandler handler = new WebViewLoginMethodHandler(mockLoginClient);

    LoginClient.Request request = createRequest();
    handler.onWebDialogComplete(request, null, new FacebookOperationCanceledException());

    ArgumentCaptor<LoginClient.Result> resultArgumentCaptor =
        ArgumentCaptor.forClass(LoginClient.Result.class);
    verify(mockLoginClient, times(1)).completeAndValidate(resultArgumentCaptor.capture());
    LoginClient.Result result = resultArgumentCaptor.getValue();

    assertNotNull(result);
    assertEquals(LoginClient.Result.Code.CANCEL, result.code);
    assertNull(result.token);
    assertNotNull(result.errorMessage);
  }

  @Test
  public void testWebViewHandlesError() {
    WebViewLoginMethodHandler handler = new WebViewLoginMethodHandler(mockLoginClient);

    LoginClient.Request request = createRequest();
    handler.onWebDialogComplete(request, null, new FacebookException(ERROR_MESSAGE));

    ArgumentCaptor<LoginClient.Result> resultArgumentCaptor =
        ArgumentCaptor.forClass(LoginClient.Result.class);
    verify(mockLoginClient, times(1)).completeAndValidate(resultArgumentCaptor.capture());
    LoginClient.Result result = resultArgumentCaptor.getValue();

    assertNotNull(result);
    assertEquals(LoginClient.Result.Code.ERROR, result.code);
    assertNull(result.token);
    assertNotNull(result.errorMessage);
    assertEquals(ERROR_MESSAGE, result.errorMessage);
  }

  @Test
  public void testFromDialog() {
    List<String> permissions = Utility.arrayList("stream_publish", "go_outside_and_play");
    String token = "AnImaginaryTokenValue";
    String userId = "1000";

    Bundle bundle = new Bundle();
    bundle.putString("access_token", token);
    bundle.putString("expires_in", "60");
    bundle.putString("signed_request", SIGNED_REQUEST_STR);

    AccessToken accessToken =
        LoginMethodHandler.createAccessTokenFromWebBundle(
            permissions, bundle, AccessTokenSource.WEB_VIEW, "1234");
    TestUtils.assertSamePermissions(permissions, accessToken);
    assertEquals(token, accessToken.getToken());
    assertEquals(AccessTokenSource.WEB_VIEW, accessToken.getSource());
    assertTrue(!accessToken.isExpired());
  }

  @Test
  public void testFromSSOWithExpiresString() {
    List<String> permissions = Utility.arrayList("stream_publish", "go_outside_and_play");
    String token = "AnImaginaryTokenValue";

    Intent intent = new Intent();
    intent.putExtra("access_token", token);
    intent.putExtra("expires_in", "60");
    intent.putExtra("extra_extra", "Something unrelated");
    intent.putExtra("signed_request", SIGNED_REQUEST_STR);

    AccessToken accessToken =
        LoginMethodHandler.createAccessTokenFromWebBundle(
            permissions, intent.getExtras(), AccessTokenSource.FACEBOOK_APPLICATION_WEB, "1234");

    TestUtils.assertSamePermissions(permissions, accessToken);
    assertEquals(token, accessToken.getToken());
    assertEquals(AccessTokenSource.FACEBOOK_APPLICATION_WEB, accessToken.getSource());
    assertTrue(!accessToken.isExpired());
  }

  @Test
  public void testFromSSOWithExpiresLong() {
    List<String> permissions = Utility.arrayList("stream_publish", "go_outside_and_play");
    String token = "AnImaginaryTokenValue";

    Intent intent = new Intent();
    intent.putExtra("access_token", token);
    intent.putExtra("expires_in", 60L);
    intent.putExtra("extra_extra", "Something unrelated");
    intent.putExtra("signed_request", SIGNED_REQUEST_STR);

    AccessToken accessToken =
        LoginMethodHandler.createAccessTokenFromWebBundle(
            permissions, intent.getExtras(), AccessTokenSource.FACEBOOK_APPLICATION_WEB, "1234");
    TestUtils.assertSamePermissions(permissions, accessToken);
    assertEquals(token, accessToken.getToken());
    assertEquals(AccessTokenSource.FACEBOOK_APPLICATION_WEB, accessToken.getSource());
    assertTrue(!accessToken.isExpired());
  }

  private void mockTryAuthorize() throws Exception {
    mockStatic(FacebookSdk.class);
    when(FacebookSdk.isInitialized()).thenReturn(true);
    when(FacebookSdk.getApplicationId()).thenReturn(AuthenticationTokenTestUtil.APP_ID);
    mockStatic(AccessToken.class);
    when(AccessToken.getCurrentAccessToken()).thenReturn(null);
    FacebookDialogFragment dialogFragment = mock(FacebookDialogFragment.class);
    whenNew(FacebookDialogFragment.class).withAnyArguments().thenReturn(dialogFragment);
  }
}
