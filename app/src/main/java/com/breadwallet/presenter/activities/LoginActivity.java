package com.breadwallet.presenter.activities;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.breadwallet.R;
import com.breadwallet.presenter.activities.camera.ScanQRActivity;
import com.breadwallet.presenter.activities.util.BRActivity;
import com.breadwallet.presenter.customviews.BRKeyboard;
import com.breadwallet.presenter.entities.CurrencyEntity;
import com.breadwallet.presenter.interfaces.BRAuthCompletion;
import com.breadwallet.tools.animation.BRAnimator;
import com.breadwallet.tools.animation.BRDialog;
import com.breadwallet.tools.animation.SpringAnimator;
import com.breadwallet.tools.manager.AnalyticsManager;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.security.AuthManager;
import com.breadwallet.tools.security.BRKeyStore;
import com.breadwallet.tools.sqlite.CurrencyDataSource;
import com.breadwallet.tools.threads.BRExecutor;
import com.breadwallet.tools.util.BRConstants;
import com.breadwallet.tools.util.BRCurrency;
import com.breadwallet.wallet.BRWalletManager;
import com.platform.APIClient;

import java.math.BigDecimal;
import java.util.Locale;

import timber.log.Timber;

import static com.breadwallet.tools.util.BRConstants.PLATFORM_ON;
import static com.breadwallet.tools.util.BRConstants.SCANNER_REQUEST;

public class LoginActivity extends BRActivity {
    private BRKeyboard keyboard;
    private LinearLayout pinLayout;
    private View dot1;
    private View dot2;
    private View dot3;
    private View dot4;
    private View dot5;
    private View dot6;
    private StringBuilder pin = new StringBuilder();
    private int pinLimit = 6;
    private static LoginActivity app;

    private ImageView unlockedImage;
    private TextView unlockedText;
    private TextView enterPinLabel;
    private TextView versionText;
    private ViewGroup ltcPriceConstraintLayout;

    private TextView ltcPriceTextView;
    private TextView ltcPriceDescTextView; 

    private ImageButton fingerPrint;
    public static boolean appVisible = false;
    private boolean inputAllowed = true;

    public static LoginActivity getApp() {
        return app;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);
        String pin = BRKeyStore.getPinCode(this);
        if (pin.isEmpty() || (pin.length() != 6 && pin.length() != 4)) {
            Intent intent = new Intent(this, SetPinActivity.class);
            intent.putExtra("noPin", true);
            startActivity(intent);
            if (!LoginActivity.this.isDestroyed()) finish();
            return;
        }
        if (BRKeyStore.getPinCode(this).length() == 4) pinLimit = 4;

        keyboard = findViewById(R.id.brkeyboard);
        pinLayout = findViewById(R.id.pinLayout);
        fingerPrint = findViewById(R.id.fingerprint_icon);
        versionText = findViewById(R.id.version_text);

        ltcPriceTextView = findViewById(R.id.ltcPriceTextView);
        ltcPriceDescTextView = findViewById(R.id.ltcPriceDescTextView);

        unlockedImage = findViewById(R.id.unlocked_image);
        unlockedText = findViewById(R.id.unlocked_text);
        enterPinLabel = findViewById(R.id.enter_pin_label);
        ltcPriceConstraintLayout = findViewById(R.id.ltcPriceConstraintLayout);

        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);
        dot4 = findViewById(R.id.dot4);
        dot5 = findViewById(R.id.dot5);
        dot6 = findViewById(R.id.dot6);

        keyboard.addOnInsertListener(key -> handleClick(key));
        keyboard.setBRButtonTextColor(R.color.white);
        keyboard.setShowDot(false);

        keyboard.setCustomButtonBackgroundColor(10, getColor(android.R.color.transparent));
        keyboard.setDeleteImage(getDrawable(R.drawable.ic_delete_white));

        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e);
        }
        String verName = pInfo != null ? pInfo.versionName : " ";
        versionText.setText(String.format(Locale.US, "%1$s", verName));

        findViewById(R.id.scanQRCodeImgBut).setOnClickListener(v -> {
            if (!BRAnimator.isClickAllowed()) return;
            try {
                // Check if the camera permission is granted
                if (ContextCompat.checkSelfPermission(app,
                        Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(app,
                            Manifest.permission.CAMERA)) {
                        BRDialog.showCustomDialog(app, getString(R.string.Send_cameraUnavailabeTitle_android),
                                getString(R.string.Send_cameraUnavailabeMessage_android), getString(R.string.AccessibilityLabels_close), null, brDialogView -> brDialogView.dismiss(), null, null, 0);
                    } else {
                        ActivityCompat.requestPermissions(app,
                                new String[]{Manifest.permission.CAMERA},
                                BRConstants.CAMERA_REQUEST_ID);
                    }
                } else {
                    Intent intent = new Intent(app, ScanQRActivity.class);
                    app.startActivityForResult(intent, SCANNER_REQUEST);
                    app.overridePendingTransition(R.anim.fade_up, 0);
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        });

        final boolean useFingerprint = AuthManager.isFingerPrintAvailableAndSetup(this) && BRSharedPrefs.getUseFingerprint(this);
        fingerPrint.setVisibility(useFingerprint ? View.VISIBLE : View.GONE);

        if (useFingerprint) {
            fingerPrint.setOnClickListener(v -> AuthManager.getInstance().authPrompt(LoginActivity.this, "", "", false, true, new BRAuthCompletion() {
                @Override
                public void onComplete() {
                    unlockWallet();
                    AnalyticsManager.logCustomEvent(BRConstants._20200217_DLWB);
                }

                @Override
                public void onCancel() {
                }
            }));
        }

        new Handler().postDelayed(() -> {
            if (fingerPrint != null && useFingerprint)
                fingerPrint.performClick();
        }, 500);

        setCurrentLtcPrice();
    }

    private void setCurrentLtcPrice() {
        String iso = BRSharedPrefs.getIso(this);

        String formattedCurrency = null;
        CurrencyEntity currency = CurrencyDataSource.getInstance(this).getCurrencyByIso(iso);
        if (currency != null) {
            final BigDecimal roundedPriceAmount = new BigDecimal(currency.rate).multiply(new BigDecimal(100))
                    .divide(new BigDecimal(100), 2, BRConstants.ROUNDING_MODE);
            formattedCurrency = BRCurrency.getFormattedCurrencyString(this, iso, roundedPriceAmount);
        } else {
            Timber.w("The currency related to %s is NULL", iso);
        }

        if (formattedCurrency != null) {
            ltcPriceTextView.setText(getString(R.string.Login_ltcPrice, formattedCurrency));
            ltcPriceDescTextView.setText(getString(R.string.Login_currentLtcPrice, iso));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDots();

        appVisible = true;
        app = this;
        inputAllowed = true;
        if (!BRWalletManager.getInstance().isCreated()) {
            BRExecutor.getInstance().forBackgroundTasks().execute(() -> BRWalletManager.getInstance().initWallet(LoginActivity.this));
        }
        if (PLATFORM_ON)
            APIClient.getInstance(this).updatePlatform();
    }

    @Override
    protected void onPause() {
        super.onPause();
        appVisible = false;
    }

    private void handleClick(String key) {
        if (!inputAllowed) {
            Timber.d("handleClick: input not allowed");
            return;
        }
        if (key == null) {
            Timber.d("handleClick: key is null! ");
            return;
        }

        if (key.isEmpty()) {
            handleDeleteClick();
        } else if (Character.isDigit(key.charAt(0))) {
            handleDigitClick(Integer.parseInt(key.substring(0, 1)));
        } else {
            Timber.d("handleClick: oops: %s", key);
        }
    }


    private void handleDigitClick(Integer dig) {
        if (pin.length() < pinLimit)
            pin.append(dig);
        updateDots();
    }

    private void handleDeleteClick() {
        if (pin.length() > 0)
            pin.deleteCharAt(pin.length() - 1);
        updateDots();
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            super.onBackPressed();
        } else {
            finishAffinity();
        }
    }

    private void unlockWallet() {
        pin = new StringBuilder();
        ltcPriceConstraintLayout.animate().translationY(-600).setInterpolator(new AccelerateInterpolator());
        pinLayout.animate().translationY(-2000).setInterpolator(new AccelerateInterpolator());
        enterPinLabel.animate().translationY(-1800).setInterpolator(new AccelerateInterpolator());
        keyboard.animate().translationY(2000).setInterpolator(new AccelerateInterpolator());
        unlockedImage.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                new Handler().postDelayed(() -> {
                    Intent intent = new Intent(LoginActivity.this, BreadActivity.class);
                    startActivity(intent);
                    overridePendingTransition(R.anim.fade_up, R.anim.fade_down);
                    if (!LoginActivity.this.isDestroyed()) {
                        LoginActivity.this.finish();

                    }
                }, 400);
            }
        });
        unlockedText.animate().alpha(1f);
    }

    private void showFailedToUnlock() {
        SpringAnimator.failShakeAnimation(LoginActivity.this, pinLayout);
        pin = new StringBuilder();
        new Handler().postDelayed(() -> {
            inputAllowed = true;
            updateDots();
        }, 1000);
    }

    private void updateDots() {
        AuthManager.getInstance().updateDots(this, pinLimit, pin.toString(), dot1, dot2, dot3, dot4, dot5, dot6, R.drawable.ic_pin_dot_white,
                () -> {
                    inputAllowed = false;
                    if (AuthManager.getInstance().checkAuth(pin.toString(), LoginActivity.this)) {
                        AuthManager.getInstance().authSuccess(LoginActivity.this);
                        unlockWallet();
                        AnalyticsManager.logCustomEvent(BRConstants._20200217_DLWP);
                    } else {
                        AuthManager.getInstance().authFail(LoginActivity.this);
                        showFailedToUnlock();
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case BRConstants.CAMERA_REQUEST_ID: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    BRAnimator.openScanner(this, BRConstants.SCANNER_REQUEST);
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    Timber.i("onRequestPermissionsResult: permission isn't granted for: %s", requestCode);
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
    }
}
