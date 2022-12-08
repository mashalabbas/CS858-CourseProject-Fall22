package com.android.server.audio;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.IProcessObserver;
import android.app.IUidObserver;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.database.ContentObserver;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.SemDeviceStatusListener;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiPlaybackClient;
import android.hardware.hdmi.HdmiTvClient;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioDevicePort;
import android.media.AudioFocusInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioManagerInternal;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioPort;
import android.media.AudioRecordingConfiguration;
import android.media.AudioRoutesInfo;
import android.media.AudioSystem;
import android.media.IAudioFocusDispatcher;
import android.media.IAudioRoutesObserver;
import android.media.IAudioServerStateDispatcher;
import android.media.IAudioService;
import android.media.IPlaybackConfigDispatcher;
import android.media.IRecordingConfigDispatcher;
import android.media.IRingtonePlayer;
import android.media.IVolumeController;
import android.media.MediaPlayer;
import android.media.PlayerBase;
import android.media.SoundPool;
import android.media.VolumePolicy;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioPolicyConfig;
import android.media.audiopolicy.IAudioPolicyCallback;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.FactoryTest;
import android.os.FileUtils;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SemHqmManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManagerInternal;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.storage.IStorageManager;
import android.provider.Settings;
import android.service.notification.ZenModeConfig;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AndroidRuntimeException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.MathUtils;
import android.util.Slog;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.XmlUtils;
import com.android.server.DirEncryptServiceHelper;
import com.android.server.EventLogTags;
import com.android.server.LocalServices;
import com.android.server.PermissionMonitor;
import com.android.server.SdpManagerService;
import com.android.server.SystemService;
import com.android.server.TimaService;
import com.android.server.audio.AudioEventLogger;
import com.android.server.audio.AudioServiceEvents;
import com.android.server.desktopmode.DesktopModeSettings;
import com.android.server.enterprise.apn.ApnSettingsPolicy;
import com.android.server.enterprise.storage.EdmStorageDefs;
import com.android.server.enterprise.vpn.knoxvpn.KnoxVpnConstants;
import com.android.server.media.MediaSessionService;
import com.android.server.net.UrspService;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.UserManagerService;
import com.android.server.sepunion.ExclusiveTaskManagerService;
import com.android.server.slice.SliceClientPermissions;
import com.android.server.utils.PriorityDump;
import com.samsung.android.audio.AppCategorizer;
import com.samsung.android.audio.AudioConstants;
import com.samsung.android.audio.AudioPopupManager;
import com.samsung.android.audio.AudioSettingsHelper;
import com.samsung.android.audio.RampInPreset;
import com.samsung.android.audio.Rune;
import com.samsung.android.camera.core.SemCamera;
import com.samsung.android.codecsolution.hdr.Constants;
import com.samsung.android.cover.CoverManager;
import com.samsung.android.cover.CoverState;
import com.samsung.android.desktopmode.DesktopModeManagerInternal;
import com.samsung.android.desktopmode.SemDesktopModeManager;
import com.samsung.android.displaysolution.ISemDisplaySolutionManager;
import com.samsung.android.displaysolution.SemDisplaySolutionManager;
import com.samsung.android.emergencymode.SemEmergencyManager;
import com.samsung.android.game.IGameManagerCallback;
import com.samsung.android.game.IGameManagerService;
import com.samsung.android.game.SemGameManager;
import com.samsung.android.hardware.context.SemContext;
import com.samsung.android.hardware.context.SemContextEvent;
import com.samsung.android.hardware.context.SemContextFlatMotion;
import com.samsung.android.hardware.context.SemContextListener;
import com.samsung.android.hardware.context.SemContextManager;
import com.samsung.android.hqm.HWParam.HWParamConst;
import com.samsung.android.hqm.HqmDBHelper;
import com.samsung.android.knox.SemPersonaManager;
import com.samsung.android.knox.container.KnoxContainerManager;
import com.samsung.android.knox.custom.CustomDeviceManagerProxy;
import com.samsung.android.knox.dlp.DLPConstants;
import com.samsung.android.media.AudioParameter;
import com.samsung.android.media.convert.core.Convert;
import com.samsung.android.os.SemDvfsManager;
import com.samsung.android.sepunion.SemGoodCatchManager;
import com.samsung.android.vr.GearVrManagerInternal;
import com.samsung.android.vr.IGearVrStateCallbacks;
import com.sec.ims.configuration.DATA;
import com.sec.ims.presence.ServiceTuple;
import com.sec.ims.settings.GlobalSettings;
import com.sec.smartcard.pkcs11.PKCS11Constants;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.obex.ResponseCodes;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

/* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
public class AudioService extends IAudioService.Stub implements AccessibilityManager.TouchExplorationStateChangeListener, AccessibilityManager.AccessibilityServicesStateChangeListener {
    private static final String ASSET_FILE_VERSION = "1.0";
    private static final String ATTR_ASSET_FILE = "file";
    private static final String ATTR_ASSET_ID = "id";
    private static final String ATTR_GROUP_NAME = "name";
    private static final String ATTR_VERSION = "version";
    private static final int BTA2DP_DOCK_TIMEOUT_MILLIS = 8000;
    private static final int BT_HEADSET_CNCT_TIMEOUT_MS = 3000;
    private static final int BT_HEARING_AID_GAIN_MIN = -128;
    public static final String CONNECT_INTENT_KEY_ADDRESS = "address";
    public static final String CONNECT_INTENT_KEY_DEVICE_CLASS = "class";
    public static final String CONNECT_INTENT_KEY_HAS_CAPTURE = "hasCapture";
    public static final String CONNECT_INTENT_KEY_HAS_MIDI = "hasMIDI";
    public static final String CONNECT_INTENT_KEY_HAS_PLAYBACK = "hasPlayback";
    public static final String CONNECT_INTENT_KEY_PORT_NAME = "portName";
    public static final String CONNECT_INTENT_KEY_STATE = "state";
    protected static final boolean DEBUG_AP = true;
    protected static final boolean DEBUG_DEVICES = true;
    protected static final boolean DEBUG_MODE = true;
    protected static final boolean DEBUG_VOL = true;
    private static final int DEFAULT_STREAM_TYPE_OVERRIDE_DELAY_MS = 0;
    protected static final int DEFAULT_VOL_STREAM_NO_PLAYBACK = 3;
    private static final int DEVICE_MEDIA_UNMUTED_ON_PLUG = 67266444;
    private static final int DEVICE_OVERRIDE_A2DP_ROUTE_ON_PLUG = 67264524;
    private static final int FLAG_ADJUST_VOLUME = 1;
    private static final int GOODCATCH_CALL_MODE = 1;
    private static final String GOODCATCH_MODULE = "AudioService";
    private static final int GOODCATCH_RINGER_MODE = 0;
    private static final String GROUP_TOUCH_SOUNDS = "touch_sounds";
    private static final int INDICATE_SYSTEM_READY_RETRY_DELAY_MS = 1000;
    private static final int MSG_A2DP_DEVICE_CONFIG_CHANGE = 103;
    private static final int MSG_ACCESSORY_PLUG_MEDIA_UNMUTE = 27;
    private static final int MSG_AUDIO_SERVER_DIED = 4;
    private static final int MSG_BROADCAST_AUDIO_BECOMING_NOISY = 15;
    private static final int MSG_BROADCAST_AUDIO_BECOMING_NOISY_PERSONA = 113;
    private static final int MSG_BROADCAST_BT_CONNECTION_STATE = 19;
    private static final int MSG_BTA2DP_DOCK_TIMEOUT = 106;
    private static final int MSG_BT_HEADSET_CNCT_FAILED = 9;
    private static final int MSG_CHECK_DVFS_CPU_BOOST_FOR_VOIP = 117;
    private static final int MSG_CHECK_MUSIC_ACTIVE = 14;
    private static final int MSG_CHECK_SAR_CONTROL = 124;
    private static final int MSG_CHECK_VOLUME_MONITOR_STATE = 107;
    private static final int MSG_CONFIGURE_SAFE_MEDIA_VOLUME = 16;
    private static final int MSG_CONFIGURE_SAFE_MEDIA_VOLUME_FORCED = 17;
    private static final int MSG_DISABLE_AUDIO_FOR_UID = 104;
    private static final int MSG_DISPATCH_AUDIO_SERVER_STATE = 29;
    private static final int MSG_DYN_POLICY_MIX_STATE_UPDATE = 25;
    private static final int MSG_ENABLE_SURROUND_FORMATS = 30;
    private static final int MSG_END_SOUND_MANNER_MODE = 111;
    private static final int MSG_FORCE_TURNOFF_SPK2 = 128;
    private static final int MSG_INDICATE_SYSTEM_READY = 26;
    private static final int MSG_INIT_DUAL_SPEAKER = 131;
    private static final int MSG_LOAD_SOUND_EFFECTS = 7;
    private static final int MSG_NOTIFY_VOL_EVENT = 28;
    private static final int MSG_PERFORM_SOFT_RESET = 108;
    private static final int MSG_PERSIST_MUSIC_ACTIVE_MS = 22;
    private static final int MSG_PERSIST_RINGER_MODE = 3;
    private static final int MSG_PERSIST_SAFE_VOLUME_STATE = 18;
    private static final int MSG_PERSIST_VOLUME = 1;
    static final int MSG_PLAYER_CHANGED_CALLBACK = 129;
    private static final int MSG_PLAY_SOUND_EFFECT = 5;
    private static final int MSG_PLAY_SOUND_MANNER_MODE = 109;
    static final int MSG_RECORDER_ERR_CALLBACK = 130;
    private static final int MSG_REPORT_NEW_ROUTES = 12;
    private static final int MSG_RUN_SOUNDASSISTANT = 122;
    private static final int MSG_SEC_BASE = 106;
    private static final int MSG_SEND_SOUNDEVENT_TO_RECEIVER = 126;
    private static final int MSG_SET_A2DP_SINK_CONNECTION_STATE = 102;
    private static final int MSG_SET_A2DP_SRC_CONNECTION_STATE = 101;
    private static final int MSG_SET_ALL_VOLUMES = 10;
    private static final int MSG_SET_DEVICE_VOLUME = 0;
    static final int MSG_SET_DVFS_FOR_SOUNDALIVE = 116;
    private static final int MSG_SET_FINE_VOLUME = 115;
    private static final int MSG_SET_FORCE_BT_A2DP_USE = 13;
    private static final int MSG_SET_FORCE_USE = 8;
    private static final int MSG_SET_HEARING_AID_CONNECTION_STATE = 105;
    private static final int MSG_SET_MULTI_DEVICE_SOUND_NOTIFICATION = 120;
    private static final int MSG_SET_MULTI_DEVICE_SOUND_ON = 118;
    private static final int MSG_SET_MULTI_DEVICE_SOUND_SETDEVICE = 119;
    private static final int MSG_SET_MUTE_INTERVAL = 121;
    private static final int MSG_SET_SYSTEMPROPERTY = 114;
    private static final int MSG_SET_WIRED_DEVICE_CONNECTION_STATE = 100;
    private static final int MSG_SOUND_ASSISTANT_INIT = 132;
    private static final int MSG_START_SENSOR_FOR_DUALSPK = 127;
    private static final int MSG_SYSTEM_READY = 21;
    private static final int MSG_UNLOAD_SOUND_EFFECTS = 20;
    private static final int MSG_UNMUTE_STREAM = 24;
    private static final int MSG_UPDATE_BT_VOLUME = 123;
    private static final int MSG_UPDATE_MULTI_DEVICE_SOUND_AUDIOFOCUS = 125;
    private static final int MSG_VIBRATE_CALL = 112;
    private static final int MSG_VIBRATE_MANNER_MODE = 110;
    private static final String MULTISOUND_APP = "multisound_app";
    private static final String MULTISOUND_DEVICE_TYPE = "multisound_devicetype";
    private static final String MULTISOUND_STATE = "multisound_state";
    private static final int MUSIC_ACTIVE_POLL_PERIOD_MS = 60000;
    private static final String NOTIFICATION_CHANNEL_ID = "AudioCore_Notification";
    private static final int NUM_SOUNDPOOL_CHANNELS = 4;
    private static final int PERFORMANCE_MODE_ENTERTAINMENT = 2;
    private static final int PERSIST_DELAY = 500;
    private static final int SAFE_MEDIA_VOLUME_ACTIVE = 3;
    private static final int SAFE_MEDIA_VOLUME_DISABLED = 1;
    private static final int SAFE_MEDIA_VOLUME_INACTIVE = 2;
    private static final int SAFE_MEDIA_VOLUME_NOT_CONFIGURED = 0;
    private static final int SAFE_VOLUME_CONFIGURE_TIMEOUT_MS = 30000;
    private static final int SCO_MODE_MAX = 2;
    private static final int SCO_MODE_RAW = 1;
    private static final int SCO_MODE_UNDEFINED = -1;
    private static final int SCO_MODE_VIRTUAL_CALL = 0;
    private static final int SCO_MODE_VR = 2;
    private static final int SCO_STATE_ACTIVATE_REQ = 1;
    private static final int SCO_STATE_ACTIVE_EXTERNAL = 2;
    private static final int SCO_STATE_ACTIVE_INTERNAL = 3;
    private static final int SCO_STATE_DEACTIVATE_REQ = 4;
    private static final int SCO_STATE_DEACTIVATING = 5;
    private static final int SCO_STATE_INACTIVE = 0;
    private static final int SENDMSG_NOOP = 1;
    private static final int SENDMSG_QUEUE = 2;
    private static final int SENDMSG_REPLACE = 0;
    private static final int SOUND_EFFECTS_LOAD_TIMEOUT_MS = 5000;
    private static final String SOUND_EFFECTS_PATH = "/media/audio/ui/";
    private static final String TAG = "AudioService";
    private static final String TAG_ASSET = "asset";
    private static final String TAG_AUDIO_ASSETS = "audio_assets";
    private static final String TAG_GROUP = "group";
    private static final int TOUCH_EXPLORE_STREAM_TYPE_OVERRIDE_DELAY_MS = 1000;
    private static final int UNMUTE_STREAM_DELAY = 350;
    private static final int UNSAFE_VOLUME_MUSIC_ACTIVE_MS_MAX = 72000000;
    protected static int[] mStreamVolumeAlias;
    private static int sSoundEffectVolumeDb;
    private static int sStreamOverrideDelayMs;
    private BluetoothA2dp mA2dp;
    private int[] mAccessibilityServiceUids;
    private ActivityManager mActivityManager;
    private final ActivityManagerInternal mActivityManagerInternal;
    private int mAllSoundMute;
    private final AppOpsManager mAppOps;
    private PowerManager.WakeLock mAudioEventWakeLock;
    private AudioHandler mAudioHandler;
    private AudioSystemThread mAudioSystemThread;
    private boolean mBluetoothA2dpEnabled;
    private BluetoothHeadset mBluetoothHeadset;
    private BluetoothDevice mBluetoothHeadsetDevice;
    private String mBtNrec;
    private String mBtSampleRate;
    @GuardedBy("mSettingsLock")
    private boolean mCameraSoundForced;
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private boolean mDexConnectedState;
    private boolean mDexPadConnectedState;
    private boolean mDexState;
    private String mDockAddress;
    private String mEnabledSurroundFormats;
    private int mEncodedSurroundMode;
    private IAudioPolicyCallback mExtVolumeController;
    private FineVolumeState mFineMediaVolume;
    private int mForcedUseForComm;
    private int mForcedUseForCommExt;
    private int mForcedUseForFMRadio;
    private int mForcedUseForMedia;
    private GameStateChecker mGameStateChecker;
    private final boolean mHasVibrator;
    private boolean mHdmiCecSink;
    private HdmiControlManager mHdmiManager;
    private HdmiPlaybackClient mHdmiPlaybackClient;
    private HdmiTvClient mHdmiTvClient;
    private BluetoothHearingAid mHearingAid;
    private int mIsBtOffloadEnabled;
    private final boolean mIsSingleVolume;
    private long mLoweredFromNormalToVibrateTime;
    private final MediaFocusControl mMediaFocusControl;
    private final boolean mMonitorRotation;
    private int mMonoMode;
    private int mMusicActiveMs;
    private int mMuteAffectedStreams;
    private int mMySoundEnabled;
    private int mNbQualityMode;
    private NotificationManager mNm;
    private PackageManager mPackageManager;
    private StreamVolumeCommand mPendingFineVolumeCommand;
    private StreamVolumeCommand mPendingVolumeCommand;
    private int mPerformanceMode;
    private int mPerformanceUpScalerMode;
    private String mPhoneType;
    private final int mPlatformType;
    private final PlaybackActivityMonitor mPlaybackMonitor;
    private AudioPopupManager mPopupManager;
    private final PowerManager mPowerManager;
    private final RecordingActivityMonitor mRecordMonitor;
    private int mRingerAndZenModeMutedStreams;
    @GuardedBy("mSettingsLock")
    private int mRingerMode;
    private AudioManagerInternal.RingerModeDelegate mRingerModeDelegate;
    private volatile IRingtonePlayer mRingtonePlayer;
    private int mSafeMediaVolumeIndex;
    private Integer mSafeMediaVolumeState;
    private float mSafeUsbMediaVolumeDbfs;
    private int mSafeUsbMediaVolumeIndex;
    private int mScoAudioMode;
    private int mScoAudioState;
    private int mScoConnectionState;
    private boolean mSelfiestickIsConnected;
    private SemDeviceStatusListener mSemDeviceStatusListener;
    private SemDisplaySolutionManager mSemDisplaySolutionManager;
    private SemGoodCatchManager mSemGoodCatchManager;
    private AudioSettingsHelper mSettingHelper;
    private SettingsObserver mSettingsObserver;
    private int mSoundBalance;
    private SoundPool mSoundPool;
    private SoundPoolCallback mSoundPoolCallBack;
    private SoundPoolListenerThread mSoundPoolListenerThread;
    private VolumeStreamState[] mStreamStates;
    private boolean mSurroundModeChanged;
    private boolean mSystemReady;
    private int mUhqBtSampleRate;
    private int mUpscalerEnabled;
    private final boolean mUseFixedVolume;
    private final UserManagerInternal mUserManagerInternal;
    private boolean mUserSwitchedReceived;
    private int mVibrateSetting;
    private Vibrator mVibrator;
    private VolumePolicy mVolumePolicy;
    private static final List<String> SOUND_EFFECT_FILES = new ArrayList();
    protected static int[] MAX_STREAM_VOLUME = {5, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 1, 1, 15, 5, 5, 15};
    protected static int[] MIN_STREAM_VOLUME = {0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0};
    private static final int[] STREAM_VOLUME_OPS = {34, 36, 35, 36, 37, 38, 39, 36, 36, 36, 64, 36, 36, 36, 34, 34, 36};
    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder().setContentType(4).setUsage(13).build();
    private static Long mLastDeviceConnectMsgTime = new Long(0);
    private static boolean sIndependentA11yVolume = false;
    private static final String[] RINGER_MODE_NAMES = {"SILENT", "VIBRATE", PriorityDump.PRIORITY_ARG_NORMAL};
    private static final String[] GOODCATCH_MODE = {"ringermode", "callmode"};
    private VolumeController mVolumeController = new VolumeController();
    private int mMode = 0;
    private final Object mSettingsLock = new Object();
    private final Object mSoundEffectsLock = new Object();
    private final int[][] SOUND_EFFECT_FILES_MAP = (int[][]) Array.newInstance(int.class, 14, 2);
    private final int[] STREAM_VOLUME_ALIAS_VOICE = {0, 1, 2, 3, 4, 2, 6, 7, 2, 3, 3, 3, 3, 3, 0, 0, 16};
    private final int[] STREAM_VOLUME_ALIAS_TELEVISION = {3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 16};
    private final int[] STREAM_VOLUME_ALIAS_DEFAULT = {0, 1, 2, 3, 4, 2, 6, 7, 2, 3, 3, 3, 3, 3, 0, 0, 16};
    private final AudioSystem.ErrorCallback mAudioSystemCallback = new AudioSystem.ErrorCallback() { // from class: com.android.server.audio.AudioService.1
        public void onError(int error) {
            if (error == 100) {
                AudioService.sendMsg(AudioService.this.mAudioHandler, 4, 1, 0, 0, null, 0);
                AudioService.sendMsg(AudioService.this.mAudioHandler, 29, 2, 0, 0, null, 0);
            }
        }
    };
    @GuardedBy("mSettingsLock")
    private int mRingerModeExternal = -1;
    private int mRingerModeAffectedStreams = 0;
    private int mZenModeAffectedStreams = 0;
    private final BroadcastReceiver mReceiver = new AudioServiceBroadcastReceiver();
    private final BroadcastReceiver mSamsungReceiver = new SamsungBroadcastReceiver();
    private final UserManagerInternal.UserRestrictionsListener mUserRestrictionsListener = new AudioServiceUserRestrictionsListener();
    private final ArrayMap<String, DeviceListSpec> mConnectedDevices = new ArrayMap<>();
    private final ArrayList<SetModeDeathHandler> mSetModeDeathHandlers = new ArrayList<>();
    private final ArrayList<ScoClient> mScoClients = new ArrayList<>();
    private Looper mSoundPoolLooper = null;
    private int mPrevVolDirection = 0;
    private int mVolumeControlStream = -1;
    private boolean mUserSelectedVolumeControlStream = false;
    private final Object mForceControlStreamLock = new Object();
    private ForceControlStreamClient mForceControlStreamClient = null;
    private final Object mBluetoothA2dpEnabledLock = new Object();
    final AudioRoutesInfo mCurAudioRoutes = new AudioRoutesInfo();
    final RemoteCallbackList<IAudioRoutesObserver> mRoutesObservers = new RemoteCallbackList<>();
    int mFixedVolumeDevices = 0;
    int mFullVolumeDevices = 0;
    private boolean mDockAudioMediaEnabled = true;
    private int mDockState = 0;
    private final Object mHearingAidLock = new Object();
    private final Object mA2dpAvrcpLock = new Object();
    private boolean mAvrcpAbsVolSupported = false;
    private final Object mAccessibilityServiceUidsLock = new Object();
    private final IUidObserver mUidObserver = new IUidObserver.Stub() { // from class: com.android.server.audio.AudioService.2
        public void onUidStateChanged(int uid, int procState, long procStateSeq) {
        }

        public void onUidGone(int uid, boolean disabled) {
            disableAudioForUid(false, uid);
        }

        public void onUidActive(int uid) throws RemoteException {
        }

        public void onUidIdle(int uid, boolean disabled) {
        }

        public void onUidCachedChanged(int uid, boolean cached) {
            disableAudioForUid(cached, uid);
        }

        private void disableAudioForUid(boolean disable, int uid) {
            AudioService.this.queueMsgUnderWakeLock(AudioService.this.mAudioHandler, 104, disable ? 1 : 0, uid, null, 0);
        }
    };
    private int mRmtSbmxFullVolRefCount = 0;
    private ArrayList<RmtSbmxFullVolDeathHandler> mRmtSbmxFullVolDeathHandlers = new ArrayList<>();
    private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener = new BluetoothProfile.ServiceListener() { // from class: com.android.server.audio.AudioService.3
        /* JADX WARN: Code restructure failed: missing block: B:47:0x0109, code lost:
            r17.this$0.mScoAudioState = 0;
            r17.this$0.broadcastScoConnectionState(0);
         */
        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct code enable 'Show inconsistent code' option in preferences
        */
        public void onServiceConnected(int r18, android.bluetooth.BluetoothProfile r19) {
            /*
                Method dump skipped, instructions count: 434
                To view this dump change 'Code comments level' option to 'DEBUG'
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.server.audio.AudioService.AnonymousClass3.onServiceConnected(int, android.bluetooth.BluetoothProfile):void");
        }

        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceDisconnected(int profile) {
            if (profile == 11) {
                AudioService.this.disconnectA2dpSink();
            } else if (profile != 21) {
                switch (profile) {
                    case 1:
                        AudioService.this.disconnectHeadset();
                        return;
                    case 2:
                        AudioService.this.disconnectA2dp();
                        return;
                    default:
                        return;
                }
            } else {
                AudioService.this.disconnectHearingAid();
            }
        }
    };
    int mBecomingNoisyIntentDevices = 201490316;
    private final Object mSafeMediaVolumeStateLock = new Object();
    private int mMcc = 0;
    private final int mSafeMediaVolumeBTDevices = 384;
    private Integer mSafeMediaVolumeBTState = 3;
    private final int mSafeMediaVolumeDevices = 67109260;
    private boolean mHdmiSystemAudioSupported = false;
    private MyDisplayStatusCallback mHdmiDisplayStatusCallback = new MyDisplayStatusCallback();
    final int LOG_NB_EVENTS_PHONE_STATE = 20;
    final int LOG_NB_EVENTS_WIRED_DEV_CONNECTION = 30;
    final int LOG_NB_EVENTS_FORCE_USE = 20;
    final int LOG_NB_EVENTS_VOLUME = 100;
    final int LOG_NB_EVENTS_DYN_POLICY = 10;
    final int LOG_NB_EVENTS_MICROPHONE_MUTE = 30;
    final int LOG_NB_REC_FAIL_POPUP = 10;
    private final AudioEventLogger mModeLogger = new AudioEventLogger(20, "phone state (logged after successful call to AudioSystem.setPhoneState(int))");
    private final AudioEventLogger mWiredDevLogger = new AudioEventLogger(30, "wired device connection (logged before onSetWiredDeviceConnectionState() is executed)");
    private final AudioEventLogger mForceUseLogger = new AudioEventLogger(20, "force use (logged before setForceUse() is executed)");
    private final AudioEventLogger mVolumeLogger = new AudioEventLogger(100, "volume changes (logged when command received by AudioService)");
    private final AudioEventLogger mDynPolicyLogger = new AudioEventLogger(10, "dynamic policy events (logged when command received by AudioService)");
    private final AudioEventLogger mMicrophoneLogger = new AudioEventLogger(30, "setMicrophoneMute use (logged after setMicrophoneMute() is executed)");
    public final AudioEventLogger mRecFailPopupLogger = new AudioEventLogger(10, "Recoding failed popup");
    private final Object mExtVolumeControllerLock = new Object();
    private final AudioSystem.DynamicPolicyCallback mDynPolicyCallback = new AudioSystem.DynamicPolicyCallback() { // from class: com.android.server.audio.AudioService.5
        public void onDynamicPolicyMixStateUpdate(String regId, int state) {
            if (!TextUtils.isEmpty(regId)) {
                AudioService.sendMsg(AudioService.this.mAudioHandler, 25, 2, state, 0, regId, 0);
            }
        }
    };
    private HashMap<IBinder, AsdProxy> mAudioServerStateListeners = new HashMap<>();
    private final HashMap<IBinder, AudioPolicyProxy> mAudioPolicies = new HashMap<>();
    @GuardedBy("mAudioPolicies")
    private int mAudioPolicyCounter = 0;
    public boolean mHMTTetheredDocked = false;
    public boolean mHMTDocked = false;
    public boolean mExtraVolume = false;
    private final String mLateBootCompleted_WiredDeviceName_real = "h2w";
    private final String mLateBootCompleted_WiredDeviceName_temporary = "h2w-before-boot-completed";
    private String mThemeTouchSoundPath = null;
    private int mZenMode = 0;
    private int mPrevRingerMode = 2;
    private DeviceListSpec mLastConnectedDevice = null;
    private int mForceSpeaker = 0;
    private int mForceControlStreamPid = -1;
    private int mForceControlStreamUid = -1;
    private KeyguardManager mKeyguardManager = null;
    private MediaSessionService.MediaSessionServiceInternal mMediaSessionService = null;
    private int mForegroundUid = -1;
    private final IProcessObserver mProcessObserver = new IProcessObserver.Stub() { // from class: com.android.server.audio.AudioService.6
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            if (foregroundActivities) {
                AudioService.this.mForegroundUid = uid;
                Log.d("AudioService", "mForegroundUid = " + AudioService.this.mForegroundUid + "(" + AudioService.this.getPackageName(AudioService.this.mForegroundUid)[0] + ")");
            }
        }

        public void onProcessDied(int pid, int uid) {
        }
    };
    public final int PRIMARY_DEVICE = 67125262;
    private final int ID_MULTISOUND_NOTIFICATION = 1004;
    private final int ID_MULTISOUND_HEADUP_NOTIFICATION = 1005;
    private Toast multiSoundToast = null;
    private boolean mCurRCVBackOffState = false;
    private boolean mSarBackoffParam = false;
    private String mBTAddress = "";
    private String m1stBTAddr = "";
    private String m2ndBTAddr = "";
    ArrayList<String> mConnectedBTList = new ArrayList<>();
    private int mMediaLastAudibleIndex = 0;
    private int mLateBootCompleted_WiredDevice = -1;
    private boolean mIsCoverSafetyVolume = false;
    public boolean mSplitSound = false;
    public boolean mIsExceptionalDevice = false;
    private SemDvfsManager mSemDvfsCpuMin = null;
    private SemDvfsManager mCpuStateLock = null;
    private boolean mIsCPUBoostedForVoIP = false;
    public int mCPUBoostValueForVoIP = 0;
    public boolean mIsScreenOn = true;
    private boolean mIsSoundAliveExist = false;
    private boolean mIsEnableVolumeMonitor = false;
    private SemEmergencyManager mEmergencyManager = null;
    private CoverManager mCoverManager = null;
    private SemContextManager mSemContextManager = null;
    private int mDualSpkEnable = 1;
    private RampInPreset defaultPreset = new RampInPreset();
    private RampInPreset dualSpeakerPreset = new RampInPreset("dual_spk_on_off");
    private SensorThread mSensorThread = null;
    private boolean mFlatMotionChangedDuringRingtone = false;
    private String mAppMode = PriorityDump.PRIORITY_ARG_NORMAL;
    private boolean mIsFolderOpen = false;
    private boolean mIsVibrate = false;
    private MultiSoundManager mMultiSoundManager = new MultiSoundManager();
    private AppCategorizer mCategorizer = null;
    private int mHallSensorType = 1;
    private boolean mIsScreenOffMusicOn = false;
    DisplayManager mDisplayManager = null;
    private boolean mVolumeLimitOn = false;
    private int mVolumeLimitValue = 9;
    AlarmManager mAlarmManager = null;
    private SemHqmManager mSemHqmManager = null;
    private boolean mIsBigDataApp = false;
    private boolean mIsTalkBackEnabled = false;
    private boolean mIsPlaySilentModeOff = false;
    private boolean mSilentModeOff = false;
    private long mLastBluetoothA2dpDisconnectionTime = 0;
    private final int reconnectDelay = 1000;
    private boolean mIsSupportDisplayVolumeControl = false;
    private boolean mScreenSharingStateResumed = false;
    private boolean mIsDLNAEnabled = false;
    private float mSoundEffectVolume = -1.0f;
    private boolean mAdjustMediaVolumeOnly = false;
    private SparseIntArray mAppVolumeFromSoundAssistant = new SparseIntArray(5);
    private boolean mEnableSoundAssistant = false;
    protected int mIgnoreAudioFocusUid = -1;
    private int mMediaVolumeStepIndex = 10;
    private int mHeadsetOnlyStream = 0;
    private int mLRSwitching = 0;
    ArrayList<SoundEventReceiver> mEventReceivers = new ArrayList<>(1);
    private SemDesktopModeManager mDesktopModeManager = null;
    private SemDesktopModeManager.EventListener desktopModeListener = new SemDesktopModeManager.EventListener() { // from class: com.android.server.audio.AudioService.7
        public void onDesktopDockConnectionChanged(boolean connected) {
            Log.d("AudioService", "DEX connected : " + connected);
            AudioService.this.mDexConnectedState = connected;
            AudioSystem.setParameters("DEXconnected=" + AudioService.this.mDexConnectedState);
        }

        public void onDesktopModeChanged(boolean enabled) {
            boolean isAudioOutputToDisplay = DesktopModeSettings.getSettings(AudioService.this.mContentResolver, DesktopModeSettings.SETTINGS_KEY_AUDIO_OUTPUT_TO_DISPLAY, false);
            Log.d("AudioService", "DEX enabled : " + enabled + ", isAudioOutputToDisplay : " + isAudioOutputToDisplay);
            if (enabled && isAudioOutputToDisplay) {
                enabled = !enabled;
            }
            AudioService.this.mDexState = enabled;
            AudioSystem.setPolicyParameters("DEXstate=" + AudioService.this.mDexState);
        }
    };
    private int mMuteIntervalMs = 0;
    private final int STREAM_MULTI_SOUND = UrspService.MSG_SET_URSP_RULE_DONE;
    private int mVolumeControllerStream = -1;
    private int[] mVolumeSteps = null;
    private VolumeMap[] mVolumeMap = null;
    private boolean mIsWifiDisplayConnected = false;
    private boolean[] mGoodCatchOn = {false, false};
    private PreventOverheatState mPreventOverheatState = new PreventOverheatState(-1, false, 0);
    private List<String> mRestrictedPackageList = new ArrayList();
    private List<String> mAllowedPackageList = new ArrayList();
    private boolean mStartingSoundAssistant = false;
    private Object mSoundAssistantLock = new Object();
    private boolean mIsFactoryMode = false;
    SemContextListener mHallSensorListener = new SemContextListener() { // from class: com.android.server.audio.-$$Lambda$AudioService$oAZhhpieXU2la8a0aGOLwnX9848
        public final void onSemContextChanged(SemContextEvent semContextEvent) {
            AudioService.lambda$new$2(AudioService.this, semContextEvent);
        }
    };
    private Object mMultiSoundLock = new Object();
    private SemGoodCatchManager.OnStateChangeListener mGoodCatchStateListener = new SemGoodCatchManager.OnStateChangeListener() { // from class: com.android.server.audio.AudioService.10
        public void onStart(String mode) {
            Log.d("AudioService", "onStart " + mode);
            if (AudioService.GOODCATCH_MODE[0].equals(mode)) {
                AudioService.this.mGoodCatchOn[0] = true;
            } else if (AudioService.GOODCATCH_MODE[1].equals(mode)) {
                AudioService.this.mGoodCatchOn[1] = true;
            }
        }

        public void onStop(String mode) {
            Log.d("AudioService", "onStop " + mode);
            if (AudioService.GOODCATCH_MODE[0].equals(mode)) {
                AudioService.this.mGoodCatchOn[0] = false;
            } else if (AudioService.GOODCATCH_MODE[1].equals(mode)) {
                AudioService.this.mGoodCatchOn[1] = false;
            }
        }
    };

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public enum BypassReason {
        NO_BYPASS,
        DISPLAY_VOLUME_CONTROL,
        CANNOT_UNMUTE_SILENT_VIBRATE,
        ALL_SOUND_MUTE,
        TMS_CONNECT,
        CONSUME_ADJUST_SAME,
        MULTISOUND,
        MEDIA_VOLUME_STEP_ON,
        SKIP_WARNING_POPUP_VISIBLE,
        SKIP_VOLUME_PANEL_NOT_VISIBLE,
        VOLUME_LIMITER
    }

    static /* synthetic */ int access$14008(AudioService x0) {
        int i = x0.mAudioPolicyCounter;
        x0.mAudioPolicyCounter = i + 1;
        return i;
    }

    private boolean isPlatformVoice() {
        return this.mPlatformType == 1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isPlatformTelevision() {
        return this.mPlatformType == 2;
    }

    private boolean isPlatformAutomotive() {
        return this.mContext.getPackageManager().hasSystemFeature("android.hardware.type.automotive");
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class DeviceListSpec {
        String mDeviceAddress;
        String mDeviceName;
        int mDeviceType;

        public DeviceListSpec(int deviceType, String deviceName, String deviceAddress) {
            this.mDeviceType = deviceType;
            this.mDeviceName = deviceName;
            this.mDeviceAddress = deviceAddress;
        }

        public String toString() {
            return "[type:0x" + Integer.toHexString(this.mDeviceType) + " name:" + this.mDeviceName + " address:" + this.mDeviceAddress + HqmDBHelper.NAME_END;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String makeDeviceListKey(int device, String deviceAddress) {
        return "0x" + Integer.toHexString(device) + ":" + deviceAddress;
    }

    public static String makeAlsaAddressString(int card, int device) {
        return "card=" + card + ";device=" + device + ";";
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public static final class Lifecycle extends SystemService {
        private AudioService mService;

        public Lifecycle(Context context) {
            super(context);
            this.mService = new AudioService(context);
        }

        @Override // com.android.server.SystemService
        public void onStart() {
            publishBinderService(ServiceTuple.MEDIA_CAP_AUDIO, this.mService);
        }

        @Override // com.android.server.SystemService
        public void onBootPhase(int phase) {
            if (phase == 550) {
                this.mService.systemReady();
            } else if (phase == 1000) {
                this.mService.onBootCompleted();
            }
        }
    }

    public AudioService(Context context) {
        boolean z;
        int i;
        this.mSettingHelper = null;
        this.mPopupManager = null;
        this.mContext = context;
        this.mContentResolver = context.getContentResolver();
        this.mAppOps = (AppOpsManager) context.getSystemService("appops");
        this.mPlatformType = AudioSystem.getPlatformType(context);
        boolean z2 = Rune.SEC_AUDIO_DEFAULT_STREAM_NOTIFICATION;
        if (this.mPlatformType == 1) {
            z = false;
        } else {
            z = true;
        }
        Rune.SEC_AUDIO_DEFAULT_STREAM_NOTIFICATION = z2 | z;
        this.mIsSingleVolume = AudioSystem.isSingleVolume(context);
        this.mUserManagerInternal = (UserManagerInternal) LocalServices.getService(UserManagerInternal.class);
        this.mActivityManagerInternal = (ActivityManagerInternal) LocalServices.getService(ActivityManagerInternal.class);
        this.mPowerManager = (PowerManager) context.getSystemService("power");
        this.mAudioEventWakeLock = this.mPowerManager.newWakeLock(1, "handleAudioEvent");
        this.mVibrator = (Vibrator) context.getSystemService("vibrator");
        this.mHasVibrator = this.mVibrator == null ? false : this.mVibrator.hasVibrator();
        initializeVolumeSteps();
        sSoundEffectVolumeDb = context.getResources().getInteger(17694916);
        this.mForcedUseForComm = 0;
        createAudioSystemThread();
        AudioSystem.setErrorCallback(this.mAudioSystemCallback);
        boolean cameraSoundForced = readCameraSoundForced();
        this.mCameraSoundForced = new Boolean(cameraSoundForced).booleanValue();
        AudioHandler audioHandler = this.mAudioHandler;
        if (!cameraSoundForced) {
            i = 0;
        } else {
            i = 11;
        }
        sendMsg(audioHandler, 8, 2, 4, i, new String("AudioService ctor"), 0);
        this.mSafeMediaVolumeState = new Integer(Settings.Global.getInt(this.mContentResolver, "audio_safe_volume_state", 0));
        this.mSafeMediaVolumeIndex = this.mContext.getResources().getInteger(17694897) * 10;
        this.mUseFixedVolume = this.mContext.getResources().getBoolean(17957079);
        this.mSettingHelper = AudioSettingsHelper.getInstance(this.mContext);
        if (Rune.SEC_AUDIO_RECORDING_FAILED_POPUP) {
            this.mPopupManager = AudioPopupManager.getInstance(this.mContext);
        }
        this.STREAM_VOLUME_ALIAS_VOICE[5] = 5;
        updateStreamVolumeAlias(false, "AudioService");
        readPersistedSettings();
        readUserRestrictions();
        this.mSettingsObserver = new SettingsObserver();
        createStreamStates();
        this.mSafeUsbMediaVolumeIndex = getSafeUsbMediaVolumeIndex();
        this.mPlaybackMonitor = new PlaybackActivityMonitor(context, MAX_STREAM_VOLUME[4]);
        this.mMediaFocusControl = new MediaFocusControl(this.mContext, this.mPlaybackMonitor, this, this.mAudioHandler.getLooper());
        this.mRecordMonitor = new RecordingActivityMonitor(this.mContext);
        readAndSetLowRamDevice();
        this.mRingerAndZenModeMutedStreams = 0;
        setRingerModeInt(getRingerModeInternal(), false);
        IntentFilter intentFilter = new IntentFilter("android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED");
        intentFilter.addAction("android.bluetooth.headset.profile.action.ACTIVE_DEVICE_CHANGED");
        intentFilter.addAction("android.intent.action.DOCK_EVENT");
        intentFilter.addAction("android.intent.action.SCREEN_ON");
        intentFilter.addAction("android.intent.action.SCREEN_OFF");
        intentFilter.addAction(Constants.SWITCH_USER);
        intentFilter.addAction("android.intent.action.USER_BACKGROUND");
        intentFilter.addAction("android.intent.action.USER_FOREGROUND");
        intentFilter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
        intentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        intentFilter.addAction("android.intent.action.CONFIGURATION_CHANGED");
        this.mMonitorRotation = SystemProperties.getBoolean("ro.audio.monitorRotation", false);
        if (this.mMonitorRotation) {
            RotationHelper.init(this.mContext, this.mAudioHandler);
        }
        intentFilter.addAction("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION");
        intentFilter.addAction("android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION");
        context.registerReceiverAsUser(this.mReceiver, UserHandle.ALL, intentFilter, null, null);
        LocalServices.addService(AudioManagerInternal.class, new AudioServiceInternal());
        this.mUserManagerInternal.addUserRestrictionsListener(this.mUserRestrictionsListener);
        this.mRecordMonitor.initMonitor();
        setupCustomRoutine();
    }

    public void systemReady() {
        sendMsg(this.mAudioHandler, 21, 2, 0, 0, null, 0);
    }

    public void onSystemReady() {
        int uid;
        this.mSystemReady = true;
        int i = 0;
        sendMsg(this.mAudioHandler, 7, 2, 0, 0, null, 0);
        this.mScoConnectionState = -1;
        resetBluetoothSco();
        getBluetoothHeadset();
        Intent newIntent = new Intent("android.media.SCO_AUDIO_STATE_CHANGED");
        newIntent.putExtra("android.media.extra.SCO_AUDIO_STATE", 0);
        sendStickyBroadcastToAll(newIntent);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(this.mContext, this.mBluetoothProfileServiceListener, 2);
            adapter.getProfileProxy(this.mContext, this.mBluetoothProfileServiceListener, 21);
        }
        if (this.mContext.getPackageManager().hasSystemFeature("android.hardware.hdmi.cec")) {
            this.mHdmiManager = (HdmiControlManager) this.mContext.getSystemService(HdmiControlManager.class);
            synchronized (this.mHdmiManager) {
                this.mHdmiTvClient = this.mHdmiManager.getTvClient();
                if (this.mHdmiTvClient != null) {
                    this.mFixedVolumeDevices &= -2883587;
                }
                this.mHdmiPlaybackClient = this.mHdmiManager.getPlaybackClient();
                this.mHdmiCecSink = false;
            }
        }
        this.mNm = (NotificationManager) this.mContext.getSystemService("notification");
        sendMsg(this.mAudioHandler, 17, 0, 0, 0, "AudioService", SystemProperties.getBoolean("audio.safemedia.bypass", false) ? 0 : 30000);
        initA11yMonitoring();
        onIndicateSystemReady();
        this.mKeyguardManager = (KeyguardManager) this.mContext.getSystemService("keyguard");
        this.mMediaSessionService = (MediaSessionService.MediaSessionServiceInternal) LocalServices.getService(MediaSessionService.MediaSessionServiceInternal.class);
        TelephonyManager telephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        this.mIsFactoryMode = FactoryTest.isFactoryMode(this.mContext, telephonyManager);
        if (this.mIsFactoryMode) {
            this.mAdjustMediaVolumeOnly = false;
            Settings.System.putIntForUser(this.mContentResolver, "adjust_media_volume_only", 0, -2);
        }
        setWiredDeviceConnectionStateBeforeBoot();
        this.mCoverManager = new CoverManager(this.mContext);
        try {
            this.mCPUBoostValueForVoIP = Integer.parseInt("0");
        } catch (NumberFormatException e) {
            Log.e("AudioService", "NumberFormatException", e);
        }
        this.mSemHqmManager = (SemHqmManager) this.mContext.getSystemService("HqmManagerService");
        startLogging();
        registerGearVrStateListener();
        if (Rune.SEC_AUDIO_MULTI_SOUND) {
            ContentResolver cr = this.mContentResolver;
            boolean multiSoundState = Settings.Global.getInt(cr, MULTISOUND_STATE, 0) != 0;
            String multiSoundPackage = Settings.System.getString(cr, MULTISOUND_APP);
            int multiSoundDevice = Settings.System.getInt(cr, MULTISOUND_DEVICE_TYPE, -1);
            this.mCategorizer = new AppCategorizer(this.mContext, this.mSettingHelper);
            setMultiSoundOn(multiSoundState);
            initPackageList();
            if (multiSoundPackage != null && multiSoundDevice != -1 && (uid = getUidForPackage(multiSoundPackage)) != 0) {
                try {
                    setAppDevice(uid, multiSoundDevice == 1 ? 8 : 2);
                } catch (IllegalArgumentException e2) {
                    Log.e("AudioService", "set app device failed", e2);
                }
            }
            while (true) {
                int i2 = i;
                if (i2 >= this.mAllowedPackageList.size()) {
                    break;
                }
                int tempUid = getUidForPackage(this.mAllowedPackageList.get(i2));
                if (tempUid != 0) {
                    this.mCategorizer.putPackage(tempUid, this.mAllowedPackageList.get(i2));
                }
                i = i2 + 1;
            }
        }
        if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER) {
            this.mSensorThread = new SensorThread();
            this.mSensorThread.start();
            onInitDualSpeaker();
        }
    }

    void onIndicateSystemReady() {
        if (AudioSystem.systemReady() == 0) {
            return;
        }
        sendMsg(this.mAudioHandler, 26, 0, 0, 0, null, 1000);
    }

    public void onAudioServerDied() {
        int forDock;
        int forSys;
        int i;
        if (!this.mSystemReady || AudioSystem.checkAudioFlinger() != 0) {
            Log.e("AudioService", "Audioserver died.");
            sendMsg(this.mAudioHandler, 4, 1, 0, 0, null, 500);
            return;
        }
        Log.e("AudioService", "Audioserver started.");
        AudioSystem.setParameters("restarting=true");
        readAndSetLowRamDevice();
        if (isInCommunication()) {
            StringBuilder parameters = new StringBuilder();
            if (this.mMode == 2) {
                parameters.append("CallState=2;");
                if (this.mPhoneType != null) {
                    parameters.append("phone_type=");
                    parameters.append(this.mPhoneType);
                    parameters.append(";");
                }
            }
            if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER) {
                if ("GAMEVOIP".equals(this.mAppMode)) {
                    parameters.append("ProximitySensorClosed=0");
                } else {
                    boolean isClosed = this.mSensorThread.isClosed();
                    parameters.append("ProximitySensorClosed=");
                    int isClosed2 = isClosed ? 1 : 0;
                    parameters.append(isClosed2);
                    parameters.append(";");
                }
            }
            if (isBluetoothScoOn()) {
                parameters.append("bt_headset_nrec=");
                parameters.append(this.mBtNrec);
                parameters.append(";bt_samplerate=");
                parameters.append(this.mBtSampleRate);
                parameters.append(";");
            }
            Log.e("AudioService", "onAudioServerDied(): " + ((Object) parameters));
            AudioSystem.setParameters(parameters.toString());
        }
        AudioSystem.setPolicyParameters("DEXstate=" + this.mDexState);
        AudioSystem.setParameters("DEXconnected=" + this.mDexConnectedState);
        AudioSystem.setParameters("DEXPADconnected=" + this.mDexPadConnectedState);
        synchronized (this.mConnectedDevices) {
            forDock = 0;
            for (int i2 = 0; i2 < this.mConnectedDevices.size(); i2++) {
                DeviceListSpec spec = this.mConnectedDevices.valueAt(i2);
                AudioSystem.setDeviceConnectionState(spec.mDeviceType, 1, spec.mDeviceAddress, spec.mDeviceName);
            }
        }
        if (this.mLastConnectedDevice != null) {
            AudioSystem.setDeviceConnectionState(this.mLastConnectedDevice.mDeviceType, 2, this.mLastConnectedDevice.mDeviceAddress, this.mLastConnectedDevice.mDeviceName);
        } else {
            AudioSystem.setDeviceConnectionState((int) PermissionMonitor.APM_FLAG_APM_IS_OFF, 1, "mediaserver_died", "");
            AudioSystem.setDeviceConnectionState((int) PermissionMonitor.APM_FLAG_APM_IS_OFF, 0, "mediaserver_died", "");
        }
        if (AudioSystem.setPhoneState(this.mMode) == 0) {
            this.mModeLogger.log(new AudioEventLogger.StringEvent("onAudioServerDied causes setPhoneState(" + AudioSystem.modeToString(this.mMode) + ")"));
        }
        this.mForceUseLogger.log(new AudioServiceEvents.ForceUseEvent(0, this.mForcedUseForComm, "onAudioServerDied"));
        AudioSystem.setForceUse(0, this.mForcedUseForComm);
        this.mForceUseLogger.log(new AudioServiceEvents.ForceUseEvent(2, this.mForcedUseForComm, "onAudioServerDied"));
        AudioSystem.setForceUse(2, this.mForcedUseForComm);
        synchronized (this.mSettingsLock) {
            if (!this.mCameraSoundForced) {
                forSys = 0;
            } else {
                forSys = 11;
            }
        }
        this.mForceUseLogger.log(new AudioServiceEvents.ForceUseEvent(4, forSys, "onAudioServerDied"));
        AudioSystem.setForceUse(4, forSys);
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        int streamType = numStreamTypes - 1;
        while (true) {
            i = 10;
            if (streamType < 0) {
                break;
            }
            VolumeStreamState streamState = this.mStreamStates[streamType];
            AudioSystem.initStreamVolume(streamType, streamState.mIndexMin / 10, streamState.mIndexMax / 10);
            streamState.applyAllVolumes();
            streamType--;
        }
        updateMasterMono(this.mContentResolver);
        setRingerModeInt(getRingerModeInternal(), false);
        if (this.mMonitorRotation) {
            RotationHelper.updateOrientation();
        }
        synchronized (this.mBluetoothA2dpEnabledLock) {
            if (this.mBluetoothA2dpEnabled) {
                i = 0;
            }
            int forMed = i;
            this.mForceUseLogger.log(new AudioServiceEvents.ForceUseEvent(1, forMed, "onAudioServerDied"));
            AudioSystem.setForceUse(1, forMed);
        }
        synchronized (this.mSettingsLock) {
            if (this.mDockAudioMediaEnabled) {
                forDock = 8;
            }
            this.mForceUseLogger.log(new AudioServiceEvents.ForceUseEvent(3, forDock, "onAudioServerDied"));
            AudioSystem.setForceUse(3, forDock);
            sendEncodedSurroundMode(this.mContentResolver, "onAudioServerDied");
            sendEnabledSurroundFormats(this.mContentResolver, true);
        }
        if (this.mHdmiManager != null) {
            synchronized (this.mHdmiManager) {
                if (this.mHdmiTvClient != null) {
                    setHdmiSystemAudioSupported(this.mHdmiSystemAudioSupported);
                }
            }
        }
        synchronized (this.mAudioPolicies) {
            for (AudioPolicyProxy policy : this.mAudioPolicies.values()) {
                policy.connectMixes();
            }
        }
        onIndicateSystemReady();
        AudioSystem.setParameters("restarting=false");
        AudioSystem.setParameters("UHQ_BT=" + this.mUhqBtSampleRate);
        setSoundBalance();
        setNbQualityMode(this.mNbQualityMode);
        if (this.mIsBtOffloadEnabled > 0) {
            AudioSystem.setParameters("bt_offload_enable=" + this.mIsBtOffloadEnabled);
        }
        if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER) {
            setDualSpeaker(this.mDualSpkEnable);
        }
        setAllSoundMute();
        if (Rune.SEC_AUDIO_PERFORMANCE_MODE && this.mPerformanceMode == 2) {
            setGlobalUpscaler(this.mPerformanceUpScalerMode);
        } else {
            setGlobalUpscaler(this.mUpscalerEnabled);
        }
        Intent intent = new Intent("com.samsung.intent.action.MEDIA_SERVER_REBOOTED");
        sendBroadcastToUser(intent, UserHandle.CURRENT);
        Log.d("AudioService", "onAudioServerDied: MediaServer Restart Intent");
        if (Rune.SEC_AUDIO_MULTI_SOUND) {
            this.mMultiSoundManager.resetByAudioServerDied();
        }
        setMonoMode(this.mMonoMode);
        sendMsg(this.mAudioHandler, 29, 2, 1, 0, null, 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onDispatchAudioServerStateChange(boolean state) {
        synchronized (this.mAudioServerStateListeners) {
            for (AsdProxy asdp : this.mAudioServerStateListeners.values()) {
                try {
                    asdp.callback().dispatchAudioServerStateChange(state);
                } catch (RemoteException e) {
                    Log.w("AudioService", "Could not call dispatchAudioServerStateChange()", e);
                }
            }
        }
    }

    private void createAudioSystemThread() {
        this.mAudioSystemThread = new AudioSystemThread();
        this.mAudioSystemThread.start();
        waitForAudioHandlerCreation();
    }

    private void waitForAudioHandlerCreation() {
        synchronized (this) {
            while (this.mAudioHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Log.e("AudioService", "Interrupted while waiting on volume handler.");
                }
            }
        }
    }

    private void checkAllAliasStreamVolumes() {
        synchronized (this.mSettingsLock) {
            synchronized (VolumeStreamState.class) {
                int numStreamTypes = AudioSystem.getNumStreamTypes();
                for (int streamType = 0; streamType < numStreamTypes; streamType++) {
                    this.mStreamStates[streamType].setAllIndexes(this.mStreamStates[mStreamVolumeAlias[streamType]], "AudioService");
                    if (!this.mStreamStates[streamType].mIsMuted) {
                        this.mStreamStates[streamType].applyAllVolumes();
                    }
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void checkAllFixedVolumeDevices() {
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int streamType = 0; streamType < numStreamTypes; streamType++) {
            this.mStreamStates[streamType].checkFixedVolumeDevices();
        }
    }

    private void checkAllFixedVolumeDevices(int streamType) {
        this.mStreamStates[streamType].checkFixedVolumeDevices();
    }

    private void checkMuteAffectedStreams() {
        for (int i = 0; i < this.mStreamStates.length; i++) {
            VolumeStreamState vss = this.mStreamStates[i];
            if (vss.mIndexMin > 0 && vss.mStreamType != 0) {
                this.mMuteAffectedStreams &= ~(1 << vss.mStreamType);
            }
        }
    }

    private void createStreamStates() {
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        VolumeStreamState[] streams = new VolumeStreamState[numStreamTypes];
        this.mStreamStates = streams;
        for (int i = 0; i < numStreamTypes; i++) {
            streams[i] = new VolumeStreamState(Settings.System.VOLUME_SETTINGS_INT[mStreamVolumeAlias[i]], i);
        }
        checkAllFixedVolumeDevices();
        checkAllAliasStreamVolumes();
        checkMuteAffectedStreams();
        updateDefaultVolumes();
    }

    private void updateDefaultVolumes() {
        for (int stream = 0; stream < this.mStreamStates.length; stream++) {
            if (stream != mStreamVolumeAlias[stream]) {
                AudioSystem.DEFAULT_STREAM_VOLUME[stream] = rescaleIndex(AudioSystem.DEFAULT_STREAM_VOLUME[mStreamVolumeAlias[stream]], mStreamVolumeAlias[stream], stream);
            }
        }
    }

    private void dumpStreamStates(PrintWriter pw) {
        pw.println("\nStream volumes (device: index)");
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int i = 0; i < numStreamTypes; i++) {
            if (i != 12 && i != 11) {
                pw.println("- " + AudioSystem.STREAM_NAMES[i] + ":");
                this.mStreamStates[i].dump(pw);
                pw.println("");
            }
        }
        pw.print("\n- mute affected streams = 0x");
        pw.println(Integer.toHexString(this.mMuteAffectedStreams));
    }

    private void updateStreamVolumeAlias(boolean updateVolumes, String caller) {
        int dtmfStreamAlias = 3;
        int a11yStreamAlias = sIndependentA11yVolume ? 10 : 3;
        if (this.mIsSingleVolume) {
            mStreamVolumeAlias = this.STREAM_VOLUME_ALIAS_TELEVISION;
            dtmfStreamAlias = 3;
        } else if (this.mPlatformType == 1) {
            mStreamVolumeAlias = this.STREAM_VOLUME_ALIAS_VOICE;
            dtmfStreamAlias = 2;
        } else {
            mStreamVolumeAlias = this.STREAM_VOLUME_ALIAS_DEFAULT;
            if (Rune.SEC_AUDIO_DEFAULT_STREAM_NOTIFICATION) {
                dtmfStreamAlias = 5;
            }
        }
        int dtmfStreamAlias2 = dtmfStreamAlias;
        if (this.mIsSingleVolume) {
            this.mRingerModeAffectedStreams = 0;
        } else if (isInCommunication() || getMode() == 2) {
            dtmfStreamAlias2 = 0;
            this.mRingerModeAffectedStreams &= -257;
        } else {
            this.mRingerModeAffectedStreams |= 256;
        }
        int dtmfStreamAlias3 = dtmfStreamAlias2;
        mStreamVolumeAlias[8] = dtmfStreamAlias3;
        mStreamVolumeAlias[10] = a11yStreamAlias;
        if (updateVolumes && this.mStreamStates != null) {
            updateDefaultVolumes();
            synchronized (this.mSettingsLock) {
                synchronized (VolumeStreamState.class) {
                    this.mStreamStates[8].setAllIndexes(this.mStreamStates[dtmfStreamAlias3], caller);
                    this.mStreamStates[10].mVolumeIndexSettingName = Settings.System.VOLUME_SETTINGS_INT[a11yStreamAlias];
                    this.mStreamStates[10].setAllIndexes(this.mStreamStates[a11yStreamAlias], caller);
                    this.mStreamStates[10].refreshRange(mStreamVolumeAlias[10]);
                }
            }
            if (sIndependentA11yVolume) {
                this.mStreamStates[10].readSettings();
            }
            setRingerModeInt(getRingerModeInternal(), false);
            sendMsg(this.mAudioHandler, 10, 2, 0, 0, this.mStreamStates[8], 0);
            sendMsg(this.mAudioHandler, 10, 2, 0, 0, this.mStreamStates[10], 0);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void readDockAudioSettings(ContentResolver cr) {
        this.mDockAudioMediaEnabled = Settings.Global.getInt(cr, "dock_audio_media_enabled", 0) == 1;
        sendMsg(this.mAudioHandler, 8, 2, 3, this.mDockAudioMediaEnabled ? 8 : 0, new String("readDockAudioSettings"), 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateMasterMono(ContentResolver cr) {
        boolean masterMono = Settings.System.getIntForUser(cr, "master_mono", 0, -2) == 1;
        if (masterMono) {
            Log.d("AudioService", String.format("Master mono %b", Boolean.valueOf(masterMono)));
        }
        AudioSystem.setMasterMono(masterMono);
    }

    private void sendEncodedSurroundMode(ContentResolver cr, String eventSource) {
        int encodedSurroundMode = Settings.Global.getInt(cr, "encoded_surround_output", 0);
        sendEncodedSurroundMode(encodedSurroundMode, eventSource);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendEncodedSurroundMode(int encodedSurroundMode, String eventSource) {
        int forceSetting = 16;
        switch (encodedSurroundMode) {
            case 0:
                forceSetting = 0;
                break;
            case 1:
                forceSetting = 13;
                break;
            case 2:
                forceSetting = 14;
                break;
            case 3:
                forceSetting = 15;
                break;
            default:
                Log.e("AudioService", "updateSurroundSoundSettings: illegal value " + encodedSurroundMode);
                break;
        }
        if (forceSetting != 16) {
            sendMsg(this.mAudioHandler, 8, 2, 6, forceSetting, eventSource, 0);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendEnabledSurroundFormats(ContentResolver cr, boolean forceUpdate) {
        if (this.mEncodedSurroundMode != 3) {
            return;
        }
        String enabledSurroundFormats = Settings.Global.getString(cr, "encoded_surround_output_enabled_formats");
        if (enabledSurroundFormats == null) {
            enabledSurroundFormats = "";
        }
        if (!forceUpdate && TextUtils.equals(enabledSurroundFormats, this.mEnabledSurroundFormats)) {
            return;
        }
        this.mEnabledSurroundFormats = enabledSurroundFormats;
        String[] surroundFormats = TextUtils.split(enabledSurroundFormats, ",");
        ArrayList<Integer> formats = new ArrayList<>();
        for (String format : surroundFormats) {
            try {
                int audioFormat = Integer.valueOf(format).intValue();
                boolean isSurroundFormat = false;
                int[] iArr = AudioFormat.SURROUND_SOUND_ENCODING;
                int length = iArr.length;
                int i = 0;
                while (true) {
                    if (i >= length) {
                        break;
                    }
                    int sf = iArr[i];
                    if (sf != audioFormat) {
                        i++;
                    } else {
                        isSurroundFormat = true;
                        break;
                    }
                }
                if (isSurroundFormat && !formats.contains(Integer.valueOf(audioFormat))) {
                    formats.add(Integer.valueOf(audioFormat));
                }
            } catch (Exception e) {
                Log.e("AudioService", "Invalid enabled surround format:" + format);
            }
        }
        Settings.Global.putString(this.mContext.getContentResolver(), "encoded_surround_output_enabled_formats", TextUtils.join(",", formats));
        sendMsg(this.mAudioHandler, 30, 2, 0, 0, formats, 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onEnableSurroundFormats(ArrayList<Integer> enabledSurroundFormats) {
        int[] iArr;
        for (int surroundFormat : AudioFormat.SURROUND_SOUND_ENCODING) {
            boolean enabled = enabledSurroundFormats.contains(Integer.valueOf(surroundFormat));
            int ret = AudioSystem.setSurroundFormatEnabled(surroundFormat, enabled);
            Log.i("AudioService", "enable surround format:" + surroundFormat + " " + enabled + " " + ret);
        }
    }

    private void readPersistedSettings() {
        ContentResolver cr = this.mContentResolver;
        int ringerModeFromSettings = Settings.Global.getInt(cr, "mode_ringer", 2);
        int ringerMode = ringerModeFromSettings;
        if (!isValidRingerMode(ringerMode)) {
            ringerMode = 2;
        }
        if (ringerMode == 1 && !this.mHasVibrator) {
            ringerMode = 0;
        }
        if (ringerMode != ringerModeFromSettings) {
            Settings.Global.putInt(cr, "mode_ringer", ringerMode);
        }
        ringerMode = (this.mUseFixedVolume || this.mIsSingleVolume) ? 2 : 2;
        synchronized (this.mSettingsLock) {
            this.mRingerMode = ringerMode;
            if (this.mRingerModeExternal == -1) {
                this.mRingerModeExternal = this.mRingerMode;
            }
            this.mVibrateSetting = AudioSystem.getValueForVibrateSetting(0, 1, this.mHasVibrator ? 2 : 0);
            this.mVibrateSetting = AudioSystem.getValueForVibrateSetting(this.mVibrateSetting, 0, this.mHasVibrator ? 2 : 0);
            updateRingerAndZenModeAffectedStreams();
            readDockAudioSettings(cr);
            sendEncodedSurroundMode(cr, "readPersistedSettings");
            sendEnabledSurroundFormats(cr, true);
        }
        this.mMuteAffectedStreams = Settings.System.getIntForUser(cr, "mute_streams_affected", 47, -2);
        if (Rune.SEC_AUDIO_DEFAULT_STREAM_NOTIFICATION) {
            this.STREAM_VOLUME_ALIAS_DEFAULT[2] = 5;
            this.STREAM_VOLUME_ALIAS_DEFAULT[5] = 5;
        }
        updateMasterMono(cr);
        broadcastRingerMode("android.media.RINGER_MODE_CHANGED", this.mRingerModeExternal);
        broadcastRingerMode("android.media.INTERNAL_RINGER_MODE_CHANGED_ACTION", this.mRingerMode);
        broadcastVibrateSetting(0);
        broadcastVibrateSetting(1);
        this.mVolumeController.loadSettings(cr);
        readPersistedCustomSettings();
    }

    private void readUserRestrictions() {
        int currentUser = getCurrentUserId();
        boolean masterMute = this.mUserManagerInternal.getUserRestriction(currentUser, "disallow_unmute_device") || this.mUserManagerInternal.getUserRestriction(currentUser, "no_adjust_volume");
        if (this.mUseFixedVolume) {
            masterMute = false;
            AudioSystem.setMasterVolume(1.0f);
        }
        Log.d("AudioService", String.format("Master mute %s, user=%d", Boolean.valueOf(masterMute), Integer.valueOf(currentUser)));
        setSystemAudioMute(masterMute);
        AudioSystem.setMasterMute(masterMute);
        broadcastMasterMuteStatus(masterMute);
        boolean microphoneMute = this.mUserManagerInternal.getUserRestriction(currentUser, "no_unmute_microphone");
        Log.d("AudioService", String.format("Mic mute %s, user=%d", Boolean.valueOf(microphoneMute), Integer.valueOf(currentUser)));
        AudioSystem.muteMicrophone(microphoneMute);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int rescaleIndex(int index, int srcStream, int dstStream) {
        int rescaled = ((this.mStreamStates[dstStream].getMaxIndex() * index) + (this.mStreamStates[srcStream].getMaxIndex() / 2)) / this.mStreamStates[srcStream].getMaxIndex();
        if (rescaled < this.mStreamStates[dstStream].getMinIndex()) {
            return this.mStreamStates[dstStream].getMinIndex();
        }
        return rescaled;
    }

    public void adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags, String callingPackage, String caller) {
        IAudioPolicyCallback extVolCtlr;
        synchronized (this.mExtVolumeControllerLock) {
            extVolCtlr = this.mExtVolumeController;
        }
        if (extVolCtlr != null) {
            sendMsg(this.mAudioHandler, 28, 2, direction, 0, extVolCtlr, 0);
        } else {
            adjustSuggestedStreamVolume(direction, suggestedStreamType, flags, callingPackage, caller, Binder.getCallingUid());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags, String callingPackage, String caller, int uid) {
        boolean activeForReal;
        int streamType;
        int maybeActiveStreamType;
        Log.d("AudioService", "adjustSuggestedStreamVolume() stream=" + suggestedStreamType + ", flags=" + flags + ", caller=" + caller + ", volControlStream=" + this.mVolumeControlStream + ", userSelect=" + this.mUserSelectedVolumeControlStream);
        AudioEventLogger audioEventLogger = this.mVolumeLogger;
        StringBuilder sb = new StringBuilder(callingPackage);
        sb.append(SliceClientPermissions.SliceAuthority.DELIMITER);
        sb.append(caller);
        sb.append(" uid:");
        sb.append(uid);
        audioEventLogger.log(new AudioServiceEvents.VolumeEvent(0, suggestedStreamType, direction, flags, sb.toString()));
        synchronized (this.mForceControlStreamLock) {
            if (this.mUserSelectedVolumeControlStream) {
                int streamType2 = this.mVolumeControlStream;
                streamType = streamType2;
            } else {
                int maybeActiveStreamType2 = getActiveStreamType(suggestedStreamType);
                if (maybeActiveStreamType2 != 2 && maybeActiveStreamType2 != 5) {
                    activeForReal = AudioSystem.isStreamActive(maybeActiveStreamType2, 0);
                    if (!activeForReal && this.mVolumeControlStream != -1) {
                        streamType = this.mVolumeControlStream;
                    }
                    streamType = maybeActiveStreamType2;
                }
                activeForReal = wasStreamActiveRecently(maybeActiveStreamType2, 0);
                if (!activeForReal) {
                    streamType = this.mVolumeControlStream;
                }
                streamType = maybeActiveStreamType2;
            }
            maybeActiveStreamType = streamType;
        }
        isMuteAdjust(direction);
        ensureValidStreamType(maybeActiveStreamType);
        int i = mStreamVolumeAlias[maybeActiveStreamType];
        if (!isCoverOpen() && mStreamVolumeAlias[maybeActiveStreamType] == 2 && !isInCommunication()) {
            Log.i("AudioService", "Volume change disabled as cover is closed.");
        } else {
            adjustStreamVolume(maybeActiveStreamType, direction, flags, callingPackage, caller, uid);
        }
    }

    public void adjustStreamVolume(int streamType, int direction, int flags, String callingPackage) {
        if (streamType == 10 && !canChangeAccessibilityVolume()) {
            Log.w("AudioService", "Trying to call adjustStreamVolume() for a11y withoutCHANGE_ACCESSIBILITY_VOLUME / callingPackage=" + callingPackage);
            return;
        }
        this.mVolumeLogger.log(new AudioServiceEvents.VolumeEvent(1, streamType, direction, flags, callingPackage));
        adjustStreamVolume(streamType, direction, flags, callingPackage, callingPackage, Binder.getCallingUid());
    }

    /* JADX WARN: Removed duplicated region for block: B:180:0x033f  */
    /* JADX WARN: Removed duplicated region for block: B:191:0x0377  */
    /* JADX WARN: Removed duplicated region for block: B:209:0x03a0  */
    /* JADX WARN: Removed duplicated region for block: B:212:0x03a6  */
    /* JADX WARN: Removed duplicated region for block: B:215:0x03b1  */
    /* JADX WARN: Removed duplicated region for block: B:258:0x0415  */
    /* JADX WARN: Removed duplicated region for block: B:276:0x0382 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    protected void adjustStreamVolume(int r33, int r34, int r35, java.lang.String r36, java.lang.String r37, int r38) {
        /*
            Method dump skipped, instructions count: 1081
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.audio.AudioService.adjustStreamVolume(int, int, int, java.lang.String, java.lang.String, int):void");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onUnmuteStream(int stream, int flags) {
        VolumeStreamState streamState = this.mStreamStates[stream];
        streamState.mute(false);
        int device = getDeviceForStream(stream);
        int index = this.mStreamStates[stream].getIndex(device);
        sendVolumeUpdate(stream, index, index, flags);
    }

    private void setSystemAudioVolume(int oldVolume, int newVolume, int maxVolume, int flags) {
        if (this.mHdmiManager == null || this.mHdmiTvClient == null || oldVolume == newVolume || (flags & 256) != 0) {
            return;
        }
        synchronized (this.mHdmiManager) {
            if (this.mHdmiSystemAudioSupported) {
                synchronized (this.mHdmiTvClient) {
                    long token = Binder.clearCallingIdentity();
                    this.mHdmiTvClient.setSystemAudioVolume(oldVolume, newVolume, maxVolume);
                    Binder.restoreCallingIdentity(token);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class StreamVolumeCommand {
        public final int mDevice;
        public final int mFlags;
        public final int mIndex;
        public final int mStreamType;

        StreamVolumeCommand(int streamType, int index, int flags, int device) {
            this.mStreamType = streamType;
            this.mIndex = index;
            this.mFlags = flags;
            this.mDevice = device;
        }

        public String toString() {
            return "{streamType=" + this.mStreamType + ",index=" + this.mIndex + ",flags=" + this.mFlags + ",device=" + this.mDevice + '}';
        }
    }

    private int getNewRingerMode(int stream, int index, int flags) {
        if (this.mIsSingleVolume) {
            return getRingerModeExternal();
        }
        if ((flags & 2) != 0 || stream == getUiSoundsStreamType()) {
            if (index != 0) {
                return 2;
            }
            if (this.mHasVibrator) {
                return 1;
            }
            return this.mVolumePolicy.volumeDownToEnterSilent ? 0 : 2;
        }
        return getRingerModeExternal();
    }

    private boolean isAndroidNPlus(String caller) {
        try {
            ApplicationInfo applicationInfo = this.mContext.getPackageManager().getApplicationInfoAsUser(caller, 0, UserHandle.getUserId(Binder.getCallingUid()));
            if (applicationInfo.targetSdkVersion >= 24) {
                return true;
            }
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private boolean wouldToggleZenMode(int newMode) {
        if (getRingerModeExternal() != 0 || newMode == 0) {
            return getRingerModeExternal() != 0 && newMode == 0;
        }
        return true;
    }

    private void onSetStreamVolume(int streamType, int index, int flags, int device, String caller) {
        int stream = mStreamVolumeAlias[streamType];
        setStreamVolumeInt(stream, index, device, false, caller);
        if ((flags & 2) != 0 || stream == getUiSoundsStreamType()) {
            setRingerMode(getNewRingerMode(stream, index, flags), "AudioService.onSetStreamVolume", false);
        }
        if (streamType == 3 && index == 0) {
            index = this.mFineMediaVolume.getIndex(device);
        }
        if (index != 0) {
            this.mStreamStates[stream].mute(false);
        }
    }

    public void setStreamVolume(int streamType, int index, int flags, String callingPackage) {
        if (streamType == 10 && !canChangeAccessibilityVolume()) {
            Log.w("AudioService", "Trying to call setStreamVolume() for a11y without CHANGE_ACCESSIBILITY_VOLUME  callingPackage=" + callingPackage);
        } else if (streamType != 0 || index != 0 || this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") == 0) {
            this.mVolumeLogger.log(new AudioServiceEvents.VolumeEvent(2, streamType, index, flags, callingPackage));
            setStreamVolume(streamType, index, flags, callingPackage, callingPackage, Binder.getCallingUid());
        } else {
            Log.w("AudioService", "Trying to call setStreamVolume() for STREAM_VOICE_CALL and index 0 without MODIFY_PHONE_STATE  callingPackage=" + callingPackage);
        }
    }

    private boolean canChangeAccessibilityVolume() {
        synchronized (this.mAccessibilityServiceUidsLock) {
            if (this.mContext.checkCallingOrSelfPermission("android.permission.CHANGE_ACCESSIBILITY_VOLUME") == 0) {
                return true;
            }
            if (this.mAccessibilityServiceUids != null) {
                int callingUid = Binder.getCallingUid();
                for (int i = 0; i < this.mAccessibilityServiceUids.length; i++) {
                    if (this.mAccessibilityServiceUids[i] == callingUid) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setStreamVolume(int streamType, int index, int flags, String callingPackage, String caller, int uid) {
        setStreamVolume(streamType, index, flags, callingPackage, caller, uid, 0);
    }

    private boolean volumeAdjustmentAllowedByDnd(int streamTypeAlias, int flags) {
        switch (this.mNm.getZenMode()) {
            case 0:
                return true;
            case 1:
            case 2:
            case 3:
                return (isStreamMutedByRingerOrZenMode(streamTypeAlias) && streamTypeAlias != getUiSoundsStreamType() && (flags & 2) == 0) ? false : true;
            default:
                return true;
        }
    }

    public void forceVolumeControlStream(int streamType, IBinder cb) {
        Log.d("AudioService", String.format("forceVolumeControlStream(%d)", Integer.valueOf(streamType)));
        synchronized (this.mForceControlStreamLock) {
            if (Rune.SEC_AUDIO_MULTI_SOUND && isMultiSoundOn()) {
                this.mVolumeControllerStream = streamType;
                if (streamType == 10003) {
                    streamType = 3;
                }
            }
            if (this.mVolumeControlStream != -1 && streamType != -1) {
                this.mUserSelectedVolumeControlStream = true;
            }
            this.mVolumeControlStream = streamType;
            if (this.mVolumeControlStream == -1) {
                if (this.mForceControlStreamClient != null) {
                    this.mForceControlStreamClient.release();
                    this.mForceControlStreamClient = null;
                }
                this.mUserSelectedVolumeControlStream = false;
            } else if (this.mForceControlStreamClient == null) {
                this.mForceControlStreamClient = new ForceControlStreamClient(cb);
            } else if (this.mForceControlStreamClient.getBinder() == cb) {
                Log.d("AudioService", "forceVolumeControlStream cb:" + cb + " is already linked.");
            } else {
                this.mForceControlStreamClient.release();
                this.mForceControlStreamClient = new ForceControlStreamClient(cb);
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    private class ForceControlStreamClient implements IBinder.DeathRecipient {
        private IBinder mCb;

        ForceControlStreamClient(IBinder cb) {
            if (cb != null) {
                try {
                    cb.linkToDeath(this, 0);
                } catch (RemoteException e) {
                    Log.w("AudioService", "ForceControlStreamClient() could not link to " + cb + " binder death");
                    cb = null;
                }
            }
            this.mCb = cb;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            synchronized (AudioService.this.mForceControlStreamLock) {
                Log.w("AudioService", "SCO client died");
                if (AudioService.this.mForceControlStreamClient == this) {
                    AudioService.this.mForceControlStreamClient = null;
                    AudioService.this.mVolumeControlStream = -1;
                    AudioService.this.mUserSelectedVolumeControlStream = false;
                } else {
                    Log.w("AudioService", "unregistered control stream client died");
                }
            }
        }

        public void release() {
            if (this.mCb != null) {
                this.mCb.unlinkToDeath(this, 0);
                this.mCb = null;
            }
        }

        public IBinder getBinder() {
            return this.mCb;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendBroadcastToAll(Intent intent) {
        intent.addFlags(67108864);
        intent.addFlags(268435456);
        long ident = Binder.clearCallingIdentity();
        try {
            this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendStickyBroadcastToAll(Intent intent) {
        intent.addFlags(268435456);
        long ident = Binder.clearCallingIdentity();
        try {
            this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private int getCurrentUserId() {
        long ident = Binder.clearCallingIdentity();
        try {
            UserInfo currentUser = ActivityManager.getService().getCurrentUser();
            int i = currentUser.id;
            Binder.restoreCallingIdentity(ident);
            return i;
        } catch (RemoteException e) {
            Binder.restoreCallingIdentity(ident);
            return 0;
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(ident);
            throw th;
        }
    }

    protected void sendVolumeUpdate(int streamType, int oldIndex, int index, int flags) {
        final int streamType2 = mStreamVolumeAlias[streamType];
        int flags2 = updateFlagsForSamsungVolume(streamType2, flags, index);
        int index2 = getProperIndexForFineVolume(streamType2, this.mStreamStates[streamType2].mObservedDevices, index);
        if (streamType2 == 3) {
            flags2 = updateFlagsForSystemAudio(flags2);
        }
        this.mVolumeController.postVolumeChanged(streamType2, flags2);
        if ((flags2 & 32) == 0) {
            final int oldIndex2 = (oldIndex + 5) / 10;
            final int index3 = (index2 + 5) / 10;
            final int flag_value = flags2;
            this.mAudioHandler.post(new Runnable() { // from class: com.android.server.audio.-$$Lambda$AudioService$Pp4tLtMJcwTExH4a9m4PtrcKIK8
                @Override // java.lang.Runnable
                public final void run() {
                    AudioService.lambda$sendVolumeUpdate$0(AudioService.this, streamType2, index3, oldIndex2, flag_value);
                }
            });
        }
    }

    public static /* synthetic */ void lambda$sendVolumeUpdate$0(AudioService audioService, int stream_type, int idx, int oldIdx, int flag_value) {
        Intent intent = new Intent("android.media.VOLUME_CHANGED_ACTION");
        intent.putExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", stream_type);
        if (audioService.isStreamMute(stream_type)) {
            intent.putExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", 0);
        } else {
            intent.putExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", idx);
        }
        intent.putExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", oldIdx);
        intent.putExtra("android.media.EXTRA_VOLUME_SHOW_UI", (flag_value & 1) != 0);
        if ((4194304 & flag_value) != 0) {
            Log.i("AudioService", "Do not play volume index information through Voice Assistant");
            intent.putExtra("voice_assistant_bypass", true);
        }
        audioService.sendBroadcastToAll(intent);
    }

    private int updateFlagsForSystemAudio(int flags) {
        if (this.mHdmiTvClient != null) {
            synchronized (this.mHdmiTvClient) {
                if (this.mHdmiSystemAudioSupported && (flags & 256) == 0) {
                    flags &= -2;
                }
            }
        }
        return flags;
    }

    private void sendMasterMuteUpdate(boolean muted, int flags) {
        this.mVolumeController.postMasterMuteChanged(updateFlagsForSystemAudio(flags));
        broadcastMasterMuteStatus(muted);
    }

    private void broadcastMasterMuteStatus(boolean muted) {
        Intent intent = new Intent("android.media.MASTER_MUTE_CHANGED_ACTION");
        intent.putExtra("android.media.EXTRA_MASTER_VOLUME_MUTED", muted);
        intent.addFlags(603979776);
        sendStickyBroadcastToAll(intent);
    }

    private void setStreamVolumeInt(int streamType, int index, int device, boolean force, String caller) {
        VolumeStreamState streamState = this.mStreamStates[streamType];
        if (streamState.setIndex(index, device, caller) || force) {
            sendMsg(this.mAudioHandler, 0, 2, device, 0, streamState, 0);
        }
    }

    private void setSystemAudioMute(boolean state) {
        if (this.mHdmiManager == null || this.mHdmiTvClient == null) {
            return;
        }
        synchronized (this.mHdmiManager) {
            if (this.mHdmiSystemAudioSupported) {
                synchronized (this.mHdmiTvClient) {
                    long token = Binder.clearCallingIdentity();
                    this.mHdmiTvClient.setSystemAudioMute(state);
                    Binder.restoreCallingIdentity(token);
                }
            }
        }
    }

    public boolean isStreamMute(int streamType) {
        boolean z;
        if (streamType == Integer.MIN_VALUE) {
            streamType = getActiveStreamType(streamType);
        }
        synchronized (VolumeStreamState.class) {
            ensureValidStreamType(streamType);
            z = this.mStreamStates[streamType].mIsMuted;
        }
        return z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class RmtSbmxFullVolDeathHandler implements IBinder.DeathRecipient {
        private IBinder mICallback;

        RmtSbmxFullVolDeathHandler(IBinder cb) {
            this.mICallback = cb;
            try {
                cb.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Log.e("AudioService", "can't link to death", e);
            }
        }

        boolean isHandlerFor(IBinder cb) {
            return this.mICallback.equals(cb);
        }

        void forget() {
            try {
                this.mICallback.unlinkToDeath(this, 0);
            } catch (NoSuchElementException e) {
                Log.e("AudioService", "error unlinking to death", e);
            }
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            Log.w("AudioService", "Recorder with remote submix at full volume died " + this.mICallback);
            AudioService.this.forceRemoteSubmixFullVolume(false, this.mICallback);
        }
    }

    private boolean discardRmtSbmxFullVolDeathHandlerFor(IBinder cb) {
        Iterator<RmtSbmxFullVolDeathHandler> it = this.mRmtSbmxFullVolDeathHandlers.iterator();
        while (it.hasNext()) {
            RmtSbmxFullVolDeathHandler handler = it.next();
            if (handler.isHandlerFor(cb)) {
                handler.forget();
                this.mRmtSbmxFullVolDeathHandlers.remove(handler);
                return true;
            }
        }
        return false;
    }

    private boolean hasRmtSbmxFullVolDeathHandlerFor(IBinder cb) {
        Iterator<RmtSbmxFullVolDeathHandler> it = this.mRmtSbmxFullVolDeathHandlers.iterator();
        while (it.hasNext()) {
            if (it.next().isHandlerFor(cb)) {
                return true;
            }
        }
        return false;
    }

    public void forceRemoteSubmixFullVolume(boolean startForcing, IBinder cb) {
        if (cb == null) {
            return;
        }
        if (this.mContext.checkCallingOrSelfPermission("android.permission.CAPTURE_AUDIO_OUTPUT") != 0) {
            Log.w("AudioService", "Trying to call forceRemoteSubmixFullVolume() without CAPTURE_AUDIO_OUTPUT");
            return;
        }
        synchronized (this.mRmtSbmxFullVolDeathHandlers) {
            boolean applyRequired = false;
            try {
                if (startForcing) {
                    if (!hasRmtSbmxFullVolDeathHandlerFor(cb)) {
                        this.mRmtSbmxFullVolDeathHandlers.add(new RmtSbmxFullVolDeathHandler(cb));
                        if (this.mRmtSbmxFullVolRefCount == 0) {
                            this.mFullVolumeDevices |= 32768;
                            this.mFixedVolumeDevices |= 32768;
                            applyRequired = true;
                        }
                        this.mRmtSbmxFullVolRefCount++;
                    }
                } else if (discardRmtSbmxFullVolDeathHandlerFor(cb) && this.mRmtSbmxFullVolRefCount > 0) {
                    this.mRmtSbmxFullVolRefCount--;
                    if (this.mRmtSbmxFullVolRefCount == 0) {
                        this.mFullVolumeDevices &= -32769;
                        this.mFixedVolumeDevices &= -32769;
                        applyRequired = true;
                    }
                }
                if (applyRequired) {
                    checkAllFixedVolumeDevices(3);
                    this.mStreamStates[3].applyAllVolumes();
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    private void setMasterMuteInternal(boolean mute, int flags, String callingPackage, int uid, int userId) {
        if (uid == 1000) {
            uid = UserHandle.getUid(userId, UserHandle.getAppId(uid));
        }
        if (!mute && this.mAppOps.noteOp(33, uid, callingPackage) != 0) {
            return;
        }
        if (userId != UserHandle.getCallingUserId() && this.mContext.checkCallingOrSelfPermission("android.permission.INTERACT_ACROSS_USERS_FULL") != 0) {
            return;
        }
        this.mVolumeLogger.log(new AudioServiceEvents.VolumeEvent(3, 0, mute ? 1 : 0, flags, callingPackage));
        setMasterMuteInternalNoCallerCheck(mute, flags, userId);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setMasterMuteInternalNoCallerCheck(boolean mute, int flags, int userId) {
        Log.d("AudioService", String.format("Master mute %s, %d, user=%d", Boolean.valueOf(mute), Integer.valueOf(flags), Integer.valueOf(userId)));
        if ((isPlatformAutomotive() || !this.mUseFixedVolume) && getCurrentUserId() == userId && mute != AudioSystem.getMasterMute()) {
            setSystemAudioMute(mute);
            AudioSystem.setMasterMute(mute);
            sendMasterMuteUpdate(mute, flags);
            Intent intent = new Intent("android.media.MASTER_MUTE_CHANGED_ACTION");
            intent.putExtra("android.media.EXTRA_MASTER_VOLUME_MUTED", mute);
            sendBroadcastToAll(intent);
        }
    }

    public boolean isMasterMute() {
        return AudioSystem.getMasterMute();
    }

    public void setMasterMute(boolean mute, int flags, String callingPackage, int userId) {
        setMasterMuteInternal(mute, flags, callingPackage, Binder.getCallingUid(), userId);
    }

    public int getStreamVolume(int streamType) {
        return getStreamVolume(streamType, 0);
    }

    public int getStreamMaxVolume(int streamType) {
        ensureValidStreamType(streamType);
        return (this.mStreamStates[streamType].getMaxIndex() + 5) / 10;
    }

    public int getStreamMinVolume(int streamType) {
        ensureValidStreamType(streamType);
        return (this.mStreamStates[streamType].getMinIndex() + 5) / 10;
    }

    public int getLastAudibleStreamVolume(int streamType) {
        ensureValidStreamType(streamType);
        int device = getDeviceForStream(streamType);
        return (this.mStreamStates[streamType].getIndex(device) + 5) / 10;
    }

    public int getUiSoundsStreamType() {
        CustomDeviceManagerProxy customDeviceManager;
        if (!isInCommunication() && (customDeviceManager = CustomDeviceManagerProxy.getInstance()) != null) {
            int volumeControlStream = customDeviceManager.getVolumeControlStream();
            if (volumeControlStream == 1) {
                return 1;
            }
            if (volumeControlStream == 4) {
                return 5;
            }
        }
        if (Rune.SEC_AUDIO_DEFAULT_STREAM_NOTIFICATION) {
            return 5;
        }
        if (isPlatformVoice()) {
            return 2;
        }
        return isPlatformTelevision() ? 3 : 3;
    }

    public void setMicrophoneMute(boolean on, String callingPackage, int userId) {
        int uid = Binder.getCallingUid();
        if (uid == 1000) {
            uid = UserHandle.getUid(userId, UserHandle.getAppId(uid));
        }
        if ((!on && this.mAppOps.noteOp(44, uid, callingPackage) != 0) || !checkAudioSettingsPermission("setMicrophoneMute()")) {
            return;
        }
        if (userId != UserHandle.getCallingUserId() && this.mContext.checkCallingOrSelfPermission("android.permission.INTERACT_ACROSS_USERS_FULL") != 0) {
            return;
        }
        setMicrophoneMuteNoCallerCheck(on, userId);
        int pid = Binder.getCallingPid();
        this.mMicrophoneLogger.log(new AudioServiceEvents.MicrophoneEvent(callingPackage, pid, on));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setMicrophoneMuteNoCallerCheck(boolean on, int userId) {
        Log.d("AudioService", String.format("Mic mute %s, user=%d", Boolean.valueOf(on), Integer.valueOf(userId)));
        if (getCurrentUserId() == userId) {
            boolean currentMute = AudioSystem.isMicrophoneMuted();
            long identity = Binder.clearCallingIdentity();
            AudioSystem.muteMicrophone(on);
            Binder.restoreCallingIdentity(identity);
            if (on != currentMute) {
                this.mContext.sendBroadcast(new Intent("android.media.action.MICROPHONE_MUTE_CHANGED").setFlags(PermissionMonitor.APM_FLAG_APM_IS_OFF));
            }
        }
    }

    public int getRingerModeExternal() {
        int i;
        synchronized (this.mSettingsLock) {
            i = this.mRingerModeExternal;
        }
        return i;
    }

    public int getRingerModeInternal() {
        int i;
        synchronized (this.mSettingsLock) {
            i = this.mRingerMode;
        }
        return i;
    }

    private void ensureValidRingerMode(int ringerMode) {
        if (!isValidRingerMode(ringerMode)) {
            throw new IllegalArgumentException("Bad ringer mode " + ringerMode);
        }
    }

    public boolean isValidRingerMode(int ringerMode) {
        return ringerMode >= 0 && ringerMode <= 2;
    }

    public void setRingerModeExternal(int ringerMode, String caller) {
        if (isAndroidNPlus(caller) && wouldToggleZenMode(ringerMode) && !this.mNm.isNotificationPolicyAccessGrantedForPackage(caller)) {
            throw new SecurityException("Not allowed to change Do Not Disturb state");
        }
        setRingerMode(ringerMode, caller, true);
    }

    public void setRingerModeInternal(int ringerMode, String caller) {
        enforceVolumeController("setRingerModeInternal");
        setRingerMode(ringerMode, caller, false);
    }

    public void silenceRingerModeInternal(String reason) {
        VibrationEffect effect = null;
        int ringerMode = 0;
        int toastText = 0;
        int silenceRingerSetting = 0;
        if (this.mContext.getResources().getBoolean(17957089)) {
            silenceRingerSetting = Settings.Secure.getIntForUser(this.mContentResolver, "volume_hush_gesture", 0, -2);
        }
        switch (silenceRingerSetting) {
            case 1:
                effect = VibrationEffect.get(5);
                ringerMode = 1;
                toastText = 17042174;
                break;
            case 2:
                effect = VibrationEffect.get(1);
                ringerMode = 0;
                toastText = 17042173;
                break;
        }
        maybeVibrate(effect);
        setRingerModeInternal(ringerMode, reason);
        Toast.makeText(this.mContext, toastText, 0).show();
    }

    private boolean maybeVibrate(VibrationEffect effect) {
        if (this.mHasVibrator) {
            boolean hapticsDisabled = Settings.System.getIntForUser(this.mContext.getContentResolver(), "haptic_feedback_enabled", 0, -2) == 0;
            if (hapticsDisabled || effect == null) {
                return false;
            }
            this.mVibrator.vibrate(Binder.getCallingUid(), this.mContext.getOpPackageName(), effect, VIBRATION_ATTRIBUTES);
            return true;
        }
        return false;
    }

    private void setRingerMode(int ringerMode, String caller, boolean external) {
        if (this.mUseFixedVolume || this.mIsSingleVolume) {
            return;
        }
        if (caller == null || caller.length() == 0) {
            throw new IllegalArgumentException("Bad caller: " + caller);
        }
        ensureValidRingerMode(ringerMode);
        this.mVolumeLogger.log(new AudioServiceEvents.VolumeEvent(4, 0, ringerMode, external ? 1 : 0, caller));
        int i = ringerMode;
        if (i == 1 && !this.mHasVibrator) {
            i = 0;
        }
        int ringerMode2 = i;
        if (this.mMuteIntervalMs != 0 && ringerMode2 != 0) {
            this.mMuteIntervalMs = 0;
        }
        if (ringerMode2 != getRingerModeInternal()) {
            sendBroadcastToSoundEventReceiver(1, ringerMode2, caller);
        }
        long identity = Binder.clearCallingIdentity();
        try {
            synchronized (this.mSettingsLock) {
                int ringerModeInternal = getRingerModeInternal();
                int ringerModeExternal = getRingerModeExternal();
                if (external) {
                    setRingerModeExt(ringerMode2);
                    if (this.mRingerModeDelegate != null) {
                        ringerMode2 = this.mRingerModeDelegate.onSetRingerModeExternal(ringerModeExternal, ringerMode2, caller, ringerModeInternal, this.mVolumePolicy);
                    }
                    if (ringerMode2 != ringerModeInternal) {
                        setRingerModeInt(ringerMode2, true);
                        handleSetRingerMode(ringerMode2, caller);
                    }
                } else {
                    if (ringerMode2 != ringerModeInternal) {
                        setRingerModeInt(ringerMode2, true);
                        handleSetRingerMode(ringerMode2, caller);
                    }
                    if (this.mRingerModeDelegate != null) {
                        ringerMode2 = this.mRingerModeDelegate.onSetRingerModeInternal(ringerModeInternal, ringerMode2, caller, ringerModeExternal, this.mVolumePolicy);
                    }
                    setRingerModeExt(ringerMode2);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void setRingerModeExt(int ringerMode) {
        synchronized (this.mSettingsLock) {
            if (ringerMode == this.mRingerModeExternal) {
                return;
            }
            this.mRingerModeExternal = ringerMode;
            broadcastRingerMode("android.media.RINGER_MODE_CHANGED", ringerMode);
        }
    }

    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:82:? -> B:73:0x0111). Please submit an issue!!! */
    @GuardedBy("mSettingsLock")
    private void muteRingerModeStreams() {
        int numStreamTypes;
        int ringerMode;
        boolean z;
        int ringerMode2;
        int numStreamTypes2 = AudioSystem.getNumStreamTypes();
        if (this.mNm == null) {
            this.mNm = (NotificationManager) this.mContext.getSystemService("notification");
        }
        int ringerMode3 = this.mRingerMode;
        boolean z2 = true;
        boolean ringerModeMute = ringerMode3 == 1 || ringerMode3 == 0;
        boolean shouldRingSco = ringerMode3 == 1 && isBluetoothScoOn();
        String eventSource = "muteRingerModeStreams() from u/pid:" + Binder.getCallingUid() + SliceClientPermissions.SliceAuthority.DELIMITER + Binder.getCallingPid();
        int i = 3;
        sendMsg(this.mAudioHandler, 8, 2, 7, shouldRingSco ? 3 : 0, eventSource, 0);
        int streamType = numStreamTypes2 - 1;
        while (streamType >= 0) {
            boolean isMuted = isStreamMutedByRingerOrZenMode(streamType);
            boolean muteAllowedBySco = (shouldRingSco && streamType == 2) ? false : z2;
            boolean shouldZenMute = shouldZenMuteStream(streamType);
            boolean shouldMute = (shouldZenMute || (ringerModeMute && isStreamAffectedByRingerMode(streamType) && muteAllowedBySco)) ? z2 : false;
            if (isMuted != shouldMute) {
                if (shouldMute) {
                    numStreamTypes = numStreamTypes2;
                    ringerMode = ringerMode3;
                    z = z2;
                    this.mStreamStates[streamType].mute(z);
                    this.mRingerAndZenModeMutedStreams |= (z ? 1 : 0) << streamType;
                } else if (mStreamVolumeAlias[streamType] != i || this.mZenMode != 2) {
                    int checkStream = Rune.SEC_AUDIO_DEFAULT_STREAM_NOTIFICATION ? 5 : 2;
                    if (mStreamVolumeAlias[streamType] == checkStream) {
                        synchronized (VolumeStreamState.class) {
                            try {
                                VolumeStreamState vss = this.mStreamStates[streamType];
                                int i2 = 0;
                                while (true) {
                                    int i3 = i2;
                                    if (i3 >= vss.mIndexMap.size()) {
                                        break;
                                    }
                                    int device = vss.mIndexMap.keyAt(i3);
                                    int numStreamTypes3 = numStreamTypes2;
                                    try {
                                        int value = vss.mIndexMap.valueAt(i3);
                                        if (value == 0) {
                                            ringerMode2 = ringerMode3;
                                            try {
                                                vss.setIndex(10, device, "AudioService");
                                            } catch (Throwable th) {
                                                th = th;
                                                throw th;
                                            }
                                        } else {
                                            ringerMode2 = ringerMode3;
                                        }
                                        i2 = i3 + 1;
                                        numStreamTypes2 = numStreamTypes3;
                                        ringerMode3 = ringerMode2;
                                    } catch (Throwable th2) {
                                        th = th2;
                                        throw th;
                                    }
                                }
                                numStreamTypes = numStreamTypes2;
                                ringerMode = ringerMode3;
                                int device2 = getDeviceForStream(streamType);
                                sendMsg(this.mAudioHandler, 1, 2, device2, 0, this.mStreamStates[streamType], 500);
                            } catch (Throwable th3) {
                                th = th3;
                            }
                        }
                    } else {
                        numStreamTypes = numStreamTypes2;
                        ringerMode = ringerMode3;
                    }
                    this.mStreamStates[streamType].mute(false);
                    z = true;
                    this.mRingerAndZenModeMutedStreams &= ~(1 << streamType);
                }
                streamType--;
                z2 = z;
                numStreamTypes2 = numStreamTypes;
                ringerMode3 = ringerMode;
                i = 3;
            }
            numStreamTypes = numStreamTypes2;
            ringerMode = ringerMode3;
            z = z2;
            streamType--;
            z2 = z;
            numStreamTypes2 = numStreamTypes;
            ringerMode3 = ringerMode;
            i = 3;
        }
    }

    private boolean isAlarm(int streamType) {
        return streamType == 4;
    }

    private boolean isNotificationOrRinger(int streamType) {
        return streamType == 5 || streamType == 2;
    }

    private boolean isMedia(int streamType) {
        return streamType == 16 || streamType == 3;
    }

    private boolean isSystem(int streamType) {
        return streamType == 1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setRingerModeInt(int ringerMode, boolean persist) {
        boolean change;
        synchronized (this.mSettingsLock) {
            change = this.mRingerMode != ringerMode;
            if (change) {
                this.mPrevRingerMode = this.mRingerMode;
            }
            this.mRingerMode = ringerMode;
            if (this.mIsFactoryMode) {
                Log.e("AudioService", "setRingerModeInt ringerMode=" + ringerMode + " " + Debug.getCallers(7));
            }
            EventLogTags.writeRingerModeChanged(ringerMode, persist ? 1 : 0);
            muteRingerModeStreams();
        }
        boolean change2 = change;
        if (persist) {
            sendMsg(this.mAudioHandler, 3, 0, 0, 0, null, 500);
        }
        if (change2) {
            broadcastRingerMode("android.media.INTERNAL_RINGER_MODE_CHANGED_ACTION", ringerMode);
        }
    }

    public boolean shouldVibrate(int vibrateType) {
        if (this.mHasVibrator) {
            switch (getVibrateSetting(vibrateType)) {
                case 0:
                    return false;
                case 1:
                    return getRingerModeExternal() != 0;
                case 2:
                    return getRingerModeExternal() == 1;
                default:
                    return false;
            }
        }
        return false;
    }

    public int getVibrateSetting(int vibrateType) {
        if (this.mHasVibrator) {
            return (this.mVibrateSetting >> (vibrateType * 2)) & 3;
        }
        return 0;
    }

    public void setVibrateSetting(int vibrateType, int vibrateSetting) {
        if (this.mHasVibrator) {
            this.mVibrateSetting = AudioSystem.getValueForVibrateSetting(this.mVibrateSetting, vibrateType, vibrateSetting);
            broadcastVibrateSetting(vibrateType);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class SetModeDeathHandler implements IBinder.DeathRecipient {
        private IBinder mCb;
        private int mMode = 0;
        private String mPackageType = PriorityDump.PRIORITY_ARG_NORMAL;
        private int mPid;

        SetModeDeathHandler(IBinder cb, int pid) {
            this.mCb = cb;
            this.mPid = pid;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            int oldModeOwnerPid = 0;
            int newModeOwnerPid = 0;
            synchronized (AudioService.this.mSetModeDeathHandlers) {
                Log.w("AudioService", "setMode() client died");
                if (!AudioService.this.mSetModeDeathHandlers.isEmpty()) {
                    oldModeOwnerPid = ((SetModeDeathHandler) AudioService.this.mSetModeDeathHandlers.get(0)).getPid();
                }
                int index = AudioService.this.mSetModeDeathHandlers.indexOf(this);
                if (index >= 0) {
                    newModeOwnerPid = AudioService.this.setModeInt(0, this.mCb, this.mPid, "AudioService");
                } else {
                    Log.w("AudioService", "unregistered setMode() client died");
                }
            }
            if (newModeOwnerPid != oldModeOwnerPid && newModeOwnerPid != 0) {
                long ident = Binder.clearCallingIdentity();
                AudioService.this.disconnectBluetoothSco(newModeOwnerPid);
                Binder.restoreCallingIdentity(ident);
            }
        }

        public int getPid() {
            return this.mPid;
        }

        public void setMode(int mode) {
            this.mMode = mode;
        }

        public int getMode() {
            return this.mMode;
        }

        public IBinder getBinder() {
            return this.mCb;
        }

        public void setPackageType(String caller) {
            try {
                if (SemGameManager.isAvailable() && SemGameManager.isGamePackage(caller)) {
                    this.mPackageType = "GAMEVOIP";
                }
            } catch (IllegalStateException e) {
                Log.e("AudioService", "setPackageType", e);
            }
        }

        public String getPackageType() {
            return this.mPackageType;
        }
    }

    public void setMode(int mode, IBinder cb, String callingPackage) {
        int newModeOwnerPid;
        if (!checkAudioSettingsPermission("setMode()")) {
            return;
        }
        if (mode == 2 && this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") != 0) {
            Log.w("AudioService", "MODIFY_PHONE_STATE Permission Denial: setMode(MODE_IN_CALL) from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
        } else if (mode < -1 || mode >= 4) {
        } else {
            int oldModeOwnerPid = 0;
            synchronized (this.mSetModeDeathHandlers) {
                if (!this.mSetModeDeathHandlers.isEmpty()) {
                    oldModeOwnerPid = this.mSetModeDeathHandlers.get(0).getPid();
                }
                if (mode == -1) {
                    mode = this.mMode;
                }
                newModeOwnerPid = setModeInt(mode, cb, Binder.getCallingPid(), callingPackage);
            }
            if (newModeOwnerPid != oldModeOwnerPid && newModeOwnerPid != 0) {
                disconnectBluetoothSco(newModeOwnerPid);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Removed duplicated region for block: B:31:0x00f3  */
    /* JADX WARN: Removed duplicated region for block: B:55:0x0183  */
    /* JADX WARN: Removed duplicated region for block: B:81:0x024f  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public int setModeInt(int r31, android.os.IBinder r32, int r33, java.lang.String r34) {
        /*
            Method dump skipped, instructions count: 709
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.audio.AudioService.setModeInt(int, android.os.IBinder, int, java.lang.String):int");
    }

    public int getMode() {
        return this.mMode;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class LoadSoundEffectReply {
        public int mStatus = 1;

        LoadSoundEffectReply() {
        }
    }

    private void loadTouchSoundAssetDefaults() {
        SOUND_EFFECT_FILES.add("Effect_Tick.ogg");
        for (int i = 0; i < 14; i++) {
            this.SOUND_EFFECT_FILES_MAP[i][0] = 0;
            this.SOUND_EFFECT_FILES_MAP[i][1] = -1;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loadTouchSoundAssets() {
        XmlResourceParser parser = null;
        if (SOUND_EFFECT_FILES.isEmpty()) {
            loadTouchSoundAssetDefaults();
            try {
                try {
                    try {
                        parser = this.mContext.getResources().getXml(18284545);
                        XmlUtils.beginDocument(parser, TAG_AUDIO_ASSETS);
                        String version = parser.getAttributeValue(null, "version");
                        boolean inTouchSoundsGroup = false;
                        if ("1.0".equals(version)) {
                            while (true) {
                                XmlUtils.nextElement(parser);
                                String element = parser.getName();
                                if (element == null) {
                                    break;
                                } else if (element.equals(TAG_GROUP)) {
                                    String name = parser.getAttributeValue(null, "name");
                                    if (GROUP_TOUCH_SOUNDS.equals(name)) {
                                        inTouchSoundsGroup = true;
                                        break;
                                    }
                                }
                            }
                            while (inTouchSoundsGroup) {
                                XmlUtils.nextElement(parser);
                                String element2 = parser.getName();
                                if (element2 == null || !element2.equals(TAG_ASSET)) {
                                    break;
                                }
                                String id = parser.getAttributeValue(null, "id");
                                String file = parser.getAttributeValue(null, "file");
                                try {
                                    Field field = AudioManager.class.getField(id);
                                    int fx = AudioManager.getPlaySoundTypeForSEP(field.getInt(null));
                                    int i = SOUND_EFFECT_FILES.indexOf(file);
                                    if (i == -1) {
                                        i = SOUND_EFFECT_FILES.size();
                                        SOUND_EFFECT_FILES.add(file);
                                    }
                                    this.SOUND_EFFECT_FILES_MAP[fx][0] = i;
                                } catch (Exception e) {
                                    Log.w("AudioService", "Invalid touch sound ID: " + id);
                                }
                            }
                        }
                        if (parser == null) {
                            return;
                        }
                    } catch (Resources.NotFoundException e2) {
                        Log.w("AudioService", "audio assets file not found", e2);
                        if (parser == null) {
                            return;
                        }
                    } catch (IOException e3) {
                        Log.w("AudioService", "I/O exception reading touch sound assets", e3);
                        if (parser == null) {
                            return;
                        }
                    }
                } catch (XmlPullParserException e4) {
                    Log.w("AudioService", "XML parser exception reading touch sound assets", e4);
                    if (parser == null) {
                        return;
                    }
                }
                parser.close();
            } catch (Throwable th) {
                if (parser != null) {
                    parser.close();
                }
                throw th;
            }
        }
    }

    public void playSoundEffect(int effectType) {
        playSoundEffectVolume(effectType, -1.0f);
    }

    public void playSoundEffectVolume(int effectType, float volume) {
        if (isStreamMutedByRingerOrZenMode(1)) {
            return;
        }
        if (effectType >= 14 || effectType < 0) {
            Log.w("AudioService", "AudioService effectType value " + effectType + " out of range");
            return;
        }
        sendMsg(this.mAudioHandler, 5, 2, effectType, (int) (1000.0f * volume), null, 0);
    }

    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:34:? -> B:28:0x003c). Please submit an issue!!! */
    public boolean loadSoundEffects() {
        Throwable th;
        int attempts = 3;
        LoadSoundEffectReply reply = new LoadSoundEffectReply();
        synchronized (reply) {
            try {
                sendMsg(this.mAudioHandler, 7, 2, 0, 0, reply, 0);
                while (true) {
                    if (reply.mStatus != 1) {
                        break;
                    }
                    int attempts2 = attempts - 1;
                    if (attempts <= 0) {
                        attempts = attempts2;
                        break;
                    }
                    try {
                        try {
                            reply.wait(5000L);
                        } catch (Throwable th2) {
                            th = th2;
                            throw th;
                        }
                    } catch (InterruptedException e) {
                        Log.w("AudioService", "loadSoundEffects Interrupted while waiting sound pool loaded.");
                    }
                    attempts = attempts2;
                }
                return reply.mStatus == 0;
            } catch (Throwable th3) {
                th = th3;
                throw th;
            }
        }
    }

    public void unloadSoundEffects() {
        sendMsg(this.mAudioHandler, 20, 2, 0, 0, null, 0);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class SoundPoolListenerThread extends Thread {
        public SoundPoolListenerThread() {
            super("SoundPoolListenerThread");
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            Looper.prepare();
            AudioService.this.mSoundPoolLooper = Looper.myLooper();
            synchronized (AudioService.this.mSoundEffectsLock) {
                if (AudioService.this.mSoundPool != null) {
                    AudioService.this.mSoundPoolCallBack = new SoundPoolCallback();
                    AudioService.this.mSoundPool.setOnLoadCompleteListener(AudioService.this.mSoundPoolCallBack);
                }
                AudioService.this.mSoundEffectsLock.notify();
            }
            Looper.loop();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public final class SoundPoolCallback implements SoundPool.OnLoadCompleteListener {
        List<Integer> mSamples;
        int mStatus;

        private SoundPoolCallback() {
            this.mStatus = 1;
            this.mSamples = new ArrayList();
        }

        public int status() {
            return this.mStatus;
        }

        public void setSamples(int[] samples) {
            for (int i = 0; i < samples.length; i++) {
                if (samples[i] > 0) {
                    this.mSamples.add(Integer.valueOf(samples[i]));
                }
            }
        }

        @Override // android.media.SoundPool.OnLoadCompleteListener
        public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
            synchronized (AudioService.this.mSoundEffectsLock) {
                int i = this.mSamples.indexOf(Integer.valueOf(sampleId));
                if (i >= 0) {
                    this.mSamples.remove(i);
                }
                if (status != 0 || this.mSamples.isEmpty()) {
                    this.mStatus = status;
                    AudioService.this.mSoundEffectsLock.notify();
                }
            }
        }
    }

    public void reloadAudioSettings() {
        readAudioSettings(false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void readAudioSettings(boolean userSwitch) {
        readPersistedSettings();
        readUserRestrictions();
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int streamType = 0; streamType < numStreamTypes; streamType++) {
            VolumeStreamState streamState = this.mStreamStates[streamType];
            if (!userSwitch || mStreamVolumeAlias[streamType] != 3) {
                streamState.readSettings();
                synchronized (VolumeStreamState.class) {
                    if (streamState.mIsMuted && ((!isStreamAffectedByMute(streamType) && !isStreamMutedByRingerOrZenMode(streamType)) || this.mUseFixedVolume)) {
                        streamState.mIsMuted = false;
                    }
                }
                continue;
            }
        }
        int streamType2 = getRingerModeInternal();
        setRingerModeInt(streamType2, false);
        checkAllFixedVolumeDevices();
        checkAllAliasStreamVolumes();
        checkMuteAffectedStreams();
        synchronized (this.mSafeMediaVolumeStateLock) {
            this.mMusicActiveMs = MathUtils.constrain(Settings.Secure.getIntForUser(this.mContentResolver, "unsafe_volume_music_active_ms", 0, -2), 0, 72000000);
            if (this.mSafeMediaVolumeState.intValue() == 3) {
                enforceSafeMediaVolume("AudioService");
            }
        }
    }

    public void setSpeakerphoneOn(boolean on) {
        if (!checkAudioSettingsPermission("setSpeakerphoneOn()")) {
            return;
        }
        String eventSource = "setSpeakerphoneOn(" + on + ") from u/pid:" + Binder.getCallingUid() + SliceClientPermissions.SliceAuthority.DELIMITER + Binder.getCallingPid();
        Log.i("AudioService", "setSpeakerphoneOn(" + on + ") from pid=" + Binder.getCallingPid());
        if (Rune.SEC_AUDIO_DISABLE_GAME_SET_SPEAKER_ON) {
            String packageName = getPackageName(Binder.getCallingUid())[0];
            try {
                if (SemGameManager.isAvailable() && SemGameManager.isGamePackage(packageName) && isInCallMode()) {
                    Log.i("AudioService", packageName + "does not have using speaker authority in call, mMode = " + this.mMode);
                    return;
                }
            } catch (IllegalStateException e) {
                Log.e("AudioService", "setSpeakerphoneOn", e);
            }
        }
        if (!on) {
            if (this.mForcedUseForComm == 1) {
                this.mForcedUseForComm = 0;
            }
        } else {
            if (this.mForcedUseForComm == 3) {
                sendMsg(this.mAudioHandler, 8, 2, 2, 0, eventSource, 0);
            }
            this.mForcedUseForComm = 1;
        }
        this.mForcedUseForCommExt = this.mForcedUseForComm;
        sendMsg(this.mAudioHandler, 8, 2, 0, this.mForcedUseForComm, eventSource, 0);
        if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER && this.mSensorThread.isStarted()) {
            sendMsg(this.mAudioHandler, 128, 0, on ? 1 : 0, 0, null, 1000);
        }
    }

    public boolean isSpeakerphoneOn() {
        return this.mForcedUseForCommExt == 1;
    }

    public void setBluetoothScoOn(boolean on) {
        int scoState;
        if (!checkAudioSettingsPermission("setBluetoothScoOn()")) {
            return;
        }
        if (on && this.mBluetoothHeadset != null) {
            List<BluetoothDevice> deviceList = this.mBluetoothHeadset.getDevicesMatchingConnectionStates(new int[]{2, 1, 3});
            if (!deviceList.isEmpty() && (scoState = this.mBluetoothHeadset.getAudioState(this.mBluetoothHeadsetDevice)) != 11 && scoState != 12) {
                Log.e("AudioService", "setBluetoothScoOn() wrong sco state:" + scoState + " mScoAudioState:" + this.mScoAudioState);
                return;
            }
        }
        if (UserHandle.getAppId(Binder.getCallingUid()) >= 10000) {
            if (!on) {
                if (this.mForcedUseForCommExt == 3) {
                    this.mForcedUseForCommExt = 0;
                }
            } else {
                this.mForcedUseForCommExt = 3;
            }
            Log.e("AudioService", "setBluetoothScoOn() return uid = " + Binder.getCallingUid() + ", pid = " + Binder.getCallingPid());
            return;
        }
        String eventSource = "setBluetoothScoOn(" + on + ") from u/pid:" + Binder.getCallingUid() + SliceClientPermissions.SliceAuthority.DELIMITER + Binder.getCallingPid();
        setBluetoothScoOnInt(on, eventSource);
    }

    public void setBluetoothScoOnInt(boolean on, String eventSource) {
        Log.i("AudioService", "setBluetoothScoOnInt: " + on + " " + eventSource);
        if (on) {
            synchronized (this.mScoClients) {
                if (this.mBluetoothHeadset != null && this.mBluetoothHeadset.getAudioState(this.mBluetoothHeadsetDevice) != 12) {
                    this.mForcedUseForCommExt = 3;
                    Log.w("AudioService", "setBluetoothScoOnInt(true) failed because " + this.mBluetoothHeadsetDevice + " is not in audio connected mode");
                    return;
                }
                this.mForcedUseForComm = 3;
            }
        } else if (this.mForcedUseForComm == 3) {
            this.mForcedUseForComm = 0;
        }
        this.mForcedUseForCommExt = this.mForcedUseForComm;
        StringBuilder sb = new StringBuilder();
        sb.append("BT_SCO=");
        sb.append(on ? SemCamera.Parameters.FLASH_MODE_ON : "off");
        AudioSystem.setParameters(sb.toString());
        sendMsg(this.mAudioHandler, 8, 2, 0, this.mForcedUseForComm, eventSource, 0);
        sendMsg(this.mAudioHandler, 8, 2, 2, this.mForcedUseForComm, eventSource, 0);
        setRingerModeInt(getRingerModeInternal(), false);
    }

    public boolean isBluetoothScoOn() {
        return this.mForcedUseForCommExt == 3;
    }

    public void setBluetoothA2dpOn(boolean on) {
        String eventSource = "setBluetoothA2dpOn(" + on + ") from u/pid:" + Binder.getCallingUid() + SliceClientPermissions.SliceAuthority.DELIMITER + Binder.getCallingPid();
        synchronized (this.mBluetoothA2dpEnabledLock) {
            if (this.mBluetoothA2dpEnabled == on) {
                return;
            }
            this.mBluetoothA2dpEnabled = on;
            sendMsg(this.mAudioHandler, 13, 2, 1, this.mBluetoothA2dpEnabled ? 0 : 10, eventSource, 0);
            if (this.mForceSpeaker == 1 && !on) {
                sendMsg(this.mAudioHandler, 8, 2, 1, this.mForceSpeaker, null, 0);
            }
            Log.i("AudioService", "setBluetoothA2dpOn(" + this.mBluetoothA2dpEnabled + ") from pid = " + Binder.getCallingPid());
            if (on != this.mBluetoothA2dpEnabled) {
                checkAndSendEarCareInfo();
            }
        }
    }

    public boolean isBluetoothA2dpOn() {
        boolean z;
        synchronized (this.mBluetoothA2dpEnabledLock) {
            z = this.mBluetoothA2dpEnabled;
        }
        return z;
    }

    public void startBluetoothSco(IBinder cb, int targetSdkVersion) {
        int scoAudioMode = targetSdkVersion < 18 ? 0 : -1;
        startBluetoothScoInt(cb, scoAudioMode);
    }

    public void startBluetoothScoVirtualCall(IBinder cb) {
        startBluetoothScoInt(cb, 0);
    }

    void startBluetoothScoInt(IBinder cb, int scoAudioMode) {
        if (!checkAudioSettingsPermission("startBluetoothSco()") || !this.mSystemReady) {
            return;
        }
        Log.i("AudioService", "startBluetoothScoInt() uid = " + Binder.getCallingUid() + ", pid = " + Binder.getCallingPid());
        ScoClient client = getScoClient(cb, true);
        long ident = Binder.clearCallingIdentity();
        client.incCount(scoAudioMode);
        Binder.restoreCallingIdentity(ident);
    }

    public void stopBluetoothSco(IBinder cb) {
        if (!checkAudioSettingsPermission("stopBluetoothSco()") || !this.mSystemReady) {
            return;
        }
        Log.i("AudioService", "stopBluetoothSco() uid = " + Binder.getCallingUid() + ", pid = " + Binder.getCallingPid());
        ScoClient client = getScoClient(cb, false);
        long ident = Binder.clearCallingIdentity();
        if (client != null) {
            client.decCount();
        }
        Binder.restoreCallingIdentity(ident);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class ScoClient implements IBinder.DeathRecipient {
        private IBinder mCb;
        private int mCreatorPid = Binder.getCallingPid();
        private int mStartcount = 0;

        ScoClient(IBinder cb) {
            this.mCb = cb;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            synchronized (AudioService.this.mScoClients) {
                Log.w("AudioService", "SCO client died");
                int index = AudioService.this.mScoClients.indexOf(this);
                if (index < 0) {
                    Log.w("AudioService", "unregistered SCO client died");
                } else {
                    clearCount(true);
                    AudioService.this.mScoClients.remove(this);
                }
            }
        }

        public void incCount(int scoAudioMode) {
            synchronized (AudioService.this.mScoClients) {
                requestScoState(12, scoAudioMode);
                if (this.mStartcount == 0) {
                    try {
                        this.mCb.linkToDeath(this, 0);
                    } catch (RemoteException e) {
                        Log.w("AudioService", "ScoClient  incCount() could not link to " + this.mCb + " binder death");
                    }
                }
                this.mStartcount++;
            }
        }

        public void decCount() {
            synchronized (AudioService.this.mScoClients) {
                if (this.mStartcount == 0) {
                    Log.w("AudioService", "ScoClient.decCount() already 0");
                } else {
                    this.mStartcount--;
                    if (this.mStartcount == 0) {
                        try {
                            this.mCb.unlinkToDeath(this, 0);
                        } catch (NoSuchElementException e) {
                            Log.w("AudioService", "decCount() going to 0 but not registered to binder");
                        }
                    }
                    requestScoState(10, 0);
                }
            }
        }

        public void clearCount(boolean stopSco) {
            synchronized (AudioService.this.mScoClients) {
                if (this.mStartcount != 0) {
                    try {
                        this.mCb.unlinkToDeath(this, 0);
                    } catch (NoSuchElementException e) {
                        Log.w("AudioService", "clearCount() mStartcount: " + this.mStartcount + " != 0 but not registered to binder");
                    }
                }
                this.mStartcount = 0;
                if (stopSco) {
                    requestScoState(10, 0);
                }
            }
        }

        public int getCount() {
            return this.mStartcount;
        }

        public IBinder getBinder() {
            return this.mCb;
        }

        public int getPid() {
            return this.mCreatorPid;
        }

        public int totalCount() {
            int count;
            synchronized (AudioService.this.mScoClients) {
                count = 0;
                Iterator it = AudioService.this.mScoClients.iterator();
                while (it.hasNext()) {
                    ScoClient mScoClient = (ScoClient) it.next();
                    count += mScoClient.getCount();
                }
            }
            return count;
        }

        private void requestScoState(int state, int scoAudioMode) {
            int modeOwnerPid;
            AudioService.this.checkScoAudioState();
            int clientCount = totalCount();
            Log.i("AudioService", "requestScoState: state=" + state + ", scoAudioMode=" + scoAudioMode + ", clientCount=" + clientCount);
            if (clientCount != 0) {
                return;
            }
            if (state == 12) {
                AudioService.this.broadcastScoConnectionState(2);
                synchronized (AudioService.this.mSetModeDeathHandlers) {
                    if (!AudioService.this.mSetModeDeathHandlers.isEmpty()) {
                        modeOwnerPid = ((SetModeDeathHandler) AudioService.this.mSetModeDeathHandlers.get(0)).getPid();
                    } else {
                        modeOwnerPid = 0;
                    }
                    if (modeOwnerPid == 0 || modeOwnerPid == this.mCreatorPid) {
                        int i = AudioService.this.mScoAudioState;
                        if (i != 0) {
                            switch (i) {
                                case 4:
                                    AudioService.this.mScoAudioState = 3;
                                    AudioService.this.broadcastScoConnectionState(1);
                                    break;
                                case 5:
                                    AudioService.this.mScoAudioState = 1;
                                    break;
                                default:
                                    Log.w("AudioService", "requestScoState: failed to connect in state " + AudioService.this.mScoAudioState + ", scoAudioMode=" + scoAudioMode);
                                    AudioService.this.broadcastScoConnectionState(0);
                                    break;
                            }
                        } else {
                            AudioService.this.mScoAudioMode = scoAudioMode;
                            if (scoAudioMode == -1) {
                                AudioService.this.mScoAudioMode = 0;
                                if (AudioService.this.mBluetoothHeadsetDevice != null) {
                                    AudioService.this.mScoAudioMode = Settings.Global.getInt(AudioService.this.mContentResolver, "bluetooth_sco_channel_" + AudioService.this.mBluetoothHeadsetDevice.getAddress(), 0);
                                    if (AudioService.this.mScoAudioMode > 2 || AudioService.this.mScoAudioMode < 0) {
                                        AudioService.this.mScoAudioMode = 0;
                                    }
                                }
                            }
                            if (AudioService.this.mBluetoothHeadset == null) {
                                if (AudioService.this.getBluetoothHeadset()) {
                                    AudioService.this.mScoAudioState = 1;
                                } else {
                                    Log.w("AudioService", "requestScoState: getBluetoothHeadset failed during connection, mScoAudioMode=" + AudioService.this.mScoAudioMode);
                                    AudioService.this.broadcastScoConnectionState(0);
                                }
                            } else if (AudioService.this.mBluetoothHeadsetDevice == null) {
                                Log.w("AudioService", "requestScoState: no active device while connecting, mScoAudioMode=" + AudioService.this.mScoAudioMode);
                                AudioService.this.broadcastScoConnectionState(0);
                            } else if (AudioService.connectBluetoothScoAudioHelper(AudioService.this.mBluetoothHeadset, AudioService.this.mBluetoothHeadsetDevice, AudioService.this.mScoAudioMode)) {
                                AudioService.this.mScoAudioState = 3;
                            } else {
                                Log.w("AudioService", "requestScoState: connect to " + AudioService.this.mBluetoothHeadsetDevice + " failed, mScoAudioMode=" + AudioService.this.mScoAudioMode);
                                AudioService.this.broadcastScoConnectionState(0);
                            }
                        }
                        return;
                    }
                    Log.w("AudioService", "requestScoState: audio mode is not NORMAL and modeOwnerPid " + modeOwnerPid + " != creatorPid " + this.mCreatorPid);
                    AudioService.this.broadcastScoConnectionState(0);
                }
            } else if (state == 10) {
                int i2 = AudioService.this.mScoAudioState;
                if (i2 == 1) {
                    AudioService.this.mScoAudioState = 0;
                    AudioService.this.broadcastScoConnectionState(0);
                } else if (i2 != 3) {
                    Log.w("AudioService", "requestScoState: failed to disconnect in state " + AudioService.this.mScoAudioState + ", scoAudioMode=" + scoAudioMode);
                    AudioService.this.broadcastScoConnectionState(0);
                } else if (AudioService.this.mBluetoothHeadset == null) {
                    if (AudioService.this.getBluetoothHeadset()) {
                        AudioService.this.mScoAudioState = 4;
                        return;
                    }
                    Log.w("AudioService", "requestScoState: getBluetoothHeadset failed during disconnection, mScoAudioMode=" + AudioService.this.mScoAudioMode);
                    AudioService.this.mScoAudioState = 0;
                    AudioService.this.broadcastScoConnectionState(0);
                } else if (AudioService.this.mBluetoothHeadsetDevice == null) {
                    AudioService.this.mScoAudioState = 0;
                    AudioService.this.broadcastScoConnectionState(0);
                } else if (AudioService.disconnectBluetoothScoAudioHelper(AudioService.this.mBluetoothHeadset, AudioService.this.mBluetoothHeadsetDevice, AudioService.this.mScoAudioMode)) {
                    AudioService.this.mScoAudioState = 5;
                } else {
                    AudioService.this.mScoAudioState = 0;
                    AudioService.this.broadcastScoConnectionState(0);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void checkScoAudioState() {
        synchronized (this.mScoClients) {
            if (this.mBluetoothHeadset != null && this.mBluetoothHeadsetDevice != null && this.mScoAudioState == 0 && this.mBluetoothHeadset.getAudioState(this.mBluetoothHeadsetDevice) != 10) {
                this.mScoAudioState = 2;
            }
        }
    }

    private ScoClient getScoClient(IBinder cb, boolean create) {
        synchronized (this.mScoClients) {
            Iterator<ScoClient> it = this.mScoClients.iterator();
            while (it.hasNext()) {
                ScoClient existingClient = it.next();
                if (existingClient.getBinder() == cb) {
                    return existingClient;
                }
            }
            if (create) {
                ScoClient newClient = new ScoClient(cb);
                this.mScoClients.add(newClient);
                return newClient;
            }
            return null;
        }
    }

    public void clearAllScoClients(int exceptPid, boolean stopSco) {
        synchronized (this.mScoClients) {
            ScoClient savedClient = null;
            Iterator<ScoClient> it = this.mScoClients.iterator();
            while (it.hasNext()) {
                ScoClient cl = it.next();
                if (cl.getPid() != exceptPid) {
                    cl.clearCount(stopSco);
                } else {
                    savedClient = cl;
                }
            }
            this.mScoClients.clear();
            if (savedClient != null) {
                this.mScoClients.add(savedClient);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean getBluetoothHeadset() {
        boolean result = false;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            result = adapter.getProfileProxy(this.mContext, this.mBluetoothProfileServiceListener, 1);
        }
        sendMsg(this.mAudioHandler, 9, 0, 0, 0, null, result ? 3000 : 0);
        return result;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void disconnectBluetoothSco(int exceptPid) {
        synchronized (this.mScoClients) {
            checkScoAudioState();
            if (this.mScoAudioState == 2) {
                return;
            }
            clearAllScoClients(exceptPid, true);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean disconnectBluetoothScoAudioHelper(BluetoothHeadset bluetoothHeadset, BluetoothDevice device, int scoAudioMode) {
        switch (scoAudioMode) {
            case 0:
                return bluetoothHeadset.stopScoUsingVirtualVoiceCall();
            case 1:
                return bluetoothHeadset.disconnectAudio();
            case 2:
                return bluetoothHeadset.stopVoiceRecognition(device);
            default:
                return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean connectBluetoothScoAudioHelper(BluetoothHeadset bluetoothHeadset, BluetoothDevice device, int scoAudioMode) {
        switch (scoAudioMode) {
            case 0:
                return bluetoothHeadset.startScoUsingVirtualVoiceCall();
            case 1:
                return bluetoothHeadset.connectAudio();
            case 2:
                return bluetoothHeadset.startVoiceRecognition(device);
            default:
                return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void resetBluetoothSco() {
        synchronized (this.mScoClients) {
            clearAllScoClients(0, false);
            this.mScoAudioState = 0;
            broadcastScoConnectionState(0);
        }
        setBluetoothScoOnInt(false, "resetBluetoothSco");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void broadcastScoConnectionState(int state) {
        sendMsg(this.mAudioHandler, 19, 2, state, 0, null, 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onBroadcastScoConnectionState(int state) {
        if (state != this.mScoConnectionState) {
            Intent newIntent = new Intent("android.media.ACTION_SCO_AUDIO_STATE_UPDATED");
            newIntent.putExtra("android.media.extra.SCO_AUDIO_STATE", state);
            newIntent.putExtra("android.media.extra.SCO_AUDIO_PREVIOUS_STATE", this.mScoConnectionState);
            sendStickyBroadcastToAll(newIntent);
            this.mScoConnectionState = state;
        }
    }

    private boolean handleBtScoActiveDeviceChange(BluetoothDevice btDevice, boolean isActive) {
        boolean result;
        if (btDevice == null) {
            return true;
        }
        String address = btDevice.getAddress();
        BluetoothClass btClass = btDevice.getBluetoothClass();
        int[] outDeviceTypes = {16, 32, 64};
        if (btClass != null) {
            int deviceClass = btClass.getDeviceClass();
            if (deviceClass == 1028 || deviceClass == 1032) {
                outDeviceTypes = new int[]{32};
            } else if (deviceClass == 1056) {
                outDeviceTypes = new int[]{64};
            }
        }
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            address = "";
        }
        String btDeviceName = btDevice.getName();
        if (btDeviceName == null) {
            btDeviceName = "";
        }
        if (isActive) {
            result = false | handleDeviceConnection(isActive, outDeviceTypes[0], address, btDeviceName);
        } else {
            boolean result2 = false;
            for (int outDeviceType : outDeviceTypes) {
                result2 |= handleDeviceConnection(isActive, outDeviceType, address, btDeviceName);
            }
            result = result2;
        }
        if (handleDeviceConnection(isActive, -2147483640, address, btDeviceName) && result) {
            return true;
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setBtScoActiveDevice(BluetoothDevice btDevice) {
        synchronized (this.mScoClients) {
            Log.i("AudioService", "setBtScoActiveDevice: " + this.mBluetoothHeadsetDevice + " -> " + btDevice);
            BluetoothDevice previousActiveDevice = this.mBluetoothHeadsetDevice;
            if (!Objects.equals(btDevice, previousActiveDevice)) {
                if (!handleBtScoActiveDeviceChange(previousActiveDevice, false)) {
                    Log.w("AudioService", "setBtScoActiveDevice() failed to remove previous device " + previousActiveDevice);
                }
                if (!handleBtScoActiveDeviceChange(btDevice, true)) {
                    Log.e("AudioService", "setBtScoActiveDevice() failed to add new device " + btDevice);
                    btDevice = null;
                }
                this.mBluetoothHeadsetDevice = btDevice;
                if (this.mBluetoothHeadsetDevice == null) {
                    resetBluetoothSco();
                }
                if (this.mBluetoothHeadsetDevice != null && this.mSplitSound) {
                    this.mSplitSound = false;
                    AudioSystem.setPolicyParameters("SplitSound=" + this.mSplitSound);
                    onSendBecomingNoisyIntent(32);
                }
            }
        }
    }

    void disconnectAllBluetoothProfiles() {
        disconnectA2dp();
        disconnectA2dpSink();
        disconnectHeadset();
        disconnectHearingAid();
    }

    void disconnectA2dp() {
        synchronized (this.mConnectedDevices) {
            synchronized (this.mA2dpAvrcpLock) {
                ArraySet<String> toRemove = null;
                for (int i = 0; i < this.mConnectedDevices.size(); i++) {
                    DeviceListSpec deviceSpec = this.mConnectedDevices.valueAt(i);
                    if (deviceSpec.mDeviceType == 128) {
                        toRemove = toRemove != null ? toRemove : new ArraySet<>();
                        toRemove.add(deviceSpec.mDeviceAddress);
                    }
                }
                if (toRemove != null) {
                    int delay = checkSendBecomingNoisyIntent(128, 0, 0);
                    for (int i2 = 0; i2 < toRemove.size(); i2++) {
                        makeA2dpDeviceUnavailableLater(toRemove.valueAt(i2), delay);
                    }
                }
            }
        }
    }

    void disconnectA2dpSink() {
        synchronized (this.mConnectedDevices) {
            int i = 0;
            ArraySet<String> toRemove = null;
            for (int i2 = 0; i2 < this.mConnectedDevices.size(); i2++) {
                DeviceListSpec deviceSpec = this.mConnectedDevices.valueAt(i2);
                if (deviceSpec.mDeviceType == -2147352576) {
                    toRemove = toRemove != null ? toRemove : new ArraySet<>();
                    toRemove.add(deviceSpec.mDeviceAddress);
                }
            }
            if (toRemove != null) {
                while (true) {
                    int i3 = i;
                    int i4 = toRemove.size();
                    if (i3 >= i4) {
                        break;
                    }
                    makeA2dpSrcUnavailable(toRemove.valueAt(i3));
                    i = i3 + 1;
                }
            }
        }
    }

    void disconnectHeadset() {
        synchronized (this.mScoClients) {
            setBtScoActiveDevice(null);
            this.mBluetoothHeadset = null;
        }
    }

    void disconnectHearingAid() {
        synchronized (this.mConnectedDevices) {
            synchronized (this.mHearingAidLock) {
                ArraySet<String> toRemove = null;
                for (int i = 0; i < this.mConnectedDevices.size(); i++) {
                    DeviceListSpec deviceSpec = this.mConnectedDevices.valueAt(i);
                    if (deviceSpec.mDeviceType == 134217728) {
                        toRemove = toRemove != null ? toRemove : new ArraySet<>();
                        toRemove.add(deviceSpec.mDeviceAddress);
                    }
                }
                if (toRemove != null) {
                    checkSendBecomingNoisyIntent(134217728, 0, 0);
                    for (int i2 = 0; i2 < toRemove.size(); i2++) {
                        makeHearingAidDeviceUnavailable(toRemove.valueAt(i2));
                    }
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onCheckMusicActive(String caller) {
        synchronized (this.mSafeMediaVolumeStateLock) {
            if (this.mSafeMediaVolumeState.intValue() == 2) {
                int device = getDeviceForStream(3);
                if ((67109260 & device) != 0) {
                    sendMsg(this.mAudioHandler, 14, 0, 0, 0, caller, 60000);
                    int index = this.mStreamStates[3].getIndex(device);
                    if (AudioSystem.isStreamActive(3, 0) && index > safeMediaVolumeIndex(device)) {
                        this.mMusicActiveMs += 60000;
                        if (this.mMusicActiveMs > 72000000) {
                            setSafeMediaVolumeEnabled(true, caller);
                            this.mMusicActiveMs = 0;
                        }
                        saveMusicActiveMs();
                    }
                }
            }
        }
    }

    private void saveMusicActiveMs() {
        this.mAudioHandler.obtainMessage(22, this.mMusicActiveMs, 0).sendToTarget();
    }

    private int getSafeUsbMediaVolumeIndex() {
        int min = MIN_STREAM_VOLUME[3];
        int max = MAX_STREAM_VOLUME[3];
        this.mSafeUsbMediaVolumeDbfs = this.mContext.getResources().getInteger(17694898) / 100.0f;
        while (Math.abs(max - min) > 1) {
            int index = (max + min) / 2;
            float gainDB = AudioSystem.getStreamVolumeDB(3, index, 67108864);
            if (Float.isNaN(gainDB) || gainDB == this.mSafeUsbMediaVolumeDbfs) {
                break;
            } else if (gainDB < this.mSafeUsbMediaVolumeDbfs) {
                min = index;
            } else {
                max = index;
            }
        }
        int min2 = this.mContext.getResources().getInteger(17694897);
        return min2 * 10;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Removed duplicated region for block: B:18:0x0057  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public void onConfigureSafeVolume(boolean r14, java.lang.String r15) {
        /*
            r13 = this;
            java.lang.Object r0 = r13.mSafeMediaVolumeStateLock
            monitor-enter(r0)
            android.content.Context r1 = r13.mContext     // Catch: java.lang.Throwable -> L9a
            android.content.res.Resources r1 = r1.getResources()     // Catch: java.lang.Throwable -> L9a
            android.content.res.Configuration r1 = r1.getConfiguration()     // Catch: java.lang.Throwable -> L9a
            int r1 = r1.mcc     // Catch: java.lang.Throwable -> L9a
            int r2 = r13.mMcc     // Catch: java.lang.Throwable -> L9a
            if (r2 != r1) goto L19
            int r2 = r13.mMcc     // Catch: java.lang.Throwable -> L9a
            if (r2 != 0) goto L98
            if (r14 == 0) goto L98
        L19:
            android.content.Context r2 = r13.mContext     // Catch: java.lang.Throwable -> L9a
            android.content.res.Resources r2 = r2.getResources()     // Catch: java.lang.Throwable -> L9a
            r3 = 17694897(0x10e00b1, float:2.6081777E-38)
            int r2 = r2.getInteger(r3)     // Catch: java.lang.Throwable -> L9a
            int r2 = r2 * 10
            r13.mSafeMediaVolumeIndex = r2     // Catch: java.lang.Throwable -> L9a
            int r2 = r13.getSafeUsbMediaVolumeIndex()     // Catch: java.lang.Throwable -> L9a
            r13.mSafeUsbMediaVolumeIndex = r2     // Catch: java.lang.Throwable -> L9a
            java.lang.String r2 = "audio.safemedia.force"
            r3 = 0
            boolean r2 = android.os.SystemProperties.getBoolean(r2, r3)     // Catch: java.lang.Throwable -> L9a
            r4 = 1
            if (r2 != 0) goto L4c
            android.content.Context r2 = r13.mContext     // Catch: java.lang.Throwable -> L9a
            android.content.res.Resources r2 = r2.getResources()     // Catch: java.lang.Throwable -> L9a
            r5 = 17957029(0x11200a5, float:2.6816427E-38)
            boolean r2 = r2.getBoolean(r5)     // Catch: java.lang.Throwable -> L9a
            if (r2 == 0) goto L4a
            goto L4c
        L4a:
            r2 = r3
            goto L4d
        L4c:
            r2 = r4
        L4d:
            java.lang.String r5 = "audio.safemedia.bypass"
            boolean r3 = android.os.SystemProperties.getBoolean(r5, r3)     // Catch: java.lang.Throwable -> L9a
            boolean r5 = r13.mIsFactoryMode     // Catch: java.lang.Throwable -> L9a
            if (r5 == 0) goto L58
            r3 = 1
        L58:
            if (r2 == 0) goto L7c
            if (r3 != 0) goto L7c
            r4 = 3
            java.lang.Integer r5 = r13.mSafeMediaVolumeState     // Catch: java.lang.Throwable -> L9a
            int r5 = r5.intValue()     // Catch: java.lang.Throwable -> L9a
            r6 = 2
            if (r5 == r6) goto L8a
            int r5 = r13.mMusicActiveMs     // Catch: java.lang.Throwable -> L9a
            if (r5 != 0) goto L75
            r5 = 3
            java.lang.Integer r5 = java.lang.Integer.valueOf(r5)     // Catch: java.lang.Throwable -> L9a
            r13.mSafeMediaVolumeState = r5     // Catch: java.lang.Throwable -> L9a
            r13.enforceSafeMediaVolume(r15)     // Catch: java.lang.Throwable -> L9a
            goto L8a
        L75:
            java.lang.Integer r5 = java.lang.Integer.valueOf(r6)     // Catch: java.lang.Throwable -> L9a
            r13.mSafeMediaVolumeState = r5     // Catch: java.lang.Throwable -> L9a
            goto L8a
        L7c:
            r5 = 1
            java.lang.Integer r6 = java.lang.Integer.valueOf(r4)     // Catch: java.lang.Throwable -> L9a
            r13.mSafeMediaVolumeState = r6     // Catch: java.lang.Throwable -> L9a
            java.lang.Integer r4 = java.lang.Integer.valueOf(r4)     // Catch: java.lang.Throwable -> L9a
            r13.mSafeMediaVolumeBTState = r4     // Catch: java.lang.Throwable -> L9a
            r4 = r5
        L8a:
            r13.mMcc = r1     // Catch: java.lang.Throwable -> L9a
            com.android.server.audio.AudioService$AudioHandler r6 = r13.mAudioHandler     // Catch: java.lang.Throwable -> L9a
            r7 = 18
            r8 = 2
            r10 = 0
            r11 = 0
            r12 = 0
            r9 = r4
            sendMsg(r6, r7, r8, r9, r10, r11, r12)     // Catch: java.lang.Throwable -> L9a
        L98:
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L9a
            return
        L9a:
            r1 = move-exception
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L9a
            throw r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.audio.AudioService.onConfigureSafeVolume(boolean, java.lang.String):void");
    }

    private int checkForRingerModeChange(int oldIndex, int direction, int step, boolean isMuted, String caller, int flags) {
        int result = 1;
        if (isPlatformTelevision() || this.mIsSingleVolume) {
            return 1;
        }
        int ringerMode = getRingerModeInternal();
        switch (ringerMode) {
            case 0:
                if (this.mIsSingleVolume && direction == -1 && oldIndex >= 2 * step && isMuted) {
                    ringerMode = 2;
                } else if (direction == 1 || direction == 101 || direction == 100) {
                    if (!this.mVolumePolicy.volumeUpToExitSilent) {
                        result = 1 | 128;
                    } else if (this.mHasVibrator && direction == 1) {
                        ringerMode = 1;
                    } else {
                        ringerMode = 2;
                        if (direction == 1 || direction == 100) {
                            this.mSilentModeOff = true;
                        }
                    }
                }
                result &= -2;
                break;
            case 1:
                if (!this.mHasVibrator) {
                    Log.e("AudioService", "checkForRingerModeChange() current ringer mode is vibratebut no vibrator is present");
                    break;
                } else {
                    if (direction == -1) {
                        if (this.mIsSingleVolume && oldIndex >= 2 * step && isMuted) {
                            ringerMode = 2;
                        } else if (this.mPrevVolDirection != -1) {
                            if (this.mVolumePolicy.volumeDownToEnterSilent) {
                                long diff = SystemClock.uptimeMillis() - this.mLoweredFromNormalToVibrateTime;
                                if (diff > this.mVolumePolicy.vibrateToSilentDebounce) {
                                    ringerMode = 0;
                                }
                            } else {
                                result = 1 | 2048;
                            }
                        }
                    } else if (direction == 1 || direction == 101 || direction == 100) {
                        ringerMode = 2;
                        if (direction == 1 || direction == 100) {
                            this.mSilentModeOff = true;
                        }
                    }
                    result &= -2;
                    break;
                }
            case 2:
                if (direction == -1) {
                    if (this.mHasVibrator) {
                        if (step <= oldIndex && oldIndex < 2 * step) {
                            ringerMode = 1;
                            this.mLoweredFromNormalToVibrateTime = SystemClock.uptimeMillis();
                        }
                    } else if (oldIndex == step && this.mVolumePolicy.volumeDownToEnterSilent) {
                        ringerMode = 0;
                    }
                    this.mSilentModeOff = false;
                    break;
                } else if (this.mIsSingleVolume && (direction == 101 || direction == -100)) {
                    if (this.mHasVibrator) {
                        ringerMode = 1;
                    } else {
                        ringerMode = 0;
                    }
                    result = 1 & (-2);
                    break;
                }
                break;
            default:
                Log.e("AudioService", "checkForRingerModeChange() wrong ringer mode: " + ringerMode);
                break;
        }
        if (!isAndroidNPlus(caller) || !wouldToggleZenMode(ringerMode) || this.mNm.isNotificationPolicyAccessGrantedForPackage(caller) || (flags & 4096) != 0) {
            setRingerMode(ringerMode, "AudioService.checkForRingerModeChange", false);
            this.mPrevVolDirection = direction;
            return result;
        }
        throw new SecurityException("Not allowed to change Do Not Disturb state");
    }

    public boolean isStreamAffectedByRingerMode(int streamType) {
        return (this.mRingerModeAffectedStreams & (1 << streamType)) != 0;
    }

    private boolean shouldZenMuteStream(int streamType) {
        if (this.mNm.getZenMode() != 1) {
            return false;
        }
        NotificationManager.Policy zenPolicy = this.mNm.getNotificationPolicy();
        boolean muteAlarms = (zenPolicy.priorityCategories & 32) == 0;
        boolean muteMedia = (zenPolicy.priorityCategories & 64) == 0;
        boolean muteSystem = (zenPolicy.priorityCategories & 128) == 0;
        boolean muteNotificationAndRing = ZenModeConfig.areAllPriorityOnlyNotificationZenSoundsMuted(this.mNm.getNotificationPolicy());
        return (muteAlarms && isAlarm(streamType)) || (muteMedia && isMedia(streamType)) || ((muteSystem && isSystem(streamType)) || (muteNotificationAndRing && isNotificationOrRinger(streamType)));
    }

    private boolean isStreamMutedByRingerOrZenMode(int streamType) {
        return (this.mRingerAndZenModeMutedStreams & (1 << streamType)) != 0;
    }

    private boolean updateZenModeAffectedStreams() {
        int zenModeAffectedStreams = 0;
        if (this.mSystemReady && this.mNm.getZenMode() == 1) {
            NotificationManager.Policy zenPolicy = this.mNm.getNotificationPolicy();
            if ((zenPolicy.priorityCategories & 32) == 0) {
                zenModeAffectedStreams = 0 | 16;
            }
            if ((zenPolicy.priorityCategories & 64) == 0) {
                zenModeAffectedStreams |= 8;
            }
            if ((zenPolicy.priorityCategories & 128) == 0) {
                zenModeAffectedStreams |= 2;
            }
            if ((zenPolicy.priorityCategories & 1) == 0 && (zenPolicy.priorityCategories & 2) == 0 && (zenPolicy.priorityCategories & 4) == 0 && (zenPolicy.priorityCategories & 8) == 0 && (zenPolicy.priorityCategories & 16) == 0 && (zenPolicy.state & 1) == 0) {
                zenModeAffectedStreams |= 32;
            }
        }
        if (this.mZenModeAffectedStreams != zenModeAffectedStreams) {
            this.mZenModeAffectedStreams = zenModeAffectedStreams;
            return true;
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    @GuardedBy("mSettingsLock")
    public boolean updateRingerAndZenModeAffectedStreams() {
        int ringerModeAffectedStreams;
        boolean updatedZenModeAffectedStreams = updateZenModeAffectedStreams();
        int ringerModeAffectedStreams2 = Settings.System.getIntForUser(this.mContentResolver, "mode_ringer_streams_affected", ResponseCodes.OBEX_HTTP_PARTIAL, -2) | 294;
        if (this.mIsSingleVolume) {
            ringerModeAffectedStreams2 = 0;
        } else if (this.mRingerModeDelegate != null) {
            ringerModeAffectedStreams2 = this.mRingerModeDelegate.getRingerModeAffectedStreams(ringerModeAffectedStreams2);
        }
        if (Rune.SEC_AUDIO_DEFAULT_STREAM_NOTIFICATION) {
            ringerModeAffectedStreams2 &= -9;
        }
        int ringerModeAffectedStreams3 = ringerModeAffectedStreams2 & (-129);
        if (mStreamVolumeAlias[8] == 2) {
            ringerModeAffectedStreams = ringerModeAffectedStreams3 | 256;
        } else {
            ringerModeAffectedStreams = ringerModeAffectedStreams3 & (-257);
        }
        if (ringerModeAffectedStreams != this.mRingerModeAffectedStreams) {
            Settings.System.putIntForUser(this.mContentResolver, "mode_ringer_streams_affected", ringerModeAffectedStreams, -2);
            this.mRingerModeAffectedStreams = ringerModeAffectedStreams;
            return true;
        }
        return updatedZenModeAffectedStreams;
    }

    public boolean isStreamAffectedByMute(int streamType) {
        return (this.mMuteAffectedStreams & (1 << streamType)) != 0;
    }

    private void ensureValidDirection(int direction) {
        if (direction != -100) {
            switch (direction) {
                case -1:
                case 0:
                case 1:
                    return;
                default:
                    switch (direction) {
                        case 100:
                        case 101:
                            return;
                        default:
                            throw new IllegalArgumentException("Bad direction " + direction);
                    }
            }
        }
    }

    private void ensureValidStreamType(int streamType) {
        if (streamType < 0 || streamType >= this.mStreamStates.length) {
            throw new IllegalArgumentException("Bad stream type " + streamType);
        }
    }

    private boolean isMuteAdjust(int adjust) {
        return adjust == -100 || adjust == 100 || adjust == 101;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isInCommunication() {
        TelecomManager telecomManager = (TelecomManager) this.mContext.getSystemService("telecom");
        long ident = Binder.clearCallingIdentity();
        boolean IsInCall = telecomManager.isInCall() && !telecomManager.isRinging();
        if (telecomManager.isRinging() && getMode() == 2) {
            IsInCall = telecomManager.isRinging();
        }
        Binder.restoreCallingIdentity(ident);
        return IsInCall || getMode() == 3 || getMode() == 2;
    }

    private boolean wasStreamActiveRecently(int stream, int delay_ms) {
        return AudioSystem.isStreamActive(stream, delay_ms) || AudioSystem.isStreamActiveRemotely(stream, delay_ms);
    }

    private int getActiveStreamType(int suggestedStreamType) {
        CustomDeviceManagerProxy customDeviceManager;
        if (suggestedStreamType == Integer.MIN_VALUE && !isInCommunication() && (customDeviceManager = CustomDeviceManagerProxy.getInstance()) != null) {
            switch (customDeviceManager.getVolumeControlStream()) {
                case 1:
                    return 1;
                case 2:
                    return 2;
                case 3:
                    return 3;
                case 4:
                    return 5;
            }
        }
        if (this.mIsSingleVolume && suggestedStreamType == Integer.MIN_VALUE) {
            return 3;
        }
        if (this.mPlatformType == 1) {
            if (isInCommunication()) {
                return AudioSystem.getForceUse(0) == 3 ? 6 : 0;
            } else if (suggestedStreamType == Integer.MIN_VALUE) {
                return getActiveStreamTypeInternal(this.mPlatformType);
            } else {
                if (wasStreamActiveRecently(5, sStreamOverrideDelayMs)) {
                    Log.v("AudioService", "getActiveStreamType: Forcing STREAM_NOTIFICATION stream active");
                    return 5;
                } else if (wasStreamActiveRecently(2, sStreamOverrideDelayMs)) {
                    Log.v("AudioService", "getActiveStreamType: Forcing STREAM_RING stream active");
                    return 2;
                }
            }
        }
        if (isInCommunication()) {
            if (AudioSystem.getForceUse(0) == 3) {
                Log.v("AudioService", "getActiveStreamType: Forcing STREAM_BLUETOOTH_SCO");
                return 6;
            }
            Log.v("AudioService", "getActiveStreamType: Forcing STREAM_VOICE_CALL");
            return 0;
        } else if (AudioSystem.isStreamActive(5, sStreamOverrideDelayMs)) {
            Log.v("AudioService", "getActiveStreamType: Forcing STREAM_NOTIFICATION");
            return 5;
        } else if (AudioSystem.isStreamActive(2, sStreamOverrideDelayMs)) {
            Log.v("AudioService", "getActiveStreamType: Forcing STREAM_RING");
            return 2;
        } else if (suggestedStreamType == Integer.MIN_VALUE) {
            return getActiveStreamTypeInternal(this.mPlatformType);
        } else {
            Log.v("AudioService", "getActiveStreamType: Returning suggested type " + suggestedStreamType);
            return suggestedStreamType;
        }
    }

    private void broadcastRingerMode(String action, int ringerMode) {
        Intent broadcast = new Intent(action);
        broadcast.putExtra("android.media.EXTRA_RINGER_MODE", ringerMode);
        broadcast.addFlags(603979776);
        sendStickyBroadcastToAll(broadcast);
    }

    private void broadcastVibrateSetting(int vibrateType) {
        if (this.mActivityManagerInternal.isSystemReady()) {
            Intent broadcast = new Intent("android.media.VIBRATE_SETTING_CHANGED");
            broadcast.putExtra("android.media.EXTRA_VIBRATE_TYPE", vibrateType);
            broadcast.putExtra("android.media.EXTRA_VIBRATE_SETTING", getVibrateSetting(vibrateType));
            sendBroadcastToAll(broadcast);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void queueMsgUnderWakeLock(Handler handler, int msg, int arg1, int arg2, Object obj, int delay) {
        long ident = Binder.clearCallingIdentity();
        this.mAudioEventWakeLock.acquire();
        Binder.restoreCallingIdentity(ident);
        sendMsg(handler, msg, 2, arg1, arg2, obj, delay);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void sendMsg(Handler handler, int msg, int existingMsgPolicy, int arg1, int arg2, Object obj, int delay) {
        if (existingMsgPolicy == 0) {
            handler.removeMessages(msg);
        } else if (existingMsgPolicy == 1 && handler.hasMessages(msg)) {
            return;
        }
        synchronized (mLastDeviceConnectMsgTime) {
            long time = SystemClock.uptimeMillis() + delay;
            if (msg == 101 || msg == 102 || msg == 105 || msg == 100 || msg == 103 || msg == 106) {
                if (mLastDeviceConnectMsgTime.longValue() >= time) {
                    time = mLastDeviceConnectMsgTime.longValue() + 30;
                }
                mLastDeviceConnectMsgTime = Long.valueOf(time);
            }
            handler.sendMessageAtTime(handler.obtainMessage(msg, arg1, arg2, obj), time);
        }
    }

    boolean checkAudioSettingsPermission(String method) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_AUDIO_SETTINGS") == 0) {
            return true;
        }
        String msg = "Audio Settings Permission Denial: " + method + " from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid();
        Log.w("AudioService", msg);
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getDeviceForStream(int stream) {
        int device = getDevicesForStream(stream);
        if (((device - 1) & device) != 0) {
            if ((device & 2) != 0) {
                return 2;
            }
            if ((262144 & device) != 0) {
                return 262144;
            }
            if ((524288 & device) != 0) {
                return 524288;
            }
            if ((2097152 & device) != 0) {
                return 2097152;
            }
            return device & 896;
        }
        return device;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getDevicesForStream(int stream) {
        return getDevicesForStream(stream, true);
    }

    private int getDevicesForStream(int stream, boolean checkOthers) {
        int observeDevicesForStream_syncVSS;
        ensureValidStreamType(stream);
        synchronized (VolumeStreamState.class) {
            observeDevicesForStream_syncVSS = this.mStreamStates[stream].observeDevicesForStream_syncVSS(checkOthers);
        }
        return observeDevicesForStream_syncVSS;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void observeDevicesForStreams(int skipStream) {
        synchronized (VolumeStreamState.class) {
            for (int stream = 0; stream < this.mStreamStates.length; stream++) {
                if (stream != skipStream) {
                    this.mStreamStates[stream].observeDevicesForStream_syncVSS(false);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class WiredDeviceConnectionState {
        public final String mAddress;
        public final String mCaller;
        public final String mName;
        public final int mState;
        public final int mType;

        public WiredDeviceConnectionState(int type, int state, String address, String name, String caller) {
            this.mType = type;
            this.mState = state;
            this.mAddress = address;
            this.mName = name;
            this.mCaller = caller;
        }
    }

    public void setWiredDeviceConnectionState(int type, int state, String address, String name, String caller) {
        ArrayMap<String, DeviceListSpec> arrayMap;
        int state2;
        ArrayMap<String, DeviceListSpec> arrayMap2 = this.mConnectedDevices;
        synchronized (arrayMap2) {
            try {
                try {
                    Slog.i("AudioService", "setWiredDeviceConnectionState(" + state + " nm: " + name + " addr:" + address + ")");
                    if (state == 0 || !((type == 4 || type == 8 || type == 67108864 || type == -2147483632) && isRestrictionHeadphone())) {
                        state2 = state;
                    } else if (!checkDeviceConnected(type)) {
                        return;
                    } else {
                        state2 = 0;
                    }
                } catch (Throwable th) {
                    th = th;
                    arrayMap = arrayMap2;
                }
                try {
                    int delay = checkSendBecomingNoisyIntent(type, state2, 0);
                    queueMsgUnderWakeLock(this.mAudioHandler, 100, 0, 0, new WiredDeviceConnectionState(type, state2, address, name, caller), delay);
                } catch (Throwable th2) {
                    th = th2;
                    arrayMap = arrayMap2;
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
            }
        }
    }

    public void setHearingAidDeviceConnectionState(BluetoothDevice device, int state) {
        Log.i("AudioService", "setBluetoothHearingAidDeviceConnectionState");
        setBluetoothHearingAidDeviceConnectionState(device, state, false, 0);
    }

    public int setBluetoothHearingAidDeviceConnectionState(BluetoothDevice device, int state, boolean suppressNoisyIntent, int musicDevice) {
        int intState;
        synchronized (this.mConnectedDevices) {
            if (!suppressNoisyIntent) {
                intState = state == 2 ? 1 : 0;
                intState = checkSendBecomingNoisyIntent(134217728, intState, musicDevice);
            }
            queueMsgUnderWakeLock(this.mAudioHandler, 105, state, 0, device, intState);
        }
        return intState;
    }

    public int setBluetoothA2dpDeviceConnectionState(BluetoothDevice device, int state, int profile) {
        return setBluetoothA2dpDeviceConnectionStateSuppressNoisyIntent(device, state, profile, false, -1);
    }

    public int setBluetoothA2dpDeviceConnectionStateSuppressNoisyIntent(BluetoothDevice device, int state, int profile, boolean suppressNoisyIntent, int a2dpVolume) {
        if (this.mAudioHandler.hasMessages(102, device)) {
            return 0;
        }
        return setBluetoothA2dpDeviceConnectionStateInt(device, state, profile, suppressNoisyIntent, 0, a2dpVolume);
    }

    public int setBluetoothA2dpDeviceConnectionStateInt(BluetoothDevice device, int state, int profile, boolean suppressNoisyIntent, int musicDevice, int a2dpVolume) {
        int delay;
        if (profile != 2 && profile != 11) {
            throw new IllegalArgumentException("invalid profile " + profile);
        }
        synchronized (this.mConnectedDevices) {
            try {
                if (profile != 2 || suppressNoisyIntent) {
                    delay = 0;
                } else {
                    int intState = state == 2 ? 1 : 0;
                    delay = checkSendBecomingNoisyIntent(128, intState, musicDevice);
                }
                int delay2 = delay;
                try {
                    Log.d("AudioService", "setBluetoothA2dpDeviceConnectionStateInt device: " + device + " state: " + state + " delay(ms): " + delay2 + " suppressNoisyIntent: " + suppressNoisyIntent);
                    if (device != null && state != 0) {
                        String address = device.getAddress();
                        Log.i("AudioService", "[BT_VOL_SEPARATION]From setBluetoothA2dpDeviceConnectionState");
                        updateBluetoothVolume(address, false);
                    } else if (state == 0) {
                        if (device.getAddress().equals(this.m1stBTAddr)) {
                            this.m1stBTAddr = "";
                        } else if (device.getAddress().equals(this.m2ndBTAddr)) {
                            this.m2ndBTAddr = "";
                        }
                    }
                    if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER && getMode() == 1 && state == 2) {
                        this.mSensorThread.stopSensor();
                    }
                    queueMsgUnderWakeLock(this.mAudioHandler, profile == 2 ? 102 : 101, state, a2dpVolume, device, delay2);
                    return delay2;
                } catch (Throwable th) {
                    th = th;
                    throw th;
                }
            } catch (Throwable th2) {
                th = th2;
            }
        }
    }

    public void handleBluetoothA2dpDeviceConfigChange(BluetoothDevice device) {
        synchronized (this.mConnectedDevices) {
            queueMsgUnderWakeLock(this.mAudioHandler, 103, 0, 0, device, 0);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onAccessoryPlugMediaUnmute(int newDevice) {
        Log.i("AudioService", String.format("onAccessoryPlugMediaUnmute newDevice=%d [%s]", Integer.valueOf(newDevice), AudioSystem.getOutputDeviceName(newDevice)));
        synchronized (this.mConnectedDevices) {
            if (this.mNm.getZenMode() != 2 && (DEVICE_MEDIA_UNMUTED_ON_PLUG & newDevice) != 0 && this.mStreamStates[3].mIsMuted && ((this.mStreamStates[3].getIndex(newDevice) != 0 || this.mFineMediaVolume.getIndex(newDevice) != 0) && (AudioSystem.getDevicesForStream(3) & newDevice) != 0)) {
                Log.i("AudioService", String.format(" onAccessoryPlugMediaUnmute unmuting device=%d [%s]", Integer.valueOf(newDevice), AudioSystem.getOutputDeviceName(newDevice)));
                this.mStreamStates[3].mute(false);
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class VolumeStreamState {
        private final SparseIntArray mIndexMap;
        private int mIndexMax;
        private int mIndexMin;
        private boolean mIsMuted;
        private String mObservedAddress;
        private int mObservedDevices;
        private final Intent mStreamDevicesChanged;
        private final int mStreamType;
        private String mVolumeIndexSettingName;

        private VolumeStreamState(String settingName, int streamType) {
            this.mIndexMap = new SparseIntArray(8);
            this.mVolumeIndexSettingName = settingName;
            this.mStreamType = streamType;
            this.mIndexMin = AudioService.MIN_STREAM_VOLUME[streamType] * 10;
            this.mIndexMax = AudioService.MAX_STREAM_VOLUME[streamType] * 10;
            AudioSystem.initStreamVolume(streamType, this.mIndexMin / 10, this.mIndexMax / 10);
            readSettings();
            this.mStreamDevicesChanged = new Intent("android.media.STREAM_DEVICES_CHANGED_ACTION");
            this.mStreamDevicesChanged.putExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", this.mStreamType);
        }

        public int observeDevicesForStream_syncVSS(boolean checkOthers) {
            int devices = AudioSystem.getDevicesForStream(this.mStreamType);
            String address = AudioService.this.getAddressForDevice(devices);
            if (devices == this.mObservedDevices && address.equals(this.mObservedAddress)) {
                return devices;
            }
            int prevDevices = this.mObservedDevices;
            this.mObservedDevices = devices;
            this.mObservedAddress = address;
            if (checkOthers) {
                AudioService.this.observeDevicesForStreams(this.mStreamType);
            }
            if (AudioService.mStreamVolumeAlias[this.mStreamType] == this.mStreamType) {
                EventLogTags.writeStreamDevicesChanged(this.mStreamType, prevDevices, devices);
            }
            if (Rune.SEC_AUDIO_MULTI_SOUND && this.mStreamType == 3 && AudioService.this.mMode == 0) {
                AudioService.sendMsg(AudioService.this.mAudioHandler, 120, 0, devices, 0, null, 0);
                AudioService.sendMsg(AudioService.this.mAudioHandler, 125, 0, devices, 0, null, 0);
            }
            if (Rune.SEC_AUDIO_PREVENT_OVERHEAT_BY_MAX_VOLUME && AudioService.this.mPreventOverheatState.mState && this.mStreamType == 3) {
                AudioService.this.mPreventOverheatState.setDevice(this.mObservedDevices);
            }
            this.mStreamDevicesChanged.addFlags(67108864);
            AudioService.this.sendBroadcastToUser(this.mStreamDevicesChanged.putExtra("android.media.EXTRA_PREV_VOLUME_STREAM_DEVICES", prevDevices).putExtra("android.media.EXTRA_VOLUME_STREAM_DEVICES", devices).putExtra("android.media.EXTRA_VOLUME_STREAM_MUSIC_ADDRESS", address), UserHandle.ALL);
            return devices;
        }

        public String getSettingNameForDevice(int device) {
            if (!hasValidSettingsName()) {
                return null;
            }
            String suffix = AudioSystem.getOutputDeviceName(device);
            if (suffix.isEmpty()) {
                return this.mVolumeIndexSettingName;
            }
            if (this.mStreamType == 3 && suffix.contains("a2dp")) {
                return this.mVolumeIndexSettingName + "_" + suffix + "_" + AudioService.this.getAddressForDevice(device);
            }
            return this.mVolumeIndexSettingName + "_" + suffix;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean hasValidSettingsName() {
            return (this.mVolumeIndexSettingName == null || this.mVolumeIndexSettingName.isEmpty()) ? false : true;
        }

        public void readSettings() {
            int defaultIndex;
            int index;
            synchronized (AudioService.this.mSettingsLock) {
                synchronized (VolumeStreamState.class) {
                    if (AudioService.this.mUseFixedVolume) {
                        this.mIndexMap.put(PermissionMonitor.APM_FLAG_APM_IS_OFF, this.mIndexMax);
                        return;
                    }
                    synchronized (VolumeStreamState.class) {
                        int remainingDevices = 1879048191;
                        int i = 0;
                        while (remainingDevices != 0) {
                            int device = 1 << i;
                            if ((device & remainingDevices) != 0) {
                                remainingDevices &= ~device;
                                if (device != 1073741824) {
                                    defaultIndex = -1;
                                } else {
                                    defaultIndex = AudioSystem.DEFAULT_STREAM_VOLUME[this.mStreamType];
                                }
                                if (!hasValidSettingsName()) {
                                    index = defaultIndex;
                                } else {
                                    String name = getSettingNameForDevice(device);
                                    index = Settings.System.getIntForUser(AudioService.this.mContentResolver, name, defaultIndex, -2);
                                }
                                if (index != -1) {
                                    this.mIndexMap.put(device, getValidIndex(10 * index));
                                }
                            }
                            i++;
                        }
                    }
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int getAbsoluteVolumeIndex(int index, int device) {
            if (index == 0 && AudioService.this.mFineMediaVolume.getIndex(device) == 0) {
                return 0;
            }
            int index2 = (this.mIndexMax + 5) / 10;
            return index2;
        }

        private int getAbsoluteVolumeIndex(int index) {
            if (index == 0) {
                return 0;
            }
            if (index == 1) {
                return ((int) (this.mIndexMax * 0.5d)) / 10;
            }
            if (index == 2) {
                return ((int) (this.mIndexMax * 0.7d)) / 10;
            }
            if (index == 3) {
                return ((int) (this.mIndexMax * 0.85d)) / 10;
            }
            return (this.mIndexMax + 5) / 10;
        }

        public void applyDeviceVolume_syncVSS(int device) {
            int index;
            if (this.mIsMuted) {
                index = 0;
            } else {
                int index2 = device & 896;
                if (index2 != 0 && AudioService.this.mAvrcpAbsVolSupported) {
                    index = getAbsoluteVolumeIndex((getIndex(device) + 5) / 10, device);
                } else if ((AudioService.this.mFullVolumeDevices & device) != 0) {
                    index = (this.mIndexMax + 5) / 10;
                } else if ((134217728 & device) != 0) {
                    index = (this.mIndexMax + 5) / 10;
                } else {
                    index = (getIndex(device) + 5) / 10;
                }
            }
            AudioSystem.setStreamVolumeIndex(this.mStreamType, index, device);
            if (Rune.SEC_AUDIO_PREVENT_OVERHEAT_BY_MAX_VOLUME && AudioService.this.mPreventOverheatState.mState && this.mStreamType == 3 && (device & 2) != 0) {
                AudioService.this.mPreventOverheatState.setLimitedVolumeForOverheat();
            }
        }

        public void applyAllVolumes() {
            int index;
            int index2;
            synchronized (VolumeStreamState.class) {
                for (int i = 0; i < this.mIndexMap.size(); i++) {
                    int device = this.mIndexMap.keyAt(i);
                    if (device != 1073741824) {
                        if (this.mIsMuted) {
                            index2 = 0;
                        } else {
                            int index3 = device & 896;
                            if (index3 != 0 && AudioService.this.mAvrcpAbsVolSupported) {
                                index2 = getAbsoluteVolumeIndex((getIndex(device) + 5) / 10, device);
                            } else if ((AudioService.this.mFullVolumeDevices & device) != 0) {
                                index2 = (this.mIndexMax + 5) / 10;
                            } else if ((134217728 & device) != 0) {
                                index2 = (this.mIndexMax + 5) / 10;
                            } else {
                                index2 = (this.mIndexMap.valueAt(i) + 5) / 10;
                                AudioSystem.setStreamVolumeIndex(this.mStreamType, index2, device);
                                if (!this.mIsMuted && this.mStreamType == 3 && AudioService.this.mFineMediaVolume != null && AudioService.this.mFineMediaVolume.getIndex(device) != 0) {
                                    AudioService.this.mFineMediaVolume.applyFineVolume(device);
                                }
                            }
                        }
                        AudioSystem.setStreamVolumeIndex(this.mStreamType, index2, device);
                        if (!this.mIsMuted) {
                            AudioService.this.mFineMediaVolume.applyFineVolume(device);
                        }
                    }
                }
                if (this.mIsMuted) {
                    index = 0;
                } else {
                    int index4 = getIndex(PermissionMonitor.APM_FLAG_APM_IS_OFF);
                    index = (index4 + 5) / 10;
                }
                AudioSystem.setStreamVolumeIndex(this.mStreamType, index, (int) PermissionMonitor.APM_FLAG_APM_IS_OFF);
            }
        }

        public boolean adjustIndex(int deltaIndex, int device, String caller) {
            return setIndex(getIndex(device) + deltaIndex, device, caller);
        }

        public boolean setIndex(int index, int device, String caller) {
            int oldIndex;
            int index2;
            boolean changed;
            boolean changed2;
            synchronized (AudioService.this.mSettingsLock) {
                synchronized (VolumeStreamState.class) {
                    oldIndex = getIndex(device);
                    index2 = getValidIndex(index);
                    if (this.mStreamType == 7 && AudioService.this.mCameraSoundForced) {
                        index2 = this.mIndexMax;
                    }
                    this.mIndexMap.put(device, index2);
                    boolean isCurrentDevice = true;
                    changed = oldIndex != index2;
                    if (device != AudioService.this.getDeviceForStream(this.mStreamType)) {
                        isCurrentDevice = false;
                    }
                    int numStreamTypes = AudioSystem.getNumStreamTypes();
                    for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
                        VolumeStreamState aliasStreamState = AudioService.this.mStreamStates[streamType];
                        if (streamType != this.mStreamType && AudioService.mStreamVolumeAlias[streamType] == this.mStreamType && (changed || !aliasStreamState.hasIndexForDevice(device))) {
                            int scaledIndex = AudioService.this.rescaleIndex(index2, this.mStreamType, streamType);
                            aliasStreamState.setIndex(scaledIndex, device, caller);
                            if (isCurrentDevice) {
                                aliasStreamState.setIndex(scaledIndex, AudioService.this.getDeviceForStream(streamType), caller);
                            }
                        }
                    }
                    if (changed && this.mStreamType == 2 && device == 2) {
                        for (int i = 0; i < this.mIndexMap.size(); i++) {
                            int otherDevice = this.mIndexMap.keyAt(i);
                            if ((otherDevice & 112) != 0) {
                                this.mIndexMap.put(otherDevice, index2);
                            }
                        }
                    }
                }
                changed2 = changed;
            }
            if (changed2) {
                int oldIndex2 = (oldIndex + 5) / 10;
                int index3 = (index2 + 5) / 10;
                if (AudioService.mStreamVolumeAlias[this.mStreamType] == this.mStreamType) {
                    if (caller == null) {
                        Log.w("AudioService", "No caller for volume_changed event", new Throwable());
                    }
                    EventLogTags.writeVolumeChanged(this.mStreamType, oldIndex2, index3, this.mIndexMax / 10, caller);
                }
            }
            if (this.mStreamType == 3 && !changed2) {
                return AudioService.this.mFineMediaVolume.lastChanged(device);
            }
            return changed2;
        }

        public int getIndex(int device) {
            int index;
            synchronized (VolumeStreamState.class) {
                index = this.mIndexMap.get(device, -1);
                if (index == -1) {
                    index = this.mIndexMap.get(PermissionMonitor.APM_FLAG_APM_IS_OFF);
                }
            }
            return index;
        }

        public boolean hasIndexForDevice(int device) {
            boolean z;
            synchronized (VolumeStreamState.class) {
                z = this.mIndexMap.get(device, -1) != -1;
            }
            return z;
        }

        public int getMaxIndex() {
            return this.mIndexMax;
        }

        public int getMinIndex() {
            return this.mIndexMin;
        }

        @GuardedBy("VolumeStreamState.class")
        public void refreshRange(int sourceStreamType) {
            this.mIndexMin = AudioService.MIN_STREAM_VOLUME[sourceStreamType] * 10;
            this.mIndexMax = AudioService.MAX_STREAM_VOLUME[sourceStreamType] * 10;
            for (int i = 0; i < this.mIndexMap.size(); i++) {
                int device = this.mIndexMap.keyAt(i);
                int index = this.mIndexMap.valueAt(i);
                this.mIndexMap.put(device, getValidIndex(index));
            }
        }

        @GuardedBy("VolumeStreamState.class")
        public void setAllIndexes(VolumeStreamState srcStream, String caller) {
            if (this.mStreamType == srcStream.mStreamType) {
                return;
            }
            int srcStreamType = srcStream.getStreamType();
            int index = srcStream.getIndex(PermissionMonitor.APM_FLAG_APM_IS_OFF);
            int index2 = AudioService.this.rescaleIndex(index, srcStreamType, this.mStreamType);
            for (int i = 0; i < this.mIndexMap.size(); i++) {
                this.mIndexMap.put(this.mIndexMap.keyAt(i), index2);
            }
            SparseIntArray srcMap = srcStream.mIndexMap;
            for (int i2 = 0; i2 < srcMap.size(); i2++) {
                int device = srcMap.keyAt(i2);
                int index3 = srcMap.valueAt(i2);
                setIndex(AudioService.this.rescaleIndex(index3, srcStreamType, this.mStreamType), device, caller);
            }
        }

        @GuardedBy("VolumeStreamState.class")
        public void setAllIndexesToMax() {
            for (int i = 0; i < this.mIndexMap.size(); i++) {
                this.mIndexMap.put(this.mIndexMap.keyAt(i), this.mIndexMax);
            }
        }

        public void mute(boolean state) {
            boolean changed = false;
            synchronized (VolumeStreamState.class) {
                if (state != this.mIsMuted) {
                    changed = true;
                    this.mIsMuted = state;
                    AudioService.sendMsg(AudioService.this.mAudioHandler, 10, 2, 0, 0, this, 0);
                }
            }
            if (changed) {
                Intent intent = new Intent("android.media.STREAM_MUTE_CHANGED_ACTION");
                intent.putExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", this.mStreamType);
                intent.putExtra("android.media.EXTRA_STREAM_VOLUME_MUTED", state);
                AudioService.this.sendBroadcastToAll(intent);
                if (this.mStreamType == 3) {
                    String caller = AudioService.this.mVolumeLogger.getLastPackageName();
                    AudioService.this.sendBroadcastToSoundEventReceiver(2, state ? 1 : 0, caller);
                }
            }
        }

        public int getStreamType() {
            return this.mStreamType;
        }

        public void checkFixedVolumeDevices() {
            synchronized (VolumeStreamState.class) {
                if (AudioService.mStreamVolumeAlias[this.mStreamType] == 3) {
                    for (int i = 0; i < this.mIndexMap.size(); i++) {
                        int device = this.mIndexMap.keyAt(i);
                        int index = this.mIndexMap.valueAt(i);
                        if ((AudioService.this.mFullVolumeDevices & device) != 0 || ((AudioService.this.mFixedVolumeDevices & device) != 0 && index != 0)) {
                            this.mIndexMap.put(device, this.mIndexMax);
                        }
                        applyDeviceVolume_syncVSS(device);
                    }
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int getValidIndex(int index) {
            if (index >= this.mIndexMin) {
                if (AudioService.this.mUseFixedVolume || index > this.mIndexMax) {
                    return this.mIndexMax;
                }
                return index;
            }
            return this.mIndexMin;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void dump(PrintWriter pw) {
            pw.print("   Muted: ");
            pw.println(this.mIsMuted);
            pw.print("   Min: ");
            pw.println((this.mIndexMin + 5) / 10);
            pw.print("   Max: ");
            pw.println((this.mIndexMax + 5) / 10);
            pw.print("   Current: ");
            int n = 0;
            for (int i = 0; i < this.mIndexMap.size(); i++) {
                if (i > 0) {
                    pw.print(", ");
                }
                int device = this.mIndexMap.keyAt(i);
                pw.print(Integer.toHexString(device));
                String deviceName = device == 1073741824 ? "default" : AudioSystem.getOutputDeviceName(device);
                if (!deviceName.isEmpty()) {
                    pw.print(" (");
                    pw.print(deviceName);
                    pw.print(")");
                }
                pw.print(": ");
                int index = (this.mIndexMap.valueAt(i) + 5) / 10;
                pw.print(index);
            }
            pw.println();
            pw.print("   Devices: ");
            int devices = AudioService.this.getDevicesForStream(this.mStreamType);
            int i2 = 0;
            while (true) {
                int device2 = 1 << i2;
                if (device2 != 1073741824) {
                    if ((devices & device2) != 0) {
                        int n2 = n + 1;
                        if (n > 0) {
                            pw.print(", ");
                        }
                        pw.print(AudioSystem.getOutputDeviceName(device2));
                        n = n2;
                    }
                    i2++;
                } else {
                    return;
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class AudioSystemThread extends Thread {
        AudioSystemThread() {
            super("AudioService");
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            Looper.prepare();
            synchronized (AudioService.this) {
                AudioService.this.mAudioHandler = new AudioHandler();
                AudioService.this.notify();
            }
            Looper.loop();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setDeviceVolume(VolumeStreamState streamState, int device) {
        synchronized (VolumeStreamState.class) {
            streamState.applyDeviceVolume_syncVSS(device);
            int numStreamTypes = AudioSystem.getNumStreamTypes();
            for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
                if (streamType != 12 && streamType != 11 && streamType != streamState.mStreamType && mStreamVolumeAlias[streamType] == streamState.mStreamType) {
                    int streamDevice = getDeviceForStream(streamType);
                    if (device != streamDevice && this.mAvrcpAbsVolSupported && (device & 896) != 0) {
                        this.mStreamStates[streamType].applyDeviceVolume_syncVSS(device);
                    }
                    this.mStreamStates[streamType].applyDeviceVolume_syncVSS(streamDevice);
                    if (Rune.SEC_AUDIO_MULTI_SOUND && this.mMultiSoundManager.isEnabled() && device != streamDevice) {
                        this.mStreamStates[streamType].applyDeviceVolume_syncVSS(device);
                    }
                }
            }
        }
        sendMsg(this.mAudioHandler, 1, 2, device, 0, streamState, 500);
        if (streamState.mStreamType == 3) {
            this.mFineMediaVolume.applyFineVolume(device);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class AudioHandler extends Handler {
        private AudioHandler() {
        }

        private void setAllVolumes(VolumeStreamState streamState) {
            streamState.applyAllVolumes();
            int numStreamTypes = AudioSystem.getNumStreamTypes();
            for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
                if (streamType != streamState.mStreamType && AudioService.mStreamVolumeAlias[streamType] == streamState.mStreamType) {
                    AudioService.this.mStreamStates[streamType].applyAllVolumes();
                }
            }
        }

        private void persistVolume(VolumeStreamState streamState, int device) {
            if (!AudioService.this.mUseFixedVolume) {
                if ((!AudioService.this.mIsSingleVolume || streamState.mStreamType == 3) && streamState.hasValidSettingsName()) {
                    int volume = (streamState.getIndex(device) + 5) / 10;
                    if (streamState.mStreamType == 1) {
                        if (device == 2) {
                            AudioService.sendMsg(AudioService.this.mAudioHandler, 114, 2, volume, 0, "persist.audio.sysvolume", 0);
                        } else if (device == 4) {
                            AudioService.sendMsg(AudioService.this.mAudioHandler, 114, 2, volume, 0, "persist.audio.headsetsysvolume", 0);
                        } else if (device == 8) {
                            AudioService.sendMsg(AudioService.this.mAudioHandler, 114, 2, volume, 0, "persist.audio.hphonesysvolume", 0);
                        }
                    }
                    if (AudioService.this.mA2dp != null && AudioService.this.mA2dp.semIsDualPlayMode()) {
                        AudioService.this.makeBTVolumeSame(AudioService.this.m2ndBTAddr, AudioService.this.m1stBTAddr);
                    }
                    String settingName = streamState.getSettingNameForDevice(device);
                    if (device == 128 && streamState.mStreamType == 3 && settingName != null) {
                        String address = settingName.substring(settingName.lastIndexOf(95) + 1, settingName.length());
                        if (!AudioService.this.mBTAddress.equals(address)) {
                            AudioService.this.updateBluetoothVolume(address, true);
                        }
                    }
                    Settings.System.putIntForUser(AudioService.this.mContentResolver, settingName, (streamState.getIndex(device) + 5) / 10, -2);
                }
            }
        }

        private void persistRingerMode(int ringerMode) {
            if (!AudioService.this.mUseFixedVolume) {
                Settings.Global.putInt(AudioService.this.mContentResolver, "mode_ringer", ringerMode);
            }
        }

        private String getSoundEffectFilePath(int effectType) {
            String filePath = Environment.getProductDirectory() + AudioService.SOUND_EFFECTS_PATH + ((String) AudioService.SOUND_EFFECT_FILES.get(AudioService.this.SOUND_EFFECT_FILES_MAP[effectType][0]));
            if (!new File(filePath).isFile()) {
                return Environment.getRootDirectory() + AudioService.SOUND_EFFECTS_PATH + ((String) AudioService.SOUND_EFFECT_FILES.get(AudioService.this.SOUND_EFFECT_FILES_MAP[effectType][0]));
            }
            return filePath;
        }

        private boolean onLoadSoundEffects() {
            int i;
            int status;
            synchronized (AudioService.this.mSoundEffectsLock) {
                if (!AudioService.this.mSystemReady) {
                    Log.w("AudioService", "onLoadSoundEffects() called before boot complete");
                    return false;
                } else if (AudioService.this.mSoundPool != null) {
                    return true;
                } else {
                    AudioService.this.loadTouchSoundAssets();
                    AudioService.this.mSoundPool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder().setUsage(13).setContentType(4).build()).build();
                    AudioService.this.mSoundPoolCallBack = null;
                    AudioService.this.mSoundPoolListenerThread = new SoundPoolListenerThread();
                    AudioService.this.mSoundPoolListenerThread.start();
                    int attempts = 3;
                    while (true) {
                        if (AudioService.this.mSoundPoolCallBack != null) {
                            break;
                        }
                        int attempts2 = attempts - 1;
                        if (attempts <= 0) {
                            attempts = attempts2;
                            break;
                        }
                        try {
                            AudioService.this.mSoundEffectsLock.wait(5000L);
                        } catch (InterruptedException e) {
                            Log.w("AudioService", "Interrupted while waiting sound pool listener thread.");
                        }
                        attempts = attempts2;
                    }
                    if (AudioService.this.mSoundPoolCallBack == null) {
                        Log.w("AudioService", "onLoadSoundEffects() SoundPool listener or thread creation error");
                        if (AudioService.this.mSoundPoolLooper != null) {
                            AudioService.this.mSoundPoolLooper.quit();
                            AudioService.this.mSoundPoolLooper = null;
                        }
                        AudioService.this.mSoundPoolListenerThread = null;
                        AudioService.this.mSoundPool.release();
                        AudioService.this.mSoundPool = null;
                        return false;
                    }
                    int[] poolId = new int[AudioService.SOUND_EFFECT_FILES.size()];
                    int fileIdx = 0;
                    while (true) {
                        i = -1;
                        if (fileIdx >= AudioService.SOUND_EFFECT_FILES.size()) {
                            break;
                        }
                        poolId[fileIdx] = -1;
                        fileIdx++;
                    }
                    int numSamples = 0;
                    int numSamples2 = 0;
                    while (numSamples2 < 14) {
                        if (AudioService.this.SOUND_EFFECT_FILES_MAP[numSamples2][1] != 0) {
                            if (poolId[AudioService.this.SOUND_EFFECT_FILES_MAP[numSamples2][0]] == i) {
                                String filePath = AudioService.this.mThemeTouchSoundPath != null ? AudioService.this.mThemeTouchSoundPath + ((String) AudioService.SOUND_EFFECT_FILES.get(AudioService.this.SOUND_EFFECT_FILES_MAP[numSamples2][0])) : getSoundEffectFilePath(numSamples2);
                                int sampleId = AudioService.this.mSoundPool.load(filePath, 0);
                                if (AudioService.this.mThemeTouchSoundPath != null && sampleId <= 0) {
                                    filePath = Environment.getRootDirectory() + AudioService.SOUND_EFFECTS_PATH + ((String) AudioService.SOUND_EFFECT_FILES.get(AudioService.this.SOUND_EFFECT_FILES_MAP[numSamples2][0]));
                                    sampleId = AudioService.this.mSoundPool.load(filePath, 0);
                                }
                                if (sampleId <= 0) {
                                    Log.w("AudioService", "Soundpool could not load file: " + filePath);
                                } else {
                                    AudioService.this.SOUND_EFFECT_FILES_MAP[numSamples2][1] = sampleId;
                                    poolId[AudioService.this.SOUND_EFFECT_FILES_MAP[numSamples2][0]] = sampleId;
                                    numSamples++;
                                }
                            } else {
                                AudioService.this.SOUND_EFFECT_FILES_MAP[numSamples2][1] = poolId[AudioService.this.SOUND_EFFECT_FILES_MAP[numSamples2][0]];
                            }
                        }
                        numSamples2++;
                        i = -1;
                    }
                    if (numSamples > 0) {
                        AudioService.this.mSoundPoolCallBack.setSamples(poolId);
                        int attempts3 = 3;
                        int status2 = 1;
                        while (true) {
                            status = status2;
                            if (status != 1) {
                                break;
                            }
                            int attempts4 = attempts3 - 1;
                            if (attempts3 <= 0) {
                                break;
                            }
                            try {
                                AudioService.this.mSoundEffectsLock.wait(5000L);
                                status2 = AudioService.this.mSoundPoolCallBack.status();
                            } catch (InterruptedException e2) {
                                Log.w("AudioService", "Interrupted while waiting sound pool callback.");
                                status2 = status;
                            }
                            attempts3 = attempts4;
                        }
                    } else {
                        status = -1;
                    }
                    int attempts5 = status;
                    if (AudioService.this.mSoundPoolLooper != null) {
                        AudioService.this.mSoundPoolLooper.quit();
                        AudioService.this.mSoundPoolLooper = null;
                    }
                    AudioService.this.mSoundPoolListenerThread = null;
                    if (attempts5 != 0) {
                        Log.w("AudioService", "onLoadSoundEffects(), Error " + attempts5 + " while loading samples");
                        for (int effect = 0; effect < 14; effect++) {
                            if (AudioService.this.SOUND_EFFECT_FILES_MAP[effect][1] > 0) {
                                AudioService.this.SOUND_EFFECT_FILES_MAP[effect][1] = -1;
                            }
                        }
                        AudioService.this.mSoundPool.release();
                        AudioService.this.mSoundPool = null;
                    }
                    return attempts5 == 0;
                }
            }
        }

        private void onUnloadSoundEffects() {
            synchronized (AudioService.this.mSoundEffectsLock) {
                if (AudioService.this.mSoundPool == null) {
                    return;
                }
                int[] poolId = new int[AudioService.SOUND_EFFECT_FILES.size()];
                for (int fileIdx = 0; fileIdx < AudioService.SOUND_EFFECT_FILES.size(); fileIdx++) {
                    poolId[fileIdx] = 0;
                }
                for (int effect = 0; effect < 14; effect++) {
                    if (AudioService.this.SOUND_EFFECT_FILES_MAP[effect][1] > 0 && poolId[AudioService.this.SOUND_EFFECT_FILES_MAP[effect][0]] == 0) {
                        AudioService.this.mSoundPool.unload(AudioService.this.SOUND_EFFECT_FILES_MAP[effect][1]);
                        AudioService.this.SOUND_EFFECT_FILES_MAP[effect][1] = -1;
                        poolId[AudioService.this.SOUND_EFFECT_FILES_MAP[effect][0]] = -1;
                    }
                }
                AudioService.this.mSoundPool.release();
                AudioService.this.mSoundPool = null;
            }
        }

        private void onPlaySoundEffect(int effectType, int volume) {
            String filePath;
            synchronized (AudioService.this.mSoundEffectsLock) {
                onLoadSoundEffects();
                if (AudioService.this.mSoundPool == null) {
                    return;
                }
                float volFloat = AudioService.this.getSoundEffectVolume(effectType);
                if (AudioService.this.SOUND_EFFECT_FILES_MAP[effectType][1] > 0) {
                    AudioService.this.mSoundPool.semSetStreamType(1);
                    if (effectType == AudioManager.getPlaySoundTypeForSEP(101)) {
                        int streamType = 2;
                        if (Rune.SEC_AUDIO_DEFAULT_STREAM_NOTIFICATION) {
                            streamType = 5;
                        }
                        AudioService.this.mSoundPool.semSetStreamType(streamType);
                    }
                    AudioService.this.mSoundPool.play(AudioService.this.SOUND_EFFECT_FILES_MAP[effectType][1], volFloat, volFloat, 0, 0, 1.0f);
                } else {
                    MediaPlayer mediaPlayer = new MediaPlayer();
                    try {
                        try {
                            if (AudioService.this.mThemeTouchSoundPath != null) {
                                filePath = AudioService.this.mThemeTouchSoundPath + ((String) AudioService.SOUND_EFFECT_FILES.get(AudioService.this.SOUND_EFFECT_FILES_MAP[effectType][0]));
                            } else {
                                filePath = getSoundEffectFilePath(effectType);
                            }
                            mediaPlayer.setDataSource(filePath);
                            mediaPlayer.setAudioStreamType(1);
                            mediaPlayer.prepare();
                            mediaPlayer.setVolume(volFloat);
                            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { // from class: com.android.server.audio.AudioService.AudioHandler.1
                                @Override // android.media.MediaPlayer.OnCompletionListener
                                public void onCompletion(MediaPlayer mp) {
                                    AudioHandler.this.cleanupPlayer(mp);
                                }
                            });
                            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() { // from class: com.android.server.audio.AudioService.AudioHandler.2
                                @Override // android.media.MediaPlayer.OnErrorListener
                                public boolean onError(MediaPlayer mp, int what, int extra) {
                                    AudioHandler.this.cleanupPlayer(mp);
                                    return true;
                                }
                            });
                            mediaPlayer.start();
                        } catch (IllegalArgumentException ex) {
                            Log.w("AudioService", "MediaPlayer IllegalArgumentException: " + ex);
                        } catch (IllegalStateException ex2) {
                            Log.w("AudioService", "MediaPlayer IllegalStateException: " + ex2);
                        }
                    } catch (IOException ex3) {
                        Log.w("AudioService", "MediaPlayer IOException: " + ex3);
                    }
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void cleanupPlayer(MediaPlayer mp) {
            if (mp != null) {
                try {
                    mp.stop();
                    mp.release();
                } catch (IllegalStateException ex) {
                    Log.w("AudioService", "MediaPlayer IllegalStateException: " + ex);
                }
            }
        }

        private void setForceUse(int usage, int config, String eventSource) {
            synchronized (AudioService.this.mConnectedDevices) {
                AudioService.this.setForceUseInt_SyncDevices(usage, config, eventSource);
            }
        }

        private void onPersistSafeVolumeState(int state) {
            Settings.Global.putInt(AudioService.this.mContentResolver, "audio_safe_volume_state", state);
        }

        private void onNotifyVolumeEvent(IAudioPolicyCallback apc, int direction) {
            try {
                apc.notifyVolumeAdjust(direction);
            } catch (Exception e) {
            }
        }

        /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            AudioRoutesInfo routes;
            int i;
            int i2 = msg.what;
            switch (i2) {
                case 0:
                    AudioService.this.setDeviceVolume((VolumeStreamState) msg.obj, msg.arg1);
                    return;
                case 1:
                    persistVolume((VolumeStreamState) msg.obj, msg.arg1);
                    return;
                default:
                    switch (i2) {
                        case 3:
                            persistRingerMode(AudioService.this.getRingerModeInternal());
                            return;
                        case 4:
                            AudioService.this.onAudioServerDied();
                            return;
                        case 5:
                            onPlaySoundEffect(msg.arg1, msg.arg2);
                            return;
                        default:
                            switch (i2) {
                                case 7:
                                    boolean loaded = onLoadSoundEffects();
                                    if (msg.obj != null) {
                                        LoadSoundEffectReply reply = (LoadSoundEffectReply) msg.obj;
                                        synchronized (reply) {
                                            reply.mStatus = loaded ? 0 : -1;
                                            reply.notify();
                                        }
                                        return;
                                    }
                                    return;
                                case 8:
                                    break;
                                case 9:
                                    AudioService.this.resetBluetoothSco();
                                    return;
                                case 10:
                                    setAllVolumes((VolumeStreamState) msg.obj);
                                    return;
                                default:
                                    switch (i2) {
                                        case 12:
                                            int N = AudioService.this.mRoutesObservers.beginBroadcast();
                                            Log.i("AudioService", "dispatchAudioRoutesChanged, N = " + N);
                                            if (N > 0) {
                                                synchronized (AudioService.this.mCurAudioRoutes) {
                                                    routes = new AudioRoutesInfo(AudioService.this.mCurAudioRoutes);
                                                }
                                                while (N > 0) {
                                                    N--;
                                                    IAudioRoutesObserver obs = AudioService.this.mRoutesObservers.getBroadcastItem(N);
                                                    try {
                                                        obs.dispatchAudioRoutesChanged(routes);
                                                    } catch (RemoteException e) {
                                                        Log.w("AudioService", "dispatchAudioRoutesChanged is failed", e);
                                                    }
                                                }
                                            }
                                            AudioService.this.mRoutesObservers.finishBroadcast();
                                            AudioService.this.observeDevicesForStreams(-1);
                                            return;
                                        case 13:
                                            break;
                                        case 14:
                                            AudioService.this.onCheckMusicActive((String) msg.obj);
                                            return;
                                        case 15:
                                            AudioService.this.onSendBecomingNoisyIntent(msg.arg1);
                                            return;
                                        case 16:
                                        case 17:
                                            AudioService.this.onConfigureSafeVolume(msg.what == 17, (String) msg.obj);
                                            return;
                                        case 18:
                                            onPersistSafeVolumeState(msg.arg1);
                                            return;
                                        case 19:
                                            AudioService.this.onBroadcastScoConnectionState(msg.arg1);
                                            return;
                                        case 20:
                                            onUnloadSoundEffects();
                                            return;
                                        case 21:
                                            AudioService.this.onSystemReady();
                                            return;
                                        case 22:
                                            int musicActiveMs = msg.arg1;
                                            Settings.Secure.putIntForUser(AudioService.this.mContentResolver, "unsafe_volume_music_active_ms", musicActiveMs, -2);
                                            return;
                                        default:
                                            switch (i2) {
                                                case 24:
                                                    AudioService.this.onUnmuteStream(msg.arg1, msg.arg2);
                                                    return;
                                                case 25:
                                                    AudioService.this.onDynPolicyMixStateUpdate((String) msg.obj, msg.arg1);
                                                    return;
                                                case 26:
                                                    AudioService.this.onIndicateSystemReady();
                                                    return;
                                                case 27:
                                                    AudioService.this.onAccessoryPlugMediaUnmute(msg.arg1);
                                                    return;
                                                case 28:
                                                    onNotifyVolumeEvent((IAudioPolicyCallback) msg.obj, msg.arg1);
                                                    return;
                                                case 29:
                                                    AudioService.this.onDispatchAudioServerStateChange(msg.arg1 == 1);
                                                    return;
                                                case 30:
                                                    AudioService.this.onEnableSurroundFormats((ArrayList) msg.obj);
                                                    return;
                                                default:
                                                    switch (i2) {
                                                        case 100:
                                                            WiredDeviceConnectionState connectState = (WiredDeviceConnectionState) msg.obj;
                                                            AudioService.this.mWiredDevLogger.log(new AudioServiceEvents.WiredDevConnectEvent(connectState));
                                                            if ("h2w-before-boot-completed".equals(connectState.mName)) {
                                                                if (!AudioService.this.mSystemReady && ((i = connectState.mType) == 4 || i == 8)) {
                                                                    AudioService.this.onSetWiredDeviceConnectionState(connectState.mType, connectState.mState, connectState.mAddress, "h2w", connectState.mCaller);
                                                                    AudioService.this.mLateBootCompleted_WiredDevice = connectState.mType;
                                                                }
                                                            } else {
                                                                AudioService.this.onSetWiredDeviceConnectionState(connectState.mType, connectState.mState, connectState.mAddress, connectState.mName, connectState.mCaller);
                                                            }
                                                            AudioService.sendMsg(AudioService.this.mAudioHandler, 124, 1, 0, 0, null, 0);
                                                            if (AudioService.this.mAudioEventWakeLock.isHeld()) {
                                                                AudioService.this.mAudioEventWakeLock.release();
                                                                return;
                                                            }
                                                            return;
                                                        case 101:
                                                            AudioService.this.onSetA2dpSourceConnectionState((BluetoothDevice) msg.obj, msg.arg1);
                                                            if (AudioService.this.mAudioEventWakeLock.isHeld()) {
                                                                AudioService.this.mAudioEventWakeLock.release();
                                                                return;
                                                            }
                                                            return;
                                                        case 102:
                                                            AudioService.this.onSetA2dpSinkConnectionState((BluetoothDevice) msg.obj, msg.arg1, msg.arg2);
                                                            if (AudioService.this.mAudioEventWakeLock.isHeld()) {
                                                                AudioService.this.mAudioEventWakeLock.release();
                                                                return;
                                                            }
                                                            return;
                                                        case 103:
                                                            AudioService.this.onBluetoothA2dpDeviceConfigChange((BluetoothDevice) msg.obj);
                                                            if (AudioService.this.mAudioEventWakeLock.isHeld()) {
                                                                AudioService.this.mAudioEventWakeLock.release();
                                                                return;
                                                            }
                                                            return;
                                                        case 104:
                                                            AudioService.this.mPlaybackMonitor.disableAudioForUid(msg.arg1 == 1, msg.arg2);
                                                            if (AudioService.this.mAudioEventWakeLock.isHeld()) {
                                                                AudioService.this.mAudioEventWakeLock.release();
                                                                return;
                                                            }
                                                            return;
                                                        case 105:
                                                            AudioService.this.onSetHearingAidConnectionState((BluetoothDevice) msg.obj, msg.arg1);
                                                            if (AudioService.this.mAudioEventWakeLock.isHeld()) {
                                                                AudioService.this.mAudioEventWakeLock.release();
                                                                return;
                                                            }
                                                            return;
                                                        case 106:
                                                            synchronized (AudioService.this.mConnectedDevices) {
                                                                AudioService.this.makeA2dpDeviceUnavailableNow((String) msg.obj);
                                                            }
                                                            if (AudioService.this.mAudioEventWakeLock.isHeld()) {
                                                                AudioService.this.mAudioEventWakeLock.release();
                                                                return;
                                                            }
                                                            return;
                                                        default:
                                                            AudioService.this.handleCustomMessage(msg);
                                                            return;
                                                    }
                                            }
                                    }
                            }
                            setForceUse(msg.arg1, msg.arg2, (String) msg.obj);
                            AudioService.sendMsg(AudioService.this.mAudioHandler, 124, 1, 0, 0, null, 0);
                            return;
                    }
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    private class SettingsObserver extends ContentObserver {
        SettingsObserver() {
            super(new Handler());
            AudioService.this.mContentResolver.registerContentObserver(Settings.Global.getUriFor("zen_mode"), false, this);
            AudioService.this.mContentResolver.registerContentObserver(Settings.Global.getUriFor("zen_mode_config_etag"), false, this);
            AudioService.this.mContentResolver.registerContentObserver(Settings.System.getUriFor("mode_ringer_streams_affected"), false, this);
            AudioService.this.mContentResolver.registerContentObserver(Settings.Global.getUriFor("dock_audio_media_enabled"), false, this);
            AudioService.this.mContentResolver.registerContentObserver(Settings.System.getUriFor("master_mono"), false, this);
            AudioService.this.mEncodedSurroundMode = Settings.Global.getInt(AudioService.this.mContentResolver, "encoded_surround_output", 0);
            AudioService.this.mContentResolver.registerContentObserver(Settings.Global.getUriFor("encoded_surround_output"), false, this);
            AudioService.this.mEnabledSurroundFormats = Settings.Global.getString(AudioService.this.mContentResolver, "encoded_surround_output_enabled_formats");
            AudioService.this.mContentResolver.registerContentObserver(Settings.Global.getUriFor("encoded_surround_output_enabled_formats"), false, this);
            AudioService.this.registerCustomObserver(this);
        }

        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            synchronized (AudioService.this.mSettingsLock) {
                if (AudioService.this.updateRingerAndZenModeAffectedStreams()) {
                    AudioService.this.setRingerModeInt(AudioService.this.getRingerModeInternal(), false);
                }
                AudioService.this.readDockAudioSettings(AudioService.this.mContentResolver);
                AudioService.this.updateMasterMono(AudioService.this.mContentResolver);
                updateEncodedSurroundOutput();
                AudioService.this.sendEnabledSurroundFormats(AudioService.this.mContentResolver, AudioService.this.mSurroundModeChanged);
            }
            AudioService.this.updateCustomSettings(AudioService.this.mContentResolver);
        }

        private void updateEncodedSurroundOutput() {
            int newSurroundMode = Settings.Global.getInt(AudioService.this.mContentResolver, "encoded_surround_output", 0);
            if (AudioService.this.mEncodedSurroundMode != newSurroundMode) {
                AudioService.this.sendEncodedSurroundMode(newSurroundMode, "SettingsObserver");
                synchronized (AudioService.this.mConnectedDevices) {
                    String key = AudioService.this.makeDeviceListKey(1024, "");
                    DeviceListSpec deviceSpec = (DeviceListSpec) AudioService.this.mConnectedDevices.get(key);
                    if (deviceSpec != null) {
                        AudioService.this.setWiredDeviceConnectionState(1024, 0, "", "", PackageManagerService.PLATFORM_PACKAGE_NAME);
                        AudioService.this.setWiredDeviceConnectionState(1024, 1, "", "", PackageManagerService.PLATFORM_PACKAGE_NAME);
                    }
                }
                AudioService.this.mEncodedSurroundMode = newSurroundMode;
                AudioService.this.mSurroundModeChanged = true;
                return;
            }
            AudioService.this.mSurroundModeChanged = false;
        }
    }

    private void makeA2dpDeviceAvailable(String address, String name, String eventSource) {
        VolumeStreamState volumeStreamState = this.mStreamStates[3];
        setBluetoothA2dpOnInt(true, eventSource);
        AudioSystem.setDeviceConnectionState(128, 1, address, name);
        AudioSystem.setParameters("A2dpSuspended=false");
        this.mConnectedDevices.put(makeDeviceListKey(128, address), new DeviceListSpec(128, name, address));
        sendMsg(this.mAudioHandler, 27, 2, 128, 0, null, 0);
        this.mLastConnectedDevice = new DeviceListSpec(128, name, address);
    }

    private void onSendBecomingNoisyIntent() {
        sendBroadcastToAll(new Intent("android.media.AUDIO_BECOMING_NOISY"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void makeA2dpDeviceUnavailableNow(String address) {
        if (address == null) {
            return;
        }
        synchronized (this.mA2dpAvrcpLock) {
        }
        AudioSystem.setDeviceConnectionState(128, 0, address, "");
        this.mConnectedDevices.remove(makeDeviceListKey(128, address));
        if (!checkDeviceConnected(128)) {
            setCurrentAudioRouteName(null);
        }
        if (this.mDockAddress == address) {
            this.mDockAddress = null;
        }
        if (this.mLastConnectedDevice != null && this.mLastConnectedDevice.mDeviceType == 128 && this.mLastConnectedDevice.mDeviceAddress.equals(address)) {
            this.mLastConnectedDevice = null;
        }
        if (!checkDeviceConnected(128) && Rune.SEC_AUDIO_FM_RADIO_BT && this.mForcedUseForFMRadio == 4) {
            setRadioOutputPath(3);
        }
    }

    private void makeA2dpDeviceUnavailableLater(String address, int delayMs) {
        AudioSystem.setParameters("A2dpSuspended=true");
        this.mConnectedDevices.remove(makeDeviceListKey(128, address));
        queueMsgUnderWakeLock(this.mAudioHandler, 106, 0, 0, address, delayMs);
    }

    private void makeA2dpSrcAvailable(String address) {
        AudioSystem.setDeviceConnectionState(-2147352576, 1, address, "");
        this.mConnectedDevices.put(makeDeviceListKey(-2147352576, address), new DeviceListSpec(-2147352576, "", address));
    }

    private void makeA2dpSrcUnavailable(String address) {
        AudioSystem.setDeviceConnectionState(-2147352576, 0, address, "");
        this.mConnectedDevices.remove(makeDeviceListKey(-2147352576, address));
    }

    private void setHearingAidVolume(int index, int streamType) {
        synchronized (this.mHearingAidLock) {
            if (this.mHearingAid != null) {
                int gainDB = (int) AudioSystem.getStreamVolumeDB(streamType, index / 10, 134217728);
                if (gainDB < -128) {
                    gainDB = -128;
                }
                this.mHearingAid.setVolume(gainDB);
            }
        }
    }

    private void makeHearingAidDeviceAvailable(String address, String name, String eventSource) {
        int index = this.mStreamStates[3].getIndex(134217728);
        setHearingAidVolume(index, 3);
        AudioSystem.setDeviceConnectionState(134217728, 1, address, name);
        this.mConnectedDevices.put(makeDeviceListKey(134217728, address), new DeviceListSpec(134217728, name, address));
        sendMsg(this.mAudioHandler, 27, 2, 134217728, 0, null, 0);
    }

    private void makeHearingAidDeviceUnavailable(String address) {
        AudioSystem.setDeviceConnectionState(134217728, 0, address, "");
        this.mConnectedDevices.remove(makeDeviceListKey(134217728, address));
        setCurrentAudioRouteName(null);
    }

    private void cancelA2dpDeviceTimeout() {
        this.mAudioHandler.removeMessages(106);
    }

    private boolean hasScheduledA2dpDockTimeout() {
        return this.mAudioHandler.hasMessages(106);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onSetA2dpSinkConnectionState(BluetoothDevice btDevice, int state, int a2dpVolume) {
        Log.d("AudioService", "onSetA2dpSinkConnectionState btDevice= " + btDevice + " state= " + state + " is dock: " + btDevice.isBluetoothDock());
        if (btDevice == null) {
            return;
        }
        String address = btDevice.getAddress();
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            address = "";
        }
        int preVolIndex = this.mStreamStates[3].getIndex(getDeviceForStream(3));
        synchronized (this.mConnectedDevices) {
            String key = makeDeviceListKey(128, btDevice.getAddress());
            DeviceListSpec deviceSpec = this.mConnectedDevices.get(key);
            boolean isConnected = deviceSpec != null;
            if (isConnected && state != 2) {
                if (btDevice.isBluetoothDock()) {
                    if (state == 0) {
                        makeA2dpDeviceUnavailableLater(address, 8000);
                    }
                } else {
                    makeA2dpDeviceUnavailableNow(address);
                }
                if (!checkDeviceConnected(128)) {
                    setCurrentAudioRouteName(null);
                }
            } else if (!isConnected && state == 2) {
                if (btDevice.isBluetoothDock()) {
                    cancelA2dpDeviceTimeout();
                    this.mDockAddress = address;
                } else if (hasScheduledA2dpDockTimeout() && this.mDockAddress != null) {
                    cancelA2dpDeviceTimeout();
                    makeA2dpDeviceUnavailableNow(this.mDockAddress);
                }
                if (a2dpVolume != -1) {
                    VolumeStreamState streamState = this.mStreamStates[3];
                    streamState.setIndex(a2dpVolume * 10, 128, "onSetA2dpSinkConnectionState");
                    setDeviceVolume(streamState, 128);
                }
                makeA2dpDeviceAvailable(address, btDevice.getName(), "onSetA2dpSinkConnectionState");
                setCurrentAudioRouteName(btDevice.getAliasName());
            }
        }
        checkAndSendEarCareInfo();
        sendVolumeUpdate(3, preVolIndex, this.mStreamStates[3].getIndex(getDeviceForStream(3)), getFlagsForDeviceConnect(state));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onSetA2dpSourceConnectionState(BluetoothDevice btDevice, int state) {
        Log.d("AudioService", "onSetA2dpSourceConnectionState btDevice=" + btDevice + " state=" + state);
        if (btDevice == null) {
            return;
        }
        String address = btDevice.getAddress();
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            address = "";
        }
        synchronized (this.mConnectedDevices) {
            String key = makeDeviceListKey(-2147352576, address);
            DeviceListSpec deviceSpec = this.mConnectedDevices.get(key);
            boolean isConnected = deviceSpec != null;
            if (isConnected && state != 2) {
                makeA2dpSrcUnavailable(address);
            } else if (!isConnected && state == 2) {
                makeA2dpSrcAvailable(address);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onSetHearingAidConnectionState(BluetoothDevice btDevice, int state) {
        Log.d("AudioService", "onSetHearingAidConnectionState btDevice=" + btDevice + ", state=" + state);
        if (btDevice == null) {
            return;
        }
        String address = btDevice.getAddress();
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            address = "";
        }
        synchronized (this.mConnectedDevices) {
            String key = makeDeviceListKey(134217728, btDevice.getAddress());
            DeviceListSpec deviceSpec = this.mConnectedDevices.get(key);
            boolean isConnected = deviceSpec != null;
            if (isConnected && state != 2) {
                makeHearingAidDeviceUnavailable(address);
                setCurrentAudioRouteName(null);
            } else if (!isConnected && state == 2) {
                makeHearingAidDeviceAvailable(address, btDevice.getName(), "onSetHearingAidConnectionState");
                setCurrentAudioRouteName(btDevice.getAliasName());
            }
        }
    }

    private void setCurrentAudioRouteName(String name) {
        synchronized (this.mCurAudioRoutes) {
            if (!TextUtils.equals(this.mCurAudioRoutes.bluetoothName, name)) {
                this.mCurAudioRoutes.bluetoothName = name;
                sendMsg(this.mAudioHandler, 12, 1, 0, 0, null, 0);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onBluetoothA2dpDeviceConfigChange(BluetoothDevice btDevice) {
        Log.d("AudioService", "onBluetoothA2dpDeviceConfigChange btDevice=" + btDevice);
        if (btDevice == null) {
            return;
        }
        String address = btDevice.getAddress();
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            address = "";
        }
        synchronized (this.mConnectedDevices) {
            if (this.mAudioHandler.hasMessages(102, btDevice)) {
                return;
            }
            String key = makeDeviceListKey(128, address);
            DeviceListSpec deviceSpec = this.mConnectedDevices.get(key);
            if (deviceSpec != null) {
                int musicDevice = getDeviceForStream(3);
                if (AudioSystem.handleDeviceConfigChange(128, address, btDevice.getName()) != 0) {
                    setBluetoothA2dpDeviceConnectionStateInt(btDevice, 0, 2, false, musicDevice, -1);
                }
                if (Rune.SEC_AUDIO_FM_RADIO_BT && this.mForcedUseForFMRadio == 4) {
                    AudioSystem.setForceUse(8, 4);
                }
            }
        }
    }

    public void avrcpSupportsAbsoluteVolume(String address, boolean support) {
        synchronized (this.mA2dpAvrcpLock) {
            this.mAvrcpAbsVolSupported = support;
            sendMsg(this.mAudioHandler, 0, 2, 128, 0, this.mStreamStates[3], 0);
        }
    }

    private boolean handleDeviceConnection(boolean connect, int device, String address, String deviceName) {
        Slog.i("AudioService", "handleDeviceConnection(" + connect + " dev:" + Integer.toHexString(device) + " address:" + address + " name:" + deviceName + ")");
        synchronized (this.mConnectedDevices) {
            String deviceKey = makeDeviceListKey(device, address);
            Slog.i("AudioService", "deviceKey:" + deviceKey);
            DeviceListSpec deviceSpec = this.mConnectedDevices.get(deviceKey);
            boolean isConnected = deviceSpec != null;
            Slog.i("AudioService", "deviceSpec:" + deviceSpec + " is(already)Connected:" + isConnected);
            Log.d("AudioService", "connect:" + connect + " device:" + device + " isConnected:" + isConnected);
            this.mSoundEffectVolume = -1.0f;
            if (connect && !isConnected) {
                int res = AudioSystem.setDeviceConnectionState(device, 1, address, deviceName);
                if (res != 0) {
                    Slog.w("AudioService", "retry: not connecting device 0x" + Integer.toHexString(device) + " due to command error " + res);
                    AudioSystem.setDeviceConnectionState(device, 0, address, deviceName);
                    res = AudioSystem.setDeviceConnectionState(device, 1, address, deviceName);
                }
                int res2 = res;
                if (res2 == 0) {
                    this.mConnectedDevices.put(deviceKey, new DeviceListSpec(device, deviceName, address));
                    sendMsg(this.mAudioHandler, 27, 2, device, 0, null, 0);
                    if ((Integer.MIN_VALUE & device) == 0) {
                        this.mLastConnectedDevice = new DeviceListSpec(device, deviceName, address);
                    }
                    wakeUpDeviceByWiredHeadset(device);
                    if (device == 1024) {
                        boolean isAudioOutputToDisplay = DesktopModeSettings.getSettings(this.mContentResolver, DesktopModeSettings.SETTINGS_KEY_AUDIO_OUTPUT_TO_DISPLAY, false);
                        DesktopModeManagerInternal service = (DesktopModeManagerInternal) LocalServices.getService(DesktopModeManagerInternal.class);
                        if (service.isDesktopModeAvailableEx(false, true) && !isAudioOutputToDisplay) {
                            this.mDexState = true;
                            AudioSystem.setPolicyParameters("DEXstate=" + this.mDexState);
                            Log.i("AudioService", "The dex mode is available. " + this.mDexState);
                        }
                    }
                    return true;
                }
                Slog.e("AudioService", "not connecting device 0x" + Integer.toHexString(device) + " due to command error " + res2);
                return false;
            } else if (!connect && isConnected) {
                AudioSystem.setDeviceConnectionState(device, 0, address, deviceName);
                this.mConnectedDevices.remove(deviceKey);
                if (this.mLastConnectedDevice != null && this.mLastConnectedDevice.toString().equals(deviceSpec.toString())) {
                    this.mLastConnectedDevice = null;
                }
                return true;
            } else {
                if (this.mSelfiestickIsConnected && device == 4) {
                    this.mSelfiestickIsConnected = false;
                    Log.i("AudioService", "selfiestick is disconnected");
                }
                Log.w("AudioService", "handleDeviceConnection() failed, deviceKey=" + deviceKey + ", deviceSpec=" + deviceSpec + ", connect=" + connect);
                return false;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int checkSendBecomingNoisyIntent(int device, int state, int musicDevice) {
        int delay;
        int delay2 = 0;
        if (state == 0 && (this.mBecomingNoisyIntentDevices & device) != 0) {
            int outDevice = getCurOutDevice();
            Log.i("AudioService", "checkSendBecomingNoisyIntent device : " + device + ", getCurOutDevice() : " + outDevice);
            if (outDevice != -1) {
                if (AudioSystem.isStreamActive(7, 0) && ((device == 4 || device == 8) && AudioSystem.getForceUse(1) != 4)) {
                    outDevice = device;
                }
                if ((outDevice & device) != 0 || ((device & 896) != 0 && isInCommunicationAndRing())) {
                    if (!this.mSplitSound || !isInCommunication() || !isWiredHeadset(device)) {
                        sendMsg(this.mAudioHandler, 15, 0, device, 0, null, 0);
                    } else {
                        Log.i("AudioService", "Don't Stop music when removing earphone in SplitSound");
                    }
                    if (device == 1024 && this.mIsFactoryMode) {
                        Log.d("AudioService", "HDMI delay in factory mode =0");
                    } else {
                        if ((device == 4 || device == 8) && isInCommunication()) {
                            delay = 0;
                            Log.d("AudioService", "in call wired headset/headphone delay =0");
                        } else {
                            delay = 500;
                        }
                        delay2 = delay;
                    }
                } else if (Rune.SEC_AUDIO_MULTI_SOUND && isMultiSoundOn() && (AudioSystem.getDevicesForStream(3) & device) != 0) {
                    sendMsg(this.mAudioHandler, 15, 0, device, 0, null, 0);
                }
            }
            if (Rune.SEC_AUDIO_MULTI_SOUND) {
                this.mMultiSoundManager.sendBecomingNoisyToPinnedApp(device);
            }
        }
        return delay2;
    }

    private boolean hasMediaDynamicPolicy() {
        synchronized (this.mAudioPolicies) {
            if (this.mAudioPolicies.isEmpty()) {
                return false;
            }
            Collection<AudioPolicyProxy> appColl = this.mAudioPolicies.values();
            for (AudioPolicyProxy app : appColl) {
                if (app.hasMixAffectingUsage(1)) {
                    return true;
                }
            }
            return false;
        }
    }

    private void updateAudioRoutes(int device, int state) {
        int newConn;
        int connType = 0;
        if (device == 4) {
            connType = 1;
        } else if (device == 8 || device == 131072) {
            connType = 2;
        } else if (device == 1024 || device == 262144) {
            connType = 8;
        } else if (device == 16384 || device == 67108864) {
            connType = 16;
        }
        synchronized (this.mCurAudioRoutes) {
            if (connType != 0) {
                try {
                    int newConn2 = this.mCurAudioRoutes.mainType;
                    if (state != 0) {
                        newConn = newConn2 | connType;
                    } else {
                        newConn = newConn2 & (~connType);
                    }
                    if (newConn != this.mCurAudioRoutes.mainType) {
                        this.mCurAudioRoutes.mainType = newConn;
                        sendMsg(this.mAudioHandler, 12, 1, 0, 0, null, 0);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
    }

    private void sendDeviceConnectionIntent(int device, int state, String address, String deviceName) {
        Slog.i("AudioService", "sendDeviceConnectionIntent(dev:0x" + Integer.toHexString(device) + " state:0x" + Integer.toHexString(state) + " address:" + address + " name:" + deviceName + ");");
        Intent intent = new Intent();
        if (device == 4) {
            intent.setAction("android.intent.action.HEADSET_PLUG");
            intent.putExtra("microphone", 1);
        } else {
            if (device == 8 || device == 131072) {
                intent.setAction("android.intent.action.HEADSET_PLUG");
                intent.putExtra("microphone", 0);
            } else if (device == 67108864) {
                intent.setAction("android.intent.action.HEADSET_PLUG");
                intent.putExtra("microphone", AudioSystem.getDeviceConnectionState(-2113929216, "") == 1 ? 1 : 0);
            } else if (device == -2113929216) {
                if (AudioSystem.getDeviceConnectionState(67108864, "") == 1) {
                    intent.setAction("android.intent.action.HEADSET_PLUG");
                    intent.putExtra("microphone", 1);
                } else {
                    return;
                }
            } else if (device == 1024 || device == 262144) {
                configureHdmiPlugIntent(intent, state);
            }
        }
        if (intent.getAction() == null) {
            return;
        }
        if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER && getMode() == 1) {
            if (state == 1) {
                this.mSensorThread.stopSensor();
            } else {
                this.mSensorThread.startSensor();
            }
        }
        intent.putExtra("state", state);
        intent.putExtra("address", address);
        intent.putExtra(CONNECT_INTENT_KEY_PORT_NAME, deviceName);
        intent.addFlags(PermissionMonitor.APM_FLAG_APM_IS_OFF);
        long ident = Binder.clearCallingIdentity();
        try {
            ActivityManager.broadcastStickyIntent(intent, -1);
            Binder.restoreCallingIdentity(ident);
            if (isWiredHeadset(device)) {
                Intent intentForCA = new Intent("com.sec.android.contextaware.HEADSET_PLUG");
                intentForCA.putExtras(intent);
                intentForCA.addFlags(32);
                intentForCA.addFlags(16777216);
                Log.i("AudioService", "sendDeviceConnectionIntent with context-aware");
                sendStickyBroadcastToAll(intentForCA);
                if (this.mForceSpeaker == 1) {
                    sendMsg(this.mAudioHandler, 8, 2, 1, this.mForceSpeaker, null, 0);
                }
            }
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(ident);
            throw th;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onSetWiredDeviceConnectionState(int device, int state, String address, String deviceName, String caller) {
        boolean z;
        Slog.i("AudioService", "onSetWiredDeviceConnectionState(dev:" + Integer.toHexString(device) + " state:" + Integer.toHexString(state) + " address:" + address + " deviceName:" + deviceName + " caller: " + caller + ");");
        synchronized (this.mConnectedDevices) {
            if (state == 0 && (device & DEVICE_OVERRIDE_A2DP_ROUTE_ON_PLUG) != 0) {
                try {
                    setBluetoothA2dpOnInt(true, "onSetWiredDeviceConnectionState state 0");
                } catch (Throwable th) {
                    throw th;
                }
            }
            if (handleDeviceConnection(state == 1, device, address, deviceName)) {
                if (state != 0) {
                    if (device == 4 || device == 8) {
                        increaseEarJackCounter();
                    }
                    if ((DEVICE_OVERRIDE_A2DP_ROUTE_ON_PLUG & device) != 0) {
                        setBluetoothA2dpOnInt(false, "onSetWiredDeviceConnectionState state not 0");
                    }
                    if ((67109260 & device) != 0) {
                        z = false;
                        sendMsg(this.mAudioHandler, 14, 0, 0, 0, caller, 60000);
                    } else {
                        z = false;
                    }
                    if (isPlatformTelevision() && (device & 1024) != 0) {
                        this.mFixedVolumeDevices |= 1024;
                        checkAllFixedVolumeDevices();
                        if (this.mHdmiManager != null) {
                            synchronized (this.mHdmiManager) {
                                if (this.mHdmiPlaybackClient != null) {
                                    this.mHdmiCecSink = z;
                                    this.mHdmiPlaybackClient.queryDisplayStatus(this.mHdmiDisplayStatusCallback);
                                }
                            }
                        }
                    }
                    if ((device & 1024) != 0) {
                        sendEnabledSurroundFormats(this.mContentResolver, true);
                    }
                } else if (isPlatformTelevision() && (device & 1024) != 0 && this.mHdmiManager != null) {
                    synchronized (this.mHdmiManager) {
                        this.mHdmiCecSink = false;
                    }
                }
                sendDeviceConnectionIntent(device, state, address, deviceName);
                updateAudioRoutes(device, state);
            }
        }
    }

    private void configureHdmiPlugIntent(Intent intent, int state) {
        int[] channelMasks;
        intent.setAction("android.media.action.HDMI_AUDIO_PLUG");
        intent.putExtra("android.media.extra.AUDIO_PLUG_STATE", state);
        if (state == 1) {
            ArrayList<AudioPort> ports = new ArrayList<>();
            int[] portGeneration = new int[1];
            int status = AudioSystem.listAudioPorts(ports, portGeneration);
            if (status == 0) {
                Iterator<AudioPort> it = ports.iterator();
                while (it.hasNext()) {
                    AudioPort next = it.next();
                    if (next instanceof AudioDevicePort) {
                        AudioDevicePort devicePort = (AudioDevicePort) next;
                        if (devicePort.type() == 1024 || devicePort.type() == 262144) {
                            int[] formats = AudioFormat.filterPublicFormats(devicePort.formats());
                            if (formats.length > 0) {
                                ArrayList<Integer> encodingList = new ArrayList<>(1);
                                for (int format : formats) {
                                    if (format != 0) {
                                        encodingList.add(Integer.valueOf(format));
                                    }
                                }
                                int[] encodingArray = new int[encodingList.size()];
                                for (int i = 0; i < encodingArray.length; i++) {
                                    encodingArray[i] = encodingList.get(i).intValue();
                                }
                                intent.putExtra("android.media.extra.ENCODINGS", encodingArray);
                            }
                            int maxChannels = 0;
                            for (int mask : devicePort.channelMasks()) {
                                int channelCount = AudioFormat.channelCountFromOutChannelMask(mask);
                                if (channelCount > maxChannels) {
                                    maxChannels = channelCount;
                                }
                            }
                            intent.putExtra("android.media.extra.MAX_CHANNEL_COUNT", maxChannels);
                        }
                    }
                }
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    private class AudioServiceBroadcastReceiver extends BroadcastReceiver {
        private AudioServiceBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            int config;
            String action = intent.getAction();
            boolean z = true;
            if (action.equals("android.intent.action.DOCK_EVENT")) {
                int dockState = intent.getIntExtra("android.intent.extra.DOCK_STATE", 0);
                switch (dockState) {
                    case 1:
                        config = 7;
                        break;
                    case 2:
                        config = 6;
                        break;
                    case 3:
                        config = 8;
                        break;
                    case 4:
                        config = 9;
                        break;
                    default:
                        config = 0;
                        break;
                }
                if (dockState != 3 && (dockState != 0 || AudioService.this.mDockState != 3)) {
                    AudioService.this.mForceUseLogger.log(new AudioServiceEvents.ForceUseEvent(3, config, "ACTION_DOCK_EVENT intent"));
                    AudioSystem.setForceUse(3, config);
                }
                if (AudioService.this.mDockState != 0 || dockState != 114) {
                    if (AudioService.this.mDockState == 114 && dockState == 0) {
                        AudioService.this.mDexPadConnectedState = false;
                        AudioSystem.setParameters("DEXPADconnected=" + AudioService.this.mDexPadConnectedState);
                    }
                } else {
                    AudioService.this.mDexPadConnectedState = true;
                    AudioSystem.setParameters("DEXPADconnected=" + AudioService.this.mDexPadConnectedState);
                }
                AudioService.this.mDockState = dockState;
            } else if (action.equals("android.bluetooth.headset.profile.action.ACTIVE_DEVICE_CHANGED")) {
                AudioService.this.setBtScoActiveDevice((BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE"));
            } else if (action.equals("android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED")) {
                boolean broadcast = false;
                int scoAudioState = -1;
                synchronized (AudioService.this.mScoClients) {
                    int btState = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                    if (!AudioService.this.mScoClients.isEmpty() && (AudioService.this.mScoAudioState == 3 || AudioService.this.mScoAudioState == 1 || AudioService.this.mScoAudioState == 4 || AudioService.this.mScoAudioState == 5)) {
                        broadcast = true;
                    }
                    switch (btState) {
                        case 10:
                            if (AudioService.this.mMode == 1) {
                                synchronized (AudioService.this.mSettingsLock) {
                                    if (AudioService.this.mRingerMode == 1) {
                                        Log.i("AudioService", "SPK ringtone volume set to 0 !!!");
                                        AudioSystem.setStreamVolumeIndex(2, 0, 2);
                                    }
                                }
                            }
                            AudioService.this.setBluetoothScoOn(false);
                            scoAudioState = 0;
                            if (AudioService.this.mScoAudioState == 1 && AudioService.this.mBluetoothHeadset != null && AudioService.this.mBluetoothHeadsetDevice != null && AudioService.connectBluetoothScoAudioHelper(AudioService.this.mBluetoothHeadset, AudioService.this.mBluetoothHeadsetDevice, AudioService.this.mScoAudioMode)) {
                                AudioService.this.mScoAudioState = 3;
                                broadcast = false;
                                break;
                            } else {
                                AudioService audioService = AudioService.this;
                                if (AudioService.this.mScoAudioState != 3) {
                                    z = false;
                                }
                                audioService.clearAllScoClients(0, z);
                                AudioService.this.mScoAudioState = 0;
                                break;
                            }
                            break;
                        case 11:
                            if (AudioService.this.mScoAudioState != 3 && AudioService.this.mScoAudioState != 4) {
                                AudioService.this.mScoAudioState = 2;
                            }
                            broadcast = false;
                            break;
                        case 12:
                            scoAudioState = 1;
                            if (AudioService.this.mScoAudioState != 3 && AudioService.this.mScoAudioState != 4) {
                                AudioService.this.mScoAudioState = 2;
                            }
                            AudioService.this.setBluetoothScoOn(true);
                            BluetoothDevice btDevice = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                            if (btDevice != null) {
                                int btVolume = Settings.Global.getInt(context.getContentResolver(), Settings.Global.getBluetoothHeadsetVolumeKey(btDevice.getAddress()), 7);
                                AudioService.this.setStreamVolume(6, btVolume, 0, context.getOpPackageName());
                                Log.i("AudioService", "SCO volume set to index " + btVolume + " by BT");
                            }
                            break;
                        default:
                            broadcast = false;
                            break;
                    }
                }
                if (broadcast) {
                    AudioService.this.broadcastScoConnectionState(scoAudioState);
                    Intent newIntent = new Intent("android.media.SCO_AUDIO_STATE_CHANGED");
                    newIntent.putExtra("android.media.extra.SCO_AUDIO_STATE", scoAudioState);
                    AudioService.this.sendStickyBroadcastToAll(newIntent);
                }
            } else if (action.equals("android.intent.action.SCREEN_ON")) {
                if (AudioService.this.mMonitorRotation) {
                    RotationHelper.enable();
                }
                AudioSystem.setParameters("screen_state=on");
                AudioService.this.mIsScreenOn = true;
                AudioService.this.notifyDVFSToSoundAlive(0);
            } else if (action.equals("android.intent.action.SCREEN_OFF")) {
                if (AudioService.this.mMonitorRotation) {
                    RotationHelper.disable();
                }
                AudioSystem.setParameters("screen_state=off");
                AudioService.this.mIsScreenOn = false;
                AudioService.this.notifyDVFSToSoundAlive(0);
            } else if (action.equals("android.intent.action.CONFIGURATION_CHANGED")) {
                AudioService.this.handleConfigurationChanged(context);
            } else if (action.equals(Constants.SWITCH_USER)) {
                if (AudioService.this.mUserSwitchedReceived) {
                    AudioService.sendMsg(AudioService.this.mAudioHandler, 15, 0, 0, 0, null, 0);
                }
                AudioService.this.mUserSwitchedReceived = true;
                AudioService.this.mMediaFocusControl.discardAudioFocusOwner();
                AudioService.this.readAudioSettings(true);
                AudioService.sendMsg(AudioService.this.mAudioHandler, 10, 2, 0, 0, AudioService.this.mStreamStates[3], 0);
                AudioService.this.checkAndSetThemeSound(true);
            } else if (action.equals("android.intent.action.USER_BACKGROUND")) {
                int userId = intent.getIntExtra("android.intent.extra.user_handle", -1);
                if (userId >= 0) {
                    UserInfo userInfo = UserManagerService.getInstance().getUserInfo(userId);
                    AudioService.this.killBackgroundUserProcessesWithRecordAudioPermission(userInfo);
                }
                UserManagerService.getInstance().setUserRestriction("no_record_audio", true, userId);
            } else if (action.equals("android.intent.action.USER_FOREGROUND")) {
                UserManagerService.getInstance().setUserRestriction("no_record_audio", false, intent.getIntExtra("android.intent.extra.user_handle", -1));
            } else if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", -1);
                if (state == 10 || state == 13) {
                    AudioService.this.disconnectAllBluetoothProfiles();
                }
            } else if (action.equals("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION") || action.equals("android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION")) {
                AudioService.this.handleAudioEffectBroadcast(context, intent);
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    private class AudioServiceUserRestrictionsListener implements UserManagerInternal.UserRestrictionsListener {
        private AudioServiceUserRestrictionsListener() {
        }

        public void onUserRestrictionsChanged(int userId, Bundle newRestrictions, Bundle prevRestrictions) {
            boolean wasRestricted = prevRestrictions.getBoolean("no_unmute_microphone");
            boolean isRestricted = newRestrictions.getBoolean("no_unmute_microphone");
            if (wasRestricted != isRestricted) {
                AudioService.this.setMicrophoneMuteNoCallerCheck(isRestricted, userId);
            }
            boolean isRestricted2 = true;
            boolean wasRestricted2 = prevRestrictions.getBoolean("no_adjust_volume") || prevRestrictions.getBoolean("disallow_unmute_device");
            if (!newRestrictions.getBoolean("no_adjust_volume") && !newRestrictions.getBoolean("disallow_unmute_device")) {
                isRestricted2 = false;
            }
            if (wasRestricted2 != isRestricted2) {
                AudioService.this.setMasterMuteInternalNoCallerCheck(isRestricted2, 0, userId);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleAudioEffectBroadcast(Context context, Intent intent) {
        ResolveInfo ri;
        String target = intent.getPackage();
        if (target != null) {
            Log.w("AudioService", "effect broadcast already targeted to " + target);
            return;
        }
        intent.addFlags(32);
        List<ResolveInfo> ril = context.getPackageManager().queryBroadcastReceivers(intent, 0);
        if (ril != null && ril.size() != 0 && (ri = ril.get(0)) != null && ri.activityInfo != null && ri.activityInfo.packageName != null) {
            intent.setPackage(ri.activityInfo.packageName);
            context.sendBroadcastAsUser(intent, UserHandle.ALL);
            return;
        }
        Log.w("AudioService", "couldn't find receiver package for effect intent");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void killBackgroundUserProcessesWithRecordAudioPermission(UserInfo oldUser) {
        PackageManager pm = this.mContext.getPackageManager();
        ComponentName homeActivityName = null;
        if (!oldUser.isManagedProfile()) {
            homeActivityName = this.mActivityManagerInternal.getHomeActivityForUser(oldUser.id);
        }
        String[] permissions = {"android.permission.RECORD_AUDIO"};
        try {
            List<PackageInfo> packages = AppGlobals.getPackageManager().getPackagesHoldingPermissions(permissions, 0, oldUser.id).getList();
            for (int j = packages.size() - 1; j >= 0; j--) {
                PackageInfo pkg = packages.get(j);
                if (UserHandle.getAppId(pkg.applicationInfo.uid) >= 10000 && pm.checkPermission("android.permission.INTERACT_ACROSS_USERS", pkg.packageName) != 0 && (homeActivityName == null || !pkg.packageName.equals(homeActivityName.getPackageName()) || !pkg.applicationInfo.isSystemApp())) {
                    try {
                        int uid = pkg.applicationInfo.uid;
                        ActivityManager.getService().killUid(UserHandle.getAppId(uid), UserHandle.getUserId(uid), "killBackgroundUserProcessesWithAudioRecordPermission");
                    } catch (RemoteException e) {
                        Log.w("AudioService", "Error calling killUid", e);
                    }
                }
            }
        } catch (RemoteException e2) {
            throw new AndroidRuntimeException(e2);
        }
    }

    private boolean forceFocusDuckingForAccessibility(AudioAttributes aa, int request, int uid) {
        Bundle extraInfo;
        if (aa != null && aa.getUsage() == 11 && request == 3 && (extraInfo = aa.getBundle()) != null && extraInfo.getBoolean("a11y_force_ducking")) {
            if (uid == 0) {
                return true;
            }
            synchronized (this.mAccessibilityServiceUidsLock) {
                if (this.mAccessibilityServiceUids != null) {
                    int callingUid = Binder.getCallingUid();
                    for (int i = 0; i < this.mAccessibilityServiceUids.length; i++) {
                        if (this.mAccessibilityServiceUids[i] == callingUid) {
                            return true;
                        }
                    }
                }
                return false;
            }
        }
        return false;
    }

    public int requestAudioFocus(AudioAttributes aa, int durationHint, IBinder cb, IAudioFocusDispatcher fd, String clientId, String callingPackageName, int flags, IAudioPolicyCallback pcb, int sdk) {
        String str;
        if ((flags & 4) == 4) {
            str = clientId;
            if ("AudioFocus_For_Phone_Ring_And_Calls".equals(str)) {
                if (this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") != 0) {
                    Log.e("AudioService", "Invalid permission to (un)lock audio focus", new Exception());
                    return 0;
                }
            } else {
                synchronized (this.mAudioPolicies) {
                    if (!this.mAudioPolicies.containsKey(pcb.asBinder())) {
                        Log.e("AudioService", "Invalid unregistered AudioPolicy to (un)lock audio focus");
                        return 0;
                    }
                }
            }
        } else {
            str = clientId;
        }
        checkAndSetSplitSound(aa, callingPackageName, durationHint);
        return this.mMediaFocusControl.requestAudioFocus(aa, durationHint, cb, fd, str, callingPackageName, flags, sdk, forceFocusDuckingForAccessibility(aa, durationHint, Binder.getCallingUid()));
    }

    public int abandonAudioFocus(IAudioFocusDispatcher fd, String clientId, AudioAttributes aa, String callingPackageName) {
        return this.mMediaFocusControl.abandonAudioFocus(fd, clientId, aa, callingPackageName);
    }

    public void unregisterAudioFocusClient(String clientId) {
        this.mMediaFocusControl.unregisterAudioFocusClient(clientId);
    }

    public int getCurrentAudioFocus() {
        return this.mMediaFocusControl.getCurrentAudioFocus();
    }

    public int getFocusRampTimeMs(int focusGain, AudioAttributes attr) {
        MediaFocusControl mediaFocusControl = this.mMediaFocusControl;
        return MediaFocusControl.getFocusRampTimeMs(focusGain, attr);
    }

    private boolean readCameraSoundForced() {
        return SystemProperties.getBoolean("audio.camerasound.force", false) || this.mContext.getResources().getBoolean(17956917);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleConfigurationChanged(Context context) {
        try {
            Configuration config = context.getResources().getConfiguration();
            if (this.mPopupManager != null) {
                this.mPopupManager.onConfigurationChangedForPopupWindow(config);
            }
            boolean cameraSoundForced = readCameraSoundForced();
            synchronized (this.mSettingsLock) {
                int i = 0;
                boolean cameraSoundForcedChanged = cameraSoundForced != this.mCameraSoundForced;
                this.mCameraSoundForced = cameraSoundForced;
                if (cameraSoundForcedChanged) {
                    if (!this.mIsSingleVolume) {
                        synchronized (VolumeStreamState.class) {
                            VolumeStreamState s = this.mStreamStates[7];
                            if (cameraSoundForced) {
                                s.setAllIndexesToMax();
                                this.mRingerModeAffectedStreams &= -129;
                            } else {
                                s.setAllIndexes(this.mStreamStates[1], "AudioService");
                            }
                        }
                        setRingerModeInt(getRingerModeInternal(), false);
                    }
                    AudioHandler audioHandler = this.mAudioHandler;
                    if (cameraSoundForced) {
                        i = 11;
                    }
                    sendMsg(audioHandler, 8, 2, 4, i, new String("handleConfigurationChanged"), 0);
                    sendMsg(this.mAudioHandler, 10, 2, 0, 0, this.mStreamStates[7], 0);
                }
            }
            this.mVolumeController.setLayoutDirection(config.getLayoutDirection());
        } catch (Exception e) {
            Log.e("AudioService", "Error handling configuration change: ", e);
        }
    }

    public void setBluetoothA2dpOnInt(boolean on, String eventSource) {
        synchronized (this.mBluetoothA2dpEnabledLock) {
            this.mBluetoothA2dpEnabled = on;
            this.mAudioHandler.removeMessages(13);
            setForceUseInt_SyncDevices(1, this.mBluetoothA2dpEnabled ? 0 : 10, eventSource);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setForceUseInt_SyncDevices(int usage, int config, String eventSource) {
        if (usage == 1) {
            sendMsg(this.mAudioHandler, 12, 1, 0, 0, null, 0);
        }
        this.mForceUseLogger.log(new AudioServiceEvents.ForceUseEvent(usage, config, eventSource));
        AudioSystem.setForceUse(usage, config);
    }

    public void setRingtonePlayer(IRingtonePlayer player) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.REMOTE_AUDIO_PLAYBACK", null);
        this.mRingtonePlayer = player;
    }

    public IRingtonePlayer getRingtonePlayer() {
        return this.mRingtonePlayer;
    }

    public AudioRoutesInfo startWatchingRoutes(IAudioRoutesObserver observer) {
        AudioRoutesInfo routes;
        synchronized (this.mCurAudioRoutes) {
            routes = new AudioRoutesInfo(this.mCurAudioRoutes);
            this.mRoutesObservers.register(observer);
        }
        return routes;
    }

    private int safeMediaVolumeIndex(int device) {
        if ((67109260 & device) == 0) {
            return MAX_STREAM_VOLUME[3];
        }
        if (device == 67108864) {
            return this.mSafeUsbMediaVolumeIndex;
        }
        return this.mSafeMediaVolumeIndex;
    }

    private void setSafeMediaVolumeEnabled(boolean on, String caller) {
        synchronized (this.mSafeMediaVolumeStateLock) {
            if (this.mSafeMediaVolumeState.intValue() != 0 && this.mSafeMediaVolumeState.intValue() != 1) {
                if (on && this.mSafeMediaVolumeState.intValue() == 2) {
                    this.mSafeMediaVolumeState = 3;
                    enforceSafeMediaVolume(caller);
                } else if (!on && (this.mSafeMediaVolumeState.intValue() == 3 || this.mSafeMediaVolumeBTState.intValue() == 3)) {
                    this.mSafeMediaVolumeBTState = 2;
                    this.mSafeMediaVolumeState = 2;
                    this.mMusicActiveMs = 1;
                    saveMusicActiveMs();
                    sendMsg(this.mAudioHandler, 14, 0, 0, 0, caller, 60000);
                }
                sendMsg(this.mAudioHandler, 18, 2, this.mSafeMediaVolumeState.intValue(), 0, null, 0);
            }
        }
    }

    private void enforceSafeMediaVolume(String caller) {
        VolumeStreamState streamState = this.mStreamStates[3];
        int devices = 67109260;
        int i = 0;
        while (devices != 0) {
            int i2 = i + 1;
            int device = 1 << i;
            if ((device & devices) != 0) {
                int index = streamState.getIndex(device);
                if (index > safeMediaVolumeIndex(device)) {
                    streamState.setIndex(safeMediaVolumeIndex(device), device, caller);
                    this.mFineMediaVolume.setIndex(0, device);
                    sendMsg(this.mAudioHandler, 0, 2, device, 0, streamState, 0);
                }
                devices &= ~device;
            }
            i = i2;
        }
    }

    private boolean checkSafeMediaVolume(int streamType, int index, int device) {
        if (this.mIsFactoryMode || this.mHMTDocked) {
            return true;
        }
        synchronized (this.mSafeMediaVolumeStateLock) {
            if (isSafeMediaVolumeDevice(device) && mStreamVolumeAlias[streamType] == 3 && (index > safeMediaVolumeIndex(device) || (index == safeMediaVolumeIndex(device) && this.mFineMediaVolume.getIndex(device) > 0))) {
                notifyVibrationForSafeMediaPopup();
                return false;
            }
            return true;
        }
    }

    public void disableSafeMediaVolume(String callingPackage) {
        enforceVolumeController("disable the safe media volume");
        synchronized (this.mSafeMediaVolumeStateLock) {
            setSafeMediaVolumeEnabled(false, callingPackage);
            if (this.mPendingVolumeCommand != null) {
                if (this.mPendingVolumeCommand.mStreamType == 3 && (this.mPendingVolumeCommand.mDevice & 896) != 0) {
                    synchronized (this.mA2dpAvrcpLock) {
                        if (this.mA2dp != null && this.mAvrcpAbsVolSupported) {
                            this.mA2dp.setAvrcpAbsoluteVolume(this.mPendingVolumeCommand.mIndex + this.mPendingFineVolumeCommand.mIndex);
                        }
                    }
                }
                if (this.mPendingFineVolumeCommand != null) {
                    int index = this.mPendingFineVolumeCommand.mIndex;
                    int device = this.mPendingFineVolumeCommand.mDevice;
                    this.mFineMediaVolume.setIndex(index, device);
                    this.mPendingFineVolumeCommand = null;
                }
                onSetStreamVolume(this.mPendingVolumeCommand.mStreamType, this.mPendingVolumeCommand.mIndex, this.mPendingVolumeCommand.mFlags, this.mPendingVolumeCommand.mDevice, callingPackage);
                Intent intent = new Intent("android.media.VOLUME_CHANGED_ACTION");
                intent.putExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", this.mPendingVolumeCommand.mStreamType);
                intent.putExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", this.mPendingVolumeCommand.mIndex / 10);
                intent.putExtra("android.media.EXTRA_VOLUME_SHOW_UI", (this.mPendingVolumeCommand.mFlags & 1) != 0);
                sendBroadcastToAll(intent);
                this.mVolumeController.postVolumeChanged(this.mPendingVolumeCommand.mStreamType, this.mPendingVolumeCommand.mFlags);
                this.mPendingVolumeCommand = null;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class MyDisplayStatusCallback implements HdmiPlaybackClient.DisplayStatusCallback {
        private MyDisplayStatusCallback() {
        }

        public void onComplete(int status) {
            if (AudioService.this.mHdmiManager != null) {
                synchronized (AudioService.this.mHdmiManager) {
                    AudioService.this.mHdmiCecSink = status != -1;
                    if (AudioService.this.isPlatformTelevision() && !AudioService.this.mHdmiCecSink) {
                        AudioService.this.mFixedVolumeDevices &= KnoxContainerManager.ERROR_INVALID_PASSWORD_RESET_TOKEN;
                    }
                    AudioService.this.checkAllFixedVolumeDevices();
                }
            }
        }
    }

    public int setHdmiSystemAudioSupported(boolean on) {
        int device = 0;
        if (this.mHdmiManager != null) {
            synchronized (this.mHdmiManager) {
                if (this.mHdmiTvClient == null) {
                    Log.w("AudioService", "Only Hdmi-Cec enabled TV device supports system audio mode.");
                    return 0;
                }
                synchronized (this.mHdmiTvClient) {
                    if (this.mHdmiSystemAudioSupported != on) {
                        this.mHdmiSystemAudioSupported = on;
                        int config = on ? 12 : 0;
                        this.mForceUseLogger.log(new AudioServiceEvents.ForceUseEvent(5, config, "setHdmiSystemAudioSupported"));
                        AudioSystem.setForceUse(5, config);
                    }
                    device = getDevicesForStream(3);
                }
            }
        }
        return device;
    }

    public boolean isHdmiSystemAudioSupported() {
        return this.mHdmiSystemAudioSupported;
    }

    private void initA11yMonitoring() {
        AccessibilityManager accessibilityManager = (AccessibilityManager) this.mContext.getSystemService("accessibility");
        updateDefaultStreamOverrideDelay(accessibilityManager.isTouchExplorationEnabled());
        updateA11yVolumeAlias(accessibilityManager.isAccessibilityVolumeStreamActive());
        accessibilityManager.addTouchExplorationStateChangeListener(this, null);
        accessibilityManager.addAccessibilityServicesStateChangeListener(this, null);
    }

    @Override // android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener
    public void onTouchExplorationStateChanged(boolean enabled) {
        updateDefaultStreamOverrideDelay(enabled);
    }

    private void updateDefaultStreamOverrideDelay(boolean touchExploreEnabled) {
        if (touchExploreEnabled) {
            sStreamOverrideDelayMs = 1000;
        } else {
            sStreamOverrideDelayMs = 0;
        }
        Log.d("AudioService", "Touch exploration enabled=" + touchExploreEnabled + " stream override delay is now " + sStreamOverrideDelayMs + " ms");
    }

    public void onAccessibilityServicesStateChanged(AccessibilityManager accessibilityManager) {
        this.mIsTalkBackEnabled = accessibilityManager.semIsAccessibilityServiceEnabled(32);
        this.mIsTalkBackEnabled |= accessibilityManager.semIsAccessibilityServiceEnabled(16);
        updateA11yVolumeAlias(accessibilityManager.isAccessibilityVolumeStreamActive());
    }

    private void updateA11yVolumeAlias(boolean a11VolEnabled) {
        Log.d("AudioService", "Accessibility volume enabled = " + a11VolEnabled);
        if (sIndependentA11yVolume != a11VolEnabled) {
            sIndependentA11yVolume = a11VolEnabled;
            updateStreamVolumeAlias(true, "AudioService");
            this.mVolumeController.setA11yMode(sIndependentA11yVolume ? 1 : 0);
            this.mVolumeController.postVolumeChanged(10, 0);
        }
    }

    public boolean isCameraSoundForced() {
        boolean z;
        synchronized (this.mSettingsLock) {
            z = this.mCameraSoundForced;
        }
        return z;
    }

    private void dumpRingerMode(PrintWriter pw) {
        pw.println("\nRinger mode: ");
        pw.println("- mode (internal) = " + RINGER_MODE_NAMES[this.mRingerMode]);
        pw.println("- mode (external) = " + RINGER_MODE_NAMES[this.mRingerModeExternal]);
        dumpRingerModeStreams(pw, "affected", this.mRingerModeAffectedStreams);
        dumpRingerModeStreams(pw, "muted", this.mRingerAndZenModeMutedStreams);
        pw.print("- delegate = ");
        pw.println(this.mRingerModeDelegate);
    }

    private void dumpRingerModeStreams(PrintWriter pw, String type, int streams) {
        pw.print("- ringer mode ");
        pw.print(type);
        pw.print(" streams = 0x");
        pw.print(Integer.toHexString(streams));
        if (streams != 0) {
            pw.print(" (");
            boolean first = true;
            for (int i = 0; i < AudioSystem.STREAM_NAMES.length; i++) {
                int stream = 1 << i;
                if ((streams & stream) != 0) {
                    if (!first) {
                        pw.print(',');
                    }
                    pw.print(AudioSystem.STREAM_NAMES[i]);
                    streams &= ~stream;
                    first = false;
                }
            }
            if (streams != 0) {
                if (!first) {
                    pw.print(',');
                }
                pw.print(streams);
            }
            pw.print(')');
        }
        pw.println();
    }

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (DumpUtils.checkDumpPermission(this.mContext, "AudioService", pw)) {
            this.mMediaFocusControl.dump(pw);
            dumpStreamStates(pw);
            dumpRingerMode(pw);
            pw.println("\nAudio routes:");
            pw.print("  mMainType=0x");
            pw.println(Integer.toHexString(this.mCurAudioRoutes.mainType));
            pw.print("  mBluetoothName=");
            pw.println(this.mCurAudioRoutes.bluetoothName);
            pw.println("\nOther state:");
            pw.print("  mVolumeController=");
            pw.println(this.mVolumeController);
            pw.print("  mSafeMediaVolumeState=");
            pw.println(safeMediaVolumeStateToString(this.mSafeMediaVolumeState));
            pw.print("  mSafeMediaVolumeIndex=");
            pw.println(this.mSafeMediaVolumeIndex);
            pw.print("  mSafeUsbMediaVolumeIndex=");
            pw.println(this.mSafeUsbMediaVolumeIndex);
            pw.print("  mSafeUsbMediaVolumeDbfs=");
            pw.println(this.mSafeUsbMediaVolumeDbfs);
            pw.print("  sIndependentA11yVolume=");
            pw.println(sIndependentA11yVolume);
            synchronized (this.mSafeMediaVolumeStateLock) {
                pw.print("  mPendingVolumeCommand=");
                pw.println(this.mPendingVolumeCommand);
            }
            pw.print("  mMusicActiveMs=");
            pw.println(this.mMusicActiveMs);
            pw.print("  mMcc=");
            pw.println(this.mMcc);
            pw.print("  mCameraSoundForced=");
            pw.println(this.mCameraSoundForced);
            pw.print("  mHasVibrator=");
            pw.println(this.mHasVibrator);
            pw.print("  mVolumePolicy=");
            pw.println(this.mVolumePolicy);
            pw.print("  mAvrcpAbsVolSupported=");
            pw.println(this.mAvrcpAbsVolSupported);
            dumpAudioPolicies(pw);
            this.mDynPolicyLogger.dump(pw);
            this.mPlaybackMonitor.dump(pw);
            this.mRecordMonitor.dump(pw);
            pw.println("\n");
            pw.println("\nEvent logs:");
            this.mModeLogger.dump(pw);
            pw.println("\n");
            this.mWiredDevLogger.dump(pw);
            pw.println("\n");
            this.mForceUseLogger.dump(pw);
            pw.println("\n");
            this.mVolumeLogger.dump(pw);
            addSamsungExtraDump(pw);
        }
    }

    private static String safeMediaVolumeStateToString(Integer state) {
        switch (state.intValue()) {
            case 0:
                return "SAFE_MEDIA_VOLUME_NOT_CONFIGURED";
            case 1:
                return "SAFE_MEDIA_VOLUME_DISABLED";
            case 2:
                return "SAFE_MEDIA_VOLUME_INACTIVE";
            case 3:
                return "SAFE_MEDIA_VOLUME_ACTIVE";
            default:
                return null;
        }
    }

    private static void readAndSetLowRamDevice() {
        boolean isLowRamDevice = ActivityManager.isLowRamDeviceStatic();
        long totalMemory = PKCS11Constants.CKF_ARRAY_ATTRIBUTE;
        try {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            ActivityManager.getService().getMemoryInfo(info);
            totalMemory = info.totalMem;
        } catch (RemoteException e) {
            Log.w("AudioService", "Cannot obtain MemoryInfo from ActivityManager, assume low memory device");
            isLowRamDevice = true;
        }
        int status = AudioSystem.setLowRamDevice(isLowRamDevice, totalMemory);
        if (status != 0) {
            Log.w("AudioService", "AudioFlinger informed of device's low RAM attribute; status " + status);
        }
    }

    private void enforceVolumeController(String action) {
        Context context = this.mContext;
        context.enforceCallingOrSelfPermission("android.permission.STATUS_BAR_SERVICE", "Only SystemUI can " + action);
    }

    public void setVolumeController(final IVolumeController controller) {
        enforceVolumeController("set the volume controller");
        if (this.mVolumeController.isSameBinder(controller)) {
            return;
        }
        this.mVolumeController.postDismiss();
        if (controller != null) {
            try {
                controller.asBinder().linkToDeath(new IBinder.DeathRecipient() { // from class: com.android.server.audio.AudioService.4
                    @Override // android.os.IBinder.DeathRecipient
                    public void binderDied() {
                        if (AudioService.this.mVolumeController.isSameBinder(controller)) {
                            AudioService.this.mVolumeController.removeController(controller);
                        }
                    }
                }, 0);
            } catch (RemoteException e) {
            }
        }
        this.mVolumeController.setController(controller);
        Log.d("AudioService", "Volume controller: " + this.mVolumeController);
    }

    public void notifyVolumeControllerVisible(IVolumeController controller, boolean visible) {
        enforceVolumeController("notify about volume controller visibility");
        if (!this.mVolumeController.isSameBinder(controller)) {
            return;
        }
        this.mVolumeController.setVisible(visible);
    }

    public void setVolumePolicy(VolumePolicy policy) {
        enforceVolumeController("set volume policy");
        if (policy != null && !policy.equals(this.mVolumePolicy)) {
            this.mVolumePolicy = policy;
            Log.d("AudioService", "Volume policy changed: " + this.mVolumePolicy);
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public static class VolumeController {
        private static final String TAG = "VolumeController";
        protected IVolumeController mController;
        private int mLongPressTimeout;
        private long mNextLongPress;
        private boolean mVisible;

        public void setController(IVolumeController controller) {
            this.mController = controller;
            this.mVisible = false;
        }

        public void loadSettings(ContentResolver cr) {
            this.mLongPressTimeout = Settings.Secure.getIntForUser(cr, "long_press_timeout", 500, -2);
        }

        public boolean suppressAdjustment(int resolvedStream, int flags, boolean isMute) {
            if (isMute || resolvedStream != 3 || this.mController == null) {
                return false;
            }
            long now = SystemClock.uptimeMillis();
            if ((flags & 1) != 0 && !this.mVisible) {
                if (this.mNextLongPress < now) {
                    this.mNextLongPress = this.mLongPressTimeout + now;
                }
                return true;
            } else if (this.mNextLongPress <= 0) {
                return false;
            } else {
                if (now > this.mNextLongPress) {
                    this.mNextLongPress = 0L;
                    return false;
                }
                return true;
            }
        }

        public void setVisible(boolean visible) {
            this.mVisible = visible;
        }

        public boolean isSameBinder(IVolumeController controller) {
            return Objects.equals(asBinder(), binder(controller));
        }

        public IBinder asBinder() {
            return binder(this.mController);
        }

        protected static IBinder binder(IVolumeController controller) {
            if (controller == null) {
                return null;
            }
            return controller.asBinder();
        }

        public String toString() {
            return "VolumeController(" + asBinder() + ",mVisible=" + this.mVisible + ")";
        }

        public void postDisplaySafeVolumeWarning(int flags) {
            if (this.mController == null) {
                return;
            }
            try {
                this.mController.displaySafeVolumeWarning(flags);
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling displaySafeVolumeWarning", e);
            }
        }

        public void postVolumeChanged(int streamType, int flags) {
            if (this.mController == null) {
                return;
            }
            try {
                this.mController.volumeChanged(streamType, flags);
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling volumeChanged", e);
            }
        }

        public void postMasterMuteChanged(int flags) {
            if (this.mController == null) {
                return;
            }
            try {
                this.mController.masterMuteChanged(flags);
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling masterMuteChanged", e);
            }
        }

        public void setLayoutDirection(int layoutDirection) {
            if (this.mController == null) {
                return;
            }
            try {
                this.mController.setLayoutDirection(layoutDirection);
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling setLayoutDirection", e);
            }
        }

        public void postDismiss() {
            if (this.mController == null) {
                return;
            }
            try {
                this.mController.dismiss();
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling dismiss", e);
            }
        }

        public void setA11yMode(int a11yMode) {
            if (this.mController == null) {
                return;
            }
            try {
                this.mController.setA11yMode(a11yMode);
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling setA11Mode", e);
            }
        }

        public boolean isVisible() {
            return this.mVisible;
        }

        public boolean isSafeVolumeDialogShowing() {
            if (this.mController == null) {
                return false;
            }
            try {
                return this.mController.isSafeVolumeDialogShowing();
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling isSafeVolumeDialogShowing", e);
                return this.mVisible;
            }
        }

        public void displayVolumeLimiterToast() {
            if (this.mController == null) {
                return;
            }
            try {
                this.mController.displayVolumeLimiterToast();
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling displayVolumeLimiterToast", e);
            }
        }

        public void removeController(IVolumeController controller) {
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    final class AudioServiceInternal extends AudioManagerInternal {
        AudioServiceInternal() {
        }

        public void setRingerModeDelegate(AudioManagerInternal.RingerModeDelegate delegate) {
            AudioService.this.mRingerModeDelegate = delegate;
            if (AudioService.this.mRingerModeDelegate != null) {
                synchronized (AudioService.this.mSettingsLock) {
                    AudioService.this.updateRingerAndZenModeAffectedStreams();
                }
                setRingerModeInternal(getRingerModeInternal(), "AudioService.setRingerModeDelegate");
            }
        }

        public void adjustSuggestedStreamVolumeForUid(int streamType, int direction, int flags, String callingPackage, int uid) {
            AudioService.this.adjustSuggestedStreamVolume(direction, streamType, flags, callingPackage, callingPackage, uid);
        }

        public void adjustStreamVolumeForUid(int streamType, int direction, int flags, String callingPackage, int uid) {
            AudioService.this.adjustStreamVolume(streamType, direction, flags, callingPackage, callingPackage, uid);
        }

        public void setStreamVolumeForUid(int streamType, int direction, int flags, String callingPackage, int uid) {
            AudioService.this.setStreamVolume(streamType, direction, flags, callingPackage, callingPackage, uid);
        }

        public int getRingerModeInternal() {
            return AudioService.this.getRingerModeInternal();
        }

        public void setRingerModeInternal(int ringerMode, String caller) {
            AudioService.this.setRingerModeInternal(ringerMode, caller);
        }

        public void silenceRingerModeInternal(String caller) {
            AudioService.this.silenceRingerModeInternal(caller);
        }

        public void updateRingerModeAffectedStreamsInternal() {
            synchronized (AudioService.this.mSettingsLock) {
                if (AudioService.this.updateRingerAndZenModeAffectedStreams()) {
                    AudioService.this.setRingerModeInt(getRingerModeInternal(), false);
                }
            }
        }

        /* JADX WARN: Removed duplicated region for block: B:15:0x0031 A[Catch: all -> 0x005a, LOOP:0: B:15:0x0031->B:20:0x004a, LOOP_START, PHI: r2 
          PHI: (r2v2 'i' int) = (r2v0 'i' int), (r2v3 'i' int) binds: [B:14:0x002e, B:20:0x004a] A[DONT_GENERATE, DONT_INLINE], TryCatch #0 {, blocks: (B:4:0x0007, B:6:0x000d, B:23:0x0058, B:7:0x0014, B:9:0x001d, B:15:0x0031, B:17:0x003a, B:20:0x004a, B:22:0x004f), top: B:28:0x0007 }] */
        /* JADX WARN: Removed duplicated region for block: B:22:0x004f A[Catch: all -> 0x005a, TryCatch #0 {, blocks: (B:4:0x0007, B:6:0x000d, B:23:0x0058, B:7:0x0014, B:9:0x001d, B:15:0x0031, B:17:0x003a, B:20:0x004a, B:22:0x004f), top: B:28:0x0007 }] */
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct code enable 'Show inconsistent code' option in preferences
        */
        public void setAccessibilityServiceUids(android.util.IntArray r6) {
            /*
                r5 = this;
                com.android.server.audio.AudioService r0 = com.android.server.audio.AudioService.this
                java.lang.Object r0 = com.android.server.audio.AudioService.access$13800(r0)
                monitor-enter(r0)
                int r1 = r6.size()     // Catch: java.lang.Throwable -> L5a
                if (r1 != 0) goto L14
                com.android.server.audio.AudioService r1 = com.android.server.audio.AudioService.this     // Catch: java.lang.Throwable -> L5a
                r2 = 0
                com.android.server.audio.AudioService.access$13902(r1, r2)     // Catch: java.lang.Throwable -> L5a
                goto L58
            L14:
                com.android.server.audio.AudioService r1 = com.android.server.audio.AudioService.this     // Catch: java.lang.Throwable -> L5a
                int[] r1 = com.android.server.audio.AudioService.access$13900(r1)     // Catch: java.lang.Throwable -> L5a
                r2 = 0
                if (r1 == 0) goto L2d
                com.android.server.audio.AudioService r1 = com.android.server.audio.AudioService.this     // Catch: java.lang.Throwable -> L5a
                int[] r1 = com.android.server.audio.AudioService.access$13900(r1)     // Catch: java.lang.Throwable -> L5a
                int r1 = r1.length     // Catch: java.lang.Throwable -> L5a
                int r3 = r6.size()     // Catch: java.lang.Throwable -> L5a
                if (r1 == r3) goto L2b
                goto L2d
            L2b:
                r1 = r2
                goto L2e
            L2d:
                r1 = 1
            L2e:
                if (r1 != 0) goto L4d
            L31:
                com.android.server.audio.AudioService r3 = com.android.server.audio.AudioService.this     // Catch: java.lang.Throwable -> L5a
                int[] r3 = com.android.server.audio.AudioService.access$13900(r3)     // Catch: java.lang.Throwable -> L5a
                int r3 = r3.length     // Catch: java.lang.Throwable -> L5a
                if (r2 >= r3) goto L4d
                int r3 = r6.get(r2)     // Catch: java.lang.Throwable -> L5a
                com.android.server.audio.AudioService r4 = com.android.server.audio.AudioService.this     // Catch: java.lang.Throwable -> L5a
                int[] r4 = com.android.server.audio.AudioService.access$13900(r4)     // Catch: java.lang.Throwable -> L5a
                r4 = r4[r2]     // Catch: java.lang.Throwable -> L5a
                if (r3 == r4) goto L4a
                r1 = 1
                goto L4d
            L4a:
                int r2 = r2 + 1
                goto L31
            L4d:
                if (r1 == 0) goto L58
                com.android.server.audio.AudioService r2 = com.android.server.audio.AudioService.this     // Catch: java.lang.Throwable -> L5a
                int[] r3 = r6.toArray()     // Catch: java.lang.Throwable -> L5a
                com.android.server.audio.AudioService.access$13902(r2, r3)     // Catch: java.lang.Throwable -> L5a
            L58:
                monitor-exit(r0)     // Catch: java.lang.Throwable -> L5a
                return
            L5a:
                r1 = move-exception
                monitor-exit(r0)     // Catch: java.lang.Throwable -> L5a
                throw r1
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.server.audio.AudioService.AudioServiceInternal.setAccessibilityServiceUids(android.util.IntArray):void");
        }
    }

    public String registerAudioPolicy(AudioPolicyConfig policyConfig, IAudioPolicyCallback pcb, boolean hasFocusListener, boolean isFocusPolicy, boolean isVolumeController) {
        AudioSystem.setDynamicPolicyCallback(this.mDynPolicyCallback);
        boolean hasPermissionForPolicy = this.mContext.checkCallingPermission("android.permission.MODIFY_AUDIO_ROUTING") == 0;
        if (!hasPermissionForPolicy) {
            Slog.w("AudioService", "Can't register audio policy for pid " + Binder.getCallingPid() + " / uid " + Binder.getCallingUid() + ", need MODIFY_AUDIO_ROUTING");
            return null;
        }
        AudioEventLogger audioEventLogger = this.mDynPolicyLogger;
        audioEventLogger.log(new AudioEventLogger.StringEvent("registerAudioPolicy for " + pcb.asBinder() + " with config:" + policyConfig).printLog("AudioService"));
        synchronized (this.mAudioPolicies) {
            try {
            } catch (Throwable th) {
                e = th;
            }
            try {
                try {
                    if (this.mAudioPolicies.containsKey(pcb.asBinder())) {
                        Slog.e("AudioService", "Cannot re-register policy");
                        return null;
                    }
                    AudioPolicyProxy app = new AudioPolicyProxy(policyConfig, pcb, hasFocusListener, isFocusPolicy, isVolumeController);
                    pcb.asBinder().linkToDeath(app, 0);
                    String regId = app.getRegistrationId();
                    this.mAudioPolicies.put(pcb.asBinder(), app);
                    return regId;
                } catch (RemoteException e) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Audio policy registration failed, could not link to ");
                    sb.append(pcb);
                    sb.append(" binder death");
                    Slog.w("AudioService", sb.toString(), e);
                    return null;
                }
            } catch (Throwable th2) {
                e = th2;
                throw e;
            }
        }
    }

    public void unregisterAudioPolicyAsync(IAudioPolicyCallback pcb) {
        AudioEventLogger audioEventLogger = this.mDynPolicyLogger;
        audioEventLogger.log(new AudioEventLogger.StringEvent("unregisterAudioPolicyAsync for " + pcb.asBinder()).printLog("AudioService"));
        synchronized (this.mAudioPolicies) {
            AudioPolicyProxy app = this.mAudioPolicies.remove(pcb.asBinder());
            if (app == null) {
                Slog.w("AudioService", "Trying to unregister unknown audio policy for pid " + Binder.getCallingPid() + " / uid " + Binder.getCallingUid());
                return;
            }
            pcb.asBinder().unlinkToDeath(app, 0);
            app.release();
        }
    }

    @GuardedBy("mAudioPolicies")
    private AudioPolicyProxy checkUpdateForPolicy(IAudioPolicyCallback pcb, String errorMsg) {
        boolean hasPermissionForPolicy = this.mContext.checkCallingPermission("android.permission.MODIFY_AUDIO_ROUTING") == 0;
        if (!hasPermissionForPolicy) {
            Slog.w("AudioService", errorMsg + " for pid " + Binder.getCallingPid() + " / uid " + Binder.getCallingUid() + ", need MODIFY_AUDIO_ROUTING");
            return null;
        }
        synchronized (this.mAudioPolicies) {
            AudioPolicyProxy app = this.mAudioPolicies.get(pcb.asBinder());
            if (app == null) {
                Slog.w("AudioService", errorMsg + " for pid " + Binder.getCallingPid() + " / uid " + Binder.getCallingUid() + ", unregistered policy");
                return null;
            }
            return app;
        }
    }

    public int addMixForPolicy(AudioPolicyConfig policyConfig, IAudioPolicyCallback pcb) {
        Log.d("AudioService", "addMixForPolicy for " + pcb.asBinder() + " with config:" + policyConfig);
        synchronized (this.mAudioPolicies) {
            AudioPolicyProxy app = checkUpdateForPolicy(pcb, "Cannot add AudioMix in audio policy");
            if (app == null) {
                return -1;
            }
            app.addMixes(policyConfig.getMixes());
            return 0;
        }
    }

    public int removeMixForPolicy(AudioPolicyConfig policyConfig, IAudioPolicyCallback pcb) {
        Log.d("AudioService", "removeMixForPolicy for " + pcb.asBinder() + " with config:" + policyConfig);
        synchronized (this.mAudioPolicies) {
            AudioPolicyProxy app = checkUpdateForPolicy(pcb, "Cannot add AudioMix in audio policy");
            if (app == null) {
                return -1;
            }
            app.removeMixes(policyConfig.getMixes());
            return 0;
        }
    }

    public int setFocusPropertiesForPolicy(int duckingBehavior, IAudioPolicyCallback pcb) {
        Log.d("AudioService", "setFocusPropertiesForPolicy() duck behavior=" + duckingBehavior + " policy " + pcb.asBinder());
        synchronized (this.mAudioPolicies) {
            AudioPolicyProxy app = checkUpdateForPolicy(pcb, "Cannot change audio policy focus properties");
            if (app == null) {
                return -1;
            }
            if (!this.mAudioPolicies.containsKey(pcb.asBinder())) {
                Slog.e("AudioService", "Cannot change audio policy focus properties, unregistered policy");
                return -1;
            }
            boolean z = true;
            if (duckingBehavior == 1) {
                for (AudioPolicyProxy policy : this.mAudioPolicies.values()) {
                    if (policy.mFocusDuckBehavior == 1) {
                        Slog.e("AudioService", "Cannot change audio policy ducking behavior, already handled");
                        return -1;
                    }
                }
            }
            app.mFocusDuckBehavior = duckingBehavior;
            MediaFocusControl mediaFocusControl = this.mMediaFocusControl;
            if (duckingBehavior != 1) {
                z = false;
            }
            mediaFocusControl.setDuckingInExtPolicyAvailable(z);
            return 0;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setExtVolumeController(IAudioPolicyCallback apc) {
        if (!this.mContext.getResources().getBoolean(17956995)) {
            Log.e("AudioService", "Cannot set external volume controller: device not set for volume keys handled in PhoneWindowManager");
            return;
        }
        synchronized (this.mExtVolumeControllerLock) {
            if (this.mExtVolumeController != null && !this.mExtVolumeController.asBinder().pingBinder()) {
                Log.e("AudioService", "Cannot set external volume controller: existing controller");
            }
            this.mExtVolumeController = apc;
        }
    }

    private void dumpAudioPolicies(PrintWriter pw) {
        pw.println("\nAudio policies:");
        synchronized (this.mAudioPolicies) {
            for (AudioPolicyProxy policy : this.mAudioPolicies.values()) {
                pw.println(policy.toLogFriendlyString());
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onDynPolicyMixStateUpdate(String regId, int state) {
        Log.d("AudioService", "onDynamicPolicyMixStateUpdate(" + regId + ", " + state + ")");
        synchronized (this.mAudioPolicies) {
            for (AudioPolicyProxy policy : this.mAudioPolicies.values()) {
                Iterator it = policy.getMixes().iterator();
                while (it.hasNext()) {
                    AudioMix mix = (AudioMix) it.next();
                    if (mix.getRegistration().equals(regId)) {
                        try {
                            policy.mPolicyCallback.notifyMixStateUpdate(regId, state);
                        } catch (RemoteException e) {
                            Log.e("AudioService", "Can't call notifyMixStateUpdate() on IAudioPolicyCallback " + policy.mPolicyCallback.asBinder(), e);
                        }
                        return;
                    }
                }
            }
        }
    }

    public void registerRecordingCallback(IRecordingConfigDispatcher rcdb) {
        boolean isPrivileged = this.mContext.checkCallingPermission("android.permission.MODIFY_AUDIO_ROUTING") == 0;
        this.mRecordMonitor.registerRecordingCallback(rcdb, isPrivileged);
    }

    public void unregisterRecordingCallback(IRecordingConfigDispatcher rcdb) {
        this.mRecordMonitor.unregisterRecordingCallback(rcdb);
    }

    public List<AudioRecordingConfiguration> getActiveRecordingConfigurations() {
        boolean isPrivileged = this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_AUDIO_ROUTING") == 0;
        return this.mRecordMonitor.getActiveRecordingConfigurations(isPrivileged);
    }

    public void disableRingtoneSync(int userId) {
        int callingUserId = UserHandle.getCallingUserId();
        if (callingUserId != userId) {
            this.mContext.enforceCallingOrSelfPermission("android.permission.INTERACT_ACROSS_USERS_FULL", "disable sound settings syncing for another profile");
        }
        long token = Binder.clearCallingIdentity();
        try {
            Settings.Secure.putIntForUser(this.mContentResolver, "sync_parent_sounds", 0, userId);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void registerPlaybackCallback(IPlaybackConfigDispatcher pcdb) {
        boolean isPrivileged = this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_AUDIO_ROUTING") == 0;
        this.mPlaybackMonitor.registerPlaybackCallback(pcdb, isPrivileged);
    }

    public void unregisterPlaybackCallback(IPlaybackConfigDispatcher pcdb) {
        this.mPlaybackMonitor.unregisterPlaybackCallback(pcdb);
    }

    public List<AudioPlaybackConfiguration> getActivePlaybackConfigurations() {
        boolean isPrivileged = this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_AUDIO_ROUTING") == 0;
        return this.mPlaybackMonitor.getActivePlaybackConfigurations(isPrivileged);
    }

    public int trackPlayer(PlayerBase.PlayerIdCard pic) {
        return this.mPlaybackMonitor.trackPlayer(pic);
    }

    public void playerAttributes(int piid, AudioAttributes attr) {
        this.mPlaybackMonitor.playerAttributes(piid, attr, Binder.getCallingUid());
    }

    public void playerEvent(int piid, int event) {
        this.mPlaybackMonitor.playerEvent(piid, event, Binder.getCallingUid());
    }

    public void playerHasOpPlayAudio(int piid, boolean hasOpPlayAudio) {
        this.mPlaybackMonitor.playerHasOpPlayAudio(piid, hasOpPlayAudio, Binder.getCallingUid());
    }

    public void releasePlayer(int piid) {
        this.mPlaybackMonitor.releasePlayer(piid, Binder.getCallingUid());
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class AudioPolicyProxy extends AudioPolicyConfig implements IBinder.DeathRecipient {
        private static final String TAG = "AudioPolicyProxy";
        int mFocusDuckBehavior;
        final boolean mHasFocusListener;
        boolean mIsFocusPolicy;
        final boolean mIsVolumeController;
        final IAudioPolicyCallback mPolicyCallback;

        AudioPolicyProxy(AudioPolicyConfig config, IAudioPolicyCallback token, boolean hasFocusListener, boolean isFocusPolicy, boolean isVolumeController) {
            super(config);
            this.mFocusDuckBehavior = 0;
            this.mIsFocusPolicy = false;
            setRegistration(new String(config.hashCode() + ":ap:" + AudioService.access$14008(AudioService.this)));
            this.mPolicyCallback = token;
            this.mHasFocusListener = hasFocusListener;
            this.mIsVolumeController = isVolumeController;
            if (this.mHasFocusListener) {
                AudioService.this.mMediaFocusControl.addFocusFollower(this.mPolicyCallback);
                if (isFocusPolicy) {
                    this.mIsFocusPolicy = true;
                    AudioService.this.mMediaFocusControl.setFocusPolicy(this.mPolicyCallback);
                }
            }
            if (this.mIsVolumeController) {
                AudioService.this.setExtVolumeController(this.mPolicyCallback);
            }
            connectMixes();
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            synchronized (AudioService.this.mAudioPolicies) {
                Log.i(TAG, "audio policy " + this.mPolicyCallback + " died");
                release();
                AudioService.this.mAudioPolicies.remove(this.mPolicyCallback.asBinder());
            }
            if (this.mIsVolumeController) {
                synchronized (AudioService.this.mExtVolumeControllerLock) {
                    AudioService.this.mExtVolumeController = null;
                }
            }
        }

        String getRegistrationId() {
            return getRegistration();
        }

        void release() {
            if (this.mIsFocusPolicy) {
                AudioService.this.mMediaFocusControl.unsetFocusPolicy(this.mPolicyCallback);
            }
            if (this.mFocusDuckBehavior == 1) {
                AudioService.this.mMediaFocusControl.setDuckingInExtPolicyAvailable(false);
            }
            if (this.mHasFocusListener) {
                AudioService.this.mMediaFocusControl.removeFocusFollower(this.mPolicyCallback);
            }
            long identity = Binder.clearCallingIdentity();
            AudioSystem.registerPolicyMixes(this.mMixes, false);
            Binder.restoreCallingIdentity(identity);
        }

        boolean hasMixAffectingUsage(int usage) {
            Iterator it = this.mMixes.iterator();
            while (it.hasNext()) {
                AudioMix mix = (AudioMix) it.next();
                if (mix.isAffectingUsage(usage)) {
                    return true;
                }
            }
            return false;
        }

        void addMixes(ArrayList<AudioMix> mixes) {
            synchronized (this.mMixes) {
                AudioSystem.registerPolicyMixes(this.mMixes, false);
                add(mixes);
                AudioSystem.registerPolicyMixes(this.mMixes, true);
            }
        }

        void removeMixes(ArrayList<AudioMix> mixes) {
            synchronized (this.mMixes) {
                AudioSystem.registerPolicyMixes(this.mMixes, false);
                remove(mixes);
                AudioSystem.registerPolicyMixes(this.mMixes, true);
            }
        }

        void connectMixes() {
            long identity = Binder.clearCallingIdentity();
            AudioSystem.registerPolicyMixes(this.mMixes, true);
            Binder.restoreCallingIdentity(identity);
        }
    }

    public int dispatchFocusChange(AudioFocusInfo afi, int focusChange, IAudioPolicyCallback pcb) {
        int dispatchFocusChange;
        if (afi == null) {
            throw new IllegalArgumentException("Illegal null AudioFocusInfo");
        }
        if (pcb == null) {
            throw new IllegalArgumentException("Illegal null AudioPolicy callback");
        }
        synchronized (this.mAudioPolicies) {
            if (!this.mAudioPolicies.containsKey(pcb.asBinder())) {
                throw new IllegalStateException("Unregistered AudioPolicy for focus dispatch");
            }
            dispatchFocusChange = this.mMediaFocusControl.dispatchFocusChange(afi, focusChange);
        }
        return dispatchFocusChange;
    }

    public void setFocusRequestResultFromExtPolicy(AudioFocusInfo afi, int requestResult, IAudioPolicyCallback pcb) {
        if (afi == null) {
            throw new IllegalArgumentException("Illegal null AudioFocusInfo");
        }
        if (pcb == null) {
            throw new IllegalArgumentException("Illegal null AudioPolicy callback");
        }
        synchronized (this.mAudioPolicies) {
            if (!this.mAudioPolicies.containsKey(pcb.asBinder())) {
                throw new IllegalStateException("Unregistered AudioPolicy for external focus");
            }
            this.mMediaFocusControl.setFocusRequestResultFromExtPolicy(afi, requestResult);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class AsdProxy implements IBinder.DeathRecipient {
        private final IAudioServerStateDispatcher mAsd;

        AsdProxy(IAudioServerStateDispatcher asd) {
            this.mAsd = asd;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            synchronized (AudioService.this.mAudioServerStateListeners) {
                AudioService.this.mAudioServerStateListeners.remove(this.mAsd.asBinder());
            }
        }

        IAudioServerStateDispatcher callback() {
            return this.mAsd;
        }
    }

    private void checkMonitorAudioServerStatePermission() {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") != 0 && this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_AUDIO_ROUTING") != 0) {
            throw new SecurityException("Not allowed to monitor audioserver state");
        }
    }

    public void registerAudioServerStateDispatcher(IAudioServerStateDispatcher asd) {
        checkMonitorAudioServerStatePermission();
        synchronized (this.mAudioServerStateListeners) {
            if (this.mAudioServerStateListeners.containsKey(asd.asBinder())) {
                Slog.w("AudioService", "Cannot re-register audio server state dispatcher");
                return;
            }
            AsdProxy asdp = new AsdProxy(asd);
            try {
                asd.asBinder().linkToDeath(asdp, 0);
            } catch (RemoteException e) {
            }
            this.mAudioServerStateListeners.put(asd.asBinder(), asdp);
        }
    }

    public void unregisterAudioServerStateDispatcher(IAudioServerStateDispatcher asd) {
        checkMonitorAudioServerStatePermission();
        synchronized (this.mAudioServerStateListeners) {
            AsdProxy asdp = this.mAudioServerStateListeners.remove(asd.asBinder());
            if (asdp == null) {
                Slog.w("AudioService", "Trying to unregister unknown audioserver state dispatcher for pid " + Binder.getCallingPid() + " / uid " + Binder.getCallingUid());
                return;
            }
            asd.asBinder().unlinkToDeath(asdp, 0);
        }
    }

    public boolean isAudioServerRunning() {
        checkMonitorAudioServerStatePermission();
        return AudioSystem.checkAudioFlinger() == 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public float getSoundEffectVolume(int type) {
        switch (type) {
            case 5:
            case 6:
            case 7:
            case 8:
                return 0.5f;
            default:
                if (type == AudioManager.getPlaySoundTypeForSEP(101)) {
                    return 1.0f;
                }
                if (this.mSoundEffectVolume == -1.0f) {
                    try {
                        this.mSoundEffectVolume = Float.parseFloat(AudioSystem.getParameters("situation=1;device=0"));
                    } catch (NumberFormatException e) {
                    }
                    Log.i("AudioService", "get sound effect volume : " + this.mSoundEffectVolume);
                }
                return this.mSoundEffectVolume;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class PreventOverheatState {
        private int mCurDevice;
        private float mLimitedVolumeForOverheat = 1.0f;
        private boolean mState;
        private int mUid;

        public PreventOverheatState(int uid, boolean state, int device) {
            this.mCurDevice = 0;
            this.mUid = uid;
            this.mState = state;
            this.mCurDevice = device;
        }

        void setState(int uid, boolean state, int device) {
            int prevUid = -1;
            if (this.mUid != -1 && this.mUid != uid) {
                prevUid = this.mUid;
            }
            this.mUid = uid;
            this.mState = state;
            this.mCurDevice = device;
            if (Rune.SEC_AUDIO_MULTI_SOUND && prevUid != -1) {
                AudioService.this.mMultiSoundManager.setAppVolumeToNative(prevUid);
            }
        }

        private boolean isSameUid(int uid) {
            return uid % KnoxVpnConstants.USERID_RANGE == this.mUid % KnoxVpnConstants.USERID_RANGE;
        }

        public void setDevice(int device) {
            boolean changed = this.mCurDevice != device;
            if (changed) {
                this.mCurDevice = device;
                setLimitedVolumeForOverheat();
            }
        }

        public String toString() {
            return "mUid:" + this.mUid + ", state:" + this.mState + ", volume : " + this.mLimitedVolumeForOverheat + ", mCureDevice : " + this.mCurDevice;
        }

        public void setLimitedVolumeForOverheat() {
            String result = AudioSystem.getPolicyParameters("volume_for_overheat");
            float volume = 1.0f;
            try {
                volume = Float.parseFloat(result);
            } catch (NullPointerException | NumberFormatException e) {
            }
            this.mLimitedVolumeForOverheat = volume;
            if (Rune.SEC_AUDIO_MULTI_SOUND) {
                AudioService.this.mMultiSoundManager.setAppVolumeToNative(this.mUid);
            }
        }

        private boolean isSpeaker() {
            boolean currentIsSpeaker = (this.mCurDevice & 2) != 0;
            if (AudioService.this.isMultiSoundOn()) {
                int multiSoundDevice = AudioService.this.mMultiSoundManager.getAppDevice(this.mUid);
                if (multiSoundDevice == 0) {
                    return currentIsSpeaker;
                }
                return multiSoundDevice == 2;
            }
            return currentIsSpeaker;
        }

        public float getLimitedVolumeForOverheat(int uid) {
            if (!this.mState || !isSameUid(uid) || !isSpeaker()) {
                return 1.0f;
            }
            return this.mLimitedVolumeForOverheat;
        }
    }

    void onBootCompleted() {
        IBinder b = ServiceManager.getService("DisplaySolution");
        if (b != null) {
            this.mSemDisplaySolutionManager = new SemDisplaySolutionManager(ISemDisplaySolutionManager.Stub.asInterface(b));
        }
        this.mGameStateChecker = new GameStateChecker();
        sendMsg(this.mAudioHandler, 132, 1, 0, 0, null, 30000);
    }

    public void handleCustomMessage(Message msg) {
        switch (msg.what) {
            case 107:
                checkAndSendEarCareInfo();
                return;
            case 108:
                Log.i("AudioService", "Soft reset is received");
                performSoftReset();
                return;
            case 109:
                playSilentModeSound();
                return;
            case 110:
                callVibrateMsg();
                return;
            case 111:
                Log.i("AudioService", "MSG_END_SOUND_MANNER_MODE");
                this.mIsPlaySilentModeOff = false;
                return;
            case 112:
                this.mIsVibrate = false;
                return;
            case 113:
                onSendBecomingNoisyIntent(msg.arg1, msg.arg2);
                return;
            case 114:
                String key = (String) msg.obj;
                String value = Integer.toString(msg.arg1);
                SystemProperties.set(key, value);
                setAudioPropertyOnFooter(key);
                return;
            case 115:
                setFineVolume_internal((FineVolumeState) msg.obj, msg.arg1);
                return;
            case 116:
                notifyDVFSToSoundAlive(msg.arg1);
                return;
            case 117:
                if (this.mIsCPUBoostedForVoIP) {
                    acquireCPUBoost();
                    return;
                } else {
                    releaseCPUBoost();
                    return;
                }
            case 118:
                boolean on = ((Boolean) msg.obj).booleanValue();
                Intent onIntent = new Intent("android.intent.action.MULTISOUND_STATE_CHANGE");
                Log.d("AudioService", "MSG_SET_MULTI_DEVICE_SOUND_ON " + on);
                Settings.Global.putInt(this.mContentResolver, MULTISOUND_STATE, on ? 1 : 0);
                if (on) {
                    this.mMultiSoundManager.enable();
                    int curDevice = AudioSystem.getDevicesForStream(3);
                    if (getPinDevice() == curDevice) {
                        showHeadupNotification(curDevice);
                    }
                } else {
                    this.mMultiSoundManager.disable();
                }
                sendBroadcastToAll(onIntent);
                if (this.mMediaSessionService != null) {
                    this.mMediaSessionService.updateMultiSoundInfo(-1, isMultiSoundOn());
                    return;
                }
                return;
            case 119:
                int devices = msg.arg1;
                int device = msg.arg2;
                if (device == 0) {
                    sendBecomingNoisyIntent(devices);
                    try {
                        Thread.sleep(200L);
                    } catch (Exception e) {
                        Log.e("AudioService", "Error in Thread.sleep() : " + e);
                    }
                }
                this.mMultiSoundManager.setAppDevice(devices, device);
                this.mMediaFocusControl.updateFocusRequester(devices);
                Intent SetDeviceIntent = new Intent("android.intent.action.MULTISOUND_STATE_CHANGE");
                sendBroadcastToAll(SetDeviceIntent);
                if (this.mMediaSessionService != null) {
                    this.mMediaSessionService.updateMultiSoundInfo(-1, isMultiSoundOn());
                }
                showNotification();
                return;
            case 120:
                int minute = msg.arg1;
                if (isMultiSoundOn() && minute != getPinDevice()) {
                    clearHeadupNotificaton();
                    return;
                } else {
                    showNotification();
                    return;
                }
            case 121:
                int minute2 = msg.arg1;
                Settings.Global.putInt(this.mContentResolver, "mode_ringer_time", minute2);
                this.mMuteIntervalMs = (minute2 * 60000) + 60000;
                if (this.mMuteIntervalMs != 0) {
                    checkMuteInterval();
                    return;
                }
                return;
            case 122:
                onRunSoundAssistant();
                return;
            case 123:
                int avcIndex = msg.arg1;
                if (this.mAvrcpAbsVolSupported) {
                    avcIndex = this.mStreamStates[3].getAbsoluteVolumeIndex(avcIndex, 128);
                }
                AudioSystem.setStreamVolumeIndex(3, avcIndex, 128);
                AudioSystem.setPolicyParameters("fine_volume=" + msg.arg2 + ";fine_device=128");
                sendVolumeUpdate(3, msg.arg1, this.mStreamStates[3].getIndex(getDeviceForStream(3)), getFlagsForDeviceConnect(EdmStorageDefs.APP_PKGNAME_INSTALLER_WHITELIST_MASK));
                return;
            case 124:
                checkRCVStateForSARTest();
                return;
            case 125:
                if (this.mMediaFocusControl != null) {
                    this.mMediaFocusControl.setDevice(msg.arg1);
                    if (this.mMediaSessionService != null) {
                        this.mMediaSessionService.updateMultiSoundInfo(msg.arg1, isMultiSoundOn());
                        return;
                    }
                    return;
                }
                return;
            case 126:
                Intent eventIntent = (Intent) msg.obj;
                sendBroadcastToAll(eventIntent);
                return;
            case 127:
                if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER) {
                    if (msg.arg1 == 0) {
                        this.mSensorThread.stopSensor();
                        return;
                    } else {
                        this.mSensorThread.startSensor();
                        return;
                    }
                }
                return;
            case 128:
                if (msg.arg1 == 1 && this.mSensorThread.isClosed()) {
                    AudioSystem.setParameters("ProximitySensorClosed=1");
                    return;
                }
                return;
            case 129:
                sendBroadcastToSoundEventReceiver(16, msg.arg1, "");
                return;
            case 130:
                if (Rune.SEC_AUDIO_RECORDING_FAILED_POPUP) {
                    checkRecordingFailedPopup(msg.arg1, msg.arg2);
                    return;
                }
                return;
            case 131:
                onInitDualSpeaker();
                return;
            case 132:
                onInitSoundAssistant();
                return;
            default:
                return;
        }
    }

    private void checkRecordingFailedPopup(int requestUid, int activeUid) {
        String[] packageName;
        if (SemGameManager.isAvailable()) {
            for (String packageName2 : getPackageName(activeUid)) {
                boolean isGame = false;
                try {
                    isGame = SemGameManager.isGamePackage(packageName2);
                } catch (IllegalArgumentException ex) {
                    Log.w("AudioService", "SemGameManager IllegalArgumentException: " + ex);
                }
                if (isGame) {
                    String requestRecApp = this.mPopupManager.getAppName(requestUid);
                    String activeRecApp = this.mPopupManager.getAppName(activeUid);
                    String notiMsg = this.mContext.getString(17041573, requestRecApp, activeRecApp);
                    String appNameKey = requestRecApp + activeRecApp;
                    if (this.mActivityManager == null) {
                        this.mActivityManager = (ActivityManager) this.mContext.getSystemService("activity");
                    }
                    int activePid = 0;
                    try {
                        activePid = this.mActivityManager.getPidOfUid(activeUid)[0];
                    } catch (Exception ex2) {
                        Log.w("AudioService", "Failed getPidOfUid: " + ex2);
                    }
                    if (this.mPopupManager.showPopupWindow(notiMsg, appNameKey, activePid)) {
                        String log = "MSG_RECORDER_ERR_CALLBACK requestRecApp = " + requestRecApp + " activeRecApp = " + activeRecApp;
                        this.mRecFailPopupLogger.log(new AudioEventLogger.StringEvent(log));
                        Log.e("AudioService", log);
                        return;
                    }
                    return;
                }
            }
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    private class SamsungBroadcastReceiver extends BroadcastReceiver {
        private SamsungBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            int uid;
            int tempUid;
            String action = intent.getAction();
            if ("com.sec.factory.app.factorytest.FTA_ON".equals(action)) {
                AudioSystem.setPolicyParameters("factoryteststate=1");
            } else if ("com.sec.factory.app.factorytest.FTA_OFF".equals(action)) {
                AudioSystem.setPolicyParameters("factoryteststate=0");
            } else {
                if ("com.samsung.flipfolder.OPEN".equals(action)) {
                    AudioService.this.mIsFolderOpen = intent.getBooleanExtra("flipOpen", false);
                    AudioSystem.setParameters("folder_open=" + AudioService.this.mIsFolderOpen);
                } else if ("com.samsung.intent.action.SETTINGS_SOFT_RESET".equals(action)) {
                    AudioService.sendMsg(AudioService.this.mAudioHandler, 108, 2, 0, 0, null, 0);
                } else if ("com.android.phone.action.PERSONALISE_CALL_SOUND_CHANGED".equals(action)) {
                    AudioService.this.mNbQualityMode = intent.getIntExtra("value", 0);
                    Log.i("AudioService", "on receive : mNbQualityMode " + AudioService.this.mNbQualityMode);
                    AudioService.this.setNbQualityMode(AudioService.this.mNbQualityMode);
                } else if ("com.samsung.intent.action.WIFI_DISPLAY_SOURCE_STATE".equals(action)) {
                    int state = intent.getIntExtra("state", 0);
                    int musicDevice = AudioService.this.getDeviceForStream(3);
                    boolean isConnected = AudioService.this.mIsWifiDisplayConnected;
                    Log.i("AudioService", "WifiDisplay device state:" + state + " isConnected:" + isConnected);
                    AudioService.this.checkSendBecomingNoisyIntent(32768, state, musicDevice);
                    if (state == 1 && !isConnected) {
                        Log.d("AudioService", "WifiDisplay is connected.");
                        AudioSystem.setParameters("audioParam;ScreenMirroringState=on");
                        AudioService.this.mIsWifiDisplayConnected = true;
                        AudioService.this.mConnectedDevices.put(AudioService.this.makeDeviceListKey(32768, "0"), new DeviceListSpec(32768, "0", "remote-submix"));
                        AudioService.this.mDisplayManager.semRegisterDeviceStatusListener(AudioService.this.mSemDeviceStatusListener, null);
                    } else if (state == 0 && isConnected) {
                        Log.d("AudioService", "WifiDisplay is disconnected.");
                        AudioSystem.setParameters("audioParam;ScreenMirroringState=off");
                        AudioService.this.mIsWifiDisplayConnected = false;
                        AudioService.this.mConnectedDevices.remove(AudioService.this.makeDeviceListKey(32768, "0"));
                        if (AudioService.this.mSplitSound && AudioService.this.isInCommunication()) {
                            AudioService.sendMsg(AudioService.this.mAudioHandler, 15, 0, 0, 0, null, 0);
                        }
                        AudioService.this.mSplitSound = false;
                        AudioSystem.setPolicyParameters("SplitSound=" + AudioService.this.mSplitSound);
                        AudioService.this.mDisplayManager.semUnregisterDeviceStatusListener(AudioService.this.mSemDeviceStatusListener);
                    }
                    if (Rune.SEC_AUDIO_DISPLAY_VOLUME_CONTROL) {
                        if (state == 1) {
                            AudioService.this.mIsSupportDisplayVolumeControl = intent.getBooleanExtra("isSupportDisplayVolumeControl", false);
                        } else {
                            AudioService.this.mIsSupportDisplayVolumeControl = false;
                        }
                        AudioService.this.mScreenSharingStateResumed = AudioService.this.mIsSupportDisplayVolumeControl;
                        Log.d("AudioService", "isSupportDisplayVolumeControl:" + AudioService.this.mIsSupportDisplayVolumeControl);
                    }
                } else if ("com.samsung.intent.action.WIFI_DISPLAY_VOLUME_SUPPORT_CHANGED".equals(action)) {
                    int state2 = intent.getIntExtra("state", 0);
                    if (Rune.SEC_AUDIO_DISPLAY_VOLUME_CONTROL) {
                        if (state2 == 1) {
                            AudioService.this.mIsSupportDisplayVolumeControl = intent.getBooleanExtra("isSupportDisplayVolumeControl", false);
                        } else {
                            AudioService.this.mIsSupportDisplayVolumeControl = false;
                        }
                        AudioService.this.mScreenSharingStateResumed = AudioService.this.mIsSupportDisplayVolumeControl;
                        Log.d("AudioService", "onReceive SEM_WIFI_DISPLAY_VOLUME_SUPPORT_CHANGED isSupportDisplayVolumeControl:" + AudioService.this.mIsSupportDisplayVolumeControl);
                    }
                } else if ("com.samsung.intent.action.GOOGLE_CAST_MIRRORING_CONNECTION_CHANGED".equals(action)) {
                    if (intent.getIntExtra("state", 0) == 1) {
                        Log.d("AudioService", "GoogleCastDevice is connected.");
                        AudioService.this.mConnectedDevices.put(AudioService.this.makeDeviceListKey(32768, "0"), new DeviceListSpec(32768, "0", "remote-submix"));
                        AudioSystem.setPolicyParameters("ScreenMirroringState=on");
                        return;
                    }
                    Log.d("AudioService", "GoogleCastDevice is disconnected.");
                    AudioService.this.mConnectedDevices.remove(AudioService.this.makeDeviceListKey(32768, "0"));
                    AudioSystem.setPolicyParameters("ScreenMirroringState=off");
                } else if ("com.samsung.intent.action.WIFIDISPLAY_NOTI_CONNECTION_MODE".equals(action)) {
                    int connectionMode = intent.getIntExtra("CONNECTION_MODE", 0);
                    AudioSystem.setPolicyParameters("wfdFixedVolume=" + connectionMode);
                } else if ("android.intent.action.ACTION_SHUTDOWN".equals(action)) {
                    AudioService.this.setAudioServiceConfig(AudioSystem.getParameters(EdmStorageDefs.WEBFILTER_LOGGING));
                    AudioService.this.checkSystemAudioFiles();
                    AudioSystem.setParameters("dev_shutdown=true");
                    int device = AudioService.this.getDeviceForStream(3);
                    if (AudioService.this.mStreamStates[3].getIndex(device) == 0 && AudioService.this.mFineMediaVolume.getIndex(device) != 0) {
                        AudioService.this.setStreamVolume(3, 1, 0, AudioService.this.mContext.getOpPackageName());
                    }
                } else if ("android.intent.action.LOCALE_CHANGED".equals(action)) {
                    if (Rune.SEC_AUDIO_MULTI_SOUND) {
                        AudioService.this.updateAudioServiceNotificationChannel();
                    }
                } else if ("com.samsung.android.app.audio.epinforequest".equals(action)) {
                    try {
                        int state3 = intent.getIntExtra("state", 0);
                        if (state3 == -1) {
                            AudioSystem.setParameters("earcare=reset");
                            return;
                        }
                        AudioService.this.mIsEnableVolumeMonitor = state3 == 1;
                        AudioService.this.checkAndSendEarCareInfo();
                    } catch (Exception e) {
                        Log.e("AudioService", "Exception:" + e);
                    }
                } else if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER && "com.samsung.android.intent.action.AUDIO_DUAL_SPEAKER_CHANGED".equals(action)) {
                    AudioService.this.mDualSpkEnable = intent.getIntExtra("value", 1);
                    AudioService.this.setDualSpeaker(AudioService.this.mDualSpkEnable);
                } else if ("com.sec.tms.audio.server".equals(action)) {
                    int state4 = intent.getIntExtra("state", 0);
                    String app = intent.getStringExtra(DLPConstants.APP);
                    Log.d("AudioService", "ACTION_TMS_STATE_CHANGED app: " + app + " state : " + state4);
                    if (ApnSettingsPolicy.Carriers.SERVER.equals(app)) {
                        boolean isConnected2 = AudioService.this.checkDeviceConnected(536870912);
                        if (state4 == 2 && isConnected2) {
                            AudioSystem.setDeviceConnectionState(536870912, 0, "", "");
                            AudioService.this.mConnectedDevices.remove(AudioService.this.makeDeviceListKey(536870912, ""));
                        } else if (state4 == 1 && !isConnected2) {
                            AudioSystem.setDeviceConnectionState(536870912, 1, "", "");
                            AudioService.this.mConnectedDevices.put(AudioService.this.makeDeviceListKey(536870912, ""), new DeviceListSpec(536870912, "", ""));
                        }
                    } else if ("client".equals(app)) {
                        boolean isConnected3 = AudioService.this.checkDeviceConnected(-2013265920);
                        if (state4 == 2 && isConnected3) {
                            AudioSystem.setDeviceConnectionState(-2013265920, 0, "", "");
                            AudioService.this.mConnectedDevices.remove(AudioService.this.makeDeviceListKey(-2013265920, ""));
                        } else if (state4 == 1 && !isConnected3) {
                            AudioSystem.setDeviceConnectionState(-2013265920, 1, "", "");
                            AudioService.this.mConnectedDevices.put(AudioService.this.makeDeviceListKey(-2013265920, ""), new DeviceListSpec(-2013265920, "", ""));
                        }
                    }
                } else if ("com.sec.media.action.AUDIOCORE_LOGGING".equals(action)) {
                    Log.i("AudioService", "[logging] receive ACTION_AUDIOCORE_LOGGING");
                    AudioService.this.setAudioServiceConfig(AudioSystem.getParameters(EdmStorageDefs.WEBFILTER_LOGGING));
                } else if (HWParamConst.ACTION_HQM_STATUS_DHR_HQM_REFRESH_REQ.equals(action)) {
                    Log.i("AudioService", "[logging] receive ACTION_AUDIOCORE_LOGGING");
                    AudioService.this.mIsBigDataApp = true;
                    AudioService.this.setAudioServiceConfig(AudioSystem.getParameters("logging;bigdataApp"));
                } else if ("com.sec.media.action.mute_interval".equals(action)) {
                    AudioService.this.checkMuteInterval();
                    Log.i("AudioService", "[Mute Interval] remaining time=" + (AudioService.this.getRemainingMuteIntervalMs() / 60000) + " mins");
                } else if (ExclusiveTaskManagerService.TURN_OFF_ALL_SOUND_BROADCAST.equals(action)) {
                    try {
                        AudioService.this.mAllSoundMute = intent.getIntExtra("mute", 0);
                        AudioService.this.setAllSoundMute();
                    } catch (Exception e2) {
                    }
                } else if (Rune.SEC_AUDIO_DISPLAY_VOLUME_CONTROL && "com.samsung.intent.action.DLNA_STATUS_CHANGED".equals(action)) {
                    int status = intent.getIntExtra("status", 0);
                    int player_type = intent.getIntExtra("player_type", -1);
                    if (status != 1 || (player_type != 0 && player_type != 2 && player_type != 3)) {
                        AudioService.this.mIsDLNAEnabled = false;
                    } else {
                        AudioService.this.mIsDLNAEnabled = true;
                    }
                    Log.d("AudioService", "mIsDLNAEnabled:" + AudioService.this.mIsDLNAEnabled);
                } else if ("com.sec.android.intent.action.SPLIT_SOUND".equals(action)) {
                    try {
                        AudioService.this.mSplitSound = intent.getBooleanExtra("enabled", false);
                    } catch (Exception e3) {
                        Log.e("AudioService", "Exception:" + e3);
                    }
                } else if ("com.samsung.intent.action.WB_AMR".equals(action)) {
                    int extra_state = intent.getIntExtra("EXTRA_STATE", 0);
                    if (extra_state == 8) {
                        Log.d("AudioService", "wb_amr swb");
                        AudioSystem.setParameters("band=SWB");
                    } else if (extra_state == 1) {
                        Log.d("AudioService", "wb_amr wb");
                        AudioSystem.setParameters("band=WB");
                    } else {
                        Log.d("AudioService", "wb_amr nb");
                        AudioSystem.setParameters("band=NB");
                    }
                } else if ("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED".equals(action)) {
                    Log.i("AudioService", "ACTION_SUBINFO_RECORD_UPDATED received");
                    AudioService.this.mMediaFocusControl.unregisterPhoneStateListener(AudioService.this.mContext);
                    AudioService.this.mMediaFocusControl.registerPhoneStateListener(AudioService.this.mContext);
                } else if ("android.intent.action.PACKAGE_REMOVED".equals(action) && !intent.getBooleanExtra("android.intent.extra.REPLACING", false)) {
                    String pkgName = intent.getData().getSchemeSpecificPart();
                    int uid2 = intent.getIntExtra("android.intent.extra.UID", -1);
                    Log.d("AudioService", pkgName + "(" + uid2 + ") is removed");
                    if (!Rune.SEC_AUDIO_MULTI_SOUND) {
                        AudioService.this.mMultiSoundManager.setAppVolume(uid2, 100);
                    } else if ("com.samsung.android.oneconnect".equals(pkgName)) {
                        AudioService.this.mMultiSoundManager.resetPinDevice();
                    } else {
                        if (AudioService.this.mMultiSoundManager.getAppDevice(uid2, true) != 0 && AudioService.this.mMultiSoundManager.removeItem(uid2)) {
                            AudioService.sendMsg(AudioService.this.mAudioHandler, 118, 2, 0, 0, false, 0);
                            Settings.System.putString(AudioService.this.mContentResolver, AudioService.MULTISOUND_APP, null);
                            AudioService.this.showMultisoundToast();
                        }
                        if (AudioService.this.mIgnoreAudioFocusUid == uid2) {
                            AudioService.this.mIgnoreAudioFocusUid = -1;
                            AudioService.this.mMediaFocusControl.setIgnoreAudioFocus(uid2, false);
                        }
                        if (AudioService.this.mCategorizer != null) {
                            AudioService.this.mCategorizer.removePackage(uid2);
                        }
                        AudioService.this.mMultiSoundManager.setAppVolume(uid2, 100);
                    }
                    AudioService.this.mAppVolumeFromSoundAssistant.delete(uid2);
                    if ("com.samsung.android.soundassistant".equals(pkgName)) {
                        AudioService.this.clearSoundAssistantSettings();
                    }
                    AudioService.this.unSetSoundSettingEventBroadcastIntent(pkgName);
                } else if ("android.intent.action.PACKAGE_ADDED".equals(action)) {
                    String pkgName2 = intent.getData().getSchemeSpecificPart();
                    if (Rune.SEC_AUDIO_MULTI_SOUND && (tempUid = AudioService.this.getUidForPackage(pkgName2)) != 0) {
                        String[] appList = AudioService.this.getSelectedAppList();
                        boolean duplicateApp = false;
                        for (String packageName : appList) {
                            if (pkgName2.contains(packageName) || packageName.contains(pkgName2)) {
                                duplicateApp = true;
                                break;
                            }
                        }
                        if (!duplicateApp && AudioService.this.isInAllowedList(pkgName2)) {
                            AudioService.this.mCategorizer.putPackage(tempUid, pkgName2);
                        }
                    }
                    if ("com.samsung.android.soundassistant".equals(pkgName2) && (uid = intent.getIntExtra("android.intent.extra.UID", -1)) != -1) {
                        AudioService.this.registBackgroundWhitelistForSoundAssistant(uid);
                    }
                } else if ("android.intent.action.TurnOff_MultiSound".equals(action)) {
                    AudioService.sendMsg(AudioService.this.mAudioHandler, 118, 2, 0, 0, false, 0);
                    Intent intentMultiSound = new Intent();
                    intentMultiSound.setPackage("com.samsung.android.setting.multisound");
                    intentMultiSound.setAction("com.samsung.intent.action.MULTISOUND_STATE_CHANGED");
                    intentMultiSound.putExtra("enabled", false);
                    AudioService.this.sendBroadcastToAll(intentMultiSound);
                } else if ("android.intent.action.SAS_NOTIFICATION_CLEAR".equals(action)) {
                    AudioService.this.clearNotification();
                } else if ("android.intent.action.Headup_Notification_Close".equals(action)) {
                    AudioService.this.clearHeadupNotificaton();
                } else if ("android.intent.action.Headup_Notification_ChangeDevice".equals(action)) {
                    int priorityDevice = AudioService.this.getPriorityDevice(AudioService.this.getPinDevice());
                    AudioService.this.setDeviceToForceByUser(priorityDevice, AudioService.this.getAddressForDevice(priorityDevice));
                    AudioService.this.clearHeadupNotificaton();
                } else if ("com.samsung.sec.knox.KNOX_MODE_CHANGED".equals(action)) {
                    int lockedPersonaId = intent.getIntExtra("persona_id", -1);
                    int userIdForCurrentAudioFocus = AudioService.this.mMediaFocusControl.getUserIdForCurrentAudioFocus();
                    int gainRequest = AudioService.this.mMediaFocusControl.getGainRequestForCurrentAudioFocus();
                    if (gainRequest == 2 || gainRequest == 3 || gainRequest == 4) {
                        return;
                    }
                    Log.d("AudioService", "AS.onReceive() persona not active: lockedPersonaId=" + lockedPersonaId + "; userIdForCurrentAudioFocus=" + userIdForCurrentAudioFocus);
                    if (lockedPersonaId != -1 && lockedPersonaId == userIdForCurrentAudioFocus && !SemPersonaManager.isSecureFolderId(lockedPersonaId)) {
                        AudioService.sendMsg(AudioService.this.mAudioHandler, 113, 0, 0, lockedPersonaId, null, 0);
                        AudioService.this.mMediaFocusControl.discardAudioFocusOwner();
                        int userIdForCurrentAudioFocus2 = AudioService.this.mMediaFocusControl.getUserIdForCurrentAudioFocus();
                        if (userIdForCurrentAudioFocus2 != -1 && userIdForCurrentAudioFocus2 != lockedPersonaId) {
                            AudioService.this.mMediaFocusControl.notifyOtherTopOfAudioFocusStack();
                        }
                    }
                } else if ("com.samsung.intent.action.EMERGENCY_STATE_CHANGED".equals(action)) {
                    int reason = intent.getIntExtra("reason", 0);
                    if (reason == 3 && AudioService.this.mEmergencyManager.checkModeType(512)) {
                        int userIdForCurrentAudioFocus3 = AudioService.this.mMediaFocusControl.getUserIdForCurrentAudioFocus();
                        if (SemPersonaManager.isKnoxId(userIdForCurrentAudioFocus3)) {
                            switch (AudioService.this.mMediaFocusControl.getGainRequestForCurrentAudioFocus()) {
                                case 2:
                                case 3:
                                case 4:
                                    return;
                                default:
                                    AudioService.sendMsg(AudioService.this.mAudioHandler, 113, 0, 0, userIdForCurrentAudioFocus3, null, 0);
                                    return;
                            }
                        }
                    }
                } else if ("android.settings.MONO_AUDIO_CHANGED".equals(action)) {
                    try {
                        AudioService.this.mMonoMode = intent.getIntExtra(SemCamera.Parameters.EFFECT_MONO, 0);
                        AudioService.this.setMonoMode(AudioService.this.mMonoMode);
                        AudioService.this.mSettingHelper.removeValue("mono_audio_db");
                    } catch (Exception e4) {
                        Log.e("AudioService", "Exception:", e4);
                    }
                } else if ("sec.app.policy.UPDATE.audio".equals(action)) {
                    Log.i("AudioService", "SoundAppPolicy reload white list");
                    AudioService.this.reloadWhitelist();
                } else if ("com.android.server.sepunion.semgoodcatchservice.GOOD_CATCH_STATE_CHANGED".equals(action)) {
                    if (AudioService.this.mSemGoodCatchManager == null) {
                        AudioService.this.mSemGoodCatchManager = new SemGoodCatchManager(AudioService.this.mContext, "AudioService", AudioService.GOODCATCH_MODE, AudioService.this.mGoodCatchStateListener);
                        Log.d("AudioService", "SemGoodCatchManager is created");
                    }
                } else if ("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED".equals(action)) {
                    int state5 = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 0);
                    BluetoothDevice btDevice = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                    if (state5 == 2) {
                        AudioService.this.setBtScoActiveDevice(btDevice);
                    }
                }
            }
        }
    }

    private void setupCustomRoutine() {
        this.mPackageManager = this.mContext.getPackageManager();
        this.mActivityManager = (ActivityManager) this.mContext.getSystemService("activity");
        this.mAlarmManager = (AlarmManager) this.mContext.getSystemService("alarm");
        this.mDisplayManager = (DisplayManager) this.mContext.getSystemService("display");
        this.mEmergencyManager = SemEmergencyManager.getInstance(this.mContext);
        this.mPlaybackMonitor.setAudioHandler(this.mAudioHandler);
        boolean configVolumeKeyAction = "Silent".equals(Rune.SEC_AUDIO_CONFIG_ACTION_VOLUME_KEY);
        boolean volumeDownToEnterSilent = this.mHasVibrator ? configVolumeKeyAction : true;
        this.mVolumePolicy = new VolumePolicy(volumeDownToEnterSilent, true, true, 400);
        if (Rune.SEC_AUDIO_RECORDING_FAILED_POPUP) {
            this.mRecordMonitor.setAudioHandler(this.mAudioHandler);
        }
        this.mFineMediaVolume = new FineVolumeState();
        this.mDesktopModeManager = (SemDesktopModeManager) this.mContext.getSystemService("desktopmode");
        if (this.mDesktopModeManager != null) {
            SemDesktopModeManager semDesktopModeManager = this.mDesktopModeManager;
            SemDesktopModeManager.registerListener(this.desktopModeListener);
            Log.d("AudioService", "DEX registration is successful");
        } else {
            Log.d("AudioService", "DEX registration is failed");
        }
        this.mVolumeController = new MultiVolumeController();
        IntentFilter intentFilterForSamsung = new IntentFilter();
        addSamsungIntentFilter(intentFilterForSamsung);
        this.mContext.registerReceiverAsUser(this.mSamsungReceiver, UserHandle.ALL, intentFilterForSamsung, null, null);
        IntentFilter intentForKnoxMode = new IntentFilter("com.samsung.sec.knox.KNOX_MODE_CHANGED");
        this.mContext.registerReceiverAsUser(this.mSamsungReceiver, UserHandle.ALL, intentForKnoxMode, "com.samsung.container.OBSERVER", null);
        if (Rune.SEC_AUDIO_MULTI_SOUND) {
            IntentFilter intentForMultiSound = new IntentFilter();
            addMultiSoundIntentFilter(intentForMultiSound);
            this.mContext.registerReceiverAsUser(this.mSamsungReceiver, UserHandle.ALL, intentForMultiSound, null, null);
            IntentFilter intentForMultiSoundNoti = new IntentFilter();
            addMultiSoundNotificationIntentFilter(intentForMultiSoundNoti);
            this.mContext.registerReceiverAsUser(this.mSamsungReceiver, UserHandle.ALL, intentForMultiSoundNoti, null, null);
        }
        this.mSemDeviceStatusListener = new SemDeviceStatusListener() { // from class: com.android.server.audio.AudioService.8
            public void onConnectionStatusChanged(int status) {
            }

            public void onQosLevelChanged(int level) {
            }

            public void onDlnaConnectionStatusChanged(boolean connected) {
            }

            public void onScreenSharingStatusChanged(int status) {
                if (status == 6) {
                    if (Rune.SEC_AUDIO_DISPLAY_VOLUME_CONTROL && AudioService.this.mIsSupportDisplayVolumeControl) {
                        AudioService.this.mScreenSharingStateResumed = true;
                    }
                } else if (status == 7) {
                    if (Rune.SEC_AUDIO_DISPLAY_VOLUME_CONTROL && AudioService.this.mIsSupportDisplayVolumeControl) {
                        AudioService.this.mScreenSharingStateResumed = false;
                    }
                    if (AudioService.this.mSplitSound) {
                        AudioService.this.mSplitSound = false;
                        AudioSystem.setPolicyParameters("SplitSound=" + AudioService.this.mSplitSound);
                        AudioService.this.mMediaFocusControl.checkSplitSoundAudioFocus();
                    }
                }
                Log.d("AudioService", "onScreenSharingStatusChanged, status = " + AudioService.this.mScreenSharingStateResumed);
            }
        };
        this.mIsFolderOpen = this.mContext.getResources().getConfiguration().hardKeyboardHidden == 1;
        AudioSystem.setParameters("folder_open=" + this.mIsFolderOpen);
        setNbQualityMode(this.mNbQualityMode);
        setSoundBalance();
        this.mIsBtOffloadEnabled = 0;
        if (this.mAllSoundMute == 1) {
            setAllSoundMute();
        }
        setGlobalMySound(this.mMySoundEnabled);
        if (!Rune.SEC_AUDIO_PERFORMANCE_MODE || this.mPerformanceMode != 2) {
            setGlobalUpscaler(this.mUpscalerEnabled);
        } else {
            setGlobalUpscaler(this.mPerformanceUpScalerMode);
        }
        this.mPhoneType = null;
        this.mBtSampleRate = null;
        this.mBtNrec = null;
        this.mUhqBtSampleRate = 0;
        this.mConnectedDevices.put(makeDeviceListKey(2, ""), new DeviceListSpec(2, "Speaker", ""));
        setMonoMode(this.mMonoMode);
        this.mDexState = false;
        this.mDexConnectedState = false;
        this.mDexPadConnectedState = false;
        this.mVolumeController = new MultiVolumeController();
        if (this.mSettingHelper.getIntValue("APP_LIST_VERSION", 0) == 0) {
            setDefaultWhiteList();
        }
        Log.i("AudioService", "SoundAppPolicy APP_LIST_VERSION =" + this.mSettingHelper.getIntValue("APP_LIST_VERSION", 0));
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class SensorThread extends Thread implements SensorEventListener, SemContextListener {
        private static final String SENSOR_SERVICE = "sensor";
        private boolean mFlatMotionSensorEnabled;
        private Sensor mProximitySensor;
        private SemContextManager mSemContextManager;
        private Handler mSensorHandler;
        private SensorManager mSensorManager;
        private boolean mbClosed;
        private boolean mbListenerStarted;

        SensorThread() {
            super("SensorThread");
            this.mbListenerStarted = false;
            this.mbClosed = false;
            this.mSensorManager = (SensorManager) AudioService.this.mContext.getSystemService(SENSOR_SERVICE);
            this.mProximitySensor = this.mSensorManager.getDefaultSensor(8);
            if (AudioService.this.mContext.getPackageManager().hasSystemFeature("com.sec.feature.sensorhub")) {
                this.mSemContextManager = (SemContextManager) AudioService.this.mContext.getSystemService("scontext");
                if (this.mSemContextManager != null) {
                    this.mFlatMotionSensorEnabled = this.mSemContextManager.isAvailableService(20);
                }
            }
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            Looper.prepare();
            this.mSensorHandler = new Handler();
            Looper.loop();
        }

        @Override // android.hardware.SensorEventListener
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override // android.hardware.SensorEventListener
        public void onSensorChanged(SensorEvent event) {
            synchronized (this) {
                if (AudioService.this.mDualSpkEnable != 0 && AudioService.this.getMode() > 1) {
                    if (event.values[0] == SdpManagerService.SDK_NOT_SUPPORTED && !"GAMEVOIP".equals(AudioService.this.mAppMode)) {
                        AudioSystem.setParameters("ProximitySensorClosed=1");
                        this.mbClosed = true;
                    } else {
                        AudioSystem.setParameters("ProximitySensorClosed=0");
                        this.mbClosed = false;
                    }
                }
            }
        }

        public void onSemContextChanged(SemContextEvent event) {
            SemContext context = event.semContext;
            if (AudioService.this.mDualSpkEnable != 0 && context.getType() == 20) {
                int device = AudioService.this.getDeviceForStream(3);
                if (!AudioService.this.mFlatMotionChangedDuringRingtone) {
                    if (device == 2 || AudioService.this.getMode() > 1) {
                        SemContextFlatMotion flatMotion = event.getFlatMotionContext();
                        int action = flatMotion.getAction();
                        switch (action) {
                            case 1:
                                AudioSystem.setParameters("FlatMotion=1");
                                return;
                            case 2:
                                AudioSystem.setParameters("FlatMotion=0");
                                if (AudioService.this.getMode() == 1) {
                                    AudioService.this.mFlatMotionChangedDuringRingtone = true;
                                    return;
                                }
                                return;
                            default:
                                return;
                        }
                    }
                }
            }
        }

        public synchronized void startSensor() {
            if (!this.mbListenerStarted) {
                this.mSensorManager.registerListener(this, this.mProximitySensor, 3, this.mSensorHandler);
                if (this.mFlatMotionSensorEnabled) {
                    this.mSemContextManager.registerListener(this, 20);
                }
                this.mbListenerStarted = true;
            }
        }

        public synchronized void stopSensor() {
            if (this.mbListenerStarted) {
                this.mSensorManager.unregisterListener(this, this.mProximitySensor);
                if (this.mFlatMotionSensorEnabled) {
                    this.mSemContextManager.unregisterListener(this, 20);
                    AudioSystem.setParameters("FlatMotion=1");
                }
                this.mbListenerStarted = false;
            }
        }

        public boolean isClosed() {
            return this.mbClosed;
        }

        public boolean isStarted() {
            return this.mbListenerStarted;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public static class GameStateChecker implements IBinder.DeathRecipient {
        private static final int GAME = 1;
        private IGameManagerService mGameManagerService = null;
        private boolean mGameIsForeground = false;

        public GameStateChecker() {
            init();
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            Log.w("AudioService", "Gamemanager is died");
            this.mGameIsForeground = false;
            this.mGameManagerService = null;
        }

        private void init() {
            this.mGameManagerService = IGameManagerService.Stub.asInterface(ServiceManager.getService("gamemanager"));
            if (this.mGameManagerService == null) {
                Log.w("AudioService", "game manager load failed");
                return;
            }
            try {
                this.mGameManagerService.asBinder().linkToDeath(this, 0);
                try {
                    this.mGameIsForeground = this.mGameManagerService.identifyForegroundApp() == 1;
                    this.mGameManagerService.registerCallback(new IGameManagerCallback.Stub() { // from class: com.android.server.audio.AudioService.GameStateChecker.1
                        public void onGameResume(String pkgName) {
                            Log.i("AudioService", "onGameResume");
                            GameStateChecker.this.mGameIsForeground = true;
                        }

                        public void onGamePause(String pkgName) {
                            Log.i("AudioService", "onGamePause");
                            GameStateChecker.this.mGameIsForeground = false;
                        }

                        public void onGameAdded(String pkgName) {
                        }

                        public void onModeChanged(int mode) {
                        }
                    });
                } catch (RemoteException e) {
                    Log.e("AudioService", "failed get game state", e);
                }
            } catch (RemoteException e2) {
                Log.w("AudioService", "fail link to death", e2);
                this.mGameManagerService = null;
            }
        }

        public boolean isGameForeground() {
            return this.mGameIsForeground;
        }

        public String toString() {
            return "mGameIsForeground=" + this.mGameIsForeground + ", mGameManagerService=" + this.mGameManagerService;
        }
    }

    void addMultiSoundIntentFilter(IntentFilter intentFilter) {
        intentFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        intentFilter.addAction("android.intent.action.PACKAGE_ADDED");
        intentFilter.addDataScheme("package");
    }

    void addMultiSoundNotificationIntentFilter(IntentFilter intentFilter) {
        intentFilter.addAction("android.intent.action.SAS_NOTIFICATION_CLEAR");
        intentFilter.addAction("android.intent.action.TurnOff_MultiSound");
        intentFilter.addAction("android.intent.action.Headup_Notification_Close");
        intentFilter.addAction("android.intent.action.Headup_Notification_ChangeDevice");
    }

    void addSamsungIntentFilter(IntentFilter intentFilter) {
        intentFilter.addAction("android.intent.action.USER_STARTED");
        intentFilter.addAction("android.intent.action.LOCALE_CHANGED");
        intentFilter.addAction("android.intent.action.ACTION_SHUTDOWN");
        intentFilter.addAction("android.intent.action.PHONE_STATE");
        intentFilter.addAction("com.samsung.intent.action.WIFI_DISPLAY_SOURCE_STATE");
        intentFilter.addAction("com.samsung.intent.action.WIFI_DISPLAY_VOLUME_SUPPORT_CHANGED");
        intentFilter.addAction("com.samsung.intent.action.GOOGLE_CAST_MIRRORING_CONNECTION_CHANGED");
        intentFilter.addAction("com.samsung.intent.action.WIFIDISPLAY_NOTI_CONNECTION_MODE");
        intentFilter.addAction("com.sec.android.intent.action.SPLIT_SOUND");
        intentFilter.addAction("com.samsung.android.app.audio.epinforequest");
        intentFilter.addAction("com.samsung.intent.action.SETTINGS_SOFT_RESET");
        intentFilter.addAction("com.sec.tms.audio.server");
        intentFilter.addAction("com.samsung.intent.action.EMERGENCY_STATE_CHANGED");
        intentFilter.addAction("com.sec.factory.app.factorytest.FTA_ON");
        intentFilter.addAction("com.sec.factory.app.factorytest.FTA_OFF");
        if (Rune.SEC_AUDIO_DISPLAY_VOLUME_CONTROL) {
            intentFilter.addAction("com.samsung.intent.action.DLNA_STATUS_CHANGED");
        }
        if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER) {
            intentFilter.addAction("com.samsung.android.intent.action.AUDIO_DUAL_SPEAKER_CHANGED");
        }
        intentFilter.addAction(ExclusiveTaskManagerService.TURN_OFF_ALL_SOUND_BROADCAST);
        intentFilter.addAction("com.samsung.intent.action.WB_AMR");
        intentFilter.addAction("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED");
        intentFilter.addAction("com.samsung.flipfolder.OPEN");
        intentFilter.addAction("com.sec.media.action.AUDIOCORE_LOGGING");
        intentFilter.addAction(HWParamConst.ACTION_HQM_STATUS_DHR_HQM_REFRESH_REQ);
        intentFilter.addAction("com.sec.media.action.mute_interval");
        Settings.Global.putInt(this.mContentResolver, "mode_ringer_time_on", 0);
        intentFilter.addAction("android.settings.MONO_AUDIO_CHANGED");
        intentFilter.addAction("com.android.phone.action.PERSONALISE_CALL_SOUND_CHANGED");
        intentFilter.addAction("sec.app.policy.UPDATE.audio");
        intentFilter.addAction("com.android.server.sepunion.semgoodcatchservice.GOOD_CATCH_STATE_CHANGED");
        intentFilter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
    }

    void wakeUpDeviceByWiredHeadset(int device) {
        if (isWiredHeadset(device)) {
            Log.i("AudioService", "onReceive set Screen power on for Contextual Awareness.");
            if (Rune.SEC_AUDIO_SCREEN_OFF_MUSIC) {
                this.mIsScreenOffMusicOn = isScreenOffMusicOn();
            }
            if (this.mIsScreenOffMusicOn) {
                Log.i("AudioService", "lcd on will be handled by screen off music");
                return;
            }
            PowerManager pm = (PowerManager) this.mContext.getSystemService("power");
            pm.semWakeUp(SystemClock.uptimeMillis(), 12);
        }
    }

    private void performSoftReset() {
        Log.i("AudioService", "performSoftReset start");
        this.mMonoMode = 0;
        Settings.System.putIntForUser(this.mContentResolver, "mono_audio_db", this.mMonoMode, -2);
        setMonoMode(this.mMonoMode);
        if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER) {
            this.mDualSpkEnable = 1;
            Settings.System.putIntForUser(this.mContentResolver, "pseudo_stereo_sound_settings", this.mDualSpkEnable, -2);
            setDualSpeaker(this.mDualSpkEnable);
        }
        this.mMySoundEnabled = 0;
        Settings.System.putIntForUser(this.mContentResolver, "hearing_musiccheck", this.mMySoundEnabled, -2);
        setGlobalMySound(this.mMySoundEnabled);
        this.mUpscalerEnabled = 0;
        Settings.System.putIntForUser(this.mContentResolver, "k2hd_effect", this.mUpscalerEnabled, -2);
        setGlobalUpscaler(this.mUpscalerEnabled);
        Settings.System.putIntForUser(this.mContentResolver, "sound_alive_effect", 0, -2);
        Settings.System.putIntForUser(this.mContentResolver, "tube_amp_effect", 0, -2);
        if (Rune.SEC_AUDIO_PERFORMANCE_MODE) {
            this.mPerformanceMode = 0;
            this.mPerformanceUpScalerMode = 0;
        }
        this.mAllSoundMute = 0;
        Settings.System.putIntForUser(this.mContentResolver, "all_sound_off", this.mAllSoundMute, -2);
        setAllSoundMute();
        this.mNbQualityMode = 0;
        Settings.Global.putInt(this.mContentResolver, "personalise_call_sound_soft", 0);
        setNbQualityMode(this.mNbQualityMode);
        this.mSettingHelper.resetTable();
        Settings.System.putIntForUser(this.mContentResolver, "adjust_media_volume_only", getDefaultVolumeOption(), -2);
        if (this.mVolumeSteps != null) {
            Settings.System.putString(this.mContentResolver, "sec_volume_steps", "");
        }
        setRingerMode(2, "AudioService.performSoftReset", false);
        Settings.Global.putInt(this.mContentResolver, MULTISOUND_STATE, 0);
        Settings.System.putString(this.mContentResolver, MULTISOUND_APP, null);
        Settings.System.putInt(this.mContentResolver, MULTISOUND_DEVICE_TYPE, -1);
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int streamType = 0; streamType < numStreamTypes; streamType++) {
            VolumeStreamState streamState = this.mStreamStates[streamType];
            if (mStreamVolumeAlias[streamType] != 3 || streamType == 3) {
                synchronized (streamState) {
                    int i = 0;
                    while (true) {
                        int i2 = i;
                        if (i2 >= streamState.mIndexMap.size()) {
                            break;
                        }
                        int device = streamState.mIndexMap.keyAt(i2);
                        int index = AudioSystem.DEFAULT_STREAM_VOLUME[streamType];
                        if (streamType == 3 && (device & 140) != 0) {
                            index = 8;
                        }
                        if (streamType == 3 && (32768 & device) != 0) {
                            index = 15;
                        }
                        streamState.mIndexMap.put(device, 10 * index);
                        streamState.applyDeviceVolume_syncVSS(device);
                        for (int streamType2 = numStreamTypes - 1; streamType2 >= 0; streamType2--) {
                            if (streamType2 != streamState.mStreamType && mStreamVolumeAlias[streamType2] == streamState.mStreamType) {
                                this.mStreamStates[streamType2].applyDeviceVolume_syncVSS(getDeviceForStream(streamType2));
                            }
                        }
                        sendMsg(this.mAudioHandler, 1, 2, device, 0, streamState, 500);
                        i = i2 + 1;
                    }
                }
                continue;
            }
        }
        Log.i("AudioService", "performSoftReset end");
    }

    private void setSeparateBTFineVolume(int index, int device) {
        int streamVolume = this.mStreamStates[3].getIndex(device);
        Log.v("AudioService", "setFineVolume device=" + device + ", streamVolume=" + streamVolume + ", fineVolume=" + index);
        if ((device & 896) != 0) {
            this.mSettingHelper.setBTVolumeIndex(this.mBTAddress, index);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendBroadcastToUser(Intent intent, UserHandle handle) {
        long ident = Binder.clearCallingIdentity();
        try {
            this.mContext.sendBroadcastAsUser(intent, handle);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private String getTime() {
        Calendar calendar = Calendar.getInstance();
        Long time = Long.valueOf(calendar.getTimeInMillis());
        SimpleDateFormat DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date curdate = new Date(time.longValue());
        String simpleTime = DateFormat.format(curdate);
        return simpleTime;
    }

    public void setForceSpeakerOn(boolean on) {
        int uid = Binder.getCallingUid();
        if (uid != 1000) {
            return;
        }
        this.mForceSpeaker = on ? 1 : 0;
        if ((getDeviceForStream(3) & 896) == 0) {
            sendMsg(this.mAudioHandler, 8, 2, 1, this.mForceSpeaker, null, 0);
        }
    }

    public boolean isForceSpeakerOn() {
        return this.mForceSpeaker == 1;
    }

    public void setForceUseForMedia(int forced_config) {
        if (!checkAudioSettingsPermission("setForceUseForMedia()")) {
            return;
        }
        if (forced_config != 10001) {
            AudioSystem.setForceUse(1, 0);
            this.mForcedUseForMedia = 0;
        } else {
            AudioSystem.setForceUse(1, (int) TimaService.TIMA_SUBCODE_LKM);
            this.mForcedUseForMedia = TimaService.TIMA_SUBCODE_LKM;
        }
        sendMsg(this.mAudioHandler, 8, 2, 1, this.mForcedUseForMedia, null, 0);
    }

    public void setRadioOutputPath(int path) {
        if (!checkAudioSettingsPermission("setRadioOutputPath()")) {
            return;
        }
        this.mStreamStates[3].mute(false);
        if (path == 2) {
            AudioSystem.setForceUse(8, 1);
            this.mForcedUseForFMRadio = 1;
        } else if (path == 8) {
            AudioSystem.setForceUse(8, 4);
            this.mForcedUseForFMRadio = 4;
        } else if (path == 3) {
            AudioSystem.setForceUse(8, 0);
            this.mForcedUseForFMRadio = 0;
        } else {
            Log.i("AudioService", "FM radio app set wrong radio output path : " + path);
        }
    }

    public int getRadioOutputPath() {
        if (this.mForcedUseForFMRadio == 1) {
            return 2;
        }
        if (this.mForcedUseForFMRadio == 4) {
            if (checkDeviceConnected(128)) {
                return 8;
            }
            this.mForcedUseForFMRadio = 0;
            return 3;
        }
        return 3;
    }

    public boolean isFMPlayerActive() {
        try {
            String fmPlayer = SystemProperties.get("persist.audio.isfmactive");
            return Integer.parseInt(fmPlayer) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isFMRadioRecording() {
        return false;
    }

    private boolean isMuteIntervalEnabled() {
        return Settings.Global.getInt(this.mContentResolver, "mode_ringer_time_on", 0) == 1;
    }

    public void setMuteInterval(int interval, String caller) {
        Log.i("AudioService", "setMuteInterval unmute timer=" + interval + " from=" + caller);
        if (this.mContext.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != 0) {
            Log.w("AudioService", "WRITE_SECURE_SETTINGS Permission Denial from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        sendMsg(this.mAudioHandler, 121, 0, interval, 0, 0, 0);
    }

    public int getMuteInterval() {
        return Settings.Global.getInt(this.mContentResolver, "mode_ringer_time", 60);
    }

    public int getRemainingMuteIntervalMs() {
        return this.mMuteIntervalMs;
    }

    public int getPrevRingerMode() {
        return this.mPrevRingerMode;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void checkMuteInterval() {
        if (!isMuteIntervalEnabled()) {
            return;
        }
        this.mMuteIntervalMs -= 60000;
        if (this.mMuteIntervalMs <= 0) {
            this.mMuteIntervalMs = 0;
            long identity = Binder.clearCallingIdentity();
            try {
                setRingerMode(this.mPrevRingerMode, "checkMuteInterval", false);
                Settings.Global.putInt(this.mContentResolver, "mode_ringer_time_on", 0);
                return;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        Intent intent = new Intent("com.sec.media.action.mute_interval");
        PendingIntent sender = PendingIntent.getBroadcast(this.mContext, 0, intent, 134217728);
        this.mAlarmManager.setExact(2, SystemClock.elapsedRealtime() + 60000, sender);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class FineVolumeState {
        private final int MAX_INDEX;
        private final ConcurrentHashMap<Integer, Integer> mIndex;
        private final ConcurrentHashMap<Integer, Integer> mOldIndex;

        private FineVolumeState() {
            this.MAX_INDEX = 9;
            this.mIndex = new ConcurrentHashMap<>(8, 0.75f, 4);
            this.mOldIndex = new ConcurrentHashMap<>(8, 0.75f, 4);
            this.mIndex.put(Integer.valueOf((int) PermissionMonitor.APM_FLAG_APM_IS_OFF), 0);
            this.mOldIndex.put(Integer.valueOf((int) PermissionMonitor.APM_FLAG_APM_IS_OFF), 0);
        }

        public boolean setIndex(int index, int device) {
            synchronized (FineVolumeState.class) {
                int index2 = getValidIndex(index);
                int oldIndex = this.mIndex.getOrDefault(Integer.valueOf(device), 0).intValue();
                this.mOldIndex.put(Integer.valueOf(device), Integer.valueOf(oldIndex));
                this.mIndex.put(Integer.valueOf(device), Integer.valueOf(index2));
            }
            return true;
        }

        public int getIndex(int device) {
            int intValue;
            synchronized (FineVolumeState.class) {
                Integer index = this.mIndex.get(Integer.valueOf(device));
                if (index == null) {
                    index = this.mIndex.get(Integer.valueOf((int) PermissionMonitor.APM_FLAG_APM_IS_OFF));
                }
                intValue = index.intValue();
            }
            return intValue;
        }

        public boolean lastChanged(int device) {
            boolean z;
            synchronized (FineVolumeState.class) {
                int oldIndex = this.mOldIndex.getOrDefault(Integer.valueOf(device), 0).intValue();
                int curIndex = this.mIndex.getOrDefault(Integer.valueOf(device), 0).intValue();
                z = oldIndex != curIndex;
            }
            return z;
        }

        private int getValidIndex(int index) {
            if (index < 0) {
                return 0;
            }
            if (index > 9) {
                return 9;
            }
            return index;
        }

        public void applyFineVolume(int device) {
            int index = getIndex(device);
            if (AudioService.this.mStreamStates[3].mIsMuted) {
                index = 0;
            }
            AudioSystem.setPolicyParameters("fine_volume=" + index + ";fine_device=" + device);
        }

        public void dump(PrintWriter pw) {
            pw.println("Fine volumes (device: index)");
            Set<Map.Entry<Integer, Integer>> indexSet = this.mIndex.entrySet();
            for (Map.Entry<Integer, Integer> entry : indexSet) {
                int device = entry.getKey().intValue();
                String deviceName = device == 1073741824 ? "default" : AudioSystem.getOutputDeviceName(device);
                int index = entry.getValue().intValue();
                pw.println(Integer.toHexString(device) + " (" + deviceName + "): " + index);
            }
            pw.println("");
        }
    }

    private void setFineVolume_internal(FineVolumeState state, int device) {
        if (state != null) {
            state.applyFineVolume(device);
        }
    }

    private int getNextFineMediaVolume(int device, int direction) {
        int index = this.mStreamStates[3].getIndex(device) + this.mFineMediaVolume.getIndex(device);
        if (index < 0 || index > getStreamMaxVolume(3) * 10) {
            return index;
        }
        if (this.mVolumeSteps != null) {
            if (direction > 0) {
                return this.mVolumeMap[index].raiseStep;
            }
            return this.mVolumeMap[index].lowerStep;
        }
        int index2 = index + (this.mMediaVolumeStepIndex * direction);
        if (index2 < 0) {
            return 0;
        }
        return index2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String getAddressForDevice(int device) {
        List<BluetoothDevice> deviceList;
        BluetoothDevice activeBT;
        if (AudioDeviceInfo.convertInternalDeviceToDeviceType(device) == 8 && this.mA2dp != null && (activeBT = this.mA2dp.getActiveDevice()) != null) {
            String addr = activeBT.getAddress();
            return addr;
        }
        if (AudioDeviceInfo.convertInternalDeviceToDeviceType(device) == 7 && this.mBluetoothHeadset != null && (deviceList = this.mBluetoothHeadset.getConnectedDevices()) != null) {
            for (BluetoothDevice dev : deviceList) {
                if (this.mBluetoothHeadset.isAudioConnected(dev)) {
                    return dev.getAddress();
                }
            }
        }
        if (device == 32768) {
            String key = makeDeviceListKey(device, "0");
            if (this.mConnectedDevices.get(key) != null) {
                return "0";
            }
        }
        String key2 = makeDeviceListKey(device, "");
        DeviceListSpec spec = this.mConnectedDevices.get(key2);
        if (spec == null) {
            return "";
        }
        String addr2 = spec.mDeviceAddress;
        return addr2;
    }

    /* JADX WARN: Removed duplicated region for block: B:39:? A[RETURN, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    private boolean isScreenOffMusicOn() {
        /*
            r15 = this;
            java.lang.String r0 = "com.sec.android.app.music.shared"
            java.lang.String r1 = "com.samsung.android.app.music.chn.setting"
            java.lang.String r2 = "setting/ready_screen_off_music"
            r3 = 0
            android.content.Context r4 = r15.mContext
            android.content.pm.PackageManager r4 = r4.getPackageManager()
            java.lang.String r5 = "com.sec.android.app.music.shared"
            r6 = 0
            android.content.pm.ProviderInfo r4 = r4.resolveContentProvider(r5, r6)
            r5 = 1
            if (r4 == 0) goto L1a
            r4 = r5
            goto L1b
        L1a:
            r4 = r6
        L1b:
            android.content.Context r7 = r15.mContext
            android.content.pm.PackageManager r7 = r7.getPackageManager()
            java.lang.String r8 = "com.samsung.android.app.music.chn.setting"
            android.content.pm.ProviderInfo r7 = r7.resolveContentProvider(r8, r6)
            if (r7 == 0) goto L2b
            r6 = r5
        L2b:
            if (r4 != 0) goto L38
            if (r6 != 0) goto L38
            java.lang.String r5 = "AudioService"
            java.lang.String r7 = "ScreenOffMusicProvider does not exist"
            android.util.Log.i(r5, r7)
            r3 = 0
            goto L96
        L38:
            r7 = 0
            if (r6 != r5) goto L4e
            java.lang.String r8 = "content://com.samsung.android.app.music.chn.setting"
            android.net.Uri r8 = android.net.Uri.parse(r8)     // Catch: java.lang.Throwable -> L4a java.lang.Exception -> L4c
            java.lang.String r9 = "setting/ready_screen_off_music"
            android.net.Uri r8 = android.net.Uri.withAppendedPath(r8, r9)     // Catch: java.lang.Throwable -> L4a java.lang.Exception -> L4c
        L48:
            r10 = r8
            goto L5c
        L4a:
            r5 = move-exception
            goto L97
        L4c:
            r5 = move-exception
            goto L8f
        L4e:
            java.lang.String r8 = "content://com.sec.android.app.music.shared"
            android.net.Uri r8 = android.net.Uri.parse(r8)     // Catch: java.lang.Throwable -> L4a java.lang.Exception -> L4c
            java.lang.String r9 = "setting/ready_screen_off_music"
            android.net.Uri r8 = android.net.Uri.withAppendedPath(r8, r9)     // Catch: java.lang.Throwable -> L4a java.lang.Exception -> L4c
            goto L48
        L5c:
            android.content.ContentResolver r9 = r15.mContentResolver     // Catch: java.lang.Throwable -> L4a java.lang.Exception -> L4c
            r11 = 0
            r12 = 0
            r13 = 0
            r14 = 0
            android.database.Cursor r8 = r9.query(r10, r11, r12, r13, r14)     // Catch: java.lang.Throwable -> L4a java.lang.Exception -> L4c
            r7 = r8
            if (r7 == 0) goto L80
            int r8 = r7.getCount()     // Catch: java.lang.Throwable -> L4a java.lang.Exception -> L4c
            if (r8 > 0) goto L70
            goto L80
        L70:
            r7.moveToFirst()     // Catch: java.lang.Throwable -> L4a java.lang.Exception -> L4c
            java.lang.String r5 = r7.getString(r5)     // Catch: java.lang.Throwable -> L4a java.lang.Exception -> L4c
            java.lang.String r8 = "true"
            boolean r8 = r8.equals(r5)     // Catch: java.lang.Throwable -> L4a java.lang.Exception -> L4c
            r3 = r8
            goto L88
        L80:
            java.lang.String r5 = "AudioService"
            java.lang.String r8 = "screen off music query failed"
            android.util.Log.e(r5, r8)     // Catch: java.lang.Throwable -> L4a java.lang.Exception -> L4c
        L88:
            if (r7 == 0) goto L96
        L8a:
            r7.close()
            r5 = 0
            goto L96
        L8f:
            r5.printStackTrace()     // Catch: java.lang.Throwable -> L4a
            if (r7 == 0) goto L96
            goto L8a
        L96:
            return r3
        L97:
            if (r7 == 0) goto L9d
            r7.close()
            r7 = 0
        L9d:
            throw r5
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.audio.AudioService.isScreenOffMusicOn():boolean");
    }

    private int getFlagsForDeviceConnect(int connected) {
        return connected == 0 ? 4194304 | EdmStorageDefs.APP_PKGNAME_INSTALLER_WHITELIST_MASK : EdmStorageDefs.APP_PKGNAME_INSTALLER_WHITELIST_MASK;
    }

    public String getCurrentAudioFocusPackageName() {
        return this.mMediaFocusControl.getCurrentAudioFocusPackageName();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onSendBecomingNoisyIntent(int device) {
        if (Rune.SEC_AUDIO_MULTI_SOUND && isMultiSoundOn()) {
            sendBecomingNoisyIntentToUnpinApps(device);
            return;
        }
        Intent noisySecIntent = new Intent("android.media.AUDIO_BECOMING_NOISY_SEC");
        sendBroadcastToAll(noisySecIntent);
        Intent noisyIntent = new Intent("android.media.AUDIO_BECOMING_NOISY");
        if ((device & 896) != 0) {
            noisyIntent.putExtra("android.bluetooth.a2dp.extra.DISCONNECT_A2DP", true);
        }
        sendBroadcastToAll(noisyIntent);
    }

    /* JADX WARN: Removed duplicated region for block: B:136:0x0234 A[Catch: all -> 0x02be, TRY_LEAVE, TryCatch #10 {all -> 0x02be, blocks: (B:126:0x0211, B:134:0x022e, B:136:0x0234), top: B:188:0x0211 }] */
    /* JADX WARN: Removed duplicated region for block: B:148:0x0278 A[Catch: all -> 0x02b9, TryCatch #8 {all -> 0x02b9, blocks: (B:142:0x0266, B:148:0x0278, B:150:0x0287, B:151:0x0292), top: B:185:0x0232 }] */
    /* JADX WARN: Removed duplicated region for block: B:179:0x0218 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    private void setStreamVolume(int r22, int r23, int r24, java.lang.String r25, java.lang.String r26, int r27, int r28) {
        /*
            Method dump skipped, instructions count: 722
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.audio.AudioService.setStreamVolume(int, int, int, java.lang.String, java.lang.String, int, int):void");
    }

    public int getStreamVolume(int streamType, int device) {
        int properIndexForFineVolume;
        ensureValidStreamType(streamType);
        if (Rune.SEC_AUDIO_MULTI_SOUND && isMultiSoundOn() && streamType == 3) {
            if (device == 0 && (device = getAppDevice(Binder.getCallingUid())) == 0) {
                device = getDeviceForStream(streamType);
            }
        } else {
            device = getDeviceForStream(streamType);
        }
        synchronized (VolumeStreamState.class) {
            int index = this.mStreamStates[streamType].getIndex(device);
            if (this.mStreamStates[streamType].mIsMuted) {
                index = 0;
            }
            if (index != 0 && mStreamVolumeAlias[streamType] == 3 && (this.mFixedVolumeDevices & device) != 0) {
                index = this.mStreamStates[streamType].getMaxIndex();
            }
            properIndexForFineVolume = (getProperIndexForFineVolume(streamType, device, index) + 5) / 10;
        }
        return properIndexForFineVolume;
    }

    public void showNotification() {
        String contents;
        if (this.mNm == null) {
            return;
        }
        int curDevice = AudioSystem.getDevicesForStream(3);
        int pinnedDevice = getPinDevice();
        if (pinnedDevice == 0 || curDevice == pinnedDevice) {
            return;
        }
        Intent launchSettingIntent = new Intent("android.intent.action.Launch_Setting");
        launchSettingIntent.setClassName("com.samsung.android.setting.multisound", "com.samsung.android.setting.multisound.MultiSoundSettingsActivity");
        PendingIntent piSetting = PendingIntent.getActivity(this.mContext, 0, launchSettingIntent, 268435456);
        Intent turnOffIntent = new Intent("android.intent.action.TurnOff_MultiSound");
        PendingIntent piTurnOff = PendingIntent.getBroadcast(this.mContext, 0, turnOffIntent, 0);
        Notification.Builder builder = new Notification.Builder(this.mContext, NOTIFICATION_CHANNEL_ID);
        if (pinnedDevice == 2) {
            contents = this.mContext.getString(17040852, getPinAppInfo(pinnedDevice));
        } else if (pinnedDevice == 4 || pinnedDevice == 8) {
            contents = this.mContext.getString(17040851, getPinAppInfo(pinnedDevice));
        } else if (pinnedDevice == 128) {
            contents = this.mContext.getString(17040850, getPinAppInfo(pinnedDevice));
        } else if (pinnedDevice == 16384 || pinnedDevice == 67108864) {
            contents = this.mContext.getString(17040853, getPinAppInfo(pinnedDevice));
        } else {
            contents = this.mContext.getString(17040852, getPinAppInfo(pinnedDevice));
        }
        builder.setStyle(new Notification.BigTextStyle().bigText(contents)).setContentTitle(this.mContext.getString(17040854)).setSmallIcon(17303992).setContentText(contents).setAutoCancel(false).setVibrate(null).setContentIntent(piSetting).addAction(17301624, this.mContext.getResources().getText(17040849), piSetting).addAction(17301624, this.mContext.getResources().getText(17040855), piTurnOff);
        this.mNm.notify(1004, builder.build());
    }

    public void clearNotification() {
        this.mNm.cancel(1004);
    }

    public void showHeadupNotification(int device) {
        AudioDeviceInfo[] list = AudioManager.getDevicesStatic(2);
        String[] deviceName = {"", ""};
        int[] deviceType = {device, getPriorityDevice(device)};
        if ((deviceType[0] & 67125262) != 0 && (deviceType[1] & 67125262) != 0) {
            return;
        }
        for (int deviceNum = 0; deviceNum < deviceType.length; deviceNum++) {
            int i = deviceType[deviceNum];
            if (i != 2) {
                if (i == 4 || i == 8) {
                    deviceName[deviceNum] = this.mContext.getResources().getString(17040380);
                } else {
                    int i2 = 0;
                    while (true) {
                        if (i2 < list.length) {
                            if (list[i2].semGetInternalType() != deviceType[deviceNum]) {
                                i2++;
                            } else {
                                deviceName[deviceNum] = list[i2].getProductName().toString();
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                }
            } else {
                deviceName[deviceNum] = this.mContext.getResources().getString(17040869);
            }
        }
        String msg = String.format(this.mContext.getString(17040847), getPinAppInfo(getPinDevice()), deviceName[0], deviceName[1]);
        Context context = this.mContext;
        Context context2 = this.mContext;
        context.getSystemService("notification");
        Notification.Builder notiBuilder = new Notification.Builder(this.mContext, NOTIFICATION_CHANNEL_ID);
        notiBuilder.setAutoCancel(true).setShowWhen(true).setSmallIcon(17303992).setContentTitle(this.mContext.getString(17040848)).setContentText(msg).setStyle(new Notification.BigTextStyle().bigText(msg));
        notiBuilder.setPriority(2).setDefaults(1);
        Intent changeIntent = new Intent("android.intent.action.Headup_Notification_ChangeDevice");
        PendingIntent changeHeadup = PendingIntent.getBroadcast(this.mContext, 1, changeIntent, 134217728);
        notiBuilder.addAction(0, this.mContext.getString(17040845), changeHeadup);
        notiBuilder.setContentIntent(changeHeadup);
        this.mNm.notify(1005, notiBuilder.build());
    }

    public void clearHeadupNotificaton() {
        this.mNm.cancel(1005);
    }

    public void showMultisoundToast() {
        if (this.multiSoundToast == null) {
            Context ToastContext = new ContextThemeWrapper(this.mContext, 16974123);
            this.multiSoundToast = Toast.makeText(ToastContext, this.mContext.getString(17040842, this.mContext.getResources().getText(17040854)), 0);
        } else {
            this.multiSoundToast.setText(this.mContext.getString(17040842, this.mContext.getResources().getText(17040854)));
        }
        this.multiSoundToast.show();
    }

    public String[] getSelectedAppList() {
        return this.mCategorizer.getSelectedPackages();
    }

    public void addPackage(int uid, String packageName) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") != 0) {
            Log.w("AudioService", "MODIFY_PHONE_STATE Permission Denial from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        try {
            ApplicationInfo appInfo = this.mContext.getPackageManager().getApplicationInfo(packageName, 128);
            this.mCategorizer.putPackage(appInfo.uid, appInfo.packageName);
        } catch (PackageManager.NameNotFoundException e) {
        }
    }

    public void removePackageForName(String packageName) {
        if (!checkAudioSettingsPermission("removePackageForName")) {
            return;
        }
        this.mCategorizer.removePackage(getUidForPackage(packageName));
    }

    public boolean isAlreadyInDB(String packageName) {
        return isRestrictedPackage(packageName) || this.mCategorizer.checkExist(packageName);
    }

    public boolean isInAllowedList(String packageName) {
        for (int i = 0; i < this.mAllowedPackageList.size(); i++) {
            if (packageName.equals(this.mAllowedPackageList.get(i))) {
                return true;
            }
        }
        return false;
    }

    private void initPackageList() {
        String packageName;
        String packageName2;
        Log.d("AudioService", "initPackageList");
        BufferedReader restrictedPackageListReader = null;
        BufferedReader allowedPackageListReader = null;
        try {
            try {
                try {
                    restrictedPackageListReader = new BufferedReader(new FileReader("/system/etc/multisound_restricted_package_list"));
                    allowedPackageListReader = new BufferedReader(new FileReader("/system/etc/multisound_allowed_package_list"));
                    do {
                        packageName = restrictedPackageListReader.readLine();
                        if (packageName != null) {
                            this.mRestrictedPackageList.add(packageName);
                            continue;
                        }
                    } while (packageName != null);
                    do {
                        packageName2 = allowedPackageListReader.readLine();
                        if (packageName2 != null) {
                            this.mAllowedPackageList.add(packageName2);
                            continue;
                        }
                    } while (packageName2 != null);
                    restrictedPackageListReader.close();
                    allowedPackageListReader.close();
                } catch (FileNotFoundException e) {
                    Log.e("AudioService", "file does not exists", e);
                    if (restrictedPackageListReader != null) {
                        restrictedPackageListReader.close();
                    }
                    if (allowedPackageListReader != null) {
                        allowedPackageListReader.close();
                    }
                } catch (IOException e2) {
                    Log.d("AudioService", "IOException", e2);
                    if (restrictedPackageListReader != null) {
                        restrictedPackageListReader.close();
                    }
                    if (allowedPackageListReader != null) {
                        allowedPackageListReader.close();
                    }
                }
            } catch (IOException e3) {
                Log.d("AudioService", "IOException", e3);
            }
        } catch (Throwable th) {
            if (0 != 0) {
                try {
                    restrictedPackageListReader.close();
                } catch (IOException e4) {
                    Log.d("AudioService", "IOException", e4);
                    throw th;
                }
            }
            if (0 != 0) {
                allowedPackageListReader.close();
            }
            throw th;
        }
    }

    private boolean isRestrictedPackage(String packageName) {
        for (int i = 0; i < this.mRestrictedPackageList.size(); i++) {
            if (packageName.equals(this.mRestrictedPackageList.get(i))) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static boolean isUsingAudioForUid(int uid) {
        String param = "hasActiveTrackUID=" + uid;
        String result = AudioSystem.getParameters(param);
        return "TRUE".equals(result);
    }

    static boolean isUsingAudioUponDevice(int device) {
        String param = "hasActiveTrackDevice=" + device;
        String result = AudioSystem.getParameters(param);
        return "TRUE".equals(result);
    }

    private int selectDevice(int device) {
        if (!isMultiSoundOn()) {
            return device;
        }
        int foregroundDevice = this.mMultiSoundManager.getAppDevice(this.mForegroundUid);
        if (foregroundDevice != 0 && (isUsingAudioForUid(this.mForegroundUid) || !AudioSystem.isStreamActive(3, 0))) {
            return foregroundDevice;
        }
        if (isUsingAudioUponDevice(device)) {
            return device;
        }
        int multiSoundDevice = this.mMultiSoundManager.getDeviceMultiSoundUsingActually();
        if (multiSoundDevice != 0) {
            return multiSoundDevice;
        }
        return device;
    }

    private void sendIntentToSpecificPackage(Intent intent, String[] exceptPackages) {
        long ident = Binder.clearCallingIdentity();
        try {
            List<ResolveInfo> infos = this.mPackageManager.queryBroadcastReceiversAsUser(intent, 0, getCurrentUserId());
            Binder.restoreCallingIdentity(ident);
            String[] activitylist = this.mActivityManager.queryRegisteredReceiverPackages(intent);
            boolean except = false;
            for (ResolveInfo resolveinfo : infos) {
                except = false;
                if (resolveinfo.activityInfo != null) {
                    String packageName = resolveinfo.activityInfo.packageName;
                    if (!packageName.isEmpty()) {
                        int length = exceptPackages.length;
                        int i = 0;
                        while (true) {
                            if (i >= length) {
                                break;
                            }
                            String exceptPkg = exceptPackages[i];
                            if (!packageName.equals(exceptPkg)) {
                                i++;
                            } else {
                                Log.d("AudioService", "send intent except " + exceptPkg);
                                except = true;
                                break;
                            }
                        }
                        if (!except) {
                            intent.setPackage(resolveinfo.activityInfo.packageName);
                            sendBroadcastToAll(intent);
                            Log.d("AudioService", "sendBecomingNoisyIntentToUnpinApps to " + resolveinfo.activityInfo.packageName);
                        }
                    }
                }
            }
            for (String activityName : activitylist) {
                boolean except2 = false;
                if (activityName != null && !activityName.isEmpty()) {
                    int length2 = exceptPackages.length;
                    int i2 = 0;
                    while (true) {
                        if (i2 >= length2) {
                            break;
                        }
                        String exceptPkg2 = exceptPackages[i2];
                        if (!activityName.equals(exceptPkg2)) {
                            i2++;
                        } else {
                            Log.d("AudioService", "send intent except " + exceptPkg2);
                            except2 = true;
                            break;
                        }
                    }
                    if (!except2) {
                        intent.setPackage(activityName);
                        sendBroadcastToAll(intent);
                        Log.d("AudioService", "sendBecomingNoisyIntentToUnpinApps to " + activityName);
                    }
                }
            }
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(ident);
            throw th;
        }
    }

    private void sendBecomingNoisyIntentToUnpinApps(int device) {
        Log.d("AudioService", "sendBecomingNoisyIntentToUnpinApps, " + device);
        int pinDevice = getPinDevice();
        String[] pinPackageName = this.mMultiSoundManager.getPinPackageName(pinDevice);
        Intent intent = new Intent();
        if ((device & 896) != 0) {
            intent.putExtra("android.bluetooth.a2dp.extra.DISCONNECT_A2DP", true);
        }
        intent.setAction("android.media.AUDIO_BECOMING_NOISY_SEC");
        sendIntentToSpecificPackage(intent, pinPackageName);
        intent.setAction("android.media.AUDIO_BECOMING_NOISY");
        if (intent.getPackage() != null) {
            intent.setPackage(null);
        }
        sendIntentToSpecificPackage(intent, pinPackageName);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String[] getPackageName(int uid) {
        String[] packageName;
        if (uid == 1000) {
            packageName = new String[]{PackageManagerService.PLATFORM_PACKAGE_NAME};
        } else {
            packageName = this.mPackageManager.getPackagesForUid(uid);
        }
        if (packageName == null) {
            String[] packageName2 = {""};
            return packageName2;
        }
        return packageName;
    }

    public int getUidForPackage(String packageName) {
        try {
            ApplicationInfo info = this.mPackageManager.getApplicationInfoAsUser(packageName, 0, getCurrentUserId());
            int uid = info.uid;
            return uid;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    public int getUidForDevice(int device) {
        Log.d("AudioService", "getUidForDevice, " + Integer.toHexString(device));
        return this.mMediaFocusControl.getUidForDevice(device);
    }

    public void setAppDevice(int uid, int device) {
        if ("".equals(getPackageName(uid)[0])) {
            throw new IllegalArgumentException("Bad uid " + uid);
        }
        int device2 = AudioDeviceInfo.convertDeviceTypeToInternalDevice(device);
        Log.d("AudioService", "setAppDevice, uid:" + uid + ", device:" + device2);
        sendMsg(this.mAudioHandler, 119, 2, uid, device2, 0, 0);
    }

    public int getAppDevice(int uid) {
        return this.mMultiSoundManager.getAppDevice(uid);
    }

    public void setAppVolume(int uid, int ratio, String callingPackage) {
        if ("".equals(getPackageName(uid)[0])) {
            throw new IllegalArgumentException("Bad uid " + uid);
        } else if (!checkAudioSettingsPermission("setAppVolume")) {
        } else {
            Log.d("AudioService", "setAppVolume, uid:" + uid + ", volume:" + ratio + ", package:" + callingPackage);
            this.mMultiSoundManager.setAppVolume(uid, ratio);
            if ("com.samsung.android.soundassistant".equals(callingPackage)) {
                this.mAppVolumeFromSoundAssistant.put(uid, ratio);
            }
        }
    }

    public int getAppVolume(int uid) {
        return this.mMultiSoundManager.getAppVolume(uid);
    }

    public String getPinAppInfo(int device) {
        return this.mMultiSoundManager.getPinAppInfo(device);
    }

    public void setMultiSoundOn(boolean on) {
        if (!checkAudioSettingsPermission("setMultiSoundOn")) {
            return;
        }
        sendMsg(this.mAudioHandler, 118, 2, 0, 0, Boolean.valueOf(on), 0);
    }

    public boolean isMultiSoundOn() {
        return this.mMultiSoundManager.isEnabled();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateAudioServiceNotificationChannel() {
        if (this.mNm != null) {
            this.mNm.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
        }
        this.mNm = (NotificationManager) this.mContext.getSystemService("notification");
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, this.mContext.getResources().getString(17040869), 4);
        this.mNm.createNotificationChannel(channel);
    }

    public void dismissVolumePanel() {
        this.mVolumeController.postDismiss();
    }

    public String getAudioServiceConfig(String keys) {
        String result = null;
        if ("SplitSound".equals(keys)) {
            if (this.mSplitSound) {
                return "true";
            }
            return "false";
        } else if ("extraVolume".equals(keys)) {
            if (this.mExtraVolume) {
                return "true";
            }
            return "false";
        } else if ("hmtState".equals(keys)) {
            if (this.mHMTDocked) {
                return "true";
            }
            return "false";
        } else if ("SafeMediaVolumeDevice".equals(keys)) {
            if ((67109260 & getDeviceForStream(3)) != 0) {
                return "true";
            }
            return "false";
        } else if ("is_fine_volume".equals(keys)) {
            return "true";
        } else {
            if ("active_addr".equals(keys)) {
                String result2 = getAddressForDevice(getDeviceForStream(3));
                return result2;
            } else if ("device_mask_QSP".equals(keys)) {
                String result3 = Integer.toHexString(getAvailableDeviceMaskForQuickSoundPath());
                return result3;
            } else if ("isAvailableUHQ".equals(keys)) {
                if (Rune.SEC_AUDIO_UHQ) {
                    return "true";
                }
                return "false";
            } else if ("volumelimit_on".equals(keys)) {
                String result4 = this.mVolumeLimitOn ? "true" : "false";
                return result4;
            } else if ("volumelimit_value".equals(keys)) {
                String result5 = Integer.toString(this.mVolumeLimitValue);
                return result5;
            } else if (keys == null || !keys.startsWith("sound_assistant;")) {
                if (keys != null && keys.contains("multisound")) {
                    if (!Rune.SEC_AUDIO_MULTI_SOUND) {
                        return null;
                    }
                    AudioParameter audioParam = new AudioParameter(keys);
                    String strVal = audioParam.get("multisound_pinappname");
                    if (strVal != null) {
                        int device = -1;
                        try {
                            device = Integer.parseInt(strVal);
                        } catch (NumberFormatException e) {
                            Log.e("AudioService", "invalid value " + strVal);
                        }
                        String result6 = getPinAppInfo(device);
                        if (this.mMultiSoundManager.isEnabled()) {
                            Log.d("AudioService", "device=0x" + Integer.toHexString(device) + ", pinappinfo=" + result6);
                            return result6;
                        }
                        return result6;
                    } else if (keys.contains("multisound_getprioritydevice")) {
                        String str = audioParam.get("multisound_getprioritydevice");
                        String strVal2 = str;
                        if (str == null) {
                            strVal2 = Integer.toString(TimaService.TIMA_SUBCODE_LKM);
                        }
                        String result7 = Integer.toString(AudioDeviceInfo.convertInternalDeviceToDeviceType(getPriorityDevice(AudioDeviceInfo.convertDeviceTypeToInternalDevice(Integer.parseInt(strVal2)))));
                        return result7;
                    } else {
                        String strVal3 = audioParam.get("multisound_get_active_bt_device_name");
                        if (strVal3 == null) {
                            return null;
                        }
                        int parseInt = Integer.parseInt(strVal3);
                        if (parseInt != 2) {
                            if (parseInt == 4 || parseInt == 8) {
                                String result8 = this.mContext.getString(17040013);
                                return result8;
                            } else if (parseInt == 128) {
                                if (this.mA2dp == null) {
                                    return null;
                                } else {
                                    if (this.mA2dp.semIsDualPlayMode()) {
                                        StringBuilder sb = new StringBuilder();
                                        List<BluetoothDevice> deviceList = this.mA2dp.getConnectedDevices();
                                        if (deviceList != null && deviceList.size() >= 1) {
                                            for (BluetoothDevice bt_Device : deviceList) {
                                                if (sb.length() > 0) {
                                                    sb.append(",");
                                                }
                                                sb.append(bt_Device.semGetAliasName());
                                            }
                                        }
                                        String result9 = sb.toString();
                                        return result9;
                                    }
                                    BluetoothDevice btDevice = this.mA2dp.getActiveDevice();
                                    if (btDevice == null) {
                                        return null;
                                    }
                                    String result10 = btDevice.getAliasName();
                                    return result10;
                                }
                            } else {
                                if (parseInt == 16384 || parseInt == 67108864) {
                                    AudioDeviceInfo[] list = AudioManager.getDevicesStatic(2);
                                    for (int i = 0; i < list.length; i++) {
                                        if (list[i].semGetInternalType() == 16384 || list[i].semGetInternalType() == 67108864) {
                                            result = list[i].getProductName().toString();
                                        }
                                    }
                                    return result;
                                }
                                String result11 = this.mContext.getString(17040010);
                                return result11;
                            }
                        }
                        String result12 = this.mContext.getString(17040010);
                        return result12;
                    }
                } else if (keys == null || !keys.contains("SafeMediaVolumeDevice") || !Rune.SEC_AUDIO_MULTI_SOUND) {
                    return null;
                } else {
                    int device2 = 0;
                    String strVal4 = new AudioParameter(keys).get("SafeMediaVolumeDevice");
                    if (strVal4 != null) {
                        try {
                            device2 = Integer.parseInt(strVal4);
                        } catch (NumberFormatException e2) {
                            Log.e("AudioService", "invalid value " + strVal4);
                        }
                    }
                    if (device2 == 2) {
                        if (checkDeviceConnected(4)) {
                            device2 = 4;
                        } else if (checkDeviceConnected(8)) {
                            device2 = 8;
                        }
                    }
                    if ((67109260 & device2) != 0) {
                        return "true";
                    }
                    return "false";
                }
            } else {
                return getSoundAssistant(keys.substring("sound_assistant".length() + 1));
            }
        }
    }

    public void setAudioServiceConfig(String keyValuePairs) {
        String strVal;
        String strVal2;
        AudioParameter audioParam = new AudioParameter(keyValuePairs);
        String strVal3 = audioParam.get("setForceUseForMedia");
        if (strVal3 != null) {
            int forced_config = Integer.parseInt(strVal3);
            setForceUseForMedia(forced_config);
            return;
        }
        String strVal4 = audioParam.get("UHQ_BT");
        if (strVal4 != null) {
            int uhqBtSampleRate = Integer.parseInt(strVal4);
            if (uhqBtSampleRate == 0) {
                getDevicesForStream(3, false);
                return;
            }
            this.mUhqBtSampleRate = uhqBtSampleRate;
            AudioSystem.setParameters(keyValuePairs);
            return;
        }
        String strVal5 = audioParam.get("bt_offload_enable");
        if (strVal5 != null) {
            this.mIsBtOffloadEnabled = Integer.parseInt(strVal5);
            AudioSystem.setPolicyParameters(keyValuePairs);
            return;
        }
        String strVal6 = audioParam.get("volumelimit_value");
        if (strVal6 != null) {
            String callingPackage = "";
            this.mVolumeLimitValue = Integer.parseInt(strVal6);
            String strVal7 = audioParam.get("package_name");
            if (strVal7 != null) {
                callingPackage = strVal7;
            }
            int device = getDeviceForStream(3);
            int curVolume = this.mStreamStates[3].getIndex(device) + this.mFineMediaVolume.getIndex(device);
            if (this.mVolumeLimitOn && curVolume > this.mVolumeLimitValue * 10 && (67109260 & device) != 0) {
                setStreamVolume(3, this.mVolumeLimitValue * 10, 2097152, callingPackage);
                return;
            }
            return;
        }
        String strVal8 = audioParam.get("volumelimit_on");
        if (strVal8 != null) {
            this.mVolumeLimitOn = "true".equals(strVal8);
            return;
        }
        String str = audioParam.get("extraVolume");
        String strVal9 = str;
        if (str != null) {
            this.mExtraVolume = "true".equals(strVal9);
        } else if (audioParam.get("sound_assistant") != null) {
            setSoundAssistant(audioParam);
        } else if (audioParam.get(EdmStorageDefs.WEBFILTER_LOGGING) != null) {
            int i = 0;
            while (i < 3) {
                ArrayList<String> types = new ArrayList<>();
                ArrayList<String> values = new ArrayList<>();
                String strVal10 = strVal9;
                for (int j = 0; j < AudioConstants.BIGDATA_ITEM[i].length; j++) {
                    String str2 = audioParam.get(AudioConstants.BIGDATA_ITEM[i][j]);
                    strVal10 = str2;
                    if (str2 != null) {
                        types.add(AudioConstants.BIGDATA_ITEM[i][j]);
                        values.add(strVal10);
                    }
                }
                sendAudioCoreBigData(types, values);
                i++;
                strVal9 = strVal10;
            }
            this.mIsBigDataApp = false;
        } else {
            String strVal11 = audioParam.get("SarBackoff");
            if (strVal11 != null) {
                Log.d("AudioService", "SarBackoff = " + strVal11);
                this.mSarBackoffParam = "On".equals(strVal11);
                sendMsg(this.mAudioHandler, 124, 1, 0, 0, null, 0);
                return;
            }
            String strVal12 = audioParam.get("phone_type");
            if (strVal12 != null) {
                this.mPhoneType = strVal12;
                return;
            }
            String strVal13 = audioParam.get("bt_samplerate");
            if (strVal13 != null) {
                this.mBtSampleRate = strVal13;
                return;
            }
            String strVal14 = audioParam.get("bt_headset_nrec");
            if (strVal14 != null) {
                this.mBtNrec = strVal14;
            } else if (Rune.SEC_AUDIO_PREVENT_OVERHEAT_BY_MAX_VOLUME && (strVal = audioParam.get("overheat_game_uid")) != null) {
                try {
                    int uid = Integer.parseInt(strVal);
                    if (uid != -1 && (strVal2 = audioParam.get("overheat_game_state")) != null) {
                        boolean state = "true".equals(strVal2);
                        int curDevice = getDeviceForStream(3);
                        this.mPreventOverheatState.setState(uid, state, curDevice);
                        this.mPreventOverheatState.setLimitedVolumeForOverheat();
                    }
                } catch (NumberFormatException e) {
                    Log.e("AudioService", "NumberFormatException", e);
                }
            } else if (audioParam.get("sound_effect_volume") != null) {
                try {
                    this.mSoundEffectVolume = Float.parseFloat(AudioSystem.getParameters("situation=1;device=0"));
                } catch (NumberFormatException e2) {
                }
                Log.i("AudioService", "reload sound effect volume : " + this.mSoundEffectVolume);
            }
        }
    }

    private void setSoundAssistant(AudioParameter param) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") != 0) {
            Log.w("AudioService", "MODIFY_PHONE_STATE Permission Denial from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        String strVal = param.get("adjust_media_volume_only");
        if (strVal != null) {
            int onOff = getIntValueFromString(strVal, 0);
            this.mAdjustMediaVolumeOnly = onOff == 1;
            setSystemSettingForSoundAssistant("adjust_media_volume_only", onOff);
            return;
        }
        String strVal2 = param.get("remove_app_volume");
        if (strVal2 != null) {
            int uid = getIntValueFromString(strVal2, -1);
            if (uid == -1 || "".equals(getPackageName(uid)[0])) {
                Log.w("AudioService", "Invalid uid from SoundAssistant");
                return;
            }
            this.mAppVolumeFromSoundAssistant.delete(uid);
            this.mMultiSoundManager.setAppVolume(uid, 100);
            return;
        }
        String strVal3 = param.get("sound_balance");
        if (strVal3 != null) {
            int balance = getIntValueFromString(strVal3, 50);
            if (balance < 0 || balance > 100) {
                throw new IllegalArgumentException("Invalid balance");
            }
            setSystemSettingForSoundAssistant("sound_balance", balance);
            AudioSystem.setParameters("sound_balance=" + balance);
            this.mSoundBalance = balance;
            return;
        }
        String strVal4 = param.get("mono_sound");
        if (strVal4 != null) {
            int onOff2 = getIntValueFromString(strVal4, 0);
            if (onOff2 != 0 && onOff2 != 1) {
                throw new IllegalArgumentException("Invalid balance");
            }
            setSystemSettingForSoundAssistant("mono_audio_db", onOff2);
            this.mMonoMode = onOff2;
            setMonoMode(onOff2);
            return;
        }
        String strVal5 = param.get("enable_floating_button");
        if (strVal5 != null) {
            this.mEnableSoundAssistant = getIntValueFromString(strVal5, 0) == 1;
            this.mSettingHelper.setBooleanValue("enable_floating_button", this.mEnableSoundAssistant);
            return;
        }
        String strVal6 = param.get("set_force_output_for_app");
        if (strVal6 != null) {
            if (!Rune.SEC_AUDIO_MULTI_SOUND) {
                Log.e("AudioService", "Can not support multi sound");
                return;
            }
            int outputDevice = getIntValueFromString(strVal6, 0);
            String strUid = param.get("uid_for_soundassistant");
            int uid2 = getIntValueFromString(strUid, -1);
            if (uid2 == -1) {
                Log.e("AudioService", "invalid arguments");
                return;
            } else if (outputDevice == 2) {
                setSystemSettingForSoundAssistant(MULTISOUND_APP, uid2);
                setSystemSettingForSoundAssistant(MULTISOUND_DEVICE_TYPE, 0);
                setAppDevice(uid2, outputDevice);
                return;
            } else if (outputDevice == 8) {
                setSystemSettingForSoundAssistant(MULTISOUND_APP, uid2);
                setSystemSettingForSoundAssistant(MULTISOUND_DEVICE_TYPE, 1);
                setAppDevice(uid2, outputDevice);
                return;
            } else {
                setAppDevice(uid2, outputDevice);
                String curPinApp = Settings.System.getString(this.mContentResolver, MULTISOUND_APP);
                if (curPinApp == null || curPinApp.equals(getPackageName(uid2)[0])) {
                    setSystemSettingForSoundAssistant(MULTISOUND_APP, -1);
                    return;
                }
                return;
            }
        }
        String strVal7 = param.get("ignore_audio_focus");
        if (strVal7 != null) {
            if (!Rune.SEC_AUDIO_MULTI_SOUND) {
                Log.e("AudioService", "Can not support multi sound");
                return;
            }
            int onOff3 = getIntValueFromString(strVal7, 0);
            String strUid2 = param.get("uid_for_soundassistant");
            int uid3 = getIntValueFromString(strUid2, -1);
            if (uid3 == -1) {
                Log.e("AudioService", "invalid arguments");
                return;
            }
            String packageName = getPackageName(uid3)[0];
            if (packageName == null || "".equals(packageName)) {
                Log.e("AudioService", "Invalid uid for ignoring audiofocus. uid : " + uid3);
                return;
            }
            Log.i("AudioService", "Set ignore audiofocus : " + packageName + ", uid : " + uid3 + ", enabled:" + onOff3);
            this.mIgnoreAudioFocusUid = onOff3 == 1 ? uid3 : -1;
            this.mSettingHelper.setIntValue("ignore_audio_focus", this.mIgnoreAudioFocusUid);
            this.mMediaFocusControl.setIgnoreAudioFocus(uid3, onOff3 == 1);
            return;
        }
        String strVal8 = param.get("media_volume_step_index");
        if (strVal8 != null) {
            int index = getIntValueFromString(strVal8, 10);
            this.mMediaVolumeStepIndex = index;
            this.mSettingHelper.setIntValue("media_volume_step_index", this.mMediaVolumeStepIndex);
            setMediaVolumeSteps(null);
            return;
        }
        String strVal9 = param.get("selfiestick");
        if (strVal9 != null) {
            if ("true".equals(strVal9) && checkDeviceConnected(4)) {
                this.mSelfiestickIsConnected = true;
                setWiredDeviceConnectionState(4, 0, "", "h2w", "selfiestick");
                setWiredDeviceConnectionState(-2147483632, 0, "", "h2w", "selfiestick");
            }
            return;
        }
        String str = param.get("ring_through_headset");
        String strVal10 = str;
        if (str != null) {
            int streams = getIntValueFromString(strVal10, 0);
            this.mHeadsetOnlyStream = streams;
            this.mSettingHelper.setIntValue("ring_through_headset", this.mHeadsetOnlyStream);
            AudioSystem.setPolicyParameters("ring_through_headset=" + this.mHeadsetOnlyStream);
        } else {
            String str2 = param.get("sound_lr_switch");
            strVal10 = str2;
            if (str2 != null) {
                int lr_onOff = getIntValueFromString(strVal10, 0);
                this.mLRSwitching = lr_onOff;
                this.mSettingHelper.setIntValue("sound_lr_switch", this.mLRSwitching);
                if (lr_onOff == 1) {
                    AudioSystem.setParameters("sound_lr_switch=1");
                } else {
                    AudioSystem.setParameters("sound_lr_switch=0");
                }
            }
        }
    }

    private void setSystemSettingForSoundAssistant(String key, int value) {
        if (!"sound_balance".equals(key) && !"mono_audio_db".equals(key) && !MULTISOUND_APP.equals(key) && !MULTISOUND_DEVICE_TYPE.equals(key) && !"adjust_media_volume_only".equals(key)) {
            return;
        }
        long ident = Binder.clearCallingIdentity();
        try {
            if (MULTISOUND_APP.equals(key)) {
                Settings.System.putStringForUser(this.mContentResolver, key, value == -1 ? null : getPackageName(value)[0], -2);
                return;
            }
            Settings.System.putIntForUser(this.mContentResolver, key, value, -2);
            Binder.restoreCallingIdentity(ident);
            this.mSettingHelper.setIntValue(key, value);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private int getIntValueFromString(String val, int defaultVal) {
        try {
            int ret = Integer.parseInt(val);
            return ret;
        } catch (NumberFormatException e) {
            Log.e("AudioService", "NumberFormatException", e);
            return defaultVal;
        }
    }

    private void runSoundAssistant(int direction, String caller) {
        if (!this.mHMTDocked && this.mEnableSoundAssistant) {
            if ((direction == -1 || direction == 1) && fromVolumeKey(caller)) {
                sendMsg(this.mAudioHandler, 122, 1, 0, 0, null, 0);
            }
        }
    }

    private void onRunSoundAssistant() {
        synchronized (this.mSoundAssistantLock) {
            if (this.mStartingSoundAssistant) {
                return;
            }
            this.mStartingSoundAssistant = true;
            new Thread(new Runnable() { // from class: com.android.server.audio.-$$Lambda$AudioService$5oHL_hNwt66GQsfrwaUtyPLhs8o
                @Override // java.lang.Runnable
                public final void run() {
                    AudioService.lambda$onRunSoundAssistant$1(AudioService.this);
                }
            }).start();
        }
    }

    public static /* synthetic */ void lambda$onRunSoundAssistant$1(AudioService audioService) {
        for (ActivityManager.RunningServiceInfo service : audioService.mActivityManager.getRunningServices(Integer.MAX_VALUE)) {
            if ("com.sec.android.soundassistant.services.SoundAssistService".equals(service.service.getClassName())) {
                Log.i("AudioService", "SoundAssistant already started");
                try {
                    Thread.sleep(1000L);
                } catch (Exception e) {
                }
                audioService.mStartingSoundAssistant = false;
                return;
            }
        }
        try {
            audioService.startSoundAssistantService(0);
            Thread.sleep(3000L);
        } catch (Exception e2) {
        }
        audioService.mStartingSoundAssistant = false;
    }

    private boolean fromVolumeKey(String caller) {
        if ("FINE_VOLUME".equals(caller) || "com.android.settings".equals(caller) || "com.android.systemui".equals(caller)) {
            return false;
        }
        return true;
    }

    private void createVariableMediaVolumeMap(int[] volumeSteps) {
        int lower;
        int raise;
        int maxIndex = getStreamMaxVolume(3);
        this.mVolumeMap = new VolumeMap[(maxIndex * 10) + 1];
        int beforeStep = 0;
        for (int i = 0; i < volumeSteps.length; i++) {
            if (i == 0) {
                lower = 0;
            } else {
                int lower2 = i - 1;
                lower = volumeSteps[lower2];
            }
            if (i == volumeSteps.length - 1) {
                raise = maxIndex * 10;
            } else {
                int raise2 = i + 1;
                raise = volumeSteps[raise2];
            }
            int curStep = volumeSteps[i];
            for (int index = beforeStep; index < curStep; index++) {
                this.mVolumeMap[index] = new VolumeMap((short) lower, (short) curStep);
            }
            this.mVolumeMap[curStep] = new VolumeMap((short) lower, (short) raise);
            beforeStep = curStep + 1;
        }
        int i2 = maxIndex * 10;
        int lower3 = beforeStep - 1;
        while (beforeStep <= maxIndex * 10) {
            this.mVolumeMap[beforeStep] = new VolumeMap((short) lower3, (short) i2);
            beforeStep++;
        }
    }

    private void reloadVariableMediaVolumeSteps() {
        String strSteps = Settings.System.getString(this.mContentResolver, "sec_volume_steps");
        if (strSteps == null || strSteps.length() == 0) {
            return;
        }
        try {
            String[] temp = strSteps.split(",");
            this.mVolumeSteps = new int[temp.length];
            for (int cur = 0; cur < temp.length; cur++) {
                this.mVolumeSteps[cur] = Integer.parseInt(temp[cur]);
            }
            createVariableMediaVolumeMap(this.mVolumeSteps);
        } catch (Exception e) {
            Log.d("AudioService", "reloadVariableMediaVolumeSteps", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void clearSoundAssistantSettings() {
        this.mMediaVolumeStepIndex = 10;
        this.mSettingHelper.removeValue("media_volume_step_index");
        this.mEnableSoundAssistant = false;
        this.mSettingHelper.removeValue("enable_floating_button");
        this.mSelfiestickIsConnected = false;
        if (this.mHeadsetOnlyStream != 0) {
            AudioSystem.setPolicyParameters("ring_through_headset=0");
        }
        this.mHeadsetOnlyStream = 0;
        this.mSettingHelper.removeValue("ring_through_headset");
        if (this.mLRSwitching == 1) {
            AudioSystem.setParameters("sound_lr_switch=0");
        }
        this.mLRSwitching = 0;
        this.mSettingHelper.removeValue("sound_lr_switch");
        setMediaVolumeSteps(null);
        this.mSettingHelper.removeValue("ignore_audio_focus");
        this.mMediaFocusControl.setIgnoreAudioFocus(-1, false);
        for (int i = 0; i < this.mAppVolumeFromSoundAssistant.size(); i++) {
            int appUid = this.mAppVolumeFromSoundAssistant.keyAt(i);
            this.mMultiSoundManager.setAppVolume(appUid, 100);
        }
        this.mAppVolumeFromSoundAssistant.clear();
        int ret = this.mSettingHelper.removeValue("mono_audio_db");
        if (ret > 0) {
            this.mMonoMode = 0;
            Settings.System.putIntForUser(this.mContentResolver, "mono_audio_db", this.mMonoMode, -2);
            setMonoMode(this.mMonoMode);
        }
        int ret2 = this.mSettingHelper.removeValue("sound_balance");
        if (ret2 > 0) {
            this.mSoundBalance = 50;
            Settings.System.putIntForUser(this.mContentResolver, "sound_balance", this.mSoundBalance, -2);
            AudioSystem.setParameters("sound_balance=" + this.mSoundBalance);
        }
    }

    String getSoundAssistant(String key) {
        if ("adjust_media_volume_only".equals(key)) {
            return String.valueOf(this.mAdjustMediaVolumeOnly ? 1 : 0);
        }
        if ("version".equals(key)) {
            return DATA.DM_FIELD_INDEX.SIP_SESSION_TIMER;
        }
        if ("get_app_volume_list".equals(key)) {
            StringBuilder appVolumeList = new StringBuilder();
            for (int i = 0; i < this.mAppVolumeFromSoundAssistant.size(); i++) {
                int uid = this.mAppVolumeFromSoundAssistant.keyAt(i);
                int ratio = this.mAppVolumeFromSoundAssistant.valueAt(i);
                appVolumeList.append(uid);
                appVolumeList.append("=");
                appVolumeList.append(ratio);
                if (i != this.mAppVolumeFromSoundAssistant.size() - 1) {
                    appVolumeList.append(";");
                }
            }
            return appVolumeList.toString();
        } else if ("enable_floating_button".equals(key)) {
            return String.valueOf(this.mEnableSoundAssistant ? 1 : 0);
        } else {
            if ("ignore_audio_focus".equals(key)) {
                return String.valueOf(this.mIgnoreAudioFocusUid);
            }
            if ("media_volume_step_index".equals(key)) {
                return String.valueOf(this.mMediaVolumeStepIndex);
            }
            if ("selfiestick_connected".equals(key)) {
                return String.valueOf(this.mSelfiestickIsConnected ? 1 : 0);
            }
            if ("ring_through_headset".equals(key)) {
                return String.valueOf(this.mHeadsetOnlyStream);
            }
            if ("sound_lr_switch".equals(key)) {
                return String.valueOf(this.mLRSwitching);
            }
            if ("using_audio_uids".equals(key)) {
                return getApplicationUidListUsingAudio();
            }
            if ("media_button_package".equals(key)) {
                return this.mMediaSessionService.getMediaButtonSessionPackage();
            }
            return null;
        }
    }

    String getApplicationUidListUsingAudio() {
        int uid;
        if (this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") != 0) {
            Log.w("AudioService", "MODIFY_PHONE_STATE Permission Denial from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return "";
        }
        StringBuilder builder = new StringBuilder();
        List<AudioPlaybackConfiguration> list = this.mPlaybackMonitor.getActivePlaybackConfigurations(true);
        for (AudioPlaybackConfiguration conf : list) {
            AudioAttributes attr = conf.getAudioAttributes();
            if (AudioAttributes.toLegacyStreamType(attr) == 3 && conf.getPlayerState() != 1 && (uid = conf.getClientUid()) >= 10000) {
                builder.append(uid + ";");
            }
        }
        return builder.toString();
    }

    private void releaseCPUBoost() {
        if (this.mSemDvfsCpuMin != null) {
            Log.i("AudioService", "release() cpu min lock");
            this.mSemDvfsCpuMin.release();
        }
        if (Rune.SEC_AUDIO_CPU_STATE_LOCK && this.mCpuStateLock != null) {
            Log.i("AudioService", "releaseCPUBoost CPU state lock");
            this.mCpuStateLock.release();
        }
    }

    private void acquireCPUBoost() {
        if (this.mSemDvfsCpuMin == null) {
            return;
        }
        Log.i("AudioService", "acquire() cpu min lock for audio VoIP : " + this.mCPUBoostValueForVoIP);
        this.mSemDvfsCpuMin.setDvfsValue(this.mSemDvfsCpuMin.getApproximateFrequency(this.mCPUBoostValueForVoIP));
        this.mSemDvfsCpuMin.acquire(30000);
        if (Rune.SEC_AUDIO_CPU_STATE_LOCK && this.mCpuStateLock != null) {
            Log.i("AudioService", "acquireCPUBoost CPU state lock");
            this.mCpuStateLock.acquire(30000);
        }
        sendMsg(this.mAudioHandler, 117, 1, 0, 0, null, Convert.BitRate.VIDEO_FHD60_BITRATE);
    }

    private void startCPUBoostForVoIP() {
        if (this.mCPUBoostValueForVoIP == 0) {
            return;
        }
        this.mIsCPUBoostedForVoIP = true;
        if (this.mSemDvfsCpuMin == null) {
            this.mSemDvfsCpuMin = SemDvfsManager.createInstance(this.mContext, "AudioService", 12);
        }
        if (this.mSemDvfsCpuMin == null) {
            Log.w("AudioService", "DvfsManager is null");
            return;
        }
        if (Rune.SEC_AUDIO_CPU_STATE_LOCK) {
            Log.i("AudioService", "initCPUBoost create cpu state lock");
            this.mCpuStateLock = SemDvfsManager.createInstance(this.mContext, "AudioService", 23);
        }
        acquireCPUBoost();
    }

    private void stopCPUBoostForVoIP() {
        if (this.mCPUBoostValueForVoIP == 0) {
            return;
        }
        this.mIsCPUBoostedForVoIP = false;
        sendMsg(this.mAudioHandler, 117, 0, 0, 0, null, 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void checkAndSendEarCareInfo() {
        int nState;
        if (!this.mSystemReady) {
            return;
        }
        Intent broadcast = new Intent("com.samsung.android.app.audio.epinforesponse");
        Bundle extras = new Bundle();
        boolean isConnected = checkDeviceConnected(384);
        if (this.mIsEnableVolumeMonitor && isConnected) {
            if (getActiveStreamCount() > 0) {
                AudioSystem.setParameters("earcare=on");
                nState = 1;
            } else {
                AudioSystem.setParameters("earcare=noActive");
                nState = 0;
            }
            String strState = AudioSystem.getParameters("earcare_percent");
            if (strState != null && !"".equals(strState)) {
                if (strState.length() <= "earcare_percent=".length()) {
                    return;
                }
                int nPercent = Integer.valueOf(strState.substring("earcare_percent=".length())).intValue();
                Log.i("AudioService", "checkAndSendEarCareInfo() - send intent");
                extras.putInt("state", nState);
                extras.putInt("percent", nPercent);
                broadcast.putExtras(extras);
                sendBroadcastToUser(broadcast, UserHandle.CURRENT);
                Log.e("AudioService", "checkAndSendEarCareInfo() state: " + nState + " percent: " + nPercent);
            }
            Log.i("AudioService", "checkAndSendEarCareInfo() - Loop");
            sendMsg(this.mAudioHandler, 107, 1, 0, 0, null, 60000);
            return;
        }
        Log.i("AudioService", "checkAndSendEarCareInfo() - send off");
        AudioSystem.setParameters("earcare=off");
        extras.putInt("state", 0);
        broadcast.putExtras(extras);
        sendBroadcastToUser(broadcast, UserHandle.CURRENT);
        this.mAudioHandler.removeMessages(107);
    }

    private int getActiveStreamCount() {
        int nReturn = 0;
        for (int nReturn2 = 0; nReturn2 < AudioSystem.getNumStreamTypes(); nReturn2++) {
            if (AudioSystem.isStreamActive(nReturn2, 0)) {
                nReturn++;
            }
        }
        return nReturn;
    }

    private void increaseEarJackCounter() {
        try {
            File earJackCounter = new File("/efs/FactoryApp/earjack_count");
            if (!earJackCounter.exists()) {
                if (earJackCounter.createNewFile()) {
                    FileUtils.stringToFile("/efs/FactoryApp/earjack_count", "1");
                }
            } else {
                String strEARCount = FileUtils.readTextFile(new File("/efs/FactoryApp/earjack_count"), 0, null);
                if (!"".equals(strEARCount)) {
                    long count = Long.parseLong(strEARCount) + 1;
                    FileUtils.stringToFile("/efs/FactoryApp/earjack_count", Long.toString(count));
                } else {
                    FileUtils.stringToFile("/efs/FactoryApp/earjack_count", "1");
                }
            }
        } catch (IOException e) {
            Log.d("AudioService", "IOException : " + e);
        } catch (NumberFormatException e2) {
            Log.d("AudioService", "NumberFormatException : " + e2);
        }
    }

    private int getCurOutDevice() {
        String path = AudioSystem.getPolicyParameters("outDevice;audioservice");
        if (path == null || "".equals(path)) {
            Log.i("AudioService", "getCurOutDevice : Can't get outDevice");
            return -1;
        }
        return Integer.valueOf(path).intValue();
    }

    private void startLogging() {
        Intent intent = new Intent("com.sec.media.action.AUDIOCORE_LOGGING");
        PendingIntent sender = PendingIntent.getBroadcast(this.mContext, 0, intent, 0);
        this.mAlarmManager.setRepeating(3, SystemClock.elapsedRealtime() + 86400000, 86400000L, sender);
    }

    private String getAudioCoreBigData(ArrayList<String> types, ArrayList<String> values, int size) {
        JSONObject obj = null;
        try {
            obj = new JSONObject();
            for (int i = 0; i < size; i++) {
                obj.put(types.get(i), values.get(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj.toString();
    }

    private void sendAudioCoreBigData(ArrayList<String> types, ArrayList<String> values) {
        int size = types.size();
        String basic_customDataSet = getAudioCoreBigData(types, values, size).replaceAll("\\{", "").replaceAll("\\}", "");
        if (this.mSemHqmManager != null) {
            if (this.mIsBigDataApp) {
                Log.v("AudioService", "sendAudioCoreBigData() App update !!!");
                this.mSemHqmManager.sendHWParamToHQM(1, "Audio", "FWAC", "sm", "0.0", GlobalSettings.SEC_AS, "", basic_customDataSet, "");
                return;
            }
            Log.v("AudioService", "sendAudioCoreBigData() Server update !!!");
            this.mSemHqmManager.sendHWParamToHQM(0, "Audio", "FWAC", "sm", "0.0", GlobalSettings.SEC_AS, "", basic_customDataSet, "");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void checkSystemAudioFiles() {
        int data_cnt = 0;
        ArrayList<String> types = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        String[] SYSTEMAUDIO_BIGDATA = {"SPK_TEMP_MAX", "SPK_TEMP_OVERCNT", "SPK_EXCU_MAX", "SPK_EXCU_OVERCNT"};
        String[] SYSTEMAUDIO_FILES = {"/sys/class/dsm/max98506/spk_temp_max", "/sys/class/dsm/max98506/spk_temp_overcnt", "/sys/class/dsm/max98506/spk_excu_max", "/sys/class/dsm/max98506/spk_excu_overcnt"};
        for (int i = 0; i < SYSTEMAUDIO_BIGDATA.length; i++) {
            FileReader fileReader = null;
            BufferedReader bufferedReader = null;
            try {
                try {
                    fileReader = new FileReader(SYSTEMAUDIO_FILES[i]);
                    bufferedReader = new BufferedReader(fileReader);
                    String value = bufferedReader.readLine();
                    if (value != null) {
                        types.add(SYSTEMAUDIO_BIGDATA[i]);
                        values.add(value);
                        data_cnt++;
                    }
                    try {
                        fileReader.close();
                        bufferedReader.close();
                    } catch (IOException e) {
                        Log.d("AudioService", "IOException");
                    }
                } catch (FileNotFoundException e2) {
                    Log.e("AudioService", "system audio file does not exists");
                    if (fileReader != null) {
                        fileReader.close();
                    }
                    if (bufferedReader != null) {
                        bufferedReader.close();
                    }
                } catch (IOException e3) {
                    Log.d("AudioService", "IOException");
                    if (fileReader != null) {
                        fileReader.close();
                    }
                    if (bufferedReader != null) {
                        bufferedReader.close();
                    }
                }
            } catch (Throwable th) {
                if (fileReader != null) {
                    try {
                        fileReader.close();
                    } catch (IOException e4) {
                        Log.d("AudioService", "IOException");
                        throw th;
                    }
                }
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                throw th;
            }
        }
        if (data_cnt <= 0 || data_cnt > SYSTEMAUDIO_BIGDATA.length) {
            return;
        }
        sendSystemAudioBigData(types, values, data_cnt);
    }

    private void sendSystemAudioBigData(ArrayList<String> types, ArrayList<String> values, int size) {
        String basic_customDataSet = getAudioCoreBigData(types, values, size);
        String basic_customDataSet2 = basic_customDataSet.replaceAll("\\{", "").replaceAll("\\}", "");
        if (this.mSemHqmManager != null) {
            Log.i("AudioService", "sendSystemAudioBigData() BigData update !!!");
            this.mSemHqmManager.sendHWParamToHQM(0, "Audio", "AUDI", "sm", "0.0", GlobalSettings.SEC_AS, "", basic_customDataSet2, "");
            return;
        }
        Log.e("AudioService", "sendSystemAudioBigData() mSemHqmManager is null!!");
    }

    private boolean isCoverOpen() {
        CoverState state;
        if (this.mCoverManager != null && (state = this.mCoverManager.getCoverState()) != null && !state.getSwitchState()) {
            return false;
        }
        return true;
    }

    private boolean isNeonCoverClosed() {
        CoverState state;
        if (this.mCoverManager != null && (state = this.mCoverManager.getCoverState()) != null && state.getType() == 11 && !state.getSwitchState()) {
            return true;
        }
        return false;
    }

    private void handleSetRingerMode(int ringerMode, String caller) {
        boolean skipPlaySound = "com.drivemode".equals(caller);
        switch (ringerMode) {
            case 0:
                Log.i("AudioService", "Ringer mode : silent & set driving mode off");
                this.mSilentModeOff = false;
                break;
            case 1:
                Log.i("AudioService", "Ringer mode : vibrate");
                this.mSilentModeOff = false;
                sendMsg(this.mAudioHandler, 110, 1, 0, 0, null, 0);
                break;
            case 2:
                Log.i("AudioService", "Ringer mode : normal");
                if (!skipPlaySound) {
                    sendMsg(this.mAudioHandler, 109, 1, 0, 0, null, 0);
                    break;
                }
                break;
        }
        sendMsg(this.mAudioHandler, 114, 2, ringerMode, 0, "persist.audio.ringermode", 0);
        if (ringerMode == 0 || ringerMode == 1) {
            sendMsg(this.mAudioHandler, 114, 2, 1, 0, "persist.sys.silent", 0);
        } else {
            sendMsg(this.mAudioHandler, 114, 2, 0, 0, "persist.sys.silent", 0);
        }
        if (this.mGoodCatchOn[0]) {
            this.mSemGoodCatchManager.update(GOODCATCH_MODE[0], caller, ringerMode, "", this.mContext.getPackageName());
        }
    }

    private void playSilentModeSound() {
        if (this.mSilentModeOff) {
            Log.i("AudioService", "playSilentModeSound: skipping as mSilentModeOff TRUE");
        } else if (getMode() == 3) {
            Log.i("AudioService", "playSilentModeSound: skipping playSilentModeSound");
        } else if (getMode() == 1 && isBluetoothScoOn()) {
            Log.i("AudioService", "playSilentModeSound: skipping while inband ringtone is playing");
        } else {
            this.mIsPlaySilentModeOff = true;
            playSoundEffect(AudioManager.getPlaySoundTypeForSEP(101));
            sendMsg(this.mAudioHandler, 111, 1, 0, 0, null, 500);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setMonoMode(int monoMode) {
        AudioSystem.setParameters("toMono=" + monoMode);
    }

    void onInitDualSpeaker() {
        if (AudioSystem.checkAudioFlinger() == 0) {
            try {
                if (Integer.parseInt(AudioSystem.getParameters("getActiveSoundInfo")) == 0) {
                    setDualSpeaker(this.mDualSpkEnable);
                    return;
                }
            } catch (NumberFormatException e) {
                Log.e("AudioService", "invalid active sound info value");
            }
        }
        sendMsg(this.mAudioHandler, 131, 0, 0, 0, null, 1000);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setDualSpeaker(int dualSpk) {
        setRampConfig(this.dualSpeakerPreset);
        AudioSystem.setParameters("Dualspk=" + dualSpk);
    }

    private void setRampConfig(RampInPreset preset) {
        AudioSystem.setParameters(preset.toString());
    }

    private void setGlobalMySound(int globalMySound) {
        AudioSystem.setEffectParameters("setGlobalMSEffect=" + globalMySound);
        ContentResolver cr = this.mContentResolver;
        String dhaParam = "0,0,0,0,0,0,0,0,0,0,0,0,0,0";
        int hearing_diagnosis = Settings.System.getInt(cr, "hearing_diagnosis", 0);
        if (hearing_diagnosis == 1) {
            int state = Settings.System.getInt(cr, "hearing_revision", 0);
            int SelectEar = Settings.System.getInt(cr, "hearing_direction", 0);
            String Param = Settings.System.getString(cr, "hearing_parameters");
            dhaParam = state + "," + SelectEar + "," + Param;
        }
        AudioSystem.setEffectParameters("dha=" + dhaParam);
        Log.d("AudioService", "setGlobalMySound Gain dha Parameter : " + dhaParam);
    }

    private void setGlobalUpscaler(int mode) {
        AudioSystem.setParameters("upscaler=" + mode);
    }

    private boolean isSoundAliveExist() {
        if (this.mIsSoundAliveExist) {
            return true;
        }
        this.mIsSoundAliveExist = this.mPackageManager.resolveContentProvider("com.sec.android.app.soundalive.compatibility.SAContentProvider", 0) != null;
        return this.mIsSoundAliveExist;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyDVFSToSoundAlive(int state) {
        if ((state != 2 && !wasStreamActiveRecently(3, 0)) || !isSoundAliveExist()) {
            return;
        }
        Log.d("AudioService", "notify dvfs state : " + state + ", screen : " + this.mIsScreenOn);
        try {
            ContentValues v = new ContentValues();
            v.put("SCREEN_STATE", Integer.valueOf(this.mIsScreenOn ? 1 : 0));
            this.mContentResolver.insert(Uri.parse("content://com.sec.android.app.soundalive.compatibility.SAContentProvider"), v);
        } catch (Exception e) {
            Log.e("AudioService", "notifyDVFSToSoundAlive", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setNbQualityMode(int nbQualityMode) {
        if (nbQualityMode > 0) {
            AudioSystem.setParameters("nb_quality=on");
        } else {
            AudioSystem.setParameters("nb_quality=off");
        }
    }

    private void setAllSoundMuteToNative() {
        AudioSystem.setPolicyParameters("allsoundmute=" + this.mAllSoundMute);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setAllSoundMute() {
        setAllSoundMuteToNative();
        sendMsg(this.mAudioHandler, 114, 2, this.mAllSoundMute, 0, "persist.audio.allsoundmute", 0);
    }

    private void setSoundBalance() {
        int soundBalance = Settings.System.getIntForUser(this.mContentResolver, "sound_balance", 50, -2);
        AudioSystem.setParameters("sound_balance=" + soundBalance);
        this.mSoundBalance = soundBalance;
    }

    private boolean isRestrictionHeadphone() {
        Uri uri = Uri.parse("content://com.sec.knox.provider/RestrictionPolicy2");
        Cursor cr = this.mContentResolver.query(uri, null, "isHeadPhoneEnabled", new String[]{"true"}, null);
        if (cr != null) {
            try {
                cr.moveToFirst();
                if ("false".equals(cr.getString(cr.getColumnIndex("isHeadPhoneEnabled")))) {
                    Log.v("AudioService", "Headset disabled");
                    return true;
                }
                return false;
            } finally {
                cr.close();
            }
        }
        return false;
    }

    private void onSendBecomingNoisyIntent(int device, int personaId) {
        Intent noisySecIntent = new Intent("android.media.AUDIO_BECOMING_NOISY_SEC");
        this.mContext.sendBroadcastAsUser(noisySecIntent, new UserHandle(personaId));
        Intent noisyIntent = new Intent("android.media.AUDIO_BECOMING_NOISY");
        if ((device & 896) != 0) {
            noisyIntent.putExtra("android.bluetooth.a2dp.extra.DISCONNECT_A2DP", true);
        }
        this.mContext.sendBroadcastAsUser(noisyIntent, new UserHandle(personaId));
    }

    private void checkRCVStateForSARTest() {
        boolean rcvSARValue = false;
        if (this.mMode == 2 || this.mMode == 3 || this.mSarBackoffParam) {
            boolean isCheckHeadPhoneConnected = checkDeviceConnected(12);
            if (!isCheckHeadPhoneConnected && this.mForcedUseForComm == 0 && getCurOutDevice() == 1) {
                rcvSARValue = true;
            }
        }
        boolean isCheckHeadPhoneConnected2 = this.mCurRCVBackOffState;
        if (isCheckHeadPhoneConnected2 == rcvSARValue) {
            return;
        }
        Intent intent = new Intent("android.samsung.media.action.receiver_sar");
        intent.putExtra("android.samsung.media.extra.receiver", rcvSARValue);
        sendBroadcastToAll(intent);
        this.mCurRCVBackOffState = rcvSARValue;
        Log.d("AudioService", "Send receiver_sar state " + rcvSARValue);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void checkAndSetThemeSound(boolean userSwitch) {
        String touch_sound = Settings.Global.getString(this.mContentResolver, "theme_touch_sound");
        if (userSwitch) {
            this.mThemeTouchSoundPath = touch_sound;
        } else if (touch_sound != null && !touch_sound.equals(this.mThemeTouchSoundPath)) {
            this.mThemeTouchSoundPath = touch_sound;
            Log.v("AudioService", "Theme is changed.");
        } else if (this.mThemeTouchSoundPath != null && touch_sound == null) {
            this.mThemeTouchSoundPath = null;
            Log.v("AudioService", "Theme is released.");
        }
        unloadSoundEffects();
        loadSoundEffects();
    }

    private boolean isAllowed(AudioAttributes aa) {
        switch (aa.getUsage()) {
            case 4:
            case 5:
            case 6:
                return true;
            default:
                return false;
        }
    }

    private void checkAndSetSplitSound(AudioAttributes aa, String callingPackageName, int durationHint) {
        if ("com.android.server.telecom".equals(callingPackageName) || isAllowed(aa)) {
            this.mIsExceptionalDevice = checkDeviceConnected(67108988);
            if (this.mIsWifiDisplayConnected && !this.mIsExceptionalDevice && AudioSystem.isStreamActiveRemotely(3, 0)) {
                this.mSplitSound = true;
            } else {
                this.mSplitSound = false;
            }
            AudioSystem.setPolicyParameters("SplitSound=" + this.mSplitSound);
        }
    }

    public static /* synthetic */ void lambda$new$2(AudioService audioService, SemContextEvent contextEvent) {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean checkDeviceConnected(int checkDevices) {
        synchronized (this.mConnectedDevices) {
            try {
                if ((checkDevices & Integer.MIN_VALUE) == 0) {
                    for (int i = 0; i < this.mConnectedDevices.size(); i++) {
                        DeviceListSpec deviceSpec = this.mConnectedDevices.valueAt(i);
                        if ((deviceSpec.mDeviceType & Integer.MIN_VALUE) == 0 && (deviceSpec.mDeviceType & checkDevices) != 0) {
                            return true;
                        }
                    }
                    if (checkDevices == 32768 && AudioSystem.getDeviceConnectionState(32768, "0") > 0) {
                        return true;
                    }
                } else {
                    for (int i2 = 0; i2 < this.mConnectedDevices.size(); i2++) {
                        DeviceListSpec deviceSpec2 = this.mConnectedDevices.valueAt(i2);
                        if ((deviceSpec2.mDeviceType & Integer.MIN_VALUE) != 0 && (deviceSpec2.mDeviceType & checkDevices & Integer.MAX_VALUE) != 0) {
                            return true;
                        }
                    }
                }
                return false;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateBluetoothVolume(String address, boolean delayedCall) {
        VolumeStreamState volState = this.mStreamStates[3];
        String name = volState.getSettingNameForDevice(128);
        if (name == null) {
            return;
        }
        Log.i("AudioService", "[BT_VOL_SEPARATION]updateBluetoothVolume() new address = " + address + " but current address = " + name);
        StringBuilder sb = new StringBuilder();
        sb.append(name.substring(0, name.lastIndexOf(95) + 1));
        sb.append(address);
        String name2 = sb.toString();
        if (!"".equals(address) && this.mConnectedBTList.indexOf(name2) == -1) {
            if (Settings.System.getIntForUser(this.mContentResolver, name2, -1, -2) >= 9) {
                long ident = Binder.clearCallingIdentity();
                try {
                    Settings.System.putIntForUser(this.mContentResolver, name2, 9, -2);
                    Binder.restoreCallingIdentity(ident);
                    this.mSettingHelper.setBTVolumeIndex(address, 0);
                } catch (Throwable th) {
                    Binder.restoreCallingIdentity(ident);
                    throw th;
                }
            }
            this.mConnectedBTList.add(name2);
        }
        if (delayedCall) {
            AudioSystem.setStreamVolumeIndex(3, 0, 128);
        }
        int index = Settings.System.getIntForUser(this.mContentResolver, name2, -1, -2);
        this.mBTAddress = address;
        if (index == -1) {
            Log.i("AudioService", "[BT_VOL_SEPARATION] set BT headset default value as 8 at first");
            index = 8;
        }
        volState.mIndexMap.put(128, volState.getValidIndex(10 * index));
        int preVolIndex = index;
        int fineIndex = this.mSettingHelper.getBTVolumeIndex(address);
        Log.i("AudioService", "[BT_VOL_SEPARATION]setFineVolume Index for stream 3 volume index " + index + " and fine index as " + fineIndex + "  address=" + address + ".");
        this.mFineMediaVolume.setIndex(fineIndex, 128);
        if (!delayedCall) {
            sendMsg(this.mAudioHandler, 123, 2, preVolIndex, fineIndex, volState, 100);
        } else {
            sendMsg(this.mAudioHandler, 0, 2, 128, 0, volState, 0);
        }
    }

    public void makeBTVolumeSame(String lastAddr, String toBeChangedAddr) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") != 0) {
            Log.w("AudioService", "MODIFY_PHONE_STATE Permission Denial from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
        } else if (lastAddr.equals("") || toBeChangedAddr.equals("")) {
        } else {
            this.mSettingHelper.setBTVolumeIndex(toBeChangedAddr, this.mSettingHelper.getBTVolumeIndex(lastAddr));
            String curName = this.mStreamStates[3].getSettingNameForDevice(128);
            if (curName == null) {
                return;
            }
            String tobeChangedName = curName.substring(0, curName.lastIndexOf(95) + 1) + toBeChangedAddr;
            long ident = Binder.clearCallingIdentity();
            try {
                Settings.System.putIntForUser(this.mContentResolver, tobeChangedName, Settings.System.getIntForUser(this.mContentResolver, curName, -1, -2), -2);
                Binder.restoreCallingIdentity(ident);
                this.m1stBTAddr = toBeChangedAddr;
                this.m2ndBTAddr = lastAddr;
            } catch (Throwable th) {
                Binder.restoreCallingIdentity(ident);
                throw th;
            }
        }
    }

    public int setDeviceToForceByUser(int device, String address) {
        int status;
        Log.d("AudioService", "setDeviceToForceByUser(" + device + ", " + address + ")");
        if (device == 128) {
            updateBluetoothVolume(address, false);
        }
        if (Rune.SEC_AUDIO_MULTI_SOUND && isMultiSoundOn() && Binder.getCallingUid() == 1002 && getDeviceForStream(3) != 128 && getPinDevice() == 128) {
            Log.d("AudioService", "Device does not change while MultiSound On");
            String btName = null;
            if (this.mA2dp != null) {
                List<BluetoothDevice> deviceList = this.mA2dp.getConnectedDevices();
                if (deviceList != null && deviceList.size() > 1) {
                    Iterator<BluetoothDevice> it = deviceList.iterator();
                    while (true) {
                        if (!it.hasNext()) {
                            break;
                        }
                        BluetoothDevice btDevice = it.next();
                        if (btDevice.getAddress().equals(address)) {
                            this.mA2dp.setActiveDevice(btDevice);
                            btName = btDevice.getAliasName();
                            break;
                        }
                    }
                } else {
                    BluetoothDevice btDevice2 = this.mA2dp.getActiveDevice();
                    if (btDevice2 != null) {
                        btName = btDevice2.getAliasName();
                    }
                }
            }
            synchronized (this.mCurAudioRoutes) {
                this.mCurAudioRoutes.bluetoothName = btName;
            }
            return 0;
        }
        synchronized (this.mConnectedDevices) {
            DeviceListSpec spec = this.mConnectedDevices.get(makeDeviceListKey(device, address));
            String btName2 = null;
            if (spec != null) {
                if (AudioDeviceInfo.convertInternalDeviceToDeviceType(device) == 8) {
                    if (this.mA2dp != null) {
                        List<BluetoothDevice> deviceList2 = this.mA2dp.getConnectedDevices();
                        if (deviceList2 != null && deviceList2.size() > 1) {
                            Iterator<BluetoothDevice> it2 = deviceList2.iterator();
                            while (true) {
                                if (!it2.hasNext()) {
                                    break;
                                }
                                BluetoothDevice btDevice3 = it2.next();
                                if (btDevice3.getAddress().equals(address)) {
                                    this.mA2dp.setActiveDevice(btDevice3);
                                    btName2 = btDevice3.getAliasName();
                                    break;
                                }
                            }
                        } else {
                            BluetoothDevice btDevice4 = this.mA2dp.getActiveDevice();
                            if (btDevice4 != null) {
                                btName2 = btDevice4.getAliasName();
                            }
                        }
                    }
                    String eventSource = "setDeviceToForceByUser(true) from u/pid:" + Binder.getCallingUid() + SliceClientPermissions.SliceAuthority.DELIMITER + Binder.getCallingPid();
                    setBluetoothA2dpOnInt(true, eventSource);
                }
                if (device == 1024 && this.mDexState) {
                    this.mDexState = false;
                    AudioSystem.setPolicyParameters("DEXstate=" + this.mDexState);
                }
                status = AudioSystem.setDeviceConnectionState(device, 2, address, "");
                if (status == 0 && device == getDeviceForStream(3)) {
                    Log.d("AudioService", "Device is changed successfully by force");
                    this.mLastConnectedDevice = spec;
                }
                synchronized (this.mCurAudioRoutes) {
                    Log.i("AudioService", "send NEW_ROUTES MSG, BT Name is " + btName2);
                    this.mCurAudioRoutes.bluetoothName = btName2;
                    sendMsg(this.mAudioHandler, 12, 1, 0, 0, null, 0);
                }
            } else {
                Log.e("AudioService", "There is no device spec in connected devices");
                status = -1;
            }
        }
        return status;
    }

    private int getAvailableDeviceMaskForQuickSoundPath() {
        int blockDevices = 0;
        synchronized (this.mConnectedDevices) {
            if (checkDeviceConnected(536870912)) {
                blockDevices = 0 | 1879048191;
            }
            if (checkDeviceConnected(-2147352576)) {
                blockDevices |= 896;
            }
            if (AudioSystem.getForceUse(0) == 3) {
                blockDevices |= 1879048191;
            }
            if (checkDeviceConnected(32768)) {
                if (this.mIsWifiDisplayConnected) {
                    DisplayManager displayManager = (DisplayManager) this.mContext.getSystemService("display");
                    if (displayManager.semGetActiveDlnaState() == 1) {
                        blockDevices |= 1879048191;
                    }
                } else {
                    blockDevices |= 1879048191;
                }
            }
            if (AudioSystem.getForceUse(1) == 10001) {
                blockDevices |= 1879048191;
            }
        }
        int mask = 1879048191 & (~(536870912 | blockDevices));
        return mask;
    }

    public int getPriorityDevice(int exceptedDevice) {
        int priorityDevice = 2;
        int[] priorityDeviceForMedia = {32768, 128, 8, 4, 67108864, 8192, 16384, 4096, 1024, 2048, 2};
        synchronized (this.mConnectedDevices) {
            int i = 0;
            int connectedDevice = 0;
            for (int connectedDevice2 = 0; connectedDevice2 < this.mConnectedDevices.size(); connectedDevice2++) {
                if ((this.mConnectedDevices.valueAt(connectedDevice2).mDeviceType & Integer.MIN_VALUE) == 0) {
                    connectedDevice |= this.mConnectedDevices.valueAt(connectedDevice2).mDeviceType;
                }
            }
            while (true) {
                int i2 = i;
                int i3 = priorityDeviceForMedia.length;
                if (i2 >= i3) {
                    break;
                }
                if (exceptedDevice != priorityDeviceForMedia[i2] && (priorityDeviceForMedia[i2] & connectedDevice) != 0) {
                    priorityDevice = priorityDeviceForMedia[i2];
                    break;
                }
                i = i2 + 1;
            }
        }
        return priorityDevice;
    }

    public void setStreamVolumeForDevice(int streamType, int index, int flags, String callingPackage, int device) {
        setStreamVolume(streamType, index, flags, callingPackage, callingPackage, Binder.getCallingUid(), device);
    }

    public void setFineVolume(int streamType, int index, int flags, int device, String callingPackage) {
        setStreamVolume(streamType, index, flags, callingPackage, callingPackage, Binder.getCallingUid(), device);
    }

    public int getStreamVolumeForDevice(int streamType, int device) {
        return getStreamVolume(streamType, device);
    }

    public int getFineVolume(int streamType, int device) {
        if (device == 0) {
            device = getDeviceForStream(streamType);
        }
        int streamVolume = getStreamVolumeForDevice(streamType, device);
        if (streamVolume == 1) {
            int index = this.mStreamStates[streamType].getIndex(device);
            if (index == 0) {
                streamVolume = 0;
            }
        }
        int fineVolume = this.mStreamStates[streamType].mIsMuted ? 0 : this.mFineMediaVolume.getIndex(device);
        return (streamVolume * 10) + fineVolume;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendBecomingNoisyIntent(int uid) {
        String[] packageName = getPackageName(uid);
        int i = 0;
        if ("".equals(packageName[0])) {
            return;
        }
        while (true) {
            int i2 = i;
            int i3 = packageName.length;
            if (i2 >= i3) {
                return;
            }
            Intent noisyIntent = new Intent("android.media.AUDIO_BECOMING_NOISY");
            noisyIntent.setPackage(packageName[i2]);
            sendBroadcastToAll(noisyIntent);
            this.mMediaFocusControl.handleExternalFocusGain(uid);
            Log.d("AudioService", "sendBecomingNoisyIntent to " + packageName[i2]);
            i = i2 + 1;
        }
    }

    public int getPinDevice() {
        return this.mMultiSoundManager.getPinDevice();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public class MultiSoundManager {
        private static final int DEFAULT_VOLUME = 100;
        private float[] mVolumeTable;
        private HashMap<Integer, MultiSoundItem> mPinAppInfoList = new HashMap<>();
        private boolean mEnabled = false;

        MultiSoundManager() {
            makeVolumeTable();
        }

        private void makeVolumeTable() {
            this.mVolumeTable = new float[101];
            this.mVolumeTable[0] = 0.0f;
            this.mVolumeTable[100] = 1.0f;
            for (int i = 1; i < 100; i++) {
                this.mVolumeTable[i] = (float) (Math.exp(((i / 100.0d) * 5.0d) - 5.0d) - Math.exp(-5.0d));
            }
        }

        public void enable() {
            if (this.mEnabled) {
                return;
            }
            Log.d("AudioService", "enable");
            this.mEnabled = true;
            resetByEnableDisable();
            AudioService.this.showNotification();
        }

        public void disable() {
            if (!this.mEnabled) {
                return;
            }
            Log.d("AudioService", "disable");
            this.mEnabled = false;
            resetByEnableDisable();
            AudioService.this.clearNotification();
        }

        public boolean isEnabled() {
            return this.mEnabled;
        }

        private void resetByEnableDisable() {
            Log.d("AudioService", "resetByEnableDisable");
            if (this.mEnabled) {
                try {
                    ActivityManager.getService().registerProcessObserver(AudioService.this.mProcessObserver);
                } catch (RemoteException e) {
                    Log.e("AudioService", "RemoteException -registerProcessObserver");
                    e.printStackTrace();
                }
            } else {
                try {
                    ActivityManager.getService().unregisterProcessObserver(AudioService.this.mProcessObserver);
                } catch (RemoteException e2) {
                    Log.e("AudioService", "RemoteException -unregisterProcessObserver");
                    e2.printStackTrace();
                }
            }
            int curDevice = AudioSystem.getDevicesForStream(3);
            List<MultiSoundItem> list = new ArrayList<>(this.mPinAppInfoList.values());
            for (MultiSoundItem item : list) {
                if (item.getAppDevice(true) != 0) {
                    if (!this.mEnabled && curDevice != 32768) {
                        int appDevice = getAppDevice(item.mUid);
                        if (curDevice != appDevice) {
                            AudioService.this.sendBecomingNoisyIntent(item.mUid);
                        }
                    }
                    AudioService.this.mMediaFocusControl.updateFocusRequester(item.mUid);
                }
            }
            setStateToNative();
        }

        /* JADX WARN: Code restructure failed: missing block: B:32:0x007c, code lost:
            if (r9.this$0.isMultiSoundOn() == false) goto L41;
         */
        /* JADX WARN: Code restructure failed: missing block: B:33:0x007e, code lost:
            if (r11 == r0) goto L41;
         */
        /* JADX WARN: Code restructure failed: missing block: B:34:0x0080, code lost:
            r9.this$0.sendBecomingNoisyIntent(r8.getUid());
         */
        /* JADX WARN: Code restructure failed: missing block: B:36:0x008b, code lost:
            java.lang.Thread.sleep(200);
         */
        /* JADX WARN: Code restructure failed: missing block: B:38:0x008f, code lost:
            r3 = move-exception;
         */
        /* JADX WARN: Code restructure failed: missing block: B:39:0x0090, code lost:
            android.util.Log.e("AudioService", "Error in Thread.sleep() : " + r3);
         */
        /* JADX WARN: Removed duplicated region for block: B:46:0x00e3 A[Catch: all -> 0x011c, TryCatch #1 {, blocks: (B:4:0x000c, B:6:0x0017, B:8:0x0019, B:10:0x0028, B:12:0x002e, B:13:0x0036, B:15:0x0038, B:21:0x0046, B:22:0x0050, B:24:0x0056, B:26:0x0069, B:29:0x0070, B:31:0x0076, B:34:0x0080, B:36:0x008b, B:40:0x00a6, B:42:0x00c4, B:39:0x0090, B:44:0x00d4, B:46:0x00e3, B:48:0x00ef, B:50:0x00f5, B:52:0x0108, B:53:0x010b, B:51:0x00ff, B:47:0x00ec), top: B:64:0x000c, inners: #0 }] */
        /* JADX WARN: Removed duplicated region for block: B:47:0x00ec A[Catch: all -> 0x011c, TryCatch #1 {, blocks: (B:4:0x000c, B:6:0x0017, B:8:0x0019, B:10:0x0028, B:12:0x002e, B:13:0x0036, B:15:0x0038, B:21:0x0046, B:22:0x0050, B:24:0x0056, B:26:0x0069, B:29:0x0070, B:31:0x0076, B:34:0x0080, B:36:0x008b, B:40:0x00a6, B:42:0x00c4, B:39:0x0090, B:44:0x00d4, B:46:0x00e3, B:48:0x00ef, B:50:0x00f5, B:52:0x0108, B:53:0x010b, B:51:0x00ff, B:47:0x00ec), top: B:64:0x000c, inners: #0 }] */
        /* JADX WARN: Removed duplicated region for block: B:50:0x00f5 A[Catch: all -> 0x011c, TryCatch #1 {, blocks: (B:4:0x000c, B:6:0x0017, B:8:0x0019, B:10:0x0028, B:12:0x002e, B:13:0x0036, B:15:0x0038, B:21:0x0046, B:22:0x0050, B:24:0x0056, B:26:0x0069, B:29:0x0070, B:31:0x0076, B:34:0x0080, B:36:0x008b, B:40:0x00a6, B:42:0x00c4, B:39:0x0090, B:44:0x00d4, B:46:0x00e3, B:48:0x00ef, B:50:0x00f5, B:52:0x0108, B:53:0x010b, B:51:0x00ff, B:47:0x00ec), top: B:64:0x000c, inners: #0 }] */
        /* JADX WARN: Removed duplicated region for block: B:51:0x00ff A[Catch: all -> 0x011c, TryCatch #1 {, blocks: (B:4:0x000c, B:6:0x0017, B:8:0x0019, B:10:0x0028, B:12:0x002e, B:13:0x0036, B:15:0x0038, B:21:0x0046, B:22:0x0050, B:24:0x0056, B:26:0x0069, B:29:0x0070, B:31:0x0076, B:34:0x0080, B:36:0x008b, B:40:0x00a6, B:42:0x00c4, B:39:0x0090, B:44:0x00d4, B:46:0x00e3, B:48:0x00ef, B:50:0x00f5, B:52:0x0108, B:53:0x010b, B:51:0x00ff, B:47:0x00ec), top: B:64:0x000c, inners: #0 }] */
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct code enable 'Show inconsistent code' option in preferences
        */
        public void setAppDevice(int r10, int r11) {
            /*
                Method dump skipped, instructions count: 287
                To view this dump change 'Code comments level' option to 'DEBUG'
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.server.audio.AudioService.MultiSoundManager.setAppDevice(int, int):void");
        }

        public int getAppDevice(int uid, boolean forced) {
            if (forced) {
                if (this.mPinAppInfoList.get(Integer.valueOf(uid)) == null) {
                    return 0;
                }
                int device = this.mPinAppInfoList.get(Integer.valueOf(uid)).getAppDevice(true);
                return device;
            }
            int device2 = getAppDevice(uid);
            return device2;
        }

        public int getAppDevice(int uid) {
            int device;
            if (this.mPinAppInfoList.get(Integer.valueOf(uid)) == null) {
                return 0;
            }
            int device2 = this.mPinAppInfoList.get(Integer.valueOf(uid)).getAppDevice(false);
            if (device2 == 32768) {
                return device2;
            }
            if (this.mEnabled) {
                if (device2 == 2) {
                    int cur_device = AudioService.this.mStreamStates[3].mObservedDevices;
                    if ((67125262 & cur_device) == 0) {
                        synchronized (AudioService.this.mConnectedDevices) {
                            int connected_device = 0;
                            for (int i = 0; i < AudioService.this.mConnectedDevices.size(); i++) {
                                DeviceListSpec spec = (DeviceListSpec) AudioService.this.mConnectedDevices.valueAt(i);
                                if ((spec.mDeviceType & Integer.MIN_VALUE) == 0) {
                                    connected_device |= spec.mDeviceType & 67125262;
                                }
                            }
                            if ((16384 & connected_device) != 0) {
                                device = 16384;
                            } else if ((67108864 & connected_device) != 0) {
                                device = 67108864;
                            } else if ((4 & connected_device) != 0) {
                                device = 4;
                            } else if ((8 & connected_device) != 0) {
                                device = 8;
                            } else if ((1024 & connected_device) != 0) {
                                device = 1024;
                            } else {
                                device = 2;
                            }
                        }
                    } else {
                        device = cur_device;
                    }
                    return device;
                }
                return device2;
            }
            return 0;
        }

        public void setAppVolume(int uid, int ratio) {
            if (ratio <= 100 && ratio >= 0) {
                synchronized (AudioService.this.mMultiSoundLock) {
                    MultiSoundItem item = this.mPinAppInfoList.get(Integer.valueOf(uid));
                    boolean remove = false;
                    if (item == null) {
                        if (ratio == 100) {
                            return;
                        }
                        item = new MultiSoundItem(uid, 0, ratio);
                    } else {
                        item.setAppVolume(ratio);
                        remove = item.removable();
                    }
                    if (remove) {
                        this.mPinAppInfoList.remove(Integer.valueOf(uid));
                    } else {
                        this.mPinAppInfoList.put(Integer.valueOf(uid), item);
                    }
                    setAppVolumeToNative(uid);
                    return;
                }
            }
            Log.e("AudioService", "Invalid volume");
        }

        public void setAppVolumeToNative(int uid) {
            String parameters = "appvol_uid=" + uid + ";appvol_value=" + getAppVolumeFloat(uid);
            AudioSystem.setParameters(parameters);
        }

        public int getAppVolume(int uid) {
            synchronized (AudioService.this.mMultiSoundLock) {
                MultiSoundItem item = this.mPinAppInfoList.get(Integer.valueOf(uid));
                if (item != null) {
                    return item.getAppVolume();
                }
                return 100;
            }
        }

        public float getAppVolumeFloat(int uid) {
            float volume;
            synchronized (AudioService.this.mMultiSoundLock) {
                MultiSoundItem item = this.mPinAppInfoList.get(Integer.valueOf(uid));
                volume = 1.0f;
                if (item != null) {
                    volume = this.mVolumeTable[item.getAppVolume()];
                }
                if (Rune.SEC_AUDIO_PREVENT_OVERHEAT_BY_MAX_VOLUME) {
                    volume *= AudioService.this.mPreventOverheatState.getLimitedVolumeForOverheat(uid);
                }
            }
            return volume;
        }

        public void sendBecomingNoisyToPinnedApp(int device) {
            if (this.mEnabled) {
                List<MultiSoundItem> list = new ArrayList<>(this.mPinAppInfoList.values());
                for (MultiSoundItem item : list) {
                    if (getAppDevice(item.mUid) == device) {
                        AudioService.this.sendBecomingNoisyIntent(item.mUid);
                    }
                }
            }
        }

        public int getPinDevice() {
            int appDevice;
            if (!this.mEnabled) {
                return 0;
            }
            List<MultiSoundItem> items = new ArrayList<>(this.mPinAppInfoList.values());
            for (MultiSoundItem item : items) {
                if (item.getAppDevice(true) != 0 && (appDevice = getAppDevice(item.mUid)) != 0 && appDevice != 32768) {
                    return appDevice;
                }
            }
            return 0;
        }

        public String getPinAppInfo(int device) {
            synchronized (AudioService.this.mMultiSoundLock) {
                if (!this.mEnabled) {
                    return "";
                }
                Set<Map.Entry<Integer, MultiSoundItem>> set = this.mPinAppInfoList.entrySet();
                for (Map.Entry<Integer, MultiSoundItem> entry : set) {
                    int uid = entry.getKey().intValue();
                    if (getAppDevice(uid) == device) {
                        String[] packages = AudioService.this.getPackageName(uid);
                        String multiSoundPackage = packages[0];
                        String multiSoundAppInSetting = Settings.System.getString(AudioService.this.mContentResolver, AudioService.MULTISOUND_APP);
                        for (String packageName : packages) {
                            if (packageName.equals(multiSoundAppInSetting)) {
                                multiSoundPackage = packageName;
                                break;
                            }
                        }
                        try {
                            return AudioService.this.mPackageManager.getApplicationLabel(AudioService.this.mPackageManager.getApplicationInfo(multiSoundPackage, 0)).toString();
                        } catch (Exception e) {
                            Log.e("AudioService", "not found");
                        }
                    }
                }
                return "";
            }
        }

        public void resetPinDevice() {
            List<MultiSoundItem> items = new ArrayList<>(this.mPinAppInfoList.values());
            for (MultiSoundItem item : items) {
                if (item.getAppDevice(true) != 0) {
                    setAppDevice(item.mUid, 0);
                }
            }
            disable();
        }

        public int getDeviceMultiSoundUsingActually() {
            if (this.mEnabled) {
                List<MultiSoundItem> items = new ArrayList<>(this.mPinAppInfoList.values());
                for (MultiSoundItem item : items) {
                    if (item.getAppDevice(true) != 0 && AudioService.isUsingAudioForUid(item.mUid)) {
                        return getAppDevice(item.mUid);
                    }
                }
                return 0;
            }
            return 0;
        }

        public String[] getPinPackageName(int device) {
            synchronized (AudioService.this.mMultiSoundLock) {
                List<MultiSoundItem> items = new ArrayList<>(this.mPinAppInfoList.values());
                for (MultiSoundItem item : items) {
                    if (item.getAppDevice(true) == device) {
                        try {
                            return AudioService.this.getPackageName(item.getUid());
                        } catch (Exception e) {
                            Log.e("AudioService", "not found");
                        }
                    }
                }
                return new String[]{""};
            }
        }

        public void resetByAudioServerDied() {
            synchronized (AudioService.this.mMultiSoundLock) {
                Log.d("AudioService", "resetByAudioServerDied");
                Set<Map.Entry<Integer, MultiSoundItem>> set = this.mPinAppInfoList.entrySet();
                for (Map.Entry<Integer, MultiSoundItem> entry : set) {
                    MultiSoundItem item = entry.getValue();
                    setDeviceToNative(item.getUid(), item.getAppDevice(true));
                    setAppVolumeToNative(item.getUid());
                }
                setStateToNative();
            }
        }

        public boolean removeItem(int uid) {
            synchronized (AudioService.this.mMultiSoundLock) {
                if (this.mPinAppInfoList.get(Integer.valueOf(uid)) != null) {
                    Log.d("AudioService", "removeItem, " + uid);
                    this.mPinAppInfoList.remove(Integer.valueOf(uid));
                    setDeviceToNative(uid, 0);
                    return true;
                }
                return false;
            }
        }

        public void dump(PrintWriter pw) {
            synchronized (AudioService.this.mMultiSoundLock) {
                pw.println("\nMultiSound, size:" + this.mPinAppInfoList.size() + ", isEnabled:" + isEnabled());
            }
            Set<Map.Entry<Integer, MultiSoundItem>> set = this.mPinAppInfoList.entrySet();
            for (Map.Entry<Integer, MultiSoundItem> entry : set) {
                MultiSoundItem item = entry.getValue();
                String packageName = "";
                try {
                    packageName = AudioService.this.getPackageName(item.getUid())[0];
                } catch (Exception e) {
                }
                pw.print("  uid = " + item.getUid() + "(" + packageName + "), device=0x" + Integer.toHexString(item.getAppDevice(true)) + ", ");
                StringBuilder sb = new StringBuilder();
                sb.append("volume=");
                sb.append(item.getAppVolume());
                pw.println(sb.toString());
            }
            pw.println("  Current Default Device: 0x" + Integer.toHexString(AudioService.this.getDeviceForStream(3)));
            pw.println("  Pin Device: 0x" + Integer.toHexString(getPinDevice()));
            pw.println("  Pin App: " + getPinAppInfo(getPinDevice()));
            pw.println("  SEC_AUDIO_MULTI_SOUND=" + Rune.SEC_AUDIO_MULTI_SOUND);
            pw.println("  SEC_AUDIO_SUPPORT_D2D=" + Rune.SEC_AUDIO_SUPPORT_D2D);
            try {
                ApplicationInfo appInfo = AudioService.this.mContext.getPackageManager().getApplicationInfo("com.samsung.android.oneconnect", 0);
                if (appInfo != null) {
                    pw.println("  Smart Things=install");
                }
            } catch (PackageManager.NameNotFoundException e2) {
                pw.println("  Smart Things=uninstall");
            }
        }

        private void setStateToNative() {
            try {
                Thread.sleep(200L);
            } catch (Exception e) {
                Log.e("AudioService", "Error in Thread.sleep() : " + e);
            }
            AudioSystem.setPolicyParameters("multisound_on=" + this.mEnabled);
        }

        private void setDeviceToNative(int uid, int device) {
            AudioSystem.setPolicyParameters("multisound_setdevice_uid=" + uid + ";multisound_setdevice_device=" + device);
        }

        /* JADX INFO: Access modifiers changed from: private */
        /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
        public class MultiSoundItem {
            private int mDevice;
            private int mRatio;
            private int mUid;

            MultiSoundItem(int uid, int device, int ratio) {
                this.mUid = uid;
                this.mDevice = device;
                setAppVolume(ratio);
                this.mRatio = ratio;
            }

            public int getUid() {
                return this.mUid;
            }

            public void setAppDevice(int device) {
                this.mDevice = device;
            }

            public int getAppDevice(boolean force) {
                if (force || AudioService.this.checkDeviceConnected(this.mDevice)) {
                    return this.mDevice;
                }
                return 0;
            }

            public void setAppVolume(int ratio) {
                if (ratio < 0 || ratio > 100) {
                    Log.e("AudioService", "Invalid app volume");
                }
                this.mRatio = ratio;
            }

            public int getAppVolume() {
                return this.mRatio;
            }

            public boolean removable() {
                if (this.mDevice == 0 && this.mRatio == 100) {
                    return true;
                }
                return false;
            }
        }
    }

    private boolean isRaiseOrLowerOrSame(int direction) {
        switch (direction) {
            case -1:
            case 0:
            case 1:
                return true;
            default:
                return false;
        }
    }

    private int getDefaultVolumeOption() {
        if (Rune.SEC_AUDIO_DEFAULT_STREAM_NOTIFICATION && !this.mIsFactoryMode) {
            return 1;
        }
        return 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendBroadcastToSoundEventReceiver(int eventType, int value, String callingPackage) {
        synchronized (this.mEventReceivers) {
            try {
                try {
                    ArrayList<SoundEventReceiver> receiverList = this.mEventReceivers;
                    if (!receiverList.isEmpty() && eventType != 0) {
                        Iterator<SoundEventReceiver> it = receiverList.iterator();
                        while (it.hasNext()) {
                            SoundEventReceiver receiver = it.next();
                            if (receiver.hasEventType(eventType)) {
                                Log.i("AudioService", "Send broadcast to " + receiver.mEventReceiver);
                                Intent intent = new Intent("com.samsung.android.intent.action.SOUND_EVENT");
                                intent.putExtra("type", eventType);
                                intent.putExtra("value", value);
                                intent.putExtra("package", callingPackage);
                                intent.setComponent(receiver.mEventReceiver);
                                sendMsg(this.mAudioHandler, 126, 2, 0, 0, intent, 0);
                            }
                        }
                    }
                } catch (Throwable th) {
                    th = th;
                    throw th;
                }
            } catch (Throwable th2) {
                th = th2;
                throw th;
            }
        }
    }

    public void setSoundSettingEventBroadcastIntent(int eventType, PendingIntent broadcastIntent) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") != 0) {
            Log.w("AudioService", "MODIFY_PHONE_STATE Permission Denial from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        long ident = Binder.clearCallingIdentity();
        try {
            ComponentName newComponent = broadcastIntent.getIntent().getComponent();
            if (newComponent == null) {
                Log.e("AudioService", "Invalid argument");
                return;
            }
            synchronized (this.mEventReceivers) {
                ArrayList<SoundEventReceiver> receiverList = this.mEventReceivers;
                SoundEventReceiver newReceiver = new SoundEventReceiver(eventType, newComponent);
                int index = receiverList.indexOf(newReceiver);
                if (eventType == 0) {
                    if (index < 0) {
                        return;
                    }
                    if (newComponent.equals(this.mMediaSessionService.getVolumeLongPressReceiver())) {
                        this.mMediaSessionService.setVolumeLongPressReceiver(null);
                    }
                    if (newComponent.equals(this.mMediaSessionService.getMediaKeyEventReceiver())) {
                        this.mMediaSessionService.setMediaKeyEventReceiver(null);
                    }
                    receiverList.remove(index);
                    setPlayerChangedCallbackLocked();
                    Log.d("AudioService", "Receiver removed, size : " + receiverList.size());
                    return;
                }
                if (index < 0) {
                    receiverList.add(newReceiver);
                } else {
                    receiverList.get(index).mEventType = eventType;
                }
                setPlayerChangedCallbackLocked();
                if ((eventType & 32) != 0) {
                    Log.i("AudioService", "Set long press receiver");
                    this.mMediaSessionService.setVolumeLongPressReceiver(newComponent);
                }
                if ((eventType & 64) != 0) {
                    Log.i("AudioService", "Set media button receiver");
                    this.mMediaSessionService.setMediaKeyEventReceiver(newComponent);
                }
                Log.d("AudioService", "Success set receiver as type : " + eventType + ", size : " + receiverList.size());
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unSetSoundSettingEventBroadcastIntent(String packageName) {
        synchronized (this.mEventReceivers) {
            SoundEventReceiver removeReceiver = null;
            Iterator<SoundEventReceiver> it = this.mEventReceivers.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                SoundEventReceiver receiver = it.next();
                if (receiver.samePackageName(packageName)) {
                    removeReceiver = receiver;
                    break;
                }
            }
            if (removeReceiver != null) {
                ComponentName longPressReceiver = this.mMediaSessionService.getVolumeLongPressReceiver();
                ComponentName mediaKeyEventReceiver = this.mMediaSessionService.getMediaKeyEventReceiver();
                if (longPressReceiver != null && longPressReceiver.getPackageName().equals(packageName)) {
                    this.mMediaSessionService.setVolumeLongPressReceiver(null);
                }
                if (mediaKeyEventReceiver != null && mediaKeyEventReceiver.getPackageName().equals(packageName)) {
                    this.mMediaSessionService.setMediaKeyEventReceiver(null);
                }
                this.mEventReceivers.remove(removeReceiver);
                setPlayerChangedCallbackLocked();
            }
        }
    }

    private void setPlayerChangedCallbackLocked() {
        ArrayList<SoundEventReceiver> receiverList = this.mEventReceivers;
        Iterator<SoundEventReceiver> it = receiverList.iterator();
        while (it.hasNext()) {
            SoundEventReceiver receiver = it.next();
            if (receiver.hasEventType(16)) {
                this.mPlaybackMonitor.setPlayerChangeObserver(true);
                return;
            }
        }
        this.mPlaybackMonitor.setPlayerChangeObserver(false);
    }

    public boolean setMediaVolumeSteps(int[] volumeSteps) {
        long ident;
        if (this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") != 0) {
            Log.w("AudioService", "MODIFY_PHONE_STATE Permission Denial from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return false;
        } else if (volumeSteps == null) {
            this.mVolumeSteps = null;
            this.mVolumeMap = null;
            ident = Binder.clearCallingIdentity();
            try {
                Settings.System.putString(this.mContentResolver, "sec_volume_steps", null);
                return true;
            } finally {
            }
        } else if (volumeSteps.length > getStreamMaxVolume(3) * 10 || volumeSteps.length < 1) {
            Log.e("AudioService", "Invalid parameter");
            return false;
        } else {
            int before = -1;
            for (int cur : volumeSteps) {
                if (cur <= before) {
                    Log.e("AudioService", "Steps have to be ordered as increasing");
                    return false;
                }
                before = cur;
            }
            for (int cur2 : volumeSteps) {
                if (cur2 < 0 || cur2 > getStreamMaxVolume(3) * 10) {
                    Log.e("AudioService", "Invalid index");
                    return false;
                }
            }
            this.mVolumeSteps = volumeSteps;
            StringBuilder sb = new StringBuilder();
            for (int step : volumeSteps) {
                sb.append(step);
                sb.append(",");
            }
            ident = Binder.clearCallingIdentity();
            try {
                Settings.System.putString(this.mContentResolver, "sec_volume_steps", sb.toString());
                Binder.restoreCallingIdentity(ident);
                createVariableMediaVolumeMap(volumeSteps);
                return true;
            } finally {
            }
        }
    }

    public int[] getMediaVolumeSteps() {
        return this.mVolumeSteps;
    }

    public BypassReason bypassAdjustStreamVolume(int streamType, int direction, int flags, String callingPackage, String caller, int uid) {
        if (Rune.SEC_AUDIO_DISPLAY_VOLUME_CONTROL && (this.mIsDLNAEnabled || (this.mScreenSharingStateResumed && AudioSystem.isStreamActiveRemotely(3, 0) && (flags & 1) != 0))) {
            this.mDisplayManager.semSetWifiDisplayConfiguration("vkev", direction);
            this.mVolumeController.postVolumeChanged(streamType, 8388608 | flags);
            return BypassReason.DISPLAY_VOLUME_CONTROL;
        } else if (direction == 100 && ((streamType == 1 || streamType == 5) && getRingerModeExternal() != 2)) {
            Log.e("AudioService", "Couldn't unmute " + streamType + " in " + getRingerModeExternal());
            return BypassReason.CANNOT_UNMUTE_SILENT_VIBRATE;
        } else if (this.mAllSoundMute == 1 && (direction != 1 || this.mRingerMode != 0)) {
            this.mVolumeController.postVolumeChanged(streamType, flags);
            return BypassReason.ALL_SOUND_MUTE;
        } else {
            if (checkDeviceConnected(536870912) && this.mMode == 0) {
                if (direction == 100 && "0".equals(SystemProperties.get("net.mirrorlink.on"))) {
                    Log.d("AudioService", "not bypass adjustStreamVolume because mirrorlink is disconnected.");
                } else {
                    return BypassReason.TMS_CONNECT;
                }
            }
            int streamTypeAlias = mStreamVolumeAlias[streamType];
            if (streamType == 3 && isRaiseOrLowerOrSame(direction)) {
                int device = getDeviceForStream(streamTypeAlias);
                int newDevice = device;
                if (Rune.SEC_AUDIO_MULTI_SOUND) {
                    newDevice = selectDevice(device);
                }
                int newDevice2 = newDevice;
                if (device == newDevice2) {
                    if (this.mMediaVolumeStepIndex != 10 || this.mVolumeSteps != null) {
                        if (direction == 0) {
                            return BypassReason.CONSUME_ADJUST_SAME;
                        }
                        setStreamVolume(streamType, getNextFineMediaVolume(device, direction), flags | 2097152, callingPackage, callingPackage, uid);
                        return BypassReason.MEDIA_VOLUME_STEP_ON;
                    }
                } else if (this.mVolumeController.isVisible() && this.mVolumeControllerStream != 10003) {
                    return BypassReason.NO_BYPASS;
                } else {
                    String str = this.mMultiSoundManager.getPinPackageName(newDevice2)[0];
                    if (direction != 0) {
                        if (this.mMediaVolumeStepIndex == 10 && this.mVolumeSteps != null) {
                            setStreamVolume(streamType, getStreamVolume(3, newDevice2) + direction, flags, callingPackage, callingPackage, uid, newDevice2);
                        } else {
                            setStreamVolume(streamType, getNextFineMediaVolume(newDevice2, direction), flags | 2097152, callingPackage, caller, uid, newDevice2);
                        }
                        return BypassReason.MULTISOUND;
                    }
                    return BypassReason.CONSUME_ADJUST_SAME;
                }
            }
            if (this.mVolumeController.isSafeVolumeDialogShowing() && ((direction == 1 || direction == 0) && !isMedia(streamTypeAlias))) {
                Log.d("AudioService", "bypassAdjustStreamVolume: stream(" + streamType + ") when ear safety pop-up is shown");
                return BypassReason.SKIP_WARNING_POPUP_VISIBLE;
            }
            if (isNotificationOrRinger(streamType) && !this.mVolumeController.isVisible() && (flags & 1) != 0) {
                CustomDeviceManagerProxy customDeviceManager = CustomDeviceManagerProxy.getInstance();
                if (customDeviceManager != null && !customDeviceManager.getVolumePanelEnabledState()) {
                    Log.d("AudioService", "Allow volume increase action for stream: " + streamType + " when volume panel hidden by Knox Custom");
                } else {
                    int device2 = getDeviceForStream(streamTypeAlias);
                    int index = this.mStreamStates[streamType].getIndex(device2);
                    sendVolumeUpdate(streamType, index, index, flags);
                    return BypassReason.SKIP_VOLUME_PANEL_NOT_VISIBLE;
                }
            }
            if (this.mVolumeLimitOn) {
                int volumeLimitDevice = getDeviceForStream(3);
                int device3 = getDeviceForStream(streamTypeAlias);
                if (mStreamVolumeAlias[streamType] == 3 && direction == 1 && this.mStreamStates[streamType].getIndex(volumeLimitDevice) + this.mFineMediaVolume.getIndex(device3) + 10 > this.mVolumeLimitValue * 10 && (67109260 & volumeLimitDevice) != 0) {
                    if (this.mStreamStates[streamType].getIndex(volumeLimitDevice) != 150 && this.mVolumeLimitValue != 15) {
                        this.mVolumeController.displayVolumeLimiterToast();
                    }
                    Log.i("AudioService", "Volume index is already reached at volume limit value");
                    sendVolumeUpdate(streamType, this.mVolumeLimitValue * 10, this.mVolumeLimitValue * 10, flags);
                    return BypassReason.VOLUME_LIMITER;
                }
            }
            return BypassReason.NO_BYPASS;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public static class SoundEventReceiver {
        ComponentName mEventReceiver;
        int mEventType;

        public SoundEventReceiver(int eventType, ComponentName eventReceiver) {
            this.mEventType = eventType;
            this.mEventReceiver = eventReceiver;
        }

        public boolean equals(Object obj) {
            if (obj != null) {
                try {
                    SoundEventReceiver other = (SoundEventReceiver) obj;
                    if (other.mEventReceiver == null) {
                        return false;
                    }
                    return other.mEventReceiver.equals(this.mEventReceiver);
                } catch (ClassCastException e) {
                }
            }
            return false;
        }

        public boolean hasEventType(int eventType) {
            return (this.mEventType & eventType) == eventType;
        }

        public boolean samePackageName(String packageName) {
            if (this.mEventReceiver == null) {
                return false;
            }
            return this.mEventReceiver.getPackageName().equals(packageName);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public static final class VolumeMap {
        short lowerStep;
        short raiseStep;

        public VolumeMap(short lowerStep, short raiseStep) {
            this.raiseStep = raiseStep;
            this.lowerStep = lowerStep;
        }
    }

    private void registerGearVrStateListener() {
        GearVrManagerInternal vrService = (GearVrManagerInternal) LocalServices.getService(GearVrManagerInternal.class);
        if (vrService != null) {
            vrService.registerVrStateListener(new IGearVrStateCallbacks.Stub() { // from class: com.android.server.audio.AudioService.9
                public void onGearVrStateChanged(int state, int type) throws RemoteException {
                    Slog.v("AudioService", "onGearVrStateChanged state=" + state + " type=" + type);
                    if (state == 16) {
                        Log.i("AudioService", "HMT mount!");
                        AudioSystem.setParameters("hmtState=mount");
                    } else if (state == 32) {
                        Log.i("AudioService", "HMT unmount");
                        AudioSystem.setParameters("hmtState=unmount");
                    } else if (state == 16384) {
                        Log.i("AudioService", "HMT headset plugged");
                    } else if (state != 32768) {
                        switch (state) {
                            case 1:
                                if (type == 2) {
                                    Log.i("AudioService", "HMT Tethered Docked");
                                    AudioService.this.mHMTTetheredDocked = true;
                                    return;
                                }
                                Log.i("AudioService", "HMT Docked");
                                AudioSystem.setParameters("hmtState=dock");
                                AudioService.this.mHMTDocked = true;
                                return;
                            case 2:
                                Log.i("AudioService", "HMT OFF");
                                AudioSystem.setParameters("hmtState=off");
                                AudioService.this.mHMTDocked = false;
                                AudioService.this.mHMTTetheredDocked = false;
                                return;
                            default:
                                Log.i("AudioService", "ignore HMT state=" + state);
                                return;
                        }
                    } else {
                        Log.i("AudioService", "HMT headset unplugged");
                    }
                }
            });
        } else {
            Slog.e("AudioService", "HMT AudioService failed");
        }
    }

    private boolean isEncryptMode() {
        return "encrypted".equals(SystemProperties.get("ro.crypto.state")) && !"default".equals(SystemProperties.get("vold.crypt.type")) && "trigger_restart_min_framework".equals(SystemProperties.get("vold.decrypt"));
    }

    private IStorageManager getStorageManager() {
        return IStorageManager.Stub.asInterface(ServiceManager.getService(DirEncryptServiceHelper.VoldCommand.MOUNT));
    }

    private void setAudioPropertyOnFooter(String key) {
        try {
            IStorageManager mountService = getStorageManager();
            if (mountService == null) {
                return;
            }
            if ("persist.audio.allsoundmute".equals(key) || "persist.audio.ringermode".equals(key) || "persist.audio.sysvolume".equals(key)) {
                int footerAudioInfo = 0;
                boolean isSysVolumeMute = "0".equals(SystemProperties.get("persist.audio.sysvolume"));
                if (this.mAllSoundMute == 1) {
                    footerAudioInfo = 0 | 1;
                }
                if (this.mRingerMode != 2) {
                    footerAudioInfo |= 16;
                } else if (isSysVolumeMute) {
                    footerAudioInfo |= 256;
                }
                mountService.setPropertyIntoFooter("FooterAudioInfo", Integer.toString(footerAudioInfo));
            }
        } catch (Exception e) {
            Log.e("AudioService", "setAudioPropertyOnFooter", e);
        }
    }

    private void readAudioPropertyFromFooter() {
        if (!isEncryptMode()) {
            return;
        }
        Log.i("AudioService", "Need to use the property until device is decrypted");
        try {
            IStorageManager mountService = getStorageManager();
            if (mountService != null) {
                int encryptedAudioInfo = Integer.parseInt(mountService.getPropertyFromFooter("FooterAudioInfo"));
                if ((encryptedAudioInfo & 16) != 0) {
                    Log.i("AudioService", "ringer mode is silent state");
                    this.mRingerMode = 1;
                    this.mRingerModeExternal = 1;
                } else if ((encryptedAudioInfo & 256) != 0) {
                    Log.i("AudioService", "system volume is zero");
                    sendMsg(this.mAudioHandler, 114, 2, 0, 0, "persist.audio.sysvolume", 0);
                }
                if ((encryptedAudioInfo & 1) != 0) {
                    Log.i("AudioService", "all sound mute is enabled");
                    this.mAllSoundMute = 1;
                }
            }
        } catch (Exception e) {
            Log.e("AudioService", "readAudioPropertyFromFooter", e);
        }
    }

    public boolean isSafeMediaVolumeStateActive() {
        synchronized (this.mSafeMediaVolumeStateLock) {
            if (this.mSafeMediaVolumeBTState.intValue() == 3) {
                return true;
            }
            return false;
        }
    }

    private boolean isSafeMediaVolumeDevice(int device) {
        return (device & 384) != 0 ? this.mSafeMediaVolumeBTState.intValue() == 3 : (67109260 & device) != 0 && this.mSafeMediaVolumeState.intValue() == 3;
    }

    private void callLcdOn() {
        PowerManager.WakeLock wl = this.mPowerManager.newWakeLock(805306378, "AudioService");
        if (!wl.isHeld()) {
            wl.acquire(5000L);
        }
    }

    private void notifyVibrationForSafeMediaPopup() {
        if (!this.mPowerManager.isScreenOn()) {
            callLcdOn();
            callVibrateMsg();
        } else if (isNeonCoverClosed()) {
            callVibrateMsg();
        }
    }

    private void callVibrateMsg() {
        if (!this.mIsVibrate) {
            vibrateCall();
            this.mIsVibrate = true;
            sendMsg(this.mAudioHandler, 112, 1, 0, 0, null, 600);
        }
    }

    private void vibrateCall() {
        try {
            if (hasVibrator() && this.mVibrator != null) {
                VibrationEffect effect = VibrationEffect.semCreateHaptic(50032, -1, VibrationEffect.SemMagnitudeType.TYPE_CALL);
                this.mVibrator.vibrate(effect);
            }
        } catch (Exception e) {
            Log.i("AudioService", "vibrateCall error", e);
        }
    }

    private boolean hasVibrator() {
        return this.mHasVibrator;
    }

    public int secGetActiveStreamType(int suggestedStreamType) {
        return getActiveStreamType(suggestedStreamType);
    }

    private int getActiveStreams() {
        try {
            String result = AudioSystem.getPolicyParameters("activeStream");
            int activeStream = Integer.parseInt(result);
            return activeStream;
        } catch (NumberFormatException e) {
            Log.e("AudioService", "Can't get active stream", e);
            return 0;
        }
    }

    private int getActiveStreamTypeInternal(int platformType) {
        int FLAG_STREAM_VOICE_CALL = getActiveStreams();
        int FLAG_STREAM_SYSTEM = FLAG_STREAM_VOICE_CALL & 1024;
        if (FLAG_STREAM_SYSTEM != 0) {
            return 10;
        }
        if ((FLAG_STREAM_VOICE_CALL & 1) != 0) {
            return 0;
        }
        if ((FLAG_STREAM_VOICE_CALL & SystemService.PHASE_DEVICE_SPECIFIC_SERVICES_READY) == 0 && !AudioSystem.isStreamActiveRemotely(3, 0)) {
            if ((FLAG_STREAM_VOICE_CALL & 16) != 0) {
                return 4;
            }
            if ((FLAG_STREAM_VOICE_CALL & 32) != 0) {
                return 5;
            }
            if ((FLAG_STREAM_VOICE_CALL & 8192) != 0) {
                return 13;
            }
            if ((65536 & FLAG_STREAM_VOICE_CALL) != 0) {
                Log.v("AudioService", "getActiveStreamTypeInternal: forcing STREAM_BIXBY...");
                return 16;
            } else if (platformType == 1) {
                if (this.mAdjustMediaVolumeOnly && (FLAG_STREAM_VOICE_CALL & 4) == 0) {
                    Log.v("AudioService", "getActiveStreamTypeInternal: only adjust media volume");
                    return 3;
                }
                Log.v("AudioService", "getActiveStreamTypeInternal: forcing STREAM_RING b/c default");
                return 2;
            } else if (Rune.SEC_AUDIO_DEFAULT_STREAM_NOTIFICATION && !this.mAdjustMediaVolumeOnly) {
                Log.v("AudioService", "getActiveStreamTypeInternal: using STREAM_NOTIFICATION as default");
                return 5;
            } else {
                Log.v("AudioService", "getActiveStreamTypeInternal: using STREAM_MUSIC as default");
                return 3;
            }
        }
        Log.v("AudioService", "getActiveStreamTypeInternal: forcing STREAM_MUSIC");
        return 3;
    }

    private void setWiredDeviceConnectionStateBeforeBoot() {
        int device_id = -1;
        FileReader file_reader = null;
        try {
            try {
                file_reader = new FileReader("/sys/class/switch/h2w/state");
                if (file_reader.ready()) {
                    char[] buffer = new char[1024];
                    int len = file_reader.read(buffer, 0, 1024);
                    device_id = Integer.valueOf(new String(buffer, 0, len).trim()).intValue();
                }
                file_reader.close();
            } catch (IOException e) {
                if (file_reader != null) {
                    file_reader.close();
                }
            } catch (Throwable th) {
                if (file_reader != null) {
                    try {
                        file_reader.close();
                    } catch (IOException e2) {
                    }
                }
                throw th;
            }
        } catch (IOException e3) {
        }
        switch (device_id) {
            case 1:
                setWiredDeviceConnectionState(4, 1, "", "h2w-before-boot-completed", "AudioService");
                return;
            case 2:
                setWiredDeviceConnectionState(8, 1, "", "h2w-before-boot-completed", "AudioService");
                return;
            default:
                return;
        }
    }

    private void mediaMuteForZenMode() {
        int zenBefore = this.mZenMode;
        this.mZenMode = Settings.Global.getInt(this.mContentResolver, "zen_mode", 0);
        if (zenBefore == this.mZenMode || this.mStreamStates == null) {
            return;
        }
        if (this.mZenMode == 2) {
            this.mStreamStates[3].mute(true);
            this.mRingerAndZenModeMutedStreams |= 8;
        } else if (zenBefore == 2) {
            this.mStreamStates[3].mute(false);
            this.mRingerAndZenModeMutedStreams &= -9;
        }
    }

    private void dumpScoClients(PrintWriter pw) {
        pw.println("\nSco Client list entries: ");
        synchronized (this.mScoClients) {
            int size = this.mScoClients.size();
            for (int i = 0; i < size; i++) {
                ScoClient cl = this.mScoClients.get(i);
                pw.println("\n  pid: " + cl.getPid());
            }
        }
    }

    void addSamsungExtraDump(PrintWriter pw) {
        pw.println("\n");
        this.mMicrophoneLogger.dump(pw);
        if (Rune.SEC_AUDIO_RECORDING_FAILED_POPUP) {
            pw.println("\n");
            this.mRecFailPopupLogger.dump(pw);
        }
        pw.println("\nAudio Setting:");
        pw.print("  mNbQualityMode=");
        pw.println(this.mNbQualityMode);
        pw.print("  mAllSoundMute=");
        pw.println(this.mAllSoundMute);
        pw.print("  mSoundBalance=");
        pw.println(this.mSoundBalance);
        pw.print("  mUhqBtSampleRate=");
        pw.println(this.mUhqBtSampleRate);
        pw.print("  mIsBtOffloadEnabled=");
        pw.println(this.mIsBtOffloadEnabled);
        pw.print("  mHMTDocked=");
        pw.println(this.mHMTDocked);
        pw.print("  mHMTTetheredDocked=");
        pw.println(this.mHMTTetheredDocked);
        if (Rune.SEC_AUDIO_DISPLAY_VOLUME_CONTROL) {
            pw.print("  mIsSupportDisplayVolumeControl=");
            pw.println(this.mIsSupportDisplayVolumeControl);
            pw.print("  mIsDLNAEnabled=");
            pw.println(this.mIsDLNAEnabled);
        }
        pw.print("  isTalkBackEnabled=");
        pw.println(this.mIsTalkBackEnabled);
        pw.print("  isFactoryMode=");
        pw.println(this.mIsFactoryMode);
        if (Rune.SEC_AUDIO_PERFORMANCE_MODE) {
            pw.print("  mPerformanceMode=");
            pw.println(this.mPerformanceMode);
            pw.print("  mPerformanceUpScalerMode=");
            pw.println(this.mPerformanceUpScalerMode);
        }
        pw.print("  mDexState=");
        pw.println(this.mDexState);
        pw.print("  mDexConnectedState=");
        pw.println(this.mDexConnectedState);
        pw.print("  mDexPadConnectedState=");
        pw.println(this.mDexPadConnectedState);
        if (Rune.SEC_AUDIO_SCREEN_OFF_MUSIC) {
            pw.print("  mIsScreenOffMusicOn=");
            pw.println(this.mIsScreenOffMusicOn);
        }
        pw.print("  mMySoundEnabled=");
        pw.println(this.mMySoundEnabled);
        pw.print("  mUpscalerEnabled=");
        pw.println(this.mUpscalerEnabled);
        pw.print("  mAdjustMediaVolumeOnly=");
        pw.println(this.mAdjustMediaVolumeOnly);
        pw.print("  mSelfiestickIsConnected=");
        pw.println(this.mSelfiestickIsConnected);
        pw.print("  mMediaVolumeStepIndex=");
        pw.println(this.mMediaVolumeStepIndex);
        pw.print("  mMediaVolumeSteps=");
        pw.println(Arrays.toString(this.mVolumeSteps));
        pw.print("  mIgnoreAudioFocusUid=");
        pw.println(this.mIgnoreAudioFocusUid);
        pw.print("  mHeadsetOnlyStream=");
        pw.println(this.mHeadsetOnlyStream);
        pw.print("  mEnableSoundAssistant=");
        pw.println(this.mEnableSoundAssistant);
        pw.print("  mLRSwitching=");
        pw.println(this.mLRSwitching);
        if (Rune.SEC_AUDIO_PREVENT_OVERHEAT_BY_MAX_VOLUME) {
            pw.print("  mPreventOverheatState=");
            pw.println(this.mPreventOverheatState);
        }
        pw.print("  mCPUBoostValueForVoIP=");
        pw.println(this.mCPUBoostValueForVoIP);
        pw.print("  mSafeMediaVolumeBTState=");
        pw.println(safeMediaVolumeStateToString(this.mSafeMediaVolumeBTState));
        pw.print("  mGameStateChecker=");
        pw.println(this.mGameStateChecker);
        if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER) {
            pw.print("  mDualSpkEnable=");
            pw.println(this.mDualSpkEnable);
        }
        pw.print("  mVolumeLimitOn=");
        pw.println(this.mVolumeLimitOn);
        pw.print("  mVolumeLimitValue=");
        pw.println(this.mVolumeLimitValue);
        dumpScoClients(pw);
        if (Rune.SEC_AUDIO_MULTI_SOUND) {
            this.mMultiSoundManager.dump(pw);
        }
    }

    private boolean isInCallMode() {
        boolean ret = false;
        if (this.mMode == 2) {
            return true;
        }
        synchronized (this.mSetModeDeathHandlers) {
            Iterator iter = this.mSetModeDeathHandlers.iterator();
            while (true) {
                if (!iter.hasNext()) {
                    break;
                }
                SetModeDeathHandler h = iter.next();
                int mode = h.getMode();
                Log.w("AudioService", "SetModeDeathHandler mode: " + mode);
                if (mode == 2) {
                    ret = true;
                    break;
                }
            }
        }
        return ret;
    }

    private int getProperIndexForFineVolume(int streamType, int device, int origIndex) {
        if (origIndex != 0 || mStreamVolumeAlias[streamType] != 3 || this.mFineMediaVolume.getIndex(device) == 0 || this.mStreamStates[streamType].mIsMuted) {
            return origIndex;
        }
        return 10;
    }

    private int updateFlagsForSamsungVolume(int streamType, int flags, int index) {
        int streamType2 = mStreamVolumeAlias[streamType];
        if (this.mIsFactoryMode && 2 == streamType2 && flags == 0 && index > 0) {
            Log.e("AudioService", "sendVolumeUpdate: enforce to FLAG_PLAY_SOUND volume index = " + index);
            flags |= 4;
        }
        if (streamType2 == 3 && this.mMode == 0 && this.mAllSoundMute == 0) {
            if (this.mGameStateChecker != null && this.mGameStateChecker.isGameForeground()) {
                int flags2 = flags | 1048576;
                Log.i("AudioService", "use minimized volume panel for game");
                return flags2;
            } else if (this.mSemDisplaySolutionManager != null && this.mSemDisplaySolutionManager.getVideoModeEnable()) {
                int flags3 = flags | 1048576;
                Log.i("AudioService", "use minimized volume panel for video");
                return flags3;
            } else {
                return flags;
            }
        }
        return flags;
    }

    private boolean isWiredHeadset(int device) {
        if (device == 4 || device == 8 || device == 67108864) {
            return true;
        }
        return false;
    }

    private void initializeVolumeSteps() {
        int callVolStepsFromProperty = SystemProperties.getInt("ro.config.vc_call_vol_steps", -1);
        int finalCallVolStep = MAX_STREAM_VOLUME[0];
        if (callVolStepsFromProperty != -1) {
            finalCallVolStep = callVolStepsFromProperty;
            Log.i("AudioService", "use default ro.config.vc_call_vol_steps " + callVolStepsFromProperty);
        }
        MAX_STREAM_VOLUME[0] = finalCallVolStep;
        MAX_STREAM_VOLUME[14] = finalCallVolStep;
        MAX_STREAM_VOLUME[15] = finalCallVolStep;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void registerCustomObserver(SettingsObserver observer) {
        this.mContentResolver.registerContentObserver(Settings.Global.getUriFor("zen_mode"), false, observer);
        if (Rune.SEC_AUDIO_PERFORMANCE_MODE) {
            this.mContentResolver.registerContentObserver(Settings.Secure.getUriFor("sem_perfomance_mode"), false, observer);
            this.mContentResolver.registerContentObserver(Settings.Global.getUriFor("pbm_uhq_upscaler"), false, observer);
        }
        this.mContentResolver.registerContentObserver(Settings.System.getUriFor("volumelimit_on"), false, observer);
        this.mContentResolver.registerContentObserver(Settings.System.getUriFor("volume_limiter_value"), false, observer);
        this.mContentResolver.registerContentObserver(Settings.System.getUriFor("hearing_musiccheck"), false, observer);
        this.mContentResolver.registerContentObserver(Settings.System.getUriFor("k2hd_effect"), false, observer);
        this.mContentResolver.registerContentObserver(Settings.System.getUriFor("k2hd_effect_from_user"), false, observer);
        this.mContentResolver.registerContentObserver(Settings.Global.getUriFor("theme_touch_sound"), false, observer);
        this.mContentResolver.registerContentObserver(Settings.Secure.getUriFor("cpu_frequency_for_voip"), false, observer);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateCustomSettings(ContentResolver cr) {
        mediaMuteForZenMode();
        if (Rune.SEC_AUDIO_PERFORMANCE_MODE) {
            int performanceMode = Settings.Secure.getIntForUser(cr, "sem_perfomance_mode", 0, -2);
            int upscalerMode = Settings.Global.getInt(cr, "pbm_uhq_upscaler", 0);
            if (this.mPerformanceMode != performanceMode) {
                this.mPerformanceMode = performanceMode;
                if (this.mPerformanceMode == 2) {
                    this.mPerformanceUpScalerMode = upscalerMode;
                    if (this.mPerformanceUpScalerMode != this.mUpscalerEnabled) {
                        setGlobalUpscaler(this.mPerformanceUpScalerMode);
                    }
                } else {
                    setGlobalUpscaler(this.mUpscalerEnabled);
                }
            } else if (this.mPerformanceUpScalerMode != upscalerMode && this.mPerformanceMode == 2) {
                this.mPerformanceUpScalerMode = upscalerMode;
                setGlobalUpscaler(this.mPerformanceUpScalerMode);
            }
        }
        this.mVolumeLimitOn = Settings.System.getInt(cr, "volumelimit_on", 0) != 0;
        this.mVolumeLimitValue = Settings.System.getInt(cr, "volume_limiter_value", this.mVolumeLimitValue);
        int isMySoundEnabled = Settings.System.getIntForUser(this.mContentResolver, "hearing_musiccheck", 0, -2);
        int isUpscalerEnabled = Settings.System.getIntForUser(this.mContentResolver, "k2hd_effect", 0, -2);
        if (this.mMySoundEnabled != isMySoundEnabled) {
            this.mMySoundEnabled = isMySoundEnabled;
            setGlobalMySound(this.mMySoundEnabled);
        } else if (this.mUpscalerEnabled != isUpscalerEnabled) {
            int fromUser = Settings.System.getIntForUser(this.mContentResolver, "k2hd_effect_from_user", 1, -2);
            if (fromUser == 1) {
                this.mUpscalerEnabled = isUpscalerEnabled;
                if (this.mPerformanceMode != 2) {
                    setGlobalUpscaler(this.mUpscalerEnabled);
                }
            }
        }
        checkAndSetThemeSound(false);
        this.mCPUBoostValueForVoIP = Settings.Secure.getInt(cr, "cpu_frequency_for_voip", this.mCPUBoostValueForVoIP);
    }

    private void readPersistedCustomSettings() {
        ContentResolver cr = this.mContentResolver;
        this.mThemeTouchSoundPath = Settings.Global.getString(cr, "theme_touch_sound");
        if (Rune.SEC_AUDIO_PSEUDO_DUAL_SPEAKER && Rune.SEC_AUDIO_DEFAULT_STREAM_NOTIFICATION) {
            this.mDualSpkEnable = Settings.System.getIntForUser(cr, "pseudo_stereo_sound_settings", 1, -2);
        }
        this.mNbQualityMode = Settings.Global.getInt(cr, "personalise_call_sound_soft", 0);
        if (Rune.SEC_AUDIO_PERFORMANCE_MODE) {
            this.mPerformanceMode = Settings.Secure.getIntForUser(cr, "sem_perfomance_mode", 0, -2);
            if (this.mPerformanceMode == 2) {
                this.mPerformanceUpScalerMode = Settings.Global.getInt(cr, "pbm_uhq_upscaler", 0);
            }
        }
        this.mMySoundEnabled = Settings.System.getIntForUser(cr, "hearing_musiccheck", 0, -2);
        this.mUpscalerEnabled = Settings.System.getIntForUser(cr, "k2hd_effect", 0, -2);
        readAudioPropertyFromFooter();
        if (!isEncryptMode()) {
            this.mAllSoundMute = Settings.System.getIntForUser(cr, "all_sound_off", 0, -2);
        }
        if (this.mAllSoundMute == 1) {
            setAllSoundMuteToNative();
        }
        Settings.System.putInt(cr, "support_split_sound", 1);
        this.mMonoMode = Settings.System.getIntForUser(cr, "mono_audio_db", 0, -2);
        this.mAdjustMediaVolumeOnly = Settings.System.getIntForUser(cr, "adjust_media_volume_only", getDefaultVolumeOption(), -2) > 0;
        this.mHeadsetOnlyStream = this.mSettingHelper.getIntValue("ring_through_headset", 0);
        if (this.mHeadsetOnlyStream != 0) {
            AudioSystem.setPolicyParameters("ring_through_headset =" + this.mHeadsetOnlyStream);
        }
        this.mLRSwitching = this.mSettingHelper.getIntValue("sound_lr_switch", 0);
        if (this.mLRSwitching == 1) {
            AudioSystem.setParameters("sound_lr_switch=1");
        }
        reloadVariableMediaVolumeSteps();
        this.mEnableSoundAssistant = this.mSettingHelper.getBooleanValue("enable_floating_button");
        this.mMediaVolumeStepIndex = this.mSettingHelper.getIntValue("media_volume_step_index", 10);
        this.mIgnoreAudioFocusUid = this.mSettingHelper.getIntValue("ignore_audio_focus", -1);
        this.mVolumeLimitOn = Settings.System.getInt(cr, "volumelimit_on", 0) != 0;
        this.mVolumeLimitValue = Settings.System.getInt(cr, "volume_limiter_value", this.mVolumeLimitValue);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public static class MultiVolumeController extends VolumeController {
        private ArrayList<IVolumeController> mVolumeControllerList;

        private MultiVolumeController() {
            this.mVolumeControllerList = new ArrayList<>(2);
        }

        @Override // com.android.server.audio.AudioService.VolumeController
        public synchronized void setController(IVolumeController controller) {
            super.setController(controller);
            if (controller != null) {
                this.mVolumeControllerList.add(controller);
            }
        }

        @Override // com.android.server.audio.AudioService.VolumeController
        public synchronized boolean isSameBinder(IVolumeController controller) {
            Iterator<IVolumeController> it = this.mVolumeControllerList.iterator();
            while (it.hasNext()) {
                IVolumeController curController = it.next();
                if (Objects.equals(binder(curController), binder(controller))) {
                    return true;
                }
            }
            return false;
        }

        @Override // com.android.server.audio.AudioService.VolumeController
        public String toString() {
            StringBuilder sb = new StringBuilder();
            Iterator<IVolumeController> it = this.mVolumeControllerList.iterator();
            while (it.hasNext()) {
                IVolumeController controller = it.next();
                this.mController = controller;
                sb.append(super.toString());
            }
            return sb.toString();
        }

        @Override // com.android.server.audio.AudioService.VolumeController
        public synchronized void postDisplaySafeVolumeWarning(int flags) {
            Iterator<IVolumeController> it = this.mVolumeControllerList.iterator();
            while (it.hasNext()) {
                IVolumeController controller = it.next();
                this.mController = controller;
                super.postDisplaySafeVolumeWarning(flags);
            }
        }

        @Override // com.android.server.audio.AudioService.VolumeController
        public synchronized void postVolumeChanged(int streamType, int flags) {
            Iterator<IVolumeController> it = this.mVolumeControllerList.iterator();
            while (it.hasNext()) {
                IVolumeController controller = it.next();
                this.mController = controller;
                super.postVolumeChanged(streamType, flags);
            }
        }

        @Override // com.android.server.audio.AudioService.VolumeController
        public synchronized void postMasterMuteChanged(int flags) {
            Iterator<IVolumeController> it = this.mVolumeControllerList.iterator();
            while (it.hasNext()) {
                IVolumeController controller = it.next();
                this.mController = controller;
                super.postMasterMuteChanged(flags);
            }
        }

        @Override // com.android.server.audio.AudioService.VolumeController
        public synchronized void setLayoutDirection(int layoutDirection) {
            Iterator<IVolumeController> it = this.mVolumeControllerList.iterator();
            while (it.hasNext()) {
                IVolumeController controller = it.next();
                this.mController = controller;
                super.setLayoutDirection(layoutDirection);
            }
        }

        @Override // com.android.server.audio.AudioService.VolumeController
        public synchronized void postDismiss() {
            Iterator<IVolumeController> it = this.mVolumeControllerList.iterator();
            while (it.hasNext()) {
                IVolumeController controller = it.next();
                this.mController = controller;
                super.postDismiss();
            }
        }

        @Override // com.android.server.audio.AudioService.VolumeController
        public synchronized void setA11yMode(int a11yMode) {
            Iterator<IVolumeController> it = this.mVolumeControllerList.iterator();
            while (it.hasNext()) {
                IVolumeController controller = it.next();
                this.mController = controller;
                super.setA11yMode(a11yMode);
            }
        }

        @Override // com.android.server.audio.AudioService.VolumeController
        public synchronized void displayVolumeLimiterToast() {
            Iterator<IVolumeController> it = this.mVolumeControllerList.iterator();
            while (it.hasNext()) {
                IVolumeController controller = it.next();
                this.mController = controller;
                super.displayVolumeLimiterToast();
            }
        }

        @Override // com.android.server.audio.AudioService.VolumeController
        public synchronized void removeController(IVolumeController controller) {
            Iterator iter = this.mVolumeControllerList.iterator();
            while (true) {
                if (!iter.hasNext()) {
                    break;
                }
                IVolumeController curController = iter.next();
                if (Objects.equals(binder(curController), binder(controller))) {
                    iter.remove();
                    break;
                }
            }
            if (this.mVolumeControllerList.isEmpty()) {
                super.setController(null);
            } else {
                super.setController(this.mVolumeControllerList.get(0));
            }
        }
    }

    /* JADX WARN: Code restructure failed: missing block: B:11:0x0034, code lost:
        if (r0 != null) goto L39;
     */
    /* JADX WARN: Code restructure failed: missing block: B:17:0x003e, code lost:
        if (r0 == null) goto L13;
     */
    /* JADX WARN: Code restructure failed: missing block: B:18:0x0040, code lost:
        r0.close();
     */
    /* JADX WARN: Code restructure failed: missing block: B:19:0x0044, code lost:
        android.util.Slog.i("AudioService", "SoundAppPolicy new version : " + r2);
     */
    /* JADX WARN: Code restructure failed: missing block: B:20:0x005a, code lost:
        if (r2 != null) goto L18;
     */
    /* JADX WARN: Code restructure failed: missing block: B:21:0x005c, code lost:
        android.util.Log.e("AudioService", "SoundAppPolicy version value is null");
     */
    /* JADX WARN: Code restructure failed: missing block: B:22:0x0063, code lost:
        return null;
     */
    /* JADX WARN: Code restructure failed: missing block: B:23:0x0064, code lost:
        r3 = java.lang.String.valueOf(r10.mSettingHelper.getIntValue("APP_LIST_VERSION", 0));
        android.util.Slog.i("AudioService", "SoundAppPolicy current version" + r3);
     */
    /* JADX WARN: Code restructure failed: missing block: B:24:0x0087, code lost:
        if (r3 == null) goto L38;
     */
    /* JADX WARN: Code restructure failed: missing block: B:26:0x008f, code lost:
        if ("".equals(r3) == false) goto L22;
     */
    /* JADX WARN: Code restructure failed: missing block: B:29:0x0094, code lost:
        r6 = java.lang.Long.valueOf(java.lang.Long.parseLong(r2));
        r6 = java.lang.Long.valueOf(java.lang.Long.parseLong(r3));
     */
    /* JADX WARN: Code restructure failed: missing block: B:31:0x00a7, code lost:
        if (r6 != null) goto L28;
     */
    /* JADX WARN: Code restructure failed: missing block: B:32:0x00a9, code lost:
        return null;
     */
    /* JADX WARN: Code restructure failed: missing block: B:33:0x00aa, code lost:
        if (r6 != null) goto L30;
     */
    /* JADX WARN: Code restructure failed: missing block: B:34:0x00ac, code lost:
        return r2;
     */
    /* JADX WARN: Code restructure failed: missing block: B:36:0x00b7, code lost:
        if (r6.longValue() <= r6.longValue()) goto L33;
     */
    /* JADX WARN: Code restructure failed: missing block: B:37:0x00b9, code lost:
        return r2;
     */
    /* JADX WARN: Code restructure failed: missing block: B:38:0x00ba, code lost:
        return null;
     */
    /* JADX WARN: Code restructure failed: missing block: B:39:0x00bb, code lost:
        r6 = move-exception;
     */
    /* JADX WARN: Code restructure failed: missing block: B:40:0x00bc, code lost:
        android.util.Log.e("AudioService", "SoundAppPolicy version parsing error", r6);
     */
    /* JADX WARN: Code restructure failed: missing block: B:41:0x00c3, code lost:
        return null;
     */
    /* JADX WARN: Code restructure failed: missing block: B:42:0x00c4, code lost:
        return r2;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    private java.lang.String getNewWhiteListVersion() {
        /*
            r10 = this;
            r0 = 0
            r1 = 0
            r2 = r1
            android.content.Context r3 = r10.mContext     // Catch: java.lang.Throwable -> L37 java.lang.Exception -> L3a
            android.content.ContentResolver r4 = r3.getContentResolver()     // Catch: java.lang.Throwable -> L37 java.lang.Exception -> L3a
            android.net.Uri r5 = com.android.server.audio.AudioService.SoundAppPolicy.access$19700()     // Catch: java.lang.Throwable -> L37 java.lang.Exception -> L3a
            java.lang.String[] r6 = com.android.server.audio.AudioService.SoundAppPolicy.access$19800()     // Catch: java.lang.Throwable -> L37 java.lang.Exception -> L3a
            java.lang.String r7 = "policyName=?"
            java.lang.String r3 = "audio"
            java.lang.String[] r8 = new java.lang.String[]{r3}     // Catch: java.lang.Throwable -> L37 java.lang.Exception -> L3a
            r9 = 0
            android.database.Cursor r3 = r4.query(r5, r6, r7, r8, r9)     // Catch: java.lang.Throwable -> L37 java.lang.Exception -> L3a
            r0 = r3
            if (r0 == 0) goto L34
            int r3 = r0.getCount()     // Catch: java.lang.Throwable -> L37 java.lang.Exception -> L3a
            if (r3 <= 0) goto L34
            boolean r3 = r0.moveToNext()     // Catch: java.lang.Throwable -> L37 java.lang.Exception -> L3a
            if (r3 == 0) goto L34
            r3 = 1
            java.lang.String r3 = r0.getString(r3)     // Catch: java.lang.Throwable -> L37 java.lang.Exception -> L3a
            r2 = r3
        L34:
            if (r0 == 0) goto L44
            goto L40
        L37:
            r1 = move-exception
            goto Lc5
        L3a:
            r3 = move-exception
            r3.printStackTrace()     // Catch: java.lang.Throwable -> L37
            if (r0 == 0) goto L44
        L40:
            r0.close()
            r0 = 0
        L44:
            java.lang.String r3 = "AudioService"
            java.lang.StringBuilder r4 = new java.lang.StringBuilder
            r4.<init>()
            java.lang.String r5 = "SoundAppPolicy new version : "
            r4.append(r5)
            r4.append(r2)
            java.lang.String r4 = r4.toString()
            android.util.Slog.i(r3, r4)
            if (r2 != 0) goto L64
            java.lang.String r3 = "AudioService"
            java.lang.String r4 = "SoundAppPolicy version value is null"
            android.util.Log.e(r3, r4)
            return r1
        L64:
            com.samsung.android.audio.AudioSettingsHelper r3 = r10.mSettingHelper
            java.lang.String r4 = "APP_LIST_VERSION"
            r5 = 0
            int r3 = r3.getIntValue(r4, r5)
            java.lang.String r3 = java.lang.String.valueOf(r3)
            java.lang.String r4 = "AudioService"
            java.lang.StringBuilder r5 = new java.lang.StringBuilder
            r5.<init>()
            java.lang.String r6 = "SoundAppPolicy current version"
            r5.append(r6)
            r5.append(r3)
            java.lang.String r5 = r5.toString()
            android.util.Slog.i(r4, r5)
            if (r3 == 0) goto Lc4
            java.lang.String r4 = ""
            boolean r4 = r4.equals(r3)
            if (r4 == 0) goto L92
            goto Lc4
        L92:
            r4 = 0
            r5 = r1
            long r6 = java.lang.Long.parseLong(r2)     // Catch: java.lang.Exception -> Lbb
            java.lang.Long r6 = java.lang.Long.valueOf(r6)     // Catch: java.lang.Exception -> Lbb
            r4 = r6
            long r6 = java.lang.Long.parseLong(r3)     // Catch: java.lang.Exception -> Lbb
            java.lang.Long r6 = java.lang.Long.valueOf(r6)     // Catch: java.lang.Exception -> Lbb
            r5 = r6
            if (r4 != 0) goto Laa
            return r1
        Laa:
            if (r5 != 0) goto Lad
            return r2
        Lad:
            long r6 = r4.longValue()
            long r8 = r5.longValue()
            int r6 = (r6 > r8 ? 1 : (r6 == r8 ? 0 : -1))
            if (r6 <= 0) goto Lba
            return r2
        Lba:
            return r1
        Lbb:
            r6 = move-exception
            java.lang.String r7 = "AudioService"
            java.lang.String r8 = "SoundAppPolicy version parsing error"
            android.util.Log.e(r7, r8, r6)
            return r1
        Lc4:
            return r2
        Lc5:
            if (r0 == 0) goto Lcb
            r0.close()
            r0 = 0
        Lcb:
            throw r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.audio.AudioService.getNewWhiteListVersion():java.lang.String");
    }

    private void setDefaultWhiteList() {
        Log.i("AudioService", "SoundAppPolicy setDefaultWhiteList()");
        this.mSettingHelper.putAppList("com.audiocn.kalaok", SoundAppPolicy.KARAOKE_WHITE);
        this.mSettingHelper.putAppList("com.tencent.karaoke", SoundAppPolicy.KARAOKE_WHITE);
        this.mSettingHelper.putAppList("com.changba", SoundAppPolicy.KARAOKE_WHITE);
        this.mSettingHelper.putAppList("com.app.hero.ui", SoundAppPolicy.KARAOKE_WHITE);
        this.mSettingHelper.putAppList("cn.banshenggua.aichang", SoundAppPolicy.KARAOKE_WHITE);
        this.mSettingHelper.putAppList("com.kibey.echo", SoundAppPolicy.KARAOKE_WHITE);
        this.mSettingHelper.putAppList("cn.kuwo.sing", SoundAppPolicy.KARAOKE_WHITE);
        this.mSettingHelper.putAppList("com.michong.haochang", SoundAppPolicy.KARAOKE_WHITE);
        this.mSettingHelper.putAppList("com.huuhoo.mystyle", SoundAppPolicy.KARAOKE_WHITE);
        this.mSettingHelper.putAppList("com.sm1.EverySing", SoundAppPolicy.KARAOKE_WHITE);
        this.mSettingHelper.putAppList("com.smule.singandroid", SoundAppPolicy.KARAOKE_WHITE);
        this.mSettingHelper.putAppList("com.google.android.youtube", SoundAppPolicy.MEDIA_BUTTON_BLACK);
        this.mSettingHelper.putAppList("com.android.chrome", SoundAppPolicy.MEDIA_BUTTON_BLACK);
        this.mSettingHelper.putAppList("com.google.android.apps.youtube.kids", SoundAppPolicy.MEDIA_BUTTON_BLACK);
        this.mSettingHelper.putAppList("com.mxtech.videoplayer.ad", SoundAppPolicy.MEDIA_BUTTON_BLACK);
        this.mSettingHelper.setIntValue("APP_LIST_VERSION", 1986010100);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reloadWhitelist() {
        boolean isSCPMClientExist = this.mContext.getPackageManager().resolveContentProvider("com.samsung.android.sm.policy", 0) != null;
        if (!isSCPMClientExist) {
            Log.e("AudioService", "SoundAppPolicy SCPMClient is not exist");
            return;
        }
        String newVersion = getNewWhiteListVersion();
        if (newVersion == null) {
            Log.i("AudioService", "SoundAppPolicy server version is not latest or incorrect");
            return;
        }
        Cursor itemCursor = null;
        ContentResolver resolver = this.mContext.getContentResolver();
        try {
            try {
                itemCursor = resolver.query(SoundAppPolicy.PACKAGE_URI, SoundAppPolicy.PACKAGE_PROJECTION, null, null, null);
                if (itemCursor != null && itemCursor.getCount() > 0) {
                    this.mSettingHelper.setIntValue("APP_LIST_VERSION", Integer.valueOf(newVersion).intValue());
                    this.mSettingHelper.resetWhiteListTable();
                    while (itemCursor.moveToNext()) {
                        String packageName = itemCursor.getString(0);
                        String category = itemCursor.getString(1);
                        this.mSettingHelper.putAppList(packageName, category);
                        Log.i("AudioService", "SoundAppPolicy reloadWhitelist() add packageName = " + packageName + " category = " + category);
                    }
                }
                if (itemCursor == null) {
                    return;
                }
            } catch (Exception e) {
                this.mSettingHelper.resetWhiteListTable();
                setDefaultWhiteList();
                e.printStackTrace();
                if (itemCursor == null) {
                    return;
                }
            }
            itemCursor.close();
        } catch (Throwable th) {
            if (itemCursor != null) {
                itemCursor.close();
            }
            throw th;
        }
    }

    /* loaded from: /home/smabbasz/Desktop/CS 858 Project/wala-workshop/wala-workshop-skeleton/Input/framework/oat/arm64/services_classes.dex */
    public static class SoundAppPolicy {
        private static final String AUTHORITY = "com.samsung.android.sm.policy";
        private static final int CATEGORY_IDX = 1;
        public static final String KARAOKE_WHITE = "karaoke_white";
        public static final String MEDIA_BUTTON_BLACK = "media_button_black";
        private static final int PACKAGE_IDX = 0;
        private static final int POLICY_VERSION_IDX = 1;
        private static final Uri AUTHORITY_URI = Uri.parse("content://com.samsung.android.sm.policy");
        private static final String VERSION_TABLE = "policy_list";
        private static final Uri VERION_URI = Uri.withAppendedPath(AUTHORITY_URI, VERSION_TABLE);
        private static final String[] VERSION_PROJECTION = {EdmStorageDefs.ADMIN_REF_POLICY, "policyVersion"};
        private static final String PACKAGE_TABLE = "policy_item/audio";
        private static final Uri PACKAGE_URI = Uri.withAppendedPath(AUTHORITY_URI, PACKAGE_TABLE);
        private static final String[] PACKAGE_PROJECTION = {com.android.server.pm.Settings.TAG_ITEM, "category"};
    }

    public boolean isUsingAudio(int uid) {
        return this.mPlaybackMonitor.isUsingAudio(uid) || this.mRecordMonitor.isUsingAudio(uid);
    }

    private boolean isInCommunicationAndRing() {
        TelecomManager telecomManager = (TelecomManager) this.mContext.getSystemService("telecom");
        long ident = Binder.clearCallingIdentity();
        boolean IsInCall = telecomManager.isInCall();
        Binder.restoreCallingIdentity(ident);
        return IsInCall || getMode() == 3 || getMode() == 2 || getMode() == 1;
    }

    private void onInitSoundAssistant() {
        int uid;
        if (this.mPackageManager == null || (uid = getUidForPackage("com.samsung.android.soundassistant")) < 10000) {
            return;
        }
        registBackgroundWhitelistForSoundAssistant(uid);
        startSoundAssistantService(1003);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void registBackgroundWhitelistForSoundAssistant(int uid) {
        if (this.mPackageManager.checkPermission("android.permission.MODIFY_PHONE_STATE", "com.samsung.android.soundassistant") != 0) {
            return;
        }
        try {
            ActivityManager.getService().backgroundWhitelistUid(uid);
        } catch (Exception e) {
            Log.e("AudioService", "Failed regist background white", e);
        }
    }

    private void startSoundAssistantService(int type) {
        Intent serviceIntent = new Intent("com.sec.android.soundassistant.SOUNDASSIST_INTENT_SERVICE");
        serviceIntent.setClassName("com.samsung.android.soundassistant", "com.sec.android.soundassistant.services.SoundAssistIntentService");
        serviceIntent.putExtra("type", type);
        try {
            this.mContext.startForegroundServiceAsUser(serviceIntent, UserHandle.CURRENT);
        } catch (Exception e) {
        }
    }
}