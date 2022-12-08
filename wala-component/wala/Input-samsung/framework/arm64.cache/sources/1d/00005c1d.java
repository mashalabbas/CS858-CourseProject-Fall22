package com.android.server.power;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.SynchronousUserSwitchObserver;
import android.app.trust.TrustManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfoProto;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.SensorManager;
import android.hardware.SystemSensorManager;
import android.hardware.display.DisplayManagerInternal;
import android.hardware.scontext.SContext;
import android.hardware.scontext.SContextEvent;
import android.hardware.scontext.SContextListener;
import android.hardware.scontext.SContextManager;
import android.hardware.scontext.SContextWirelessChargingDetection;
import android.icu.text.PluralRules;
import android.metrics.LogMaker;
import android.net.Uri;
import android.os.BatteryManagerInternal;
import android.os.Binder;
import android.os.Bundle;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.PowerSaveState;
import android.os.Process;
import android.os.PsProto;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.SemHqmManager;
import android.os.ShellCallback;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.SystemPropertiesProto;
import android.os.SystemProto;
import android.os.Trace;
import android.os.UidProto;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.provider.Settings;
import android.security.keymaster.KeymasterDefs;
import android.service.dreams.DreamManagerInternal;
import android.service.vr.IVrManager;
import android.service.vr.IVrStateCallbacks;
import android.util.EventLog;
import android.util.KeyValueListParser;
import android.util.PrintWriterPrinter;
import android.util.SparseArray;
import android.util.TimeUtils;
import android.util.TimedRemoteCaller;
import android.util.proto.ProtoOutputStream;
import android.view.Display;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.IAppOpsService;
import com.android.internal.app.IBatteryStats;
import com.android.internal.hardware.AmbientDisplayConfiguration;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.RingBuffer;
import com.android.internal.widget.LockPatternUtils;
import com.android.server.EventLogTags;
import com.android.server.LocalServices;
import com.android.server.LockGuard;
import com.android.server.RescueParty;
import com.android.server.ServiceThread;
import com.android.server.SystemService;
import com.android.server.UiThread;
import com.android.server.Watchdog;
import com.android.server.am.BatteryStatsService;
import com.android.server.lights.Light;
import com.android.server.lights.LightsManager;
import com.android.server.policy.WindowManagerPolicy;
import com.android.server.power.PowerManagerUtil;
import com.android.server.power.batterysaver.BatterySaverController;
import com.android.server.power.batterysaver.BatterySaverStateMachine;
import com.android.server.power.batterysaver.BatterySavingStats;
import com.android.server.utils.PriorityDump;
import com.android.server.wifi.util.TelephonyUtil;
import com.samsung.android.cover.CoverManager;
import com.samsung.android.cover.CoverState;
import com.samsung.android.desktopmode.SemDesktopModeManager;
import com.samsung.android.desktopmode.SemDesktopModeState;
import com.samsung.android.edge.EdgeManagerInternal;
import com.samsung.android.hqm.HqmDBHelper;
import com.samsung.android.knox.SemPersonaManager;
import com.samsung.android.knox.custom.SettingsManager;
import com.samsung.android.os.SemPerfManager;
import com.samsung.android.smartface.SmartFaceManager;
import com.samsung.android.vr.IGearVrManagerService;
import com.samsung.android.vr.IGearVrStateCallbacks;
import com.sec.ims.settings.GlobalSettings;
import com.sec.tima_keychain.TimaKeychain;
import gov.nist.core.Separators;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
public final class PowerManagerService extends SystemService implements Watchdog.Monitor {
    private static final String ACTION_BROADCAST_BEFORE_DIM = "com.android.server.PowerManagerService.action.FAILED_TO_DETECT_FACE_BEFORE_DIM";
    private static final String ACTION_DISPLAY_ON_TIME = "com.sec.android.app.server.power.DISPLAY_ON_TIME";
    static final String ACTION_FTA_OFF = "com.sec.factory.app.factorytest.FTA_OFF";
    static final String ACTION_FTA_ON = "com.sec.factory.app.factorytest.FTA_ON";
    static final String ACTION_GOOGLE_CAST_CONNECTED = "com.samsung.intent.action.GOOGLE_CAST_MIRRORING_CONNECTION_CHANGED";
    private static final String ACTION_INTERNAL_DISPLAY_OFF_BY_POWERKEY = "com.samsung.android.server.power.ACTION_INTERNAL_DISPLAY_OFF_BY_POWERKEY";
    private static final String ACTION_KNOX_LOCK_TIMEOUT = "com.sec.knox.container.INTENT_ACTION_LOCK_TIMEOUT";
    private static final String ACTION_PMS_LOGGING = "com.sec.android.app.server.power.LOGGING";
    static final String ACTION_WIFI_DISPLAY_CONNECTED = "com.samsung.intent.action.WIFI_DISPLAY_SOURCE_STATE";
    private static final long COVER_AUTH_READY_TIME_SINCE_BOOT_MS = 15000;
    public static final String COVER_OPEN = "com.samsung.cover.OPEN";
    private static final String COVER_SYSFS = "/sys/class/sec/sec_key/hall_detect";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_SPEW = false;
    private static final boolean DEBUG_VR = false;
    private static final int DEFAULT_DOUBLE_TAP_TO_WAKE = 0;
    private static final int DEFAULT_KEYBOARD_OFF_TIMEOUT = 3000;
    private static final int DEFAULT_KEY_NIGHT_MODE = 0;
    private static final int DEFAULT_SCREEN_OFF_TIMEOUT = 15000;
    private static final String DEFAULT_SCREEN_TIMEOUT_VALUE = "600000";
    private static final int DEFAULT_SLEEP_TIMEOUT = -1;
    private static final int DIRTY_ACTUAL_DISPLAY_POWER_STATE_UPDATED = 8;
    private static final int DIRTY_BATTERY_STATE = 256;
    private static final int DIRTY_BOOT_COMPLETED = 16;
    private static final int DIRTY_BUTTON_EVENT = 65536;
    private static final int DIRTY_DOCK_STATE = 1024;
    private static final int DIRTY_IS_POWERED = 64;
    private static final int DIRTY_KEY_LIGHT_ONOFF_BY_FOLDER = 262144;
    private static final int DIRTY_KEY_LIGHT_ONOFF_BY_SCREEN = 131072;
    private static final int DIRTY_PROXIMITY_POSITIVE = 512;
    private static final int DIRTY_QUIESCENT = 4096;
    private static final int DIRTY_SCREEN_BRIGHTNESS_BOOST = 2048;
    private static final int DIRTY_SETTINGS = 32;
    private static final int DIRTY_STAY_ON = 128;
    private static final int DIRTY_TOUCHKEY_EVENT = 16384;
    private static final int DIRTY_TOUCHSCREEN_EVENT = 32768;
    private static final int DIRTY_USER_ACTIVITY = 4;
    private static final int DIRTY_VR_MODE_CHANGED = 8192;
    private static final int DIRTY_WAKEFULNESS = 2;
    private static final int DIRTY_WAKE_LOCKS = 1;
    private static final int DISPLAY_ON_TIME_SEND_EVERYMONTH = 2592000;
    private static final String DISPLAY_POWERMANAGERSERVICE_LCD_DURATION = "DPLD";
    private static final String DISPLAY_POWERMANAGERSERVICE_SETTING_INFO = "DPSI";
    private static final String DISPLAY_POWERMANAGERSERVICE_USER_CHANGE = "DPUC";
    private static final int DPUI_LUBS_HW_PARAMETER_BIGDATA_DELAY = 10000;
    private static final String EXTRA_DISPLAY_ON_TIME = "display_on_time";
    private static final String FEATURE_COVER_FLIP = "com.sec.feature.cover.flip";
    private static final String FEATURE_COVER_SVIEW = "com.sec.feature.cover.sview";
    private static final int HALT_MODE_REBOOT = 1;
    private static final int HALT_MODE_REBOOT_SAFE_MODE = 2;
    private static final int HALT_MODE_SHUTDOWN = 0;
    public static final String IS_VISIBLE_WINDOW = "AxT9IME.isVisibleWindow";
    private static final String LAST_REBOOT_PROPERTY = "persist.sys.boot.reason";
    private static final int LCD_ON_DURATION_UPDATE_EVERYDAY = 86400000;
    static final long MIN_LONG_WAKE_CHECK_INTERVAL = 60000;
    private static final int MSG_CHECK_FOR_LONG_WAKELOCKS = 4;
    private static final int MSG_KEY_LIGHT_ONOFF_BY_SCREEN = 5;
    private static final int MSG_KNOX_LOCK_TIMEOUT = 2;
    private static final int MSG_KNOX_RESET_LOCK_TIMEOUT = 1;
    private static final int MSG_PRE_SMART_STAY = 2;
    private static final int MSG_SANDMAN = 2;
    private static final int MSG_SCREEN_BRIGHTNESS_BOOST_TIMEOUT = 3;
    private static final int MSG_SEND_DPUI_LCBS_HW_PARAMETER_BIGDATA = 8;
    private static final int MSG_SEND_DPUI_LD_HW_PARAMETER_BIGDATA = 7;
    private static final int MSG_SEND_DPUI_LUBS_HW_PARAMETER_BIGDATA = 6;
    private static final int MSG_SMART_STAY = 1;
    private static final int MSG_USER_ACTIVITY_TIMEOUT = 1;
    private static final long NOTIFICATION_SCREEN_OFF_TIME = 8000;
    private static final long NOTIFICATION_SCREEN_OFF_TIME_DEFAULT = -1;
    private static final String OUTDOOR_MODE_SETTING = "display_outdoor_mode";
    private static final int OUTDOOR_MODE_TIMEOUT = 900000;
    private static final String PATH_AFC_DISPLAY_ON_TIME = "/efs/afc/display_on_time";
    private static final String PMS_DB_BRIGHTNESS_ENABLE = "pms_notification_panel_brightness_adjustment";
    private static final int POWER_FEATURE_DOUBLE_TAP_TO_WAKE = 1;
    private static final String REASON_BATTERY_THERMAL_STATE = "shutdown,thermal,battery";
    private static final String REASON_LOW_BATTERY = "shutdown,battery";
    private static final String REASON_REBOOT = "reboot";
    private static final String REASON_REBOOT_BY_USER = "reboot,userrequested";
    private static final String REASON_SHUTDOWN = "shutdown";
    private static final String REASON_THERMAL_SHUTDOWN = "shutdown,thermal";
    private static final String REASON_USERREQUESTED = "shutdown,userrequested";
    public static final String RESPONSE_AXT9INFO = "ResponseAxT9Info";
    private static final int SCREEN_BRIGHTNESS_BOOST_TIMEOUT = 5000;
    private static final int SCREEN_ON_LATENCY_WARNING_MS = 200;
    private static final String SETTINGS_AOD_MODE = "aod_mode";
    private static final int SET_WAKELOCK_FAIL = 0;
    private static final int SET_WAKELOCK_SUCCESS = 1;
    private static final String SYSTEM_PROPERTY_QUIESCENT = "ro.boot.quiescent";
    private static final String SYSTEM_PROPERTY_RETAIL_DEMO_ENABLED = "sys.retaildemo.enabled";
    private static final String TAG = "PowerManagerService";
    private static final String TAG_INPUT_DEVICE_LIGHT = "[input device light] ";
    public static final String TAG_LOG_TO_KERNEL = "!@";
    private static final String TAG_PRINT_WAKE_LOCK = "[PWL] ";
    private static final String TIMEOUT_DEX = "timeout_dex";
    private static final String TRACE_SCREEN_ON = "Screen turning on";
    private static final int USER_ACTIVITY_SCREEN_BRIGHT = 1;
    private static final int USER_ACTIVITY_SCREEN_DIM = 2;
    private static final int USER_ACTIVITY_SCREEN_DREAM = 4;
    private static final int USER_ACTIVITY_SCREEN_EXTERNAL_ONLY = 16;
    private static final int WAKE_LOCK_BUTTON_BRIGHT = 8;
    private static final int WAKE_LOCK_CPU = 1;
    private static final int WAKE_LOCK_DOZE = 64;
    private static final int WAKE_LOCK_DRAW = 128;
    private static final int WAKE_LOCK_PROXIMITY_SCREEN_OFF = 16;
    private static final int WAKE_LOCK_SCREEN_BRIGHT = 2;
    private static final int WAKE_LOCK_SCREEN_DIM = 4;
    private static final int WAKE_LOCK_SCREEN_EXTERNAL = 1024;
    private static final int WAKE_LOCK_STAY_AWAKE = 32;
    private static final int WORKSOURCE_WAKELOCK = 2;
    private static boolean sQuiescent;
    private final int COUNT_FOR_LOGGING_FREQUENT_EVENT;
    private final int COUNT_TO_RESET_FOR_LOGGING_FREQUENT_EVENT;
    private final boolean USE_PRE_SMART_STAY;
    private final boolean USE_SMART_STAY;
    boolean isActive;
    private boolean mAbuseWakeLockDisabled;
    private final ArrayList<WakeLock> mAbuseWakeLocks;
    private boolean mAdaptiveBrightnessCameraModeSetting;
    private AlarmManager mAlarmManager;
    private boolean mAllBrightWakeLockAbuse;
    private int mAlpmHlpmMode;
    private boolean mAlwaysOnEnabled;
    private final AmbientDisplayConfiguration mAmbientDisplayConfiguration;
    private boolean mAodMode;
    private IAppOpsService mAppOps;
    private Light mAttentionLight;
    private int mAutoBrightnessLowerLimit;
    private int mAutoBrightnessUpperLimit;
    private int mBatteryLevel;
    private boolean mBatteryLevelCritical;
    private boolean mBatteryLevelLow;
    private int mBatteryLevelWhenDreamStarted;
    private BatteryManagerInternal mBatteryManagerInternal;
    private final BatterySaverController mBatterySaverController;
    private final BatterySaverPolicy mBatterySaverPolicy;
    private final BatterySaverStateMachine mBatterySaverStateMachine;
    private final BatterySavingStats mBatterySavingStats;
    private IBatteryStats mBatteryStats;
    private boolean mBootCompleted;
    private Runnable[] mBootCompletedRunnables;
    private int mClearViewBrightnessModeCount;
    private boolean mClearViewBrightnessModeEnabled;
    private boolean mColorFadeExit;
    private int mColorWeaknessModeCount;
    private boolean mColorWeaknessModeEnabled;
    final Constants mConstants;
    private final Context mContext;
    private boolean mCoverAuthReady;
    private Runnable mCoverAuthReadyRunnable;
    private CoverManager mCoverManager;
    private CoverManager.CoverStateListener mCoverStateListener;
    private int mCoverType;
    private long[] mDPSIDuration;
    private boolean mDecoupleHalAutoSuspendModeFromDisplayConfig;
    private boolean mDecoupleHalInteractiveModeFromDisplayConfig;
    private long mDelayTimePrintWakeLock;
    private boolean mDeviceIdleMode;
    int[] mDeviceIdleTempWhitelist;
    int[] mDeviceIdleWhitelist;
    private DexScreenOffTimeoutObserver mDexScreenOffTimeoutObserver;
    private int mDirty;
    private DisplayManagerInternal mDisplayManagerInternal;
    private int mDisplayOnTime;
    private final DisplayManagerInternal.DisplayPowerCallbacks mDisplayPowerCallbacks;
    private final DisplayManagerInternal.DisplayPowerRequest mDisplayPowerRequest;
    private boolean mDisplayReady;
    private final SuspendBlocker mDisplaySuspendBlocker;
    private int mDockState;
    private boolean mDoubleTapWakeEnabled;
    private boolean mDozeAfterScreenOff;
    private int mDozeScreenBrightnessOverrideFromDreamManager;
    private int mDozeScreenStateOverrideFromDreamManager;
    private boolean mDrawWakeLockOverrideFromSidekick;
    private DreamManagerInternal mDreamManager;
    private boolean mDreamsActivateOnDockSetting;
    private boolean mDreamsActivateOnSleepSetting;
    private boolean mDreamsActivatedOnDockByDefaultConfig;
    private boolean mDreamsActivatedOnSleepByDefaultConfig;
    private int mDreamsBatteryLevelDrainCutoffConfig;
    private int mDreamsBatteryLevelMinimumWhenNotPoweredConfig;
    private int mDreamsBatteryLevelMinimumWhenPoweredConfig;
    private boolean mDreamsEnabledByDefaultConfig;
    private boolean mDreamsEnabledOnBatteryConfig;
    private boolean mDreamsEnabledSetting;
    private boolean mDreamsSupportedConfig;
    private int mDualScreenPolicy;
    private boolean mEarlyWakeUp;
    private EdgeManagerInternal mEdgeInternal;
    private boolean mFTAMode;
    private boolean mFaceDetected;
    private Intent mFaceDetectedFailIntent;
    private boolean mFeatureCoverSysfs;
    private boolean mForceSlowChange;
    private boolean mForcedDisplayOff;
    private int mForegroundProfile;
    private boolean mFreezingScreenBrightness;
    private boolean mGameAutobrightnessLocked;
    private final IGearVrStateCallbacks mGearVrStateCallbacks;
    private boolean mGoToSleepPreventionEnabled;
    private boolean mHalAutoSuspendModeEnabled;
    private boolean mHalInteractiveModeEnabled;
    private final PowerManagerHandler mHandler;
    private Handler mHandlerPmsMisc;
    private Handler mHandlerSmartStay;
    private final ServiceThread mHandlerThread;
    private HandlerThread mHandlerThreadPmsMisc;
    private HandlerThread mHandlerThreadSmartStay;
    private boolean mHoldingDisplaySuspendBlocker;
    private boolean mHoldingWakeLockSuspendBlocker;
    private boolean mInputDeviceEnabled;
    private Intent mInternalDisplayOffByPowerKeyIntent;
    private int mInternalWakefulnessForDex;
    private final boolean mIsBadCurrentConsumptionDevice;
    public boolean mIsCoverClosed;
    boolean mIsDeviceMoving;
    private boolean mIsDualScreenFolder;
    private boolean mIsDualViewMode;
    private boolean mIsDualViewModeChanging;
    private boolean mIsFolderType;
    private boolean mIsGearVrDocked;
    private boolean mIsGearVrModeEnabled;
    private boolean mIsGearVrPersistentVrModeEnabled;
    private boolean mIsGearVrTetheredDocked;
    private boolean mIsKeyboardVisible;
    public boolean mIsLidClosed;
    private boolean mIsPowered;
    public boolean mIsSipVisible;
    private boolean mIsSupportedLightSensor;
    private boolean mIsVrModeEnabled;
    boolean mIsWirelessChargerSContextEnabled;
    boolean mIsWirelessChargerSContextRegistered;
    private int mKeyLedOffNightModeSetting;
    private InputDeviceLightState mKeyboardLightState;
    private int mKeyboardOffTimeoutSetting;
    private KeyguardManager mKeyguardManager;
    private final Handler mKnoxHandler;
    private boolean mLCDFlashMode;
    private LCDFlashModeLock mLCDFlashModeLock;
    private long mLastAutoBrightnessLimitTime;
    private boolean mLastBrightnessOverrideState;
    private int mLastGoToSleepReason;
    private long mLastInteractivePowerHintTime;
    private long mLastOutdoorModeEnblaedTime;
    private long mLastScreenBrightnessBoostTime;
    private long mLastScreenOffEventElapsedRealTime;
    private int mLastScreenTimeout;
    private long mLastScreenWakeTime;
    private long mLastSleepTime;
    private int mLastUserActivitySummary;
    private long mLastUserActivityTime;
    private long mLastUserActivityTimeForDex;
    private long mLastUserActivityTimeNoChangeLights;
    private long mLastUserSetScreenBrightnessTime;
    private long mLastWakeTime;
    private int mLastWakeUpReason;
    private long mLastWarningAboutUserActivityPermission;
    private boolean mLightDeviceIdleMode;
    private LightsManager mLightsManager;
    private final Object mLock;
    private long mMaximumScreenDimDurationConfig;
    private float mMaximumScreenDimRatioConfig;
    private long mMaximumScreenOffTimeoutFromDeviceAdmin;
    private long mMinimumScreenOffTimeoutConfig;
    private int mNativeUserActivityCount;
    private boolean mNeedCheckAbuseWakelocks;
    private long mNextTimeoutForPreSmartStay;
    private long mNextTimeoutForSmartStay;
    private Intent mNoUserActivityIntent;
    private Intent mNoUserActivityIntentLegacy;
    private boolean mNoUserActivitySent;
    private long mNotificationLightScreenOverride;
    private Notifier mNotifier;
    private long mNotifyLongDispatched;
    private long mNotifyLongNextCheck;
    private long mNotifyLongScheduled;
    private boolean mOutdoorModeSetting;
    private long mOverriddenTimeout;
    private long mPassedWakeupTime;
    private boolean mPendingMessagePreSmartStay;
    private boolean mPendingMessageSmartStay;
    private boolean mPendingScreenStateChangeStartedByProximity;
    private int mPlugType;
    private WindowManagerPolicy mPolicy;
    private final int mPreSmartStayDelay;
    private String mPrevReleasedWakeLock;
    private boolean mPrevTouchKeyForceDisable;
    private final Runnable mPrintWakeLockAperiodicallyRunnable;
    private final SparseArray<ProfilePowerState> mProfilePowerState;
    private boolean mProximityPositive;
    private boolean mRequestWaitForNegativeProximity;
    private boolean mSandmanScheduled;
    private boolean mSandmanSummoned;
    private ScaleFactorLock mScaleFactorLock;
    private boolean mScreenBrightnessBoostInProgress;
    private int mScreenBrightnessModeSetting;
    private int mScreenBrightnessOverrideFromWindowManager;
    private float mScreenBrightnessScaleFactor;
    private int mScreenBrightnessSetting;
    private int mScreenBrightnessSettingDefault;
    private int mScreenBrightnessSettingMaximum;
    private int mScreenBrightnessSettingMinimum;
    private long mScreenDimDurationOverrideFromWindowManager;
    private int mScreenExtendedBrightnessMaximum;
    private String mScreenOffReason;
    private int mScreenOffTimeoutForDex;
    private long mScreenOffTimeoutSetting;
    private int mScreenOnOffCount;
    private String mScreenOnReason;
    private SemHqmManager mSemHqmManager;
    private boolean mSentAllDisplayOnTimeIntent;
    private int[] mSentDisplayOnTimeIntent;
    private SettingsObserver mSettingsObserver;
    private String mShutdownOrRebootCaller;
    private BroadcastReceiver mSipIntentReceiver;
    private long mSleepTimeoutSetting;
    private SmartFaceManager mSmartFaceManager;
    private final int mSmartStayDelay;
    private boolean mSmartStayEnabledSetting;
    private boolean mSmartStayProgress;
    private boolean mSmartViewConnected;
    private boolean mStayOn;
    private int mStayOnWhilePluggedInSetting;
    private boolean mSupportsDoubleTapWakeConfig;
    private final ArrayList<SuspendBlocker> mSuspendBlockers;
    private boolean mSuspendWhenScreenOffDueToProximityConfig;
    private boolean mSystemReady;
    private boolean mTheaterModeEnabled;
    private PowerManagerUtil.TimeMeasurement mTimeMeasurement;
    private boolean mTouchKeyForceDisable;
    private boolean mTouchKeyForceDisableOverrideFromSystemPowerSaveMode;
    private long mTouchKeyOffTimeoutOverrideFromWindowManager;
    private int mTouchKeyOffTimeoutSetting;
    private InputDeviceLightState mTouchkeyLightState;
    private TrustManager mTrustManager;
    private final SparseArray<UidState> mUidState;
    private boolean mUidsChanged;
    private boolean mUidsChanging;
    private Intent mUserActivityIntent;
    private Intent mUserActivityIntentLegacy;
    private int mUserActivitySummary;
    private final UserActivityTask mUserActivityTask;
    private long mUserActivityTimeoutForDexOverrideFromWindowManager;
    private long mUserActivityTimeoutOverrideFromWindowManager;
    private int mUserActivityTimeoutSetting;
    private final UserActivityTimeoutTask mUserActivityTimeoutTask;
    private boolean mUserInactiveOverrideFromWindowManager;
    private final IVrStateCallbacks mVrStateCallbacks;
    private int mWakeLockSummary;
    private final SuspendBlocker mWakeLockSuspendBlocker;
    private final ArrayList<WakeLock> mWakeLocks;
    private boolean mWakeUpEvenThoughProximityPositive;
    private boolean mWakeUpPreventionDisabled;
    private boolean mWakeUpWhenPluggedOrUnpluggedConfig;
    private boolean mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig;
    private int mWakefulness;
    private boolean mWakefulnessChanging;
    private ArrayList<Integer> mWakelockBlacklistAppid;
    private final int mWhenCheckSmartStay;
    private WirelessChargerDetector mWirelessChargerDetector;
    private SContextListener mWirelessChargerSContextListener;
    SContextManager mWirelessChargerSContextManager;
    private long mlastSetCoverTypeTime;
    private String mlastUpdateCoverStateReason;
    private long mlastUpdateCoverStateTime;
    private String mlastUpdateCoverTypeReason;
    private static final boolean SEC_USE_FACTORY_BINARY = FactoryTest.isFactoryBinary();
    private static final Uri DEX_SETTINGS_URI = Uri.parse("content://com.sec.android.desktopmode.uiservice.SettingsProvider/settings");
    private static final String[] LUBS_KEYS = {"LUBS_B", "LUBS_L", "LUBS_M", "LUBS_P"};
    private static final String[] LD_KEYS = {"LBLD", "LBMD", "LBHD", "LOD", "LOCNT"};
    private static final String[] LCBS_KEYS = {"MODE", "LEVEL"};
    private static RingBuffer<WakeUpHistory> mWakeUpHistory = new RingBuffer<>(WakeUpHistory.class, 50);

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public @interface HaltMode {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativeAcquireSuspendBlocker(String str);

    private native void nativeInit();

    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativeReleaseSuspendBlocker(String str);

    private static native void nativeSendPowerHint(int i, int i2);

    private static native void nativeSetAutoSuspend(boolean z);

    private static native void nativeSetFeature(int i, int i2);

    private static native void nativeSetInteractive(boolean z);

    private static native void nativeSetInteractiveForDualScreen(boolean z, int i);

    static /* synthetic */ int access$13812(PowerManagerService x0, int x1) {
        int i = x0.mClearViewBrightnessModeCount + x1;
        x0.mClearViewBrightnessModeCount = i;
        return i;
    }

    static /* synthetic */ int access$13820(PowerManagerService x0, int x1) {
        int i = x0.mClearViewBrightnessModeCount - x1;
        x0.mClearViewBrightnessModeCount = i;
        return i;
    }

    static /* synthetic */ long access$17914(PowerManagerService x0, long x1) {
        long j = x0.mDelayTimePrintWakeLock + x1;
        x0.mDelayTimePrintWakeLock = j;
        return j;
    }

    static /* synthetic */ int access$2176(PowerManagerService x0, int x1) {
        int i = x0.mDirty | x1;
        x0.mDirty = i;
        return i;
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class ForegroundProfileObserver extends SynchronousUserSwitchObserver {
        private ForegroundProfileObserver() {
        }

        @Override // android.app.SynchronousUserSwitchObserver
        public void onUserSwitching(int newUserId) throws RemoteException {
        }

        @Override // android.app.UserSwitchObserver, android.app.IUserSwitchObserver
        public void onForegroundProfileSwitch(int newProfileId) throws RemoteException {
            Slog.d(PowerManagerService.TAG, "[api] onForegroundProfileSwitch: " + newProfileId);
            long now = SystemClock.uptimeMillis();
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService.this.mForegroundProfile = newProfileId;
                PowerManagerService.this.maybeUpdateForegroundProfileLastActivityLocked(now);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public static final class ProfilePowerState {
        long mLastUserActivityTime;
        boolean mLockingNotified;
        long mScreenOffTimeout;
        long mScreenOffTimeoutForUser;
        final int mUserId;
        int mWakeLockSummary;

        public ProfilePowerState(int userId, long screenOffTimeout) {
            Slog.d(PowerManagerService.TAG, "new ProfilePowerState: " + userId + ", " + screenOffTimeout);
            this.mUserId = userId;
            this.mScreenOffTimeout = screenOffTimeout;
            this.mLastUserActivityTime = SystemClock.uptimeMillis();
        }

        public ProfilePowerState(int userId, long screenOffTimeout, boolean calledFromKnox) {
            Slog.d(PowerManagerService.TAG, "new ProfilePowerState: " + userId + ", " + screenOffTimeout);
            this.mUserId = userId;
            this.mScreenOffTimeout = calledFromKnox ? Long.MAX_VALUE : screenOffTimeout;
            this.mScreenOffTimeoutForUser = calledFromKnox ? screenOffTimeout : Long.MAX_VALUE;
            this.mLastUserActivityTime = SystemClock.uptimeMillis();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public final class Constants extends ContentObserver {
        private static final boolean DEFAULT_NO_CACHED_WAKE_LOCKS = true;
        private static final String KEY_NO_CACHED_WAKE_LOCKS = "no_cached_wake_locks";
        public boolean NO_CACHED_WAKE_LOCKS;
        private final KeyValueListParser mParser;
        private ContentResolver mResolver;

        public Constants(Handler handler) {
            super(handler);
            this.NO_CACHED_WAKE_LOCKS = true;
            this.mParser = new KeyValueListParser(',');
        }

        public void start(ContentResolver resolver) {
            this.mResolver = resolver;
            this.mResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.POWER_MANAGER_CONSTANTS), false, this);
            updateConstants();
        }

        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange, Uri uri) {
            updateConstants();
        }

        private void updateConstants() {
            synchronized (PowerManagerService.this.mLock) {
                try {
                    this.mParser.setString(Settings.Global.getString(this.mResolver, Settings.Global.POWER_MANAGER_CONSTANTS));
                } catch (IllegalArgumentException e) {
                    Slog.e(PowerManagerService.TAG, "Bad alarm manager settings", e);
                }
                this.NO_CACHED_WAKE_LOCKS = this.mParser.getBoolean(KEY_NO_CACHED_WAKE_LOCKS, true);
                Slog.d(PowerManagerService.TAG, "[api] updateConstants: NO_CACHED_WAKE_LOCKS: " + this.NO_CACHED_WAKE_LOCKS);
            }
        }

        void dump(PrintWriter pw) {
            pw.println("  Settings power_manager_constants:");
            pw.print("    ");
            pw.print(KEY_NO_CACHED_WAKE_LOCKS);
            pw.print(Separators.EQUALS);
            pw.println(this.NO_CACHED_WAKE_LOCKS);
        }

        void dumpProto(ProtoOutputStream proto) {
            long constantsToken = proto.start(1146756268033L);
            proto.write(1133871366145L, this.NO_CACHED_WAKE_LOCKS);
            proto.end(constantsToken);
        }
    }

    public PowerManagerService(Context context) {
        super(context);
        this.mLock = LockGuard.installNewLock(1);
        this.mSuspendBlockers = new ArrayList<>();
        this.mWakeLocks = new ArrayList<>();
        this.mAbuseWakeLocks = new ArrayList<>();
        this.mNeedCheckAbuseWakelocks = false;
        this.mAbuseWakeLockDisabled = false;
        this.mAllBrightWakeLockAbuse = false;
        this.mAutoBrightnessLowerLimit = -1;
        this.mAutoBrightnessUpperLimit = -1;
        this.mDisplayPowerRequest = new DisplayManagerInternal.DisplayPowerRequest();
        this.mDockState = 0;
        this.mFTAMode = false;
        this.mMaximumScreenOffTimeoutFromDeviceAdmin = Long.MAX_VALUE;
        this.mScreenBrightnessOverrideFromWindowManager = -1;
        this.mOverriddenTimeout = -1L;
        this.mUserActivityTimeoutOverrideFromWindowManager = -1L;
        this.mNotificationLightScreenOverride = -1L;
        this.mUserActivityTimeoutForDexOverrideFromWindowManager = -1L;
        this.mDozeScreenStateOverrideFromDreamManager = 0;
        this.mDozeScreenBrightnessOverrideFromDreamManager = -1;
        this.mLastWarningAboutUserActivityPermission = Long.MIN_VALUE;
        this.mDeviceIdleWhitelist = new int[0];
        this.mDeviceIdleTempWhitelist = new int[0];
        this.mUidState = new SparseArray<>();
        this.mIsFolderType = false;
        this.mIsLidClosed = false;
        this.mIsDualScreenFolder = false;
        this.mWakeUpEvenThoughProximityPositive = false;
        this.mAlpmHlpmMode = -1;
        this.mForcedDisplayOff = false;
        this.mUserActivityTimeoutSetting = 0;
        this.mUserActivityTask = new UserActivityTask();
        this.mUserActivityTimeoutTask = new UserActivityTimeoutTask();
        this.mInternalWakefulnessForDex = 1;
        this.mIsDualViewModeChanging = false;
        this.mScreenOffTimeoutForDex = 600000;
        this.mSmartFaceManager = null;
        this.USE_SMART_STAY = true;
        this.USE_PRE_SMART_STAY = isTablet();
        this.mSmartStayDelay = EventLogTags.NOTIFICATION_ENQUEUE;
        this.mPreSmartStayDelay = 6000;
        this.mIsBadCurrentConsumptionDevice = true;
        this.mWhenCheckSmartStay = 1;
        this.mSmartStayEnabledSetting = false;
        this.mPendingMessageSmartStay = false;
        this.mPendingMessagePreSmartStay = false;
        this.mFaceDetected = false;
        this.mNextTimeoutForSmartStay = -1L;
        this.mNextTimeoutForPreSmartStay = -1L;
        this.mSmartStayProgress = false;
        this.mShutdownOrRebootCaller = "";
        this.mLastUserActivitySummary = -1;
        this.mLastScreenTimeout = -1;
        this.mIsSupportedLightSensor = true;
        this.mIsCoverClosed = false;
        this.mlastUpdateCoverStateReason = "";
        this.mlastUpdateCoverStateTime = -1L;
        this.mlastUpdateCoverTypeReason = "";
        this.mlastSetCoverTypeTime = -1L;
        this.mFeatureCoverSysfs = true;
        this.mCoverManager = null;
        this.mCoverType = 2;
        this.mEdgeInternal = null;
        this.mWirelessChargerSContextManager = null;
        this.mIsWirelessChargerSContextEnabled = false;
        this.mIsWirelessChargerSContextRegistered = false;
        this.mIsDeviceMoving = true;
        this.mPassedWakeupTime = 0L;
        this.mLCDFlashModeLock = null;
        this.mOutdoorModeSetting = false;
        this.mClearViewBrightnessModeCount = 0;
        this.mClearViewBrightnessModeEnabled = false;
        this.mColorWeaknessModeCount = 0;
        this.mColorWeaknessModeEnabled = false;
        this.mScreenBrightnessScaleFactor = -1.0f;
        this.mFreezingScreenBrightness = false;
        this.mAlarmManager = null;
        this.mSemHqmManager = null;
        this.mDPSIDuration = new long[5];
        this.mSentDisplayOnTimeIntent = new int[4];
        this.mSentAllDisplayOnTimeIntent = false;
        this.mDisplayOnTime = -1;
        this.mDelayTimePrintWakeLock = -1L;
        this.mScreenOffReason = "";
        this.mScreenOnReason = "";
        this.mPrevReleasedWakeLock = "";
        this.mScreenOnOffCount = 0;
        this.COUNT_FOR_LOGGING_FREQUENT_EVENT = 10;
        this.COUNT_TO_RESET_FOR_LOGGING_FREQUENT_EVENT = 5000;
        this.mNativeUserActivityCount = 0;
        this.mSmartViewConnected = false;
        this.mDualScreenPolicy = -1;
        this.mKeyboardLightState = null;
        this.mTouchkeyLightState = null;
        this.mTouchKeyOffTimeoutOverrideFromWindowManager = -1L;
        this.mKeyboardOffTimeoutSetting = 2000;
        this.mColorFadeExit = true;
        this.mPrevTouchKeyForceDisable = false;
        this.mInputDeviceEnabled = true;
        this.mTouchKeyForceDisable = false;
        this.mTouchKeyForceDisableOverrideFromSystemPowerSaveMode = false;
        this.mIsSipVisible = false;
        this.mScreenDimDurationOverrideFromWindowManager = -1L;
        this.mWakelockBlacklistAppid = new ArrayList<>();
        this.mLastBrightnessOverrideState = false;
        this.mKnoxHandler = new KnoxTimeoutHandler();
        this.isActive = false;
        this.mProfilePowerState = new SparseArray<>();
        this.mLastWakeUpReason = 0;
        this.mDisplayPowerCallbacks = new DisplayManagerInternal.DisplayPowerCallbacks() { // from class: com.android.server.power.PowerManagerService.9
            private int mDisplayState = 0;

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onStateChanged() {
                synchronized (PowerManagerService.this.mLock) {
                    Slog.d(PowerManagerService.TAG, "DisplayPowerCallbacks : onStateChanged()");
                    PowerManagerService.access$2176(PowerManagerService.this, 8);
                    PowerManagerService.this.updatePowerStateLocked();
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onProximityPositive() {
                synchronized (PowerManagerService.this.mLock) {
                    if (PowerManagerService.this.mProximityPositive) {
                        return;
                    }
                    Slog.d(PowerManagerService.TAG, "DisplayPowerCallbacks : onProximityPositive()");
                    PowerManagerService.this.mNotifier.onScreenStateChangeStartedByProximity(true);
                    PowerManagerService.this.mProximityPositive = true;
                    PowerManagerService.access$2176(PowerManagerService.this, 512);
                    PowerManagerService.this.updatePowerStateLocked();
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onProximityNegative() {
                synchronized (PowerManagerService.this.mLock) {
                    if (PowerManagerService.this.mProximityPositive) {
                        Slog.d(PowerManagerService.TAG, "DisplayPowerCallbacks : onProximityNegative()");
                        if (PowerManagerService.this.mWakefulness == 1) {
                            PowerManagerService.this.mNotifier.onScreenStateChangeStartedByProximity(false);
                            PowerManagerService.this.mLastScreenWakeTime = SystemClock.uptimeMillis();
                        }
                        PowerManagerService.this.mProximityPositive = false;
                        if (PowerManagerService.this.mWakefulness != 1) {
                            PowerManagerService.this.mPendingScreenStateChangeStartedByProximity = true;
                            PowerManagerService.this.wakeUpNoUpdateLocked(SystemClock.uptimeMillis(), "android.server.power:PROXIMITY", 1000, PowerManagerService.this.mContext.getOpPackageName(), 1000);
                        }
                        PowerManagerService.access$2176(PowerManagerService.this, 512);
                        PowerManagerService.this.userActivityNoUpdateLocked(SystemClock.uptimeMillis(), 0, 0, 1000);
                        PowerManagerService.this.updatePowerStateLocked();
                    }
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onDisplayStateChange(int state, boolean forceUpdate) {
                synchronized (PowerManagerService.this.mLock) {
                    if (this.mDisplayState != state || forceUpdate) {
                        this.mDisplayState = state;
                        if (state == 1) {
                            PowerManagerService.this.setNotiLightScreenTimeoutOverride(-1L);
                            if (!PowerManagerService.this.mDecoupleHalInteractiveModeFromDisplayConfig) {
                                PowerManagerService.this.setHalInteractiveModeLocked(false, forceUpdate);
                            }
                            if (!PowerManagerService.this.mDecoupleHalAutoSuspendModeFromDisplayConfig) {
                                PowerManagerService.this.setHalAutoSuspendModeLocked(true);
                            }
                        } else {
                            if (!PowerManagerService.this.mDecoupleHalAutoSuspendModeFromDisplayConfig) {
                                PowerManagerService.this.setHalAutoSuspendModeLocked(false);
                            }
                            if (!PowerManagerService.this.mDecoupleHalInteractiveModeFromDisplayConfig && state == 2) {
                                PowerManagerService.this.setHalInteractiveModeLocked(true, forceUpdate);
                            }
                        }
                    }
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onColorFadeExit(boolean exit) {
                Slog.d(PowerManagerService.TAG, "[input device light] onColorFadeExit(" + exit + Separators.RPAREN);
                if (PowerManagerService.this.mColorFadeExit != exit) {
                    PowerManagerService.this.mColorFadeExit = exit;
                    if (PowerManagerService.this.mColorFadeExit) {
                        PowerManagerService.this.mHandler.sendEmptyMessage(5);
                        PowerManagerService.this.userActivityNoUpdateLocked(SystemClock.uptimeMillis(), 0, 1, 1000);
                    }
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void acquireSuspendBlocker() {
                PowerManagerService.this.mDisplaySuspendBlocker.acquire();
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void releaseSuspendBlocker() {
                PowerManagerService.this.mDisplaySuspendBlocker.release();
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void updateDuration(long[] duration) {
                long lastScreenOnDuration = (SystemClock.uptimeMillis() - PowerManagerService.this.mLastScreenWakeTime) / 1000;
                int i = 0;
                long sumOfBrightnessDuration = 0;
                for (int i2 = 0; i2 < PowerManagerService.LD_KEYS.length - 2; i2++) {
                    sumOfBrightnessDuration += duration[i2];
                }
                int i3 = (lastScreenOnDuration > sumOfBrightnessDuration ? 1 : (lastScreenOnDuration == sumOfBrightnessDuration ? 0 : -1));
                if (i3 >= 0) {
                    while (true) {
                        int i4 = i;
                        if (i4 >= PowerManagerService.LD_KEYS.length - 2) {
                            break;
                        }
                        long[] jArr = PowerManagerService.this.mDPSIDuration;
                        jArr[i4] = jArr[i4] + duration[i4];
                        i = i4 + 1;
                    }
                    long[] jArr2 = PowerManagerService.this.mDPSIDuration;
                    jArr2[3] = jArr2[3] + lastScreenOnDuration;
                    if (PowerManagerUtil.SEC_FEATURE_AFC && !PowerManagerService.this.mSentAllDisplayOnTimeIntent) {
                        PowerManagerService.this.addDisplayOnTime((int) lastScreenOnDuration);
                    }
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onAutoBrightnessAdjutmentApplied(int brightness, int lux, int beforeBrightness) {
                String packageName = "";
                ActivityManager activityManager = (ActivityManager) PowerManagerService.this.mContext.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
                if (!tasks.isEmpty()) {
                    packageName = tasks.get(0).topActivity.getPackageName();
                }
                String totalInfomation = packageName + "," + beforeBrightness + "," + PowerManagerService.this.mBatteryLevel;
                String[] values = {Integer.toString(brightness), Integer.toString(lux), Integer.toString(PowerManagerService.this.mScreenBrightnessModeSetting), totalInfomation};
                String dataset = PowerManagerService.this.parseBigData(PowerManagerService.LUBS_KEYS, values, PowerManagerService.LUBS_KEYS.length);
                PowerManagerService.this.mHandler.removeMessages(6);
                PowerManagerService.this.mHandler.sendMessageDelayed(PowerManagerService.this.mHandler.obtainMessage(6, dataset), 10000L);
            }

            public String toString() {
                String str;
                synchronized (this) {
                    str = "state=" + Display.stateToString(this.mDisplayState);
                }
                return str;
            }
        };
        this.mVrStateCallbacks = new IVrStateCallbacks.Stub() { // from class: com.android.server.power.PowerManagerService.13
            @Override // android.service.vr.IVrStateCallbacks
            public void onVrStateChanged(boolean enabled) {
                PowerManagerService.this.powerHintInternal(7, enabled ? 1 : 0);
                synchronized (PowerManagerService.this.mLock) {
                    if (PowerManagerService.this.mIsVrModeEnabled != enabled) {
                        PowerManagerService.this.setVrModeEnabled(enabled);
                        PowerManagerService.access$2176(PowerManagerService.this, 8192);
                        PowerManagerService.this.updatePowerStateLocked();
                    }
                }
            }
        };
        this.mGearVrStateCallbacks = new IGearVrStateCallbacks.Stub() { // from class: com.android.server.power.PowerManagerService.14
            public void onGearVrStateChanged(int state, int type) throws RemoteException {
                synchronized (PowerManagerService.this.mLock) {
                    boolean vrModeEnabled = PowerManagerService.this.isVrModeEnabled();
                    Slog.v(PowerManagerService.TAG, "onGearVrStateChanged state=" + state + " type=" + type + " vrModeEnabled=" + vrModeEnabled);
                    if (state == 64) {
                        if (type == 2) {
                            PowerManagerService.this.mIsGearVrPersistentVrModeEnabled = true;
                        } else {
                            PowerManagerService.this.mIsGearVrModeEnabled = true;
                        }
                        if (vrModeEnabled != PowerManagerService.this.isVrModeEnabled()) {
                            PowerManagerService.access$2176(PowerManagerService.this, 8192);
                            Slog.v(PowerManagerService.TAG, "updatePowerStateLocked mIsGearVrModeEnabled=" + PowerManagerService.this.mIsGearVrModeEnabled + " mIsGearVrPersistentVrModeEnabled=" + PowerManagerService.this.mIsGearVrPersistentVrModeEnabled);
                            PowerManagerService.this.updatePowerStateLocked();
                        }
                    } else if (state != 128) {
                        switch (state) {
                            case 1:
                                if (type == 2) {
                                    PowerManagerService.this.mIsGearVrTetheredDocked = true;
                                }
                                PowerManagerService.this.mIsGearVrDocked = true;
                                if (vrModeEnabled != PowerManagerService.this.isVrModeEnabled()) {
                                    PowerManagerService.access$2176(PowerManagerService.this, 8192);
                                    Slog.v(PowerManagerService.TAG, "updatePowerStateLocked for TVR mIsGearVrDocked=" + PowerManagerService.this.mIsGearVrDocked + " mIsGearVrTetheredDocked=" + PowerManagerService.this.mIsGearVrTetheredDocked);
                                    PowerManagerService.this.updatePowerStateLocked();
                                    break;
                                }
                                break;
                            case 2:
                                PowerManagerService.this.mIsGearVrDocked = false;
                                PowerManagerService.this.mIsGearVrTetheredDocked = false;
                                if (vrModeEnabled != PowerManagerService.this.isVrModeEnabled()) {
                                    PowerManagerService.access$2176(PowerManagerService.this, 8192);
                                    Slog.v(PowerManagerService.TAG, "updatePowerStateLocked mIsGearVrDocked=" + PowerManagerService.this.mIsGearVrDocked + " mIsGearVrTetheredDocked=" + PowerManagerService.this.mIsGearVrTetheredDocked);
                                    PowerManagerService.this.updatePowerStateLocked();
                                    break;
                                }
                                break;
                        }
                    } else {
                        if (type == 2) {
                            PowerManagerService.this.mIsGearVrPersistentVrModeEnabled = false;
                        } else {
                            PowerManagerService.this.mIsGearVrModeEnabled = false;
                        }
                        if (vrModeEnabled != PowerManagerService.this.isVrModeEnabled()) {
                            PowerManagerService.access$2176(PowerManagerService.this, 8192);
                            Slog.v(PowerManagerService.TAG, "updatePowerStateLocked mIsGearVrModeEnabled=" + PowerManagerService.this.mIsGearVrModeEnabled + " mIsGearVrPersistentVrModeEnabled=" + PowerManagerService.this.mIsGearVrPersistentVrModeEnabled);
                            PowerManagerService.this.updatePowerStateLocked();
                        }
                    }
                }
            }
        };
        this.mSipIntentReceiver = new BroadcastReceiver() { // from class: com.android.server.power.PowerManagerService.15
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                synchronized (PowerManagerService.this.mLock) {
                    String action = intent.getAction();
                    if (PowerManagerService.RESPONSE_AXT9INFO.equals(action)) {
                        try {
                            PowerManagerService.this.mIsSipVisible = intent.getBooleanExtra(PowerManagerService.IS_VISIBLE_WINDOW, false);
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
        this.mCoverAuthReadyRunnable = new Runnable() { // from class: com.android.server.power.PowerManagerService.16
            @Override // java.lang.Runnable
            public void run() {
                synchronized (PowerManagerService.this.mLock) {
                    Slog.d(PowerManagerService.TAG, "mCoverAuthReady since boot");
                    PowerManagerService.this.mCoverAuthReady = true;
                    PowerManagerService.this.mCoverAuthReadyRunnable = null;
                }
            }
        };
        this.mCoverStateListener = new CoverManager.CoverStateListener() { // from class: com.android.server.power.PowerManagerService.17
            public void onCoverSwitchStateChanged(boolean swtichState) {
            }

            public void onCoverAttachStateChanged(boolean attach) {
                if (PowerManagerService.this.mFeatureCoverSysfs) {
                    try {
                        if (attach) {
                            CoverState state = PowerManagerService.this.mCoverManager.getCoverState();
                            if (state != null) {
                                PowerManagerService.this.mCoverType = state.getType();
                            }
                        } else {
                            PowerManagerService.this.mCoverType = 2;
                        }
                        Slog.d(PowerManagerService.TAG, "onCoverAttachStateChanged: attach: " + attach + "  mCoverType: " + PowerManagerService.this.mCoverType);
                    } catch (NullPointerException e) {
                        Slog.d(PowerManagerService.TAG, "onCoverAttachStateChanged: mCoverManager returns NULL.");
                    }
                    long time = SystemClock.uptimeMillis();
                    PowerManagerService.this.mlastSetCoverTypeTime = time;
                    PowerManagerService powerManagerService = PowerManagerService.this;
                    powerManagerService.mlastUpdateCoverTypeReason = "onCoverAttachStateChanged = " + PowerManagerService.this.mCoverType;
                }
            }
        };
        this.mPrintWakeLockAperiodicallyRunnable = new Runnable() { // from class: com.android.server.power.PowerManagerService.18
            @Override // java.lang.Runnable
            public void run() {
                synchronized (PowerManagerService.this.mLock) {
                    PowerManagerService.this.mHandler.removeCallbacks(this);
                    PowerManagerService.this.printWakeLockLocked();
                    PowerManagerService.access$17914(PowerManagerService.this, TimedRemoteCaller.DEFAULT_CALL_TIMEOUT_MILLIS);
                    PowerManagerService.this.mDelayTimePrintWakeLock = Math.min(1800000L, PowerManagerService.this.mDelayTimePrintWakeLock);
                    PowerManagerService.this.mHandler.postDelayed(this, PowerManagerService.this.mDelayTimePrintWakeLock);
                }
            }
        };
        this.mWirelessChargerSContextListener = new SContextListener() { // from class: com.android.server.power.PowerManagerService.20
            @Override // android.hardware.scontext.SContextListener
            public void onSContextChanged(SContextEvent event) {
                SContext scontext = event.scontext;
                long moveStateChangeTime = SystemClock.elapsedRealtime();
                if (scontext.getType() == 46) {
                    SContextWirelessChargingDetection wirelessChargingDetection = event.getWirelessChargingDetectionContext();
                    int action = wirelessChargingDetection.getAction();
                    switch (action) {
                        case 0:
                            Slog.d(PowerManagerService.TAG, "WirelessChargerSContextListener : No Move");
                            PowerManagerService.this.mIsDeviceMoving = false;
                            return;
                        case 1:
                            Slog.d(PowerManagerService.TAG, "WirelessChargerSContextListener : Move");
                            PowerManagerService.this.mIsDeviceMoving = true;
                            if (PowerManagerService.this.mIsWirelessChargerSContextRegistered && !PowerManagerService.this.mBatteryManagerInternal.isPowered(4)) {
                                Slog.d(PowerManagerService.TAG, "SContextListener : Unregister SContextListener");
                                PowerManagerService.this.mWirelessChargerSContextManager.unregisterListener(PowerManagerService.this.mWirelessChargerSContextListener, 46);
                                PowerManagerService.this.mIsWirelessChargerSContextRegistered = false;
                                synchronized (PowerManagerService.this.mLock) {
                                    if (moveStateChangeTime - PowerManagerService.this.mPassedWakeupTime < 300) {
                                        Slog.d(PowerManagerService.TAG, "WirelessChargerSContextListener : received move lately");
                                        PowerManagerService.this.mPassedWakeupTime = 0L;
                                        PowerManagerService.access$2176(PowerManagerService.this, 64);
                                        PowerManagerService.this.mScreenOnReason = " powered change";
                                        PowerManagerService.this.mLastWakeUpReason = 11;
                                        PowerManagerService.this.wakeUpNoUpdateLocked(SystemClock.uptimeMillis(), "android.server.power:POWER", 1000, PowerManagerService.this.mContext.getOpPackageName(), 1000);
                                        PowerManagerService.this.updatePowerStateLocked();
                                    }
                                }
                                return;
                            }
                            return;
                        default:
                            return;
                    }
                }
            }
        };
        this.mScaleFactorLock = null;
        this.mContext = context;
        this.mHandlerThread = new ServiceThread(TAG, -4, false);
        this.mHandlerThread.start();
        this.mHandler = new PowerManagerHandler(this.mHandlerThread.getLooper());
        this.mHandlerThreadPmsMisc = new HandlerThread("pms.misc");
        this.mHandlerThreadPmsMisc.start();
        this.mHandlerPmsMisc = new Handler(this.mHandlerThreadPmsMisc.getLooper());
        this.mConstants = new Constants(this.mHandler);
        this.mAmbientDisplayConfiguration = new AmbientDisplayConfiguration(this.mContext);
        this.mBatterySavingStats = new BatterySavingStats(this.mLock);
        this.mBatterySaverPolicy = new BatterySaverPolicy(this.mLock, this.mContext, this.mBatterySavingStats);
        this.mBatterySaverController = new BatterySaverController(this.mLock, this.mContext, BackgroundThread.get().getLooper(), this.mBatterySaverPolicy, this.mBatterySavingStats);
        this.mBatterySaverStateMachine = new BatterySaverStateMachine(this.mLock, this.mContext, this.mBatterySaverController);
        this.mTimeMeasurement = new PowerManagerUtil.TimeMeasurement();
        synchronized (this.mLock) {
            this.mWakeLockSuspendBlocker = createSuspendBlockerLocked("PowerManagerService.WakeLocks");
            this.mDisplaySuspendBlocker = createSuspendBlockerLocked("PowerManagerService.Display");
            this.mDisplaySuspendBlocker.acquire();
            this.mHoldingDisplaySuspendBlocker = true;
            this.mHalAutoSuspendModeEnabled = false;
            this.mHalInteractiveModeEnabled = true;
            this.mWakefulness = 1;
            sQuiescent = SystemProperties.get(SYSTEM_PROPERTY_QUIESCENT, "0").equals("1");
            nativeInit();
            nativeSetAutoSuspend(false);
            nativeSetInteractive(true);
            nativeSetFeature(1, 0);
            PowerManagerUtil.isAdaptiveBrightnessFeatureSupported();
        }
    }

    @VisibleForTesting
    PowerManagerService(Context context, BatterySaverPolicy batterySaverPolicy) {
        super(context);
        this.mLock = LockGuard.installNewLock(1);
        this.mSuspendBlockers = new ArrayList<>();
        this.mWakeLocks = new ArrayList<>();
        this.mAbuseWakeLocks = new ArrayList<>();
        this.mNeedCheckAbuseWakelocks = false;
        this.mAbuseWakeLockDisabled = false;
        this.mAllBrightWakeLockAbuse = false;
        this.mAutoBrightnessLowerLimit = -1;
        this.mAutoBrightnessUpperLimit = -1;
        this.mDisplayPowerRequest = new DisplayManagerInternal.DisplayPowerRequest();
        this.mDockState = 0;
        this.mFTAMode = false;
        this.mMaximumScreenOffTimeoutFromDeviceAdmin = Long.MAX_VALUE;
        this.mScreenBrightnessOverrideFromWindowManager = -1;
        this.mOverriddenTimeout = -1L;
        this.mUserActivityTimeoutOverrideFromWindowManager = -1L;
        this.mNotificationLightScreenOverride = -1L;
        this.mUserActivityTimeoutForDexOverrideFromWindowManager = -1L;
        this.mDozeScreenStateOverrideFromDreamManager = 0;
        this.mDozeScreenBrightnessOverrideFromDreamManager = -1;
        this.mLastWarningAboutUserActivityPermission = Long.MIN_VALUE;
        this.mDeviceIdleWhitelist = new int[0];
        this.mDeviceIdleTempWhitelist = new int[0];
        this.mUidState = new SparseArray<>();
        this.mIsFolderType = false;
        this.mIsLidClosed = false;
        this.mIsDualScreenFolder = false;
        this.mWakeUpEvenThoughProximityPositive = false;
        this.mAlpmHlpmMode = -1;
        this.mForcedDisplayOff = false;
        this.mUserActivityTimeoutSetting = 0;
        this.mUserActivityTask = new UserActivityTask();
        this.mUserActivityTimeoutTask = new UserActivityTimeoutTask();
        this.mInternalWakefulnessForDex = 1;
        this.mIsDualViewModeChanging = false;
        this.mScreenOffTimeoutForDex = 600000;
        this.mSmartFaceManager = null;
        this.USE_SMART_STAY = true;
        this.USE_PRE_SMART_STAY = isTablet();
        this.mSmartStayDelay = EventLogTags.NOTIFICATION_ENQUEUE;
        this.mPreSmartStayDelay = 6000;
        this.mIsBadCurrentConsumptionDevice = true;
        this.mWhenCheckSmartStay = 1;
        this.mSmartStayEnabledSetting = false;
        this.mPendingMessageSmartStay = false;
        this.mPendingMessagePreSmartStay = false;
        this.mFaceDetected = false;
        this.mNextTimeoutForSmartStay = -1L;
        this.mNextTimeoutForPreSmartStay = -1L;
        this.mSmartStayProgress = false;
        this.mShutdownOrRebootCaller = "";
        this.mLastUserActivitySummary = -1;
        this.mLastScreenTimeout = -1;
        this.mIsSupportedLightSensor = true;
        this.mIsCoverClosed = false;
        this.mlastUpdateCoverStateReason = "";
        this.mlastUpdateCoverStateTime = -1L;
        this.mlastUpdateCoverTypeReason = "";
        this.mlastSetCoverTypeTime = -1L;
        this.mFeatureCoverSysfs = true;
        this.mCoverManager = null;
        this.mCoverType = 2;
        this.mEdgeInternal = null;
        this.mWirelessChargerSContextManager = null;
        this.mIsWirelessChargerSContextEnabled = false;
        this.mIsWirelessChargerSContextRegistered = false;
        this.mIsDeviceMoving = true;
        this.mPassedWakeupTime = 0L;
        this.mLCDFlashModeLock = null;
        this.mOutdoorModeSetting = false;
        this.mClearViewBrightnessModeCount = 0;
        this.mClearViewBrightnessModeEnabled = false;
        this.mColorWeaknessModeCount = 0;
        this.mColorWeaknessModeEnabled = false;
        this.mScreenBrightnessScaleFactor = -1.0f;
        this.mFreezingScreenBrightness = false;
        this.mAlarmManager = null;
        this.mSemHqmManager = null;
        this.mDPSIDuration = new long[5];
        this.mSentDisplayOnTimeIntent = new int[4];
        this.mSentAllDisplayOnTimeIntent = false;
        this.mDisplayOnTime = -1;
        this.mDelayTimePrintWakeLock = -1L;
        this.mScreenOffReason = "";
        this.mScreenOnReason = "";
        this.mPrevReleasedWakeLock = "";
        this.mScreenOnOffCount = 0;
        this.COUNT_FOR_LOGGING_FREQUENT_EVENT = 10;
        this.COUNT_TO_RESET_FOR_LOGGING_FREQUENT_EVENT = 5000;
        this.mNativeUserActivityCount = 0;
        this.mSmartViewConnected = false;
        this.mDualScreenPolicy = -1;
        this.mKeyboardLightState = null;
        this.mTouchkeyLightState = null;
        this.mTouchKeyOffTimeoutOverrideFromWindowManager = -1L;
        this.mKeyboardOffTimeoutSetting = 2000;
        this.mColorFadeExit = true;
        this.mPrevTouchKeyForceDisable = false;
        this.mInputDeviceEnabled = true;
        this.mTouchKeyForceDisable = false;
        this.mTouchKeyForceDisableOverrideFromSystemPowerSaveMode = false;
        this.mIsSipVisible = false;
        this.mScreenDimDurationOverrideFromWindowManager = -1L;
        this.mWakelockBlacklistAppid = new ArrayList<>();
        this.mLastBrightnessOverrideState = false;
        this.mKnoxHandler = new KnoxTimeoutHandler();
        this.isActive = false;
        this.mProfilePowerState = new SparseArray<>();
        this.mLastWakeUpReason = 0;
        this.mDisplayPowerCallbacks = new DisplayManagerInternal.DisplayPowerCallbacks() { // from class: com.android.server.power.PowerManagerService.9
            private int mDisplayState = 0;

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onStateChanged() {
                synchronized (PowerManagerService.this.mLock) {
                    Slog.d(PowerManagerService.TAG, "DisplayPowerCallbacks : onStateChanged()");
                    PowerManagerService.access$2176(PowerManagerService.this, 8);
                    PowerManagerService.this.updatePowerStateLocked();
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onProximityPositive() {
                synchronized (PowerManagerService.this.mLock) {
                    if (PowerManagerService.this.mProximityPositive) {
                        return;
                    }
                    Slog.d(PowerManagerService.TAG, "DisplayPowerCallbacks : onProximityPositive()");
                    PowerManagerService.this.mNotifier.onScreenStateChangeStartedByProximity(true);
                    PowerManagerService.this.mProximityPositive = true;
                    PowerManagerService.access$2176(PowerManagerService.this, 512);
                    PowerManagerService.this.updatePowerStateLocked();
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onProximityNegative() {
                synchronized (PowerManagerService.this.mLock) {
                    if (PowerManagerService.this.mProximityPositive) {
                        Slog.d(PowerManagerService.TAG, "DisplayPowerCallbacks : onProximityNegative()");
                        if (PowerManagerService.this.mWakefulness == 1) {
                            PowerManagerService.this.mNotifier.onScreenStateChangeStartedByProximity(false);
                            PowerManagerService.this.mLastScreenWakeTime = SystemClock.uptimeMillis();
                        }
                        PowerManagerService.this.mProximityPositive = false;
                        if (PowerManagerService.this.mWakefulness != 1) {
                            PowerManagerService.this.mPendingScreenStateChangeStartedByProximity = true;
                            PowerManagerService.this.wakeUpNoUpdateLocked(SystemClock.uptimeMillis(), "android.server.power:PROXIMITY", 1000, PowerManagerService.this.mContext.getOpPackageName(), 1000);
                        }
                        PowerManagerService.access$2176(PowerManagerService.this, 512);
                        PowerManagerService.this.userActivityNoUpdateLocked(SystemClock.uptimeMillis(), 0, 0, 1000);
                        PowerManagerService.this.updatePowerStateLocked();
                    }
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onDisplayStateChange(int state, boolean forceUpdate) {
                synchronized (PowerManagerService.this.mLock) {
                    if (this.mDisplayState != state || forceUpdate) {
                        this.mDisplayState = state;
                        if (state == 1) {
                            PowerManagerService.this.setNotiLightScreenTimeoutOverride(-1L);
                            if (!PowerManagerService.this.mDecoupleHalInteractiveModeFromDisplayConfig) {
                                PowerManagerService.this.setHalInteractiveModeLocked(false, forceUpdate);
                            }
                            if (!PowerManagerService.this.mDecoupleHalAutoSuspendModeFromDisplayConfig) {
                                PowerManagerService.this.setHalAutoSuspendModeLocked(true);
                            }
                        } else {
                            if (!PowerManagerService.this.mDecoupleHalAutoSuspendModeFromDisplayConfig) {
                                PowerManagerService.this.setHalAutoSuspendModeLocked(false);
                            }
                            if (!PowerManagerService.this.mDecoupleHalInteractiveModeFromDisplayConfig && state == 2) {
                                PowerManagerService.this.setHalInteractiveModeLocked(true, forceUpdate);
                            }
                        }
                    }
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onColorFadeExit(boolean exit) {
                Slog.d(PowerManagerService.TAG, "[input device light] onColorFadeExit(" + exit + Separators.RPAREN);
                if (PowerManagerService.this.mColorFadeExit != exit) {
                    PowerManagerService.this.mColorFadeExit = exit;
                    if (PowerManagerService.this.mColorFadeExit) {
                        PowerManagerService.this.mHandler.sendEmptyMessage(5);
                        PowerManagerService.this.userActivityNoUpdateLocked(SystemClock.uptimeMillis(), 0, 1, 1000);
                    }
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void acquireSuspendBlocker() {
                PowerManagerService.this.mDisplaySuspendBlocker.acquire();
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void releaseSuspendBlocker() {
                PowerManagerService.this.mDisplaySuspendBlocker.release();
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void updateDuration(long[] duration) {
                long lastScreenOnDuration = (SystemClock.uptimeMillis() - PowerManagerService.this.mLastScreenWakeTime) / 1000;
                int i = 0;
                long sumOfBrightnessDuration = 0;
                for (int i2 = 0; i2 < PowerManagerService.LD_KEYS.length - 2; i2++) {
                    sumOfBrightnessDuration += duration[i2];
                }
                int i3 = (lastScreenOnDuration > sumOfBrightnessDuration ? 1 : (lastScreenOnDuration == sumOfBrightnessDuration ? 0 : -1));
                if (i3 >= 0) {
                    while (true) {
                        int i4 = i;
                        if (i4 >= PowerManagerService.LD_KEYS.length - 2) {
                            break;
                        }
                        long[] jArr = PowerManagerService.this.mDPSIDuration;
                        jArr[i4] = jArr[i4] + duration[i4];
                        i = i4 + 1;
                    }
                    long[] jArr2 = PowerManagerService.this.mDPSIDuration;
                    jArr2[3] = jArr2[3] + lastScreenOnDuration;
                    if (PowerManagerUtil.SEC_FEATURE_AFC && !PowerManagerService.this.mSentAllDisplayOnTimeIntent) {
                        PowerManagerService.this.addDisplayOnTime((int) lastScreenOnDuration);
                    }
                }
            }

            @Override // android.hardware.display.DisplayManagerInternal.DisplayPowerCallbacks
            public void onAutoBrightnessAdjutmentApplied(int brightness, int lux, int beforeBrightness) {
                String packageName = "";
                ActivityManager activityManager = (ActivityManager) PowerManagerService.this.mContext.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
                if (!tasks.isEmpty()) {
                    packageName = tasks.get(0).topActivity.getPackageName();
                }
                String totalInfomation = packageName + "," + beforeBrightness + "," + PowerManagerService.this.mBatteryLevel;
                String[] values = {Integer.toString(brightness), Integer.toString(lux), Integer.toString(PowerManagerService.this.mScreenBrightnessModeSetting), totalInfomation};
                String dataset = PowerManagerService.this.parseBigData(PowerManagerService.LUBS_KEYS, values, PowerManagerService.LUBS_KEYS.length);
                PowerManagerService.this.mHandler.removeMessages(6);
                PowerManagerService.this.mHandler.sendMessageDelayed(PowerManagerService.this.mHandler.obtainMessage(6, dataset), 10000L);
            }

            public String toString() {
                String str;
                synchronized (this) {
                    str = "state=" + Display.stateToString(this.mDisplayState);
                }
                return str;
            }
        };
        this.mVrStateCallbacks = new IVrStateCallbacks.Stub() { // from class: com.android.server.power.PowerManagerService.13
            @Override // android.service.vr.IVrStateCallbacks
            public void onVrStateChanged(boolean enabled) {
                PowerManagerService.this.powerHintInternal(7, enabled ? 1 : 0);
                synchronized (PowerManagerService.this.mLock) {
                    if (PowerManagerService.this.mIsVrModeEnabled != enabled) {
                        PowerManagerService.this.setVrModeEnabled(enabled);
                        PowerManagerService.access$2176(PowerManagerService.this, 8192);
                        PowerManagerService.this.updatePowerStateLocked();
                    }
                }
            }
        };
        this.mGearVrStateCallbacks = new IGearVrStateCallbacks.Stub() { // from class: com.android.server.power.PowerManagerService.14
            public void onGearVrStateChanged(int state, int type) throws RemoteException {
                synchronized (PowerManagerService.this.mLock) {
                    boolean vrModeEnabled = PowerManagerService.this.isVrModeEnabled();
                    Slog.v(PowerManagerService.TAG, "onGearVrStateChanged state=" + state + " type=" + type + " vrModeEnabled=" + vrModeEnabled);
                    if (state == 64) {
                        if (type == 2) {
                            PowerManagerService.this.mIsGearVrPersistentVrModeEnabled = true;
                        } else {
                            PowerManagerService.this.mIsGearVrModeEnabled = true;
                        }
                        if (vrModeEnabled != PowerManagerService.this.isVrModeEnabled()) {
                            PowerManagerService.access$2176(PowerManagerService.this, 8192);
                            Slog.v(PowerManagerService.TAG, "updatePowerStateLocked mIsGearVrModeEnabled=" + PowerManagerService.this.mIsGearVrModeEnabled + " mIsGearVrPersistentVrModeEnabled=" + PowerManagerService.this.mIsGearVrPersistentVrModeEnabled);
                            PowerManagerService.this.updatePowerStateLocked();
                        }
                    } else if (state != 128) {
                        switch (state) {
                            case 1:
                                if (type == 2) {
                                    PowerManagerService.this.mIsGearVrTetheredDocked = true;
                                }
                                PowerManagerService.this.mIsGearVrDocked = true;
                                if (vrModeEnabled != PowerManagerService.this.isVrModeEnabled()) {
                                    PowerManagerService.access$2176(PowerManagerService.this, 8192);
                                    Slog.v(PowerManagerService.TAG, "updatePowerStateLocked for TVR mIsGearVrDocked=" + PowerManagerService.this.mIsGearVrDocked + " mIsGearVrTetheredDocked=" + PowerManagerService.this.mIsGearVrTetheredDocked);
                                    PowerManagerService.this.updatePowerStateLocked();
                                    break;
                                }
                                break;
                            case 2:
                                PowerManagerService.this.mIsGearVrDocked = false;
                                PowerManagerService.this.mIsGearVrTetheredDocked = false;
                                if (vrModeEnabled != PowerManagerService.this.isVrModeEnabled()) {
                                    PowerManagerService.access$2176(PowerManagerService.this, 8192);
                                    Slog.v(PowerManagerService.TAG, "updatePowerStateLocked mIsGearVrDocked=" + PowerManagerService.this.mIsGearVrDocked + " mIsGearVrTetheredDocked=" + PowerManagerService.this.mIsGearVrTetheredDocked);
                                    PowerManagerService.this.updatePowerStateLocked();
                                    break;
                                }
                                break;
                        }
                    } else {
                        if (type == 2) {
                            PowerManagerService.this.mIsGearVrPersistentVrModeEnabled = false;
                        } else {
                            PowerManagerService.this.mIsGearVrModeEnabled = false;
                        }
                        if (vrModeEnabled != PowerManagerService.this.isVrModeEnabled()) {
                            PowerManagerService.access$2176(PowerManagerService.this, 8192);
                            Slog.v(PowerManagerService.TAG, "updatePowerStateLocked mIsGearVrModeEnabled=" + PowerManagerService.this.mIsGearVrModeEnabled + " mIsGearVrPersistentVrModeEnabled=" + PowerManagerService.this.mIsGearVrPersistentVrModeEnabled);
                            PowerManagerService.this.updatePowerStateLocked();
                        }
                    }
                }
            }
        };
        this.mSipIntentReceiver = new BroadcastReceiver() { // from class: com.android.server.power.PowerManagerService.15
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                synchronized (PowerManagerService.this.mLock) {
                    String action = intent.getAction();
                    if (PowerManagerService.RESPONSE_AXT9INFO.equals(action)) {
                        try {
                            PowerManagerService.this.mIsSipVisible = intent.getBooleanExtra(PowerManagerService.IS_VISIBLE_WINDOW, false);
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
        this.mCoverAuthReadyRunnable = new Runnable() { // from class: com.android.server.power.PowerManagerService.16
            @Override // java.lang.Runnable
            public void run() {
                synchronized (PowerManagerService.this.mLock) {
                    Slog.d(PowerManagerService.TAG, "mCoverAuthReady since boot");
                    PowerManagerService.this.mCoverAuthReady = true;
                    PowerManagerService.this.mCoverAuthReadyRunnable = null;
                }
            }
        };
        this.mCoverStateListener = new CoverManager.CoverStateListener() { // from class: com.android.server.power.PowerManagerService.17
            public void onCoverSwitchStateChanged(boolean swtichState) {
            }

            public void onCoverAttachStateChanged(boolean attach) {
                if (PowerManagerService.this.mFeatureCoverSysfs) {
                    try {
                        if (attach) {
                            CoverState state = PowerManagerService.this.mCoverManager.getCoverState();
                            if (state != null) {
                                PowerManagerService.this.mCoverType = state.getType();
                            }
                        } else {
                            PowerManagerService.this.mCoverType = 2;
                        }
                        Slog.d(PowerManagerService.TAG, "onCoverAttachStateChanged: attach: " + attach + "  mCoverType: " + PowerManagerService.this.mCoverType);
                    } catch (NullPointerException e) {
                        Slog.d(PowerManagerService.TAG, "onCoverAttachStateChanged: mCoverManager returns NULL.");
                    }
                    long time = SystemClock.uptimeMillis();
                    PowerManagerService.this.mlastSetCoverTypeTime = time;
                    PowerManagerService powerManagerService = PowerManagerService.this;
                    powerManagerService.mlastUpdateCoverTypeReason = "onCoverAttachStateChanged = " + PowerManagerService.this.mCoverType;
                }
            }
        };
        this.mPrintWakeLockAperiodicallyRunnable = new Runnable() { // from class: com.android.server.power.PowerManagerService.18
            @Override // java.lang.Runnable
            public void run() {
                synchronized (PowerManagerService.this.mLock) {
                    PowerManagerService.this.mHandler.removeCallbacks(this);
                    PowerManagerService.this.printWakeLockLocked();
                    PowerManagerService.access$17914(PowerManagerService.this, TimedRemoteCaller.DEFAULT_CALL_TIMEOUT_MILLIS);
                    PowerManagerService.this.mDelayTimePrintWakeLock = Math.min(1800000L, PowerManagerService.this.mDelayTimePrintWakeLock);
                    PowerManagerService.this.mHandler.postDelayed(this, PowerManagerService.this.mDelayTimePrintWakeLock);
                }
            }
        };
        this.mWirelessChargerSContextListener = new SContextListener() { // from class: com.android.server.power.PowerManagerService.20
            @Override // android.hardware.scontext.SContextListener
            public void onSContextChanged(SContextEvent event) {
                SContext scontext = event.scontext;
                long moveStateChangeTime = SystemClock.elapsedRealtime();
                if (scontext.getType() == 46) {
                    SContextWirelessChargingDetection wirelessChargingDetection = event.getWirelessChargingDetectionContext();
                    int action = wirelessChargingDetection.getAction();
                    switch (action) {
                        case 0:
                            Slog.d(PowerManagerService.TAG, "WirelessChargerSContextListener : No Move");
                            PowerManagerService.this.mIsDeviceMoving = false;
                            return;
                        case 1:
                            Slog.d(PowerManagerService.TAG, "WirelessChargerSContextListener : Move");
                            PowerManagerService.this.mIsDeviceMoving = true;
                            if (PowerManagerService.this.mIsWirelessChargerSContextRegistered && !PowerManagerService.this.mBatteryManagerInternal.isPowered(4)) {
                                Slog.d(PowerManagerService.TAG, "SContextListener : Unregister SContextListener");
                                PowerManagerService.this.mWirelessChargerSContextManager.unregisterListener(PowerManagerService.this.mWirelessChargerSContextListener, 46);
                                PowerManagerService.this.mIsWirelessChargerSContextRegistered = false;
                                synchronized (PowerManagerService.this.mLock) {
                                    if (moveStateChangeTime - PowerManagerService.this.mPassedWakeupTime < 300) {
                                        Slog.d(PowerManagerService.TAG, "WirelessChargerSContextListener : received move lately");
                                        PowerManagerService.this.mPassedWakeupTime = 0L;
                                        PowerManagerService.access$2176(PowerManagerService.this, 64);
                                        PowerManagerService.this.mScreenOnReason = " powered change";
                                        PowerManagerService.this.mLastWakeUpReason = 11;
                                        PowerManagerService.this.wakeUpNoUpdateLocked(SystemClock.uptimeMillis(), "android.server.power:POWER", 1000, PowerManagerService.this.mContext.getOpPackageName(), 1000);
                                        PowerManagerService.this.updatePowerStateLocked();
                                    }
                                }
                                return;
                            }
                            return;
                        default:
                            return;
                    }
                }
            }
        };
        this.mScaleFactorLock = null;
        this.mContext = context;
        this.mHandlerThread = new ServiceThread(TAG, -4, false);
        this.mHandlerThread.start();
        this.mHandler = new PowerManagerHandler(this.mHandlerThread.getLooper());
        this.mHandlerThreadPmsMisc = new HandlerThread("pms.misc");
        this.mHandlerThreadPmsMisc.start();
        this.mHandlerPmsMisc = new Handler(this.mHandlerThreadPmsMisc.getLooper());
        this.mConstants = new Constants(this.mHandler);
        this.mAmbientDisplayConfiguration = new AmbientDisplayConfiguration(this.mContext);
        this.mDisplaySuspendBlocker = null;
        this.mWakeLockSuspendBlocker = null;
        this.mBatterySavingStats = new BatterySavingStats(this.mLock);
        this.mBatterySaverPolicy = batterySaverPolicy;
        this.mBatterySaverController = new BatterySaverController(this.mLock, context, BackgroundThread.getHandler().getLooper(), batterySaverPolicy, this.mBatterySavingStats);
        this.mBatterySaverStateMachine = new BatterySaverStateMachine(this.mLock, this.mContext, this.mBatterySaverController);
    }

    @Override // com.android.server.SystemService
    public void onStart() {
        publishBinderService(Context.POWER_SERVICE, new BinderService());
        publishLocalService(PowerManagerInternal.class, new LocalService());
        Watchdog.getInstance().addMonitor(this);
        Watchdog.getInstance().addThread(this.mHandler);
    }

    @Override // com.android.server.SystemService
    public void onBootPhase(int phase) {
        Runnable[] runnableArr;
        synchronized (this.mLock) {
            try {
                if (phase == 600) {
                    incrementBootCount();
                } else if (phase == 1000) {
                    long now = SystemClock.uptimeMillis();
                    this.mBootCompleted = true;
                    this.mDirty |= 16;
                    this.mBatterySaverStateMachine.onBootCompleted();
                    userActivityNoUpdateLocked(now, 0, 0, 1000);
                    updatePowerStateLocked();
                    if (!ArrayUtils.isEmpty(this.mBootCompletedRunnables)) {
                        Slog.d(TAG, "Posting " + this.mBootCompletedRunnables.length + " delayed runnables");
                        for (Runnable r : this.mBootCompletedRunnables) {
                            BackgroundThread.getHandler().post(r);
                        }
                    }
                    this.mBootCompletedRunnables = null;
                    this.mHandler.postDelayed(this.mCoverAuthReadyRunnable, COVER_AUTH_READY_TIME_SINCE_BOOT_MS);
                    handleDexScreenOffTimeoutChange();
                    if (PowerManagerUtil.SEC_FEATURE_AFC) {
                        this.mDisplayOnTime = readDisplayOnTimeFromEfs();
                        initSentDisplayOnTimeIntent(this.mDisplayOnTime);
                        Slog.d(TAG, "DisplayOnTime : " + this.mDisplayOnTime);
                    }
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void systemReady(IAppOpsService appOps) {
        synchronized (this.mLock) {
            this.mSystemReady = true;
            this.mAppOps = appOps;
            this.mDreamManager = (DreamManagerInternal) getLocalService(DreamManagerInternal.class);
            this.mDisplayManagerInternal = (DisplayManagerInternal) getLocalService(DisplayManagerInternal.class);
            this.mPolicy = (WindowManagerPolicy) getLocalService(WindowManagerPolicy.class);
            this.mBatteryManagerInternal = (BatteryManagerInternal) getLocalService(BatteryManagerInternal.class);
            this.mEdgeInternal = (EdgeManagerInternal) LocalServices.getService(EdgeManagerInternal.class);
            PowerManager pm = (PowerManager) this.mContext.getSystemService(Context.POWER_SERVICE);
            this.mScreenBrightnessSettingMinimum = pm.getMinimumScreenBrightnessSetting();
            this.mScreenBrightnessSettingMaximum = pm.getMaximumScreenBrightnessSetting();
            this.mScreenBrightnessSettingDefault = pm.getDefaultScreenBrightnessSetting();
            this.mScreenExtendedBrightnessMaximum = pm.getMaximumScreenBrightnessExtended();
            SensorManager sensorManager = new SystemSensorManager(this.mContext, this.mHandler.getLooper());
            this.mIsSupportedLightSensor = sensorManager.getDefaultSensor(5) != null;
            this.mBatteryStats = BatteryStatsService.getService();
            this.mNotifier = new Notifier(Looper.getMainLooper(), this.mContext, this.mBatteryStats, createSuspendBlockerLocked("PowerManagerService.Broadcasts"), this.mPolicy);
            this.mWirelessChargerDetector = new WirelessChargerDetector(sensorManager, createSuspendBlockerLocked("PowerManagerService.WirelessChargerDetector"), this.mHandler);
            this.mSettingsObserver = new SettingsObserver(this.mHandler);
            this.mLightsManager = (LightsManager) getLocalService(LightsManager.class);
            this.mAttentionLight = this.mLightsManager.getLight(5);
            this.mIsFolderType = this.mContext.getPackageManager().hasSystemFeature(PackageManager.SEM_FEATURE_FOLDER_TYPE);
            this.mIsDualScreenFolder = this.mContext.getPackageManager().hasSystemFeature(PackageManager.SEM_FEATURE_DUAL_SCREEN);
            this.mTouchkeyLightState = new InputDeviceLightState(2);
            if (this.mIsFolderType) {
                this.mKeyboardLightState = new InputDeviceLightState(1);
            }
            this.mDisplayManagerInternal.initPowerManagement(this.mDisplayPowerCallbacks, this.mHandler, sensorManager);
            try {
                ForegroundProfileObserver observer = new ForegroundProfileObserver();
                ActivityManager.getService().registerUserSwitchObserver(observer, TAG);
            } catch (RemoteException e) {
            }
            if (this.mFeatureCoverSysfs) {
                this.mCoverManager = new CoverManager(this.mContext);
                if (this.mCoverManager != null) {
                    this.mCoverManager.registerListener(this.mCoverStateListener);
                } else {
                    Slog.d(TAG, "mCoverManager is null!!!!");
                }
            }
            if (this.mContext.getPackageManager().hasSystemFeature("com.sec.feature.sensorhub")) {
                this.mWirelessChargerSContextManager = (SContextManager) this.mContext.getSystemService("scontext");
                if (this.mWirelessChargerSContextManager != null) {
                    this.mIsWirelessChargerSContextEnabled = this.mWirelessChargerSContextManager.isAvailableService(46);
                    Slog.d(TAG, "start : (mWirelessChargerSContextManager != null - mIsWirelessChargerSContextEnabled = " + this.mIsWirelessChargerSContextEnabled);
                }
            }
            readConfigurationLocked();
            updateSettingsLocked();
            this.mDirty |= 256;
            updatePowerStateLocked();
        }
        this.mSemHqmManager = (SemHqmManager) this.mContext.getSystemService(Context.HQM_SERVICE);
        this.mAlarmManager = (AlarmManager) this.mContext.getSystemService("alarm");
        if (this.mSemHqmManager != null && this.mAlarmManager != null) {
            Intent intent = new Intent(ACTION_PMS_LOGGING);
            PendingIntent loggingPendingIntent = PendingIntent.getBroadcast(this.mContext, 0, intent, 0);
            this.mAlarmManager.setInexactRepeating(2, SystemClock.elapsedRealtime() + 86400000, 86400000L, loggingPendingIntent);
        }
        ContentResolver resolver = this.mContext.getContentResolver();
        this.mConstants.start(resolver);
        this.mBatterySaverController.systemReady();
        this.mBatterySaverPolicy.systemReady();
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_ENABLED), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.SLEEP_TIMEOUT), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.Global.getUriFor("stay_on_while_plugged_in"), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.THEATER_MODE_ON), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DOZE_ALWAYS_ON), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DOUBLE_TAP_TO_WAKE), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.DEVICE_DEMO_MODE), false, this.mSettingsObserver, 0);
        resolver.registerContentObserver(Settings.System.getUriFor("button_key_light"), false, this.mSettingsObserver, -1);
        if (this.mIsFolderType) {
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.KEY_BACKLIGHT_TIMEOUT), false, this.mSettingsObserver, -1);
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.KEY_NIGHT_MODE), false, this.mSettingsObserver, -1);
        }
        resolver.registerContentObserver(Settings.System.getUriFor("intelligent_sleep_mode"), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.USER_ACTIVITY_TIMEOUT), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.System.getUriFor(OUTDOOR_MODE_SETTING), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ADAPTIVE_BRIGHTNESS), false, this.mSettingsObserver, -1);
        resolver.registerContentObserver(Settings.Secure.getUriFor("game_autobrightness_lock"), false, this.mSettingsObserver, -1);
        Settings.System.putInt(this.mContext.getContentResolver(), PMS_DB_BRIGHTNESS_ENABLE, 1);
        if (PowerManagerUtil.SEC_FEATURE_WAKEUP_WHEN_PLUG_CHANGED) {
            resolver.registerContentObserver(Settings.System.getUriFor(SETTINGS_AOD_MODE), false, this.mSettingsObserver, -1);
        }
        IVrManager vrManager = (IVrManager) getBinderService(Context.VR_SERVICE);
        if (vrManager != null) {
            try {
                vrManager.registerListener(this.mVrStateCallbacks);
            } catch (RemoteException e2) {
                Slog.e(TAG, "Failed to register VR mode state listener: " + e2);
            }
        }
        IGearVrManagerService vr = getBinderService("vr");
        if (vr != null) {
            try {
                vr.registerVrStateListener(this.mGearVrStateCallbacks);
            } catch (RemoteException e3) {
                android.util.Slog.e(TAG, "Failed to register GearVR state listener: " + e3);
            }
        }
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("vrmode_developer_mode"), true, this.mSettingsObserver);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.setPriority(1000);
        this.mContext.registerReceiver(new BatteryReceiver(), filter, null, this.mHandler);
        IntentFilter filter2 = new IntentFilter();
        filter2.addAction(Intent.ACTION_DREAMING_STARTED);
        filter2.addAction(Intent.ACTION_DREAMING_STOPPED);
        this.mContext.registerReceiver(new DreamReceiver(), filter2, null, this.mHandler);
        IntentFilter filter3 = new IntentFilter();
        filter3.addAction("android.intent.action.USER_SWITCHED");
        this.mContext.registerReceiver(new UserSwitchedReceiver(), filter3, null, this.mHandler);
        IntentFilter filter4 = new IntentFilter();
        filter4.addAction(Intent.ACTION_DOCK_EVENT);
        this.mContext.registerReceiver(new DockReceiver(), filter4, null, this.mHandler);
        IntentFilter filter5 = new IntentFilter();
        filter5.addAction(Intent.ACTION_SHUTDOWN);
        this.mContext.registerReceiver(new ShutdownReceiver(), filter5, null, this.mHandler);
        IntentFilter filter6 = new IntentFilter();
        filter6.addAction("com.samsung.intent.action.WIFI_DISPLAY_SOURCE_STATE");
        filter6.addAction("com.samsung.intent.action.GOOGLE_CAST_MIRRORING_CONNECTION_CHANGED");
        this.mContext.registerReceiver(new SmartViewReceiver(), filter6, null, this.mHandler);
        IntentFilter filter7 = new IntentFilter();
        filter7.addAction(ACTION_FTA_ON);
        filter7.addAction(ACTION_FTA_OFF);
        this.mContext.registerReceiver(new FTAModeChangeReceiver(), filter7, null, this.mHandler);
        if (PowerManagerUtil.Sales.NORTH_AMERICA) {
            this.mContext.registerReceiver(this.mSipIntentReceiver, new IntentFilter(RESPONSE_AXT9INFO));
        }
        IntentFilter filter8 = new IntentFilter();
        filter8.addAction(ACTION_PMS_LOGGING);
        this.mContext.registerReceiver(new PmsLoggingReceiver(), filter8, null, this.mHandler);
        IntentFilter timeoutFilter = new IntentFilter();
        timeoutFilter.addAction(ACTION_KNOX_LOCK_TIMEOUT);
        this.mContext.registerReceiverAsUser(new KnoxTimeoutReceiver(), UserHandle.ALL, timeoutFilter, null, null);
        this.mKeyguardManager = (KeyguardManager) this.mContext.getSystemService(Context.KEYGUARD_SERVICE);
        this.mTrustManager = (TrustManager) this.mContext.getSystemService(TrustManager.class);
        this.mNoUserActivityIntent = new Intent("com.samsung.android.knox.intent.action.NO_USER_ACTIVITY");
        this.mUserActivityIntent = new Intent("com.samsung.android.knox.intent.action.USER_ACTIVITY");
        this.mNoUserActivityIntentLegacy = new Intent("com.sec.action.NO_USER_ACTIVITY");
        this.mUserActivityIntentLegacy = new Intent("com.sec.action.USER_ACTIVITY");
        this.mNoUserActivitySent = false;
        SemDesktopModeManager desktopModeManager = (SemDesktopModeManager) this.mContext.getSystemService(Context.SEM_DESKTOP_MODE_SERVICE);
        if (desktopModeManager != null) {
            desktopModeManager.registerListener(new SemDesktopModeManager.DesktopModeListener() { // from class: com.android.server.power.PowerManagerService.1
                public void onDesktopModeStateChanged(SemDesktopModeState state) {
                    synchronized (PowerManagerService.this.mLock) {
                        boolean wasDualViewMode = PowerManagerService.this.mIsDualViewMode;
                        int displayType = state.getDisplayType();
                        int enableState = state.getEnabled();
                        PowerManagerService.this.mIsDualViewMode = displayType == 102 && (enableState == 4 || enableState == 3);
                        Slog.d(PowerManagerService.TAG, "Dex display type: " + displayType + ", enabled: " + enableState + ", dual view: " + wasDualViewMode + " -> " + PowerManagerService.this.mIsDualViewMode);
                        if (wasDualViewMode != PowerManagerService.this.mIsDualViewMode) {
                            PowerManagerService.this.mInternalWakefulnessForDex = 1;
                            PowerManagerService.this.mIsDualViewModeChanging = true;
                            PowerManagerService.access$2176(PowerManagerService.this, 2);
                            PowerManagerService.this.userActivityNoUpdateLocked(SystemClock.uptimeMillis(), 0, 3072, 1000);
                            PowerManagerService.this.updatePowerStateLocked();
                        } else {
                            PowerManagerService.this.mIsDualViewModeChanging = false;
                        }
                    }
                }
            });
        }
        this.mDexScreenOffTimeoutObserver = new DexScreenOffTimeoutObserver(this.mHandler);
        resolver.registerContentObserver(Uri.withAppendedPath(DEX_SETTINGS_URI, "timeout_dex"), true, this.mDexScreenOffTimeoutObserver);
        this.mInternalDisplayOffByPowerKeyIntent = new Intent(ACTION_INTERNAL_DISPLAY_OFF_BY_POWERKEY);
        this.mInternalDisplayOffByPowerKeyIntent.addFlags(KeymasterDefs.KM_ULONG);
    }

    private void readConfigurationLocked() {
        Resources resources = this.mContext.getResources();
        this.mDecoupleHalAutoSuspendModeFromDisplayConfig = true;
        this.mDecoupleHalInteractiveModeFromDisplayConfig = resources.getBoolean(17957020);
        this.mWakeUpWhenPluggedOrUnpluggedConfig = true;
        this.mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig = resources.getBoolean(17956892);
        this.mSuspendWhenScreenOffDueToProximityConfig = true;
        this.mDreamsSupportedConfig = resources.getBoolean(17956951);
        this.mDreamsEnabledByDefaultConfig = resources.getBoolean(17956949);
        this.mDreamsActivatedOnSleepByDefaultConfig = resources.getBoolean(17956948);
        this.mDreamsActivatedOnDockByDefaultConfig = resources.getBoolean(17956947);
        this.mDreamsEnabledOnBatteryConfig = resources.getBoolean(17956950);
        this.mDreamsBatteryLevelMinimumWhenPoweredConfig = resources.getInteger(17694807);
        this.mDreamsBatteryLevelMinimumWhenNotPoweredConfig = resources.getInteger(17694806);
        this.mDreamsBatteryLevelDrainCutoffConfig = resources.getInteger(17694805);
        this.mDozeAfterScreenOff = resources.getBoolean(17956944);
        this.mMinimumScreenOffTimeoutConfig = resources.getInteger(17694864);
        this.mMaximumScreenDimDurationConfig = 20000L;
        this.mMaximumScreenDimRatioConfig = 0.333f;
        this.mSupportsDoubleTapWakeConfig = resources.getBoolean(17957054);
    }

    private void updateSettingsLocked() {
        String str;
        ContentResolver resolver = this.mContext.getContentResolver();
        this.mDreamsEnabledSetting = Settings.Secure.getIntForUser(resolver, Settings.Secure.SCREENSAVER_ENABLED, this.mDreamsEnabledByDefaultConfig ? 1 : 0, -2) != 0;
        this.mDreamsActivateOnSleepSetting = Settings.Secure.getIntForUser(resolver, Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, this.mDreamsActivatedOnSleepByDefaultConfig ? 1 : 0, -2) != 0;
        this.mDreamsActivateOnDockSetting = Settings.Secure.getIntForUser(resolver, Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK, this.mDreamsActivatedOnDockByDefaultConfig ? 1 : 0, -2) != 0;
        this.mScreenOffTimeoutSetting = Settings.System.getIntForUser(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 15000, -2);
        this.mSleepTimeoutSetting = Settings.Secure.getIntForUser(resolver, Settings.Secure.SLEEP_TIMEOUT, -1, -2);
        this.mStayOnWhilePluggedInSetting = Settings.Global.getInt(resolver, "stay_on_while_plugged_in", 1);
        this.mTheaterModeEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), Settings.Global.THEATER_MODE_ON, 0) == 1;
        this.mAlwaysOnEnabled = this.mAmbientDisplayConfiguration.alwaysOnEnabled(-2);
        this.mTouchKeyOffTimeoutSetting = Settings.System.getIntForUser(resolver, "button_key_light", 1500, -2);
        if (this.mIsFolderType) {
            this.mKeyboardOffTimeoutSetting = Settings.System.getIntForUser(resolver, Settings.System.KEY_BACKLIGHT_TIMEOUT, 3000, -2);
            this.mKeyLedOffNightModeSetting = Settings.System.getIntForUser(resolver, Settings.System.KEY_NIGHT_MODE, 0, -2);
        }
        boolean prevSmartStayEnabledSetting = this.mSmartStayEnabledSetting;
        this.mSmartStayEnabledSetting = Settings.System.getIntForUser(resolver, "intelligent_sleep_mode", 0, -2) != 0;
        if (prevSmartStayEnabledSetting != this.mSmartStayEnabledSetting) {
            setSmartStayLocked();
        }
        boolean prevSmartStayEnabledSetting2 = this.mOutdoorModeSetting;
        this.mOutdoorModeSetting = Settings.System.getIntForUser(resolver, OUTDOOR_MODE_SETTING, 0, -2) == 1;
        if (prevSmartStayEnabledSetting2 != this.mOutdoorModeSetting && this.mOutdoorModeSetting) {
            this.mLastOutdoorModeEnblaedTime = SystemClock.elapsedRealtime();
            Slog.d(TAG, "[api] updateSettingsLocked : Update OutdoorMode enabled time");
        }
        this.mUserActivityTimeoutSetting = Settings.System.getIntForUser(resolver, Settings.System.USER_ACTIVITY_TIMEOUT, 0, -2);
        if (PowerManagerUtil.CAMERA_ADAPTIVE_BRIGHTNESS_SUPPORTED) {
            this.mAdaptiveBrightnessCameraModeSetting = Settings.System.getInt(resolver, Settings.System.ADAPTIVE_BRIGHTNESS, 1) != 0;
        } else {
            this.mAdaptiveBrightnessCameraModeSetting = false;
        }
        if (this.mSupportsDoubleTapWakeConfig) {
            boolean doubleTapWakeEnabled = Settings.Secure.getIntForUser(resolver, Settings.Secure.DOUBLE_TAP_TO_WAKE, 0, -2) != 0;
            if (doubleTapWakeEnabled != this.mDoubleTapWakeEnabled) {
                this.mDoubleTapWakeEnabled = doubleTapWakeEnabled;
                nativeSetFeature(1, this.mDoubleTapWakeEnabled ? 1 : 0);
            }
        }
        String retailDemoValue = UserManager.isDeviceInDemoMode(this.mContext) ? "1" : "0";
        if (!retailDemoValue.equals(SystemProperties.get(SYSTEM_PROPERTY_RETAIL_DEMO_ENABLED))) {
            SystemProperties.set(SYSTEM_PROPERTY_RETAIL_DEMO_ENABLED, retailDemoValue);
        }
        this.mScreenBrightnessModeSetting = Settings.System.getIntForUser(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0, -2);
        this.mGameAutobrightnessLocked = Settings.Secure.getIntForUser(resolver, "game_autobrightness_lock", 0, -2) != 0;
        if (PowerManagerUtil.SEC_FEATURE_WAKEUP_WHEN_PLUG_CHANGED) {
            this.mAodMode = Settings.System.getIntForUser(resolver, SETTINGS_AOD_MODE, 0, -2) != 0;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[api] updateSettingsLocked : mDreamsEnabledSetting: ");
        sb.append(this.mDreamsEnabledSetting);
        sb.append(" mDreamsActivateOnSleepSetting: ");
        sb.append(this.mDreamsActivateOnSleepSetting);
        sb.append(" mDreamsActivateOnDockSetting: ");
        sb.append(this.mDreamsActivateOnDockSetting);
        sb.append(" mScreenOffTimeoutSetting: ");
        sb.append(this.mScreenOffTimeoutSetting);
        sb.append(" mSleepTimeoutSetting: ");
        sb.append(this.mSleepTimeoutSetting);
        sb.append(" mStayOnWhilePluggedInSetting: ");
        sb.append(this.mStayOnWhilePluggedInSetting);
        sb.append(" mTheaterModeEnabled: ");
        sb.append(this.mTheaterModeEnabled);
        sb.append(" mAlwaysOnEnabled: ");
        sb.append(this.mAlwaysOnEnabled);
        sb.append(" mSmartStayEnabledSetting: ");
        sb.append(this.mSmartStayEnabledSetting);
        sb.append(" mDoubleTapWakeEnabled: ");
        sb.append(this.mDoubleTapWakeEnabled);
        sb.append(" retailDemoValue: ");
        sb.append(retailDemoValue);
        sb.append(" mScreenBrightnessModeSetting: ");
        sb.append(this.mScreenBrightnessModeSetting);
        sb.append(" mGameAutobrightnessLocked: ");
        sb.append(this.mGameAutobrightnessLocked);
        if (PowerManagerUtil.SEC_FEATURE_WAKEUP_WHEN_PLUG_CHANGED) {
            str = " mAodMode: " + this.mAodMode;
        } else {
            str = "";
        }
        sb.append(str);
        Slog.d(TAG, sb.toString());
        this.mDirty |= 32;
    }

    private void postAfterBootCompleted(Runnable r) {
        if (this.mBootCompleted) {
            BackgroundThread.getHandler().post(r);
            return;
        }
        Slog.d(TAG, "Delaying runnable until system is booted");
        this.mBootCompletedRunnables = (Runnable[]) ArrayUtils.appendElement(Runnable.class, this.mBootCompletedRunnables, r);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleSettingsChangedLocked() {
        updateSettingsLocked();
        updatePowerStateLocked();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void acquireWakeLockInternal(IBinder lock, int flags, String tag, String packageName, WorkSource ws, String historyTag, int uid, int pid, boolean notificationEnabled) {
        Object obj;
        int index;
        WakeLock wakeLock;
        WakeLock wakeLock2;
        int index2;
        Object obj2 = this.mLock;
        synchronized (obj2) {
            try {
                int index3 = findWakeLockIndexLocked(lock);
                if (index3 >= 0) {
                    wakeLock = this.mWakeLocks.get(index3);
                    if (wakeLock.hasSameProperties(flags, tag, ws, uid, pid)) {
                        index2 = index3;
                    } else {
                        index2 = index3;
                        notifyWakeLockChangingLocked(wakeLock, flags, tag, packageName, uid, pid, ws, historyTag);
                        wakeLock.updateProperties(flags, tag, packageName, ws, historyTag, uid, pid);
                    }
                    wakeLock2 = null;
                    obj = obj2;
                    index = uid;
                } else {
                    UidState state = this.mUidState.get(uid);
                    if (state == null) {
                        state = new UidState(uid);
                        state.mProcState = 19;
                        this.mUidState.put(uid, state);
                    }
                    UidState state2 = state;
                    state2.mNumWakeLocks++;
                    obj = obj2;
                    index = uid;
                    try {
                        WakeLock wakeLock3 = new WakeLock(lock, flags, tag, packageName, ws, historyTag, uid, pid, state2);
                        try {
                            try {
                                try {
                                    lock.linkToDeath(wakeLock3, 0);
                                    this.mWakeLocks.add(wakeLock3);
                                    setWakeLockDisabledStateLocked(wakeLock3);
                                    wakeLock = wakeLock3;
                                    wakeLock2 = 1;
                                } catch (RemoteException e) {
                                    throw new IllegalArgumentException("Wake lock is already dead.");
                                }
                            } catch (Throwable th) {
                                ex = th;
                                throw ex;
                            }
                        } catch (Throwable th2) {
                            ex = th2;
                            throw ex;
                        }
                    } catch (Throwable th3) {
                        ex = th3;
                        throw ex;
                    }
                }
                int i = wakeLock.mFlags & 65535;
                if (i != 1 && (i == 6 || i == 10 || i == 26 || i == 32 || i == 64 || i == 128)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[api] acquire WakeLock flags=0x");
                    sb.append(Integer.toHexString(flags));
                    sb.append(" tag=");
                    try {
                        sb.append(tag);
                        sb.append(" uid=");
                        sb.append(index);
                        sb.append(" pid=");
                        try {
                            sb.append(pid);
                            sb.append(" ws=");
                        } catch (Throwable th4) {
                            ex = th4;
                        }
                        try {
                            sb.append(ws);
                            sb.append(" pkg=");
                            try {
                                sb.append(packageName);
                                Slog.d(TAG, sb.toString());
                            } catch (Throwable th5) {
                                ex = th5;
                                throw ex;
                            }
                        } catch (Throwable th6) {
                            ex = th6;
                            throw ex;
                        }
                    } catch (Throwable th7) {
                        ex = th7;
                        throw ex;
                    }
                }
                int last = this.mWakefulness;
                applyWakeLockFlagsOnAcquireLocked(wakeLock, index, notificationEnabled);
                this.mDirty |= 1;
                if (!PowerManagerInternal.isInteractive(last) && this.mWakefulness == 1 && UserHandle.isApp(uid) && isScreenLock(wakeLock)) {
                    setNotiLightScreenTimeoutOverride(NOTIFICATION_SCREEN_OFF_TIME);
                }
                updatePowerStateLocked();
                if (wakeLock2 != null) {
                    notifyWakeLockAcquiredLocked(wakeLock);
                }
            } catch (Throwable th8) {
                ex = th8;
                obj = obj2;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setProximityDebounceTimeInternal(IBinder lock, int positive, int negative) {
        synchronized (this.mLock) {
            int index = findWakeLockIndexLocked(lock);
            WakeLock wakeLock = this.mWakeLocks.get(index);
            wakeLock.mProximityPositiveDebounce = positive;
            wakeLock.mProximityNegativeDebounce = negative;
            this.mDirty |= 1;
            updatePowerStateLocked();
        }
    }

    private static boolean isScreenLock(WakeLock wakeLock) {
        int i = wakeLock.mFlags & 65535;
        if (i == 6 || i == 10 || i == 26) {
            return true;
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isScreenLock(int flags) {
        int i = 65535 & flags;
        if (i == 6 || i == 10 || i == 26) {
            return true;
        }
        return false;
    }

    private void applyWakeLockFlagsOnAcquireLocked(WakeLock wakeLock, int uid, boolean notificationEnabled) {
        String opPackageName;
        int opUid;
        if ((wakeLock.mFlags & 268435456) != 0 && isScreenLock(wakeLock)) {
            if (wakeLock.mWorkSource != null && wakeLock.mWorkSource.getName(0) != null) {
                opPackageName = wakeLock.mWorkSource.getName(0);
                opUid = wakeLock.mWorkSource.get(0);
            } else {
                opPackageName = wakeLock.mPackageName;
                opUid = wakeLock.mWorkSource != null ? wakeLock.mWorkSource.get(0) : wakeLock.mOwnerUid;
            }
            if (!this.mWakeUpPreventionDisabled) {
                if (opUid >= 10000 && !notificationEnabled) {
                    Slog.d(TAG, "Screen__On : Cancel (notifications are disabled : " + opPackageName + Separators.RPAREN);
                    return;
                } else if (isWakeUpPreventionNeededForEdgeLightingLocked(wakeLock)) {
                    Slog.d(TAG, "applyWakeLockFlagsOnAcquireLocked return by isWakeUpPreventionNeededForEdgeLighting");
                    return;
                }
            }
            this.mLastWakeUpReason = 14;
            this.mScreenOnReason = " wl: " + wakeLock.mTag;
            wakeUpNoUpdateLocked(SystemClock.uptimeMillis(), wakeLock.mTag, opUid, opPackageName, opUid);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void releaseWakeLockInternal(IBinder lock, int flags) {
        synchronized (this.mLock) {
            int index = findWakeLockIndexLocked(lock);
            if (index < 0) {
                return;
            }
            WakeLock wakeLock = this.mWakeLocks.get(index);
            if ((flags & 1) != 0 && (!this.mIsDualScreenFolder || !this.mIsLidClosed)) {
                this.mRequestWaitForNegativeProximity = true;
            }
            int i = wakeLock.mFlags & 65535;
            if (i != 1 && (i == 6 || i == 10 || i == 26 || i == 32 || i == 64 || i == 128)) {
                Slog.d(TAG, "[api] release WakeLock " + wakeLock + " (0x" + Integer.toHexString(flags) + Separators.RPAREN);
            }
            wakeLock.mLock.unlinkToDeath(wakeLock, 0);
            removeWakeLockLocked(wakeLock, index);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleWakeLockDeath(WakeLock wakeLock) {
        Slog.d(TAG, "[api] handleWakeLockDeath : release WakeLock : " + wakeLock);
        synchronized (this.mLock) {
            int index = this.mWakeLocks.indexOf(wakeLock);
            if (index < 0) {
                return;
            }
            removeWakeLockLocked(wakeLock, index);
        }
    }

    private void removeWakeLockLocked(WakeLock wakeLock, int index) {
        this.mWakeLocks.remove(index);
        UidState state = wakeLock.mUidState;
        state.mNumWakeLocks--;
        if (state.mNumWakeLocks <= 0 && state.mProcState == 19) {
            this.mUidState.remove(state.mUid);
        }
        notifyWakeLockReleasedLocked(wakeLock);
        applyWakeLockFlagsOnReleaseLocked(wakeLock);
        this.mPrevReleasedWakeLock = wakeLock.mTag;
        this.mDirty |= 1;
        updatePowerStateLocked();
    }

    private void applyWakeLockFlagsOnReleaseLocked(WakeLock wakeLock) {
        int flags;
        if ((wakeLock.mFlags & 536870912) != 0 && isScreenLock(wakeLock)) {
            Slog.d(TAG, "[api] applyWakeLockFlagsOnReleaseLocked : userActivityNoUpdateLocked is called : " + wakeLock);
            if (this.mIsDualViewMode && (wakeLock.mFlags & 65536) != 0) {
                flags = 1024;
            } else {
                flags = 1;
            }
            userActivityNoUpdateLocked(SystemClock.uptimeMillis(), 0, flags, wakeLock.mOwnerUid);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateWakeLockWorkSourceInternal(IBinder lock, WorkSource ws, String historyTag, int callingUid) {
        synchronized (this.mLock) {
            try {
                try {
                    int index = findWakeLockIndexLocked(lock);
                    try {
                        if (index >= 0) {
                            try {
                                WakeLock wakeLock = this.mWakeLocks.get(index);
                                if (!wakeLock.hasSameWorkSource(ws)) {
                                    notifyWakeLockChangingLocked(wakeLock, wakeLock.mFlags, wakeLock.mTag, wakeLock.mPackageName, wakeLock.mOwnerUid, wakeLock.mOwnerPid, ws, historyTag);
                                    wakeLock.mHistoryTag = historyTag;
                                    wakeLock.updateWorkSource(ws);
                                }
                                return;
                            } catch (Throwable th) {
                                th = th;
                                throw th;
                            }
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append("Wake lock not active: ");
                        try {
                            sb.append(lock);
                            sb.append(" from uid ");
                            sb.append(callingUid);
                            throw new IllegalArgumentException(sb.toString());
                        } catch (Throwable th2) {
                            th = th2;
                            throw th;
                        }
                    } catch (Throwable th3) {
                        th = th3;
                    }
                } catch (Throwable th4) {
                    th = th4;
                }
            } catch (Throwable th5) {
                th = th5;
            }
        }
    }

    private int findWakeLockIndexLocked(IBinder lock) {
        int count = this.mWakeLocks.size();
        for (int i = 0; i < count; i++) {
            if (this.mWakeLocks.get(i).mLock == lock) {
                return i;
            }
        }
        return -1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyWakeLockAcquiredLocked(WakeLock wakeLock) {
        if (this.mSystemReady && !wakeLock.mDisabled) {
            wakeLock.mNotifiedAcquired = true;
            this.mNotifier.onWakeLockAcquired(wakeLock.mFlags, wakeLock.mTag, wakeLock.mPackageName, wakeLock.mOwnerUid, wakeLock.mOwnerPid, wakeLock.mWorkSource, wakeLock.mHistoryTag);
            restartNofifyLongTimerLocked(wakeLock);
        }
    }

    private void enqueueNotifyLongMsgLocked(long time) {
        this.mNotifyLongScheduled = time;
        Message msg = this.mHandler.obtainMessage(4);
        msg.setAsynchronous(true);
        this.mHandler.sendMessageAtTime(msg, time);
    }

    private void restartNofifyLongTimerLocked(WakeLock wakeLock) {
        wakeLock.mAcquireTime = SystemClock.uptimeMillis();
        if ((wakeLock.mFlags & 65535) == 1 && this.mNotifyLongScheduled == 0) {
            enqueueNotifyLongMsgLocked(wakeLock.mAcquireTime + 60000);
        }
    }

    private void notifyWakeLockLongStartedLocked(WakeLock wakeLock) {
        if (this.mSystemReady && !wakeLock.mDisabled) {
            wakeLock.mNotifiedLong = true;
            this.mNotifier.onLongPartialWakeLockStart(wakeLock.mTag, wakeLock.mOwnerUid, wakeLock.mWorkSource, wakeLock.mHistoryTag);
        }
    }

    private void notifyWakeLockLongFinishedLocked(WakeLock wakeLock) {
        if (wakeLock.mNotifiedLong) {
            wakeLock.mNotifiedLong = false;
            this.mNotifier.onLongPartialWakeLockFinish(wakeLock.mTag, wakeLock.mOwnerUid, wakeLock.mWorkSource, wakeLock.mHistoryTag);
        }
    }

    private void notifyWakeLockChangingLocked(WakeLock wakeLock, int flags, String tag, String packageName, int uid, int pid, WorkSource ws, String historyTag) {
        if (this.mSystemReady && wakeLock.mNotifiedAcquired) {
            if ((wakeLock.mFlags & 65535) == 1) {
                long time = SystemClock.uptimeMillis() - wakeLock.mAcquireTime;
                Object[] objArr = new Object[2];
                objArr[0] = Long.valueOf(time);
                StringBuilder sb = new StringBuilder();
                sb.append(wakeLock.mTag);
                sb.append(":");
                sb.append(wakeLock.mPackageName);
                sb.append(wakeLock.mDisabled ? "(disabled)" : "");
                objArr[1] = sb.toString();
                EventLog.writeEvent((int) EventLogTags.POWER_PARTIAL_WAKE_STATE, objArr);
            }
            this.mNotifier.onWakeLockChanging(wakeLock.mFlags, wakeLock.mTag, wakeLock.mPackageName, wakeLock.mOwnerUid, wakeLock.mOwnerPid, wakeLock.mWorkSource, wakeLock.mHistoryTag, flags, tag, packageName, uid, pid, ws, historyTag);
            notifyWakeLockLongFinishedLocked(wakeLock);
            restartNofifyLongTimerLocked(wakeLock);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyWakeLockReleasedLocked(WakeLock wakeLock) {
        if (this.mSystemReady && wakeLock.mNotifiedAcquired) {
            if ((wakeLock.mFlags & 65535) == 1) {
                long time = SystemClock.uptimeMillis() - wakeLock.mAcquireTime;
                Object[] objArr = new Object[2];
                objArr[0] = Long.valueOf(time);
                StringBuilder sb = new StringBuilder();
                sb.append(wakeLock.mTag);
                sb.append(":");
                sb.append(wakeLock.mPackageName);
                sb.append(wakeLock.mDisabled ? "(disabled)" : "");
                objArr[1] = sb.toString();
                EventLog.writeEvent((int) EventLogTags.POWER_PARTIAL_WAKE_STATE, objArr);
            }
            wakeLock.mNotifiedAcquired = false;
            wakeLock.mAcquireTime = 0L;
            this.mNotifier.onWakeLockReleased(wakeLock.mFlags, wakeLock.mTag, wakeLock.mPackageName, wakeLock.mOwnerUid, wakeLock.mOwnerPid, wakeLock.mWorkSource, wakeLock.mHistoryTag);
            notifyWakeLockLongFinishedLocked(wakeLock);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isWakeLockLevelSupportedInternal(int level) {
        synchronized (this.mLock) {
            boolean z = true;
            try {
                if (level != 1 && level != 6 && level != 10 && level != 26) {
                    if (level == 32) {
                        if (!this.mSystemReady || !this.mDisplayManagerInternal.isProximitySensorAvailable()) {
                            z = false;
                        }
                        return z;
                    } else if (level != 64 && level != 128) {
                        return false;
                    }
                }
                return true;
            } finally {
            }
        }
    }

    private void userActivityFromNative(long eventTime, int event, int flags) {
        this.mNativeUserActivityCount++;
        if (this.mNativeUserActivityCount % 10 == 0) {
            Slog.d(TAG, "[api] userActivityFromNative : " + this.mNativeUserActivityCount + " (event: " + event + " flags: " + flags + ") eventTime = " + eventTime);
            if (this.mNativeUserActivityCount >= 5000) {
                this.mNativeUserActivityCount = 0;
            }
        }
        userActivityInternal(eventTime, event, flags, 1000);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void userActivityInternal(long eventTime, int event, int flags, int uid) {
        synchronized (this.mLock) {
            if (event != 0) {
                try {
                    setNotiLightScreenTimeoutOverride(-1L);
                } catch (Throwable th) {
                    throw th;
                }
            }
            if (userActivityNoUpdateLocked(eventTime, event, flags, uid)) {
                updatePowerStateLocked();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean userActivityNoUpdateLocked(long eventTime, int event, int flags, int uid) {
        if (eventTime < this.mLastSleepTime || eventTime < this.mLastWakeTime || !this.mBootCompleted || !this.mSystemReady) {
            return false;
        }
        Trace.traceBegin(131072L, "userActivity");
        try {
            if (eventTime > this.mLastInteractivePowerHintTime) {
                powerHintInternal(2, 0);
                this.mLastInteractivePowerHintTime = eventTime;
            }
            if (event == 4) {
                this.mNotifier.onUserActivity(1, uid);
            } else {
                this.mNotifier.onUserActivity(event, uid);
            }
            if (this.mUserInactiveOverrideFromWindowManager) {
                Slog.d(TAG, "userActivityNoUpdateLocked: mUserInactiveOverrideFromWindowManager : false");
                this.mUserInactiveOverrideFromWindowManager = false;
                this.mOverriddenTimeout = -1L;
            }
            if (this.mWakefulness != 0 && this.mWakefulness != 3 && (flags & 2) == 0) {
                maybeUpdateForegroundProfileLastActivityLocked(eventTime);
                if ((flags & 1) != 0) {
                    if (eventTime > this.mLastUserActivityTimeNoChangeLights && eventTime > this.mLastUserActivityTime) {
                        this.mLastUserActivityTimeNoChangeLights = eventTime;
                        this.mDirty = 4 | this.mDirty;
                        if (event == 1) {
                            this.mDirty |= 4096;
                        }
                        return true;
                    }
                } else if (eventTime > this.mLastUserActivityTime) {
                    if (!this.mIsDualViewMode || (flags & 1024) == 0) {
                        this.mLastUserActivityTime = eventTime;
                    } else {
                        this.mLastUserActivityTimeForDex = eventTime;
                        if ((flags & 2048) != 0) {
                            this.mLastUserActivityTime = eventTime;
                        }
                    }
                    this.mDirty |= 4;
                    if (event == 1) {
                        this.mDirty |= 4096;
                    }
                    if (event != 4 && (event != 2 || !PowerManagerUtil.Sales.NORTH_AMERICA || this.mIsSipVisible)) {
                        if (event == 1 && this.mIsFolderType) {
                            this.mDirty |= 65536;
                        }
                        if (this.mUserActivityTimeoutSetting > 0 && (event != 0 || (this.mDirty & 16) != 0)) {
                            Slog.e(TAG, "call changes for MDM");
                            setUserActivityTimeoutLocked(eventTime);
                        }
                        return true;
                    }
                    this.mDirty |= 16384;
                    if (this.mUserActivityTimeoutSetting > 0) {
                        Slog.e(TAG, "call changes for MDM");
                        setUserActivityTimeoutLocked(eventTime);
                    }
                    return true;
                }
                return false;
            }
            return false;
        } finally {
            Trace.traceEnd(131072L);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void maybeUpdateForegroundProfileLastActivityLocked(long eventTime) {
        ProfilePowerState profile = this.mProfilePowerState.get(this.mForegroundProfile);
        if (this.mForegroundProfile != 0 && profile == null && SemPersonaManager.isKnoxId(this.mForegroundProfile)) {
            updateKnoxTimeoutValue(this.mForegroundProfile);
            Slog.d(TAG, "create ProfilePowerState for Workspace");
        } else if (profile != null && eventTime > profile.mLastUserActivityTime) {
            profile.mLastUserActivityTime = eventTime;
            if (SemPersonaManager.isKnoxId(this.mForegroundProfile) && this.mAlarmManager != null) {
                if (profile.mScreenOffTimeoutForUser > 0 || (profile.mScreenOffTimeout != Long.MAX_VALUE && profile.mScreenOffTimeout > 0)) {
                    this.mKnoxHandler.sendMessage(this.mKnoxHandler.obtainMessage(1, profile.mUserId, 0));
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void wakeUpWithReasonInternal(long eventTime, int event, int uid, int flags) {
        synchronized (this.mLock) {
            this.mLastWakeUpReason = event;
            if (wakeUpNoUpdateLocked(eventTime, getWakeUpReasonString(event), uid, this.mContext.getOpPackageName(), 1000, flags)) {
                updatePowerStateLocked();
            }
        }
    }

    private String getWakeUpReasonString(int event) {
        switch (event) {
            case 0:
                return "WAKE_UP_REASON_UNKNOWN";
            case 1:
                return "WAKE_UP_REASON_KEY";
            case 2:
                return "WAKE_UP_REASON_MOTION";
            case 3:
                return "WAKE_UP_REASON_CAMERA_LENS_COVER";
            case 4:
                return "WAKE_UP_REASON_SPEN";
            case 5:
                return "WAKE_UP_REASON_LID_SWITCH";
            case 6:
                return "WAKE_UP_REASON_COVER_OPEN";
            case 7:
                return "WAKE_UP_REASON_GESTURE";
            case 8:
                return "WAKE_UP_REASON_DREAM";
            case 9:
                return "WAKE_UP_REASON_DOCK";
            case 10:
                return "WAKE_UP_REASON_HDMI";
            case 11:
                return "WAKE_UP_REASON_POWER";
            case 12:
                return "WAKE_UP_REASON_EAR_JACK";
            case 13:
                return "WAKE_UP_REASON_SENSOR_CA";
            case 14:
                return "WAKE_UP_REASON_WAKE_LOCK";
            case 15:
                return "WAKE_UP_REASON_SANDMAN";
            case 16:
                return "WAKE_UP_REASON_PROXIMITY";
            case 17:
                return "WAKE_UP_REASON_APPLICATION";
            case 18:
                return "WAKE_UP_REASON_FINGER";
            case 19:
                return "WAKE_UP_REASON_BIXBY";
            default:
                return "UNKOWN";
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void wakeUpInternal(long eventTime, String reason, int uid, String opPackageName, int opUid) {
        synchronized (this.mLock) {
            this.mLastWakeUpReason = 0;
            if (wakeUpNoUpdateLocked(eventTime, reason, uid, opPackageName, opUid)) {
                updatePowerStateLocked();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean wakeUpNoUpdateLocked(long eventTime, String reason, int reasonUid, String opPackageName, int opUid) {
        return wakeUpNoUpdateLocked(eventTime, reason, reasonUid, opPackageName, opUid, 0);
    }

    private boolean wakeUpNoUpdateLocked(long eventTime, final String reason, final int reasonUid, final String opPackageName, final int opUid, int flags) {
        long j;
        if (this.mProximityPositive && (this.mLastWakeUpReason == 1 || !shouldUseProximitySensorLocked())) {
            this.mWakeUpEvenThoughProximityPositive = true;
        }
        if (eventTime < this.mLastSleepTime || !((this.mWakefulness != 1 || this.mWakeUpEvenThoughProximityPositive) && this.mBootCompleted && this.mSystemReady)) {
            if (this.mIsDualViewMode && this.mInternalWakefulnessForDex == 0 && (flags & 1) == 0 && this.mWakefulness == 1) {
                this.mDirty |= 2;
                Slog.d(TAG, "wakeUpNoUpdateLocked: turning on default display only, reason=" + this.mLastWakeUpReason);
                this.mInternalWakefulnessForDex = 1;
                userActivityNoUpdateLocked(eventTime, 0, 0, reasonUid);
                return true;
            }
            return false;
        }
        Slog.i(TAG, "!@Screen__On  - " + this.mScreenOnOffCount + " : " + this.mScreenOnReason + " (" + this.mLastWakeUpReason + Separators.RPAREN);
        this.mScreenOnReason = "";
        this.mScreenOffReason = "";
        this.mScreenOnOffCount = this.mScreenOnOffCount + 1;
        boolean isWakeUpPreventionNeeded = false;
        if (!this.mWakeUpPreventionDisabled) {
            if (isWakeUpPreventionNeededLocked()) {
                isWakeUpPreventionNeeded = true;
            }
            if (!isWakeUpPreventionNeeded && this.mEdgeInternal != null) {
                isWakeUpPreventionNeeded = this.mEdgeInternal.showForWakeUp(opPackageName, opUid);
            }
            if (isWakeUpPreventionNeeded) {
                this.mLastWakeUpReason = 0;
                return true;
            }
        }
        final int reasonNum = this.mLastWakeUpReason;
        this.mHandlerPmsMisc.post(new Runnable() { // from class: com.android.server.power.PowerManagerService.2
            @Override // java.lang.Runnable
            public void run() {
                ActivityManager am = (ActivityManager) PowerManagerService.this.mContext.getSystemService(Context.ACTIVITY_SERVICE);
                String topActivity = null;
                String topPackage = null;
                List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                if (!tasks.isEmpty()) {
                    topActivity = tasks.get(0).topActivity.getClassName();
                    topPackage = tasks.get(0).topActivity.getPackageName();
                }
                long nowRTC = System.currentTimeMillis();
                SimpleDateFormat mTimeFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
                String time = mTimeFormat.format(new Date(nowRTC));
                PowerManagerService.mWakeUpHistory.append(new WakeUpHistory(reasonUid, nowRTC, time, reasonNum, reason, opPackageName, topActivity, topPackage));
            }
        });
        Trace.asyncTraceBegin(131072L, TRACE_SCREEN_ON, 0);
        Trace.traceBegin(131072L, "wakeUp");
        try {
            int i = this.mWakefulness;
            if (i == 0) {
                Slog.i(TAG, "Waking up from sleep (uid=" + reasonUid + " reason=" + reason + ")...");
            } else {
                switch (i) {
                    case 2:
                        Slog.i(TAG, "Waking up from dream (uid=" + reasonUid + " reason=" + reason + ")...");
                        break;
                    case 3:
                        Slog.i(TAG, "Waking up from dozing (uid=" + reasonUid + " reason=" + reason + ")...");
                        break;
                }
            }
            this.mLastWakeTime = eventTime;
            this.mLastScreenWakeTime = this.mLastWakeTime;
            setWakefulnessLocked(1, this.mLastWakeUpReason);
            this.mNotifier.onWakeUp(reason, reasonUid, opPackageName, opUid);
            if (this.mPendingScreenStateChangeStartedByProximity) {
                this.mNotifier.onScreenStateChangeStartedByProximity(false);
                this.mPendingScreenStateChangeStartedByProximity = false;
            }
            try {
                if (this.mIsDualViewMode) {
                    if ((flags & 1) != 0) {
                        userActivityNoUpdateLocked(eventTime, 0, 1024, reasonUid);
                    } else {
                        this.mInternalWakefulnessForDex = 1;
                        userActivityNoUpdateLocked(eventTime, 0, 0, reasonUid);
                    }
                } else {
                    userActivityNoUpdateLocked(eventTime, 0, 0, reasonUid);
                }
                Trace.traceEnd(131072L);
                this.mLastWakeUpReason = 0;
                return true;
            } catch (Throwable th) {
                th = th;
                j = 131072;
                Trace.traceEnd(j);
                throw th;
            }
        } catch (Throwable th2) {
            th = th2;
            j = 131072;
        }
    }

    private boolean isWakeUpPreventionNeededForEdgeLightingLocked(WakeLock wakeLock) {
        if (this.mEdgeInternal == null || isInteractiveInternal()) {
            return false;
        }
        int i = wakeLock.mFlags & 65535;
        if (i == 6 || i == 10 || i == 26) {
            return this.mEdgeInternal.showForWakeLock(wakeLock.mPackageName, wakeLock.mOwnerUid);
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isWakeUpPreventionNeededLocked() {
        if (this.mLastWakeUpReason == 19) {
            return false;
        }
        if (this.mProximityPositive && !this.mWakeUpEvenThoughProximityPositive) {
            Slog.d(TAG, "!@Screen__On : Cancel (proximity)");
            return true;
        } else if (screenBlockedByCoverLocked() && !this.mIsDualViewMode) {
            Slog.d(TAG, "Screen__On : Cancel (coverType: " + this.mCoverType + Separators.RPAREN);
            return true;
        } else if (!this.mIsFolderType || !this.mIsLidClosed || this.mIsDualScreenFolder || SEC_USE_FACTORY_BINARY || this.mDockState == 104) {
            return false;
        } else {
            Slog.d(TAG, "Screen__On : Cancel (folder)");
            return true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void goToSleepInternal(long eventTime, int reason, int flags, int uid) {
        synchronized (this.mLock) {
            if (goToSleepNoUpdateLocked(eventTime, reason, flags, uid)) {
                updatePowerStateLocked();
            }
        }
    }

    private boolean goToSleepNoUpdateLocked(long eventTime, int reason, int flags, int uid) {
        int reason2 = reason;
        if (eventTime < this.mLastWakeTime || this.mWakefulness == 0 || this.mWakefulness == 3 || !this.mBootCompleted || !this.mSystemReady) {
            return false;
        }
        if (this.mGoToSleepPreventionEnabled) {
            Slog.d(TAG, "goToSleepNoUpdateLocked: GoToSleep prevention is enabled");
            return false;
        } else if (this.mIsDualViewMode && (reason2 == 4 || reason2 == 10 || reason2 == 11)) {
            this.mDirty |= 2;
            Slog.d(TAG, "turning on/off default display only, off=" + this.mDisplayPowerRequest.internalDisplayOff);
            if (this.mInternalWakefulnessForDex == 0) {
                this.mInternalWakefulnessForDex = 1;
                this.mLastUserActivityTime = eventTime;
            } else {
                this.mInternalWakefulnessForDex = 0;
                if (reason2 == 4) {
                    this.mHandlerPmsMisc.post(new Runnable() { // from class: com.android.server.power.PowerManagerService.3
                        @Override // java.lang.Runnable
                        public void run() {
                            PowerManagerService.this.mContext.sendBroadcastAsUser(PowerManagerService.this.mInternalDisplayOffByPowerKeyIntent, UserHandle.ALL);
                        }
                    });
                }
            }
            return true;
        } else {
            Slog.i(TAG, "!@Screen__Off - " + this.mScreenOnOffCount + " : " + this.mScreenOffReason + " (" + reason2 + Separators.RPAREN);
            this.mScreenOnReason = "";
            this.mScreenOffReason = "";
            Trace.traceBegin(131072L, "goToSleep");
            long[] jArr = this.mDPSIDuration;
            jArr[4] = jArr[4] + 1;
            try {
                switch (reason2) {
                    case 1:
                        Slog.i(TAG, "Going to sleep due to device administration policy (uid " + uid + ")...");
                        break;
                    case 2:
                        Slog.i(TAG, "Going to sleep due to screen timeout (uid " + uid + ")...");
                        break;
                    case 3:
                        Slog.i(TAG, "Going to sleep due to lid switch (uid " + uid + ")...");
                        break;
                    case 4:
                        Slog.i(TAG, "Going to sleep due to power button (uid " + uid + ")...");
                        break;
                    case 5:
                        Slog.i(TAG, "Going to sleep due to HDMI standby (uid " + uid + ")...");
                        break;
                    case 6:
                        Slog.i(TAG, "Going to sleep due to sleep button (uid " + uid + ")...");
                        break;
                    case 7:
                        Slog.i(TAG, "Going to sleep by an accessibility service request (uid " + uid + ")...");
                        break;
                    case 8:
                        Slog.i(TAG, "Going to sleep due to proximity...");
                        break;
                    default:
                        Slog.i(TAG, "Going to sleep by application request (uid " + uid + ")...");
                        reason2 = 0;
                        break;
                }
                this.mLastSleepTime = eventTime;
                this.mSandmanSummoned = true;
                setWakefulnessLocked(3, reason2);
                this.mLastGoToSleepReason = reason2;
                int numWakeLocksCleared = 0;
                int numWakeLocks = this.mWakeLocks.size();
                for (int i = 0; i < numWakeLocks; i++) {
                    WakeLock wakeLock = this.mWakeLocks.get(i);
                    int i2 = wakeLock.mFlags & 65535;
                    if (i2 == 6 || i2 == 10 || i2 == 26) {
                        numWakeLocksCleared++;
                    }
                }
                EventLogTags.writePowerSleepRequested(numWakeLocksCleared);
                if ((flags & 1) != 0) {
                    reallyGoToSleepNoUpdateLocked(eventTime, uid);
                }
                return true;
            } finally {
                Trace.traceEnd(131072L);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void napInternal(long eventTime, int uid) {
        synchronized (this.mLock) {
            if (napNoUpdateLocked(eventTime, uid)) {
                updatePowerStateLocked();
            }
        }
    }

    private boolean napNoUpdateLocked(long eventTime, int uid) {
        if (eventTime >= this.mLastWakeTime && this.mWakefulness == 1 && this.mBootCompleted && this.mSystemReady) {
            Trace.traceBegin(131072L, "nap");
            try {
                Slog.i(TAG, "Nap time (uid " + uid + ")...");
                this.mSandmanSummoned = true;
                setWakefulnessLocked(2, 0);
                return true;
            } finally {
                Trace.traceEnd(131072L);
            }
        }
        return false;
    }

    private boolean reallyGoToSleepNoUpdateLocked(long eventTime, int uid) {
        if (eventTime < this.mLastWakeTime || this.mWakefulness == 0 || !this.mBootCompleted || !this.mSystemReady) {
            return false;
        }
        if (this.mGoToSleepPreventionEnabled) {
            Slog.d(TAG, "reallyGoToSleepNoUpdateLocked: GoToSleep prevention is enabled");
            return false;
        }
        Trace.traceBegin(131072L, "reallyGoToSleep");
        try {
            Slog.i(TAG, "Sleeping (uid " + uid + ")...");
            setWakefulnessLocked(0, 2);
            Trace.traceEnd(131072L);
            return true;
        } catch (Throwable th) {
            Trace.traceEnd(131072L);
            throw th;
        }
    }

    @VisibleForTesting
    void setWakefulnessLocked(int wakefulness, int reason) {
        if (this.mWakefulness != wakefulness) {
            boolean wasInteractive = PowerManagerInternal.isInteractive(this.mWakefulness);
            boolean isInteractive = PowerManagerInternal.isInteractive(wakefulness);
            if (wasInteractive && !isInteractive) {
                this.mNativeUserActivityCount = 0;
                this.mDelayTimePrintWakeLock = 0L;
                this.mHandler.post(this.mPrintWakeLockAperiodicallyRunnable);
                this.mLastScreenOffEventElapsedRealTime = SystemClock.elapsedRealtime();
            } else if (!wasInteractive && isInteractive) {
                this.mHandler.removeCallbacks(this.mPrintWakeLockAperiodicallyRunnable);
            }
            this.mWakefulness = wakefulness;
            this.mWakefulnessChanging = true;
            this.mDirty |= 2;
            if (this.mNotifier != null) {
                this.mNotifier.onWakefulnessChangeStarted(wakefulness, reason);
            }
            if (this.mIsDualViewMode && this.mWakefulness != 1) {
                this.mInternalWakefulnessForDex = 0;
            }
        }
    }

    private void logSleepTimeoutRecapturedLocked() {
        long now = SystemClock.uptimeMillis();
        long savedWakeTimeMs = this.mOverriddenTimeout - now;
        if (savedWakeTimeMs >= 0) {
            EventLogTags.writePowerSoftSleepRequested(savedWakeTimeMs);
            this.mOverriddenTimeout = -1L;
        }
    }

    private void logScreenOn() {
        Trace.asyncTraceEnd(131072L, TRACE_SCREEN_ON, 0);
        int latencyMs = (int) (SystemClock.uptimeMillis() - this.mLastWakeTime);
        LogMaker log = new LogMaker(198);
        log.setType(1);
        log.setSubtype(0);
        log.setLatency(latencyMs);
        MetricsLogger.action(log);
        EventLogTags.writePowerScreenState(1, 0, 0L, 0, latencyMs);
        if (latencyMs >= 200) {
            Slog.w(TAG, "Screen on took " + latencyMs + " ms");
        }
    }

    private void finishWakefulnessChangeIfNeededLocked() {
        if (this.mWakefulnessChanging && this.mDisplayReady) {
            if (this.mWakefulness == 3 && (this.mWakeLockSummary & 64) == 0) {
                return;
            }
            if (this.mWakefulness == 3 || this.mWakefulness == 0) {
                logSleepTimeoutRecapturedLocked();
            }
            if (this.mWakefulness == 1) {
                logScreenOn();
            }
            this.mWakefulnessChanging = false;
            this.mNotifier.onWakefulnessChangeFinished();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updatePowerStateLocked() {
        int dirtyPhase1;
        if (!this.mSystemReady || this.mDirty == 0) {
            return;
        }
        if (!Thread.holdsLock(this.mLock)) {
            Slog.wtf(TAG, "Power manager lock was not held when calling updatePowerStateLocked");
        }
        Trace.traceBegin(131072L, "updatePowerState");
        try {
            updateIsPoweredLocked(this.mDirty);
            updateStayOnLocked(this.mDirty);
            updateScreenBrightnessBoostLocked(this.mDirty);
            long now = SystemClock.uptimeMillis();
            int dirtyPhase2 = 0;
            do {
                dirtyPhase1 = this.mDirty;
                dirtyPhase2 |= dirtyPhase1;
                this.mDirty = 0;
                updateWakeLockSummaryLocked(dirtyPhase1);
                updateUserActivitySummaryLocked(now, dirtyPhase1);
            } while (updateWakefulnessLocked(dirtyPhase1));
            updateProfilesLocked(now);
            boolean displayBecameReady = updateDisplayPowerStateLocked(dirtyPhase2);
            updateInputDeviceLightStateLocked(dirtyPhase2);
            updateDreamLocked(dirtyPhase2, displayBecameReady);
            finishWakefulnessChangeIfNeededLocked();
            updateSuspendBlockerLocked();
        } finally {
            Trace.traceEnd(131072L);
        }
    }

    private void updateProfilesLocked(long now) {
        int numProfiles = this.mProfilePowerState.size();
        for (int i = 0; i < numProfiles; i++) {
            ProfilePowerState profile = this.mProfilePowerState.valueAt(i);
            if (profile.mScreenOffTimeoutForUser > 0 || profile.mScreenOffTimeout != Long.MAX_VALUE) {
                if (isProfileBeingKeptAwakeLocked(profile, now)) {
                    profile.mLockingNotified = false;
                } else if (!profile.mLockingNotified) {
                    profile.mLockingNotified = true;
                    this.mNotifier.onProfileTimeout(profile.mUserId);
                }
            }
        }
    }

    private boolean isProfileBeingKeptAwakeLocked(ProfilePowerState profile, long now) {
        long minTimeout;
        if (profile.mScreenOffTimeoutForUser > 0) {
            minTimeout = Math.min(profile.mScreenOffTimeout, profile.mScreenOffTimeoutForUser);
        } else {
            minTimeout = profile.mScreenOffTimeout;
        }
        return profile.mLastUserActivityTime + minTimeout > now || (profile.mWakeLockSummary & 32) != 0 || (this.mProximityPositive && (profile.mWakeLockSummary & 16) != 0);
    }

    private void updateIsPoweredLocked(int dirty) {
        if ((dirty & 256) != 0) {
            boolean wasPowered = this.mIsPowered;
            int oldPlugType = this.mPlugType;
            boolean z = this.mBatteryLevelLow;
            this.mIsPowered = this.mBatteryManagerInternal.isPowered(7);
            this.mPlugType = this.mBatteryManagerInternal.getPlugType();
            this.mBatteryLevel = this.mBatteryManagerInternal.getBatteryLevel();
            this.mBatteryLevelLow = this.mBatteryManagerInternal.getBatteryLevelLow();
            this.mBatteryLevelCritical = this.mBatteryManagerInternal.getBatteryLevelCritical();
            if (wasPowered != this.mIsPowered || oldPlugType != this.mPlugType) {
                Slog.d(TAG, "[api] updateIsPoweredLocked : mIsPowered: " + this.mIsPowered + " mPlugType: " + this.mPlugType);
                this.mDirty = this.mDirty | 64;
                boolean dockedOnWirelessCharger = this.mWirelessChargerDetector.update(this.mIsPowered, this.mPlugType);
                boolean shouldWakeUpWhenWirelessChargerPluggedOrUnplugged = true;
                if (this.mIsWirelessChargerSContextEnabled && this.mWirelessChargerSContextManager.isAvailableService(46)) {
                    if (this.mPlugType == 4) {
                        if (!this.mIsWirelessChargerSContextRegistered) {
                            Slog.d(TAG, "updateIsPoweredLocked : Register SContextListener");
                            this.mWirelessChargerSContextManager.registerListener(this.mWirelessChargerSContextListener, 46);
                            this.mIsWirelessChargerSContextRegistered = true;
                        } else if (!this.mIsDeviceMoving && !wasPowered) {
                            shouldWakeUpWhenWirelessChargerPluggedOrUnplugged = false;
                            Slog.d(TAG, "updateIsPoweredLocked : Device is not moved, skip wakeup when connect wireless chager ");
                        }
                    } else if (oldPlugType == 4) {
                        if (this.mIsDeviceMoving) {
                            if (this.mIsWirelessChargerSContextRegistered) {
                                Slog.d(TAG, "updateIsPoweredLocked : Unregister SContextListener");
                                this.mWirelessChargerSContextManager.unregisterListener(this.mWirelessChargerSContextListener, 46);
                                this.mIsWirelessChargerSContextRegistered = false;
                            }
                        } else if (!this.mIsPowered) {
                            shouldWakeUpWhenWirelessChargerPluggedOrUnplugged = false;
                            Slog.d(TAG, "updateIsPoweredLocked : Device is not moved, skip wakeup when disconnect wireless charger");
                            this.mPassedWakeupTime = SystemClock.elapsedRealtime();
                        }
                    }
                }
                long now = SystemClock.uptimeMillis();
                if (shouldWakeUpWhenPluggedOrUnpluggedLocked(wasPowered, oldPlugType, dockedOnWirelessCharger) && shouldWakeUpWhenWirelessChargerPluggedOrUnplugged) {
                    this.mScreenOnReason = " powered change";
                    this.mLastWakeUpReason = 11;
                    wakeUpNoUpdateLocked(now, "android.server.power:POWER", 1000, this.mContext.getOpPackageName(), 1000);
                }
                userActivityNoUpdateLocked(now, 0, 0, 1000);
            }
            this.mBatterySaverStateMachine.setBatteryStatus(this.mIsPowered, this.mBatteryLevel, this.mBatteryLevelLow);
        }
    }

    private boolean shouldWakeUpWhenPluggedOrUnpluggedLocked(boolean wasPowered, int oldPlugType, boolean dockedOnWirelessCharger) {
        if (!(PowerManagerUtil.SEC_FEATURE_WAKEUP_WHEN_PLUG_CHANGED && this.mAodMode && !wasPowered && this.mIsPowered) && this.mWakeUpWhenPluggedOrUnpluggedConfig) {
            if (this.mIsPowered && this.mWakefulness == 2) {
                return false;
            }
            if (!this.mTheaterModeEnabled || this.mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig) {
                SettingsManager settingsManager = SettingsManager.getInstance();
                return settingsManager == null || settingsManager.getScreenWakeupOnPowerState();
            }
            return false;
        }
        return false;
    }

    private void updateStayOnLocked(int dirty) {
        if ((dirty & 288) != 0) {
            boolean wasStayOn = this.mStayOn;
            if (this.mStayOnWhilePluggedInSetting != 0 && !isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked()) {
                this.mStayOn = this.mBatteryManagerInternal.isPowered(this.mStayOnWhilePluggedInSetting);
            } else {
                this.mStayOn = false;
            }
            if (this.mStayOn != wasStayOn) {
                this.mDirty |= 128;
            }
        }
    }

    private void updateWakeLockSummaryLocked(int dirty) {
        int numWakeLocks = this.mWakeLocks.size();
        if (this.mNeedCheckAbuseWakelocks) {
            if (this.mAbuseWakeLocks.size() > 0 && (this.mUserActivitySummary & 6) != 0) {
                releaseAbuseWakelockLocked();
                this.mAbuseWakeLockDisabled = true;
                this.mAbuseWakeLocks.clear();
            } else if (this.mAbuseWakeLockDisabled && (this.mUserActivitySummary & 1) != 0) {
                acquireAbuseWakelockLocked();
                this.mAbuseWakeLockDisabled = false;
            }
            this.mNeedCheckAbuseWakelocks = false;
        }
        if ((dirty & 3) != 0) {
            this.mWakeLockSummary = 0;
            int numProfiles = this.mProfilePowerState.size();
            for (int i = 0; i < numProfiles; i++) {
                this.mProfilePowerState.valueAt(i).mWakeLockSummary = 0;
            }
            for (int i2 = 0; i2 < numWakeLocks; i2++) {
                WakeLock wakeLock = this.mWakeLocks.get(i2);
                if (this.mIsDualViewMode && isScreenLock(wakeLock) && (wakeLock.mFlags & 65536) != 0) {
                    this.mWakeLockSummary |= 1024;
                } else {
                    int wakeLockFlags = getWakeLockSummaryFlags(wakeLock);
                    this.mWakeLockSummary |= wakeLockFlags;
                    for (int j = 0; j < numProfiles; j++) {
                        ProfilePowerState profile = this.mProfilePowerState.valueAt(j);
                        if (wakeLockAffectsUser(wakeLock, profile.mUserId)) {
                            profile.mWakeLockSummary |= wakeLockFlags;
                        }
                    }
                }
            }
            int i3 = this.mWakeLockSummary;
            this.mWakeLockSummary = adjustWakeLockSummaryLocked(i3);
            for (int i4 = 0; i4 < numProfiles; i4++) {
                ProfilePowerState profile2 = this.mProfilePowerState.valueAt(i4);
                profile2.mWakeLockSummary = adjustWakeLockSummaryLocked(profile2.mWakeLockSummary);
            }
        }
    }

    private int adjustWakeLockSummaryLocked(int wakeLockSummary) {
        if (this.mWakefulness != 3) {
            wakeLockSummary &= -193;
        }
        if (this.mWakefulness == 0 || (wakeLockSummary & 64) != 0) {
            wakeLockSummary &= -1039;
            if (this.mWakefulness == 0 && this.mLastGoToSleepReason == 4) {
                wakeLockSummary &= -17;
            }
        }
        if (this.mIsDualViewMode && this.mInternalWakefulnessForDex == 0 && (wakeLockSummary & 6) != 0) {
            wakeLockSummary = (wakeLockSummary & (-7)) | 1024;
        }
        if (this.mWakefulness == 3 && this.mLastGoToSleepReason == 4) {
            wakeLockSummary &= -17;
        }
        if ((wakeLockSummary & 1030) != 0) {
            if (this.mWakefulness == 1) {
                wakeLockSummary |= 33;
            } else if (this.mWakefulness == 2) {
                wakeLockSummary |= 1;
            }
        }
        if ((wakeLockSummary & 128) != 0) {
            return wakeLockSummary | 1;
        }
        return wakeLockSummary;
    }

    private int getWakeLockSummaryFlags(WakeLock wakeLock) {
        int i = wakeLock.mFlags & 65535;
        if (i == 1) {
            return !wakeLock.mDisabled ? 1 : 0;
        } else if (i == 6) {
            if (!wakeLock.mDisabled) {
                this.mWakeLockSummary |= 4;
                return 0;
            }
            return 0;
        } else if (i == 10) {
            if (!wakeLock.mDisabled) {
                return 2;
            }
            return 0;
        } else if (i == 26) {
            return !wakeLock.mDisabled ? 10 : 0;
        } else if (i != 32) {
            if (i != 64) {
                return i != 128 ? 0 : 128;
            }
            return 64;
        } else {
            return 16;
        }
    }

    private boolean wakeLockAffectsUser(WakeLock wakeLock, int userId) {
        if (wakeLock.mWorkSource != null) {
            for (int k = 0; k < wakeLock.mWorkSource.size(); k++) {
                int uid = wakeLock.mWorkSource.get(k);
                if (userId == UserHandle.getUserId(uid)) {
                    return true;
                }
            }
            ArrayList<WorkSource.WorkChain> workChains = wakeLock.mWorkSource.getWorkChains();
            if (workChains != null) {
                for (int k2 = 0; k2 < workChains.size(); k2++) {
                    int uid2 = workChains.get(k2).getAttributionUid();
                    if (userId == UserHandle.getUserId(uid2)) {
                        return true;
                    }
                }
            }
        }
        return userId == UserHandle.getUserId(wakeLock.mOwnerUid);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleAbuseWakelockWhenUseractivityChanged() {
        ArrayList<WakeLock> tempWakeLocks = new ArrayList<>();
        synchronized (this.mLock) {
            int numAbuseWakeLocks = this.mAbuseWakeLocks.size();
            if (numAbuseWakeLocks <= 0 && !this.mAbuseWakeLockDisabled) {
                int numWakeLocks = this.mWakeLocks.size();
                for (int i = 0; i < numWakeLocks; i++) {
                    WakeLock wakeLock = this.mWakeLocks.get(i);
                    if (isScreenLock(wakeLock)) {
                        tempWakeLocks.add(wakeLock);
                    }
                }
                int numWakeLocks2 = tempWakeLocks.size();
                ActivityManager activityManager = (ActivityManager) this.mContext.getSystemService(Context.ACTIVITY_SERVICE);
                PackageManager pm = this.mContext.getPackageManager();
                this.mAllBrightWakeLockAbuse = true;
                boolean isSystemApp = false;
                for (int importance = 0; importance < numWakeLocks2; importance++) {
                    WakeLock wakeLock2 = tempWakeLocks.get(importance);
                    int importance2 = activityManager.getPackageImportance(wakeLock2.mPackageName);
                    boolean isApp = UserHandle.isApp(wakeLock2.mOwnerUid);
                    try {
                        PackageInfo packageInfo = pm.getPackageInfo(wakeLock2.mPackageName, 0);
                        if ((packageInfo.applicationInfo.flags & 1) == 0) {
                            isSystemApp = false;
                        } else {
                            isSystemApp = true;
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        Slog.e(TAG, "Process abuse wakelock; Failed to find " + wakeLock2.mPackageName);
                    }
                    int i2 = wakeLock2.mFlags & 65535;
                    if (i2 != 6) {
                        if (i2 == 10 || i2 == 26) {
                            if (isApp && !isSystemApp && importance2 > 125) {
                                synchronized (this.mLock) {
                                    this.mAbuseWakeLocks.add(wakeLock2);
                                }
                            } else {
                                this.mAllBrightWakeLockAbuse = false;
                            }
                        }
                    } else if (isApp && !isSystemApp && importance2 > 125) {
                        synchronized (this.mLock) {
                            this.mAbuseWakeLocks.add(wakeLock2);
                        }
                    }
                }
            }
        }
    }

    private void disableAbusiveWakeLockInternal(IBinder lock, boolean state, boolean update) {
        synchronized (this.mLock) {
            int index = findWakeLockIndexLocked(lock);
            if (index >= 0) {
                WakeLock temp = this.mWakeLocks.get(index);
                if (temp.mDisabled != state) {
                    temp.mDisabled = state;
                    if (temp.mDisabled) {
                        notifyWakeLockReleasedLocked(temp);
                    } else {
                        notifyWakeLockAcquiredLocked(temp);
                    }
                    if (update) {
                        this.mDirty |= 1;
                        updatePowerStateLocked();
                    }
                }
            }
        }
    }

    private void releaseAbuseWakelockLocked() {
        Slog.d(TAG, "releaseAbuseWakelockLocked; size = " + this.mAbuseWakeLocks.size());
        Iterator<WakeLock> it = this.mAbuseWakeLocks.iterator();
        while (it.hasNext()) {
            WakeLock mwl = it.next();
            disableAbusiveWakeLockInternal(mwl.mLock, true, false);
        }
    }

    private void acquireAbuseWakelockLocked() {
        Slog.d(TAG, "acquireAbuseWakelockLocked;");
        int numWakeLocks = this.mWakeLocks.size();
        for (int i = 0; i < numWakeLocks; i++) {
            WakeLock wakeLock = this.mWakeLocks.get(i);
            int i2 = wakeLock.mFlags & 65535;
            if ((i2 == 6 || i2 == 10 || i2 == 26) && wakeLock.mDisabled) {
                disableAbusiveWakeLockInternal(wakeLock.mLock, false, false);
            }
        }
    }

    void checkForLongWakeLocks() {
        synchronized (this.mLock) {
            long now = SystemClock.uptimeMillis();
            this.mNotifyLongDispatched = now;
            long when = now - 60000;
            long nextCheckTime = Long.MAX_VALUE;
            int numWakeLocks = this.mWakeLocks.size();
            for (int i = 0; i < numWakeLocks; i++) {
                WakeLock wakeLock = this.mWakeLocks.get(i);
                if ((wakeLock.mFlags & 65535) == 1 && wakeLock.mNotifiedAcquired && !wakeLock.mNotifiedLong) {
                    if (wakeLock.mAcquireTime >= when) {
                        long checkTime = wakeLock.mAcquireTime + 60000;
                        if (checkTime < nextCheckTime) {
                            nextCheckTime = checkTime;
                        }
                    } else {
                        notifyWakeLockLongStartedLocked(wakeLock);
                    }
                }
            }
            this.mNotifyLongScheduled = 0L;
            this.mHandler.removeMessages(4);
            if (nextCheckTime != Long.MAX_VALUE) {
                this.mNotifyLongNextCheck = nextCheckTime;
                enqueueNotifyLongMsgLocked(nextCheckTime);
            } else {
                this.mNotifyLongNextCheck = 0L;
            }
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Removed duplicated region for block: B:116:0x0205  */
    /* JADX WARN: Removed duplicated region for block: B:117:0x0218  */
    /* JADX WARN: Removed duplicated region for block: B:97:0x01b0  */
    /* JADX WARN: Type inference failed for: r1v1 */
    /* JADX WARN: Type inference failed for: r1v2, types: [int, boolean] */
    /* JADX WARN: Type inference failed for: r1v7 */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    private void updateUserActivitySummaryLocked(long r30, int r32) {
        /*
            Method dump skipped, instructions count: 784
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.power.PowerManagerService.updateUserActivitySummaryLocked(long, int):void");
    }

    private void scheduleUserInactivityTimeout(long timeMs) {
        Message msg = this.mHandler.obtainMessage(1);
        msg.setAsynchronous(true);
        this.mHandler.sendMessageAtTime(msg, timeMs);
    }

    private long getNextProfileTimeoutLocked(long now) {
        long nextTimeout = -1;
        int numProfiles = this.mProfilePowerState.size();
        for (int i = 0; i < numProfiles; i++) {
            ProfilePowerState profile = this.mProfilePowerState.valueAt(i);
            long timeout = profile.mLastUserActivityTime + profile.mScreenOffTimeout;
            long timeoutForUser = Long.MAX_VALUE;
            if (profile.mScreenOffTimeoutForUser != Long.MAX_VALUE) {
                timeoutForUser = profile.mLastUserActivityTime + profile.mScreenOffTimeoutForUser;
            }
            long minTimeout = Math.min(timeout, timeoutForUser);
            if (minTimeout > now && (nextTimeout == -1 || minTimeout < nextTimeout)) {
                nextTimeout = minTimeout;
            }
        }
        return nextTimeout;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleUserActivityTimeout() {
        synchronized (this.mLock) {
            this.mDirty |= 4;
            updatePowerStateLocked();
        }
    }

    private long getSleepTimeoutLocked() {
        long timeout = this.mSleepTimeoutSetting;
        if (timeout <= 0) {
            return -1L;
        }
        return Math.max(timeout, this.mMinimumScreenOffTimeoutConfig);
    }

    private long getScreenOffTimeoutLocked(long sleepTimeout) {
        long timeout = this.mScreenOffTimeoutSetting;
        if (isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked()) {
            timeout = Math.min(timeout, this.mMaximumScreenOffTimeoutFromDeviceAdmin);
        }
        if (this.mNotificationLightScreenOverride >= 0) {
            Slog.d(TAG, "[api]  override the screen timeout by mNotificationLightScreenOverride");
            timeout = (int) this.mNotificationLightScreenOverride;
        }
        if (this.mUserActivityTimeoutOverrideFromWindowManager >= 0) {
            timeout = Math.min(timeout, this.mUserActivityTimeoutOverrideFromWindowManager);
        }
        if (sleepTimeout >= 0) {
            timeout = Math.min(timeout, sleepTimeout);
        }
        if (this.mIsDualViewMode && timeout > getScreenOffTimeoutForDexLocked()) {
            timeout = getScreenOffTimeoutForDexLocked();
        }
        if (timeout != this.mLastScreenTimeout) {
            Slog.d(TAG, "getScreenOffTimeoutLocked: " + this.mLastScreenTimeout + " -> " + timeout);
            this.mLastScreenTimeout = (int) timeout;
        }
        return Math.max(timeout, this.mMinimumScreenOffTimeoutConfig);
    }

    private int getScreenOffTimeoutForDexLocked() {
        if (this.mUserActivityTimeoutForDexOverrideFromWindowManager >= 0) {
            return (int) this.mUserActivityTimeoutForDexOverrideFromWindowManager;
        }
        return this.mScreenOffTimeoutForDex;
    }

    private long getScreenDimDurationLocked(long screenOffTimeout) {
        if (this.mScreenDimDurationOverrideFromWindowManager != -1 && this.mScreenDimDurationOverrideFromWindowManager < screenOffTimeout) {
            return this.mScreenDimDurationOverrideFromWindowManager;
        }
        return Math.min(this.mMaximumScreenDimDurationConfig, ((float) screenOffTimeout) * this.mMaximumScreenDimRatioConfig);
    }

    private boolean updateWakefulnessLocked(int dirty) {
        String screenOffReason;
        boolean changed = false;
        if ((dirty & 1687) != 0 && this.mWakefulness == 1 && isItBedTimeYetLocked()) {
            long time = SystemClock.uptimeMillis();
            if (shouldNapAtBedTimeLocked()) {
                changed = napNoUpdateLocked(time, 1000);
            } else {
                if ((dirty & 1) != 0) {
                    screenOffReason = "release wake lock: " + this.mPrevReleasedWakeLock;
                } else {
                    screenOffReason = "";
                }
                StringBuilder sb = new StringBuilder();
                sb.append(screenOffReason);
                sb.append((dirty & 128) != 0 ? "release stay on " : "");
                String screenOffReason2 = sb.toString();
                StringBuilder sb2 = new StringBuilder();
                sb2.append(screenOffReason2);
                sb2.append((dirty & 4) != 0 ? "timeout " : "");
                String screenOffReason3 = sb2.toString();
                StringBuilder sb3 = new StringBuilder();
                sb3.append(screenOffReason3);
                sb3.append((dirty & 512) != 0 ? "prox pos " : "");
                String screenOffReason4 = sb3.toString();
                this.mScreenOffReason = screenOffReason4 + "(0x" + Integer.toHexString(dirty) + Separators.RPAREN;
                changed = goToSleepNoUpdateLocked(time, 2, 0, 1000);
            }
        }
        return changed || this.mNeedCheckAbuseWakelocks;
    }

    private boolean shouldNapAtBedTimeLocked() {
        return this.mDreamsActivateOnSleepSetting || (this.mDreamsActivateOnDockSetting && this.mDockState != 0);
    }

    private boolean isItBedTimeYetLocked() {
        return this.mBootCompleted && !isBeingKeptAwakeLocked();
    }

    private boolean isBeingKeptAwakeLocked() {
        return this.mStayOn || this.mProximityPositive || (this.mWakeLockSummary & 32) != 0 || (this.mUserActivitySummary & 19) != 0 || this.mScreenBrightnessBoostInProgress;
    }

    private void updateDreamLocked(int dirty, boolean displayBecameReady) {
        if (((dirty & 1015) != 0 || displayBecameReady) && this.mDisplayReady) {
            scheduleSandmanLocked();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scheduleSandmanLocked() {
        if (!this.mSandmanScheduled) {
            this.mSandmanScheduled = true;
            Message msg = this.mHandler.obtainMessage(2);
            msg.setAsynchronous(true);
            this.mHandler.sendMessage(msg);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleSandman() {
        int wakefulness;
        boolean startDreaming;
        boolean isDreaming;
        synchronized (this.mLock) {
            this.mSandmanScheduled = false;
            wakefulness = this.mWakefulness;
            if (this.mSandmanSummoned && this.mDisplayReady) {
                if (!canDreamLocked() && !canDozeLocked()) {
                    startDreaming = false;
                    Slog.d(TAG, "handleSandman : startDreaming: " + startDreaming + "  (canDreamLocked: " + canDreamLocked() + "  canDozeLocked: " + canDozeLocked() + Separators.RPAREN);
                    this.mSandmanSummoned = false;
                }
                startDreaming = true;
                Slog.d(TAG, "handleSandman : startDreaming: " + startDreaming + "  (canDreamLocked: " + canDreamLocked() + "  canDozeLocked: " + canDozeLocked() + Separators.RPAREN);
                this.mSandmanSummoned = false;
            } else {
                startDreaming = false;
            }
        }
        if (this.mDreamManager != null) {
            if (startDreaming) {
                this.mDreamManager.stopDream(false);
                StringBuilder sb = new StringBuilder();
                sb.append("handleSandman : startDream(");
                sb.append(wakefulness == 3);
                sb.append(Separators.RPAREN);
                Slog.d(TAG, sb.toString());
                this.mDreamManager.startDream(wakefulness == 3);
            }
            isDreaming = this.mDreamManager.isDreaming();
        } else {
            isDreaming = false;
        }
        boolean isDreaming2 = isDreaming;
        if (startDreaming && !isDreaming2) {
            Slog.e(TAG, "handleSandman : startDreaming, but isDreaming false");
        }
        synchronized (this.mLock) {
            if (startDreaming && isDreaming2) {
                this.mBatteryLevelWhenDreamStarted = this.mBatteryLevel;
                if (wakefulness == 3) {
                    Slog.i(TAG, "Dozing...");
                } else {
                    Slog.i(TAG, "Dreaming...");
                }
            }
            if (!this.mSandmanSummoned && this.mWakefulness == wakefulness) {
                if (wakefulness == 2) {
                    if (isDreaming2 && canDreamLocked()) {
                        if (this.mDreamsBatteryLevelDrainCutoffConfig < 0 || this.mBatteryLevel >= this.mBatteryLevelWhenDreamStarted - this.mDreamsBatteryLevelDrainCutoffConfig || isBeingKeptAwakeLocked()) {
                            return;
                        }
                        Slog.i(TAG, "Stopping dream because the battery appears to be draining faster than it is charging.  Battery level when dream started: " + this.mBatteryLevelWhenDreamStarted + "%.  Battery level now: " + this.mBatteryLevel + "%.");
                    }
                    if (isItBedTimeYetLocked()) {
                        this.mScreenOffReason = " dream(timeout)";
                        goToSleepNoUpdateLocked(SystemClock.uptimeMillis(), 2, 0, 1000);
                        updatePowerStateLocked();
                    } else {
                        this.mScreenOnReason = " dream end";
                        this.mLastWakeUpReason = 15;
                        wakeUpNoUpdateLocked(SystemClock.uptimeMillis(), "android.server.power:DREAM", 1000, this.mContext.getOpPackageName(), 1000);
                        updatePowerStateLocked();
                    }
                } else if (wakefulness == 3) {
                    if (isDreaming2) {
                        return;
                    }
                    reallyGoToSleepNoUpdateLocked(SystemClock.uptimeMillis(), 1000);
                    updatePowerStateLocked();
                }
                if (isDreaming2) {
                    Slog.d(TAG, "handleSandman : stopDream");
                    this.mDreamManager.stopDream(false);
                }
            }
        }
    }

    private boolean canDreamLocked() {
        if (this.mWakefulness == 2 && this.mDreamsSupportedConfig && this.mDreamsEnabledSetting && this.mDisplayPowerRequest.isBrightOrDim() && !this.mDisplayPowerRequest.isVr() && (this.mUserActivitySummary & 23) != 0 && this.mBootCompleted && !verifiedCoverClosedLocked()) {
            if (!isBeingKeptAwakeLocked()) {
                if (this.mIsPowered || this.mDreamsEnabledOnBatteryConfig) {
                    if (this.mIsPowered || this.mDreamsBatteryLevelMinimumWhenNotPoweredConfig < 0 || this.mBatteryLevel >= this.mDreamsBatteryLevelMinimumWhenNotPoweredConfig) {
                        return !this.mIsPowered || this.mDreamsBatteryLevelMinimumWhenPoweredConfig < 0 || this.mBatteryLevel >= this.mDreamsBatteryLevelMinimumWhenPoweredConfig;
                    }
                    return false;
                }
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean canDozeLocked() {
        return this.mWakefulness == 3;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setScreenDimDurationOverrideFromWindowManagerInternal(long timeoutMillis) {
        synchronized (this.mLock) {
            if (this.mScreenDimDurationOverrideFromWindowManager != timeoutMillis) {
                Slog.d(TAG, "[api] setScreenDimDurationOverrideFromWindowManagerInternal: timeoutMillis: " + timeoutMillis);
                this.mScreenDimDurationOverrideFromWindowManager = timeoutMillis;
                this.mDirty = this.mDirty | 32;
                updatePowerStateLocked();
            }
        }
    }

    public void updateLidState(boolean closed) {
        synchronized (this.mLock) {
            if (this.mIsLidClosed != closed) {
                Slog.d(TAG, "[api] updateLidState: " + closed);
                this.mIsLidClosed = closed;
                this.mDirty = this.mDirty | 262144;
                updatePowerStateLocked();
            }
        }
    }

    private void updateInputDeviceLightStateLocked(int dirty) {
        if ((475427 & dirty) != 0) {
            boolean needUpdate = true;
            if ((dirty & 32) != 0 || (dirty & 256) != 0) {
                int timeout = (this.mTouchKeyOffTimeoutOverrideFromWindowManager < 1500 || this.mTouchKeyOffTimeoutSetting == 0) ? this.mTouchKeyOffTimeoutSetting : (int) this.mTouchKeyOffTimeoutOverrideFromWindowManager;
                if (this.mTouchkeyLightState.getInputDeviceLightTimeout() == timeout) {
                    if (!this.mIsFolderType || this.mKeyboardLightState.getInputDeviceLightTimeout() == this.mKeyboardOffTimeoutSetting) {
                        if ((this.mTouchKeyForceDisable || this.mBatterySaverController.isEnabled() || verifiedCoverClosedLocked()) != this.mPrevTouchKeyForceDisable) {
                            this.mPrevTouchKeyForceDisable = !this.mPrevTouchKeyForceDisable;
                            this.mTouchKeyForceDisableOverrideFromSystemPowerSaveMode = this.mBatterySaverController.isEnabled();
                        } else {
                            needUpdate = false;
                        }
                    } else {
                        this.mKeyboardLightState.setInputDeviceLightTimeout(this.mKeyboardOffTimeoutSetting);
                        if (!isInputDeviceLightAvailable(1)) {
                            needUpdate = false;
                        }
                    }
                } else {
                    this.mTouchkeyLightState.setInputDeviceLightTimeout(timeout);
                    if (!isInputDeviceLightAvailable(2)) {
                        needUpdate = false;
                    }
                }
            }
            if ((dirty & 1) != 0) {
                if ((this.mWakeLockSummary & 8) == 0 || this.mTouchkeyLightState.getInputDeviceLightKeep()) {
                    if ((this.mWakeLockSummary & 8) != 0 || !this.mTouchkeyLightState.getInputDeviceLightKeep()) {
                        needUpdate = false;
                    } else {
                        this.mTouchkeyLightState.setInputDeviceLightKeep(false);
                        if (this.mIsFolderType) {
                            this.mKeyboardLightState.setInputDeviceLightKeep(false);
                        }
                    }
                } else {
                    this.mTouchkeyLightState.setInputDeviceLightKeep(true);
                    if (this.mIsFolderType) {
                        this.mKeyboardLightState.setInputDeviceLightKeep(true);
                    }
                }
            }
            if (needUpdate) {
                if (isInputDeviceLightAvailable(2) && this.mTouchkeyLightState.getInputDeviceLightTimeout() != 0) {
                    this.mTouchkeyLightState.setInputDeviceLightOn(1);
                } else {
                    this.mTouchkeyLightState.setInputDeviceLightOn(0);
                }
                if (this.mIsFolderType) {
                    if (isInputDeviceLightAvailable(1) && this.mKeyboardLightState.getInputDeviceLightTimeout() != 0) {
                        this.mKeyboardLightState.setInputDeviceLightOn(1);
                    } else {
                        this.mKeyboardLightState.setInputDeviceLightOn(0);
                    }
                }
            }
        }
    }

    private boolean isInputDeviceLightAvailable(int lightsId) {
        if (this.mTouchKeyForceDisable || this.mTouchKeyForceDisableOverrideFromSystemPowerSaveMode || verifiedCoverClosedLocked() || this.mWakefulness != 1 || !this.mColorFadeExit || !this.mInputDeviceEnabled) {
            return false;
        }
        return lightsId == 2 ? !this.mIsFolderType || this.mIsLidClosed : lightsId == 1 && this.mIsFolderType && !this.mIsLidClosed && !isKeyboardLightDisabledByNightMode();
    }

    private boolean isKeyboardLightDisabledByNightMode() {
        Date now = Calendar.getInstance().getTime();
        int nowHour = now.getHours();
        return PowerManagerUtil.Sales.CHINA && this.mKeyLedOffNightModeSetting == 1 && nowHour >= 7 && nowHour < 17;
    }

    private boolean updateDisplayPowerStateLocked(int dirty) {
        boolean autoBrightness;
        int screenBrightnessOverride;
        boolean oldDisplayReady = this.mDisplayReady;
        if ((dirty & 14655) != 0) {
            int prevScreenPolicy = this.mDisplayPowerRequest.policy;
            this.mDisplayPowerRequest.policy = getDesiredScreenPolicyLocked();
            boolean wasInternalDisplayOff = this.mDisplayPowerRequest.internalDisplayOff;
            this.mDisplayPowerRequest.internalDisplayOff = false;
            if (this.mIsDualViewMode && this.mDisplayPowerRequest.isBrightOrDim()) {
                if (this.mInternalWakefulnessForDex == 0) {
                    this.mDisplayPowerRequest.internalDisplayOff = true;
                } else if ((this.mUserActivitySummary & 20) != 0 && (this.mWakeLockSummary & 6) == 0) {
                    this.mDisplayPowerRequest.internalDisplayOff = true;
                    this.mInternalWakefulnessForDex = 0;
                } else if (screenBlockedByCoverLocked()) {
                    this.mDisplayPowerRequest.internalDisplayOff = true;
                    this.mInternalWakefulnessForDex = 0;
                }
            }
            if (this.mIsDualViewModeChanging && !this.mIsDualViewMode && screenBlockedByCoverLocked()) {
                this.mDisplayPowerRequest.internalDisplayOff = true;
            }
            if (wasInternalDisplayOff != this.mDisplayPowerRequest.internalDisplayOff) {
                this.mNotifier.onInternalDisplayStateChange(!this.mDisplayPowerRequest.internalDisplayOff);
            }
            boolean isBrightnessOverriden = false;
            if (!this.mBootCompleted) {
                autoBrightness = false;
                screenBrightnessOverride = this.mScreenBrightnessSettingDefault;
            } else if (isValidBrightness(this.mScreenBrightnessOverrideFromWindowManager)) {
                autoBrightness = false;
                screenBrightnessOverride = this.mScreenBrightnessOverrideFromWindowManager;
                isBrightnessOverriden = true;
            } else {
                autoBrightness = this.mScreenBrightnessModeSetting == 1 || this.mAdaptiveBrightnessCameraModeSetting;
                screenBrightnessOverride = -1;
            }
            if (isBrightnessOverriden != this.mLastBrightnessOverrideState) {
                this.mLastBrightnessOverrideState = isBrightnessOverriden;
                this.mHandler.post(new Runnable() { // from class: com.android.server.power.PowerManagerService.7
                    @Override // java.lang.Runnable
                    public void run() {
                        Slog.d(PowerManagerService.TAG, "Hide QuickPanel Brightness Bar: " + PowerManagerService.this.mLastBrightnessOverrideState);
                        Settings.System.putInt(PowerManagerService.this.mContext.getContentResolver(), PowerManagerService.PMS_DB_BRIGHTNESS_ENABLE, !PowerManagerService.this.mLastBrightnessOverrideState ? 1 : 0);
                    }
                });
            }
            if (!this.mCoverAuthReady) {
                this.mIsCoverClosed = isCoverClosedBySysfs();
                this.mlastUpdateCoverStateReason = "isCoverClosedBySysfs called on closed = " + this.mIsCoverClosed;
            }
            if (this.mCoverType == 8 && this.mIsCoverClosed) {
                screenBrightnessOverride = 255;
            }
            if ((!this.mIsSupportedLightSensor && !this.mAdaptiveBrightnessCameraModeSetting) || ((!this.mIsFolderType && sensorBlockedByCoverLocked() && autoBrightness) || ((this.mIsFolderType && this.mIsLidClosed && autoBrightness) || this.mGameAutobrightnessLocked || this.mFreezingScreenBrightness))) {
                autoBrightness = false;
            }
            this.mDisplayPowerRequest.screenBrightnessOverride = screenBrightnessOverride;
            this.mDisplayPowerRequest.useAutoBrightness = autoBrightness;
            this.mDisplayPowerRequest.useProximitySensor = shouldUseProximitySensorLocked();
            this.mDisplayPowerRequest.boostScreenBrightness = shouldBoostScreenBrightness();
            if (this.mLastAutoBrightnessLimitTime > this.mDisplayManagerInternal.getLastUserSetScreenBrightnessTime()) {
                this.mDisplayPowerRequest.autoBrightnessLowerLimit = this.mAutoBrightnessLowerLimit;
                this.mDisplayPowerRequest.autoBrightnessUpperLimit = this.mAutoBrightnessUpperLimit;
                this.mDisplayPowerRequest.forceSlowChange = this.mForceSlowChange;
                this.mForceSlowChange = false;
            } else {
                this.mDisplayPowerRequest.autoBrightnessLowerLimit = -1;
                this.mDisplayPowerRequest.autoBrightnessUpperLimit = -1;
            }
            this.mDisplayPowerRequest.wakeUpEvenThoughProximityPositive = this.mWakeUpEvenThoughProximityPositive;
            if (this.mDisplayPowerRequest.useProximitySensor) {
                this.mDisplayPowerRequest.proximityPositiveDebounce = getMaxProximityPositiveDebounce();
                this.mDisplayPowerRequest.proximityNegativeDebounce = getMaxProximityNegativeDebounce();
            } else {
                this.mDisplayPowerRequest.proximityPositiveDebounce = -1;
                this.mDisplayPowerRequest.proximityNegativeDebounce = -1;
            }
            this.mDisplayPowerRequest.batteryLevel = this.mBatteryLevel;
            this.mDisplayPowerRequest.batteryLevelCritical = this.mBatteryLevelCritical;
            this.mDisplayPowerRequest.isPowered = this.mIsPowered;
            this.mDisplayPowerRequest.coverClosed = sensorBlockedByCoverLocked();
            this.mDisplayPowerRequest.mFTAMode = this.mFTAMode;
            this.mDisplayPowerRequest.useColorWeaknessMode = this.mColorWeaknessModeEnabled;
            this.mDisplayPowerRequest.useClearViewBrightnessMode = this.mClearViewBrightnessModeEnabled && !this.mBatterySaverController.isEnabled();
            this.mDisplayPowerRequest.screenBrightnessScaleFactor = this.mScreenBrightnessScaleFactor;
            updatePowerRequestFromBatterySaverPolicy(this.mDisplayPowerRequest);
            if (this.mDisplayPowerRequest.policy == 1) {
                this.mDisplayPowerRequest.dozeScreenState = this.mDozeScreenStateOverrideFromDreamManager;
                if ((this.mWakeLockSummary & 128) != 0 && !this.mDrawWakeLockOverrideFromSidekick) {
                    if (this.mDisplayPowerRequest.dozeScreenState == 4) {
                        this.mDisplayPowerRequest.dozeScreenState = 3;
                    }
                    if (this.mDisplayPowerRequest.dozeScreenState == 6) {
                        this.mDisplayPowerRequest.dozeScreenState = 2;
                    }
                }
                this.mDisplayPowerRequest.dozeScreenBrightness = this.mDozeScreenBrightnessOverrideFromDreamManager;
            } else {
                this.mDisplayPowerRequest.dozeScreenState = 0;
                this.mDisplayPowerRequest.dozeScreenBrightness = -1;
            }
            this.mDisplayPowerRequest.dualScreenPolicy = this.mDualScreenPolicy;
            this.mDisplayPowerRequest.isOutdoorMode = this.mOutdoorModeSetting;
            if (this.mDisplayPowerRequest.isOutdoorMode && this.mDisplayPowerRequest.policy == 3 && prevScreenPolicy == 0 && SystemClock.elapsedRealtime() - this.mLastOutdoorModeEnblaedTime > AlarmManager.INTERVAL_FIFTEEN_MINUTES) {
                this.mDisplayPowerRequest.isOutdoorMode = false;
                this.mHandler.post(new Runnable() { // from class: com.android.server.power.PowerManagerService.8
                    @Override // java.lang.Runnable
                    public void run() {
                        Slog.d(PowerManagerService.TAG, "updateDisplayPowerStateLocked: OutdoorMode timed out");
                        Settings.System.putIntForUser(PowerManagerService.this.mContext.getContentResolver(), PowerManagerService.OUTDOOR_MODE_SETTING, 0, -2);
                    }
                });
            }
            this.mDisplayPowerRequest.earlyWakeUp = this.mEarlyWakeUp;
            this.mEarlyWakeUp = false;
            this.mDisplayPowerRequest.lcdFlashMode = this.mLCDFlashMode;
            this.mDisplayReady = this.mDisplayManagerInternal.requestPowerState(this.mDisplayPowerRequest, this.mRequestWaitForNegativeProximity);
            this.mRequestWaitForNegativeProximity = false;
            if ((dirty & 4096) != 0) {
                sQuiescent = false;
            }
            if (this.mDisplayReady != oldDisplayReady) {
                Slog.d(TAG, "mDisplayReady: " + this.mDisplayReady);
            }
            this.mWakeUpEvenThoughProximityPositive = false;
        }
        return this.mDisplayReady && !oldDisplayReady;
    }

    private void updateScreenBrightnessBoostLocked(int dirty) {
        if ((dirty & 2048) != 0 && this.mScreenBrightnessBoostInProgress) {
            long now = SystemClock.uptimeMillis();
            this.mHandler.removeMessages(3);
            if (this.mLastScreenBrightnessBoostTime > this.mLastSleepTime) {
                long boostTimeout = this.mLastScreenBrightnessBoostTime + TimedRemoteCaller.DEFAULT_CALL_TIMEOUT_MILLIS;
                if (boostTimeout > now) {
                    Message msg = this.mHandler.obtainMessage(3);
                    msg.setAsynchronous(true);
                    this.mHandler.sendMessageAtTime(msg, boostTimeout);
                    return;
                }
            }
            this.mScreenBrightnessBoostInProgress = false;
            this.mNotifier.onScreenBrightnessBoostChanged();
            userActivityNoUpdateLocked(now, 0, 0, 1000);
        }
    }

    private boolean shouldBoostScreenBrightness() {
        return !isVrModeEnabled() && this.mScreenBrightnessBoostInProgress;
    }

    private static boolean isValidBrightness(int value) {
        return value >= 0 && value <= 255;
    }

    @VisibleForTesting
    int getDesiredScreenPolicyLocked() {
        if (this.mWakefulness == 0 || sQuiescent) {
            return 0;
        }
        if (this.mWakefulness == 3) {
            if ((this.mWakeLockSummary & 64) != 0) {
                return 1;
            }
            if (this.mDozeAfterScreenOff) {
                return 0;
            }
        }
        if (isVrModeEnabled()) {
            return 4;
        }
        return ((this.mWakeLockSummary & 2) == 0 && (this.mUserActivitySummary & 1) == 0 && this.mBootCompleted && !this.mScreenBrightnessBoostInProgress) ? 2 : 3;
    }

    private boolean shouldUseProximitySensorLocked() {
        return (isVrModeEnabled() || (this.mWakeLockSummary & 16) == 0 || this.mDockState == 110 || this.mDockState == 114 || this.mIsDualViewMode) ? false : true;
    }

    private int getMaxProximityPositiveDebounce() {
        int maxDebounce = -1;
        int count = this.mWakeLocks.size();
        for (int i = 0; i < count; i++) {
            if ((this.mWakeLocks.get(i).mFlags & 65535) == 32 && maxDebounce < this.mWakeLocks.get(i).mProximityPositiveDebounce) {
                maxDebounce = this.mWakeLocks.get(i).mProximityPositiveDebounce;
            }
        }
        return maxDebounce;
    }

    private int getMaxProximityNegativeDebounce() {
        int maxDebounce = -1;
        int count = this.mWakeLocks.size();
        for (int i = 0; i < count; i++) {
            if ((this.mWakeLocks.get(i).mFlags & 65535) == 32 && maxDebounce < this.mWakeLocks.get(i).mProximityNegativeDebounce) {
                maxDebounce = this.mWakeLocks.get(i).mProximityNegativeDebounce;
            }
        }
        return maxDebounce;
    }

    private void updateSuspendBlockerLocked() {
        boolean needWakeLockSuspendBlocker = (this.mWakeLockSummary & 1) != 0;
        boolean needDisplaySuspendBlocker = needDisplaySuspendBlockerLocked();
        boolean autoSuspend = !needDisplaySuspendBlocker;
        boolean interactive = this.mDisplayPowerRequest.isBrightOrDim();
        if (!autoSuspend && this.mDecoupleHalAutoSuspendModeFromDisplayConfig) {
            setHalAutoSuspendModeLocked(false);
        }
        if (needWakeLockSuspendBlocker && !this.mHoldingWakeLockSuspendBlocker) {
            this.mWakeLockSuspendBlocker.acquire();
            this.mHoldingWakeLockSuspendBlocker = true;
        }
        if (needDisplaySuspendBlocker && !this.mHoldingDisplaySuspendBlocker) {
            this.mDisplaySuspendBlocker.acquire();
            this.mHoldingDisplaySuspendBlocker = true;
        }
        if (this.mDecoupleHalInteractiveModeFromDisplayConfig && (interactive || this.mDisplayReady)) {
            setHalInteractiveModeLocked(interactive, false);
        }
        if (!needWakeLockSuspendBlocker && this.mHoldingWakeLockSuspendBlocker) {
            this.mWakeLockSuspendBlocker.release();
            this.mHoldingWakeLockSuspendBlocker = false;
        }
        if (!needDisplaySuspendBlocker && this.mHoldingDisplaySuspendBlocker) {
            this.mDisplaySuspendBlocker.release();
            this.mHoldingDisplaySuspendBlocker = false;
        }
        if (autoSuspend && this.mDecoupleHalAutoSuspendModeFromDisplayConfig) {
            setHalAutoSuspendModeLocked(true);
        }
    }

    private boolean needDisplaySuspendBlockerLocked() {
        if (this.mDisplayReady) {
            return (this.mDisplayPowerRequest.isBrightOrDim() && !(this.mProximityPositive && this.mSuspendWhenScreenOffDueToProximityConfig)) || this.mScreenBrightnessBoostInProgress;
        }
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setHalAutoSuspendModeLocked(boolean enable) {
        if (enable != this.mHalAutoSuspendModeEnabled) {
            this.mHalAutoSuspendModeEnabled = enable;
            Trace.traceBegin(131072L, "setHalAutoSuspend(" + enable + Separators.RPAREN);
            try {
                nativeSetAutoSuspend(enable);
            } finally {
                Trace.traceEnd(131072L);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Removed duplicated region for block: B:49:0x00aa A[Catch: all -> 0x00a6, TryCatch #4 {all -> 0x00a6, blocks: (B:11:0x004b, B:15:0x0064, B:28:0x007c, B:24:0x0073, B:34:0x008b, B:49:0x00aa, B:50:0x00ad, B:52:0x00b2, B:14:0x0050, B:32:0x0082), top: B:57:0x004b, inners: #3 }] */
    /* JADX WARN: Removed duplicated region for block: B:52:0x00b2 A[Catch: all -> 0x00a6, TRY_LEAVE, TryCatch #4 {all -> 0x00a6, blocks: (B:11:0x004b, B:15:0x0064, B:28:0x007c, B:24:0x0073, B:34:0x008b, B:49:0x00aa, B:50:0x00ad, B:52:0x00b2, B:14:0x0050, B:32:0x0082), top: B:57:0x004b, inners: #3 }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public void setHalInteractiveModeLocked(boolean r8, boolean r9) {
        /*
            r7 = this;
            boolean r0 = r7.mHalInteractiveModeEnabled
            if (r8 != r0) goto L6
            if (r9 == 0) goto Lba
        L6:
            java.lang.String r0 = "PowerManagerService"
            java.lang.StringBuilder r1 = new java.lang.StringBuilder
            r1.<init>()
            java.lang.String r2 = "Setting HAL interactive mode to "
            r1.append(r2)
            r1.append(r8)
            java.lang.String r1 = r1.toString()
            com.android.server.power.Slog.d(r0, r1)
            boolean r0 = r7.mForcedDisplayOff
            if (r0 == 0) goto L29
            java.lang.String r0 = "PowerManagerService"
            java.lang.String r1 = "setHalInteractiveModeLocked returned because display is forced off"
            com.android.server.power.Slog.d(r0, r1)
            return
        L29:
            r7.mHalInteractiveModeEnabled = r8
            java.lang.StringBuilder r0 = new java.lang.StringBuilder
            r0.<init>()
            java.lang.String r1 = "setHalInteractive("
            r0.append(r1)
            r0.append(r8)
            java.lang.String r1 = ")"
            r0.append(r1)
            java.lang.String r0 = r0.toString()
            r1 = 131072(0x20000, double:6.47582E-319)
            android.os.Trace.traceBegin(r1, r0)
            r0 = 0
            if (r8 != 0) goto La8
            boolean r3 = r7.mProximityPositive     // Catch: java.lang.Throwable -> La6
            if (r3 == 0) goto La8
            r3 = 0
            java.io.FileWriter r4 = new java.io.FileWriter     // Catch: java.lang.Throwable -> L6e java.lang.Exception -> L70 java.io.FileNotFoundException -> L81
            java.lang.String r5 = "/sys/class/sec/tsp/prox_power_off"
            r4.<init>(r5, r0)     // Catch: java.lang.Throwable -> L6e java.lang.Exception -> L70 java.io.FileNotFoundException -> L81
            r3 = r4
            java.lang.String r4 = "1"
            r3.write(r4)     // Catch: java.lang.Throwable -> L6e java.lang.Exception -> L70 java.io.FileNotFoundException -> L81
            java.lang.String r4 = "PowerManagerService"
            java.lang.String r5 = "Turning off TSP completely"
            com.android.server.power.Slog.d(r4, r5)     // Catch: java.lang.Throwable -> L6e java.lang.Exception -> L70 java.io.FileNotFoundException -> L81
            r3.close()     // Catch: java.lang.Exception -> L68 java.lang.Throwable -> La6
            goto L80
        L68:
            r4 = move-exception
            java.lang.String r5 = "PowerManagerService"
            java.lang.String r6 = "Failed to close /sys/class/sec/tsp/prox_power_off"
            goto L7c
        L6e:
            r0 = move-exception
            goto L95
        L70:
            r4 = move-exception
            if (r3 == 0) goto L80
            r3.close()     // Catch: java.lang.Exception -> L77 java.lang.Throwable -> La6
            goto L80
        L77:
            r4 = move-exception
            java.lang.String r5 = "PowerManagerService"
            java.lang.String r6 = "Failed to close /sys/class/sec/tsp/prox_power_off"
        L7c:
            com.android.server.power.Slog.e(r5, r6, r4)     // Catch: java.lang.Throwable -> La6
            goto La8
        L80:
            goto La8
        L81:
            r4 = move-exception
            java.lang.String r5 = "PowerManagerService"
            java.lang.String r6 = "/sys/class/sec/tsp/prox_power_off not found"
            com.android.server.power.Slog.d(r5, r6)     // Catch: java.lang.Throwable -> L6e
            if (r3 == 0) goto L80
            r3.close()     // Catch: java.lang.Exception -> L8f java.lang.Throwable -> La6
            goto L80
        L8f:
            r4 = move-exception
            java.lang.String r5 = "PowerManagerService"
            java.lang.String r6 = "Failed to close /sys/class/sec/tsp/prox_power_off"
            goto L7c
        L95:
            if (r3 == 0) goto La5
            r3.close()     // Catch: java.lang.Exception -> L9c java.lang.Throwable -> La6
            goto La5
        L9c:
            r4 = move-exception
            java.lang.String r5 = "PowerManagerService"
            java.lang.String r6 = "Failed to close /sys/class/sec/tsp/prox_power_off"
            com.android.server.power.Slog.e(r5, r6, r4)     // Catch: java.lang.Throwable -> La6
        La5:
            throw r0     // Catch: java.lang.Throwable -> La6
        La6:
            r0 = move-exception
            goto Lbb
        La8:
            if (r8 != 0) goto Lad
            r7.setInputDeviceEnabledLocked(r0)     // Catch: java.lang.Throwable -> La6
        Lad:
            nativeSetInteractive(r8)     // Catch: java.lang.Throwable -> La6
            if (r8 == 0) goto Lb6
            r0 = 1
            r7.setInputDeviceEnabledLocked(r0)     // Catch: java.lang.Throwable -> La6
        Lb6:
            android.os.Trace.traceEnd(r1)
        Lba:
            return
        Lbb:
            android.os.Trace.traceEnd(r1)
            throw r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.power.PowerManagerService.setHalInteractiveModeLocked(boolean, boolean):void");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isInteractiveInternal() {
        boolean isInteractive;
        synchronized (this.mLock) {
            isInteractive = PowerManagerInternal.isInteractive(this.mWakefulness);
        }
        return isInteractive;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isInteractiveInternal(int displayId, int uid) {
        synchronized (this.mLock) {
            int appId = UserHandle.getAppId(uid);
            if (this.mIsDualViewMode && displayId == 0 && (UserHandle.isApp(uid) || appId == 1001)) {
                boolean interactive = PowerManagerInternal.isInteractive(this.mWakefulness);
                boolean interactiveInternal = PowerManagerInternal.isInteractive(this.mInternalWakefulnessForDex);
                if (interactive != interactiveInternal) {
                    Slog.d(TAG, "isInteractiveInternal(): uid=" + uid + ", interactive=" + interactive + ", interactive(internal)=" + interactiveInternal);
                }
                return interactiveInternal;
            }
            return PowerManagerInternal.isInteractive(this.mWakefulness);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean setLowPowerModeInternal(boolean enabled) {
        synchronized (this.mLock) {
            if (this.mIsPowered) {
                return false;
            }
            this.mBatterySaverStateMachine.setBatterySaverEnabledManually(enabled);
            return true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isProximityPositiveInternal() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mProximityPositive;
        }
        return z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isInternalDisplayOffInternal() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mDisplayPowerRequest.internalDisplayOff;
        }
        return z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setGoToSleepPreventionInternal(boolean enabled) {
        this.mGoToSleepPreventionEnabled = enabled;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setDisableWakeUpPreventionInternal(boolean disabled) {
        this.mWakeUpPreventionDisabled = disabled;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setForcedDisplayOffInternal(boolean off) {
        synchronized (this.mLock) {
            if (!this.mForcedDisplayOff && off) {
                setHalInteractiveModeLocked(false, false);
                this.mForcedDisplayOff = true;
            } else if (this.mForcedDisplayOff && !off) {
                this.mForcedDisplayOff = false;
                setHalInteractiveModeLocked(true, false);
            }
        }
    }

    boolean isDeviceIdleModeInternal() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mDeviceIdleMode;
        }
        return z;
    }

    boolean isLightDeviceIdleModeInternal() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mLightDeviceIdleMode;
        }
        return z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleBatteryStateChangedLocked() {
        this.mDirty |= 256;
        updatePowerStateLocked();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void shutdownOrRebootInternal(int haltMode, boolean confirm, String reason, boolean wait) {
        shutdownOrRebootInternal(haltMode, confirm, reason, wait, false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void shutdownOrRebootInternal(final int haltMode, final boolean confirm, final String reason, boolean wait, final boolean isSystem) {
        if (this.mHandler == null || !this.mSystemReady) {
            if (RescueParty.isAttemptingFactoryReset()) {
                lowLevelReboot(reason);
            } else {
                throw new IllegalStateException("Too early to call shutdown() or reboot()");
            }
        }
        Runnable runnable = new Runnable() { // from class: com.android.server.power.PowerManagerService.10
            @Override // java.lang.Runnable
            public void run() {
                synchronized (this) {
                    if (haltMode == 2) {
                        ShutdownThread.rebootSafeMode(PowerManagerService.this.getUiContext(), confirm);
                    } else if (haltMode == 1) {
                        if (isSystem) {
                            ShutdownThread.systemReboot(PowerManagerService.this.getUiContext(), reason, PowerManagerService.this.mShutdownOrRebootCaller);
                        } else {
                            ShutdownThread.reboot(PowerManagerService.this.getUiContext(), reason, confirm, PowerManagerService.this.mShutdownOrRebootCaller);
                        }
                    } else {
                        ShutdownThread.shutdown(PowerManagerService.this.getUiContext(), reason, confirm, PowerManagerService.this.mShutdownOrRebootCaller);
                    }
                    if (ShutdownThread.getRestrict()) {
                        notifyAll();
                    }
                }
            }
        };
        Message msg = Message.obtain(UiThread.getHandler(), runnable);
        msg.setAsynchronous(true);
        UiThread.getHandler().sendMessage(msg);
        if (wait) {
            synchronized (runnable) {
                while (true) {
                    try {
                        runnable.wait();
                    } catch (InterruptedException e) {
                    }
                    if (ShutdownThread.getRestrict()) {
                    }
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void crashInternal(final String message) {
        Thread t = new Thread("PowerManagerService.crash()") { // from class: com.android.server.power.PowerManagerService.11
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                throw new RuntimeException(message);
            }
        };
        try {
            t.start();
            t.join();
        } catch (InterruptedException e) {
            Slog.wtf(TAG, e);
        }
    }

    @VisibleForTesting
    void updatePowerRequestFromBatterySaverPolicy(DisplayManagerInternal.DisplayPowerRequest displayPowerRequest) {
        PowerSaveState state = this.mBatterySaverPolicy.getBatterySaverPolicy(7, this.mBatterySaverController.isEnabled());
        displayPowerRequest.lowPowerMode = state.batterySaverEnabled;
        displayPowerRequest.screenLowPowerBrightnessFactor = state.brightnessFactor;
    }

    void setStayOnSettingInternal(int val) {
        Settings.Global.putInt(this.mContext.getContentResolver(), "stay_on_while_plugged_in", val);
    }

    void setMaximumScreenOffTimeoutFromDeviceAdminInternal(int userId, long timeMs) {
        if (userId < 0) {
            Slog.wtf(TAG, "Attempt to set screen off timeout for invalid user: " + userId);
            return;
        }
        synchronized (this.mLock) {
            try {
                if (userId == 0) {
                    this.mMaximumScreenOffTimeoutFromDeviceAdmin = timeMs;
                } else {
                    if (timeMs != Long.MAX_VALUE && timeMs != 0) {
                        ProfilePowerState profile = this.mProfilePowerState.get(userId);
                        if (profile != null) {
                            profile.mScreenOffTimeout = timeMs;
                        } else {
                            this.mProfilePowerState.put(userId, new ProfilePowerState(userId, timeMs));
                            updateKnoxTimeoutValue(userId);
                            this.mDirty |= 1;
                        }
                    }
                    this.mProfilePowerState.delete(userId);
                }
                this.mDirty |= 32;
                updatePowerStateLocked();
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    private void updateKnoxTimeoutValue(int userId) {
        ContentResolver resolver = this.mContext.getContentResolver();
        int timeoutForKnox = Settings.System.getIntForUser(resolver, Settings.System.KNOX_SCREEN_OFF_TIMEOUT, 0, userId);
        ProfilePowerState profile = this.mProfilePowerState.get(userId);
        if (profile != null) {
            profile.mScreenOffTimeoutForUser = timeoutForKnox;
        } else {
            this.mProfilePowerState.put(userId, new ProfilePowerState(userId, timeoutForKnox, true));
        }
    }

    void setMaximumScreenOffTimeoutFromKnoxInternal(int userId, long timeMs) {
        if (userId < 0) {
            Slog.wtf(TAG, "Attempt to set screen off timeout for invalid user: " + userId);
            return;
        }
        synchronized (this.mLock) {
            ProfilePowerState profile = this.mProfilePowerState.get(userId);
            if (profile != null) {
                Slog.d(TAG, "profile timeout changed : " + profile.mScreenOffTimeoutForUser + " -> " + timeMs);
                profile.mScreenOffTimeoutForUser = timeMs;
            } else {
                this.mProfilePowerState.put(userId, new ProfilePowerState(userId, timeMs, true));
                this.mDirty |= 1;
            }
            this.mDirty |= 32;
            updatePowerStateLocked();
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private class KnoxTimeoutHandler extends Handler {
        private KnoxTimeoutHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            long j;
            super.handleMessage(msg);
            switch (msg.what) {
                case 1:
                    int userId = msg.arg1;
                    ProfilePowerState profile = (ProfilePowerState) PowerManagerService.this.mProfilePowerState.get(userId);
                    LockPatternUtils lpUtils = new LockPatternUtils(PowerManagerService.this.mContext);
                    if (lpUtils.isSeparateProfileChallengeEnabled(userId) && profile != null && PowerManagerService.this.mKeyguardManager.isDeviceSecure(userId) && !PowerManagerService.this.mKeyguardManager.isDeviceLocked(userId)) {
                        if (profile.mLockingNotified) {
                            Slog.d(PowerManagerService.TAG, "Ignore reset timeout for userId : " + userId);
                            return;
                        }
                        Intent intent = new Intent(PowerManagerService.ACTION_KNOX_LOCK_TIMEOUT);
                        intent.putExtra("android.intent.extra.user_handle", userId);
                        PendingIntent pi = PendingIntent.getBroadcast(PowerManagerService.this.mContext, userId, intent, 1207959552);
                        PowerManagerService.this.mAlarmManager.cancel(pi);
                        long KnoxTimeout = profile.mScreenOffTimeoutForUser;
                        if (profile.mScreenOffTimeout > 0) {
                            if (profile.mScreenOffTimeoutForUser > 0) {
                                j = Math.min(profile.mScreenOffTimeout, profile.mScreenOffTimeoutForUser);
                            } else {
                                j = profile.mScreenOffTimeout;
                            }
                            KnoxTimeout = j;
                        }
                        PowerManagerService.this.mAlarmManager.setExact(2, SystemClock.elapsedRealtime() + KnoxTimeout, pi);
                        return;
                    }
                    return;
                case 2:
                    int userId2 = msg.arg1;
                    Slog.d(PowerManagerService.TAG, "Lock profile user : " + userId2);
                    if (PowerManagerService.this.mTrustManager != null) {
                        PowerManagerService.this.mTrustManager.setDeviceLockedForUser(userId2, true);
                        return;
                    }
                    return;
                default:
                    Slog.d(PowerManagerService.TAG, "unknown msg type " + msg.what);
                    return;
            }
        }
    }

    boolean setDeviceIdleModeInternal(boolean enabled) {
        synchronized (this.mLock) {
            if (this.mDeviceIdleMode == enabled) {
                return false;
            }
            this.mDeviceIdleMode = enabled;
            updateWakeLockDisabledStatesLocked();
            if (enabled) {
                EventLogTags.writeDeviceIdleOnPhase(Context.POWER_SERVICE);
                return true;
            }
            EventLogTags.writeDeviceIdleOffPhase(Context.POWER_SERVICE);
            return true;
        }
    }

    boolean setLightDeviceIdleModeInternal(boolean enabled) {
        synchronized (this.mLock) {
            if (this.mLightDeviceIdleMode != enabled) {
                this.mLightDeviceIdleMode = enabled;
                return true;
            }
            return false;
        }
    }

    void setDeviceIdleWhitelistInternal(int[] appids) {
        synchronized (this.mLock) {
            this.mDeviceIdleWhitelist = appids;
            if (this.mDeviceIdleMode) {
                updateWakeLockDisabledStatesLocked();
            }
        }
    }

    void setDeviceIdleTempWhitelistInternal(int[] appids) {
        synchronized (this.mLock) {
            this.mDeviceIdleTempWhitelist = appids;
            if (this.mDeviceIdleMode) {
                updateWakeLockDisabledStatesLocked();
            }
        }
    }

    void startUidChangesInternal() {
        synchronized (this.mLock) {
            this.mUidsChanging = true;
        }
    }

    void finishUidChangesInternal() {
        synchronized (this.mLock) {
            this.mUidsChanging = false;
            if (this.mUidsChanged) {
                updateWakeLockDisabledStatesLocked();
                this.mUidsChanged = false;
            }
        }
    }

    private void handleUidStateChangeLocked() {
        if (this.mUidsChanging) {
            this.mUidsChanged = true;
        } else {
            updateWakeLockDisabledStatesLocked();
        }
    }

    void updateUidProcStateInternal(int uid, int procState) {
        synchronized (this.mLock) {
            UidState state = this.mUidState.get(uid);
            if (state == null) {
                state = new UidState(uid);
                this.mUidState.put(uid, state);
            }
            boolean oldShouldAllow = state.mProcState <= 10;
            state.mProcState = procState;
            if (state.mNumWakeLocks > 0) {
                if (this.mDeviceIdleMode) {
                    handleUidStateChangeLocked();
                } else if (!state.mActive) {
                    if (oldShouldAllow != (procState <= 10)) {
                        handleUidStateChangeLocked();
                    }
                }
            }
        }
    }

    void uidGoneInternal(int uid) {
        synchronized (this.mLock) {
            int index = this.mUidState.indexOfKey(uid);
            if (index >= 0) {
                UidState state = this.mUidState.valueAt(index);
                state.mProcState = 19;
                state.mActive = false;
                this.mUidState.removeAt(index);
                if (this.mDeviceIdleMode && state.mNumWakeLocks > 0) {
                    handleUidStateChangeLocked();
                }
            }
        }
    }

    void uidActiveInternal(int uid) {
        synchronized (this.mLock) {
            UidState state = this.mUidState.get(uid);
            if (state == null) {
                state = new UidState(uid);
                state.mProcState = 18;
                this.mUidState.put(uid, state);
            }
            state.mActive = true;
            if (state.mNumWakeLocks > 0) {
                handleUidStateChangeLocked();
            }
        }
    }

    void uidIdleInternal(int uid) {
        synchronized (this.mLock) {
            UidState state = this.mUidState.get(uid);
            if (state != null) {
                state.mActive = false;
                if (state.mNumWakeLocks > 0) {
                    handleUidStateChangeLocked();
                }
            }
        }
    }

    private void updateWakeLockDisabledStatesLocked() {
        boolean changed = false;
        int numWakeLocks = this.mWakeLocks.size();
        for (int i = 0; i < numWakeLocks; i++) {
            WakeLock wakeLock = this.mWakeLocks.get(i);
            if ((wakeLock.mFlags & 65535) == 1 && setWakeLockDisabledStateLocked(wakeLock)) {
                changed = true;
                if (wakeLock.mDisabled) {
                    notifyWakeLockReleasedLocked(wakeLock);
                } else {
                    notifyWakeLockAcquiredLocked(wakeLock);
                }
            }
        }
        if (changed) {
            this.mDirty |= 1;
            updatePowerStateLocked();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean setWakeLockDisabledStateLocked(WakeLock wakeLock) {
        if ((wakeLock.mFlags & 65535) == 1) {
            boolean disabled = false;
            int appid = UserHandle.getAppId(wakeLock.mOwnerUid);
            if (appid >= 10000) {
                if (this.mConstants.NO_CACHED_WAKE_LOCKS) {
                    disabled = (wakeLock.mUidState.mActive || wakeLock.mUidState.mProcState == 19 || wakeLock.mUidState.mProcState <= 10) ? false : true;
                }
                if (this.mDeviceIdleMode) {
                    UidState state = wakeLock.mUidState;
                    if (Arrays.binarySearch(this.mDeviceIdleWhitelist, appid) < 0 && Arrays.binarySearch(this.mDeviceIdleTempWhitelist, appid) < 0 && state.mProcState != 19 && state.mProcState > 4) {
                        disabled = true;
                    }
                }
                if (wakeLock.mDisabledByFreecess) {
                    disabled = true;
                }
                if (this.mWakelockBlacklistAppid.contains(Integer.valueOf(appid))) {
                    disabled = true;
                }
            }
            if (wakeLock.mDisabled != disabled) {
                wakeLock.mDisabled = disabled;
                if (!disabled) {
                    EventLog.writeEvent((int) EventLogTags.POWER_PARTIAL_WAKE_STATE, 0, wakeLock.mTag + ":" + wakeLock.mPackageName + "(reAcquired)");
                }
                return true;
            }
        }
        return false;
    }

    private boolean isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked() {
        return this.mMaximumScreenOffTimeoutFromDeviceAdmin >= 0 && this.mMaximumScreenOffTimeoutFromDeviceAdmin < Long.MAX_VALUE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setAttentionLightInternal(boolean on, int color) {
        synchronized (this.mLock) {
            if (this.mSystemReady) {
                Light light = this.mAttentionLight;
                light.setFlashing(color, 2, on ? 3 : 0, 0);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setDozeAfterScreenOffInternal(boolean on) {
        synchronized (this.mLock) {
            this.mDozeAfterScreenOff = on;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void boostScreenBrightnessInternal(long eventTime, int uid) {
        synchronized (this.mLock) {
            if (this.mSystemReady && this.mWakefulness != 0 && eventTime >= this.mLastScreenBrightnessBoostTime) {
                Slog.i(TAG, "Brightness boost activated (uid " + uid + ")...");
                this.mLastScreenBrightnessBoostTime = eventTime;
                if (!this.mScreenBrightnessBoostInProgress) {
                    this.mScreenBrightnessBoostInProgress = true;
                    this.mNotifier.onScreenBrightnessBoostChanged();
                }
                this.mDirty |= 2048;
                userActivityNoUpdateLocked(eventTime, 0, 0, uid);
                updatePowerStateLocked();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isScreenBrightnessBoostedInternal() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mScreenBrightnessBoostInProgress;
        }
        return z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleScreenBrightnessBoostTimeout() {
        synchronized (this.mLock) {
            this.mDirty |= 2048;
            updatePowerStateLocked();
        }
    }

    public long timeSinceScreenWasLastOn() {
        synchronized (this.mLock) {
            if (PowerManagerInternal.isInteractive(this.mWakefulness)) {
                return 0L;
            }
            return SystemClock.elapsedRealtime() - this.mLastScreenOffEventElapsedRealTime;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setScreenBrightnessOverrideFromWindowManagerInternal(int brightness) {
        synchronized (this.mLock) {
            if (this.mScreenBrightnessOverrideFromWindowManager != brightness) {
                Slog.d(TAG, "[api] setScreenBrightnessOverrideFromWindowManagerInternal: brightness: " + brightness);
                this.mScreenBrightnessOverrideFromWindowManager = brightness;
                this.mDirty = this.mDirty | 32;
                updatePowerStateLocked();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setUserInactiveOverrideFromWindowManagerInternal() {
        synchronized (this.mLock) {
            this.mUserInactiveOverrideFromWindowManager = true;
            this.mDirty |= 4;
            updatePowerStateLocked();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setUserActivityTimeoutOverrideFromWindowManagerInternal(long timeoutMillis) {
        synchronized (this.mLock) {
            if (this.mUserActivityTimeoutOverrideFromWindowManager != timeoutMillis) {
                Slog.d(TAG, "[api] setUserActivityTimeoutOverrideFromWindowManagerInternal: timeoutMillis: " + timeoutMillis);
                this.mUserActivityTimeoutOverrideFromWindowManager = timeoutMillis;
                EventLogTags.writeUserActivityTimeoutOverride(timeoutMillis);
                this.mDirty = this.mDirty | 32;
                updatePowerStateLocked();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setNotiLightScreenTimeoutOverride(long timeoutMillis) {
        synchronized (this.mLock) {
            if (this.mNotificationLightScreenOverride != timeoutMillis) {
                Slog.d(TAG, "[api] setNotiLightScreenTimeoutOverride: timeoutMillis: " + timeoutMillis);
                this.mNotificationLightScreenOverride = timeoutMillis;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setUserActivityTimeoutForDexOverrideFromWindowManagerInternal(long timeoutMillis) {
        synchronized (this.mLock) {
            if (this.mUserActivityTimeoutForDexOverrideFromWindowManager != timeoutMillis) {
                Slog.d(TAG, "[api] setUserActivityTimeoutForDexOverrideFromWindowManagerInternal: timeoutMillis: " + timeoutMillis);
                this.mUserActivityTimeoutForDexOverrideFromWindowManager = timeoutMillis;
                this.mDirty = this.mDirty | 32;
                updatePowerStateLocked();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setDozeOverrideFromDreamManagerInternal(int screenState, int screenBrightness) {
        synchronized (this.mLock) {
            if (this.mDozeScreenStateOverrideFromDreamManager != screenState || this.mDozeScreenBrightnessOverrideFromDreamManager != screenBrightness) {
                this.mDozeScreenStateOverrideFromDreamManager = screenState;
                this.mDozeScreenBrightnessOverrideFromDreamManager = screenBrightness;
                this.mDirty |= 32;
                updatePowerStateLocked();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setDrawWakeLockOverrideFromSidekickInternal(boolean keepState) {
        synchronized (this.mLock) {
            if (this.mDrawWakeLockOverrideFromSidekick != keepState) {
                Slog.d(TAG, "[api] setDrawWakeLockOverrideFromSidekickInternal()" + callerInfoToString());
                this.mDrawWakeLockOverrideFromSidekick = keepState;
                this.mDirty = this.mDirty | 32;
                updatePowerStateLocked();
            }
        }
    }

    @VisibleForTesting
    void setVrModeEnabled(boolean enabled) {
        this.mIsVrModeEnabled = enabled;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void powerHintInternal(int hintId, int data) {
        Trace.traceBegin(131072L, "powerHintInternal");
        if (hintId != 2) {
            if (hintId == 8 && data == 1 && this.mBatterySaverController.isLaunchBoostDisabled()) {
                return;
            }
        } else {
            SemPerfManager.sendCommandToSsrm("NORMAL_TOUCH_BOOSTER", "TRUE");
        }
        nativeSendPowerHint(hintId, data);
        Trace.traceEnd(131072L);
    }

    public static void lowLevelShutdown(String reason) {
        Slog.d(TAG, "[api] lowLevelShutdown: " + callerInfoToString());
        Slog.saveLastPMSLog(true);
        if (reason == null) {
            reason = "";
        }
        SystemProperties.set("sys.powerctl", "shutdown," + reason);
    }

    public static void lowLevelReboot(String reason) {
        Slog.d(TAG, "[api] lowLevelReboot: " + reason + callerInfoToString());
        Slog.saveLastPMSLog(true);
        if (reason == null) {
            reason = "";
        }
        if (reason.equals(PowerManager.REBOOT_QUIESCENT)) {
            sQuiescent = true;
            reason = "";
        } else if (reason.endsWith(",quiescent")) {
            sQuiescent = true;
            reason = reason.substring(0, (reason.length() - PowerManager.REBOOT_QUIESCENT.length()) - 1);
        }
        reason = (reason.equals("recovery") || reason.equals(PowerManager.REBOOT_RECOVERY_UPDATE)) ? "recovery" : "recovery";
        if (sQuiescent) {
            reason = reason + ",quiescent";
        }
        SystemProperties.set("sys.powerctl", "reboot," + reason);
        try {
            Thread.sleep(20000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Slog.wtf(TAG, "Unexpected return from lowLevelReboot!");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendDPSIDuration() {
        int i = 0;
        if (this.mDPSIDuration[3] < 86400) {
            String[] values = {Long.toString(this.mDPSIDuration[0]), Long.toString(this.mDPSIDuration[1]), Long.toString(this.mDPSIDuration[2]), Long.toString(this.mDPSIDuration[3]), Long.toString(this.mDPSIDuration[4])};
            String dataset = parseBigData(LD_KEYS, values, LD_KEYS.length);
            this.mHandler.obtainMessage(7, dataset).sendToTarget();
        } else {
            Slog.d(TAG, "sendDPSIDuration LOD : " + this.mDPSIDuration[3]);
        }
        while (true) {
            int i2 = i;
            if (i2 >= LD_KEYS.length) {
                return;
            }
            this.mDPSIDuration[i2] = 0;
            i = i2 + 1;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendCurrentBrightnessStatus() {
        this.mScreenBrightnessSetting = Settings.System.getIntForUser(this.mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, this.mScreenBrightnessSettingDefault, -2);
        String[] values = {Integer.toString(this.mScreenBrightnessModeSetting), Integer.toString(this.mScreenBrightnessSetting)};
        String dataset = parseBigData(LCBS_KEYS, values, LCBS_KEYS.length);
        this.mHandler.obtainMessage(8, dataset).sendToTarget();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String parseBigData(String[] keys, String[] values, int length) {
        JSONObject obj = null;
        try {
            obj = new JSONObject();
            for (int i = 0; i < length; i++) {
                obj.put(keys[i], values[i]);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String customDataSet = obj.toString();
        return customDataSet.replaceAll("\\{", "").replaceAll("\\}", "");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleSendBroadcastToHWParam(String feat, String customdataset) {
        if (this.mSemHqmManager != null) {
            this.mSemHqmManager.sendHWParamToHQM(0, "Display", feat, "sm", "0.0", GlobalSettings.SEC_AS, "", customdataset, "");
        } else {
            Slog.d(TAG, "sendBroadcastToHWParam() mSemHqmManager is null");
        }
    }

    @Override // com.android.server.Watchdog.Monitor
    public void monitor() {
        synchronized (this.mLock) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dumpInternal(PrintWriter pw) {
        WirelessChargerDetector wcd;
        pw.println("POWER MANAGER (dumpsys power)\n");
        synchronized (this.mLock) {
            pw.println("Power Manager State:");
            this.mConstants.dump(pw);
            pw.println("  mDirty=0x" + Integer.toHexString(this.mDirty));
            pw.println("  mWakefulness=" + PowerManagerInternal.wakefulnessToString(this.mWakefulness));
            pw.println("  mWakefulnessChanging=" + this.mWakefulnessChanging);
            pw.println("  mIsPowered=" + this.mIsPowered);
            pw.println("  mPlugType=" + this.mPlugType);
            pw.println("  mBatteryLevel=" + this.mBatteryLevel);
            pw.println("  mBatteryLevelWhenDreamStarted=" + this.mBatteryLevelWhenDreamStarted);
            pw.println("  mDockState=" + this.mDockState);
            pw.println("  mStayOn=" + this.mStayOn);
            pw.println("  mProximityPositive=" + this.mProximityPositive);
            pw.println("  mBootCompleted=" + this.mBootCompleted);
            pw.println("  mSystemReady=" + this.mSystemReady);
            pw.println("  mHalAutoSuspendModeEnabled=" + this.mHalAutoSuspendModeEnabled);
            pw.println("  mHalInteractiveModeEnabled=" + this.mHalInteractiveModeEnabled);
            pw.println("  mWakeLockSummary=0x" + Integer.toHexString(this.mWakeLockSummary));
            pw.print("  mNotifyLongScheduled=");
            if (this.mNotifyLongScheduled == 0) {
                pw.print("(none)");
            } else {
                TimeUtils.formatDuration(this.mNotifyLongScheduled, SystemClock.uptimeMillis(), pw);
            }
            pw.println();
            pw.print("  mNotifyLongDispatched=");
            if (this.mNotifyLongDispatched == 0) {
                pw.print("(none)");
            } else {
                TimeUtils.formatDuration(this.mNotifyLongDispatched, SystemClock.uptimeMillis(), pw);
            }
            pw.println();
            pw.print("  mNotifyLongNextCheck=");
            if (this.mNotifyLongNextCheck == 0) {
                pw.print("(none)");
            } else {
                TimeUtils.formatDuration(this.mNotifyLongNextCheck, SystemClock.uptimeMillis(), pw);
            }
            pw.println();
            pw.println("  mUserActivitySummary=0x" + Integer.toHexString(this.mUserActivitySummary));
            pw.println("  mRequestWaitForNegativeProximity=" + this.mRequestWaitForNegativeProximity);
            pw.println("  mSandmanScheduled=" + this.mSandmanScheduled);
            pw.println("  mSandmanSummoned=" + this.mSandmanSummoned);
            pw.println("  mBatteryLevelLow=" + this.mBatteryLevelLow);
            pw.println("  mLightDeviceIdleMode=" + this.mLightDeviceIdleMode);
            pw.println("  mDeviceIdleMode=" + this.mDeviceIdleMode);
            pw.println("  mDeviceIdleWhitelist=" + Arrays.toString(this.mDeviceIdleWhitelist));
            pw.println("  mDeviceIdleTempWhitelist=" + Arrays.toString(this.mDeviceIdleTempWhitelist));
            pw.println("  mLastWakeTime=" + TimeUtils.formatUptime(this.mLastWakeTime));
            pw.println("  mLastSleepTime=" + TimeUtils.formatUptime(this.mLastSleepTime));
            pw.println("  mLastUserActivityTime=" + TimeUtils.formatUptime(this.mLastUserActivityTime));
            pw.println("  mLastUserActivityTimeNoChangeLights=" + TimeUtils.formatUptime(this.mLastUserActivityTimeNoChangeLights));
            pw.println("  mLastInteractivePowerHintTime=" + TimeUtils.formatUptime(this.mLastInteractivePowerHintTime));
            pw.println("  mLastScreenBrightnessBoostTime=" + TimeUtils.formatUptime(this.mLastScreenBrightnessBoostTime));
            pw.println("  mScreenBrightnessBoostInProgress=" + this.mScreenBrightnessBoostInProgress);
            pw.println("  mDisplayReady=" + this.mDisplayReady);
            pw.println("  mHoldingWakeLockSuspendBlocker=" + this.mHoldingWakeLockSuspendBlocker);
            pw.println("  mHoldingDisplaySuspendBlocker=" + this.mHoldingDisplaySuspendBlocker);
            pw.println();
            pw.println("Settings and Configuration:");
            pw.println("  mDecoupleHalAutoSuspendModeFromDisplayConfig=" + this.mDecoupleHalAutoSuspendModeFromDisplayConfig);
            pw.println("  mDecoupleHalInteractiveModeFromDisplayConfig=" + this.mDecoupleHalInteractiveModeFromDisplayConfig);
            pw.println("  mWakeUpWhenPluggedOrUnpluggedConfig=" + this.mWakeUpWhenPluggedOrUnpluggedConfig);
            pw.println("  mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig=" + this.mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig);
            pw.println("  mTheaterModeEnabled=" + this.mTheaterModeEnabled);
            pw.println("  mSuspendWhenScreenOffDueToProximityConfig=" + this.mSuspendWhenScreenOffDueToProximityConfig);
            pw.println("  mDreamsSupportedConfig=" + this.mDreamsSupportedConfig);
            pw.println("  mDreamsEnabledByDefaultConfig=" + this.mDreamsEnabledByDefaultConfig);
            pw.println("  mDreamsActivatedOnSleepByDefaultConfig=" + this.mDreamsActivatedOnSleepByDefaultConfig);
            pw.println("  mDreamsActivatedOnDockByDefaultConfig=" + this.mDreamsActivatedOnDockByDefaultConfig);
            pw.println("  mDreamsEnabledOnBatteryConfig=" + this.mDreamsEnabledOnBatteryConfig);
            pw.println("  mDreamsBatteryLevelMinimumWhenPoweredConfig=" + this.mDreamsBatteryLevelMinimumWhenPoweredConfig);
            pw.println("  mDreamsBatteryLevelMinimumWhenNotPoweredConfig=" + this.mDreamsBatteryLevelMinimumWhenNotPoweredConfig);
            pw.println("  mDreamsBatteryLevelDrainCutoffConfig=" + this.mDreamsBatteryLevelDrainCutoffConfig);
            pw.println("  mDreamsEnabledSetting=" + this.mDreamsEnabledSetting);
            pw.println("  mDreamsActivateOnSleepSetting=" + this.mDreamsActivateOnSleepSetting);
            pw.println("  mDreamsActivateOnDockSetting=" + this.mDreamsActivateOnDockSetting);
            pw.println("  mDozeAfterScreenOff=" + this.mDozeAfterScreenOff);
            pw.println("  mMinimumScreenOffTimeoutConfig=" + this.mMinimumScreenOffTimeoutConfig);
            pw.println("  mMaximumScreenDimDurationConfig=" + this.mMaximumScreenDimDurationConfig);
            pw.println("  mMaximumScreenDimRatioConfig=" + this.mMaximumScreenDimRatioConfig);
            pw.println("  mScreenOffTimeoutSetting=" + this.mScreenOffTimeoutSetting);
            pw.println("  mSleepTimeoutSetting=" + this.mSleepTimeoutSetting);
            pw.println("  mMaximumScreenOffTimeoutFromDeviceAdmin=" + this.mMaximumScreenOffTimeoutFromDeviceAdmin + " (enforced=" + isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked() + Separators.RPAREN);
            StringBuilder sb = new StringBuilder();
            sb.append("  mStayOnWhilePluggedInSetting=");
            sb.append(this.mStayOnWhilePluggedInSetting);
            pw.println(sb.toString());
            pw.println("  mScreenBrightnessSetting=" + this.mScreenBrightnessSetting);
            pw.println("  mScreenBrightnessModeSetting=" + this.mScreenBrightnessModeSetting);
            pw.println("  mScreenBrightnessOverrideFromWindowManager=" + this.mScreenBrightnessOverrideFromWindowManager);
            pw.println("  mUserActivityTimeoutOverrideFromWindowManager=" + this.mUserActivityTimeoutOverrideFromWindowManager);
            pw.println("  mUserActivityTimeoutForDexOverrideFromWindowManager=" + this.mUserActivityTimeoutForDexOverrideFromWindowManager);
            pw.println("  mUserInactiveOverrideFromWindowManager=" + this.mUserInactiveOverrideFromWindowManager);
            pw.println("  mDozeScreenStateOverrideFromDreamManager=" + this.mDozeScreenStateOverrideFromDreamManager);
            pw.println("  mDrawWakeLockOverrideFromSidekick=" + this.mDrawWakeLockOverrideFromSidekick);
            pw.println("  mDozeScreenBrightnessOverrideFromDreamManager=" + this.mDozeScreenBrightnessOverrideFromDreamManager);
            pw.println("  mScreenBrightnessSettingMinimum=" + this.mScreenBrightnessSettingMinimum);
            pw.println("  mScreenBrightnessSettingMaximum=" + this.mScreenBrightnessSettingMaximum);
            pw.println("  mScreenBrightnessSettingDefault=" + this.mScreenBrightnessSettingDefault);
            pw.println("  mDoubleTapWakeEnabled=" + this.mDoubleTapWakeEnabled);
            pw.println("  mIsVrModeEnabled=" + this.mIsVrModeEnabled);
            pw.println("  mIsGearVrModeEnabled=" + this.mIsGearVrModeEnabled);
            pw.println("  mIsGearVrPersistentVrModeEnabled=" + this.mIsGearVrPersistentVrModeEnabled);
            pw.println("  mIsGearVrDocked=" + this.mIsGearVrDocked);
            pw.println("  mIsGearVrTetheredDocked=" + this.mIsGearVrTetheredDocked);
            pw.println("  mForegroundProfile=" + this.mForegroundProfile);
            pw.println("  mLastWakeUpReason: " + this.mLastWakeUpReason);
            pw.println("  mOutdoorModeSetting: " + this.mOutdoorModeSetting);
            pw.println("  mAutoBrightnessLowerLimit=" + this.mAutoBrightnessLowerLimit);
            pw.println("  mAutoBrightnessUpperLimit=" + this.mAutoBrightnessUpperLimit);
            pw.println("  mLastAutoBrightnessLimitTime=" + this.mLastAutoBrightnessLimitTime);
            pw.println("  mIsSupportedLightSensor: " + this.mIsSupportedLightSensor);
            pw.println("  SEC_FEATURE_USE_GED_DOZE: " + PowerManagerUtil.SEC_FEATURE_USE_GED_DOZE);
            pw.println("  SEC_FEATURE_SEAMLESS_AOD: " + PowerManagerUtil.SEC_FEATURE_SEAMLESS_AOD);
            pw.println("  SEC_FEATURE_WA_WAITING_AOD_WHEN_WAKINGUP_FROM_DOZE: " + PowerManagerUtil.SEC_FEATURE_WA_WAITING_AOD_WHEN_WAKINGUP_FROM_DOZE);
            pw.println("  SEC_FEATURE_WA_COLOR_FADE_WHEN_WAKINGUP_FROM_DOZE: " + PowerManagerUtil.SEC_FEATURE_WA_COLOR_FADE_WHEN_WAKINGUP_FROM_DOZE);
            pw.println("  SEC_FEATURE_PWRUI_SUPPORT_BATTERY_CHARGER_CONNECTION_VI: " + PowerManagerUtil.SEC_FEATURE_PWRUI_SUPPORT_BATTERY_CHARGER_CONNECTION_VI);
            pw.println("  SEC_FEATURE_WAKEUP_WHEN_PLUG_CHANGED: " + PowerManagerUtil.SEC_FEATURE_WAKEUP_WHEN_PLUG_CHANGED);
            pw.println("  mAodMode: " + this.mAodMode);
            pw.println("  SEC_FEATURE_DEX_DUAL_VIEW: true");
            long sleepTimeout = getSleepTimeoutLocked();
            long screenOffTimeout = getScreenOffTimeoutLocked(sleepTimeout);
            long screenDimDuration = getScreenDimDurationLocked(screenOffTimeout);
            pw.println();
            pw.println("Sleep timeout: " + sleepTimeout + " ms");
            pw.println("Screen off timeout: " + screenOffTimeout + " ms");
            pw.println("Screen dim duration: " + screenDimDuration + " ms");
            pw.println("Screen dim duration override: " + this.mScreenDimDurationOverrideFromWindowManager + " ms");
            pw.println("Screen off timeout for DeX: " + this.mScreenOffTimeoutForDex + " ms");
            pw.println();
            pw.println("Smart Stay:");
            pw.println("  USE_SMART_STAY: true");
            pw.println("  USE_PRE_SMART_STAY: " + this.USE_PRE_SMART_STAY);
            pw.println("  mSmartStayEnabledSetting: " + this.mSmartStayEnabledSetting);
            pw.println("  SmartStayDelay: 2750");
            pw.println("  mNextTimeoutForSmartStay: " + this.mNextTimeoutForSmartStay);
            pw.println("  mPendingMessageSmartStay: " + this.mPendingMessageSmartStay);
            pw.println("  mPendingMessagePreSmartStay: " + this.mPendingMessagePreSmartStay);
            pw.println("  mFaceDetected: " + this.mFaceDetected);
            pw.println("  mIsBadCurrentConsumptionDevice: true");
            pw.println();
            pw.println("Wakelock Blacklist App id: size=" + this.mWakelockBlacklistAppid.size());
            Iterator<Integer> it = this.mWakelockBlacklistAppid.iterator();
            while (it.hasNext()) {
                int appId = it.next().intValue();
                pw.println("  " + appId);
            }
            if (PowerManagerUtil.SEC_FEATURE_AFC) {
                pw.println();
                pw.println("mDisplayOnTime: " + this.mDisplayOnTime);
                pw.println("mSentAllDisplayOnTimeIntent: " + this.mSentAllDisplayOnTimeIntent);
            }
            pw.println();
            pw.println("mSmartViewConnected: " + this.mSmartViewConnected);
            pw.println();
            pw.print("UID states (changing=");
            pw.print(this.mUidsChanging);
            pw.print(" changed=");
            pw.print(this.mUidsChanged);
            pw.println("):");
            for (int i = 0; i < this.mUidState.size(); i++) {
                UidState state = this.mUidState.valueAt(i);
                pw.print("  UID ");
                UserHandle.formatUid(pw, this.mUidState.keyAt(i));
                pw.print(PluralRules.KEYWORD_RULE_SEPARATOR);
                if (state.mActive) {
                    pw.print("  ACTIVE ");
                } else {
                    pw.print("INACTIVE ");
                }
                pw.print(" count=");
                pw.print(state.mNumWakeLocks);
                pw.print(" state=");
                pw.println(state.mProcState);
            }
            pw.println();
            pw.println("InputDeviceLightState:");
            pw.println("  mTouchKeyOffTimeoutSetting: " + this.mTouchKeyOffTimeoutSetting);
            if (this.mIsFolderType) {
                pw.println("  mIsLidClosed: " + this.mIsLidClosed);
                pw.println("  mKeyboardOffTimeoutSetting: " + this.mKeyboardOffTimeoutSetting);
                pw.println("  mKeyLedOffNightModeSetting: " + this.mKeyLedOffNightModeSetting);
            }
            pw.println("  mIsSipVisible: " + this.mIsSipVisible);
            pw.println("  mTouchKeyForceDisable: " + this.mTouchKeyForceDisable);
            pw.println("  mTouchKeyForceDisableOverrideFromSystemPowerSaveMode: " + this.mTouchKeyForceDisableOverrideFromSystemPowerSaveMode);
            pw.println("  mInputDeviceEnabled: " + this.mInputDeviceEnabled);
            pw.println("  mIsFolderType: " + this.mIsFolderType);
            pw.println();
            pw.println("Looper state:");
            this.mHandler.getLooper().dump(new PrintWriterPrinter(pw), "  ");
            pw.println("Clear Cover:");
            pw.println("  mIsCoverClosed: " + this.mIsCoverClosed);
            pw.println("  mlastUpdateCoverStateReason: " + this.mlastUpdateCoverStateReason);
            pw.println("  mlastUpdateCoverStateTime: = " + TimeUtils.formatUptime(this.mlastUpdateCoverStateTime));
            pw.println("  mFeatureCoverSysfs: " + this.mFeatureCoverSysfs);
            pw.println("  mCoverType: " + this.mCoverType + " at " + TimeUtils.formatUptime(this.mlastSetCoverTypeTime));
            StringBuilder sb2 = new StringBuilder();
            sb2.append("  mlastUpdateCoverTypeReason: ");
            sb2.append(this.mlastUpdateCoverTypeReason);
            pw.println(sb2.toString());
            pw.println();
            pw.println("FTA mode: " + this.mFTAMode);
            pw.println("SEC_USE_FACTORY_BINARY: " + SEC_USE_FACTORY_BINARY);
            pw.println();
            pw.println("Wake Locks: size=" + this.mWakeLocks.size());
            Iterator<WakeLock> it2 = this.mWakeLocks.iterator();
            while (it2.hasNext()) {
                WakeLock wl = it2.next();
                pw.println("  " + wl);
            }
            pw.println();
            pw.println("Suspend Blockers: size=" + this.mSuspendBlockers.size());
            Iterator<SuspendBlocker> it3 = this.mSuspendBlockers.iterator();
            while (it3.hasNext()) {
                SuspendBlocker sb3 = it3.next();
                pw.println("  " + sb3);
            }
            pw.println();
            pw.println("Display Power: " + this.mDisplayPowerCallbacks);
            pw.println();
            pw.println("mFreezingScreenBrightness: " + this.mFreezingScreenBrightness);
            pw.println();
            pw.println("WakeUp History size: " + mWakeUpHistory.size());
            WakeUpHistory[] events = (WakeUpHistory[]) mWakeUpHistory.toArray();
            for (int i2 = 0; i2 < events.length; i2++) {
                pw.print(TimaKeychain.CREATOR_LEFT_SEPARATOR + i2 + HqmDBHelper.NAME_END);
                StringBuilder sb4 = new StringBuilder();
                sb4.append(" uid=");
                sb4.append(events[i2].uid);
                pw.print(sb4.toString());
                pw.print(", timeMillis=" + events[i2].timeMillis);
                pw.print(", time=" + events[i2].time);
                pw.print(", reasonNum=" + events[i2].reasonNum);
                pw.print(", reasonStr=" + events[i2].reasonStr);
                pw.print(", packageName=" + events[i2].packageName);
                pw.print(", topActivity=" + events[i2].topActivity);
                pw.print(", topPackage=" + events[i2].topPackage);
                pw.println();
            }
            pw.println();
            pw.println("AutoBrightness History size: " + PowerManagerUtil.History.getAutoBrightnessSize());
            PowerManagerUtil.History.HistoryInternal[] history = PowerManagerUtil.History.getAutoBrightness();
            for (int i3 = 0; i3 < history.length; i3++) {
                pw.print(TimaKeychain.CREATOR_LEFT_SEPARATOR + i3 + HqmDBHelper.NAME_END);
                StringBuilder sb5 = new StringBuilder();
                sb5.append(" ");
                sb5.append(history[i3].time);
                pw.print(sb5.toString());
                pw.print(" " + history[i3].log);
                pw.println();
            }
            pw.println();
            pw.println("Brightness ChangeReason History size: " + PowerManagerUtil.History.getChangeReasonSize());
            PowerManagerUtil.History.HistoryInternal[] history2 = PowerManagerUtil.History.getChangeReason();
            for (int i4 = 0; i4 < history2.length; i4++) {
                pw.print(TimaKeychain.CREATOR_LEFT_SEPARATOR + i4 + HqmDBHelper.NAME_END);
                StringBuilder sb6 = new StringBuilder();
                sb6.append(" ");
                sb6.append(history2[i4].time);
                pw.print(sb6.toString());
                pw.print(" " + history2[i4].log);
                pw.println();
            }
            this.mBatterySaverPolicy.dump(pw);
            this.mBatterySaverStateMachine.dump(pw);
            pw.println();
            int numProfiles = this.mProfilePowerState.size();
            pw.println("Profile power states: size=" + numProfiles);
            for (int i5 = 0; i5 < numProfiles; i5++) {
                ProfilePowerState profile = this.mProfilePowerState.valueAt(i5);
                pw.print("  mUserId=");
                pw.print(profile.mUserId);
                pw.print(" mScreenOffTimeout=");
                pw.print(profile.mScreenOffTimeout);
                pw.print(" mScreenOffTimeoutForUser=");
                pw.print(profile.mScreenOffTimeoutForUser);
                pw.print(" mWakeLockSummary=");
                pw.print(profile.mWakeLockSummary);
                pw.print(" mLastUserActivityTime=");
                pw.print(profile.mLastUserActivityTime);
                pw.print(" mLockingNotified=");
                pw.println(profile.mLockingNotified);
            }
            wcd = this.mWirelessChargerDetector;
        }
        if (wcd != null) {
            wcd.dump(pw);
        }
        pw.println("\n\n ====================== beginning of PMS  log ======================");
        pw.println("  Day       Time    Tid Debug     TAG            INFO");
        pw.println(" ===================================================================");
        Slog.savePMSLog(pw);
        pw.println(" ======================= end of PMS log ========================");
        pw.println(" PMSLog Done !!! \n\n");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dumpProto(FileDescriptor fd) {
        int[] iArr;
        int[] iArr2;
        WirelessChargerDetector wcd;
        ProtoOutputStream proto = new ProtoOutputStream(fd);
        synchronized (this.mLock) {
            this.mConstants.dumpProto(proto);
            proto.write(1120986464258L, this.mDirty);
            proto.write(1159641169923L, this.mWakefulness);
            proto.write(1133871366148L, this.mWakefulnessChanging);
            proto.write(1133871366149L, this.mIsPowered);
            proto.write(1159641169926L, this.mPlugType);
            proto.write(1120986464263L, this.mBatteryLevel);
            proto.write(1120986464264L, this.mBatteryLevelWhenDreamStarted);
            proto.write(1159641169929L, this.mDockState);
            proto.write(1133871366154L, this.mStayOn);
            proto.write(1133871366155L, this.mProximityPositive);
            proto.write(1133871366156L, this.mBootCompleted);
            proto.write(1133871366157L, this.mSystemReady);
            proto.write(1133871366158L, this.mHalAutoSuspendModeEnabled);
            proto.write(SystemPropertiesProto.Ro.COM_ANDROID_PROV_MOBILEDATA, this.mHalInteractiveModeEnabled);
            long activeWakeLocksToken = proto.start(1146756268048L);
            int i = 0;
            proto.write(1133871366145L, (this.mWakeLockSummary & 1) != 0);
            proto.write(1133871366146L, (this.mWakeLockSummary & 2) != 0);
            proto.write(1133871366147L, (this.mWakeLockSummary & 4) != 0);
            proto.write(1133871366148L, (this.mWakeLockSummary & 8) != 0);
            proto.write(1133871366149L, (this.mWakeLockSummary & 16) != 0);
            proto.write(1133871366150L, (this.mWakeLockSummary & 32) != 0);
            proto.write(1133871366151L, (this.mWakeLockSummary & 64) != 0);
            proto.write(1133871366152L, (this.mWakeLockSummary & 128) != 0);
            proto.end(activeWakeLocksToken);
            proto.write(1112396529681L, this.mNotifyLongScheduled);
            proto.write(UidProto.Network.WIFI_BYTES_BG_TX, this.mNotifyLongDispatched);
            proto.write(1112396529683L, this.mNotifyLongNextCheck);
            long userActivityToken = proto.start(1146756268052L);
            proto.write(1133871366145L, (this.mUserActivitySummary & 1) != 0);
            proto.write(1133871366146L, (this.mUserActivitySummary & 2) != 0);
            proto.write(1133871366147L, (this.mUserActivitySummary & 4) != 0);
            proto.end(userActivityToken);
            proto.write(1133871366165L, this.mRequestWaitForNegativeProximity);
            proto.write(1133871366166L, this.mSandmanScheduled);
            proto.write(1133871366167L, this.mSandmanSummoned);
            proto.write(SystemPropertiesProto.Ro.DEBUGGABLE, this.mBatteryLevelLow);
            proto.write(1133871366169L, this.mLightDeviceIdleMode);
            proto.write(1133871366170L, this.mDeviceIdleMode);
            for (int id : this.mDeviceIdleWhitelist) {
                proto.write(2220498092059L, id);
            }
            for (int id2 : this.mDeviceIdleTempWhitelist) {
                proto.write(2220498092060L, id2);
            }
            proto.write(1112396529693L, this.mLastWakeTime);
            proto.write(1112396529694L, this.mLastSleepTime);
            proto.write(1112396529695L, this.mLastUserActivityTime);
            proto.write(1112396529696L, this.mLastUserActivityTimeNoChangeLights);
            proto.write(1112396529697L, this.mLastInteractivePowerHintTime);
            proto.write(1112396529698L, this.mLastScreenBrightnessBoostTime);
            proto.write(1133871366179L, this.mScreenBrightnessBoostInProgress);
            proto.write(1133871366180L, this.mDisplayReady);
            proto.write(SystemPropertiesProto.Ro.STORAGE_MANAGER_ENABLED, this.mHoldingWakeLockSuspendBlocker);
            proto.write(1133871366182L, this.mHoldingDisplaySuspendBlocker);
            long settingsAndConfigurationToken = proto.start(1146756268071L);
            proto.write(1133871366145L, this.mDecoupleHalAutoSuspendModeFromDisplayConfig);
            proto.write(1133871366146L, this.mDecoupleHalInteractiveModeFromDisplayConfig);
            proto.write(1133871366147L, this.mWakeUpWhenPluggedOrUnpluggedConfig);
            proto.write(1133871366148L, this.mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig);
            proto.write(1133871366149L, this.mTheaterModeEnabled);
            proto.write(1133871366150L, this.mSuspendWhenScreenOffDueToProximityConfig);
            proto.write(1133871366151L, this.mDreamsSupportedConfig);
            proto.write(1133871366152L, this.mDreamsEnabledByDefaultConfig);
            proto.write(1133871366153L, this.mDreamsActivatedOnSleepByDefaultConfig);
            proto.write(1133871366154L, this.mDreamsActivatedOnDockByDefaultConfig);
            proto.write(1133871366155L, this.mDreamsEnabledOnBatteryConfig);
            proto.write(PsProto.Process.NI, this.mDreamsBatteryLevelMinimumWhenPoweredConfig);
            proto.write(1172526071821L, this.mDreamsBatteryLevelMinimumWhenNotPoweredConfig);
            proto.write(1172526071822L, this.mDreamsBatteryLevelDrainCutoffConfig);
            proto.write(SystemPropertiesProto.Ro.COM_ANDROID_PROV_MOBILEDATA, this.mDreamsEnabledSetting);
            proto.write(ApplicationInfoProto.Detail.IS_FULL_BACKUP, this.mDreamsActivateOnSleepSetting);
            proto.write(1133871366161L, this.mDreamsActivateOnDockSetting);
            proto.write(1133871366162L, this.mDozeAfterScreenOff);
            proto.write(1120986464275L, this.mMinimumScreenOffTimeoutConfig);
            proto.write(SystemProto.Misc.LIGHT_DOZE_IDLING_COUNT, this.mMaximumScreenDimDurationConfig);
            proto.write(1108101562389L, this.mMaximumScreenDimRatioConfig);
            proto.write(SystemPropertiesProto.DalvikVm.IMAGE_DEX2OAT_THREADS, this.mScreenOffTimeoutSetting);
            proto.write(1172526071831L, this.mSleepTimeoutSetting);
            proto.write(1120986464280L, Math.min(this.mMaximumScreenOffTimeoutFromDeviceAdmin, 2147483647L));
            proto.write(1133871366169L, isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked());
            long stayOnWhilePluggedInToken = proto.start(1146756268058L);
            proto.write(1133871366145L, (this.mStayOnWhilePluggedInSetting & 1) != 0);
            proto.write(1133871366146L, (this.mStayOnWhilePluggedInSetting & 2) != 0);
            proto.write(1133871366147L, (this.mStayOnWhilePluggedInSetting & 4) != 0);
            proto.end(stayOnWhilePluggedInToken);
            proto.write(SystemPropertiesProto.InitSvc.SURFACEFLINGER, this.mScreenBrightnessModeSetting);
            proto.write(1172526071836L, this.mScreenBrightnessOverrideFromWindowManager);
            proto.write(1176821039133L, this.mUserActivityTimeoutOverrideFromWindowManager);
            proto.write(1133871366174L, this.mUserInactiveOverrideFromWindowManager);
            proto.write(SystemPropertiesProto.InitSvc.UPDATE_ENGINE, this.mDozeScreenStateOverrideFromDreamManager);
            proto.write(1133871366180L, this.mDrawWakeLockOverrideFromSidekick);
            proto.write(1108101562400L, this.mDozeScreenBrightnessOverrideFromDreamManager);
            long screenBrightnessSettingLimitsToken = proto.start(1146756268065L);
            proto.write(1120986464257L, this.mScreenBrightnessSettingMinimum);
            proto.write(1120986464258L, this.mScreenBrightnessSettingMaximum);
            proto.write(1120986464259L, this.mScreenBrightnessSettingDefault);
            proto.end(screenBrightnessSettingLimitsToken);
            proto.write(1133871366178L, this.mDoubleTapWakeEnabled);
            proto.write(1133871366179L, this.mIsVrModeEnabled);
            proto.end(settingsAndConfigurationToken);
            long sleepTimeout = getSleepTimeoutLocked();
            long screenOffTimeout = getScreenOffTimeoutLocked(sleepTimeout);
            long screenDimDuration = getScreenDimDurationLocked(screenOffTimeout);
            proto.write(1172526071848L, sleepTimeout);
            proto.write(SystemPropertiesProto.DalvikVm.JITPRITHREADWEIGHT, screenOffTimeout);
            long screenDimDuration2 = screenDimDuration;
            proto.write(SystemPropertiesProto.DalvikVm.JITTHRESHOLD, screenDimDuration2);
            proto.write(1133871366187L, this.mUidsChanging);
            proto.write(1133871366188L, this.mUidsChanged);
            while (true) {
                int i2 = i;
                if (i2 >= this.mUidState.size()) {
                    break;
                }
                UidState state = this.mUidState.valueAt(i2);
                long screenDimDuration3 = screenDimDuration2;
                long uIDToken = proto.start(2246267895853L);
                int uid = this.mUidState.keyAt(i2);
                proto.write(1120986464257L, uid);
                proto.write(1138166333442L, UserHandle.formatUid(uid));
                proto.write(1133871366147L, state.mActive);
                proto.write(1120986464260L, state.mNumWakeLocks);
                proto.write(1159641169925L, ActivityManager.processStateAmToProto(state.mProcState));
                proto.end(uIDToken);
                i = i2 + 1;
                screenDimDuration2 = screenDimDuration3;
                settingsAndConfigurationToken = settingsAndConfigurationToken;
                stayOnWhilePluggedInToken = stayOnWhilePluggedInToken;
            }
            this.mBatterySaverStateMachine.dumpProto(proto, 1146756268082L);
            this.mHandler.getLooper().writeToProto(proto, 1146756268078L);
            Iterator<WakeLock> it = this.mWakeLocks.iterator();
            while (it.hasNext()) {
                WakeLock wl = it.next();
                wl.writeToProto(proto, 2246267895855L);
            }
            Iterator<SuspendBlocker> it2 = this.mSuspendBlockers.iterator();
            while (it2.hasNext()) {
                SuspendBlocker sb = it2.next();
                sb.writeToProto(proto, 2246267895856L);
            }
            wcd = this.mWirelessChargerDetector;
        }
        if (wcd != null) {
            wcd.writeToProto(proto, 1146756268081L);
        }
        proto.flush();
    }

    private SuspendBlocker createSuspendBlockerLocked(String name) {
        SuspendBlocker suspendBlocker = new SuspendBlockerImpl(name);
        this.mSuspendBlockers.add(suspendBlocker);
        return suspendBlocker;
    }

    private void incrementBootCount() {
        int count;
        synchronized (this.mLock) {
            try {
                count = Settings.Global.getInt(getContext().getContentResolver(), Settings.Global.BOOT_COUNT);
            } catch (Settings.SettingNotFoundException e) {
                count = 0;
            }
            Settings.Global.putInt(getContext().getContentResolver(), Settings.Global.BOOT_COUNT, count + 1);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static WorkSource copyWorkSource(WorkSource workSource) {
        if (workSource != null) {
            return new WorkSource(workSource);
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setDozeModeBySysfs(int mode) {
        if (this.mAlpmHlpmMode != mode) {
            if (mode == -1) {
                Slog.d(TAG, "setDozeModeBySysfs:  value is -1 , we ignore it. ");
                return;
            }
            this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            this.mAlpmHlpmMode = mode;
            String value = Integer.toString(mode);
            PowerManagerUtil.fileWriteString("/sys/class/lcd/panel/alpm", value);
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class KnoxTimeoutReceiver extends BroadcastReceiver {
        private KnoxTimeoutReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int userHandle = intent.getIntExtra("android.intent.extra.user_handle", -1);
            Slog.d(PowerManagerService.TAG, "[api] KnoxTimeoutReceiver: onReceive: " + intent.getAction() + ", userHandle: " + userHandle);
            if (action.equals(PowerManagerService.ACTION_KNOX_LOCK_TIMEOUT) && PowerManagerService.this.mWakefulness != 1) {
                PowerManagerService.this.mKnoxHandler.sendMessage(PowerManagerService.this.mKnoxHandler.obtainMessage(2, userHandle, 0));
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class BatteryReceiver extends BroadcastReceiver {
        private BatteryReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService.this.handleBatteryStateChangedLocked();
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class DreamReceiver extends BroadcastReceiver {
        private DreamReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            Slog.d(PowerManagerService.TAG, "[api] DreamReceiver: onReceive: " + intent.getAction());
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService.this.scheduleSandmanLocked();
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class UserSwitchedReceiver extends BroadcastReceiver {
        private UserSwitchedReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            Slog.d(PowerManagerService.TAG, "[api] UserSwitchedReceiver: onReceive: UserHandle: " + ActivityManager.getCurrentUser() + " -> " + intent.getIntExtra("android.intent.extra.user_handle", 0));
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService.this.handleSettingsChangedLocked();
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class DockReceiver extends BroadcastReceiver {
        private DockReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            synchronized (PowerManagerService.this.mLock) {
                int dockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, 0);
                Slog.d(PowerManagerService.TAG, "[api] DockReceiver: onReceive: DockState: " + PowerManagerService.this.mDockState + " -> " + dockState);
                if (PowerManagerService.this.mDockState != dockState) {
                    PowerManagerService.this.mDockState = dockState;
                    PowerManagerService.access$2176(PowerManagerService.this, 1024);
                    PowerManagerService.this.updatePowerStateLocked();
                }
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class FTAModeChangeReceiver extends BroadcastReceiver {
        private FTAModeChangeReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            Slog.d(PowerManagerService.TAG, "[api] FTAModeChangeReceiver: onReceive: ");
            String action = intent.getAction();
            synchronized (PowerManagerService.this.mLock) {
                if (action.equals(PowerManagerService.ACTION_FTA_ON)) {
                    PowerManagerService.this.mFTAMode = true;
                    Slog.d(PowerManagerService.TAG, "FTA mode ON");
                } else if (action.equals(PowerManagerService.ACTION_FTA_OFF)) {
                    PowerManagerService.this.mFTAMode = false;
                    Slog.d(PowerManagerService.TAG, "FTA mode OFF");
                }
                PowerManagerService.access$2176(PowerManagerService.this, 8);
                PowerManagerService.this.updatePowerStateLocked();
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange, Uri uri) {
            Slog.d(PowerManagerService.TAG, "[api] SettingsObserver: onChange: " + uri);
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService.this.handleSettingsChangedLocked();
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class DexScreenOffTimeoutObserver extends ContentObserver {
        public DexScreenOffTimeoutObserver(Handler handler) {
            super(handler);
        }

        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            PowerManagerService.this.handleDexScreenOffTimeoutChange();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleDexScreenOffTimeoutChange() {
        this.mHandler.post(new Runnable() { // from class: com.android.server.power.PowerManagerService.12
            @Override // java.lang.Runnable
            public void run() {
                Bundle extras = new Bundle();
                extras.putString("key", "timeout_dex");
                extras.putString("def", PowerManagerService.DEFAULT_SCREEN_TIMEOUT_VALUE);
                try {
                    Bundle bundle = PowerManagerService.this.mContext.getContentResolver().call(PowerManagerService.DEX_SETTINGS_URI, "getSettings", null, extras);
                    if (bundle != null) {
                        String timeoutForDex = bundle.getString("timeout_dex");
                        int timeout = Integer.parseInt(timeoutForDex);
                        synchronized (PowerManagerService.this.mLock) {
                            if (timeout != PowerManagerService.this.mScreenOffTimeoutForDex) {
                                Slog.d(PowerManagerService.TAG, "Dex screen off timeout : " + PowerManagerService.this.mScreenOffTimeoutForDex + " -> " + timeout);
                                PowerManagerService.this.mScreenOffTimeoutForDex = timeout;
                                PowerManagerService.access$2176(PowerManagerService.this, 4);
                                PowerManagerService.this.updatePowerStateLocked();
                            }
                        }
                        Slog.d(PowerManagerService.TAG, "timeout for dex is " + timeoutForDex + " = " + PowerManagerService.this.mScreenOffTimeoutForDex);
                    }
                } catch (IllegalArgumentException e) {
                    Slog.e(PowerManagerService.TAG, "IllegalArgumentException :: getDeXSettings timeout_dex");
                }
            }
        });
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class ShutdownReceiver extends BroadcastReceiver {
        private ShutdownReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SHUTDOWN.equals(action)) {
                Slog.d(PowerManagerService.TAG, "[api] ShutdownReceiver: onReceive: " + action);
                if (PowerManagerUtil.SEC_FEATURE_AFC && !PowerManagerService.this.mSentAllDisplayOnTimeIntent) {
                    PowerManagerService.this.writeDisplayOnTimetoEfs();
                }
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class SmartViewReceiver extends BroadcastReceiver {
        private SmartViewReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            boolean wifiDisplayStatus = false;
            boolean googleCastStatus = false;
            boolean z = false;
            if (action.equals("com.samsung.intent.action.WIFI_DISPLAY_SOURCE_STATE")) {
                int status = intent.getIntExtra("state", -1);
                Slog.d(PowerManagerService.TAG, "[api] SmartViewReceiver: WIFI" + action);
                if (status == 1) {
                    wifiDisplayStatus = true;
                } else if (status == 0) {
                    wifiDisplayStatus = false;
                }
            } else if (action.equals("com.samsung.intent.action.GOOGLE_CAST_MIRRORING_CONNECTION_CHANGED")) {
                int status2 = intent.getIntExtra("state", 0);
                Slog.d(PowerManagerService.TAG, "[api] SmartViewReceiver: GOOGLE_CAST" + action);
                if (status2 == 1) {
                    googleCastStatus = true;
                } else if (status2 == 0) {
                    googleCastStatus = false;
                }
            }
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService powerManagerService = PowerManagerService.this;
                if (!wifiDisplayStatus && !googleCastStatus) {
                    powerManagerService.mSmartViewConnected = z;
                    Slog.d(PowerManagerService.TAG, "[api] mSmartViewConnected: : " + PowerManagerService.this.mSmartViewConnected);
                }
                z = true;
                powerManagerService.mSmartViewConnected = z;
                Slog.d(PowerManagerService.TAG, "[api] mSmartViewConnected: : " + PowerManagerService.this.mSmartViewConnected);
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class PmsLoggingReceiver extends BroadcastReceiver {
        private PmsLoggingReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(PowerManagerService.ACTION_PMS_LOGGING)) {
                Slog.d(PowerManagerService.TAG, "[api] PmsLoggingReceiver: onReceive: " + action);
                PowerManagerService.this.sendDPSIDuration();
                PowerManagerService.this.sendCurrentBrightnessStatus();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isVrModeEnabled() {
        boolean result;
        synchronized (this.mLock) {
            result = (this.mIsVrModeEnabled || this.mIsGearVrPersistentVrModeEnabled || this.mIsGearVrModeEnabled) && isInteractiveInternal() && !this.mIsGearVrTetheredDocked;
        }
        return result;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public final class PowerManagerHandler extends Handler {
        public PowerManagerHandler(Looper looper) {
            super(looper, null, true);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    if (!PowerManagerService.this.mSmartStayEnabledSetting) {
                        PowerManagerService.this.handleAbuseWakelockWhenUseractivityChanged();
                    }
                    PowerManagerService.this.handleUserActivityTimeout();
                    return;
                case 2:
                    PowerManagerService.this.handleSandman();
                    return;
                case 3:
                    PowerManagerService.this.handleScreenBrightnessBoostTimeout();
                    return;
                case 4:
                    PowerManagerService.this.checkForLongWakeLocks();
                    return;
                case 5:
                    PowerManagerService.this.handleInputDeviceLightOnByScreenOn();
                    return;
                case 6:
                    PowerManagerService.this.handleSendBroadcastToHWParam(PowerManagerService.DISPLAY_POWERMANAGERSERVICE_USER_CHANGE, (String) msg.obj);
                    return;
                case 7:
                    PowerManagerService.this.handleSendBroadcastToHWParam(PowerManagerService.DISPLAY_POWERMANAGERSERVICE_LCD_DURATION, (String) msg.obj);
                    return;
                case 8:
                    PowerManagerService.this.handleSendBroadcastToHWParam(PowerManagerService.DISPLAY_POWERMANAGERSERVICE_SETTING_INFO, (String) msg.obj);
                    return;
                default:
                    return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public final class WakeLock implements IBinder.DeathRecipient {
        public long mAcquireTime;
        public boolean mDisabled;
        public boolean mDisabledByFreecess;
        public int mFlags;
        public String mHistoryTag;
        public final IBinder mLock;
        public boolean mNotifiedAcquired;
        public boolean mNotifiedLong;
        public final int mOwnerPid;
        public final int mOwnerUid;
        public final String mPackageName;
        public int mProximityNegativeDebounce;
        public int mProximityPositiveDebounce;
        public String mTag;
        public final UidState mUidState;
        public WorkSource mWorkSource;

        public WakeLock(IBinder lock, int flags, String tag, String packageName, WorkSource workSource, String historyTag, int ownerUid, int ownerPid, UidState uidState) {
            this.mLock = lock;
            this.mFlags = flags;
            this.mTag = tag;
            this.mPackageName = packageName;
            this.mWorkSource = PowerManagerService.copyWorkSource(workSource);
            this.mHistoryTag = historyTag;
            this.mOwnerUid = ownerUid;
            this.mOwnerPid = ownerPid;
            this.mUidState = uidState;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            PowerManagerService.this.handleWakeLockDeath(this);
        }

        public boolean hasSameProperties(int flags, String tag, WorkSource workSource, int ownerUid, int ownerPid) {
            return this.mFlags == flags && this.mTag.equals(tag) && hasSameWorkSource(workSource) && this.mOwnerUid == ownerUid && this.mOwnerPid == ownerPid;
        }

        public void updateProperties(int flags, String tag, String packageName, WorkSource workSource, String historyTag, int ownerUid, int ownerPid) {
            if (!this.mPackageName.equals(packageName)) {
                throw new IllegalStateException("Existing wake lock package name changed: " + this.mPackageName + " to " + packageName);
            } else if (this.mOwnerUid != ownerUid) {
                throw new IllegalStateException("Existing wake lock uid changed: " + this.mOwnerUid + " to " + ownerUid);
            } else if (this.mOwnerPid != ownerPid) {
                throw new IllegalStateException("Existing wake lock pid changed: " + this.mOwnerPid + " to " + ownerPid);
            } else {
                this.mFlags = flags;
                this.mTag = tag;
                updateWorkSource(workSource);
                this.mHistoryTag = historyTag;
            }
        }

        public boolean hasSameWorkSource(WorkSource workSource) {
            return Objects.equals(this.mWorkSource, workSource);
        }

        public void updateWorkSource(WorkSource workSource) {
            this.mWorkSource = PowerManagerService.copyWorkSource(workSource);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getLockLevelString());
            sb.append(" '");
            sb.append(this.mTag);
            sb.append(Separators.QUOTE);
            sb.append(getLockFlagsString());
            if (this.mDisabled) {
                sb.append(" DISABLED");
            }
            if (this.mNotifiedAcquired) {
                sb.append(" ACQ=");
                TimeUtils.formatDuration(this.mAcquireTime - SystemClock.uptimeMillis(), sb);
            }
            if (this.mNotifiedLong) {
                sb.append(" LONG");
            }
            sb.append(" (uid=");
            sb.append(this.mOwnerUid);
            if (this.mOwnerPid != 0) {
                sb.append(" pid=");
                sb.append(this.mOwnerPid);
            }
            if (this.mWorkSource != null) {
                sb.append(" ws=");
                sb.append(this.mWorkSource);
            }
            sb.append(Separators.RPAREN);
            return sb.toString();
        }

        public void writeToProto(ProtoOutputStream proto, long fieldId) {
            long wakeLockToken = proto.start(fieldId);
            proto.write(1159641169921L, this.mFlags & 65535);
            proto.write(1138166333442L, this.mTag);
            long wakeLockFlagsToken = proto.start(1146756268035L);
            proto.write(1133871366145L, (this.mFlags & 268435456) != 0);
            proto.write(1133871366146L, (this.mFlags & 536870912) != 0);
            proto.end(wakeLockFlagsToken);
            proto.write(1133871366148L, this.mDisabled);
            if (this.mNotifiedAcquired) {
                proto.write(1112396529669L, this.mAcquireTime);
            }
            proto.write(1133871366150L, this.mNotifiedLong);
            proto.write(1120986464263L, this.mOwnerUid);
            proto.write(1120986464264L, this.mOwnerPid);
            if (this.mWorkSource != null) {
                this.mWorkSource.writeToProto(proto, 1146756268041L);
            }
            proto.end(wakeLockToken);
        }

        private String getLockLevelString() {
            int i = this.mFlags & 65535;
            if (i != 1) {
                if (i != 6) {
                    if (i != 10) {
                        if (i != 26) {
                            if (i != 32) {
                                if (i != 64) {
                                    if (i == 128) {
                                        return "DRAW_WAKE_LOCK                ";
                                    }
                                    return "???                           ";
                                }
                                return "DOZE_WAKE_LOCK                ";
                            }
                            return "PROXIMITY_SCREEN_OFF_WAKE_LOCK";
                        }
                        return "FULL_WAKE_LOCK                ";
                    }
                    return "SCREEN_BRIGHT_WAKE_LOCK       ";
                }
                return "SCREEN_DIM_WAKE_LOCK          ";
            }
            return "PARTIAL_WAKE_LOCK             ";
        }

        private String getLockFlagsString() {
            String result = "";
            if ((this.mFlags & 268435456) != 0) {
                result = " ACQUIRE_CAUSES_WAKEUP";
            }
            if ((this.mFlags & 536870912) != 0) {
                return result + " ON_AFTER_RELEASE";
            }
            return result;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public final class SuspendBlockerImpl implements SuspendBlocker {
        private final boolean mIsShowLog;
        private final String mName;
        private int mReferenceCount;
        private final String mTraceName;

        public SuspendBlockerImpl(String name) {
            this.mName = name;
            this.mIsShowLog = this.mName.equals("PowerManagerService.Broadcasts") || this.mName.equals("PowerManagerService.Display");
            this.mTraceName = "SuspendBlocker (" + name + Separators.RPAREN;
        }

        protected void finalize() throws Throwable {
            try {
                if (this.mReferenceCount != 0) {
                    Slog.wtf(PowerManagerService.TAG, "Suspend blocker \"" + this.mName + "\" was finalized without being released!");
                    this.mReferenceCount = 0;
                    PowerManagerService.nativeReleaseSuspendBlocker(this.mName);
                    Trace.asyncTraceEnd(131072L, this.mTraceName, 0);
                }
            } finally {
                super.finalize();
            }
        }

        @Override // com.android.server.power.SuspendBlocker
        public void acquire() {
            synchronized (this) {
                this.mReferenceCount++;
                if (this.mReferenceCount == 1) {
                    if (this.mIsShowLog) {
                        Slog.d(PowerManagerService.TAG, "[PWL] sb acquire: " + this.mName);
                    }
                    Trace.asyncTraceBegin(131072L, this.mTraceName, 0);
                    PowerManagerService.nativeAcquireSuspendBlocker(this.mName);
                }
            }
        }

        @Override // com.android.server.power.SuspendBlocker
        public void release() {
            synchronized (this) {
                this.mReferenceCount--;
                if (this.mReferenceCount == 0) {
                    PowerManagerService.nativeReleaseSuspendBlocker(this.mName);
                    if (this.mIsShowLog) {
                        Slog.d(PowerManagerService.TAG, "[PWL] sb release: " + this.mName);
                    }
                    Trace.asyncTraceEnd(131072L, this.mTraceName, 0);
                } else if (this.mReferenceCount < 0) {
                    Slog.wtf(PowerManagerService.TAG, "Suspend blocker \"" + this.mName + "\" was released without being acquired!", new Throwable());
                    this.mReferenceCount = 0;
                }
            }
        }

        public String toString() {
            String str;
            synchronized (this) {
                str = this.mName + ": ref count=" + this.mReferenceCount;
            }
            return str;
        }

        public boolean isHeld() {
            boolean z;
            synchronized (this) {
                z = this.mReferenceCount != 0;
            }
            return z;
        }

        @Override // com.android.server.power.SuspendBlocker
        public void writeToProto(ProtoOutputStream proto, long fieldId) {
            long sbToken = proto.start(fieldId);
            synchronized (this) {
                proto.write(1138166333441L, this.mName);
                proto.write(1120986464258L, this.mReferenceCount);
            }
            proto.end(sbToken);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public static final class UidState {
        boolean mActive;
        int mNumWakeLocks;
        int mProcState;
        final int mUid;

        UidState(int uid) {
            this.mUid = uid;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public final class BinderService extends IPowerManager.Stub {
        private final Runnable mClearViewBrightnessModeDisableRunnable;
        private ClearViewLock mClearViewLock;
        private ColorWeaknessLock mColorWeaknessLock;

        private BinderService() {
            this.mClearViewBrightnessModeDisableRunnable = new Runnable() { // from class: com.android.server.power.PowerManagerService.BinderService.1
                @Override // java.lang.Runnable
                public void run() {
                    synchronized (PowerManagerService.this.mLock) {
                        PowerManagerService.this.mHandler.removeCallbacks(this);
                        PowerManagerService.access$13820(PowerManagerService.this, 1);
                        boolean oldClearViewBrightnessModeEnabled = PowerManagerService.this.mClearViewBrightnessModeEnabled;
                        if (PowerManagerService.this.mClearViewBrightnessModeCount > 0) {
                            PowerManagerService.this.mClearViewBrightnessModeEnabled = true;
                        } else {
                            PowerManagerService.this.mClearViewBrightnessModeEnabled = false;
                        }
                        Slog.d(PowerManagerService.TAG, "[api] setClearViewBrightnessMode(Runnable) : enable:" + PowerManagerService.this.mClearViewBrightnessModeEnabled);
                        if (oldClearViewBrightnessModeEnabled != PowerManagerService.this.mClearViewBrightnessModeEnabled) {
                            PowerManagerService.access$2176(PowerManagerService.this, 32);
                            PowerManagerService.this.updatePowerStateLocked();
                        }
                    }
                }
            };
            this.mClearViewLock = null;
            this.mColorWeaknessLock = null;
        }

        @Override // android.os.Binder
        public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err, String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
            new PowerManagerShellCommand(this).exec(this, in, out, err, args, callback, resultReceiver);
        }

        @Override // android.os.IPowerManager
        public void acquireWakeLockWithUid(IBinder lock, int flags, String tag, String packageName, int uid) {
            if (uid < 0) {
                uid = Binder.getCallingUid();
            }
            acquireWakeLock(lock, flags, tag, packageName, new WorkSource(uid), null);
        }

        @Override // android.os.IPowerManager
        public void powerHint(int hintId, int data) {
            Slog.d(PowerManagerService.TAG, "[api] powerHint: id:" + hintId + ", d: " + data + PowerManagerService.callerInfoToString());
            if (PowerManagerService.this.mSystemReady) {
                PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
                PowerManagerService.this.powerHintInternal(hintId, data);
            }
        }

        /* JADX WARN: Can't wrap try/catch for region: R(9:4|(1:6)|(1:43)(1:10)|11|(5:(11:15|(3:17|18|19)(1:41)|20|21|22|23|24|25|26|27|28)|25|26|27|28)|42|22|23|24) */
        /* JADX WARN: Code restructure failed: missing block: B:35:0x00a5, code lost:
            r0 = th;
         */
        /* JADX WARN: Code restructure failed: missing block: B:36:0x00a6, code lost:
            r12 = r10;
         */
        @Override // android.os.IPowerManager
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct code enable 'Show inconsistent code' option in preferences
        */
        public void acquireWakeLock(android.os.IBinder r20, int r21, java.lang.String r22, java.lang.String r23, android.os.WorkSource r24, java.lang.String r25) {
            /*
                r19 = this;
                r1 = r19
                r13 = r23
                if (r20 == 0) goto Lb4
                if (r13 == 0) goto Lab
                android.os.PowerManager.validateWakeLockParameters(r21, r22)
                com.android.server.power.PowerManagerService r0 = com.android.server.power.PowerManagerService.this
                android.content.Context r0 = com.android.server.power.PowerManagerService.access$2400(r0)
                java.lang.String r2 = "android.permission.WAKE_LOCK"
                r3 = 0
                r0.enforceCallingOrSelfPermission(r2, r3)
                r0 = r21 & 64
                if (r0 == 0) goto L26
                com.android.server.power.PowerManagerService r0 = com.android.server.power.PowerManagerService.this
                android.content.Context r0 = com.android.server.power.PowerManagerService.access$2400(r0)
                java.lang.String r2 = "android.permission.DEVICE_POWER"
                r0.enforceCallingOrSelfPermission(r2, r3)
            L26:
                if (r24 == 0) goto L3c
                boolean r2 = r24.isEmpty()
                if (r2 != 0) goto L3c
                com.android.server.power.PowerManagerService r2 = com.android.server.power.PowerManagerService.this
                android.content.Context r2 = com.android.server.power.PowerManagerService.access$2400(r2)
                java.lang.String r4 = "android.permission.UPDATE_DEVICE_STATS"
                r2.enforceCallingOrSelfPermission(r4, r3)
                r14 = r24
                goto L3e
            L3c:
                r0 = 0
                r14 = r0
            L3e:
                int r15 = android.os.Binder.getCallingUid()
                int r16 = android.os.Binder.getCallingPid()
                long r10 = android.os.Binder.clearCallingIdentity()
                r2 = 1
                r0 = 268435456(0x10000000, float:2.5243549E-29)
                r0 = r21 & r0
                if (r0 == 0) goto L87
                boolean r0 = com.android.server.power.PowerManagerService.access$9300(r21)
                if (r0 == 0) goto L87
                android.app.INotificationManager r0 = android.app.NotificationManager.getService()
                r3 = r0
                if (r14 == 0) goto L66
                r0 = 0
                int r0 = r14.get(r0)     // Catch: android.os.RemoteException -> L64
                goto L67
            L64:
                r0 = move-exception
                goto L70
            L66:
                r0 = r15
            L67:
                boolean r4 = r3.areNotificationsEnabledForPackage(r13, r0)     // Catch: android.os.RemoteException -> L64
                r0 = r4
                r17 = r0
                goto L89
            L70:
                java.lang.String r4 = "PowerManagerService"
                java.lang.StringBuilder r5 = new java.lang.StringBuilder
                r5.<init>()
                java.lang.String r6 = "Failed to call NotificationManager : "
                r5.append(r6)
                r5.append(r0)
                java.lang.String r5 = r5.toString()
                com.android.server.power.Slog.e(r4, r5)
            L87:
                r17 = r2
            L89:
                com.android.server.power.PowerManagerService r2 = com.android.server.power.PowerManagerService.this     // Catch: java.lang.Throwable -> La5
                r3 = r20
                r4 = r21
                r5 = r22
                r6 = r13
                r7 = r14
                r8 = r25
                r9 = r15
                r12 = r10
                r10 = r16
                r11 = r17
                com.android.server.power.PowerManagerService.access$9400(r2, r3, r4, r5, r6, r7, r8, r9, r10, r11)     // Catch: java.lang.Throwable -> La3
                android.os.Binder.restoreCallingIdentity(r12)
                return
            La3:
                r0 = move-exception
                goto La7
            La5:
                r0 = move-exception
                r12 = r10
            La7:
                android.os.Binder.restoreCallingIdentity(r12)
                throw r0
            Lab:
                java.lang.IllegalArgumentException r2 = new java.lang.IllegalArgumentException
                java.lang.String r3 = "packageName must not be null"
                r2.<init>(r3)
                throw r2
            Lb4:
                java.lang.IllegalArgumentException r2 = new java.lang.IllegalArgumentException
                java.lang.String r3 = "lock must not be null"
                r2.<init>(r3)
                throw r2
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.server.power.PowerManagerService.BinderService.acquireWakeLock(android.os.IBinder, int, java.lang.String, java.lang.String, android.os.WorkSource, java.lang.String):void");
        }

        @Override // android.os.IPowerManager
        public void releaseWakeLock(IBinder lock, int flags) {
            if (lock != null) {
                PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.WAKE_LOCK, null);
                long ident = Binder.clearCallingIdentity();
                try {
                    PowerManagerService.this.releaseWakeLockInternal(lock, flags);
                    return;
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
            throw new IllegalArgumentException("lock must not be null");
        }

        @Override // android.os.IPowerManager
        public void updateWakeLockUids(IBinder lock, int[] uids) {
            WorkSource ws = null;
            if (uids != null) {
                ws = new WorkSource();
                for (int i : uids) {
                    ws.add(i);
                }
            }
            updateWakeLockWorkSource(lock, ws, null);
        }

        @Override // android.os.IPowerManager
        public void updateWakeLockWorkSource(IBinder lock, WorkSource ws, String historyTag) {
            if (lock != null) {
                PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.WAKE_LOCK, null);
                if (ws != null && !ws.isEmpty()) {
                    PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.UPDATE_DEVICE_STATS, null);
                } else {
                    ws = null;
                }
                int callingUid = Binder.getCallingUid();
                long ident = Binder.clearCallingIdentity();
                try {
                    PowerManagerService.this.updateWakeLockWorkSourceInternal(lock, ws, historyTag, callingUid);
                    return;
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
            throw new IllegalArgumentException("lock must not be null");
        }

        @Override // android.os.IPowerManager
        public boolean isWakeLockLevelSupported(int level) {
            long ident = Binder.clearCallingIdentity();
            try {
                return PowerManagerService.this.isWakeLockLevelSupportedInternal(level);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public void userActivity(long eventTime, int event, int flags) {
            Slog.d(PowerManagerService.TAG, "[api] userActivity : event: " + event + " flags: " + flags + PowerManagerService.callerInfoToString() + " eventTime = " + eventTime);
            long now = SystemClock.uptimeMillis();
            if (PowerManagerService.this.mContext.checkCallingOrSelfPermission(Manifest.permission.DEVICE_POWER) != 0 && PowerManagerService.this.mContext.checkCallingOrSelfPermission(Manifest.permission.USER_ACTIVITY) != 0) {
                synchronized (PowerManagerService.this.mLock) {
                    if (now >= PowerManagerService.this.mLastWarningAboutUserActivityPermission + 300000) {
                        PowerManagerService.this.mLastWarningAboutUserActivityPermission = now;
                        Slog.w(PowerManagerService.TAG, "Ignoring call to PowerManager.userActivity() because the caller does not have DEVICE_POWER or USER_ACTIVITY permission.  Please fix your app!   pid=" + Binder.getCallingPid() + " uid=" + Binder.getCallingUid());
                    }
                }
            } else if (eventTime > now) {
                throw new IllegalArgumentException("event time must not be in the future");
            } else {
                int uid = Binder.getCallingUid();
                long ident = Binder.clearCallingIdentity();
                try {
                    PowerManagerService.this.userActivityInternal(eventTime, event, flags, uid);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }

        @Override // android.os.IPowerManager
        public void wakeUpWithReason(long eventTime, int event, int flags) {
            Slog.d(PowerManagerService.TAG, "[api] wakeUpWithReason" + PowerManagerService.callerInfoToString() + " eventTime = " + eventTime + " event = " + event + " flags = 0x" + Integer.toHexString(flags));
            if (eventTime <= SystemClock.uptimeMillis()) {
                PowerManagerService.this.mTimeMeasurement.resetTime();
                PowerManagerService powerManagerService = PowerManagerService.this;
                powerManagerService.mScreenOnReason = " wakeUpWithReason: " + event + PowerManagerService.callerInfoToString();
                PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
                int uid = Binder.getCallingUid();
                long ident = Binder.clearCallingIdentity();
                try {
                    PowerManagerService.this.wakeUpWithReasonInternal(eventTime, event, uid, flags);
                    return;
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
            throw new IllegalArgumentException("event time must not be in the future");
        }

        @Override // android.os.IPowerManager
        public void wakeUp(long eventTime, String reason, String opPackageName) {
            PowerManagerService.this.mTimeMeasurement.resetTime();
            if (eventTime <= SystemClock.uptimeMillis()) {
                Slog.d(PowerManagerService.TAG, "[api] wakeUp" + PowerManagerService.callerInfoToString() + " eventTime = " + eventTime);
                PowerManagerService powerManagerService = PowerManagerService.this;
                StringBuilder sb = new StringBuilder();
                sb.append(" wakeUp: ");
                sb.append(PowerManagerService.callerInfoToString());
                powerManagerService.mScreenOnReason = sb.toString();
                PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
                int uid = Binder.getCallingUid();
                long ident = Binder.clearCallingIdentity();
                try {
                    PowerManagerService.this.wakeUpInternal(eventTime, reason, uid, opPackageName, uid);
                    return;
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
            throw new IllegalArgumentException("event time must not be in the future");
        }

        @Override // android.os.IPowerManager
        public void goToSleep(long eventTime, int reason, int flags) {
            Slog.d(PowerManagerService.TAG, "[api] goToSleep: " + reason + PowerManagerService.callerInfoToString() + " eventTime = " + eventTime);
            if (eventTime <= SystemClock.uptimeMillis()) {
                PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
                PowerManagerService powerManagerService = PowerManagerService.this;
                powerManagerService.mScreenOffReason = " goToSleep: " + PowerManagerService.callerInfoToString();
                int uid = Binder.getCallingUid();
                long ident = Binder.clearCallingIdentity();
                try {
                    PowerManagerService.this.goToSleepInternal(eventTime, reason, flags, uid);
                    return;
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
            throw new IllegalArgumentException("event time must not be in the future");
        }

        @Override // android.os.IPowerManager
        public void nap(long eventTime) {
            Slog.d(PowerManagerService.TAG, "[api] nap" + PowerManagerService.callerInfoToString() + " eventTime = " + eventTime);
            if (eventTime <= SystemClock.uptimeMillis()) {
                PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
                int uid = Binder.getCallingUid();
                long ident = Binder.clearCallingIdentity();
                try {
                    PowerManagerService.this.napInternal(eventTime, uid);
                    return;
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
            throw new IllegalArgumentException("event time must not be in the future");
        }

        @Override // android.os.IPowerManager
        public boolean isInteractive() {
            long ident = Binder.clearCallingIdentity();
            try {
                return PowerManagerService.this.isInteractiveInternal();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public boolean isInteractiveForDisplay(int displayId) {
            int uid = Binder.getCallingUid();
            long ident = Binder.clearCallingIdentity();
            try {
                return PowerManagerService.this.isInteractiveInternal(displayId, uid);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public boolean isPowerSaveMode() {
            long ident = Binder.clearCallingIdentity();
            try {
                return PowerManagerService.this.mBatterySaverController.isEnabled();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public PowerSaveState getPowerSaveState(int serviceType) {
            long ident = Binder.clearCallingIdentity();
            try {
                return PowerManagerService.this.mBatterySaverPolicy.getBatterySaverPolicy(serviceType, PowerManagerService.this.mBatterySaverController.isEnabled());
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public boolean setPowerSaveMode(boolean enabled) {
            Slog.d(PowerManagerService.TAG, "[api] setPowerSaveMode: " + enabled + PowerManagerService.callerInfoToString());
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            long ident = Binder.clearCallingIdentity();
            try {
                return PowerManagerService.this.setLowPowerModeInternal(enabled);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public boolean isDeviceIdleMode() {
            long ident = Binder.clearCallingIdentity();
            try {
                return PowerManagerService.this.isDeviceIdleModeInternal();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public boolean isLightDeviceIdleMode() {
            long ident = Binder.clearCallingIdentity();
            try {
                return PowerManagerService.this.isLightDeviceIdleModeInternal();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public int getLastShutdownReason() {
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            long ident = Binder.clearCallingIdentity();
            try {
                return PowerManagerService.this.getLastShutdownReasonInternal(PowerManagerService.LAST_REBOOT_PROPERTY);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public void systemReboot(String reason) {
            Slog.d(PowerManagerService.TAG, "[api] systemReboot");
            if (Binder.getCallingPid() != Process.myPid()) {
                Slog.i(PowerManagerService.TAG, "system reboot is called by non-system");
                return;
            }
            PowerManagerService.this.mShutdownOrRebootCaller = PowerManagerService.callerInfoWithProcessName();
            long ident = Binder.clearCallingIdentity();
            try {
                Slog.d(PowerManagerService.TAG, "calling shutdownOrRebootInternal");
                PowerManagerService.this.shutdownOrRebootInternal(1, false, reason, true, true);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public void reboot(boolean confirm, String reason, boolean wait) {
            PowerManagerService.this.mShutdownOrRebootCaller = PowerManagerService.callerInfoWithProcessName();
            StringBuilder sb = new StringBuilder(PowerManagerService.TAG_LOG_TO_KERNEL);
            sb.append(String.format(" reason: %s", reason));
            sb.append(String.format(" confirm: %b", Boolean.valueOf(confirm)));
            sb.append(String.format(" wait: %b", Boolean.valueOf(wait)));
            StringBuilder rebootReason = sb.append(PowerManagerService.this.mShutdownOrRebootCaller);
            Slog.d(PowerManagerService.TAG, "[api] " + rebootReason.toString());
            if (reason == null && Binder.getCallingPid() == Process.myPid()) {
                ShutdownThread.saveReasonforshutdown(new Exception("System server call reboot"));
            }
            if (reason == null) {
                reason = PowerManagerService.this.mShutdownOrRebootCaller;
            }
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.REBOOT, null);
            if ("recovery".equals(reason) || PowerManager.REBOOT_RECOVERY_UPDATE.equals(reason)) {
                PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.RECOVERY, null);
            }
            long ident = Binder.clearCallingIdentity();
            try {
                PowerManagerService.this.shutdownOrRebootInternal(1, confirm, reason, wait);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public void rebootSafeMode(boolean confirm, boolean wait) {
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.REBOOT, null);
            long ident = Binder.clearCallingIdentity();
            try {
                PowerManagerService.this.shutdownOrRebootInternal(2, confirm, PowerManager.REBOOT_SAFE_MODE, wait);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public void shutdown(boolean confirm, String reason, boolean wait) {
            PowerManagerService.this.mShutdownOrRebootCaller = PowerManagerService.callerInfoWithProcessName();
            StringBuilder sb = new StringBuilder(PowerManagerService.TAG_LOG_TO_KERNEL);
            sb.append(" shutdown");
            sb.append(String.format(" confirm: %b", Boolean.valueOf(confirm)));
            sb.append(String.format(" wait: %b", Boolean.valueOf(wait)));
            StringBuilder rebootReason = sb.append(PowerManagerService.this.mShutdownOrRebootCaller);
            Slog.d(PowerManagerService.TAG, "[api] " + rebootReason.toString());
            if (Binder.getCallingPid() == Process.myPid()) {
                ShutdownThread.saveReasonforshutdown(new Exception("System server call shutdown"));
            }
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.REBOOT, null);
            long ident = Binder.clearCallingIdentity();
            try {
                PowerManagerService.this.shutdownOrRebootInternal(0, confirm, reason, wait);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public void crash(String message) {
            Slog.d(PowerManagerService.TAG, "[api] crash: " + message + PowerManagerService.callerInfoToString());
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.REBOOT, null);
            long ident = Binder.clearCallingIdentity();
            try {
                PowerManagerService.this.crashInternal(message);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public void setStayOnSetting(int val) {
            Slog.d(PowerManagerService.TAG, "[api] setStayOnSetting: " + val + PowerManagerService.callerInfoToString());
            int uid = Binder.getCallingUid();
            if (uid != 0 && !Settings.checkAndNoteWriteSettingsOperation(PowerManagerService.this.mContext, uid, Settings.getPackageNameForUid(PowerManagerService.this.mContext, uid), true)) {
                Slog.d(PowerManagerService.TAG, "[api] setStayOnSetting: checkAndNoteWriteSettingsOperation() false");
                return;
            }
            long ident = Binder.clearCallingIdentity();
            try {
                PowerManagerService.this.setStayOnSettingInternal(val);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public void setTemporaryMarkerScreenBrightnessSettingOverride(int brightness) {
        }

        @Override // android.os.IPowerManager
        public void setAttentionLight(boolean on, int color) {
            Slog.d(PowerManagerService.TAG, "[api] setAttentionLight: on: " + on + " color: " + color + PowerManagerService.callerInfoToString());
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            long ident = Binder.clearCallingIdentity();
            try {
                PowerManagerService.this.setAttentionLightInternal(on, color);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public void setDozeAfterScreenOff(boolean on) {
            Slog.d(PowerManagerService.TAG, "[api] setDozeAfterScreenOff: " + on + PowerManagerService.callerInfoToString());
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            long ident = Binder.clearCallingIdentity();
            try {
                PowerManagerService.this.setDozeAfterScreenOffInternal(on);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.IPowerManager
        public void boostScreenBrightness(long eventTime) {
            Slog.d(PowerManagerService.TAG, "[api] boostScreenBrightness: " + eventTime + PowerManagerService.callerInfoToString());
            if (eventTime <= SystemClock.uptimeMillis()) {
                PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
                int uid = Binder.getCallingUid();
                long ident = Binder.clearCallingIdentity();
                try {
                    PowerManagerService.this.boostScreenBrightnessInternal(eventTime, uid);
                    return;
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
            throw new IllegalArgumentException("event time must not be in the future");
        }

        @Override // android.os.IPowerManager
        public boolean isScreenBrightnessBoosted() {
            long ident = Binder.clearCallingIdentity();
            try {
                return PowerManagerService.this.isScreenBrightnessBoostedInternal();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // android.os.Binder
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (DumpUtils.checkDumpPermission(PowerManagerService.this.mContext, PowerManagerService.TAG, pw)) {
                long ident = Binder.clearCallingIdentity();
                boolean isDumpProto = false;
                for (String arg : args) {
                    if (arg.equals(PriorityDump.PROTO_ARG)) {
                        isDumpProto = true;
                    }
                }
                try {
                    if (isDumpProto) {
                        PowerManagerService.this.dumpProto(fd);
                    } else {
                        PowerManagerService.this.dumpInternal(pw);
                    }
                    Binder.restoreCallingIdentity(ident);
                } catch (Throwable th) {
                    Binder.restoreCallingIdentity(ident);
                    throw th;
                }
            }
        }

        @Override // android.os.IPowerManager
        public void setKeyboardVisibility(boolean visible) {
            Slog.d(PowerManagerService.TAG, "[api] setKeyboardVisibility: visible: " + visible);
            synchronized (PowerManagerService.this.mLock) {
                if (PowerManagerService.this.mIsFolderType && PowerManagerService.this.mKeyboardLightState != null && PowerManagerService.this.mIsKeyboardVisible != visible) {
                    PowerManagerService.this.mIsKeyboardVisible = visible;
                }
            }
            if (PowerManagerService.this.mIsFolderType) {
                PowerManagerService.this.updateLidState(!visible);
            }
        }

        @Override // android.os.IPowerManager
        public void setButtonBrightnessLimit(int brightness) {
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            Slog.d(PowerManagerService.TAG, "[api] setButtonBrightnessLimit: " + brightness);
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService.this.mTouchKeyForceDisable = brightness == 0;
                PowerManagerService.access$2176(PowerManagerService.this, 32);
                PowerManagerService.this.updatePowerStateLocked();
            }
        }

        @Override // android.os.IPowerManager
        public void resetPersonalAutoBrightnessData() {
        }

        @Override // android.os.IPowerManager
        public void switchForceLcdBacklightOffState() {
            Slog.d(PowerManagerService.TAG, "[api] switchForceLcdBacklightOffState()");
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            synchronized (PowerManagerService.this.mLock) {
                if (PowerManagerService.this.mDisplayPowerRequest.forceLcdBacklightOffEnabled) {
                    PowerManagerService.this.mDisplayPowerRequest.forceLcdBacklightOffEnabled = false;
                } else {
                    PowerManagerService.this.mDisplayPowerRequest.forceLcdBacklightOffEnabled = true;
                }
                PowerManagerService.access$2176(PowerManagerService.this, 32);
                PowerManagerService.this.updatePowerStateLocked();
            }
        }

        @Override // android.os.IPowerManager
        public void updateCoverState(boolean closed) {
            Slog.d(PowerManagerService.TAG, "[api] updateCoverState: closed: " + closed);
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService.this.mIsCoverClosed = closed;
                if (!PowerManagerService.this.mCoverAuthReady) {
                    PowerManagerService.this.mCoverAuthReady = true;
                    PowerManagerService.this.mHandler.removeCallbacks(PowerManagerService.this.mCoverAuthReadyRunnable);
                    PowerManagerService.this.mCoverAuthReadyRunnable = null;
                }
                PowerManagerService.access$2176(PowerManagerService.this, 32);
                long time = SystemClock.uptimeMillis();
                PowerManagerService.this.mlastUpdateCoverStateTime = time;
                PowerManagerService powerManagerService = PowerManagerService.this;
                powerManagerService.mlastUpdateCoverStateReason = "updateCoverState called on closed = " + closed;
                PowerManagerService.this.userActivityNoUpdateLocked(SystemClock.uptimeMillis(), 0, 0, 1000);
                PowerManagerService.this.updatePowerStateLocked();
            }
        }

        @Override // android.os.IPowerManager
        public void setCoverType(int coverType) {
            Slog.d(PowerManagerService.TAG, "[api] setCoverType: " + coverType);
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService.this.mCoverType = coverType;
                long time = SystemClock.uptimeMillis();
                PowerManagerService.this.mlastSetCoverTypeTime = time;
                PowerManagerService powerManagerService = PowerManagerService.this;
                powerManagerService.mlastUpdateCoverTypeReason = "setCoverType = " + coverType;
                PowerManagerService.access$2176(PowerManagerService.this, 32);
                PowerManagerService.this.updatePowerStateLocked();
            }
        }

        @Override // android.os.IPowerManager
        public void setAutoBrightnessForEbookOnly(boolean enabled) {
        }

        @Override // android.os.IPowerManager
        public void setAutoBrightnessLimit(int lowerLimit, int upperLimit, boolean slowChange) {
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.CONTROL_DISPLAY_BRIGHTNESS, null);
            if (lowerLimit < -1 || upperLimit < -1 || (upperLimit >= 0 && lowerLimit > upperLimit)) {
                Slog.d(PowerManagerService.TAG, "[api] setAutoBrightnessLimit : invalid Limits : " + lowerLimit + ", " + upperLimit + PowerManagerService.callerInfoToString());
                return;
            }
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService.this.mLastAutoBrightnessLimitTime = SystemClock.uptimeMillis();
                if (PowerManagerService.this.mAutoBrightnessLowerLimit != lowerLimit || PowerManagerService.this.mAutoBrightnessUpperLimit != upperLimit) {
                    Slog.d(PowerManagerService.TAG, "[api] setAutoBrightnessLimit : lowerLimit " + lowerLimit + "   upperLimit : " + upperLimit + "   slowChange : " + slowChange + PowerManagerService.callerInfoToString());
                    PowerManagerService.this.mAutoBrightnessLowerLimit = lowerLimit;
                    PowerManagerService.this.mAutoBrightnessUpperLimit = upperLimit;
                    PowerManagerService.this.mForceSlowChange = slowChange;
                    PowerManagerService.access$2176(PowerManagerService.this, 32);
                    PowerManagerService.this.updatePowerStateLocked();
                }
            }
        }

        @Override // android.os.IPowerManager
        public void setClearViewBrightnessMode(boolean enable, int delayAfterRelease, IBinder binder) {
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            synchronized (PowerManagerService.this.mLock) {
                if (PowerManagerService.this.mClearViewBrightnessModeCount > 0 || enable) {
                    if (enable) {
                        PowerManagerService.access$13812(PowerManagerService.this, 1);
                    } else {
                        PowerManagerService.access$13820(PowerManagerService.this, 1);
                    }
                    boolean oldClearViewBrightnessModeEnabled = PowerManagerService.this.mClearViewBrightnessModeEnabled;
                    if (PowerManagerService.this.mClearViewBrightnessModeCount > 0) {
                        PowerManagerService.this.mClearViewBrightnessModeEnabled = true;
                    } else {
                        PowerManagerService.this.mClearViewBrightnessModeEnabled = false;
                    }
                    if (this.mClearViewLock == null) {
                        this.mClearViewLock = new ClearViewLock(binder);
                    }
                    Slog.d(PowerManagerService.TAG, "[api] setClearViewBrightnessMode : enable:" + enable + ", delayAfterRelease:" + delayAfterRelease + ", count:" + PowerManagerService.this.mClearViewBrightnessModeCount + ", mode:" + PowerManagerService.this.mClearViewBrightnessModeEnabled + " " + PowerManagerService.callerInfoToString());
                    if (oldClearViewBrightnessModeEnabled != PowerManagerService.this.mClearViewBrightnessModeEnabled) {
                        if (!PowerManagerService.this.mClearViewBrightnessModeEnabled && delayAfterRelease > 0) {
                            PowerManagerService.this.mClearViewBrightnessModeEnabled = oldClearViewBrightnessModeEnabled;
                            PowerManagerService.access$13812(PowerManagerService.this, 1);
                            PowerManagerService.this.mHandler.postDelayed(this.mClearViewBrightnessModeDisableRunnable, delayAfterRelease);
                        } else {
                            PowerManagerService.access$2176(PowerManagerService.this, 32);
                            PowerManagerService.this.updatePowerStateLocked();
                        }
                    }
                    return;
                }
                Slog.d(PowerManagerService.TAG, "[api] setClearViewBrightnessMode : Clear View Brightness Mode is NOT enabled yet.");
            }
        }

        /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
        private class ClearViewLock implements IBinder.DeathRecipient {
            IBinder binder;

            ClearViewLock(IBinder binder) {
                this.binder = binder;
                try {
                    binder.linkToDeath(this, 0);
                } catch (RemoteException e) {
                    binderDied();
                }
            }

            @Override // android.os.IBinder.DeathRecipient
            public void binderDied() {
                synchronized (PowerManagerService.this.mLock) {
                    Slog.d(PowerManagerService.TAG, "ClearViewLock : binderDied");
                    PowerManagerService.this.mClearViewBrightnessModeCount = 0;
                    PowerManagerService.this.mClearViewBrightnessModeEnabled = false;
                    BinderService.this.mClearViewLock = null;
                    PowerManagerService.access$2176(PowerManagerService.this, 32);
                    PowerManagerService.this.updatePowerStateLocked();
                }
            }
        }

        @Override // android.os.IPowerManager
        public void setColorWeaknessMode(boolean enable, IBinder binder) {
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            synchronized (PowerManagerService.this.mLock) {
                Slog.d(PowerManagerService.TAG, "[api] setColorWeaknessMode : enable:" + enable + ", mode:" + PowerManagerService.this.mColorWeaknessModeEnabled + " " + PowerManagerService.callerInfoToString());
                if (this.mColorWeaknessLock == null) {
                    this.mColorWeaknessLock = new ColorWeaknessLock(binder);
                }
                if (enable != PowerManagerService.this.mColorWeaknessModeEnabled) {
                    PowerManagerService.this.mColorWeaknessModeEnabled = enable;
                    PowerManagerService.access$2176(PowerManagerService.this, 32);
                    PowerManagerService.this.updatePowerStateLocked();
                }
            }
        }

        /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
        private class ColorWeaknessLock implements IBinder.DeathRecipient {
            IBinder binder;

            ColorWeaknessLock(IBinder binder) {
                this.binder = binder;
                try {
                    binder.linkToDeath(this, 0);
                } catch (RemoteException e) {
                    binderDied();
                }
            }

            @Override // android.os.IBinder.DeathRecipient
            public void binderDied() {
                synchronized (PowerManagerService.this.mLock) {
                    Slog.d(PowerManagerService.TAG, "ColorWeaknessLock : binderDied");
                    PowerManagerService.this.mColorWeaknessModeCount = 0;
                    PowerManagerService.this.mColorWeaknessModeEnabled = false;
                    BinderService.this.mColorWeaknessLock = null;
                    PowerManagerService.access$2176(PowerManagerService.this, 32);
                    PowerManagerService.this.updatePowerStateLocked();
                }
            }
        }

        @Override // android.os.IPowerManager
        public void setLCDFlashMode(boolean enable, IBinder binder) {
            Slog.d(PowerManagerService.TAG, "[api] setLCDFlashMode: enable: " + enable + PowerManagerService.callerInfoToString());
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            PowerManagerService.this.setLCDFlashModeInternal(enable, binder);
        }

        @Override // android.os.IPowerManager
        public void setMasterBrightnessLimit(int lowerLimit, int upperLimit) {
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.CONTROL_DISPLAY_BRIGHTNESS, null);
            int maxScreenBrightness = Math.max(PowerManagerService.this.mScreenExtendedBrightnessMaximum, 255);
            if (lowerLimit != -1 && (lowerLimit < 0 || lowerLimit > maxScreenBrightness)) {
                throw new IllegalArgumentException("setMasterBrightnessLimit : invalid lowerLimit : " + lowerLimit);
            } else if (upperLimit != -1 && (upperLimit < 0 || upperLimit > maxScreenBrightness)) {
                throw new IllegalArgumentException("setMasterBrightnessLimit : invalid upperLimit" + upperLimit);
            } else if (lowerLimit == -1 || upperLimit == -1 || lowerLimit <= upperLimit) {
                PowerManagerService.this.mDisplayPowerRequest.minBrightness = lowerLimit;
                PowerManagerService.this.mDisplayPowerRequest.maxBrightness = upperLimit;
                Slog.d(PowerManagerService.TAG, "[api] setMasterBrightnessLimit : minBrightnss : " + lowerLimit + "  maxBrightness : " + upperLimit + PowerManagerService.callerInfoToString());
                synchronized (PowerManagerService.this.mLock) {
                    PowerManagerService.access$2176(PowerManagerService.this, 32);
                    PowerManagerService.this.updatePowerStateLocked();
                }
            } else {
                throw new IllegalArgumentException("setMasterBrightnessLimit : lowerLimit " + lowerLimit + " > upperLimit " + upperLimit);
            }
        }

        @Override // android.os.IPowerManager
        public void setScreenBrightnessScaleFactor(float scaleFactor, IBinder binder) {
            Slog.d(PowerManagerService.TAG, "[api] setScreenBrightnessScaleFactor: scaleFactor: " + scaleFactor + PowerManagerService.callerInfoToString());
            if (scaleFactor != -1.0f && scaleFactor < 0.0f) {
                throw new IllegalArgumentException("setScreenBrightnessScaleFactor: scaleFactor:" + scaleFactor);
            } else if (binder != null) {
                PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
                synchronized (PowerManagerService.this.mLock) {
                    if (scaleFactor != PowerManagerService.this.mScreenBrightnessScaleFactor) {
                        PowerManagerService.this.mScreenBrightnessScaleFactor = scaleFactor;
                        PowerManagerService.access$2176(PowerManagerService.this, 32);
                        PowerManagerService.this.updatePowerStateLocked();
                    }
                }
            } else {
                throw new IllegalArgumentException("lock must not be null");
            }
        }

        @Override // android.os.IPowerManager
        public void setFreezingScreenBrightness(boolean freezing) {
            Slog.d(PowerManagerService.TAG, "[api] setFreezingScreenBrightness: freezing: " + freezing + PowerManagerService.callerInfoToString());
            PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.DEVICE_POWER, null);
            synchronized (PowerManagerService.this.mLock) {
                if (freezing != PowerManagerService.this.mFreezingScreenBrightness) {
                    PowerManagerService.this.mFreezingScreenBrightness = freezing;
                    PowerManagerService.access$2176(PowerManagerService.this, 32);
                    PowerManagerService.this.updatePowerStateLocked();
                }
            }
        }

        @Override // android.os.IPowerManager
        public float getCurrentBrightness(boolean ratio) {
            Slog.d(PowerManagerService.TAG, "[api] +getCurrentBrightness: ratio: " + ratio + PowerManagerService.callerInfoToString());
            float curBrightness = PowerManagerService.this.mScreenBrightnessModeSetting == 1 ? PowerManagerService.this.mDisplayManagerInternal.getLastAutomaticScreenBrightness() : PowerManagerService.this.mDisplayManagerInternal.getCurrentScreenBrightness();
            if (curBrightness > 255.0f) {
                curBrightness = 255.0f;
            }
            if (ratio) {
                float ret = curBrightness / 255.0f;
                return ret;
            }
            float ret2 = curBrightness;
            return ret2;
        }

        @Override // android.os.IPowerManager
        public void setDualScreenPolicy(int dualScreenPolicy) {
            Slog.d(PowerManagerService.TAG, "[api] setDualScreenPolicy: dualScreenPolicy: " + dualScreenPolicy);
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService.this.mDualScreenPolicy = dualScreenPolicy;
                PowerManagerService.this.userActivityNoUpdateLocked(SystemClock.uptimeMillis(), 0, 0, 1000);
                PowerManagerService.this.updatePowerStateLocked();
            }
        }

        @Override // android.os.IPowerManager
        public void setSmartGlowState(int ledno, int color1, int color2, int color3, int color4, int onMs1, int onMs2, int onMs3, int onMs4, int offMs1, int offMs2, int offMs3, int offMs4, int mode) {
        }

        @Override // android.os.IPowerManager
        public void setCameraMode(boolean set, IBinder binder) {
        }

        @Override // android.os.IPowerManager
        public void setDozeOverrideFromAod(int mode, int screenState, int screenBrightness, IBinder binder) {
        }

        @Override // android.os.IPowerManager
        public void setProximityDebounceTime(IBinder lock, int positive, int negative) {
            Slog.d(PowerManagerService.TAG, "[api] setProximityDebounceTime: Positive = " + positive + ", Negative = " + negative);
            if (lock != null) {
                PowerManagerService.this.mContext.enforceCallingOrSelfPermission(Manifest.permission.WAKE_LOCK, null);
                PowerManagerService.this.setProximityDebounceTimeInternal(lock, positive, negative);
                return;
            }
            throw new IllegalArgumentException("lock must not be null");
        }

        @Override // android.os.IPowerManager
        public void setEarlyWakeUp(boolean enable) {
            Slog.d(PowerManagerService.TAG, "[api] setEarlyWakeUp: enable: " + enable);
            synchronized (PowerManagerService.this.mLock) {
                PowerManagerService.this.mLastWakeUpReason = 0;
                if (PowerManagerService.this.isWakeUpPreventionNeededLocked()) {
                    return;
                }
                PowerManagerService.access$2176(PowerManagerService.this, 32);
                PowerManagerService.this.mEarlyWakeUp = enable;
                PowerManagerService.this.updatePowerStateLocked();
            }
        }
    }

    @VisibleForTesting
    int getLastShutdownReasonInternal(String lastRebootReasonProperty) {
        String line = SystemProperties.get(lastRebootReasonProperty);
        if (line == null) {
            return 0;
        }
        char c = 65535;
        switch (line.hashCode()) {
            case -2117951935:
                if (line.equals(REASON_THERMAL_SHUTDOWN)) {
                    c = 4;
                    break;
                }
                break;
            case -1099647817:
                if (line.equals(REASON_LOW_BATTERY)) {
                    c = 5;
                    break;
                }
                break;
            case -934938715:
                if (line.equals(REASON_REBOOT)) {
                    c = 1;
                    break;
                }
                break;
            case -852189395:
                if (line.equals(REASON_USERREQUESTED)) {
                    c = 3;
                    break;
                }
                break;
            case -169343402:
                if (line.equals(REASON_SHUTDOWN)) {
                    c = 0;
                    break;
                }
                break;
            case 42900028:
                if (line.equals(REASON_REBOOT_BY_USER)) {
                    c = 2;
                    break;
                }
                break;
            case 1218064802:
                if (line.equals(REASON_BATTERY_THERMAL_STATE)) {
                    c = 6;
                    break;
                }
                break;
        }
        switch (c) {
            case 0:
                return 1;
            case 1:
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            case 6:
                return 6;
            default:
                return 0;
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private final class LocalService extends PowerManagerInternal {
        private LocalService() {
        }

        @Override // android.os.PowerManagerInternal
        public void setScreenBrightnessOverrideFromWindowManager(int screenBrightness) {
            screenBrightness = (screenBrightness < -1 || screenBrightness > 255) ? -1 : -1;
            PowerManagerService.this.setScreenBrightnessOverrideFromWindowManagerInternal(screenBrightness);
        }

        @Override // android.os.PowerManagerInternal
        public void setDozeOverrideFromDreamManager(int screenState, int screenBrightness) {
            Slog.d(PowerManagerService.TAG, "[api] setDozeOverrideFromDreamManager: screenState: " + Display.stateToString(screenState) + " screenBrightness: " + screenBrightness);
            switch (screenState) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                    break;
                default:
                    screenState = 0;
                    break;
            }
            screenBrightness = (screenBrightness < -1 || screenBrightness > 255) ? -1 : -1;
            PowerManagerService.this.setDozeOverrideFromDreamManagerInternal(screenState, screenBrightness);
        }

        @Override // android.os.PowerManagerInternal
        public void setDozeOverrideFromDreamManager(int screenState, int dozeMode, int screenBrightness) {
            PowerManagerService.this.setDozeModeBySysfs(dozeMode);
            setDozeOverrideFromDreamManager(screenState, screenBrightness);
        }

        @Override // android.os.PowerManagerInternal
        public void setUserInactiveOverrideFromWindowManager() {
            Slog.d(PowerManagerService.TAG, "[api] setUserInactiveOverrideFromWindowManager()" + PowerManagerService.callerInfoToString());
            PowerManagerService.this.setUserInactiveOverrideFromWindowManagerInternal();
        }

        @Override // android.os.PowerManagerInternal
        public void setUserActivityTimeoutOverrideFromWindowManager(long timeoutMillis) {
            PowerManagerService.this.setUserActivityTimeoutOverrideFromWindowManagerInternal(timeoutMillis);
        }

        @Override // android.os.PowerManagerInternal
        public void setUserActivityTimeoutForDexOverrideFromWindowManager(long timeoutMillis) {
            PowerManagerService.this.setUserActivityTimeoutForDexOverrideFromWindowManagerInternal(timeoutMillis);
        }

        @Override // android.os.PowerManagerInternal
        public void setDrawWakeLockOverrideFromSidekick(boolean keepState) {
            PowerManagerService.this.setDrawWakeLockOverrideFromSidekickInternal(keepState);
        }

        @Override // android.os.PowerManagerInternal
        public void setMaximumScreenOffTimeoutFromDeviceAdmin(int userId, long timeMs) {
            Slog.d(PowerManagerService.TAG, "[api] setMaximumScreenOffTimeoutFromDeviceAdmin: userId: " + userId + " timeMs: " + timeMs + PowerManagerService.callerInfoToString());
            PowerManagerService.this.setMaximumScreenOffTimeoutFromDeviceAdminInternal(userId, timeMs);
        }

        @Override // android.os.PowerManagerInternal
        public void setMaximumScreenOffTimeoutFromKnox(int userId, long timeMs) {
            Slog.d(PowerManagerService.TAG, "[api] setMaximumScreenOffTimeoutFromKnox: userId: " + userId + " timeMs: " + timeMs + PowerManagerService.callerInfoToString());
            PowerManagerService.this.setMaximumScreenOffTimeoutFromKnoxInternal(userId, timeMs);
        }

        @Override // android.os.PowerManagerInternal
        public PowerSaveState getLowPowerState(int serviceType) {
            return PowerManagerService.this.mBatterySaverPolicy.getBatterySaverPolicy(serviceType, PowerManagerService.this.mBatterySaverController.isEnabled());
        }

        @Override // android.os.PowerManagerInternal
        public void registerLowPowerModeObserver(PowerManagerInternal.LowPowerModeListener listener) {
            Slog.d(PowerManagerService.TAG, "[api] registerLowPowerModeObserver: " + listener + PowerManagerService.callerInfoToString());
            PowerManagerService.this.mBatterySaverController.addListener(listener);
        }

        @Override // android.os.PowerManagerInternal
        public boolean setDeviceIdleMode(boolean enabled) {
            Slog.d(PowerManagerService.TAG, "[api] setDeviceIdleMode: " + enabled + PowerManagerService.callerInfoToString());
            return PowerManagerService.this.setDeviceIdleModeInternal(enabled);
        }

        @Override // android.os.PowerManagerInternal
        public boolean setLightDeviceIdleMode(boolean enabled) {
            Slog.d(PowerManagerService.TAG, "[api] setLightDeviceIdleMode: " + enabled + PowerManagerService.callerInfoToString());
            return PowerManagerService.this.setLightDeviceIdleModeInternal(enabled);
        }

        @Override // android.os.PowerManagerInternal
        public void setDeviceIdleWhitelist(int[] appids) {
            Slog.d(PowerManagerService.TAG, "[api] setDeviceIdleWhitelist: appids: " + PowerManagerService.arrayToString(appids) + PowerManagerService.callerInfoToString());
            PowerManagerService.this.setDeviceIdleWhitelistInternal(appids);
        }

        @Override // android.os.PowerManagerInternal
        public void setDeviceIdleTempWhitelist(int[] appids) {
            Slog.d(PowerManagerService.TAG, "[api] setDeviceIdleTempWhitelist: appids: " + PowerManagerService.arrayToString(appids) + PowerManagerService.callerInfoToString());
            PowerManagerService.this.setDeviceIdleTempWhitelistInternal(appids);
        }

        @Override // android.os.PowerManagerInternal
        public void startUidChanges() {
            PowerManagerService.this.startUidChangesInternal();
        }

        @Override // android.os.PowerManagerInternal
        public void finishUidChanges() {
            PowerManagerService.this.finishUidChangesInternal();
        }

        @Override // android.os.PowerManagerInternal
        public void updateUidProcState(int uid, int procState) {
            PowerManagerService.this.updateUidProcStateInternal(uid, procState);
        }

        @Override // android.os.PowerManagerInternal
        public void uidGone(int uid) {
            PowerManagerService.this.uidGoneInternal(uid);
        }

        @Override // android.os.PowerManagerInternal
        public void uidActive(int uid) {
            PowerManagerService.this.uidActiveInternal(uid);
        }

        @Override // android.os.PowerManagerInternal
        public void uidIdle(int uid) {
            PowerManagerService.this.uidIdleInternal(uid);
        }

        @Override // android.os.PowerManagerInternal
        public void powerHint(int hintId, int data) {
            PowerManagerService.this.powerHintInternal(hintId, data);
        }

        @Override // android.os.PowerManagerInternal
        public void setScreenDimDurationOverrideFromWindowManager(long timeoutMillis) {
            PowerManagerService.this.setScreenDimDurationOverrideFromWindowManagerInternal(timeoutMillis);
        }

        @Override // android.os.PowerManagerInternal
        public boolean isProximityPositive() {
            return PowerManagerService.this.isProximityPositiveInternal();
        }

        @Override // android.os.PowerManagerInternal
        public boolean isInternalDisplayOff() {
            return PowerManagerService.this.isInternalDisplayOffInternal();
        }

        @Override // android.os.PowerManagerInternal
        public void setGoToSleepPrevention(boolean enabled) {
            Slog.d(PowerManagerService.TAG, "[api] setGoToSleepPrevention: enabled: " + enabled + PowerManagerService.callerInfoToString());
            PowerManagerService.this.setGoToSleepPreventionInternal(enabled);
        }

        @Override // android.os.PowerManagerInternal
        public void setDisableWakeUpPrevention(boolean disabled) {
            Slog.d(PowerManagerService.TAG, "[api] setDisableWakeUpPrevention: disabled: " + disabled + PowerManagerService.callerInfoToString());
            PowerManagerService.this.setDisableWakeUpPreventionInternal(disabled);
        }

        @Override // android.os.PowerManagerInternal
        public void setForcedDisplayOff(boolean off) {
            Slog.d(PowerManagerService.TAG, "[api] setForcedDisplayOff: off: " + off + PowerManagerService.callerInfoToString());
            PowerManagerService.this.setForcedDisplayOffInternal(off);
        }

        @Override // android.os.PowerManagerInternal
        public int setWakeLockEnableDisable(int uid, boolean disable) {
            synchronized (PowerManagerService.this.mLock) {
                Slog.i(PowerManagerService.TAG, "[PWL] SetWakeLockEnableDisable uid = " + uid + " , disable= " + disable);
                boolean changed = false;
                Iterator it = PowerManagerService.this.mWakeLocks.iterator();
                while (true) {
                    if (it.hasNext()) {
                        WakeLock wl = (WakeLock) it.next();
                        if (wl.mOwnerUid == uid && wl.mDisabledByFreecess != disable) {
                            wl.mDisabledByFreecess = disable;
                            if (PowerManagerService.this.setWakeLockDisabledStateLocked(wl)) {
                                changed = true;
                                if (wl.mDisabled) {
                                    PowerManagerService.this.notifyWakeLockReleasedLocked(wl);
                                } else {
                                    PowerManagerService.this.notifyWakeLockAcquiredLocked(wl);
                                }
                            }
                            if (changed) {
                                Slog.i(PowerManagerService.TAG, "[PWL] find out: " + wl + "  disabled -> " + disable);
                            }
                        } else if (wl.mWorkSource != null) {
                            int wsSize = wl.mWorkSource.size();
                            for (int i = 0; i < wsSize; i++) {
                                int wsUid = wl.mWorkSource.get(i);
                                if (wsUid == uid && wl.mDisabled != disable) {
                                    Slog.i(PowerManagerService.TAG, "[PWL] find out in ws: " + wl + "  disabled -> " + disable);
                                    return 2;
                                }
                            }
                            continue;
                        } else {
                            continue;
                        }
                    } else if (changed) {
                        PowerManagerService.access$2176(PowerManagerService.this, 1);
                        PowerManagerService.this.updatePowerStateLocked();
                        return 1;
                    } else {
                        Slog.i(PowerManagerService.TAG, "[PWL] can not change uid =  " + uid);
                        return 0;
                    }
                }
            }
        }

        @Override // android.os.PowerManagerInternal
        public void setWakeLockBlackListEnableDisable(int _appid, boolean enable) {
            synchronized (PowerManagerService.this.mLock) {
                boolean changed = false;
                try {
                    if (enable) {
                        if (PowerManagerService.this.mWakelockBlacklistAppid.contains(Integer.valueOf(_appid))) {
                            return;
                        }
                        PowerManagerService.this.mWakelockBlacklistAppid.add(Integer.valueOf(_appid));
                    } else if (!PowerManagerService.this.mWakelockBlacklistAppid.contains(Integer.valueOf(_appid))) {
                        return;
                    } else {
                        int index = PowerManagerService.this.mWakelockBlacklistAppid.indexOf(Integer.valueOf(_appid));
                        PowerManagerService.this.mWakelockBlacklistAppid.remove(index);
                    }
                    Iterator it = PowerManagerService.this.mWakeLocks.iterator();
                    while (it.hasNext()) {
                        WakeLock wl = (WakeLock) it.next();
                        int appid = UserHandle.getAppId(wl.mOwnerUid);
                        if (appid == _appid && PowerManagerService.this.setWakeLockDisabledStateLocked(wl)) {
                            changed = true;
                            if (wl.mDisabled) {
                                PowerManagerService.this.notifyWakeLockReleasedLocked(wl);
                            } else {
                                PowerManagerService.this.notifyWakeLockAcquiredLocked(wl);
                            }
                        }
                    }
                    if (changed) {
                        PowerManagerService.access$2176(PowerManagerService.this, 1);
                        PowerManagerService.this.updatePowerStateLocked();
                        return;
                    }
                    Slog.i(PowerManagerService.TAG, "[PWL] can not change appid =  " + _appid);
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public static final class WakeUpHistory {
        private static final int MAX_HISTORY = 50;
        public final String packageName;
        public final int reasonNum;
        public final String reasonStr;
        public final String time;
        public final long timeMillis;
        public final String topActivity;
        public final String topPackage;
        public final int uid;

        private WakeUpHistory(int uid, long timeMillis, String time, int reasonNum, String reasonStr, String packageName, String topActivity, String topPackage) {
            this.uid = uid;
            this.timeMillis = timeMillis;
            this.time = time;
            this.reasonNum = reasonNum;
            this.reasonStr = reasonStr;
            this.packageName = packageName;
            this.topActivity = topActivity;
            this.topPackage = topPackage;
        }

        public static WakeUpHistory[] getHistory() {
            return (WakeUpHistory[]) PowerManagerService.mWakeUpHistory.toArray();
        }
    }

    private void setInputDeviceEnabledLocked(boolean enabled) {
        this.mInputDeviceEnabled = enabled;
        this.mHandler.sendEmptyMessage(5);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleInputDeviceLightOnByScreenOn() {
        synchronized (this.mLock) {
            this.mDirty |= 131072;
            updatePowerStateLocked();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public final class InputDeviceLightState {
        public static final int LIGHT_STATE_OFF = 0;
        public static final int LIGHT_STATE_ON = 1;
        public static final int LIGHT_STATE_UNKNOWN = 2;
        public static final int MSG_LIGHT_OFF = 0;
        public static final int MSG_LIGHT_ON = 1;
        private Handler mHandlerInputDeviceLight;
        private final Light mInputDeviceLight;
        private boolean mIsKeepLight;
        private boolean mIsMsgPending;
        private int mInputDeviceLightState = 0;
        private int mInputDeviceLightTimeout = 1500;
        private HandlerThread mHandlerThreadInputDeviceLight = new HandlerThread("InputDeviceLight");

        public InputDeviceLightState(int lightsId) {
            this.mInputDeviceLight = PowerManagerService.this.mLightsManager.getLight(lightsId);
            this.mHandlerThreadInputDeviceLight.start();
            this.mHandlerInputDeviceLight = new Handler(this.mHandlerThreadInputDeviceLight.getLooper()) { // from class: com.android.server.power.PowerManagerService.InputDeviceLightState.1
                @Override // android.os.Handler
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case 0:
                            InputDeviceLightState.this.handleInputDeviceLightOff();
                            return;
                        case 1:
                            InputDeviceLightState.this.handleInputDeviceLightOn();
                            return;
                        default:
                            return;
                    }
                }
            };
            setInputDeviceLightOn(1);
        }

        public void setInputDeviceLightOn(int value) {
            if (this.mIsMsgPending) {
                this.mHandlerInputDeviceLight.removeMessages(0);
                this.mIsMsgPending = false;
            }
            if (value == 1) {
                Message msg = this.mHandlerInputDeviceLight.obtainMessage(1);
                msg.setAsynchronous(true);
                this.mHandlerInputDeviceLight.sendMessage(msg);
                if (this.mInputDeviceLightTimeout != -1 && !this.mIsKeepLight) {
                    Message msg2 = this.mHandlerInputDeviceLight.obtainMessage(0);
                    msg2.setAsynchronous(true);
                    this.mHandlerInputDeviceLight.sendMessageDelayed(msg2, this.mInputDeviceLightTimeout);
                    this.mIsMsgPending = true;
                }
            } else {
                Message msg3 = this.mHandlerInputDeviceLight.obtainMessage(0);
                msg3.setAsynchronous(true);
                this.mHandlerInputDeviceLight.sendMessage(msg3);
            }
            this.mInputDeviceLightState = value;
        }

        private int getInputDeviceLightState() {
            return this.mInputDeviceLightState;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleInputDeviceLightOn() {
            this.mInputDeviceLight.setBrightness(1);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleInputDeviceLightOff() {
            this.mHandlerInputDeviceLight.removeMessages(0);
            this.mIsMsgPending = false;
            this.mInputDeviceLight.setBrightness(0);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setInputDeviceLightTimeout(int value) {
            if (this.mInputDeviceLightTimeout != value) {
                this.mInputDeviceLightTimeout = value;
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int getInputDeviceLightTimeout() {
            return this.mInputDeviceLightTimeout;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setInputDeviceLightKeep(boolean enable) {
            this.mIsKeepLight = enable;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean getInputDeviceLightKeep() {
            return this.mIsKeepLight;
        }
    }

    private boolean isCoverClosedBySysfs() {
        if (isSupportCover()) {
            String coverState = readOneLine(COVER_SYSFS);
            return "CLOSE".equals(coverState);
        }
        return false;
    }

    private String readOneLine(String filepath) {
        String ret = "";
        BufferedReader buf = null;
        try {
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            try {
                buf = new BufferedReader(new FileReader(filepath), 10);
                ret = buf.readLine();
                if (ret != null) {
                    ret = ret.trim();
                }
                buf.close();
            } catch (IOException e2) {
                e2.printStackTrace();
                if (buf != null) {
                    buf.close();
                }
            }
            if (ret != null) {
                return ret;
            }
            return "";
        } catch (Throwable th) {
            if (buf != null) {
                try {
                    buf.close();
                } catch (IOException e3) {
                    e3.printStackTrace();
                }
            }
            throw th;
        }
    }

    private boolean isSupportCover() {
        boolean sIsFilpCoverSystemFeatureEnabled = this.mContext.getPackageManager().hasSystemFeature("com.sec.feature.cover.flip");
        boolean sIsSViewCoverSystemFeatureEnabled = this.mContext.getPackageManager().hasSystemFeature("com.sec.feature.cover.sview");
        return sIsFilpCoverSystemFeatureEnabled || sIsSViewCoverSystemFeatureEnabled;
    }

    private boolean sensorBlockedByCoverLocked() {
        int i = this.mCoverType;
        if (i == -1 || i == 2) {
            if (!this.mCoverAuthReady) {
                return this.mIsCoverClosed;
            }
            return false;
        }
        switch (i) {
            case 9:
            case 10:
                return false;
            default:
                return this.mIsCoverClosed;
        }
    }

    private boolean screenBlockedByCoverLocked() {
        int i = this.mCoverType;
        if (i == 0 || i == 7 || i == 100) {
            return this.mIsCoverClosed;
        }
        return false;
    }

    private boolean verifiedCoverClosedLocked() {
        int i = this.mCoverType;
        if (i == -1 || i == 2) {
            return false;
        }
        return this.mIsCoverClosed;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String callerInfoToString() {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        String retStr = " (uid: " + uid + " pid: " + pid + Separators.RPAREN;
        return retStr;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void printWakeLockLocked() {
        if (!this.mSystemReady || this.mDisplayPowerRequest.isBrightOrDim() || this.mDisplayPowerRequest.isVr()) {
            Slog.i(TAG, "[PWL] On : " + TimeUtils.formatUptime(this.mLastWakeTime));
            Slog.i(TAG, "[PWL]  mStayOn: " + this.mStayOn + "  mWakeLockSummary & WAKE_LOCK_STAY_AWAKE: " + Integer.toHexString(this.mWakeLockSummary & 32) + "  mUserActivitySummary: 0x" + Integer.toHexString(this.mUserActivitySummary));
            if ((this.mWakeLockSummary & 32) != 0) {
                Iterator<WakeLock> it = this.mWakeLocks.iterator();
                while (it.hasNext()) {
                    WakeLock wl = it.next();
                    if (isScreenLock(wl)) {
                        Slog.i(TAG, "[PWL]  " + wl);
                    }
                }
                return;
            }
            return;
        }
        Slog.i(TAG, "[PWL] Off : " + (timeSinceScreenWasLastOn() / 1000) + "s ago");
        Iterator<SuspendBlocker> it2 = this.mSuspendBlockers.iterator();
        while (it2.hasNext()) {
            SuspendBlocker sb = it2.next();
            SuspendBlockerImpl sbImpl = (SuspendBlockerImpl) sb;
            if (sbImpl.isHeld()) {
                Slog.i(TAG, "[PWL]   " + sb);
                if (!sbImpl.mName.equals(((SuspendBlockerImpl) this.mWakeLockSuspendBlocker).mName)) {
                    if (sbImpl.mName.equals(((SuspendBlockerImpl) this.mDisplaySuspendBlocker).mName)) {
                        if (this.mUserActivitySummary != 0) {
                            Slog.i(TAG, "[PWL]     mUserActivitySummary : 0x" + Integer.toHexString(this.mUserActivitySummary));
                        }
                        if (this.mDisplayPowerRequest.policy != 0) {
                            Slog.i(TAG, "[PWL]     mDisplayPowerRequest.policy : " + this.mDisplayPowerRequest.policy);
                        }
                        if (!this.mDisplayReady) {
                            Slog.i(TAG, "[PWL]     mDisplayReady : " + this.mDisplayReady);
                        }
                        if (!this.mBootCompleted) {
                            Slog.i(TAG, "[PWL]     mBootCompleted : " + this.mBootCompleted);
                        }
                    }
                } else if ((this.mWakeLockSummary & (-17)) != 0) {
                    Slog.i(TAG, "[PWL]     mWakeLockSummary : 0x" + Integer.toHexString(this.mWakeLockSummary));
                    if ((this.mWakeLockSummary & 1) != 0) {
                        Iterator<WakeLock> it3 = this.mWakeLocks.iterator();
                        while (it3.hasNext()) {
                            WakeLock wl2 = it3.next();
                            if ((wl2.mFlags & 65535) == 1) {
                                Slog.i(TAG, "[PWL]       " + wl2);
                            }
                        }
                    }
                    if ((this.mWakeLockSummary & (-2)) != 0) {
                        Iterator<WakeLock> it4 = this.mWakeLocks.iterator();
                        while (it4.hasNext()) {
                            WakeLock wl3 = it4.next();
                            if (isScreenLock(wl3)) {
                                Slog.e(TAG, "[PWL]       " + wl3);
                            }
                        }
                    }
                }
            }
        }
    }

    private void setSmartStayLocked() {
        if (this.mSmartStayEnabledSetting) {
            startSmartStayLocked();
        } else {
            stopSmartStayLocked();
        }
    }

    private void startSmartStayLocked() {
        if (this.mSmartStayProgress) {
            Slog.d(TAG, "SmartStay alredy started");
            return;
        }
        Slog.d(TAG, "SmartStay start");
        this.mSmartStayProgress = true;
        this.mHandlerThreadSmartStay = new HandlerThread("[smart stay]");
        this.mHandlerThreadSmartStay.start();
        this.mHandlerSmartStay = new Handler(this.mHandlerThreadSmartStay.getLooper()) { // from class: com.android.server.power.PowerManagerService.19
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        PowerManagerService.this.handleAbuseWakelockWhenUseractivityChanged();
                        PowerManagerService.this.handleSmartStay(false);
                        return;
                    case 2:
                        PowerManagerService.this.handleAbuseWakelockWhenUseractivityChanged();
                        PowerManagerService.this.handleSmartStay(true);
                        return;
                    default:
                        Slog.e(PowerManagerService.TAG, "undefined message : " + msg.what);
                        return;
                }
            }
        };
        this.mFaceDetectedFailIntent = new Intent(ACTION_BROADCAST_BEFORE_DIM, (Uri) null);
    }

    private void stopSmartStayLocked() {
        if (this.mSmartStayProgress) {
            Slog.d(TAG, "SmartStay stop");
            this.mSmartStayProgress = false;
            this.mHandlerSmartStay.removeCallbacksAndMessages(null);
            this.mHandlerThreadSmartStay.interrupt();
            this.mHandlerThreadSmartStay.quit();
            this.mHandlerThreadSmartStay = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleSmartStay(boolean bPreTest) {
        StringBuilder sb = new StringBuilder();
        sb.append("handleSmartStay");
        sb.append(bPreTest ? "(preTest)" : "");
        Slog.d(TAG, sb.toString());
        this.mFaceDetected = false;
        if (!this.mBootCompleted) {
            Slog.d(TAG, "handleSmartStay: mBootCompleted is false. return");
        } else if ((this.mWakeLockSummary & 2) != 0 && !this.mAllBrightWakeLockAbuse) {
            Slog.d(TAG, "handleSmartStay : skip smart stay because of screen bright wake lock");
        } else if (this.mIsFolderType && this.mIsLidClosed) {
            Slog.d(TAG, "handleSmartStay : skip smart stay because folder is closed");
        } else {
            long startTimeFaceDetect = SystemClock.uptimeMillis();
            boolean bFaceDetected = faceDetect();
            long finishTimeFaceDetect = SystemClock.uptimeMillis();
            long elapsedTime = finishTimeFaceDetect - startTimeFaceDetect;
            if (bPreTest) {
                if (finishTimeFaceDetect > this.mNextTimeoutForPreSmartStay && finishTimeFaceDetect < this.mNextTimeoutForSmartStay) {
                    if (!bFaceDetected) {
                        this.mContext.sendBroadcastAsUser(this.mFaceDetectedFailIntent, UserHandle.ALL);
                        Slog.d(TAG, "handleSmartStay(preTest) : sendBroadcast done.");
                        return;
                    }
                    return;
                }
                Slog.e(TAG, "handleSmartStay(preTest) : canceled (T:" + elapsedTime + "ms)");
            } else if (finishTimeFaceDetect > this.mNextTimeoutForSmartStay) {
                this.mFaceDetected = bFaceDetected;
                Slog.d(TAG, "handleSmartStay : mFaceDetected : " + this.mFaceDetected);
            } else {
                Slog.e(TAG, "handleSmartStay : canceled (T:" + elapsedTime + "ms)");
            }
        }
    }

    private boolean isPreSmartStayNeeded(long now) {
        return this.USE_PRE_SMART_STAY && this.mUserActivitySummary == 1 && now < this.mNextTimeoutForPreSmartStay && this.mNextTimeoutForPreSmartStay < this.mNextTimeoutForSmartStay;
    }

    private boolean isSmartStayNeeded(long now) {
        return this.mNextTimeoutForSmartStay > now;
    }

    private boolean faceDetect() {
        if (this.mSmartFaceManager == null) {
            this.mSmartFaceManager = SmartFaceManager.getSmartFaceManager(this.mContext);
        }
        if (this.mSmartFaceManager == null) {
            return false;
        }
        boolean bFaceDetected = this.mSmartFaceManager.checkForSmartStay();
        Slog.d(TAG, "faceDetect : bFaceDetected : " + bFaceDetected);
        return bFaceDetected;
    }

    private boolean isTablet() {
        String deviceType = SystemProperties.get("ro.build.characteristics");
        return deviceType != null && deviceType.contains("tablet");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String callerInfoWithProcessName() {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        String processName = null;
        BufferedReader in = null;
        try {
            try {
                try {
                    in = new BufferedReader(new FileReader(String.format("/proc/%d/cmdline", Integer.valueOf(pid))));
                    String line = in.readLine();
                    if (line != null) {
                        String[] t = line.split(TelephonyUtil.DEFAULT_EAP_PREFIX);
                        if (t.length > 0) {
                            processName = t[0];
                        }
                    }
                    in.close();
                } catch (Throwable th) {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            Slog.e(TAG, "error on in.close", e);
                        }
                    }
                    throw th;
                }
            } catch (IOException e2) {
                Slog.e(TAG, "error on read process name", e2);
                if (in != null) {
                    in.close();
                }
            }
        } catch (IOException e3) {
            Slog.e(TAG, "error on in.close", e3);
        }
        return String.format(" (uid: %d pid: %d processName: %s)", Integer.valueOf(uid), Integer.valueOf(pid), processName);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String arrayToString(int[] array) {
        if (array == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            sb.append(String.format("[%d]%d ", Integer.valueOf(i), Integer.valueOf(array[i])));
        }
        return sb.toString();
    }

    private void setUserActivityTimeoutLocked(long now) {
        Slog.d(TAG, "setUserActivityTimeoutLocked SRUK : now=" + now);
        if (this.mBootCompleted) {
            long when = this.mUserActivityTimeoutSetting + now;
            if (this.mNoUserActivitySent) {
                this.mHandler.removeCallbacks(this.mUserActivityTask);
                this.mHandler.post(this.mUserActivityTask);
            }
            this.mHandler.removeCallbacks(this.mUserActivityTimeoutTask);
            this.mHandler.postAtTime(this.mUserActivityTimeoutTask, when);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public class UserActivityTask implements Runnable {
        private UserActivityTask() {
        }

        @Override // java.lang.Runnable
        public void run() {
            if (PowerManagerService.this.mNoUserActivitySent && PowerManagerService.this.mContext != null && ActivityManagerNative.isSystemReady()) {
                PowerManagerService.this.mContext.sendBroadcastAsUser(PowerManagerService.this.mUserActivityIntent, UserHandle.ALL, Manifest.permission.KNOX_CUSTOM_SYSTEM);
                PowerManagerService.this.mContext.sendBroadcastAsUser(PowerManagerService.this.mUserActivityIntentLegacy, UserHandle.ALL, Manifest.permission.CUSTOM_SYSTEM);
                PowerManagerService.this.mNoUserActivitySent = false;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public class UserActivityTimeoutTask implements Runnable {
        private UserActivityTimeoutTask() {
        }

        @Override // java.lang.Runnable
        public void run() {
            if (PowerManagerService.this.mContext != null && ActivityManagerNative.isSystemReady()) {
                PowerManagerService.this.mContext.sendBroadcastAsUser(PowerManagerService.this.mNoUserActivityIntent, UserHandle.ALL, Manifest.permission.KNOX_CUSTOM_SYSTEM);
                PowerManagerService.this.mContext.sendBroadcastAsUser(PowerManagerService.this.mNoUserActivityIntentLegacy, UserHandle.ALL, Manifest.permission.CUSTOM_SYSTEM);
                PowerManagerService.this.mNoUserActivitySent = true;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    public class LCDFlashModeLock implements IBinder.DeathRecipient {
        IBinder binder;

        LCDFlashModeLock(IBinder binder) {
            this.binder = binder;
            if (binder != null) {
                try {
                    binder.linkToDeath(this, 0);
                } catch (RemoteException e) {
                    binderDied();
                }
            }
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            synchronized (PowerManagerService.this.mLock) {
                Slog.d(PowerManagerService.TAG, "LCDFlashModeLock : binderDied");
                PowerManagerService.this.setLCDFlashModeInternal(false, this.binder);
                PowerManagerService.this.initLCDFlashMode();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void initLCDFlashMode() {
        this.mLCDFlashModeLock = null;
        Slog.d(TAG, "mLCDFlashModeLock set to null");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setLCDFlashModeInternal(boolean enable, IBinder binder) {
        synchronized (this.mLock) {
            if (this.mLCDFlashModeLock == null) {
                this.mLCDFlashModeLock = new LCDFlashModeLock(binder);
            }
            if (this.mLCDFlashMode != enable) {
                this.mLCDFlashMode = enable;
                this.mDirty |= 32;
                updatePowerStateLocked();
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes2.dex */
    private class ScaleFactorLock implements IBinder.DeathRecipient {
        IBinder binder;

        ScaleFactorLock(IBinder binder) {
            this.binder = binder;
            try {
                binder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                binderDied();
            }
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            synchronized (PowerManagerService.this.mLock) {
                Slog.e(PowerManagerService.TAG, "ScaleFactorLock : binderDied");
                PowerManagerService.this.mScreenBrightnessScaleFactor = -1.0f;
                PowerManagerService.this.mScaleFactorLock = null;
                PowerManagerService.access$2176(PowerManagerService.this, 32);
                PowerManagerService.this.updatePowerStateLocked();
            }
        }
    }

    private int readDisplayOnTimeFromEfs() {
        int ret;
        String display_on_time = readOneLine(PATH_AFC_DISPLAY_ON_TIME);
        if ("".equals(display_on_time) || (ret = Integer.parseInt(display_on_time)) <= 0) {
            return 0;
        }
        return ret;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void writeDisplayOnTimetoEfs() {
        PowerManagerUtil.fileWriteInt(PATH_AFC_DISPLAY_ON_TIME, this.mDisplayOnTime);
    }

    private void initSentDisplayOnTimeIntent(int time) {
        if (time > 0) {
            int month = time / DISPLAY_ON_TIME_SEND_EVERYMONTH;
            if (month <= 4) {
                for (int i = 0; i < month; i++) {
                    this.mSentDisplayOnTimeIntent[i] = 1;
                }
            }
            if (month >= 4) {
                Slog.d(TAG, "already sent ACTION_DISPLAY_ON_TIME all months");
                this.mSentAllDisplayOnTimeIntent = true;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void addDisplayOnTime(int time) {
        if (time <= 86400) {
            this.mDisplayOnTime += time;
            checkDisplayOnTimeForIntnet(this.mDisplayOnTime);
        }
    }

    private void checkDisplayOnTimeForIntnet(int time) {
        int month = time / DISPLAY_ON_TIME_SEND_EVERYMONTH;
        switch (month) {
            case 1:
            case 2:
            case 3:
            case 4:
                if (this.mSentDisplayOnTimeIntent[month - 1] != 1) {
                    sendDisplayonTimeIntent(month);
                    this.mSentDisplayOnTimeIntent[month - 1] = 1;
                    return;
                }
                return;
            default:
                return;
        }
    }

    private void sendDisplayonTimeIntent(final int month) {
        this.mHandler.post(new Runnable() { // from class: com.android.server.power.PowerManagerService.21
            @Override // java.lang.Runnable
            public void run() {
                Slog.d(PowerManagerService.TAG, "Sending ACTION_DISPLAY_ON_TIME");
                Intent displayOnTimeIntent = new Intent(PowerManagerService.ACTION_DISPLAY_ON_TIME);
                displayOnTimeIntent.putExtra(PowerManagerService.EXTRA_DISPLAY_ON_TIME, month);
                PowerManagerService.this.mContext.sendBroadcastAsUser(displayOnTimeIntent, UserHandle.ALL);
                if (month == 4) {
                    Slog.d(PowerManagerService.TAG, "Sending ACTION_DISPLAY_ON_TIME all months");
                    PowerManagerService.this.mSentAllDisplayOnTimeIntent = true;
                    PowerManagerService.this.writeDisplayOnTimetoEfs();
                }
            }
        });
    }
}