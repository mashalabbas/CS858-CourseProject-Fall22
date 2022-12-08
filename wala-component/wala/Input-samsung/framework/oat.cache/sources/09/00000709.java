package com.android.server;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.AppGlobals;
import android.app.KeyguardManager;
import android.app.StatusBarManager;
import android.app.admin.IDevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDump;
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothCallback;
import android.bluetooth.IBluetoothDisableBleCallback;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothHeadset;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothManagerCallback;
import android.bluetooth.IBluetoothProfileServiceConnection;
import android.bluetooth.IBluetoothSinkServiceChangeCallback;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.CursorWindowAllocationException;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HidlSupport;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.UserManagerInternal;
import android.provider.Settings;
import android.sec.enterprise.auditlog.AuditLog;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Slog;
import android.util.StatsLog;
import android.view.KeyEvent;
import com.android.server.enterprise.apn.ApnSettingsPolicy;
import com.android.server.pm.PersonaServiceHelper;
import com.android.server.pm.UserRestrictionsUtils;
import com.samsung.android.codecsolution.CodecSolutionManagerService;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.hqm.HqmDBHelper;
import com.samsung.app.video.editor.external.BuildConfig;
import com.sec.ims.configuration.DATA;
import com.sec.vsim.namespaces.NSDSNamespaces;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import vendor.samsung.hardware.bluetooth.V1_0.IBTVendor;

/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
public class BluetoothManagerService extends IBluetoothManager.Stub {
    private static final int ACTIVE_LOG_MAX_SIZE = 20;
    private static final int ADD_PROXY_DELAY_MS = 100;
    private static final String BLUETOOTH_ADMIN_PERM = "android.permission.BLUETOOTH_ADMIN";
    private static final int BLUETOOTH_OFF = 0;
    private static final int BLUETOOTH_ON_AIRPLANE = 2;
    private static final int BLUETOOTH_ON_BLUETOOTH = 1;
    private static final String BLUETOOTH_PERM = "android.permission.BLUETOOTH";
    private static final String BLUETOOTH_PROPERTY_SECURITY_POLICY = "service.bt.security.policy.mode";
    private static final int CRASHED = 1;
    private static final int CRASH_LOG_MAX_SIZE = 100;
    private static final int CRASH_NONE = 0;
    private static final boolean DBG_ADMIN = true;
    private static final int ERROR_RESTART_TIME_MS = 3000;
    private static final int MAX_ERROR_RESTART_RETRIES = 6;
    private static final int MESSAGE_ADD_PROXY_DELAYED = 400;
    private static final int MESSAGE_BIND_PROFILE_SERVICE = 401;
    private static final int MESSAGE_BLUETOOTH_SERVICE_CONNECTED = 40;
    private static final int MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED = 41;
    private static final int MESSAGE_BLUETOOTH_STATE_CHANGE = 60;
    private static final int MESSAGE_BT_IT_POLICY = 5;
    private static final int MESSAGE_CHINA_BT_ENABLE_SECURITY_POPUP = 700;
    private static final int MESSAGE_DISABLE = 2;
    private static final int MESSAGE_ENABLE = 1;
    private static final int MESSAGE_GET_NAME_AND_ADDRESS = 200;
    private static final int MESSAGE_GOOGLE_BLE_STATE_CHANGE = 702;
    private static final int MESSAGE_REGISTER_ADAPTER = 20;
    private static final int MESSAGE_REGISTER_SINK_SERVICE_STATE_CALLBACK = 1020;
    private static final int MESSAGE_REGISTER_STATE_CHANGE_CALLBACK = 30;
    private static final int MESSAGE_REGISTER_STATE_DISBLE_BLE_CALLBACK = 1018;
    private static final int MESSAGE_RESTART_BLUETOOTH_SERVICE = 42;
    private static final int MESSAGE_RESTORE_USER_SETTING = 500;
    private static final int MESSAGE_SAMSUNG_BLE_STATE_CHANGE = 703;
    private static final int MESSAGE_SEND_SINK_SERVICE_STATE_CHANGE = 1022;
    private static final int MESSAGE_TIMEOUT_BIND = 100;
    private static final int MESSAGE_TIMEOUT_UNBIND = 101;
    private static final int MESSAGE_UNREGISTER_ADAPTER = 21;
    private static final int MESSAGE_UNREGISTER_SINK_SERVICE_STATE_CALLBACK = 1021;
    private static final int MESSAGE_UNREGISTER_STATE_CHANGE_CALLBACK = 31;
    private static final int MESSAGE_UNREGISTER_STATE_DISBLE_BLE_CALLBACK = 1019;
    private static final int MESSAGE_USER_SWITCHED = 300;
    private static final int MESSAGE_USER_UNLOCKED = 301;
    private static final int RESTORE_SETTING_TO_OFF = 0;
    private static final int RESTORE_SETTING_TO_ON = 1;
    private static final String SECURE_SETTINGS_BLUETOOTH_ADDRESS = "bluetooth_address";
    private static final String SECURE_SETTINGS_BLUETOOTH_ADDR_VALID = "bluetooth_addr_valid";
    private static final String SECURE_SETTINGS_BLUETOOTH_NAME = "bluetooth_name";
    private static final int SERVICE_IBLUETOOTH = 1;
    private static final int SERVICE_IBLUETOOTHGATT = 2;
    private static final int SERVICE_RESTART_TIME_MS = 200;
    private static final int STATE_BTMGR_BLE_OFF = 0;
    private static final int STATE_BTMGR_BLE_ON = 2;
    private static final int STATE_BTMGR_BLE_TURNING_OFF = 3;
    private static final int STATE_BTMGR_BLE_TURNING_ON = 1;
    private static final String TAG = "BluetoothManagerService";
    private static final int TIMEOUT_BIND_MS = 3000;
    private static final int TYPE_BLE = 1;
    private static final int TYPE_BLUETOOTH = 0;
    private static final int USER_SWITCHED_TIME_MS = 200;
    File logDir;
    private IBTVendor mBTvendor;
    private AlertDialog mBtEnablePopUp;
    private final RemoteCallbackList<IBluetoothManagerCallback> mCallbacks;
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final RemoteCallbackList<IBluetoothDisableBleCallback> mDisableBleCallback;
    private boolean mEnableExternal;
    private long mLastEnabledTime;
    private final boolean mPermissionReviewRequired;
    private boolean mQuietEnableExternal;
    private final RemoteCallbackList<IBluetoothSinkServiceChangeCallback> mSinkServiceChangeCallback;
    private final RemoteCallbackList<IBluetoothStateChangeCallback> mStateChangeCallbacks;
    private final int mSystemUiUid;
    File mainF;
    File subF;
    private static final boolean DBG = Debug.semIsProductDev();
    private static int mPolicy = 2;
    private static int mBleAppCount = 0;
    private static int mSamsungBleAppCount = 0;
    private static boolean mShutdown = false;
    private static StringBuilder subBuffer = new StringBuilder();
    private static String mainBuffer = new String();
    private static int limit = 524288;
    private static boolean isEnablePopup = false;
    private final ReentrantReadWriteLock mBluetoothLock = new ReentrantReadWriteLock();
    private boolean mQuietEnable = false;
    private boolean mUsingSSUpdate = false;
    private int mAdapterServiceCrashed = 0;
    private int mSamsungBleState = 0;
    private int mGoogleBleState = 0;
    private boolean mIsBootCompleted = false;
    private Object mBleLock = new Object();
    private final LinkedList<ActiveLog> mActiveLogs = new LinkedList<>();
    private final LinkedList<Long> mCrashTimestamps = new LinkedList<>();
    private Map<IBinder, ClientDeathRecipient> mBleApps = new ConcurrentHashMap();
    private Map<IBinder, ClientDeathRecipient> mSamsungBleApps = new ConcurrentHashMap();
    private final Map<Integer, ProfileServiceConnections> mProfileServices = new HashMap();
    private final Map<String, Integer> mHeadsetProxies = new HashMap();
    private final Map<String, Integer> mMaxHeadsetProxies = new HashMap();
    String logDirp = "/data/misc/bluedroiddump";
    String mainBfp = "/data/misc/bluedroiddump/mainBuffer.log";
    String subBfp = "/data/misc/bluedroiddump/subBuffer.log";
    private String chinaThirdPartyAppName = "";
    private final IBluetoothCallback mBluetoothCallback = new IBluetoothCallback.Stub() { // from class: com.android.server.BluetoothManagerService.1
        public void onBluetoothStateChange(int prevState, int newState) throws RemoteException {
            Message msg = BluetoothManagerService.this.mHandler.obtainMessage(60, prevState, newState);
            BluetoothManagerService.this.mHandler.sendMessage(msg);
        }
    };
    private final UserManagerInternal.UserRestrictionsListener mUserRestrictionsListener = new UserManagerInternal.UserRestrictionsListener() { // from class: com.android.server.BluetoothManagerService.2
        public void onUserRestrictionsChanged(int userId, Bundle newRestrictions, Bundle prevRestrictions) {
            if (UserRestrictionsUtils.restrictionsChanged(prevRestrictions, newRestrictions, "no_bluetooth_sharing")) {
                BluetoothManagerService.this.updateOppLauncherComponentState(userId, newRestrictions.getBoolean("no_bluetooth_sharing"));
            }
            if (userId == 0 && UserRestrictionsUtils.restrictionsChanged(prevRestrictions, newRestrictions, "no_bluetooth")) {
                if (userId != 0 || !newRestrictions.getBoolean("no_bluetooth")) {
                    BluetoothManagerService.this.updateOppLauncherComponentState(userId, newRestrictions.getBoolean("no_bluetooth_sharing"));
                    return;
                }
                BluetoothManagerService.this.updateOppLauncherComponentState(userId, true);
                BluetoothManagerService.this.mEnableExternal = false;
                BluetoothManagerService.this.sendDisableMsg(3, BluetoothManagerService.this.mContext.getPackageName());
            }
        }
    };
    private final ContentObserver mAirplaneModeObserver = new ContentObserver(null) { // from class: com.android.server.BluetoothManagerService.3
        @Override // android.database.ContentObserver
        public void onChange(boolean unused) {
            ReentrantReadWriteLock.ReadLock readLock;
            synchronized (this) {
                BluetoothDump.BtLog("BluetoothManagerService -- received ACTION_AIRPLANE_MODE_CHANGED, mode is " + BluetoothManagerService.this.isAirplaneModeOn() + " " + BluetoothManagerService.this.isBluetoothPersistedStateOn() + " " + BluetoothManagerService.this.isBluetoothPersistedStateOnBluetooth() + " " + BluetoothManagerService.this.mQuietEnable);
                if (BluetoothManagerService.this.isAirplaneModeOn()) {
                    BluetoothManagerService.this.mEnableExternal = false;
                } else if (BluetoothManagerService.this.isBluetoothPersistedStateOn() || BluetoothManagerService.this.mQuietEnable) {
                    BluetoothManagerService.this.mEnableExternal = true;
                }
                if (BluetoothManagerService.this.isBluetoothPersistedStateOn()) {
                    if (BluetoothManagerService.this.isAirplaneModeOn()) {
                        BluetoothManagerService.this.persistBluetoothSetting(2);
                    } else {
                        BluetoothManagerService.this.persistBluetoothSetting(1);
                    }
                }
                try {
                    BluetoothManagerService.this.mBluetoothLock.readLock().lock();
                    int st = BluetoothManagerService.this.mBluetooth != null ? BluetoothManagerService.this.mBluetooth.getState() : 10;
                    BluetoothManagerService.this.mBluetoothLock.readLock().unlock();
                    Slog.d(BluetoothManagerService.TAG, "Airplane Mode change - current state:  " + BluetoothAdapter.nameForState(st));
                    if (BluetoothManagerService.this.isAirplaneModeOn()) {
                        BluetoothManagerService.this.clearBleApps();
                        if (st == 15) {
                            try {
                                BluetoothManagerService.this.mBluetoothLock.readLock().lock();
                                if (BluetoothManagerService.this.mBluetooth != null) {
                                    BluetoothManagerService.this.addActiveLog(2, BluetoothManagerService.this.mContext.getPackageName(), false);
                                    BluetoothManagerService.this.mBluetooth.onBrEdrDown();
                                    BluetoothManagerService.this.mEnableExternal = false;
                                }
                                readLock = BluetoothManagerService.this.mBluetoothLock.readLock();
                            } catch (RemoteException e) {
                                Slog.e(BluetoothManagerService.TAG, "Unable to call onBrEdrDown", e);
                                readLock = BluetoothManagerService.this.mBluetoothLock.readLock();
                            }
                            readLock.unlock();
                        } else if (st == 12) {
                            BluetoothManagerService.this.sendDisableMsg(2, BluetoothManagerService.this.mContext.getPackageName());
                        } else if (st == 11) {
                            Slog.d(BluetoothManagerService.TAG, "Calling disable in STATE_TURNING_ON states");
                            BluetoothManagerService.this.sendDisableMsg(2, BluetoothManagerService.this.mContext.getPackageName());
                        }
                    } else if (st != 12 && BluetoothManagerService.this.mEnableExternal) {
                        if (st == 15) {
                            try {
                                if (BluetoothManagerService.this.mBluetooth != null) {
                                    Slog.d(BluetoothManagerService.TAG, "Calling enable current state BLE_ON");
                                    BluetoothManagerService.this.mBluetooth.onLeServiceUp();
                                }
                            } catch (RemoteException e2) {
                                Slog.e(BluetoothManagerService.TAG, "Unable to call onServiceUp", e2);
                            }
                        } else {
                            Slog.d(BluetoothManagerService.TAG, "Calling enable BLE BREDR");
                            BluetoothManagerService.this.sendEnableMsg(BluetoothManagerService.this.mQuietEnable, 2, BluetoothManagerService.this.mContext.getPackageName());
                        }
                    }
                } catch (RemoteException e3) {
                    Slog.e(BluetoothManagerService.TAG, "Unable to call getState", e3);
                    BluetoothManagerService.this.mBluetoothLock.readLock().unlock();
                }
            }
        }
    };
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.server.BluetoothManagerService.4
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.bluetooth.adapter.action.LOCAL_NAME_CHANGED".equals(action)) {
                String newName = intent.getStringExtra("android.bluetooth.adapter.extra.LOCAL_NAME");
                if (BluetoothManagerService.DBG) {
                    Slog.d(BluetoothManagerService.TAG, "Bluetooth Adapter name changed to " + newName);
                }
                if (newName != null) {
                    BluetoothManagerService.this.storeNameAndAddress(newName, null);
                }
            } else if ("android.bluetooth.adapter.action.BLUETOOTH_ADDRESS_CHANGED".equals(action)) {
                String newAddress = intent.getStringExtra("android.bluetooth.adapter.extra.BLUETOOTH_ADDRESS");
                if (newAddress != null) {
                    if (BluetoothManagerService.DBG) {
                        Slog.d(BluetoothManagerService.TAG, "Bluetooth Adapter address changed to " + newAddress);
                    }
                    BluetoothManagerService.this.storeNameAndAddress(null, newAddress);
                } else if (BluetoothManagerService.DBG) {
                    Slog.e(BluetoothManagerService.TAG, "No Bluetooth Adapter address parameter found");
                }
            } else {
                if ("android.os.action.SETTING_RESTORED".equals(action)) {
                    String name = intent.getStringExtra("setting_name");
                    if ("bluetooth_on".equals(name)) {
                        String prevValue = intent.getStringExtra("previous_value");
                        String newValue = intent.getStringExtra("new_value");
                        if (BluetoothManagerService.DBG) {
                            Slog.d(BluetoothManagerService.TAG, "ACTION_SETTING_RESTORED with BLUETOOTH_ON, prevValue=" + prevValue + ", newValue=" + newValue);
                        }
                        if (newValue != null && prevValue != null && !prevValue.equals(newValue)) {
                            Message msg = BluetoothManagerService.this.mHandler.obtainMessage(500, newValue.equals("0") ? 0 : 1, 0);
                            BluetoothManagerService.this.mHandler.sendMessage(msg);
                        }
                    }
                } else if ("android.intent.action.CLOSE_SYSTEM_DIALOGS".equals(action)) {
                    if (SemCscFeature.getInstance().getString("CscFeature_Common_ConfigLocalSecurityPolicy").equals("ChinaNalSecurity") && BluetoothManagerService.this.mBtEnablePopUp != null && BluetoothManagerService.this.mBtEnablePopUp.isShowing()) {
                        Slog.d(BluetoothManagerService.TAG, "dismiss dialog");
                        BluetoothManagerService.this.mBtEnablePopUp.cancel();
                    }
                } else if (action.equals("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED")) {
                    boolean isNotiLockScreen = intent.getBooleanExtra("isNotiFromLockScreen", false);
                    boolean BtPolicyChanged = intent.getBooleanExtra("isBTChanged", false);
                    if (isNotiLockScreen) {
                        if (BluetoothManagerService.DBG) {
                            Slog.e(BluetoothManagerService.TAG, "EXTRA_NOTIFY_FROM_LOCKSCREEN");
                        }
                    } else if (BtPolicyChanged) {
                        Slog.w(BluetoothManagerService.TAG, "IT policy is changed for BT");
                        BluetoothDump.BtLog("BluetoothManagerService -- IT policy is changed for BT");
                        if (BluetoothManagerService.this.isEnabled()) {
                            BluetoothManagerService.this.persistBluetoothSetting(0);
                            BluetoothManagerService.this.mEnableExternal = false;
                            BluetoothManagerService.this.sendDisableMsg(10, BluetoothManagerService.this.mContext.getPackageName());
                            BluetoothManagerService.this.checkItPolicy(false);
                        }
                    }
                } else if ("android.intent.action.BOOT_COMPLETED".equals(action)) {
                    BluetoothManagerService.this.mIsBootCompleted = true;
                }
            }
        }
    };
    private BluetoothServiceConnection mConnection = new BluetoothServiceConnection();
    private final BluetoothHandler mHandler = new BluetoothHandler(IoThread.get().getLooper());
    private int mCrashes = 0;
    private IBluetooth mBluetooth = null;
    private IBinder mBluetoothBinder = null;
    private IBluetoothGatt mBluetoothGatt = null;
    private boolean mBinding = false;
    private boolean mUnbinding = false;
    private boolean mSwitching = false;
    private boolean mEnable = false;
    private int mState = 10;
    private String mAddress = null;
    private String mName = null;
    private int mErrorRecoveryRetryCounter = 0;

    static /* synthetic */ int access$2306() {
        int i = mBleAppCount - 1;
        mBleAppCount = i;
        return i;
    }

    static /* synthetic */ int access$2406() {
        int i = mSamsungBleAppCount - 1;
        mSamsungBleAppCount = i;
        return i;
    }

    static /* synthetic */ int access$5708(BluetoothManagerService x0) {
        int i = x0.mErrorRecoveryRetryCounter;
        x0.mErrorRecoveryRetryCounter = i + 1;
        return i;
    }

    private static String getStrVer(int version) {
        switch (version) {
            case 0:
                return "1.0";
            case 1:
                return "1.1";
            case 2:
                return CodecSolutionManagerService.VERSION;
            case 3:
                return "2.0";
            case 4:
                return "2.1";
            case 5:
                return NSDSNamespaces.NSDSApiVersion.MDSP30;
            case 6:
                return BuildConfig.VERSION_NAME;
            case 7:
                return "4.1";
            case 8:
                return "4.2";
            case 9:
                return "5.0";
            default:
                return "0";
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static CharSequence timeToLog(long timestamp) {
        return DateFormat.format("MM-dd HH:mm:ss", timestamp);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class ActiveLog {
        private boolean mEnable;
        private String mPackageName;
        private int mReason;
        private long mTimestamp;

        ActiveLog(int reason, String packageName, boolean enable, long timestamp) {
            this.mReason = reason;
            this.mPackageName = packageName;
            this.mEnable = enable;
            this.mTimestamp = timestamp;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append((Object) BluetoothManagerService.timeToLog(this.mTimestamp));
            sb.append(this.mEnable ? "  Enabled " : " Disabled ");
            sb.append(" due to ");
            sb.append(BluetoothManagerService.getEnableDisableReasonString(this.mReason));
            sb.append(" by ");
            sb.append(this.mPackageName);
            return sb.toString();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public BluetoothManagerService(Context context) {
        this.mContext = context;
        this.mPermissionReviewRequired = context.getResources().getBoolean(17957017);
        this.mEnableExternal = false;
        this.mContentResolver = context.getContentResolver();
        registerForBleScanModeChange();
        this.mCallbacks = new RemoteCallbackList<>();
        this.mStateChangeCallbacks = new RemoteCallbackList<>();
        this.mSinkServiceChangeCallback = new RemoteCallbackList<>();
        this.mDisableBleCallback = new RemoteCallbackList<>();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.bluetooth.adapter.action.LOCAL_NAME_CHANGED");
        filter.addAction("android.bluetooth.adapter.action.BLUETOOTH_ADDRESS_CHANGED");
        filter.addAction("android.os.action.SETTING_RESTORED");
        filter.addAction("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED");
        filter.addAction("android.intent.action.CLOSE_SYSTEM_DIALOGS");
        filter.setPriority(1000);
        filter.addAction("android.intent.action.BOOT_COMPLETED");
        this.mContext.registerReceiver(this.mReceiver, filter);
        loadStoredNameAndAddress();
        if (isBluetoothPersistedStateOnBluetooth()) {
            if (DBG) {
                Slog.d(TAG, "Startup: Bluetooth persisted state is ON.");
            }
            this.mEnableExternal = true;
        }
        String airplaneModeRadios = Settings.Global.getString(this.mContentResolver, "airplane_mode_radios");
        if (airplaneModeRadios == null || airplaneModeRadios.contains("bluetooth")) {
            this.mContentResolver.registerContentObserver(Settings.Global.getUriFor("airplane_mode_on"), true, this.mAirplaneModeObserver);
        }
        try {
            boolean noHome = this.mContext.getResources().getBoolean(17957013);
            systemUiUid = noHome ? -1 : this.mContext.getPackageManager().getPackageUidAsUser("com.android.systemui", 1048576, 0);
            Slog.d(TAG, "Detected SystemUiUid: " + Integer.toString(systemUiUid));
        } catch (PackageManager.NameNotFoundException e) {
            Slog.w(TAG, "Unable to resolve SystemUI's UID.", e);
        }
        this.mSystemUiUid = systemUiUid;
        setBdroidVersion();
        makeHeadsetProfileServiceConnection();
    }

    private void setBdroidVersion() {
        int verInt = Integer.parseInt(DATA.DM_FIELD_INDEX.SIP_SESSION_TIMER);
        if (DBG) {
            Slog.d(TAG, "setBdroidVersion, Int version = " + verInt);
        }
        SystemProperties.set("persist.service.bdroid.version", getStrVer(verInt));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isAirplaneModeOn() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) == 1;
    }

    private boolean supportBluetoothPersistedState() {
        return this.mContext.getResources().getBoolean(17957053);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isBluetoothPersistedStateOn() {
        if (supportBluetoothPersistedState()) {
            int state = Settings.Global.getInt(this.mContentResolver, "bluetooth_on", -1);
            if (DBG) {
                Slog.d(TAG, "Bluetooth persisted state: " + state);
            }
            return state != 0;
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isBluetoothPersistedStateOnBluetooth() {
        return supportBluetoothPersistedState() && Settings.Global.getInt(this.mContentResolver, "bluetooth_on", 1) == 1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void persistBluetoothSetting(int value) {
        BluetoothDump.BtLog("BluetoothManagerService -- Persisting Bluetooth Setting : " + value);
        if (DBG) {
            Slog.d(TAG, "Persisting Bluetooth Setting : " + value);
        }
        long callingIdentity = Binder.clearCallingIdentity();
        Settings.Global.putInt(this.mContext.getContentResolver(), "bluetooth_on", value);
        Binder.restoreCallingIdentity(callingIdentity);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isNameAndAddressSet() {
        return this.mName != null && this.mAddress != null && this.mName.length() > 0 && this.mAddress.length() > 0;
    }

    private void loadStoredNameAndAddress() {
        if (DBG) {
            Slog.d(TAG, "Loading stored name and address");
        }
        if (this.mContext.getResources().getBoolean(17956906) && Settings.Secure.getInt(this.mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDR_VALID, 0) == 0) {
            if (DBG) {
                Slog.d(TAG, "invalid bluetooth name and address stored");
                return;
            }
            return;
        }
        this.mName = Settings.Secure.getString(this.mContentResolver, SECURE_SETTINGS_BLUETOOTH_NAME);
        this.mAddress = Settings.Secure.getString(this.mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDRESS);
        if (DBG) {
            Slog.d(TAG, "Stored bluetooth Name=" + this.mName + ",Address=" + this.mAddress);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void storeNameAndAddress(String name, String address) {
        if (name != null) {
            Settings.Secure.putString(this.mContentResolver, SECURE_SETTINGS_BLUETOOTH_NAME, name);
            this.mName = name;
            if (DBG) {
                Slog.d(TAG, "Stored Bluetooth name: " + Settings.Secure.getString(this.mContentResolver, SECURE_SETTINGS_BLUETOOTH_NAME));
            }
        }
        if (address != null) {
            Settings.Secure.putString(this.mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDRESS, address);
            this.mAddress = address;
            if (DBG) {
                Slog.d(TAG, "Stored Bluetoothaddress: " + Settings.Secure.getString(this.mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDRESS));
            }
        }
        if (name != null && address != null) {
            Settings.Secure.putInt(this.mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDR_VALID, 1);
        }
    }

    public IBluetooth registerAdapter(IBluetoothManagerCallback callback) {
        if (callback == null) {
            Slog.w(TAG, "Callback is null in registerAdapter");
            return null;
        }
        Message msg = this.mHandler.obtainMessage(20);
        msg.obj = callback;
        msg.arg1 = this.mBluetooth == null ? 1 : 0;
        this.mHandler.sendMessage(msg);
        return this.mBluetooth;
    }

    public void unregisterAdapter(IBluetoothManagerCallback callback) {
        if (callback == null) {
            Slog.w(TAG, "Callback is null in unregisterAdapter");
            return;
        }
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = this.mHandler.obtainMessage(21);
        msg.obj = callback;
        this.mHandler.sendMessage(msg);
    }

    public void registerStateChangeCallback(IBluetoothStateChangeCallback callback) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (callback == null) {
            Slog.w(TAG, "registerStateChangeCallback: Callback is null!");
            return;
        }
        Message msg = this.mHandler.obtainMessage(30);
        msg.obj = callback;
        this.mHandler.sendMessage(msg);
    }

    public void unregisterStateChangeCallback(IBluetoothStateChangeCallback callback) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (callback == null) {
            Slog.w(TAG, "unregisterStateChangeCallback: Callback is null!");
            return;
        }
        Message msg = this.mHandler.obtainMessage(31);
        msg.obj = callback;
        this.mHandler.sendMessage(msg);
    }

    public void registerStateDisableBleCallback(IBluetoothDisableBleCallback callback) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = this.mHandler.obtainMessage(1018);
        msg.obj = callback;
        this.mHandler.sendMessage(msg);
    }

    public void unregisterStateDisableBleCallback(IBluetoothDisableBleCallback callback) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = this.mHandler.obtainMessage(1019);
        msg.obj = callback;
        this.mHandler.sendMessage(msg);
    }

    public void registerSinkServiceChangeCallback(IBluetoothSinkServiceChangeCallback callback) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = this.mHandler.obtainMessage(1020);
        msg.obj = callback;
        this.mHandler.sendMessage(msg);
    }

    public void unregisterSinkServiceChangeCallback(IBluetoothSinkServiceChangeCallback callback) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = this.mHandler.obtainMessage(1021);
        msg.obj = callback;
        this.mHandler.sendMessage(msg);
    }

    public void sendSinkServiceChangeCallback(boolean isUp) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = this.mHandler.obtainMessage(1022);
        msg.obj = Boolean.valueOf(isUp);
        this.mHandler.sendMessage(msg);
    }

    public boolean isEnabled() {
        if (Binder.getCallingUid() != 1000 && !checkIfCallerIsForegroundUser()) {
            Slog.w(TAG, "isEnabled(): not allowed for non-active and non system user");
            return false;
        }
        try {
            try {
                this.mBluetoothLock.readLock().lock();
                if (this.mBluetooth != null) {
                    return this.mBluetooth.isEnabled();
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "isEnabled()", e);
            }
            return false;
        } finally {
            this.mBluetoothLock.readLock().unlock();
        }
    }

    public int getState() {
        if (Binder.getCallingUid() != 1000 && !checkIfCallerIsForegroundUser()) {
            Slog.w(TAG, "getState(): report OFF for non-active and non system user");
            return 10;
        }
        try {
            try {
                this.mBluetoothLock.readLock().lock();
                if (this.mBluetooth != null) {
                    return this.mBluetooth.getState();
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "getState()", e);
            }
            return 10;
        } finally {
            this.mBluetoothLock.readLock().unlock();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class ClientDeathRecipient implements IBinder.DeathRecipient {
        private String mPackageName;
        boolean mIsSamsungApp = false;
        IBinder mToken = null;

        public void setTokenAndAppInfo(IBinder token, boolean isSamsungApp) {
            if (BluetoothManagerService.DBG) {
                Slog.d(BluetoothManagerService.TAG, "setTokenAndAppInfo :: isSamsungApp = " + isSamsungApp + "token is " + token);
            }
            this.mIsSamsungApp = isSamsungApp;
            this.mToken = token;
        }

        ClientDeathRecipient(String packageName) {
            this.mPackageName = packageName;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            int prevBleAppCount;
            int prevSamsungAppCount;
            int bleAppCount;
            int samsungBleAppCount;
            if (BluetoothManagerService.DBG) {
                Slog.d(BluetoothManagerService.TAG, "Binder is dead - unregister " + this.mPackageName);
            }
            synchronized (BluetoothManagerService.this.mBleLock) {
                prevBleAppCount = BluetoothManagerService.mBleAppCount;
                prevSamsungAppCount = BluetoothManagerService.mSamsungBleAppCount;
                bleAppCount = BluetoothManagerService.mBleAppCount;
                samsungBleAppCount = BluetoothManagerService.mSamsungBleAppCount;
            }
            if (bleAppCount > 0) {
                BluetoothManagerService.access$2306();
                bleAppCount--;
            }
            if (this.mToken != null) {
                if (BluetoothManagerService.DBG) {
                    Slog.d(BluetoothManagerService.TAG, "Google : remove token =" + this.mToken);
                }
                BluetoothManagerService.this.mBleApps.remove(this.mToken);
            }
            if (this.mIsSamsungApp) {
                if (BluetoothManagerService.DBG) {
                    Slog.d(BluetoothManagerService.TAG, "Its Samsung App, decrease the count");
                }
                if (samsungBleAppCount > 0) {
                    BluetoothManagerService.access$2406();
                    samsungBleAppCount--;
                }
                if (this.mToken != null) {
                    if (BluetoothManagerService.DBG) {
                        Slog.d(BluetoothManagerService.TAG, "Samsung : remove token =" + this.mToken);
                    }
                    BluetoothManagerService.this.mSamsungBleApps.remove(this.mToken);
                }
            }
            if (prevBleAppCount > prevSamsungAppCount) {
                if (BluetoothManagerService.DBG) {
                    Slog.d(BluetoothManagerService.TAG, "Google app killed");
                }
                if (bleAppCount == samsungBleAppCount) {
                    try {
                        if (BluetoothManagerService.this.mBluetooth != null && BluetoothManagerService.this.mBluetooth.getState() != 12) {
                            if (BluetoothManagerService.DBG) {
                                Slog.d(BluetoothManagerService.TAG, "Stop third party scan, if any");
                            }
                            BluetoothManagerService.this.sendDisableBleCallback();
                        }
                    } catch (RemoteException e) {
                        Slog.e(BluetoothManagerService.TAG, "error when sending ble disable", e);
                    }
                }
            }
            if (bleAppCount == 0 && !BluetoothManagerService.this.mEnableExternal) {
                if (BluetoothManagerService.DBG) {
                    Slog.d(BluetoothManagerService.TAG, "Disabling LE only mode after application crash");
                }
                try {
                    try {
                        BluetoothManagerService.this.mBluetoothLock.readLock().lock();
                        if (BluetoothManagerService.this.mBluetooth != null) {
                            BluetoothManagerService.this.mBluetooth.onBrEdrDown();
                        }
                    } finally {
                        BluetoothManagerService.this.mBluetoothLock.readLock().unlock();
                    }
                } catch (RemoteException e2) {
                    Slog.e(BluetoothManagerService.TAG, "Unable to call onBrEdrDown", e2);
                }
            }
        }

        public String getPackageName() {
            return this.mPackageName;
        }
    }

    public boolean isBleScanAlwaysAvailable() {
        if (!isAirplaneModeOn() || this.mEnableExternal) {
            try {
                return Settings.Global.getInt(this.mContentResolver, "ble_scan_always_enabled") != 0;
            } catch (Settings.SettingNotFoundException e) {
                return false;
            }
        }
        return false;
    }

    private void registerForBleScanModeChange() {
        ContentObserver contentObserver = new ContentObserver(null) { // from class: com.android.server.BluetoothManagerService.5
            @Override // android.database.ContentObserver
            public void onChange(boolean selfChange) {
                int bleAppCount;
                int samsungBleAppCount;
                Slog.d(BluetoothManagerService.TAG, "onChange mBleAppCount : " + BluetoothManagerService.mBleAppCount + " / mSamsungBleAppCount : " + BluetoothManagerService.mSamsungBleAppCount);
                synchronized (BluetoothManagerService.this.mBleLock) {
                    bleAppCount = BluetoothManagerService.mBleAppCount;
                    samsungBleAppCount = BluetoothManagerService.mSamsungBleAppCount;
                }
                if (!BluetoothManagerService.this.isBleScanAlwaysAvailable()) {
                    if (samsungBleAppCount == 0) {
                        if (BluetoothManagerService.DBG) {
                            Slog.d(BluetoothManagerService.TAG, "clear all BleApps");
                        }
                        BluetoothManagerService.this.disableBleScanMode();
                        BluetoothManagerService.this.clearBleApps();
                        try {
                            try {
                                BluetoothManagerService.this.mBluetoothLock.readLock().lock();
                                BluetoothManagerService.this.addActiveLog(1, BluetoothManagerService.this.mContext.getPackageName(), false);
                                if (BluetoothManagerService.this.mBluetooth != null) {
                                    BluetoothManagerService.this.mBluetooth.onBrEdrDown();
                                }
                            } catch (RemoteException e) {
                                Slog.e(BluetoothManagerService.TAG, "error when disabling bluetooth", e);
                            }
                        } finally {
                            BluetoothManagerService.this.mBluetoothLock.readLock().unlock();
                        }
                    } else if (bleAppCount > samsungBleAppCount) {
                        if (BluetoothManagerService.DBG) {
                            Slog.d(BluetoothManagerService.TAG, "Clear Google BleAppCount, Copy SamsungBleApps");
                        }
                        BluetoothManagerService.this.mBleApps.clear();
                        int unused = BluetoothManagerService.mBleAppCount = BluetoothManagerService.mSamsungBleAppCount;
                        BluetoothManagerService.this.sendDisableBleCallback();
                        for (Map.Entry<IBinder, ClientDeathRecipient> entry : BluetoothManagerService.this.mSamsungBleApps.entrySet()) {
                            IBinder token = entry.getKey();
                            String packageName = entry.getValue().getPackageName();
                            ClientDeathRecipient deathRec = new ClientDeathRecipient(packageName);
                            deathRec.setTokenAndAppInfo(token, true);
                            try {
                                token.linkToDeath(deathRec, 0);
                                BluetoothManagerService.this.mBleApps.put(token, deathRec);
                            } catch (RemoteException e2) {
                                throw new IllegalArgumentException("Wake lock is already dead.");
                            }
                        }
                    }
                    Slog.d(BluetoothManagerService.TAG, "onChange changed mBleAppcount : " + BluetoothManagerService.mBleAppCount + " / mSamsungBleAppCount : " + BluetoothManagerService.mSamsungBleAppCount);
                }
            }
        };
        this.mContentResolver.registerContentObserver(Settings.Global.getUriFor("ble_scan_always_enabled"), false, contentObserver);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void disableBleScanMode() {
        try {
            try {
                this.mBluetoothLock.writeLock().lock();
                if (this.mBluetooth != null && this.mBluetooth.getState() != 12) {
                    if (DBG) {
                        Slog.d(TAG, "Reseting the mEnable flag for clean disable");
                    }
                    this.mEnable = false;
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "getState()", e);
            }
        } finally {
            this.mBluetoothLock.writeLock().unlock();
        }
    }

    public int updateSBleAppCount(IBinder token, boolean enable, String packageName) {
        int prevBleAppCount;
        int bleAppCount;
        int samsungBleAppCount;
        ClientDeathRecipient r;
        if (restrictedByMdmPolicy(1)) {
            Slog.e(TAG, "updateSBleAppCount BLE is restriced by policy");
            return mSamsungBleAppCount;
        }
        synchronized (this.mBleLock) {
            prevBleAppCount = mBleAppCount;
            this.mUsingSSUpdate = true;
            updateBleAppCount(token, enable, packageName);
            this.mUsingSSUpdate = false;
            bleAppCount = mBleAppCount;
            samsungBleAppCount = mSamsungBleAppCount;
        }
        if (prevBleAppCount < bleAppCount) {
            ClientDeathRecipient r2 = this.mBleApps.get(token);
            if (r2 == null) {
                Slog.e(TAG, "Updating Ble App count failed, return");
                return mSamsungBleAppCount;
            }
            r2.setTokenAndAppInfo(token, true);
            this.mSamsungBleApps.put(token, r2);
            mSamsungBleAppCount++;
            if (DBG) {
                Slog.d(TAG, "Registered for death Notification");
            }
        } else if (prevBleAppCount > bleAppCount && (r = this.mSamsungBleApps.get(token)) != null) {
            try {
                token.unlinkToDeath(r, 0);
            } catch (NoSuchElementException e) {
                Slog.w(TAG, "no such element, already unlinked");
            }
            this.mSamsungBleApps.remove(token);
            if (samsungBleAppCount > 0) {
                mSamsungBleAppCount--;
            }
            if (DBG) {
                Slog.d(TAG, "Unregistered for death Notification");
            }
        }
        return mSamsungBleAppCount;
    }

    public int updateBleAppCount(IBinder token, boolean enable, String packageName) {
        int bleAppCount;
        int samsungBleAppCount;
        if (restrictedByMdmPolicy(1)) {
            Slog.e(TAG, "updateBleAppCount BLE is restriced by policy");
            return mBleAppCount;
        } else if (mShutdown) {
            if (DBG) {
                Slog.d(TAG, "updateBleAppCount Pass");
            }
            return mBleAppCount;
        } else {
            synchronized (this.mBleLock) {
                bleAppCount = mBleAppCount;
                samsungBleAppCount = mSamsungBleAppCount;
            }
            ClientDeathRecipient r = this.mBleApps.get(token);
            if (r == null && enable) {
                ClientDeathRecipient deathRec = new ClientDeathRecipient(packageName);
                try {
                    token.linkToDeath(deathRec, 0);
                    this.mBleApps.put(token, deathRec);
                    synchronized (this.mBleLock) {
                        try {
                            Slog.d(TAG, "updateBleAppCount #UP | mBleAppCount: " + bleAppCount + ", mSappCount: " + samsungBleAppCount + ", Using SS: " + this.mUsingSSUpdate);
                            if (this.mBluetooth != null && this.mBluetooth.getState() == 12) {
                                if (DBG) {
                                    Slog.d(TAG, "updateBleAppCount on state");
                                }
                            } else if (bleAppCount - samsungBleAppCount == 0 && !this.mUsingSSUpdate && this.mBluetooth != null && this.mBluetooth.getState() == 15) {
                                Message msg = this.mHandler.obtainMessage(702, 10, 14);
                                this.mHandler.sendMessage(msg);
                                this.mGoogleBleState = 1;
                                if (this.mBluetooth.getState() != 14) {
                                    Message msg2 = this.mHandler.obtainMessage(702, 14, 15);
                                    this.mHandler.sendMessage(msg2);
                                    this.mGoogleBleState = 2;
                                }
                            } else if (samsungBleAppCount == 0 && this.mUsingSSUpdate && this.mBluetooth != null && this.mBluetooth.getState() == 15) {
                                Message msg3 = this.mHandler.obtainMessage(703, 10, 14);
                                this.mHandler.sendMessage(msg3);
                                this.mSamsungBleState = 1;
                                if (this.mBluetooth.getState() != 14) {
                                    Message msg4 = this.mHandler.obtainMessage(703, 14, 15);
                                    this.mHandler.sendMessage(msg4);
                                    this.mSamsungBleState = 2;
                                }
                            } else if (DBG) {
                                Slog.d(TAG, "updateBleAppCount else case");
                            }
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Unable to call updateBleAppCount", e);
                        }
                        mBleAppCount++;
                        bleAppCount++;
                    }
                    if (DBG) {
                        Slog.d(TAG, "Registered for death of " + packageName);
                    }
                } catch (RemoteException e2) {
                    throw new IllegalArgumentException("BLE app (" + packageName + ") already dead!");
                }
            } else if (!enable && r != null) {
                try {
                    token.unlinkToDeath(r, 0);
                } catch (NoSuchElementException e3) {
                    Slog.w(TAG, "no such element, already unlinked");
                }
                this.mBleApps.remove(token);
                if (bleAppCount > 0) {
                    synchronized (this.mBleLock) {
                        try {
                            Slog.d(TAG, "updateBleAppCount #DOWN | mBleAppCount: " + bleAppCount + ", mSappCount" + samsungBleAppCount + ", Using SS: " + this.mUsingSSUpdate);
                            if (this.mBluetooth != null && this.mBluetooth.getState() == 12) {
                                if (DBG) {
                                    Slog.d(TAG, "updateBleAppCount on state : false");
                                }
                            } else if (bleAppCount - samsungBleAppCount == 1 && !this.mUsingSSUpdate && samsungBleAppCount > 0) {
                                Message msg5 = this.mHandler.obtainMessage(702, 15, 16);
                                this.mHandler.sendMessage(msg5);
                                this.mGoogleBleState = 3;
                                if (this.mBluetooth != null && this.mBluetooth.getState() != 16) {
                                    Message msg6 = this.mHandler.obtainMessage(702, 16, 10);
                                    this.mHandler.sendMessage(msg6);
                                    this.mGoogleBleState = 0;
                                    sendDisableBleCallback();
                                }
                            } else if (samsungBleAppCount == 1 && this.mUsingSSUpdate && bleAppCount > 1) {
                                Message msg7 = this.mHandler.obtainMessage(703, 15, 16);
                                this.mHandler.sendMessage(msg7);
                                this.mSamsungBleState = 3;
                                if (this.mBluetooth != null && this.mBluetooth.getState() != 16) {
                                    Message msg8 = this.mHandler.obtainMessage(703, 16, 10);
                                    this.mHandler.sendMessage(msg8);
                                    this.mSamsungBleState = 0;
                                }
                            } else if (DBG) {
                                Slog.d(TAG, "updateBleAppCount else:false case");
                            }
                        } catch (RemoteException e4) {
                            Slog.e(TAG, "Unable to call updateBleAppCount", e4);
                        }
                        mBleAppCount--;
                        bleAppCount--;
                    }
                }
                if (DBG) {
                    Slog.d(TAG, "Unregistered for death of " + packageName);
                }
            } else {
                Slog.e(TAG, "Unable to handle updateBleAppCount");
                return -1;
            }
            if (DBG) {
                Slog.d(TAG, bleAppCount + " registered Ble Apps");
            }
            if (bleAppCount == 0 && this.mEnable) {
                disableBleScanMode();
            }
            if (bleAppCount == 0 && !this.mEnableExternal) {
                sendBrEdrDownCallback();
            }
            return mBleAppCount;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void clearBleApps() {
        clearAllmBleApps();
        clearAllmSamsungBleApps();
        this.mBleApps.clear();
        this.mSamsungBleApps.clear();
        synchronized (this.mBleLock) {
            mBleAppCount = 0;
            mSamsungBleAppCount = 0;
        }
    }

    private void clearAllmBleApps() {
        for (Map.Entry<IBinder, ClientDeathRecipient> entry : this.mBleApps.entrySet()) {
            IBinder token = entry.getKey();
            ClientDeathRecipient tokenRecipient = entry.getValue();
            if (token != null && tokenRecipient != null) {
                try {
                    token.unlinkToDeath(tokenRecipient, 0);
                } catch (NoSuchElementException e) {
                    Slog.w(TAG, "no such element, already unlinked");
                }
            }
        }
    }

    private void clearAllmSamsungBleApps() {
        for (Map.Entry<IBinder, ClientDeathRecipient> entry : this.mSamsungBleApps.entrySet()) {
            IBinder token = entry.getKey();
            ClientDeathRecipient tokenRecipient = entry.getValue();
            if (token != null && tokenRecipient != null) {
                try {
                    token.unlinkToDeath(tokenRecipient, 0);
                } catch (NoSuchElementException e) {
                    Slog.w(TAG, "no such element, already unlinked");
                }
            }
        }
    }

    public boolean isBleAppPresent() {
        if (DBG) {
            Slog.d(TAG, "isBleAppPresent() count: " + mBleAppCount);
        }
        return mBleAppCount > 0;
    }

    public int getBleAppCount() {
        if (DBG) {
            Slog.d(TAG, "getBleAppCount mBleAppCount : " + mBleAppCount);
        }
        return mBleAppCount;
    }

    public int getSamsungBleAppCount() {
        if (DBG) {
            Slog.d(TAG, "getSamsungBleAppCount mSamsung : " + mSamsungBleAppCount);
        }
        return mSamsungBleAppCount;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onBluetoothGattServiceUp() {
        Slog.d(TAG, "BluetoothGatt Service is Up");
        try {
            try {
                this.mBluetoothLock.readLock().lock();
            } catch (RemoteException e) {
                Slog.e(TAG, "Unable to call onServiceUp", e);
            }
            if (this.mBluetooth == null) {
                Slog.w(TAG, "onBluetoothServiceUp: mBluetooth is null!");
                return;
            }
            int st = this.mBluetooth.getState();
            if (st != 15) {
                Slog.v(TAG, "onBluetoothServiceUp: state isn't BLE_ON: " + BluetoothAdapter.nameForState(st));
                return;
            }
            if (this.mEnableExternal && st == 15) {
                if (isRestrictedBtPolicy()) {
                    return;
                }
                this.mBluetooth.onLeServiceUp();
            }
        } finally {
            this.mBluetoothLock.readLock().unlock();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendBrEdrDownCallback() {
        if (DBG) {
            Slog.d(TAG, "Calling sendBrEdrDownCallback callbacks");
        }
        if (this.mBluetooth == null) {
            Slog.w(TAG, "Bluetooth handle is null");
        } else if (!isBleAppPresent() || this.mSwitching) {
            try {
                try {
                    this.mBluetoothLock.readLock().lock();
                    if (this.mBluetooth != null) {
                        this.mBluetooth.onBrEdrDown();
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "Call to onBrEdrDown() failed.", e);
                }
            } finally {
                this.mBluetoothLock.readLock().unlock();
            }
        }
    }

    public boolean enableNoAutoConnect(String packageName) {
        if (isRestrictedBtPolicy()) {
            Slog.e(TAG, "Enabling BT is restriced by policy");
            return false;
        } else if (isBluetoothDisallowed()) {
            if (DBG) {
                Slog.d(TAG, "enableNoAutoConnect(): not enabling - bluetooth disallowed");
            }
            return false;
        } else {
            this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
            if (DBG) {
                Slog.d(TAG, "enableNoAutoConnect():  mBluetooth =" + this.mBluetooth + " mBinding = " + this.mBinding);
            }
            int callingAppId = UserHandle.getAppId(Binder.getCallingUid());
            if (callingAppId != 1027) {
                throw new SecurityException("no permission to enable Bluetooth quietly");
            }
            synchronized (this.mReceiver) {
                this.mEnableExternal = true;
                sendEnableMsg(true, 1, packageName);
            }
            AuditLog.logAsUser(5, 5, true, Process.myPid(), BluetoothManagerService.class.getSimpleName(), "Enabling Bluetooth", -1);
            return true;
        }
    }

    public boolean enable(String packageName) throws RemoteException {
        int callingUid = Binder.getCallingUid();
        boolean callerSystem = UserHandle.getAppId(callingUid) == 1000;
        BluetoothDump.BtLog("BluetoothManagerService -- enable() BT ManagerService enable called");
        if (isRestrictedBtPolicy()) {
            Slog.e(TAG, "Enabling BT is restriced by policy");
            BluetoothDump.BtLog("BluetoothManagerService -- Enabling BT is restriced by policy");
            return false;
        } else if (isBluetoothDisallowed()) {
            if (DBG) {
                Slog.d(TAG, "enable(): not enabling - bluetooth disallowed");
            }
            BluetoothDump.BtLog("BluetoothManagerService -- enable(): not enabling - bluetooth disallowed");
            return false;
        } else {
            if (!callerSystem) {
                if (!checkIfCallerIsForegroundUser()) {
                    Slog.w(TAG, "enable(): not allowed for non-active and non system user");
                    BluetoothDump.BtLog("BluetoothManagerService -- enable(): not allowed for non-active and non system user");
                    return false;
                }
                this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
                if (!isEnabled() && this.mPermissionReviewRequired && startConsentUiIfNeeded(packageName, callingUid, "android.bluetooth.adapter.action.REQUEST_ENABLE")) {
                    Slog.e(TAG, "enable(): not allowed. mPermissionReviewRequired : " + this.mPermissionReviewRequired);
                    BluetoothDump.BtLog("BluetoothManagerService -- enable(): not allowed. mPermissionReviewRequired : " + this.mPermissionReviewRequired);
                    return false;
                }
            }
            if (!SemCscFeature.getInstance().getString("CscFeature_Common_ConfigLocalSecurityPolicy").isEmpty()) {
                String ChinaNalSecurityType = SemCscFeature.getInstance().getString("CscFeature_Common_ConfigLocalSecurityPolicy");
                int isChecked = Settings.Global.getInt(this.mContentResolver, "bluetooth_security_on_check", 1);
                Slog.d(TAG, "enable(): enable PopUp for Bluetooth isChecked : " + isChecked);
                if ("ChinaNalSecurity".equals(ChinaNalSecurityType) && isChecked == 1 && isUserAction()) {
                    if (!isEnablePopup) {
                        Message securityPopupMsg = this.mHandler.obtainMessage(700);
                        securityPopupMsg.obj = packageName;
                        this.mHandler.sendMessageDelayed(securityPopupMsg, 50L);
                        return true;
                    }
                    isEnablePopup = false;
                }
            }
            persistBluetoothSetting(1);
            if (DBG) {
                Slog.d(TAG, "enable(" + packageName + "):  mBluetooth =" + this.mBluetooth + " mBinding = " + this.mBinding + " mState = " + BluetoothAdapter.nameForState(this.mState));
            }
            synchronized (this.mReceiver) {
                this.mEnableExternal = true;
                sendEnableMsg(false, 1, packageName);
            }
            if (DBG) {
                Slog.d(TAG, "enable returning");
            }
            AuditLog.logAsUser(5, 5, true, Process.myPid(), BluetoothManagerService.class.getSimpleName(), "Enabling Bluetooth", -1);
            return true;
        }
    }

    public boolean enableBle(String packageName) throws RemoteException {
        if (!restrictedByMdmPolicy(1)) {
            if (mShutdown) {
                Slog.d(TAG, "enableBle() : during shutdown");
                return false;
            }
            int callingUid = Binder.getCallingUid();
            boolean callerSystem = UserHandle.getAppId(callingUid) == 1000;
            BluetoothDump.BtLog("BluetoothManagerService -- enableBle(" + packageName + ") BT ManagerService enableBle called ");
            if (isBluetoothDisallowed()) {
                Slog.d(TAG, "enableBle(): not enabling - bluetooth disallowed");
                BluetoothDump.BtLog("BluetoothManagerService -- enableBle(): not allowed for non-active and non system user");
                return false;
            }
            if (!callerSystem) {
                if (!checkIfCallerIsForegroundUser()) {
                    Slog.w(TAG, "enableBle(): not allowed for non-active and non system user");
                    return false;
                }
                this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
                if (isEnabled()) {
                    return true;
                }
            }
            Slog.d(TAG, "enableBle(" + packageName + "):  mBluetooth =" + this.mBluetooth + " mBinding = " + this.mBinding + " mState = " + BluetoothAdapter.nameForState(this.mState));
            synchronized (this.mReceiver) {
                sendEnableMsg(false, 1, packageName);
            }
            Slog.d(TAG, "enable returning");
            AuditLog.logAsUser(5, 5, true, Process.myPid(), BluetoothManagerService.class.getSimpleName(), "Enabling Bluetooth", -1);
            return true;
        }
        Slog.e(TAG, "enableBle BLE is restriced by policy");
        return false;
    }

    public boolean shutdown() {
        if (DBG) {
            Slog.d(TAG, "shutDown()");
        }
        int st = 10;
        int callingUid = Binder.getCallingUid();
        boolean callerSystem = UserHandle.getAppId(callingUid) == 1000;
        if (!callerSystem) {
            this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
        }
        if (this.mBluetooth != null) {
            try {
                try {
                    this.mBluetoothLock.readLock().lock();
                    st = this.mBluetooth.getState();
                } finally {
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "Unable to call getState", e);
            }
            this.mBluetoothLock.readLock().unlock();
            Slog.d(TAG, "state" + st);
            if (st != 10) {
                mShutdown = true;
                clearBleApps();
                if (st == 15) {
                    if (DBG) {
                        Slog.d(TAG, "shutdown BLE disable()");
                    }
                    try {
                        try {
                            this.mBluetoothLock.readLock().lock();
                            if (this.mBluetooth != null) {
                                this.mBluetooth.onBrEdrDown();
                                this.mEnableExternal = false;
                            }
                        } catch (RemoteException e2) {
                            Slog.e(TAG, "Unable to call onBrEdrDown", e2);
                        }
                    } finally {
                    }
                } else if (st == 12) {
                    if (DBG) {
                        Slog.d(TAG, "shutdown BT disable()");
                    }
                    persistBluetoothSetting(0);
                    this.mEnableExternal = false;
                    sendDisableMsg(1, this.mContext.getPackageName());
                }
            }
            return true;
        }
        Slog.d(TAG, "shutdown mBluetooth is null");
        return false;
    }

    public boolean disable(String packageName, boolean persist) throws RemoteException {
        int callingUid = Binder.getCallingUid();
        boolean callerSystem = UserHandle.getAppId(callingUid) == 1000;
        BluetoothDump.BtLog("BluetoothManagerService -- disable() BT ManagerService disable called");
        if (!callerSystem) {
            if (!checkIfCallerIsForegroundUser()) {
                Slog.w(TAG, "disable(): not allowed for non-active and non system user");
                BluetoothDump.BtLog("BluetoothManagerService -- disable(): not allowed for non-active and non system user");
                return false;
            }
            this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
            if (isEnabled() && this.mPermissionReviewRequired && startConsentUiIfNeeded(packageName, callingUid, "android.bluetooth.adapter.action.REQUEST_DISABLE")) {
                Slog.e(TAG, "disable(): not allowed. mPermissionReviewRequired : " + this.mPermissionReviewRequired);
                BluetoothDump.BtLog("BluetoothManagerService -- disable(): not allowed. mPermissionReviewRequired : " + this.mPermissionReviewRequired);
                return false;
            }
        }
        if (DBG) {
            Slog.d(TAG, "disable(): mBluetooth = " + this.mBluetooth + " mBinding = " + this.mBinding);
        }
        synchronized (this.mReceiver) {
            if (persist) {
                try {
                    persistBluetoothSetting(0);
                } catch (Throwable th) {
                    throw th;
                }
            }
            this.mEnableExternal = false;
            sendDisableMsg(1, packageName);
        }
        AuditLog.logAsUser(5, 5, false, Process.myPid(), BluetoothManagerService.class.getSimpleName(), "Disabling Bluetooth", -1);
        return true;
    }

    private boolean startConsentUiIfNeeded(String packageName, int callingUid, String intentAction) throws RemoteException {
        if (checkBluetoothPermissionWhenPermissionReviewRequired()) {
            return false;
        }
        try {
            ApplicationInfo applicationInfo = this.mContext.getPackageManager().getApplicationInfoAsUser(packageName, 268435456, UserHandle.getUserId(callingUid));
            if (applicationInfo.uid != callingUid) {
                throw new SecurityException("Package " + packageName + " not in uid " + callingUid);
            }
            Intent intent = new Intent(intentAction);
            intent.putExtra("android.intent.extra.PACKAGE_NAME", packageName);
            intent.setFlags(276824064);
            try {
                this.mContext.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException e) {
                Slog.e(TAG, "Intent to handle action " + intentAction + " missing");
                return false;
            }
        } catch (PackageManager.NameNotFoundException e2) {
            throw new RemoteException(e2.getMessage());
        }
    }

    private boolean checkBluetoothPermissionWhenPermissionReviewRequired() {
        if (this.mPermissionReviewRequired) {
            int result = this.mContext.checkCallingPermission("android.permission.MANAGE_BLUETOOTH_WHEN_PERMISSION_REVIEW_REQUIRED");
            return result == 0;
        }
        return false;
    }

    public void unbindAndFinish() {
        if (DBG) {
            Slog.d(TAG, "unbindAndFinish(): " + this.mBluetooth + " mBinding = " + this.mBinding + " mUnbinding = " + this.mUnbinding);
        }
        try {
            this.mBluetoothLock.writeLock().lock();
            if (this.mUnbinding) {
                return;
            }
            this.mUnbinding = true;
            this.mHandler.removeMessages(60);
            this.mHandler.removeMessages(401);
            if (this.mBluetooth != null) {
                try {
                    this.mBluetooth.unregisterCallback(this.mBluetoothCallback);
                } catch (RemoteException re) {
                    Slog.e(TAG, "Unable to unregister BluetoothCallback", re);
                }
                this.mBluetoothBinder = null;
                this.mBluetooth = null;
                this.mContext.unbindService(this.mConnection);
                this.mUnbinding = false;
                this.mBinding = false;
            } else {
                this.mUnbinding = false;
            }
            this.mBluetoothGatt = null;
        } finally {
            this.mBluetoothLock.writeLock().unlock();
        }
    }

    public IBluetoothGatt getBluetoothGatt() {
        if (PersonaServiceHelper.shouldBlockBluetooth(this.mContext, UserHandle.getCallingUserId())) {
            BluetoothDump.BtLog("BluetoothManagerService -- getBluetoothGatt() but knox block it ");
            Log.w(TAG, "getBluetoothGatt() but knox block it");
            return null;
        }
        return this.mBluetoothGatt;
    }

    public boolean bindBluetoothProfileService(int bluetoothProfile, IBluetoothProfileServiceConnection proxy) {
        if (!this.mEnableExternal) {
            if (DBG) {
                Slog.d(TAG, "Trying to bind to profile: " + bluetoothProfile + ", while Bluetooth was disabled");
            }
            return false;
        } else if (proxy != null && bluetoothProfile == 1) {
            Message addProxyMsg = this.mHandler.obtainMessage(400);
            addProxyMsg.arg1 = bluetoothProfile;
            addProxyMsg.obj = proxy;
            this.mHandler.sendMessageDelayed(addProxyMsg, 100L);
            return true;
        } else {
            return false;
        }
    }

    public void unbindBluetoothProfileService(int bluetoothProfile, IBluetoothProfileServiceConnection proxy) {
        synchronized (this.mProfileServices) {
            ProfileServiceConnections psc = this.mProfileServices.get(new Integer(bluetoothProfile));
            if (psc == null) {
                return;
            }
            psc.removeProxy(proxy);
        }
    }

    public boolean bindBluetoothProfileServiceWithCaller(int bluetoothProfile, IBluetoothProfileServiceConnection proxy, String caller) {
        if (bluetoothProfile == 1) {
            synchronized (this.mHeadsetProxies) {
                Integer headsetProxyCount = this.mHeadsetProxies.get(caller);
                if (headsetProxyCount == null) {
                    this.mHeadsetProxies.put(caller, 1);
                } else {
                    headsetProxyCount = new Integer(headsetProxyCount.intValue() + 1);
                    this.mHeadsetProxies.put(caller, headsetProxyCount);
                }
                Integer headsetMaxProxyCount = this.mMaxHeadsetProxies.get(caller);
                if (headsetMaxProxyCount == null) {
                    this.mMaxHeadsetProxies.put(caller, 1);
                } else if (headsetProxyCount != null && headsetMaxProxyCount.intValue() < headsetProxyCount.intValue()) {
                    this.mMaxHeadsetProxies.put(caller, new Integer(headsetProxyCount.intValue()));
                }
            }
        }
        return bindBluetoothProfileService(bluetoothProfile, proxy);
    }

    public void unbindBluetoothProfileServiceWithCaller(int bluetoothProfile, IBluetoothProfileServiceConnection proxy, String caller) {
        if (bluetoothProfile == 1) {
            synchronized (this.mHeadsetProxies) {
                Integer headsetProxyCount = this.mHeadsetProxies.get(caller);
                if (headsetProxyCount != null) {
                    if (headsetProxyCount.intValue() >= 2) {
                        this.mHeadsetProxies.put(caller, new Integer(headsetProxyCount.intValue() - 1));
                    } else {
                        this.mHeadsetProxies.remove(caller);
                    }
                }
            }
        }
        unbindBluetoothProfileService(bluetoothProfile, proxy);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unbindAllBluetoothProfileServices() {
        Slog.d(TAG, "unbindAllBluetoothProfileServices");
        synchronized (this.mProfileServices) {
            for (Integer i : this.mProfileServices.keySet()) {
                ProfileServiceConnections psc = this.mProfileServices.get(i);
                if (psc != null) {
                    psc.removeAllProxies();
                }
            }
            this.mProfileServices.clear();
        }
    }

    /* JADX WARN: Code restructure failed: missing block: B:20:0x00e1, code lost:
        if (r4 != false) goto L36;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public void handleOnBootPhase() {
        /*
            Method dump skipped, instructions count: 324
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.BluetoothManagerService.handleOnBootPhase():void");
    }

    public void handleOnSwitchUser(int userHandle) {
        if (DBG) {
            Slog.d(TAG, "User " + userHandle + " switched");
        }
        BluetoothDump.BtLog("BluetoothManagerService -- Bluetooth user switched. userHandle : " + userHandle);
        Slog.d(TAG, "mSwitching to true");
        this.mSwitching = true;
        this.mHandler.obtainMessage(300, userHandle, 0).sendToTarget();
    }

    public void handleOnUnlockUser(int userHandle) {
        if (DBG) {
            Slog.d(TAG, "User " + userHandle + " unlocked");
        }
        BluetoothDump.BtLog("BluetoothManagerService -- User unlocked. userHandle : " + userHandle);
        this.mHandler.obtainMessage(301, userHandle, 0).sendToTarget();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public final class ProfileServiceConnections implements ServiceConnection, IBinder.DeathRecipient {
        Intent mIntent;
        int mTryBind;
        final RemoteCallbackList<IBluetoothProfileServiceConnection> mProxies = new RemoteCallbackList<>();
        volatile boolean mInvokingProxyCallbacks = false;
        IBinder mService = null;
        ComponentName mClassName = null;

        ProfileServiceConnections(Intent intent) {
            this.mTryBind = 0;
            this.mIntent = intent;
            this.mTryBind = 0;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean bindService() {
            if (this.mIntent != null && this.mService == null && BluetoothManagerService.this.doBind(this.mIntent, this, 0, UserHandle.CURRENT_OR_SELF)) {
                this.mTryBind++;
                Slog.d(BluetoothManagerService.TAG, "bind HeadsetService : " + this.mTryBind);
                if (this.mTryBind < 5) {
                    Message msg = BluetoothManagerService.this.mHandler.obtainMessage(401);
                    msg.obj = this;
                    msg.arg1 = this.mTryBind;
                    BluetoothManagerService.this.mHandler.sendMessageDelayed(msg, 3000L);
                }
                return true;
            }
            Slog.w(BluetoothManagerService.TAG, "Unable to bind with intent: " + this.mIntent);
            return false;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void addProxy(IBluetoothProfileServiceConnection proxy) {
            Slog.d(BluetoothManagerService.TAG, "addProxy");
            this.mProxies.register(proxy);
            if (this.mService != null) {
                try {
                    proxy.onServiceConnected(this.mClassName, this.mService);
                } catch (RemoteException e) {
                    Slog.e(BluetoothManagerService.TAG, "Unable to connect to proxy", e);
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void removeProxy(IBluetoothProfileServiceConnection proxy) {
            if (proxy != null) {
                Slog.d(BluetoothManagerService.TAG, "removeProxy");
                if (this.mProxies.unregister(proxy)) {
                    try {
                        proxy.onServiceDisconnected(this.mClassName);
                        return;
                    } catch (RemoteException e) {
                        Slog.e(BluetoothManagerService.TAG, "Unable to disconnect proxy", e);
                        return;
                    }
                }
                Slog.d(BluetoothManagerService.TAG, "removeProxy : false");
                return;
            }
            Slog.w(BluetoothManagerService.TAG, "Trying to remove a null proxy");
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void removeAllProxies() {
            onServiceDisconnected(this.mClassName);
            Slog.d(BluetoothManagerService.TAG, "removeAllProxies");
            this.mProxies.kill();
        }

        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName className, IBinder service) {
            Slog.d(BluetoothManagerService.TAG, "onServiceConnected : " + this.mTryBind);
            this.mTryBind = 0;
            BluetoothManagerService.this.mHandler.removeMessages(401, this);
            this.mService = service;
            this.mClassName = className;
            try {
                this.mService.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Slog.e(BluetoothManagerService.TAG, "Unable to linkToDeath", e);
            }
            synchronized (this.mProxies) {
                if (this.mInvokingProxyCallbacks) {
                    Slog.e(BluetoothManagerService.TAG, "Proxy callbacks already in progress.");
                    return;
                }
                this.mInvokingProxyCallbacks = true;
                int n = this.mProxies.beginBroadcast();
                Slog.d(BluetoothManagerService.TAG, "onServiceConnected : broadcast to " + n + " proxies");
                for (int i = 0; i < n; i++) {
                    try {
                        try {
                            try {
                                this.mProxies.getBroadcastItem(i).onServiceConnected(className, service);
                            } finally {
                                this.mProxies.finishBroadcast();
                                this.mInvokingProxyCallbacks = false;
                            }
                        } catch (RemoteException e2) {
                            Slog.e(BluetoothManagerService.TAG, "Unable to connect to proxy", e2);
                        }
                    } catch (IllegalStateException e3) {
                        Slog.e(BluetoothManagerService.TAG, "Unable to begin broadcast", e3);
                    }
                }
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName className) {
            Slog.d(BluetoothManagerService.TAG, "onServiceDisconnected : " + this.mTryBind);
            this.mTryBind = 0;
            if (this.mService != null) {
                try {
                    this.mService.unlinkToDeath(this, 0);
                } catch (NoSuchElementException e) {
                    Slog.e(BluetoothManagerService.TAG, "No death recipient registered" + e);
                }
                this.mService = null;
                this.mClassName = null;
                synchronized (this.mProxies) {
                    if (this.mInvokingProxyCallbacks) {
                        Slog.e(BluetoothManagerService.TAG, "Proxy callbacks already in progress.");
                        return;
                    }
                    this.mInvokingProxyCallbacks = true;
                    int n = this.mProxies.beginBroadcast();
                    Slog.d(BluetoothManagerService.TAG, "onServiceDisconnected : broadcast to " + n + " proxies");
                    for (int i = 0; i < n; i++) {
                        try {
                            try {
                                try {
                                    this.mProxies.getBroadcastItem(i).onServiceDisconnected(className);
                                } catch (IllegalStateException e2) {
                                    Slog.e(BluetoothManagerService.TAG, "Unable to begin broadcast", e2);
                                }
                            } catch (Throwable th) {
                                this.mProxies.finishBroadcast();
                                this.mInvokingProxyCallbacks = false;
                                throw th;
                            }
                        } catch (RemoteException e3) {
                            Slog.e(BluetoothManagerService.TAG, "Unable to disconnect from proxy", e3);
                        }
                    }
                    this.mProxies.finishBroadcast();
                    this.mInvokingProxyCallbacks = false;
                    Slog.d(BluetoothManagerService.TAG, "onServiceDisconnected : unbindService");
                    try {
                        BluetoothManagerService.this.mContext.unbindService(this);
                    } catch (IllegalArgumentException e4) {
                        Slog.e(BluetoothManagerService.TAG, "Unable to unbind service with intent: " + this.mIntent, e4);
                    }
                }
            }
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            if (BluetoothManagerService.DBG) {
                Slog.w(BluetoothManagerService.TAG, "Profile service for profile: " + this.mClassName + " died.");
            } else {
                Slog.d(BluetoothManagerService.TAG, "binderDied");
            }
            onServiceDisconnected(this.mClassName);
            Message msg = BluetoothManagerService.this.mHandler.obtainMessage(401);
            msg.obj = this;
            BluetoothManagerService.this.mHandler.sendMessageDelayed(msg, 3000L);
        }
    }

    private void sendBluetoothStateCallback(boolean isUp) {
        try {
            int n = this.mStateChangeCallbacks.beginBroadcast();
            Slog.d(TAG, "Broadcasting onBluetoothStateChange(" + isUp + ") to " + n + " receivers.");
            for (int i = 0; i < n; i++) {
                try {
                    this.mStateChangeCallbacks.getBroadcastItem(i).onBluetoothStateChange(isUp);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to call onBluetoothStateChange() on callback #" + i, e);
                }
            }
        } finally {
            this.mStateChangeCallbacks.finishBroadcast();
        }
    }

    public void sendDisableBleCallback() {
        synchronized (this.mDisableBleCallback) {
            int n = this.mDisableBleCallback.beginBroadcast();
            Slog.d(TAG, "Broadcasting sendDisableBleCallback to " + n + " receivers.");
            for (int i = 0; i < n; i++) {
                try {
                    this.mDisableBleCallback.getBroadcastItem(i).ondisableBLE();
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to call sendDisableBleCallback() on callback #" + i, e);
                }
            }
            this.mDisableBleCallback.finishBroadcast();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendBluetoothServiceUpCallback() {
        try {
            int n = this.mCallbacks.beginBroadcast();
            Slog.d(TAG, "Broadcasting onBluetoothServiceUp() to " + n + " receivers.");
            for (int i = 0; i < n; i++) {
                try {
                    this.mCallbacks.getBroadcastItem(i).onBluetoothServiceUp(this.mBluetooth);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to call onBluetoothServiceUp() on callback #" + i, e);
                }
            }
        } finally {
            this.mCallbacks.finishBroadcast();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendBluetoothServiceDownCallback() {
        try {
            int n = this.mCallbacks.beginBroadcast();
            Slog.d(TAG, "Broadcasting onBluetoothServiceDown() to " + n + " receivers.");
            for (int i = 0; i < n; i++) {
                try {
                    this.mCallbacks.getBroadcastItem(i).onBluetoothServiceDown();
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to call onBluetoothServiceDown() on callback #" + i, e);
                }
            }
        } finally {
            this.mCallbacks.finishBroadcast();
        }
    }

    public String getAddress() {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (Binder.getCallingUid() != 1000 && !checkIfCallerIsForegroundUser()) {
            Slog.w(TAG, "getAddress(): not allowed for non-active and non system user");
            return null;
        } else if (this.mContext.checkCallingOrSelfPermission("android.permission.LOCAL_MAC_ADDRESS") != 0) {
            return "02:00:00:00:00:00";
        } else {
            try {
                try {
                    this.mBluetoothLock.readLock().lock();
                    if (this.mBluetooth != null) {
                        String addr = this.mBluetooth.getAddress();
                        if (addr != null) {
                            return addr;
                        }
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "getAddress(): Unable to retrieve address remotely. Returning cached address", e);
                }
                this.mBluetoothLock.readLock().unlock();
                return this.mAddress;
            } finally {
                this.mBluetoothLock.readLock().unlock();
            }
        }
    }

    public String getName() {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (Binder.getCallingUid() != 1000 && !checkIfCallerIsForegroundUser()) {
            Slog.w(TAG, "getName(): not allowed for non-active and non system user");
            return null;
        }
        try {
            try {
                this.mBluetoothLock.readLock().lock();
                if (this.mBluetooth != null) {
                    return this.mBluetooth.getName();
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "getName(): Unable to retrieve name remotely. Returning cached name", e);
            }
            this.mBluetoothLock.readLock().unlock();
            return this.mName;
        } finally {
            this.mBluetoothLock.readLock().unlock();
        }
    }

    public String getSettingsName() {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (Binder.getCallingUid() != 1000 && !checkIfCallerIsForegroundUser()) {
            Slog.w(TAG, "getName(): not allowed for non-active and non system user");
            return null;
        }
        ContentResolver resolver = this.mContext.getContentResolver();
        String localName = Settings.System.getString(resolver, "device_name");
        if (localName == null) {
            localName = Settings.Global.getString(resolver, "device_name");
        }
        Slog.d(TAG, "Settings Name is = " + localName);
        return localName;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class BluetoothServiceConnection implements ServiceConnection {
        private BluetoothServiceConnection() {
        }

        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            String name = componentName.getClassName();
            if (BluetoothManagerService.DBG) {
                Slog.d(BluetoothManagerService.TAG, "BluetoothServiceConnection: " + name);
            }
            Message msg = BluetoothManagerService.this.mHandler.obtainMessage(40);
            if (name.equals("com.android.bluetooth.btservice.AdapterService")) {
                msg.arg1 = 1;
            } else if (name.equals("com.android.bluetooth.gatt.GattService")) {
                msg.arg1 = 2;
            } else {
                Slog.e(BluetoothManagerService.TAG, "Unknown service connected: " + name);
                return;
            }
            msg.obj = service;
            BluetoothManagerService.this.mHandler.sendMessage(msg);
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
            String name = componentName.getClassName();
            if (BluetoothManagerService.DBG) {
                Slog.d(BluetoothManagerService.TAG, "BluetoothServiceConnection, disconnected: " + name);
            }
            Message msg = BluetoothManagerService.this.mHandler.obtainMessage(41);
            if (name.equals("com.android.bluetooth.btservice.AdapterService")) {
                msg.arg1 = 1;
                BluetoothManagerService.this.mAdapterServiceCrashed = 1;
            } else if (name.equals("com.android.bluetooth.gatt.GattService")) {
                msg.arg1 = 2;
                BluetoothManagerService.this.mAdapterServiceCrashed = 1;
            } else {
                Slog.e(BluetoothManagerService.TAG, "Unknown service disconnected: " + name);
                return;
            }
            BluetoothManagerService.this.mHandler.sendMessage(msg);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class BluetoothHandler extends Handler {
        BluetoothHandler(Looper looper) {
            super(looper);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int i;
            int i2;
            int bleStateByBtState;
            BluetoothManagerService bluetoothManagerService;
            ReentrantReadWriteLock reentrantReadWriteLock;
            ReentrantReadWriteLock.WriteLock writeLock;
            int i3;
            int i4 = 0;
            switch (msg.what) {
                case 1:
                    if (BluetoothManagerService.DBG) {
                        Slog.d(BluetoothManagerService.TAG, "MESSAGE_ENABLE(" + msg.arg1 + "): mBluetooth = " + BluetoothManagerService.this.mBluetooth);
                    }
                    BluetoothManagerService.this.mHandler.removeMessages(42);
                    BluetoothManagerService.this.mEnable = true;
                    try {
                        try {
                            BluetoothManagerService.this.mBluetoothLock.readLock().lock();
                            if (BluetoothManagerService.this.mBluetooth != null) {
                                int state = BluetoothManagerService.this.mBluetooth.getState();
                                if (BluetoothManagerService.this.mEnableExternal && state == 15) {
                                    Slog.w(BluetoothManagerService.TAG, "BT Enable in BLE_ON State, going to ON");
                                    BluetoothManagerService.this.mBluetooth.onLeServiceUp();
                                    BluetoothManagerService.this.persistBluetoothSetting(1);
                                    return;
                                }
                            }
                        } catch (RemoteException e) {
                            Slog.e(BluetoothManagerService.TAG, "", e);
                        }
                        BluetoothManagerService.this.mBluetoothLock.readLock().unlock();
                        BluetoothManagerService.this.mQuietEnable = msg.arg1 == 1;
                        if (BluetoothManagerService.this.mBluetooth == null) {
                            BluetoothManagerService.this.handleEnable(BluetoothManagerService.this.mQuietEnable);
                            return;
                        }
                        return;
                    } finally {
                    }
                case 2:
                    if (BluetoothManagerService.DBG) {
                        Slog.d(BluetoothManagerService.TAG, "MESSAGE_DISABLE: mBluetooth = " + BluetoothManagerService.this.mBluetooth);
                    }
                    BluetoothManagerService.this.mHandler.removeMessages(42);
                    if (!BluetoothManagerService.this.mEnableExternal || BluetoothManagerService.this.mBluetooth == null) {
                        BluetoothManagerService.this.handleDisable();
                        return;
                    }
                    BluetoothManagerService.this.waitForOnOff(true, false);
                    BluetoothManagerService.this.handleDisable();
                    BluetoothManagerService.this.waitForOnOff(false, false);
                    return;
                case 5:
                    if (BluetoothManagerService.mPolicy == 0 || BluetoothManagerService.mPolicy == 1) {
                        Intent in = new Intent("com.samsung.server.BT_IT_POLICY");
                        if (BluetoothManagerService.mPolicy == 0) {
                            in.putExtra("ToastMsg", BluetoothManagerService.this.mContext.getString(17039696));
                        } else {
                            in.putExtra("ToastMsg", BluetoothManagerService.this.mContext.getString(17039697));
                        }
                        in.setPackage("com.android.bluetooth");
                        in.addFlags(67108864);
                        BluetoothManagerService.this.mContext.sendBroadcastAsUser(in, UserHandle.CURRENT, BluetoothManagerService.BLUETOOTH_PERM);
                    }
                    BluetoothManagerService.this.mHandler.removeMessages(5);
                    return;
                case 20:
                    IBluetoothManagerCallback callback = (IBluetoothManagerCallback) msg.obj;
                    BluetoothManagerService.this.mCallbacks.register(callback);
                    if (msg.arg1 != 1 || BluetoothManagerService.this.mBluetooth == null) {
                        return;
                    }
                    Slog.e(BluetoothManagerService.TAG, "Call missing callback.");
                    BluetoothDump.BtLog("BluetoothManagerService -- Call missing callback.");
                    try {
                        callback.onBluetoothServiceUp(BluetoothManagerService.this.mBluetooth);
                        return;
                    } catch (RemoteException e2) {
                        Slog.e(BluetoothManagerService.TAG, "Unable to call onBluetoothServiceUp() ", e2);
                        return;
                    }
                case 21:
                    BluetoothManagerService.this.mCallbacks.unregister((IBluetoothManagerCallback) msg.obj);
                    return;
                case 30:
                    BluetoothManagerService.this.mStateChangeCallbacks.register((IBluetoothStateChangeCallback) msg.obj);
                    return;
                case 31:
                    BluetoothManagerService.this.mStateChangeCallbacks.unregister((IBluetoothStateChangeCallback) msg.obj);
                    return;
                case 40:
                    if (BluetoothManagerService.DBG) {
                        Slog.d(BluetoothManagerService.TAG, "MESSAGE_BLUETOOTH_SERVICE_CONNECTED: " + msg.arg1);
                    }
                    BluetoothDump.BtLog("BluetoothManagerService BLUETOOTH_SERVICE_CONNECTED: " + msg.arg1);
                    IBinder service = (IBinder) msg.obj;
                    if (msg.arg1 == 2 && BluetoothManagerService.this.mAdapterServiceCrashed != 0) {
                        BluetoothManagerService.this.mBluetoothGatt = IBluetoothGatt.Stub.asInterface(Binder.allowBlocking(service));
                        BluetoothDump.BtLog("BluetoothManagerService Previously BluetoothAdapterService got crash");
                        BluetoothManagerService.this.onBluetoothGattServiceUp();
                        return;
                    }
                    try {
                        BluetoothManagerService.this.mBluetoothLock.writeLock().lock();
                        if (msg.arg1 == 2) {
                            if (BluetoothManagerService.this.mBluetooth == null) {
                                Slog.e(BluetoothManagerService.TAG, "MESSAGE_BLUETOOTH_SERVICE_CONNECTED: IBLUETOOTHGATT but mBluetooth is null");
                                bleStateByBtState = 10;
                            } else {
                                BluetoothManagerService.this.mBluetoothGatt = IBluetoothGatt.Stub.asInterface(Binder.allowBlocking(service));
                                BluetoothManagerService.this.onBluetoothGattServiceUp();
                                bleStateByBtState = 15;
                            }
                            BluetoothManagerService.this.mBluetoothLock.writeLock().unlock();
                            if (msg.arg1 == 2) {
                                BluetoothManagerService.this.bleStateByBtStateChangeHandler(11, bleStateByBtState);
                                return;
                            }
                            return;
                        }
                        BluetoothManagerService.this.mHandler.removeMessages(100);
                        BluetoothManagerService.this.mBinding = false;
                        BluetoothManagerService.this.mBluetoothBinder = service;
                        BluetoothManagerService.this.mBluetooth = IBluetooth.Stub.asInterface(Binder.allowBlocking(service));
                        if (!BluetoothManagerService.this.isNameAndAddressSet()) {
                            Message getMsg = BluetoothManagerService.this.mHandler.obtainMessage(200);
                            BluetoothManagerService.this.mHandler.sendMessage(getMsg);
                            if (i == i2) {
                                return;
                            }
                            return;
                        }
                        try {
                            BluetoothManagerService.this.mBluetooth.registerCallback(BluetoothManagerService.this.mBluetoothCallback);
                            BluetoothManagerService.this.mBluetooth.setBootState(BluetoothManagerService.this.mIsBootCompleted);
                        } catch (RemoteException re) {
                            Slog.e(BluetoothManagerService.TAG, "Unable to register BluetoothCallback", re);
                        }
                        BluetoothManagerService.this.sendBluetoothServiceUpCallback();
                        try {
                            if (BluetoothManagerService.this.mQuietEnable) {
                                if (!BluetoothManagerService.this.mBluetooth.enableNoAutoConnect()) {
                                    Slog.e(BluetoothManagerService.TAG, "IBluetooth.enableNoAutoConnect() returned false");
                                }
                            } else if (!BluetoothManagerService.this.mBluetooth.enable()) {
                                Slog.e(BluetoothManagerService.TAG, "IBluetooth.enable() returned false");
                            }
                        } catch (RemoteException e3) {
                            Slog.e(BluetoothManagerService.TAG, "Unable to call enable()", e3);
                        }
                        BluetoothManagerService.this.mBluetoothLock.writeLock().unlock();
                        if (msg.arg1 == 2) {
                            BluetoothManagerService.this.bleStateByBtStateChangeHandler(11, 10);
                            return;
                        }
                        return;
                    } finally {
                        BluetoothManagerService.this.mBluetoothLock.writeLock().unlock();
                        if (msg.arg1 == 2) {
                            BluetoothManagerService.this.bleStateByBtStateChangeHandler(11, 10);
                        }
                    }
                case 41:
                    Slog.e(BluetoothManagerService.TAG, "MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED(" + msg.arg1 + ")");
                    StringBuilder sb = new StringBuilder();
                    sb.append("BluetoothManagerService MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED: ");
                    sb.append(msg.arg1);
                    BluetoothDump.BtLog(sb.toString());
                    try {
                        BluetoothManagerService.this.mBluetoothLock.writeLock().lock();
                        if (msg.arg1 == 1) {
                            if (BluetoothManagerService.this.mBluetooth != null) {
                                BluetoothManagerService.this.mBluetooth = null;
                                BluetoothManagerService.this.mBluetoothLock.writeLock().unlock();
                                BluetoothManagerService.this.addCrashLog();
                                BluetoothManagerService.this.addActiveLog(7, BluetoothManagerService.this.mContext.getPackageName(), false);
                                if (BluetoothManagerService.this.mEnable) {
                                    BluetoothManagerService.this.mEnable = false;
                                    if (BluetoothManagerService.access$5708(BluetoothManagerService.this) < 6) {
                                        Slog.e(BluetoothManagerService.TAG, "Retry count : " + BluetoothManagerService.this.mErrorRecoveryRetryCounter);
                                        Message restartMsg = BluetoothManagerService.this.mHandler.obtainMessage(42);
                                        BluetoothManagerService.this.mHandler.sendMessageDelayed(restartMsg, 200L);
                                    }
                                }
                                BluetoothManagerService.this.sendBluetoothServiceDownCallback();
                                BluetoothManagerService.this.clearBleApps();
                                if (BluetoothManagerService.this.mState == 14 || BluetoothManagerService.this.mState == 15 || BluetoothManagerService.this.mState == 16) {
                                    if (BluetoothManagerService.this.mSamsungBleState == 1 || BluetoothManagerService.this.mSamsungBleState == 2) {
                                        i3 = 16;
                                        BluetoothManagerService.this.sendSamsungBleStateChanged(15, 16);
                                        BluetoothManagerService.this.mSamsungBleState = 3;
                                    } else {
                                        i3 = 16;
                                    }
                                    if (BluetoothManagerService.this.mSamsungBleState == 3) {
                                        BluetoothManagerService.this.sendSamsungBleStateChanged(i3, 10);
                                        BluetoothManagerService.this.mSamsungBleState = 0;
                                    }
                                }
                                if (BluetoothManagerService.this.mState == 11 || BluetoothManagerService.this.mState == 12) {
                                    BluetoothManagerService.this.bluetoothStateChangeHandler(12, 13);
                                    if (BluetoothManagerService.this.mGoogleBleState == 2) {
                                        BluetoothManagerService.this.sendBleStateChanged(12, 13);
                                        BluetoothManagerService.this.mGoogleBleState = 3;
                                    }
                                    if (BluetoothManagerService.this.mSamsungBleState == 2) {
                                        BluetoothManagerService.this.sendSamsungBleStateChanged(12, 13);
                                        BluetoothManagerService.this.mSamsungBleState = 3;
                                    }
                                    BluetoothManagerService.this.mState = 13;
                                }
                                if (BluetoothManagerService.this.mState == 13) {
                                    BluetoothManagerService.this.bluetoothStateChangeHandler(13, 10);
                                    if (BluetoothManagerService.this.mGoogleBleState == 3 || BluetoothManagerService.this.mGoogleBleState == 2) {
                                        BluetoothManagerService.this.sendBleStateChanged(13, 10);
                                        BluetoothManagerService.this.mGoogleBleState = 0;
                                    }
                                    if (BluetoothManagerService.this.mSamsungBleState == 3 || BluetoothManagerService.this.mSamsungBleState == 2) {
                                        BluetoothManagerService.this.sendSamsungBleStateChanged(13, 10);
                                        BluetoothManagerService.this.mSamsungBleState = 0;
                                    }
                                }
                                BluetoothManagerService.this.mGoogleBleState = 0;
                                BluetoothManagerService.this.mSamsungBleState = 0;
                                BluetoothManagerService.this.mHandler.removeMessages(60);
                                BluetoothManagerService.this.mState = 10;
                                return;
                            }
                        } else if (msg.arg1 == 2) {
                            BluetoothManagerService.this.mBluetoothGatt = null;
                        } else {
                            Slog.e(BluetoothManagerService.TAG, "Unknown argument for service disconnect!");
                        }
                        return;
                    } finally {
                    }
                case 42:
                    Slog.d(BluetoothManagerService.TAG, "MESSAGE_RESTART_BLUETOOTH_SERVICE");
                    BluetoothManagerService.this.mEnable = true;
                    BluetoothManagerService.this.addActiveLog(4, BluetoothManagerService.this.mContext.getPackageName(), true);
                    BluetoothManagerService.this.handleEnable(BluetoothManagerService.this.mQuietEnable);
                    return;
                case 60:
                    int prevState = msg.arg1;
                    int newState = msg.arg2;
                    if (BluetoothManagerService.DBG) {
                        Slog.d(BluetoothManagerService.TAG, "MESSAGE_BLUETOOTH_STATE_CHANGE: " + BluetoothAdapter.nameForState(prevState) + " > " + BluetoothAdapter.nameForState(newState));
                    }
                    BluetoothManagerService.this.mState = newState;
                    if (!BluetoothManagerService.this.isBleAppPresent() && prevState == 15 && newState == 16) {
                        BluetoothManagerService.this.mEnable = false;
                    }
                    BluetoothManagerService.this.bluetoothStateChangeHandler(prevState, newState);
                    BluetoothManagerService.this.bleStateByBtStateChangeHandler(prevState, newState);
                    if (prevState == 14 && newState == 10 && BluetoothManagerService.this.mBluetooth != null && BluetoothManagerService.this.mEnable) {
                        BluetoothManagerService.this.recoverBluetoothServiceFromError(false);
                    }
                    if (prevState == 11 && newState == 15 && BluetoothManagerService.this.mBluetooth != null && BluetoothManagerService.this.mEnable) {
                        BluetoothManagerService.this.recoverBluetoothServiceFromError(true);
                    }
                    if (prevState == 16 && newState == 10 && BluetoothManagerService.this.mEnable) {
                        Slog.d(BluetoothManagerService.TAG, "Entering STATE_OFF but mEnabled is true; restarting.");
                        BluetoothManagerService.this.waitForOnOff(false, true);
                        Message restartMsg2 = BluetoothManagerService.this.mHandler.obtainMessage(42);
                        BluetoothManagerService.this.mHandler.sendMessageDelayed(restartMsg2, 400L);
                    }
                    if ((newState == 12 || newState == 15) && BluetoothManagerService.this.mErrorRecoveryRetryCounter != 0) {
                        Slog.w(BluetoothManagerService.TAG, "bluetooth is recovered from error");
                        BluetoothDump.BtLog("BluetoothManagerService bluetooth is recovered from error. Reset mErrorRecoveryRetryCounter as Zero");
                        BluetoothManagerService.this.mErrorRecoveryRetryCounter = 0;
                    }
                    if (prevState == 13 && newState == 15 && BluetoothManagerService.this.mBluetooth != null && BluetoothManagerService.this.mEnableExternal) {
                        Slog.d(BluetoothManagerService.TAG, "Entering STATE_BLE_ON but mEnableExternal is true; restarting BR/EDR.");
                        try {
                            BluetoothManagerService.this.mBluetooth.onLeServiceUp();
                            return;
                        } catch (RemoteException e4) {
                            Slog.e(BluetoothManagerService.TAG, "", e4);
                            return;
                        }
                    } else if (newState != 12 || BluetoothManagerService.this.mEnableExternal) {
                        return;
                    } else {
                        Slog.d(BluetoothManagerService.TAG, "Entering STATE_ON but mEnableExternal is false; stopping BR/EDR.");
                        BluetoothManagerService.this.handleDisable();
                        return;
                    }
                case 100:
                    Slog.e(BluetoothManagerService.TAG, "MESSAGE_TIMEOUT_BIND");
                    BluetoothManagerService.this.mBluetoothLock.writeLock().lock();
                    BluetoothManagerService.this.mBinding = false;
                    return;
                case 101:
                    Slog.e(BluetoothManagerService.TAG, "MESSAGE_TIMEOUT_UNBIND");
                    BluetoothManagerService.this.mBluetoothLock.writeLock().lock();
                    BluetoothManagerService.this.mUnbinding = false;
                    return;
                case 200:
                    if (BluetoothManagerService.DBG) {
                        Slog.d(BluetoothManagerService.TAG, "MESSAGE_GET_NAME_AND_ADDRESS");
                    }
                    try {
                        BluetoothManagerService.this.mBluetoothLock.writeLock().lock();
                        if (BluetoothManagerService.this.mBluetooth == null && !BluetoothManagerService.this.mBinding) {
                            if (BluetoothManagerService.DBG) {
                                Slog.d(BluetoothManagerService.TAG, "Binding to service to get name and address");
                            }
                            Message timeoutMsg = BluetoothManagerService.this.mHandler.obtainMessage(100);
                            BluetoothManagerService.this.mHandler.sendMessageDelayed(timeoutMsg, 3000L);
                            Intent i5 = new Intent(IBluetooth.class.getName());
                            if (BluetoothManagerService.this.doBind(i5, BluetoothManagerService.this.mConnection, 65, UserHandle.CURRENT)) {
                                BluetoothManagerService.this.mBinding = true;
                            } else {
                                BluetoothManagerService.this.mHandler.removeMessages(100);
                            }
                        } else if (BluetoothManagerService.this.mBluetooth != null) {
                            try {
                                BluetoothManagerService.this.mBluetooth.enable();
                                if (BluetoothManagerService.this.waitForOnOffBle(true, false)) {
                                    BluetoothManagerService.this.mBluetooth.onBrEdrDown();
                                    BluetoothManagerService.this.waitForOnOffBle(false, true);
                                    BluetoothManagerService.this.storeNameAndAddress(BluetoothManagerService.this.mBluetooth.getName(), BluetoothManagerService.this.mBluetooth.getAddress());
                                    BluetoothManagerService.this.unbindAndFinish();
                                } else {
                                    BluetoothManagerService.this.waitForOnOffBle(false, true);
                                    BluetoothManagerService.this.unbindAndFinish();
                                    BluetoothDump.BtLog("BluetoothManagerServiceBLE_START_TIMEOUT: unbindAndFinish is called after BT is off");
                                }
                                if (BluetoothManagerService.this.mEnableExternal || BluetoothManagerService.this.isBleAppPresent()) {
                                    BluetoothManagerService.this.sendEnableMsg(false, 1, BluetoothManagerService.this.mContext.getPackageName());
                                }
                            } catch (RemoteException re2) {
                                Slog.e(BluetoothManagerService.TAG, "Unable to grab names", re2);
                            }
                        }
                        return;
                    } finally {
                    }
                case 300:
                    if (BluetoothManagerService.DBG) {
                        Slog.d(BluetoothManagerService.TAG, "MESSAGE_USER_SWITCHED");
                    }
                    BluetoothManagerService.this.mHandler.removeMessages(300);
                    if (BluetoothManagerService.this.mEnable) {
                        try {
                            if (BluetoothManagerService.this.mBluetooth != null) {
                                try {
                                    BluetoothManagerService.this.mBluetoothLock.readLock().lock();
                                    if (BluetoothManagerService.this.mBluetooth != null) {
                                        BluetoothManagerService.this.mBluetooth.unregisterCallback(BluetoothManagerService.this.mBluetoothCallback);
                                    }
                                } catch (RemoteException re3) {
                                    Slog.e(BluetoothManagerService.TAG, "Unable to unregister", re3);
                                }
                                BluetoothManagerService.this.mBluetoothLock.readLock().unlock();
                                boolean didDisableTimeout = false;
                                BluetoothManagerService.this.clearBleApps();
                                try {
                                    int st = BluetoothManagerService.this.mBluetooth.getState();
                                    Slog.d(BluetoothManagerService.TAG, "state : " + st);
                                    if (st == 11) {
                                        BluetoothManagerService.this.waitForOnOff(true, false);
                                        BluetoothManagerService.this.bluetoothStateChangeHandler(11, 12);
                                        BluetoothManagerService.this.bleStateByBtStateChangeHandler(11, 12);
                                        st = BluetoothManagerService.this.mBluetooth.getState();
                                    }
                                    if (st == 12) {
                                        BluetoothManagerService.this.bluetoothStateChangeHandler(12, 13);
                                        BluetoothManagerService.this.bleStateByBtStateChangeHandler(12, 13);
                                        Slog.d(BluetoothManagerService.TAG, "Turn off BR/EDR");
                                        BluetoothManagerService.this.unbindAllBluetoothProfileServices();
                                        BluetoothManagerService.this.addActiveLog(8, BluetoothManagerService.this.mContext.getPackageName(), false);
                                        BluetoothManagerService.this.handleDisable();
                                        BluetoothManagerService.this.waitForOnOffBle(true, false);
                                        st = BluetoothManagerService.this.mBluetooth.getState();
                                    }
                                    if (st == 13 || st == 14 || st == 10) {
                                        BluetoothManagerService.this.waitForOnOffBle(true, false);
                                        st = BluetoothManagerService.this.mBluetooth.getState();
                                    }
                                    if (st == 15) {
                                        Slog.d(BluetoothManagerService.TAG, "Turn off BLE");
                                        BluetoothManagerService.this.sendBrEdrDownCallback();
                                        BluetoothManagerService.this.waitForOnOff(false, true);
                                        BluetoothManagerService.this.bluetoothStateChangeHandler(13, 10);
                                        if (BluetoothManagerService.this.mSamsungBleState != 0) {
                                            BluetoothManagerService.this.mSamsungBleState = 3;
                                        }
                                        BluetoothManagerService.this.bleStateByBtStateChangeHandler(13, 10);
                                        BluetoothManagerService.this.mGoogleBleState = 0;
                                    }
                                    if (st == 16) {
                                        didDisableTimeout = !BluetoothManagerService.this.waitForOnOff(false, true);
                                    }
                                } catch (RemoteException e5) {
                                    Slog.d(BluetoothManagerService.TAG, "RemoteException " + e5);
                                }
                                BluetoothManagerService.this.sendBluetoothServiceDownCallback();
                                try {
                                    BluetoothManagerService.this.mBluetoothLock.writeLock().lock();
                                    if (BluetoothManagerService.this.mBluetooth != null) {
                                        BluetoothManagerService.this.mBluetooth = null;
                                        BluetoothManagerService.this.mContext.unbindService(BluetoothManagerService.this.mConnection);
                                    }
                                    BluetoothManagerService.this.mBluetoothGatt = null;
                                    if (didDisableTimeout) {
                                        SystemClock.sleep(3000L);
                                    } else {
                                        SystemClock.sleep(100L);
                                    }
                                } finally {
                                }
                            }
                            BluetoothManagerService.this.mHandler.removeMessages(60);
                            BluetoothManagerService.this.mState = 10;
                            BluetoothManagerService.this.addActiveLog(8, BluetoothManagerService.this.mContext.getPackageName(), true);
                            BluetoothManagerService.this.handleEnable(BluetoothManagerService.this.mQuietEnable);
                        } finally {
                        }
                    } else if (BluetoothManagerService.this.mBinding || BluetoothManagerService.this.mBluetooth != null) {
                        Message userMsg = BluetoothManagerService.this.mHandler.obtainMessage(300);
                        userMsg.arg2 = 1 + msg.arg2;
                        BluetoothManagerService.this.mHandler.sendMessageDelayed(userMsg, 200L);
                        if (BluetoothManagerService.DBG) {
                            Slog.d(BluetoothManagerService.TAG, "Retry MESSAGE_USER_SWITCHED " + userMsg.arg2);
                        }
                    }
                    Slog.d(BluetoothManagerService.TAG, "mSwitching to false");
                    BluetoothManagerService.this.mSwitching = false;
                    return;
                case 301:
                    if (BluetoothManagerService.DBG) {
                        Slog.d(BluetoothManagerService.TAG, "MESSAGE_USER_UNLOCKED");
                    }
                    BluetoothManagerService.this.mHandler.removeMessages(300);
                    if (BluetoothManagerService.this.mEnable && !BluetoothManagerService.this.mBinding && BluetoothManagerService.this.mBluetooth == null) {
                        if (BluetoothManagerService.DBG) {
                            Slog.d(BluetoothManagerService.TAG, "Enabled but not bound; retrying after unlock");
                        }
                        BluetoothManagerService.this.handleEnable(BluetoothManagerService.this.mQuietEnable);
                        return;
                    }
                    return;
                case 400:
                    ProfileServiceConnections psc = (ProfileServiceConnections) BluetoothManagerService.this.mProfileServices.get(Integer.valueOf(msg.arg1));
                    if (psc == null) {
                        return;
                    }
                    IBluetoothProfileServiceConnection proxy = (IBluetoothProfileServiceConnection) msg.obj;
                    psc.addProxy(proxy);
                    return;
                case 401:
                    ProfileServiceConnections psc2 = (ProfileServiceConnections) msg.obj;
                    removeMessages(401, msg.obj);
                    if (psc2 == null) {
                        return;
                    }
                    psc2.bindService();
                    return;
                case 500:
                    if (msg.arg1 == 0 && BluetoothManagerService.this.mEnable) {
                        if (BluetoothManagerService.DBG) {
                            Slog.d(BluetoothManagerService.TAG, "Restore Bluetooth state to disabled");
                        }
                        BluetoothManagerService.this.persistBluetoothSetting(0);
                        BluetoothManagerService.this.mEnableExternal = false;
                        BluetoothManagerService.this.sendDisableMsg(9, BluetoothManagerService.this.mContext.getPackageName());
                        return;
                    } else if (msg.arg1 != 1 || BluetoothManagerService.this.mEnable) {
                        return;
                    } else {
                        if (BluetoothManagerService.DBG) {
                            Slog.d(BluetoothManagerService.TAG, "Restore Bluetooth state to enabled");
                        }
                        BluetoothManagerService.this.mQuietEnableExternal = false;
                        BluetoothManagerService.this.mEnableExternal = true;
                        BluetoothManagerService.this.sendEnableMsg(false, 9, BluetoothManagerService.this.mContext.getPackageName());
                        return;
                    }
                case 700:
                    Slog.d(BluetoothManagerService.TAG, "mHandler.removeMessages(MESSAGE_BT_ENABLE_SECURITY_POPUP");
                    String packageName = (String) msg.obj;
                    BluetoothManagerService.this.mHandler.removeMessages(700);
                    BluetoothManagerService.this.enablePopupForBluetooth(packageName);
                    return;
                case 702:
                    int prevState2 = msg.arg1;
                    int newState2 = msg.arg2;
                    if (BluetoothManagerService.DBG) {
                        Slog.d(BluetoothManagerService.TAG, "MESSAGE_GOOGLE_BLE_STATE_CHANGE: prevState = " + prevState2 + ", newState=" + newState2);
                    }
                    BluetoothManagerService.this.sendBleStateChanged(prevState2, newState2);
                    return;
                case 703:
                    int prevState3 = msg.arg1;
                    int newState3 = msg.arg2;
                    if (BluetoothManagerService.DBG) {
                        Slog.d(BluetoothManagerService.TAG, "MESSAGE_SAMSUNG_BLE_STATE_CHANGE: prevState = " + prevState3 + ", newState=" + newState3);
                    }
                    BluetoothManagerService.this.sendSamsungBleStateChanged(prevState3, newState3);
                    return;
                case 1018:
                    IBluetoothDisableBleCallback callback2 = (IBluetoothDisableBleCallback) msg.obj;
                    if (callback2 != null) {
                        BluetoothManagerService.this.mDisableBleCallback.register(callback2);
                        return;
                    }
                    return;
                case 1019:
                    IBluetoothDisableBleCallback callback3 = (IBluetoothDisableBleCallback) msg.obj;
                    if (callback3 != null) {
                        BluetoothManagerService.this.mDisableBleCallback.unregister(callback3);
                        return;
                    }
                    return;
                case 1020:
                    IBluetoothSinkServiceChangeCallback callback4 = (IBluetoothSinkServiceChangeCallback) msg.obj;
                    if (callback4 != null) {
                        BluetoothManagerService.this.mSinkServiceChangeCallback.register(callback4);
                        return;
                    }
                    return;
                case 1021:
                    IBluetoothSinkServiceChangeCallback callback5 = (IBluetoothSinkServiceChangeCallback) msg.obj;
                    if (callback5 != null) {
                        BluetoothManagerService.this.mSinkServiceChangeCallback.unregister(callback5);
                        return;
                    }
                    return;
                case 1022:
                    boolean isUp = ((Boolean) msg.obj).booleanValue();
                    try {
                        int n = BluetoothManagerService.this.mSinkServiceChangeCallback.beginBroadcast();
                        if (BluetoothManagerService.DBG) {
                            Slog.d(BluetoothManagerService.TAG, "Broadcasting sendSinkServiceChangeCallback to " + n + " receivers.");
                        }
                        while (true) {
                            int i6 = i4;
                            if (i6 >= n) {
                                return;
                            }
                            try {
                                BluetoothManagerService.this.mSinkServiceChangeCallback.getBroadcastItem(i6).onBluetoothServiceStateChange(isUp);
                            } catch (RemoteException e6) {
                                Slog.e(BluetoothManagerService.TAG, "Unable to call sendSinkServiceChangeCallback() on callback #" + i6, e6);
                            }
                            i4 = i6 + 1;
                        }
                    } finally {
                        BluetoothManagerService.this.mSinkServiceChangeCallback.finishBroadcast();
                    }
                default:
                    return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleEnable(boolean quietMode) {
        this.mQuietEnable = quietMode;
        this.mEnable = true;
        try {
            this.mBluetoothLock.writeLock().lock();
            if (this.mBluetooth == null && !this.mBinding) {
                Message timeoutMsg = this.mHandler.obtainMessage(100);
                this.mHandler.sendMessageDelayed(timeoutMsg, 3000L);
                Intent i = new Intent(IBluetooth.class.getName());
                if (!doBind(i, this.mConnection, 65, UserHandle.CURRENT)) {
                    this.mHandler.removeMessages(100);
                } else {
                    this.mBinding = true;
                }
            } else if (this.mBluetooth != null) {
                try {
                    if (!this.mQuietEnable) {
                        if (!this.mBluetooth.enable()) {
                            Slog.e(TAG, "IBluetooth.enable() returned false");
                        }
                    } else if (!this.mBluetooth.enableNoAutoConnect()) {
                        Slog.e(TAG, "IBluetooth.enableNoAutoConnect() returned false");
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to call enable()", e);
                }
            }
        } finally {
            this.mBluetoothLock.writeLock().unlock();
        }
    }

    boolean doBind(Intent intent, ServiceConnection conn, int flags, UserHandle user) {
        ComponentName comp = intent.resolveSystemService(this.mContext.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !this.mContext.bindServiceAsUser(intent, conn, flags, user)) {
            Slog.e(TAG, "Fail to bind to: " + intent);
            return false;
        }
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleDisable() {
        this.mQuietEnable = false;
        try {
            try {
                this.mBluetoothLock.readLock().lock();
                if (this.mBluetooth != null) {
                    if (DBG) {
                        Slog.d(TAG, "Sending off request.");
                    }
                    if (!this.mBluetooth.disable()) {
                        Slog.e(TAG, "IBluetooth.disable() returned false");
                    }
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "Unable to call disable()", e);
            }
        } finally {
            this.mBluetoothLock.readLock().unlock();
        }
    }

    private boolean checkIfCallerIsForegroundUser() {
        boolean valid;
        int callingUser = UserHandle.getCallingUserId();
        int callingUid = Binder.getCallingUid();
        long callingIdentity = Binder.clearCallingIdentity();
        UserManager um = (UserManager) this.mContext.getSystemService(ApnSettingsPolicy.Carriers.USER);
        UserInfo ui = um.getProfileParent(callingUser);
        int parentUser = ui != null ? ui.id : -10000;
        int callingAppId = UserHandle.getAppId(callingUid);
        boolean z = false;
        try {
            int foregroundUser = ActivityManager.getCurrentUser();
            if (callingUser != foregroundUser && parentUser != foregroundUser && callingAppId != 1027 && callingAppId != this.mSystemUiUid) {
                valid = z;
                if (DBG && !valid) {
                    Slog.d(TAG, "checkIfCallerIsForegroundUser: valid=" + valid + " callingUser=" + callingUser + " parentUser=" + parentUser + " foregroundUser=" + foregroundUser);
                }
                return valid;
            }
            z = true;
            valid = z;
            if (DBG) {
                Slog.d(TAG, "checkIfCallerIsForegroundUser: valid=" + valid + " callingUser=" + callingUser + " parentUser=" + parentUser + " foregroundUser=" + foregroundUser);
            }
            return valid;
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendBleStateChanged(int prevState, int newState) {
        if (DBG) {
            Slog.d(TAG, "Sending BLE State Change: " + BluetoothAdapter.nameForState(prevState) + " > " + BluetoothAdapter.nameForState(newState));
        }
        Intent intent = new Intent("android.bluetooth.adapter.action.BLE_STATE_CHANGED");
        intent.putExtra("android.bluetooth.adapter.extra.PREVIOUS_STATE", prevState);
        intent.putExtra("android.bluetooth.adapter.extra.STATE", newState);
        intent.addFlags(67108864);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL, BLUETOOTH_PERM);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendSamsungBleStateChanged(int prevState, int newState) {
        if (DBG) {
            Slog.d(TAG, "Samsung BLE State Change Intent: " + BluetoothAdapter.nameForState(prevState) + " > " + BluetoothAdapter.nameForState(newState));
        }
        Intent intent = new Intent("com.samsung.bluetooth.adapter.action.BLE_STATE_CHANGED");
        intent.putExtra("android.bluetooth.adapter.extra.PREVIOUS_STATE", prevState);
        intent.putExtra("android.bluetooth.adapter.extra.STATE", newState);
        intent.addFlags(83886080);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL, BLUETOOTH_PERM);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void bluetoothStateChangeHandler(int prevState, int newState) {
        boolean isStandardBroadcast = true;
        if (prevState == newState) {
            return;
        }
        if (newState == 15 || newState == 10) {
            boolean intermediate_off = prevState == 13 && newState == 15;
            if (newState == 10) {
                if (DBG) {
                    Slog.d(TAG, "Bluetooth is complete send Service Down");
                }
                sendBluetoothServiceDownCallback();
                unbindAndFinish();
                isStandardBroadcast = false;
                if (mShutdown) {
                    Slog.d(TAG, "After shutdown : " + mBleAppCount + " " + isAirplaneModeOn() + " " + this.mEnableExternal);
                    mShutdown = false;
                    if (this.mEnableExternal) {
                        synchronized (this.mReceiver) {
                            sendEnableMsg(this.mQuietEnable, 4, null);
                        }
                    }
                } else if (this.mEnable) {
                    int i = this.mErrorRecoveryRetryCounter;
                    this.mErrorRecoveryRetryCounter = i + 1;
                    if (i < 6) {
                        Slog.d(TAG, "Call sendEnableMsg() again!!! mErrorRecoveryRetryCounter : " + this.mErrorRecoveryRetryCounter + ", mQuietEnable : " + this.mQuietEnable);
                        StringBuilder sb = new StringBuilder();
                        sb.append("BluetoothManagerService Call sendEnableMsg to recover. mErrorRecoveryRetryCounter : ");
                        sb.append(this.mErrorRecoveryRetryCounter);
                        BluetoothDump.BtLog(sb.toString());
                        synchronized (this.mReceiver) {
                            sendEnableMsg(this.mQuietEnable, 4, null);
                        }
                    } else {
                        this.mEnable = false;
                        BluetoothDump.BtLog("BluetoothManagerService RetryCounter reached the limit. Don't call sendEnableMsg anymore");
                    }
                }
            } else if (!intermediate_off) {
                if (DBG) {
                    Slog.d(TAG, "Bluetooth is in LE only mode");
                }
                if (this.mBluetoothGatt != null) {
                    if (DBG) {
                        Slog.d(TAG, "Calling BluetoothGattServiceUp");
                    }
                    BluetoothDump.BtLog("BluetoothManagerService BLE_ON, mBluetoothGatt is not null");
                    onBluetoothGattServiceUp();
                } else {
                    if (DBG) {
                        Slog.d(TAG, "Binding Bluetooth GATT service");
                    }
                    if (this.mContext.getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
                        Intent i2 = new Intent(IBluetoothGatt.class.getName());
                        doBind(i2, this.mConnection, 65, UserHandle.CURRENT);
                    }
                }
                isStandardBroadcast = false;
            } else if (intermediate_off) {
                if (DBG) {
                    Slog.d(TAG, "Intermediate off, back to LE only mode");
                }
                sendBluetoothStateCallback(false);
                newState = 10;
                sendBrEdrDownCallback();
            }
        } else if (newState == 12) {
            bindHeadsetProfileServiceConnection();
            boolean isUp = newState == 12;
            sendBluetoothStateCallback(isUp);
        } else if (newState == 14 || newState == 16) {
            isStandardBroadcast = false;
        } else if (newState != 11) {
        }
        if (isStandardBroadcast) {
            if (prevState == 15) {
                prevState = 10;
            }
            Intent intent = new Intent("android.bluetooth.adapter.action.STATE_CHANGED");
            intent.putExtra("android.bluetooth.adapter.extra.PREVIOUS_STATE", prevState);
            intent.putExtra("android.bluetooth.adapter.extra.STATE", newState);
            intent.addFlags(67108864);
            this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL, BLUETOOTH_PERM);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void bleStateByBtStateChangeHandler(int prevState, int newState) {
        if (prevState == newState) {
            return;
        }
        synchronized (this.mBleLock) {
            try {
                if (newState == 15 || newState == 10) {
                    boolean intermediate_off = prevState == 13 && newState == 15;
                    if (newState == 10) {
                        if (prevState == 16) {
                            Slog.d(TAG, "Bluetooth is complete send Service Down");
                            if (this.mGoogleBleState == 3) {
                                sendBleStateChanged(prevState, newState);
                                this.mGoogleBleState = 0;
                                sendDisableBleCallback();
                            }
                            if (this.mSamsungBleState == 3) {
                                sendSamsungBleStateChanged(prevState, newState);
                                this.mSamsungBleState = 0;
                            }
                        } else {
                            clearBleApps();
                            if (prevState != 14 && prevState != 15) {
                                if (prevState == 11 || prevState == 12 || prevState == 13) {
                                    if (this.mGoogleBleState == 2) {
                                        sendBleStateChanged(prevState, newState);
                                    }
                                    if (this.mSamsungBleState == 2) {
                                        sendSamsungBleStateChanged(prevState, newState);
                                    }
                                }
                                this.mSamsungBleState = 0;
                                this.mGoogleBleState = 0;
                            }
                            sendSamsungBleStateChanged(prevState, newState);
                            this.mSamsungBleState = 0;
                            this.mGoogleBleState = 0;
                        }
                    } else if (!intermediate_off) {
                        Slog.d(TAG, "Bluetooth is in LE only mode");
                        if (this.mAdapterServiceCrashed != 0) {
                            if (this.mBluetoothGatt == null) {
                                BluetoothDump.BtLog("BluetoothManagerService mBluetoothGatt is null after mAdapterServiceCrashed");
                                Slog.d(TAG, "mBluetoothGatt is null after mAdapterServiceCrashed");
                            }
                            this.mAdapterServiceCrashed = 0;
                        }
                        if (this.mBluetoothGatt != null) {
                            if ((this.mEnableExternal && this.mGoogleBleState == 0) || (mBleAppCount - mSamsungBleAppCount > 0 && this.mGoogleBleState == 0)) {
                                sendBleStateChanged(10, 14);
                                sendBleStateChanged(prevState, newState);
                                this.mGoogleBleState = 2;
                            } else if (this.mGoogleBleState == 1) {
                                sendBleStateChanged(prevState, newState);
                                this.mGoogleBleState = 2;
                            }
                            if ((this.mEnableExternal && this.mSamsungBleState == 0) || (mSamsungBleAppCount > 0 && this.mSamsungBleState == 0)) {
                                sendSamsungBleStateChanged(10, 14);
                                sendSamsungBleStateChanged(prevState, newState);
                                this.mSamsungBleState = 2;
                            } else if (this.mSamsungBleState == 1) {
                                sendSamsungBleStateChanged(prevState, newState);
                                this.mSamsungBleState = 2;
                            }
                        }
                    } else if (intermediate_off) {
                        Slog.d(TAG, "Intermediate off, back to LE only mode");
                        if (this.mGoogleBleState == 2) {
                            sendBleStateChanged(prevState, newState);
                            if (mSamsungBleAppCount != 0 && mBleAppCount - mSamsungBleAppCount == 0) {
                                sendBleStateChanged(15, 16);
                                sendBleStateChanged(16, 10);
                                this.mGoogleBleState = 0;
                                sendDisableBleCallback();
                            }
                        }
                        if (this.mSamsungBleState == 2) {
                            sendSamsungBleStateChanged(prevState, newState);
                            if (mBleAppCount != 0 && mSamsungBleAppCount == 0) {
                                sendSamsungBleStateChanged(15, 16);
                                sendSamsungBleStateChanged(16, 10);
                                this.mSamsungBleState = 0;
                            }
                        }
                    }
                } else if (newState == 12) {
                    Slog.d(TAG, "BT ON On, G state: " + this.mGoogleBleState + ", S state: " + this.mSamsungBleState);
                    if (this.mGoogleBleState == 2) {
                        sendBleStateChanged(prevState, newState);
                    }
                    if (this.mSamsungBleState == 2) {
                        sendSamsungBleStateChanged(prevState, newState);
                    }
                } else if (newState == 14) {
                    Slog.d(TAG, "Ble Turning On, G state: " + this.mGoogleBleState + ", S state: " + this.mSamsungBleState);
                    if (mBleAppCount - mSamsungBleAppCount > 0 && this.mGoogleBleState == 0) {
                        sendBleStateChanged(prevState, newState);
                        this.mGoogleBleState = 1;
                    } else if (this.mEnableExternal && this.mGoogleBleState == 0) {
                        sendBleStateChanged(prevState, newState);
                        this.mGoogleBleState = 1;
                    }
                    if (mSamsungBleAppCount > 0 && this.mSamsungBleState == 0) {
                        sendSamsungBleStateChanged(prevState, newState);
                        this.mSamsungBleState = 1;
                    } else if (this.mEnableExternal && this.mSamsungBleState == 0) {
                        sendSamsungBleStateChanged(prevState, newState);
                        this.mSamsungBleState = 1;
                    }
                } else if (newState == 16) {
                    Slog.d(TAG, "Ble Turning Off, G state: " + this.mGoogleBleState + ", S state: " + this.mSamsungBleState);
                    if (this.mGoogleBleState == 2) {
                        sendBleStateChanged(prevState, newState);
                        this.mGoogleBleState = 3;
                    }
                    if (this.mSamsungBleState == 2) {
                        sendSamsungBleStateChanged(prevState, newState);
                        this.mSamsungBleState = 3;
                    }
                } else if (newState == 11) {
                    Slog.d(TAG, "Turning On, G state: " + this.mGoogleBleState + ", S state: " + this.mSamsungBleState + ", mBLE count: " + mBleAppCount + ", s BLE count: " + mSamsungBleAppCount);
                    if (this.mGoogleBleState == 2) {
                        sendBleStateChanged(prevState, newState);
                    } else {
                        sendBleStateChanged(10, 14);
                        sendBleStateChanged(14, 15);
                        sendBleStateChanged(prevState, newState);
                        this.mGoogleBleState = 2;
                    }
                    if (this.mSamsungBleState == 2) {
                        sendSamsungBleStateChanged(prevState, newState);
                    } else if (this.mSamsungBleState == 1 && mSamsungBleAppCount == 0) {
                        sendSamsungBleStateChanged(10, 14);
                        sendSamsungBleStateChanged(14, 15);
                        sendSamsungBleStateChanged(prevState, newState);
                        this.mSamsungBleState = 2;
                    }
                } else if (newState == 13) {
                    Slog.d(TAG, "Turning Off, G state: " + this.mGoogleBleState + ", S state: " + this.mSamsungBleState + ", mBLE count: " + mBleAppCount + ", s BLE count: " + mSamsungBleAppCount);
                    if (this.mGoogleBleState == 2) {
                        sendBleStateChanged(prevState, newState);
                    }
                    if (this.mSamsungBleState == 2) {
                        sendSamsungBleStateChanged(prevState, newState);
                    }
                }
            } finally {
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean waitForOnOff(boolean on, boolean off) {
        int i = 0;
        while (true) {
            if (i >= 40) {
                break;
            }
            try {
                this.mBluetoothLock.readLock().lock();
                if (this.mBluetooth == null) {
                    break;
                }
                if (on) {
                    if (this.mBluetooth.getState() == 12) {
                        return true;
                    }
                } else if (off) {
                    if (this.mBluetooth.getState() == 10) {
                        return true;
                    }
                } else if (this.mBluetooth.getState() != 12) {
                    return true;
                }
                this.mBluetoothLock.readLock().unlock();
                if (on || off) {
                    SystemClock.sleep(250L);
                } else {
                    SystemClock.sleep(50L);
                }
                i++;
            } catch (RemoteException e) {
                Slog.e(TAG, "getState()", e);
                Slog.e(TAG, "waitForOnOff time out");
                return false;
            } finally {
                this.mBluetoothLock.readLock().unlock();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code restructure failed: missing block: B:29:0x0038, code lost:
        if (r7 != false) goto L19;
     */
    /* JADX WARN: Code restructure failed: missing block: B:30:0x003a, code lost:
        if (r8 == false) goto L16;
     */
    /* JADX WARN: Code restructure failed: missing block: B:32:0x003d, code lost:
        android.os.SystemClock.sleep(50);
     */
    /* JADX WARN: Code restructure failed: missing block: B:33:0x0043, code lost:
        android.os.SystemClock.sleep(250);
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public boolean waitForOnOffBle(boolean r7, boolean r8) {
        /*
            r6 = this;
            r0 = 0
            r1 = r0
        L2:
            r2 = 80
            if (r1 >= r2) goto L5a
            com.android.server.BluetoothManagerService$BluetoothServiceConnection r2 = r6.mConnection
            monitor-enter(r2)
            android.bluetooth.IBluetooth r3 = r6.mBluetooth     // Catch: java.lang.Throwable -> L4b android.os.RemoteException -> L4d
            if (r3 != 0) goto Lf
            monitor-exit(r2)     // Catch: java.lang.Throwable -> L4b
            goto L5a
        Lf:
            r3 = 15
            r4 = 1
            if (r7 == 0) goto L1e
            android.bluetooth.IBluetooth r5 = r6.mBluetooth     // Catch: java.lang.Throwable -> L4b android.os.RemoteException -> L4d
            int r5 = r5.getState()     // Catch: java.lang.Throwable -> L4b android.os.RemoteException -> L4d
            if (r5 != r3) goto L36
            monitor-exit(r2)     // Catch: java.lang.Throwable -> L4b
            return r4
        L1e:
            if (r8 == 0) goto L2c
            android.bluetooth.IBluetooth r3 = r6.mBluetooth     // Catch: java.lang.Throwable -> L4b android.os.RemoteException -> L4d
            int r3 = r3.getState()     // Catch: java.lang.Throwable -> L4b android.os.RemoteException -> L4d
            r5 = 10
            if (r3 != r5) goto L36
            monitor-exit(r2)     // Catch: java.lang.Throwable -> L4b
            return r4
        L2c:
            android.bluetooth.IBluetooth r5 = r6.mBluetooth     // Catch: java.lang.Throwable -> L4b android.os.RemoteException -> L4d
            int r5 = r5.getState()     // Catch: java.lang.Throwable -> L4b android.os.RemoteException -> L4d
            if (r5 == r3) goto L36
            monitor-exit(r2)     // Catch: java.lang.Throwable -> L4b
            return r4
        L36:
            monitor-exit(r2)     // Catch: java.lang.Throwable -> L4b
            if (r7 != 0) goto L43
            if (r8 == 0) goto L3d
            goto L43
        L3d:
            r2 = 50
            android.os.SystemClock.sleep(r2)
            goto L48
        L43:
            r2 = 250(0xfa, double:1.235E-321)
            android.os.SystemClock.sleep(r2)
        L48:
            int r1 = r1 + 1
            goto L2
        L4b:
            r0 = move-exception
            goto L58
        L4d:
            r3 = move-exception
            java.lang.String r4 = "BluetoothManagerService"
            java.lang.String r5 = "getState()"
            android.util.Slog.e(r4, r5, r3)     // Catch: java.lang.Throwable -> L4b
            monitor-exit(r2)     // Catch: java.lang.Throwable -> L4b
            goto L5a
        L58:
            monitor-exit(r2)     // Catch: java.lang.Throwable -> L4b
            throw r0
        L5a:
            java.lang.String r2 = "BluetoothManagerService"
            java.lang.String r3 = "waitForOnOffBle time out"
            android.util.Slog.e(r2, r3)
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.BluetoothManagerService.waitForOnOffBle(boolean, boolean):boolean");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendDisableMsg(int reason, String packageName) {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(2));
        addActiveLog(reason, packageName, false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendEnableMsg(boolean quietMode, int reason, String packageName) {
        this.mEnable = true;
        this.mHandler.sendMessage(this.mHandler.obtainMessage(1, quietMode ? 1 : 0, 0));
        addActiveLog(reason, packageName, true);
        this.mLastEnabledTime = SystemClock.elapsedRealtime();
    }

    public boolean isRestrictedBtPolicy() {
        return checkItPolicy(true) == 0 || restrictedByMdmPolicy(0) || restrictedByMdmPolicy(1) || PersonaServiceHelper.shouldBlockBluetooth(this.mContext, UserHandle.getCallingUserId());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int checkItPolicy(boolean notifyOff) {
        IDevicePolicyManager mDPM = IDevicePolicyManager.Stub.asInterface(ServiceManager.getService("device_policy"));
        int userId = 0;
        try {
            userId = ActivityManagerNative.getDefault().getCurrentUser().id;
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed getting userId using ActivityManagerNative", e);
        } catch (SecurityException e2) {
            Slog.w(TAG, "Failed getting userId using ActivityManagerNative", e2);
        }
        if (mDPM != null) {
            try {
                int BtMode = mDPM.semGetAllowBluetoothMode((ComponentName) null, userId);
                if (BtMode == 0) {
                    mPolicy = 0;
                    SystemProperties.set("service.bt.security.policy.mode", "0");
                    Slog.e(TAG, "BT IT Policy is changed to disable Bluetooth. Cannot enable");
                    BluetoothDump.BtLog("BluetoothManagerService -- BT IT Policy is changed to Disable mode");
                    if (notifyOff) {
                        Intent intent = new Intent("android.bluetooth.adapter.action.STATE_CHANGED");
                        intent.putExtra("android.bluetooth.adapter.extra.STATE", 10);
                        intent.addFlags(67108864);
                        this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT, BLUETOOTH_PERM);
                    }
                    this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(5), 100L);
                } else if (BtMode == 1) {
                    mPolicy = 1;
                    SystemProperties.set("service.bt.security.policy.mode", "1");
                    Slog.e(TAG, "BT IT Policy is changed to support HF only. Can use HF only");
                    BluetoothDump.BtLog("BluetoothManagerService -- BT IT Policy is changed to Hands Free mode");
                    this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(5), 100L);
                } else {
                    mPolicy = 2;
                    SystemProperties.set("service.bt.security.policy.mode", "2");
                }
            } catch (Exception e3) {
                mPolicy = 2;
            }
        }
        return mPolicy;
    }

    private boolean restrictedByMdmPolicy(int type) {
        String selectionType;
        if (type == 0) {
            selectionType = "isBluetoothEnabled";
        } else if (type == 1) {
            selectionType = "isBLEAllowed";
        } else {
            Slog.e(TAG, "Type error : " + type);
            return false;
        }
        Uri uri = Uri.parse("content://com.sec.knox.provider/BluetoothPolicy");
        boolean isEnabled = true;
        Cursor cr = this.mContentResolver.query(uri, null, selectionType, null, null);
        try {
            if (cr != null) {
                try {
                    cr.moveToFirst();
                    isEnabled = cr.getString(cr.getColumnIndex(selectionType)).equals("true");
                } catch (CursorWindowAllocationException e) {
                    Slog.e(TAG, "CursorWindowAllocationException" + e);
                } catch (CursorIndexOutOfBoundsException e2) {
                    Slog.e(TAG, "CursorIndexOutOfBoundsException" + e2);
                }
            }
            if (isEnabled) {
                return false;
            }
            Slog.d(TAG, "MDM: " + type + " is Disabled");
            return true;
        } finally {
            cr.close();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void addActiveLog(int reason, String packageName, boolean enable) {
        synchronized (this.mActiveLogs) {
            if (this.mActiveLogs.size() > 20) {
                this.mActiveLogs.remove();
            }
            this.mActiveLogs.add(new ActiveLog(reason, packageName, enable, System.currentTimeMillis()));
        }
        int state = enable ? 1 : 2;
        StatsLog.write_non_chained(67, Binder.getCallingUid(), null, state, reason, packageName);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void addCrashLog() {
        synchronized (this.mCrashTimestamps) {
            if (this.mCrashTimestamps.size() == 100) {
                this.mCrashTimestamps.removeFirst();
            }
            this.mCrashTimestamps.add(Long.valueOf(System.currentTimeMillis()));
            this.mCrashes++;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void recoverBluetoothServiceFromError(boolean clearBle) {
        Slog.e(TAG, "recoverBluetoothServiceFromError");
        try {
            try {
                this.mBluetoothLock.readLock().lock();
                if (this.mBluetooth != null) {
                    this.mBluetooth.unregisterCallback(this.mBluetoothCallback);
                }
            } catch (Throwable th) {
                this.mBluetoothLock.readLock().unlock();
                throw th;
            }
        } catch (RemoteException re) {
            Slog.e(TAG, "Unable to unregister", re);
        }
        this.mBluetoothLock.readLock().unlock();
        SystemClock.sleep(500L);
        addActiveLog(5, this.mContext.getPackageName(), false);
        sendBluetoothServiceDownCallback();
        try {
            this.mBluetoothLock.writeLock().lock();
            if (this.mBluetooth != null) {
                this.mBluetooth = null;
                this.mContext.unbindService(this.mConnection);
            }
            this.mBluetoothGatt = null;
            this.mBluetoothLock.writeLock().unlock();
            this.mHandler.removeMessages(60);
            this.mState = 10;
            if (clearBle) {
                clearBleApps();
            }
            this.mEnable = false;
            int i = this.mErrorRecoveryRetryCounter;
            this.mErrorRecoveryRetryCounter = i + 1;
            if (i < 6) {
                Message restartMsg = this.mHandler.obtainMessage(42);
                this.mHandler.sendMessageDelayed(restartMsg, 3000L);
            }
        } catch (Throwable th2) {
            this.mBluetoothLock.writeLock().unlock();
            throw th2;
        }
    }

    private boolean isBluetoothDisallowed() {
        long callingIdentity = Binder.clearCallingIdentity();
        try {
            return ((UserManager) this.mContext.getSystemService(UserManager.class)).hasUserRestriction("no_bluetooth", UserHandle.SYSTEM);
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateOppLauncherComponentState(int userId, boolean bluetoothSharingDisallowed) {
        ComponentName oppLauncherComponent = new ComponentName("com.android.bluetooth", "com.android.bluetooth.opp.BluetoothOppLauncherActivity");
        int newState = bluetoothSharingDisallowed ? 2 : 0;
        try {
            IPackageManager imp = AppGlobals.getPackageManager();
            imp.setComponentEnabledSetting(oppLauncherComponent, newState, 1, userId);
        } catch (Exception e) {
        }
    }

    private boolean isCustomizedByKccAgent() {
        File kccAgentSharedPref = new File("/data/data/com.sec.knox.kccagent/shared_prefs/customized.xml");
        return kccAgentSharedPref.exists();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void enablePopupForBluetooth(final String packageName) {
        Slog.d(TAG, "enablePopupForBluetooth");
        if (this.mBtEnablePopUp != null && this.mBtEnablePopUp.isShowing()) {
            this.mBtEnablePopUp.cancel();
        }
        StatusBarManager statusBar = (StatusBarManager) this.mContext.getSystemService("statusbar");
        final KeyguardManager keyguardManager = (KeyguardManager) this.mContext.getSystemService("keyguard");
        if (statusBar != null) {
            statusBar.collapsePanels();
        }
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() { // from class: com.android.server.BluetoothManagerService.6
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                if (which == -1) {
                    boolean unused = BluetoothManagerService.isEnablePopup = true;
                    BluetoothDump.BtLog("BluetoothManagerService -- enable(), China popup state : positive");
                    try {
                        BluetoothManagerService.this.enable(packageName);
                    } catch (RemoteException e) {
                        Slog.e(BluetoothManagerService.TAG, "", e);
                    }
                } else {
                    BluetoothManagerService.this.cancelChinaPopupState();
                }
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        };
        Resources r = Resources.getSystem();
        this.mBtEnablePopUp = new AlertDialog.Builder(this.mContext, 5).setTitle(this.mContext.getString(17039693)).setMessage(this.mContext.getString(17039681, this.chinaThirdPartyAppName, this.mContext.getString(17039682))).setPositiveButton(r.getString(17039370), listener).setNegativeButton(r.getString(17039360), listener).setCancelable(true).setOnKeyListener(new DialogInterface.OnKeyListener() { // from class: com.android.server.BluetoothManagerService.8
            @Override // android.content.DialogInterface.OnKeyListener
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (event.getKeyCode() == 3 && keyguardManager != null && !keyguardManager.isKeyguardLocked()) {
                    BluetoothManagerService.this.cancelChinaPopupState();
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                    try {
                        Intent intent = new Intent();
                        intent.setAction("android.intent.action.MAIN");
                        intent.addCategory("android.intent.category.HOME");
                        intent.addFlags(329252864);
                        BluetoothManagerService.this.mContext.startActivity(intent);
                        return false;
                    } catch (ActivityNotFoundException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
                return false;
            }
        }).setOnCancelListener(new DialogInterface.OnCancelListener() { // from class: com.android.server.BluetoothManagerService.7
            @Override // android.content.DialogInterface.OnCancelListener
            public void onCancel(DialogInterface dialog) {
                BluetoothManagerService.this.cancelChinaPopupState();
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        }).create();
        if (keyguardManager != null && keyguardManager.isKeyguardLocked()) {
            this.mBtEnablePopUp.getWindow().setType(2009);
        } else {
            this.mBtEnablePopUp.getWindow().setType(2008);
        }
        this.mBtEnablePopUp.show();
        SystemClock.sleep(100L);
        this.chinaThirdPartyAppName = "";
    }

    public boolean isUserAction() {
        String[] strArr;
        ActivityManager am = (ActivityManager) this.mContext.getSystemService("activity");
        List<ActivityManager.RunningAppProcessInfo> list = am.getRunningAppProcesses();
        if (list != null && list.size() > 0) {
            for (ActivityManager.RunningAppProcessInfo info : list) {
                PackageManager pm = this.mContext.getPackageManager();
                if ("com.android.systemui".equals(info.processName.toLowerCase()) || "com.android.settings".equals(info.processName.toLowerCase()) || "com.sec.android.easysettings".equals(info.processName.toLowerCase()) || "com.sec.android.emergencymode.service".equals(info.processName.toLowerCase()) || "com.samsung.android.app.sreminder".equals(info.processName.toLowerCase()) || (("com.sec.knox.kccagent".equals(info.processName.toLowerCase()) && !isCustomizedByKccAgent()) || "com.samsung.cts.AndroidBluetooth".toLowerCase().equals(info.processName.toLowerCase()) || "com.samsung.android.app.routines".toLowerCase().equals(info.processName.toLowerCase()))) {
                    Slog.d(TAG, "processName = " + info.processName);
                    if (info.pid == getCallingPid()) {
                        Slog.d(TAG, "info pid = " + info.pid + ",  getCallingPid() = " + getCallingPid());
                        if (this.mBtEnablePopUp != null && this.mBtEnablePopUp.isShowing()) {
                            this.mBtEnablePopUp.cancel();
                        }
                        return false;
                    }
                }
                if (info.pid == getCallingPid()) {
                    try {
                        if (info.pkgList != null) {
                            for (String mPackageList : info.pkgList) {
                                this.chinaThirdPartyAppName = pm.getApplicationLabel(pm.getApplicationInfo(mPackageList, 128)).toString();
                                Slog.d(TAG, "appName = " + this.chinaThirdPartyAppName);
                            }
                        } else {
                            this.chinaThirdPartyAppName = pm.getApplicationLabel(pm.getApplicationInfo(info.processName, 128)).toString();
                            Slog.d(TAG, "appName = " + this.chinaThirdPartyAppName);
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        this.chinaThirdPartyAppName = info.processName;
                        e.printStackTrace();
                    }
                    return true;
                }
            }
        }
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void cancelChinaPopupState() {
        BluetoothDump.BtLog("BluetoothManagerService -- enable(), China popup state : cancel");
        if (this.mBluetooth != null) {
            try {
                if (this.mBluetooth.getState() != 11 && this.mBluetooth.getState() != 12) {
                    this.mContext.sendBroadcast(new Intent("com.samsung.settings.CHINA_NAL_SECURITY_SCAN_CANCEL"));
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "", e);
            }
        }
    }

    public void putLogs(String cmd) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (Binder.getCallingUid() != 1000 && !checkIfCallerIsForegroundUser()) {
            return;
        }
        try {
            if (subBuffer.length() + cmd.length() > limit) {
                Slog.i(TAG, "buffer limit reached");
                bufferLimitReached();
            }
            subBuffer.append(cmd);
        } catch (Exception e) {
            Slog.e(TAG, "btsvc manager.putSlogs", e);
        }
    }

    public static boolean bufferLimitReached() {
        Slog.i(TAG, "In bufferLimitReached");
        mainBuffer = new String(subBuffer);
        subBuffer = new StringBuilder();
        return true;
    }

    public void readFromFile() {
        FileInputStream mainFinS = null;
        FileInputStream subFinS = null;
        Slog.d(TAG, "readFromFile start");
        this.logDir = new File(this.logDirp);
        this.mainF = new File(this.mainBfp);
        this.subF = new File(this.subBfp);
        if (!this.logDir.exists()) {
            this.logDir.mkdirs();
        }
        try {
            try {
                if (this.mainF.exists()) {
                    mainFinS = new FileInputStream(this.mainF);
                    Slog.d(TAG, "readFromFile main: " + mainFinS.available());
                    mainBuffer = new String(readFile(mainFinS));
                }
                if (this.subF.exists()) {
                    subFinS = new FileInputStream(this.subF);
                    Slog.d(TAG, "readFromFile sub: " + subFinS.available());
                    subBuffer = new StringBuilder(readFile(subFinS));
                }
                if (mainFinS != null) {
                    try {
                        mainFinS.close();
                    } catch (IOException e) {
                        return;
                    }
                }
                if (subFinS != null) {
                    subFinS.close();
                }
            } catch (IOException e2) {
                e2.printStackTrace();
                if (mainFinS != null) {
                    try {
                        mainFinS.close();
                    } catch (IOException e3) {
                        return;
                    }
                }
                if (subFinS != null) {
                    subFinS.close();
                }
            }
        } catch (Throwable th) {
            if (mainFinS != null) {
                try {
                    mainFinS.close();
                } catch (IOException e4) {
                    throw th;
                }
            }
            if (subFinS != null) {
                subFinS.close();
            }
            throw th;
        }
    }

    public String readFile(FileInputStream FinS) {
        InputStreamReader isr = new InputStreamReader(FinS);
        BufferedReader bReader = new BufferedReader(isr);
        StringBuilder sb = new StringBuilder("");
        while (true) {
            try {
                String read = bReader.readLine();
                if (read == null) {
                    break;
                }
                sb.append(read);
                sb.append("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    public void dumpInFile() {
        FileOutputStream mainFoutS = null;
        FileOutputStream subFoutS = null;
        Slog.d(TAG, "dumpInFile start");
        this.logDir = new File(this.logDirp);
        this.mainF = new File(this.mainBfp);
        this.subF = new File(this.subBfp);
        if (!this.logDir.exists()) {
            this.logDir.mkdirs();
        }
        try {
            try {
                try {
                    if (this.mainF.exists()) {
                        Slog.d(TAG, "dumpInFile delete main file");
                        this.mainF.delete();
                    }
                    if (this.subF.exists()) {
                        Slog.d(TAG, "dumpInFile delete sub file");
                        this.subF.delete();
                    }
                    if (mainBuffer.length() > 0) {
                        this.mainF.createNewFile();
                        mainFoutS = new FileOutputStream(this.mainF);
                        Slog.d(TAG, "dumpInFile write main file");
                        mainFoutS.write(mainBuffer.getBytes());
                    }
                    if (subBuffer.length() > 0) {
                        this.subF.createNewFile();
                        subFoutS = new FileOutputStream(this.subF);
                        Slog.d(TAG, "dumpInFile write sub file");
                        subFoutS.write(new String(subBuffer).getBytes());
                    }
                    Slog.d(TAG, "dumpInFile end");
                } catch (IOException e) {
                    e.printStackTrace();
                    if (mainFoutS != null) {
                        try {
                            mainFoutS.close();
                        } catch (IOException e2) {
                            return;
                        }
                    }
                    if (subFoutS != null) {
                        subFoutS.close();
                    }
                }
            } catch (StringIndexOutOfBoundsException e3) {
                e3.printStackTrace();
                if (mainFoutS != null) {
                    try {
                        mainFoutS.close();
                    } catch (IOException e4) {
                        return;
                    }
                }
                if (subFoutS != null) {
                    subFoutS.close();
                }
            }
        } finally {
            if (mainFoutS != null) {
                try {
                    mainFoutS.close();
                } catch (IOException e5) {
                }
            }
            if (subFoutS != null) {
                subFoutS.close();
            }
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:65:0x02a0  */
    /* JADX WARN: Removed duplicated region for block: B:70:0x02b3  */
    /* JADX WARN: Removed duplicated region for block: B:73:0x02c0  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public void dump(java.io.FileDescriptor r21, java.io.PrintWriter r22, java.lang.String[] r23) {
        /*
            Method dump skipped, instructions count: 713
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.BluetoothManagerService.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String getEnableDisableReasonString(int reason) {
        switch (reason) {
            case 1:
                return "APPLICATION_REQUEST";
            case 2:
                return "AIRPLANE_MODE";
            case 3:
                return "DISALLOWED";
            case 4:
                return "RESTARTED";
            case 5:
                return "START_ERROR";
            case 6:
                return "SYSTEM_BOOT";
            case 7:
                return "CRASH";
            case 8:
                return "USER_SWITCH";
            case 9:
                return "RESTORE_USER_SETTING";
            case 10:
                return "CHANGED_IT_POLICY";
            default:
                return "UNKNOWN[" + reason + HqmDBHelper.NAME_END;
        }
    }

    ProfileServiceConnections makeHeadsetProfileServiceConnection() {
        ProfileServiceConnections psc;
        synchronized (this.mProfileServices) {
            psc = this.mProfileServices.get(new Integer(1));
            if (psc == null) {
                Slog.d(TAG, "Creating new ProfileServiceConnections object for profile: 1");
                Intent intent = new Intent(IBluetoothHeadset.class.getName());
                psc = new ProfileServiceConnections(intent);
                this.mProfileServices.put(new Integer(1), psc);
            } else {
                Slog.d(TAG, "Already ProfileServiceConnections object is made for profile: 1");
            }
        }
        return psc;
    }

    void bindHeadsetProfileServiceConnection() {
        ProfileServiceConnections psc = makeHeadsetProfileServiceConnection();
        Slog.d(TAG, "bindHeadsetProfileServiceConnection");
        psc.bindService();
    }

    protected IBTVendor getSecBTHIDLServiceMockable() {
        try {
            return IBTVendor.getService();
        } catch (RemoteException e) {
            Slog.e(TAG, "Exception getting IBTVendor service: ", e);
            return null;
        }
    }

    private boolean initSecBluetoothService() {
        try {
            try {
                this.mBluetoothLock.readLock().lock();
                this.mBTvendor = getSecBTHIDLServiceMockable();
                this.mBluetoothLock.readLock().unlock();
                return true;
            } catch (Exception e) {
                Slog.e(TAG, "IBTVendor.getService exception : ", e);
                this.mBluetoothLock.readLock().unlock();
                return false;
            }
        } catch (Throwable th) {
            this.mBluetoothLock.readLock().unlock();
            throw th;
        }
    }

    public boolean writeFileService(String file_path, String cmd) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (Binder.getCallingUid() != 1000 && Binder.getCallingUid() != 1001 && Binder.getCallingUid() != 1002) {
            Slog.d(TAG, "writeFileService(): No System UID or Phone UID or Bluetooth UID");
            return false;
        } else if (!initSecBluetoothService()) {
            Slog.d(TAG, "writeFileService(): Bluetooth Vendor HIDL service failed");
            return false;
        } else {
            Slog.d(TAG, "writeFileService(): Bluetooth Vendor HIDL service starts");
            boolean is_writevendor = false;
            try {
                try {
                    this.mBluetoothLock.readLock().lock();
                    if (this.mBTvendor != null) {
                        is_writevendor = this.mBTvendor.writeOutVendorFile(file_path, cmd);
                    } else {
                        Slog.e(TAG, "writeFileService() : mBTvendor is null");
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "writeFileService() : exception = ", e);
                }
                this.mBluetoothLock.readLock().unlock();
                Slog.d(TAG, "writeFileService(): is_writevendor = " + is_writevendor);
                return is_writevendor;
            } catch (Throwable th) {
                this.mBluetoothLock.readLock().unlock();
                throw th;
            }
        }
    }

    public String getFileService(String file_path) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (Binder.getCallingUid() != 1000 && Binder.getCallingUid() != 1001 && Binder.getCallingUid() != 1002) {
            Slog.d(TAG, "getFileService(): No System UID or Phone UID or Bluetooth UID");
            return null;
        } else if (!initSecBluetoothService()) {
            Slog.d(TAG, "getFileService(): Bluetooth Vendor HIDL service failed");
            return null;
        } else {
            Slog.d(TAG, "getFileService(): Bluetooth Vendor HIDL service starts");
            final HidlSupport.Mutable<String> getfileinfo = new HidlSupport.Mutable<>();
            try {
                try {
                    this.mBluetoothLock.readLock().lock();
                    if (this.mBTvendor != null) {
                        this.mBTvendor.getVendorFileInfo(file_path, new IBTVendor.getVendorFileInfoCallback() { // from class: com.android.server.-$$Lambda$BluetoothManagerService$-oZKjd2jrowubGXH4Bn_6Aua1Jk
                            @Override // vendor.samsung.hardware.bluetooth.V1_0.IBTVendor.getVendorFileInfoCallback
                            public final void onValues(boolean z, String str) {
                                BluetoothManagerService.lambda$getFileService$0(HidlSupport.Mutable.this, z, str);
                            }
                        });
                    } else {
                        Slog.e(TAG, "getFileService() : mBTvendor is null");
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "getFileService() : exception = ", e);
                }
                this.mBluetoothLock.readLock().unlock();
                Slog.d(TAG, "getFileService(): getfileinfo.value = " + ((String) getfileinfo.value));
                return (String) getfileinfo.value;
            } catch (Throwable th) {
                this.mBluetoothLock.readLock().unlock();
                throw th;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* JADX WARN: Multi-variable type inference failed */
    public static /* synthetic */ void lambda$getFileService$0(HidlSupport.Mutable getfileinfo, boolean success, String data) {
        if (success) {
            getfileinfo.value = data;
        }
    }

    public boolean setPropertyService(String prop_name, String data) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (Binder.getCallingUid() != 1000 && Binder.getCallingUid() != 1001) {
            Slog.d(TAG, "setPropertyService(): No System UID or Phone UID");
            return false;
        } else if (!initSecBluetoothService()) {
            Slog.d(TAG, "setPropertyService(): Bluetooth Vendor HIDL service failed");
            return false;
        } else {
            Slog.d(TAG, "setPropertyService(): Bluetooth Vendor HIDL service starts");
            boolean is_writevendor = false;
            try {
                try {
                    this.mBluetoothLock.readLock().lock();
                    if (this.mBTvendor != null) {
                        is_writevendor = this.mBTvendor.setVendorProperty(prop_name, data);
                    } else {
                        Slog.e(TAG, "setPropertyService() : mBTvendor is null");
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "setPropertyService() : exception = ", e);
                }
                this.mBluetoothLock.readLock().unlock();
                Slog.d(TAG, "setPropertyService(): is_writevendor = " + is_writevendor);
                return is_writevendor;
            } catch (Throwable th) {
                this.mBluetoothLock.readLock().unlock();
                throw th;
            }
        }
    }

    public String getPropertyService(String prop_name) {
        this.mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (Binder.getCallingUid() != 1000 && Binder.getCallingUid() != 1001) {
            Slog.d(TAG, "getPropertyService(): No System UID or Phone UID");
            return null;
        } else if (!initSecBluetoothService()) {
            Slog.d(TAG, "getPropertyService(): Bluetooth Vendor HIDL service failed");
            return null;
        } else {
            Slog.d(TAG, "getPropertyService(): Bluetooth Vendor HIDL service starts");
            final HidlSupport.Mutable<String> getpropinfo = new HidlSupport.Mutable<>();
            try {
                try {
                    this.mBluetoothLock.readLock().lock();
                    if (this.mBTvendor != null) {
                        this.mBTvendor.getVendorProperty(prop_name, new IBTVendor.getVendorPropertyCallback() { // from class: com.android.server.-$$Lambda$BluetoothManagerService$7xVgbWYFxmtTxrTc6v5OSFBEabc
                            @Override // vendor.samsung.hardware.bluetooth.V1_0.IBTVendor.getVendorPropertyCallback
                            public final void onValues(boolean z, String str) {
                                BluetoothManagerService.lambda$getPropertyService$1(HidlSupport.Mutable.this, z, str);
                            }
                        });
                    } else {
                        Slog.e(TAG, "getPropertyService() : mBTvendor is null");
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "getPropertyService() : exception = ", e);
                }
                this.mBluetoothLock.readLock().unlock();
                Slog.d(TAG, "getPropertyService(): getpropinfo.value = " + ((String) getpropinfo.value));
                return (String) getpropinfo.value;
            } catch (Throwable th) {
                this.mBluetoothLock.readLock().unlock();
                throw th;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* JADX WARN: Multi-variable type inference failed */
    public static /* synthetic */ void lambda$getPropertyService$1(HidlSupport.Mutable getpropinfo, boolean success, String data) {
        if (success) {
            getpropinfo.value = data;
        }
    }

    public int[] getBleAppCounts() {
        return new int[]{getBleAppCount(), getSamsungBleAppCount()};
    }
}