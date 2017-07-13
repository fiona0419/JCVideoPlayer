package fm.jiecao.jcvideoplayer_lib;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * Created by Nathen on 16/7/30.
 */
public abstract class JCVideoPlayer extends FrameLayout implements JCMediaPlayerListener, View.OnClickListener, SeekBar.OnSeekBarChangeListener, View.OnTouchListener, TextureView.SurfaceTextureListener {

    public static final String TAG = "JieCaoVideoPlayer";

    public static final int THRESHOLD = 80;
    public static final int FULL_SCREEN_NORMAL_DELAY = 500;

    public static boolean ACTION_BAR_EXIST = true;
    public static boolean TOOL_BAR_EXIST = true;
    public static boolean WIFI_TIP_DIALOG_SHOWED = false;
    public static long CLICK_QUIT_FULLSCREEN_TIME = 0;

    public static final int SCREEN_LAYOUT_LIST = 0;
    public static final int SCREEN_WINDOW_FULLSCREEN = 1;
    public static final int SCREEN_WINDOW_TINY = 2;
    public static final int SCREEN_LAYOUT_DETAIL = 3;

    public static final int CURRENT_STATE_NORMAL = 0;
    public static final int CURRENT_STATE_PREPARING = 1;
    public static final int CURRENT_STATE_PLAYING = 2;
    public static final int CURRENT_STATE_PLAYING_BUFFERING_START = 3;
    public static final int CURRENT_STATE_PAUSE = 5;
    public static final int CURRENT_STATE_AUTO_COMPLETE = 6;
    public static final int CURRENT_STATE_ERROR = 7;

    public int currentState = -1;
    public int currentScreen = -1;


    public String url = null;
    public Object[] objects = null;
    public boolean looping = false;
    public Map<String, String> mapHeadData = new HashMap<>();
    public int seekToInAdvance = -1;

    public ImageView startButton;
    public SeekBar progressBar;
    public ImageView fullscreenButton;
    public TextView currentTimeTextView, totalTimeTextView;
    public ViewGroup textureViewContainer;
    public ViewGroup topContainer, bottomContainer;
    public Surface surface;

    protected static JCBuriedPoint JC_BURIED_POINT;
    protected static Timer UPDATE_PROGRESS_TIMER;

    protected int mScreenWidth;
    protected int mScreenHeight;
    protected AudioManager mAudioManager;
    protected Handler mHandler;
    protected ProgressTimerTask mProgressTimerTask;

    protected boolean mTouchingProgressBar;
    protected float mDownX;
    protected float mDownY;
    protected boolean mChangeVolume;
    protected boolean mChangePosition;
    protected int mDownPosition;
    protected int mGestureDownVolume;
    protected int mSeekTimePosition;

    protected boolean mSeekBarTouch;
    protected static boolean mPlayerBuffering;

    protected static int mPauseBeforePosition = 0;
    protected static int mUpdateTimes = -1;

    public JCVideoPlayer(Context context) {
        super(context);
        init(context);
    }

    public JCVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void init(Context context) {
        View.inflate(context, getLayoutId(), this);
        startButton = (ImageView) findViewById(R.id.start);
        fullscreenButton = (ImageView) findViewById(R.id.fullscreen);
        progressBar = (SeekBar) findViewById(R.id.progress);
        currentTimeTextView = (TextView) findViewById(R.id.current);
        totalTimeTextView = (TextView) findViewById(R.id.total);
        bottomContainer = (ViewGroup) findViewById(R.id.layout_bottom);
        textureViewContainer = (ViewGroup) findViewById(R.id.surface_container);
        topContainer = (ViewGroup) findViewById(R.id.layout_top);

        startButton.setOnClickListener(this);
        fullscreenButton.setOnClickListener(this);
        progressBar.setOnSeekBarChangeListener(this);
        bottomContainer.setOnClickListener(this);
        textureViewContainer.setOnClickListener(this);

        textureViewContainer.setOnTouchListener(this);
        mScreenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        mScreenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler();
    }

    public boolean setUp(String url, int screen, Object... objects) {
        if (!TextUtils.isEmpty(this.url) && TextUtils.equals(this.url, url)) {
            return false;
        }
        this.currentState = CURRENT_STATE_NORMAL;
        this.url = url;
        this.objects = objects;
        this.currentScreen = screen;
        setUiWitStateAndScreen(CURRENT_STATE_NORMAL);
        return true;
    }

    public boolean setUp(String url, int screen, Map<String, String> mapHeadData, Object... objects) {
        if (setUp(url, screen, objects)) {
            this.mapHeadData.clear();
            this.mapHeadData.putAll(mapHeadData);
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.start) {
            Log.i(TAG, "onClick start [" + this.hashCode() + "] ");
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(getContext(), getResources().getString(R.string.no_url), Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentState == CURRENT_STATE_NORMAL || currentState == CURRENT_STATE_ERROR) {
                if (!url.startsWith("file") && JCUtils.isMobileConnected(getContext()) && !WIFI_TIP_DIALOG_SHOWED) {
                    showWifiDialog();
                    return;
                }
                prepareVideo();
                onEvent(currentState != CURRENT_STATE_ERROR ? JCBuriedPoint.ON_CLICK_START_ICON : JCBuriedPoint.ON_CLICK_START_ERROR);
            } else if (currentState == CURRENT_STATE_PLAYING) {
                onEvent(JCBuriedPoint.ON_CLICK_PAUSE);
                mPauseBeforePosition = getCurrentPosition();
                Log.d(TAG, "pauseVideo [" + this.hashCode() + "] ");
                JCMediaManager.instance().mediaPlayer.pause();
                setUiWitStateAndScreen(CURRENT_STATE_PAUSE);
            } else if (currentState == CURRENT_STATE_PAUSE) {
                onEvent(JCBuriedPoint.ON_CLICK_RESUME);
                JCMediaManager.instance().mediaPlayer.start();
                if (mPauseBeforePosition != 0) {
                    mUpdateTimes = 0;
                }
                setUiWitStateAndScreen(CURRENT_STATE_PLAYING);
            } else if (currentState == CURRENT_STATE_AUTO_COMPLETE) {
                onEvent(JCBuriedPoint.ON_CLICK_START_AUTO_COMPLETE);
                prepareVideo();
            }
        } else if (i == R.id.fullscreen) {
            Log.i(TAG, "onClick fullscreen [" + this.hashCode() + "] ");
            if (currentState == CURRENT_STATE_AUTO_COMPLETE) return;
            if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
                //quit fullscreen
                backPress();
            } else {
                Log.d(TAG, "toFullscreenActivity [" + this.hashCode() + "] ");
                onEvent(JCBuriedPoint.ON_ENTER_FULLSCREEN);
                startWindowFullscreen();
            }
        } else if (i == R.id.surface_container && currentState == CURRENT_STATE_ERROR) {
            Log.i(TAG, "onClick surfaceContainer State=Error [" + this.hashCode() + "] ");
//            prepareVideo();  // 取消在CURRENT_STATE_ERROR状态下点击视频节目就重新播放的功能。
        }
    }

    public void prepareVideo() {
        Log.d(TAG, "prepareVideo [" + this.hashCode() + "] ");
        // 解决全屏模式下的CURRENT_STATE_ERROR状态下, 点击重新加载后退出了全屏模式.
        // 间接解决因为mediaplayer状态混乱(全屏模式下会prepare, 全屏模式被退出后小窗口状态异常,被点击后容易导致)引起的App Crash问题
        if (currentScreen != SCREEN_WINDOW_FULLSCREEN) {
            JCMediaPlayerListener listener = JCVideoPlayerManager.listener();
            if (listener != null) {
                listener.onCompletion();
            }
        }
        JCVideoPlayerManager.setListener(this);
        addTextureView();
        AudioManager mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        JCUtils.scanForActivity(getContext()).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        JCMediaManager.instance().prepare(url, mapHeadData, looping);
        setUiWitStateAndScreen(CURRENT_STATE_PREPARING);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int id = v.getId();
        if (id == R.id.surface_container) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.i(TAG, "onTouch surfaceContainer actionDown [" + this.hashCode() + "] ");
                    mTouchingProgressBar = true;

                    mDownX = x;
                    mDownY = y;
                    mChangeVolume = false;
                    mChangePosition = false;
                    /////////////////////
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.i(TAG, "onTouch surfaceContainer actionMove [" + this.hashCode() + "] ");
                    float deltaX = x - mDownX;
                    float deltaY = y - mDownY;
                    float absDeltaX = Math.abs(deltaX);
                    float absDeltaY = Math.abs(deltaY);
                    if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
                        if (!mChangePosition && !mChangeVolume) {
                            if (absDeltaX > THRESHOLD || absDeltaY > THRESHOLD) {
                                cancelProgressTimer();
                                if (absDeltaX >= THRESHOLD) {
                                    // 全屏模式下的CURRENT_STATE_ERROR状态下,不响应进度拖动事件.
                                    // 否则会因为mediaplayer的状态非法导致App Crash
                                    if (currentState != CURRENT_STATE_ERROR) {
                                        clearPositionHelper();
                                        mChangePosition = true;
                                        mDownPosition = getCurrentPositionWhenPlaying();
                                    }
                                } else {
                                    mChangeVolume = true;
                                    mGestureDownVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                }
                            }
                        }
                    }
                    if (mChangePosition) {
                        int totalTimeDuration = getDuration();
                        mSeekTimePosition = (int) (mDownPosition + deltaX * totalTimeDuration / mScreenWidth);
                        if (mSeekTimePosition > totalTimeDuration)
                            mSeekTimePosition = totalTimeDuration;
                        String seekTime = JCUtils.stringForTime(mSeekTimePosition);
                        String totalTime = JCUtils.stringForTime(totalTimeDuration);

                        showProgressDialog(deltaX, seekTime, mSeekTimePosition, totalTime, totalTimeDuration);
                    }
                    if (mChangeVolume) {
                        deltaY = -deltaY;
                        int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int deltaV = (int) (max * deltaY * 3 / mScreenHeight);
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mGestureDownVolume + deltaV, 0);
                        int volumePercent = (int) (mGestureDownVolume * 100 / max + deltaY * 3 * 100 / mScreenHeight);

                        showVolumeDialog(-deltaY, volumePercent);
                    }

                    break;
                case MotionEvent.ACTION_UP:
                    Log.i(TAG, "onTouch surfaceContainer actionUp [" + this.hashCode() + "] ");
                    mTouchingProgressBar = false;
                    dismissProgressDialog();
                    dismissVolumeDialog();
                    if (mChangePosition) {
                        onEvent(JCBuriedPoint.ON_TOUCH_SCREEN_SEEK_POSITION);
                        JCMediaManager.instance().mediaPlayer.seekTo(mSeekTimePosition);
                        int duration = getDuration();
                        int progress = mSeekTimePosition * 100 / (duration == 0 ? 1 : duration);
                        progressBar.setProgress(progress);
                    }
                    if (mChangeVolume) {
                        onEvent(JCBuriedPoint.ON_TOUCH_SCREEN_SEEK_VOLUME);
                    }
                    startProgressTimer();
                    break;
            }
        }
        return false;
    }

    public void addTextureView() {
        Log.d(TAG, "addTextureView [" + this.hashCode() + "] ");
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        JCMediaManager.textureView = null;
        JCMediaManager.textureView = new JCResizeTextureView(getContext());
        JCMediaManager.textureView.setVideoSize(JCMediaManager.instance().getVideoSize());
        JCMediaManager.textureView.setRotation(JCMediaManager.instance().videoRotation);
        JCMediaManager.textureView.setSurfaceTextureListener(this);

        FrameLayout.LayoutParams layoutParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER);
        textureViewContainer.addView(JCMediaManager.textureView, layoutParams);
    }

    public void setUiWitStateAndScreen(int state) {
        currentState = state;
        switch (currentState) {
            case CURRENT_STATE_NORMAL:
                if (isCurrentMediaListener()) {
                    cancelProgressTimer();
                    JCMediaManager.instance().releaseMediaPlayer();
                }
                break;
            case CURRENT_STATE_PREPARING:
                resetProgressAndTime();
                break;
            case CURRENT_STATE_PLAYING:
                startProgressTimer();
                break;
            case CURRENT_STATE_PAUSE:
            case CURRENT_STATE_PLAYING_BUFFERING_START:
                cancelProgressTimer();
                break;
            case CURRENT_STATE_ERROR:
                cancelProgressTimer();
                if (isCurrentMediaListener()) {
                    JCMediaManager.instance().releaseMediaPlayer();
                }
                break;
            case CURRENT_STATE_AUTO_COMPLETE:
                cancelProgressTimer();
                progressBar.setProgress(100);
                currentTimeTextView.setText(totalTimeTextView.getText());
                break;
        }
    }

    public void startProgressTimer() {
        cancelProgressTimer();
        UPDATE_PROGRESS_TIMER = new Timer();
        mProgressTimerTask = new ProgressTimerTask();
        UPDATE_PROGRESS_TIMER.schedule(mProgressTimerTask, 0, 300);
    }

    public void cancelProgressTimer() {
        if (UPDATE_PROGRESS_TIMER != null) {
            UPDATE_PROGRESS_TIMER.cancel();
        }
        if (mProgressTimerTask != null) {
            mProgressTimerTask.cancel();
        }
    }

    @Override
    public void onPrepared() {
        Log.i(TAG, "onPrepared " + " [" + this.hashCode() + "] ");

        if (currentState != CURRENT_STATE_PREPARING) return;
        JCMediaManager.instance().mediaPlayer.start();
        if (seekToInAdvance != -1) {
            JCMediaManager.instance().mediaPlayer.seekTo(seekToInAdvance);
            seekToInAdvance = -1;
        }
        startProgressTimer();
        setUiWitStateAndScreen(CURRENT_STATE_PLAYING);
    }

    public void clearFullscreenLayout() {
        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext())).findViewById(Window.ID_ANDROID_CONTENT);
        View oldF = vp.findViewById(R.id.FULLSCREEN_ID);
        View oldT = vp.findViewById(R.id.TINY_ID);
        if (oldF != null) {
            vp.removeView(oldF);
        }
        if (oldT != null) {
            vp.removeView(oldT);
        }
        showSupportActionBar(getContext());
    }

    @Override
    public void onAutoCompletion() {
        Log.i(TAG, "onAutoCompletion " + " [" + this.hashCode() + "] ");
        onEvent(JCBuriedPoint.ON_AUTO_COMPLETE);
        JCMediaPlayerListener listener = JCVideoPlayerManager.listener();
        if (listener != null) {
            listener.onCompletion();
            JCVideoPlayerManager.setListener(null);
        }

        JCMediaPlayerListener lastListener = JCVideoPlayerManager.lastListener();
        if (lastListener != null) {
            lastListener.onCompletion();
            JCVideoPlayerManager.setLastListener(null);
        }
    }

    @Override
    public void onCompletion() {
        Log.i(TAG, "onCompletion " + " [" + this.hashCode() + "] ");
        setUiWitStateAndScreen(CURRENT_STATE_NORMAL);
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }

        JCVideoPlayerManager.setListener(null);//这里还不完全,
//        JCVideoPlayerManager.setLastListener(null);
        JCMediaManager.instance().currentVideoWidth = 0;
        JCMediaManager.instance().currentVideoHeight = 0;

        // 清理缓存变量
        JCMediaManager.instance().bufferPercent = 0;
        JCMediaManager.instance().videoRotation = 0;

        // 防止在Buffering时按Home键退出。
        mPlayerBuffering = false;

        AudioManager mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
        JCUtils.scanForActivity(getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        clearFullscreenLayout();
    }

    @Override
    public boolean goToOtherListener() {//这里这个名字这么写并不对,这是在回退的时候gotoother,如果直接gotoother就不叫这个名字
        Log.i(TAG, "goToOtherListener " + " [" + this.hashCode() + "] ");

        if (currentScreen == JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN
                || currentScreen == JCVideoPlayerStandard.SCREEN_WINDOW_TINY) {
//            if (currentScreen == JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN) {
//                final Animation ra = AnimationUtils.loadAnimation(getContext(), R.anim.quit_fullscreen);
//                startAnimation(ra);
//            }
            onEvent(currentScreen == JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN ?
                    JCBuriedPoint.ON_QUIT_FULLSCREEN :
                    JCBuriedPoint.ON_QUIT_TINYSCREEN);
            if (JCVideoPlayerManager.lastListener() == null) {//directly fullscreen
                JCMediaPlayerListener listener = JCVideoPlayerManager.listener();
                if (listener != null) {
                    listener.onCompletion();
                }
                showSupportActionBar(getContext());
                return true;
            }
            ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext())).findViewById(Window.ID_ANDROID_CONTENT);
            vp.removeView(this);
            JCVideoPlayerManager.setListener(JCVideoPlayerManager.lastListener());
            JCVideoPlayerManager.setLastListener(null);
            JCMediaManager.instance().lastState = currentState;//save state
            JCMediaPlayerListener listener = JCVideoPlayerManager.listener();
            if (listener != null) {
                listener.goBackThisListener();
            }
            CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    long lastAutoFullscreenTime = 0;

    @Override
    public void autoFullscreenLeft() {
        if ((System.currentTimeMillis() - lastAutoFullscreenTime) > 2000
                && isCurrentMediaListener()
                && currentState == CURRENT_STATE_PLAYING
                && currentScreen != SCREEN_WINDOW_FULLSCREEN
                && currentScreen != SCREEN_WINDOW_TINY) {
            lastAutoFullscreenTime = System.currentTimeMillis();
            startWindowFullscreen();
        }
    }

    @Override
    public void autoFullscreenRight() {

    }

    @Override
    public void autoQuitFullscreen() {
        if ((System.currentTimeMillis() - lastAutoFullscreenTime) > 2000
                && isCurrentMediaListener()
                && currentState == CURRENT_STATE_PLAYING
                && currentScreen == SCREEN_WINDOW_FULLSCREEN) {
            lastAutoFullscreenTime = System.currentTimeMillis();
            backPress();
        }
    }

    @Override
    public void onBufferingUpdate(int percent) {
        if (currentState != CURRENT_STATE_NORMAL && currentState != CURRENT_STATE_PREPARING) {
            Log.v(TAG, "onBufferingUpdate " + percent + " [" + this.hashCode() + "] ");
            JCMediaManager.instance().bufferPercent = percent;
        }
    }

    @Override
    public void onSeekComplete() {
        clearPositionHelper();
    }

    @Override
    public void onError(int what, int extra) {
        Log.e(TAG, "onError " + what + " - " + extra + " [" + this.hashCode() + "] ");
        if (what != 38 && what != -38) {
            setUiWitStateAndScreen(CURRENT_STATE_ERROR);

            // 防止在Buffering时网络突然中断。
            mPlayerBuffering = false;
        }
    }

    @Override
    public void onInfo(int what, int extra) {
        Log.d(TAG, "onInfo what - " + what + " extra - " + extra);
        if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            if (currentState != CURRENT_STATE_PLAYING_BUFFERING_START) {
                mPlayerBuffering = true;
                JCMediaManager.instance().backUpBufferState = currentState;
                Log.d(TAG, "BUFFERING_START backUpBufferState - " + JCMediaManager.instance().backUpBufferState);
                setUiWitStateAndScreen(CURRENT_STATE_PLAYING_BUFFERING_START);
                Log.d(TAG, "MEDIA_INFO_BUFFERING_START");
            } else {
                Log.d(TAG, "currentState ==  CURRENT_STATE_PLAYING_BUFFERING_START ignore");
            }
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
            mPlayerBuffering = false;
            Log.d(TAG, "BUFFERING_END backUpBufferState - " + JCMediaManager.instance().backUpBufferState);
            if (JCMediaManager.instance().backUpBufferState != -1) {
                if (JCMediaManager.instance().backUpBufferState == CURRENT_STATE_PAUSE &&
                        JCMediaManager.instance().mediaPlayer.isPlaying()) {
                    JCMediaManager.instance().mediaPlayer.pause();
                }
                if (JCMediaManager.instance().backUpBufferState == CURRENT_STATE_PLAYING &&
                        !JCMediaManager.instance().mediaPlayer.isPlaying()) {
                    JCMediaManager.instance().mediaPlayer.start();
                }
                setUiWitStateAndScreen(JCMediaManager.instance().backUpBufferState);
                JCMediaManager.instance().backUpBufferState = -1;
            }
            Log.d(TAG, "MEDIA_INFO_BUFFERING_END");
        } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
            JCMediaManager.instance().videoRotation = extra;
            JCMediaManager.textureView.setRotation(extra);
            Log.d(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED");
        }
    }

    @Override
    public void onVideoSizeChanged() {
        Log.i(TAG, "onVideoSizeChanged " + " [" + this.hashCode() + "] ");
        JCMediaManager.textureView.setVideoSize(JCMediaManager.instance().getVideoSize());
    }

    @Override
    public void goBackThisListener() {
        Log.i(TAG, "goBackThisListener " + " [" + this.hashCode() + "] ");

        currentState = JCMediaManager.instance().lastState;
        setUiWitStateAndScreen(currentState);
        setTextAndProgress(JCMediaManager.instance().bufferPercent);
        // 在全屏模式下，有可能播放失败，切换到CURRENT_STATE_ERROR会releaseMediaPlayer。
        // 此时按Back键，会在addTextureView中的setSurfaceTextureListener地方Crash
        if (currentState != CURRENT_STATE_ERROR) {
            addTextureView();
        }

        showSupportActionBar(getContext());
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureAvailable [" + this.hashCode() + "] ");
        this.surface = new Surface(surface);
        JCMediaManager.instance().setDisplay(this.surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        surface.release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        Log.v(TAG, "bottomProgress onProgressChanged " + progress + ", " + fromUser + " [" + this.hashCode() + "] ");
        if (fromUser) {
            if (currentState != CURRENT_STATE_PLAYING &&
                    currentState != CURRENT_STATE_PAUSE &&
                    currentState != CURRENT_STATE_PLAYING_BUFFERING_START) return;
            int duration = getDuration();
            int currentTime = progress * (duration == 0 ? 1 : duration) / 100;
            currentTimeTextView.setText(JCUtils.stringForTime(currentTime));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        Log.i(TAG, "bottomProgress onStartTrackingTouch [" + this.hashCode() + "] ");
        clearPositionHelper();
        mSeekBarTouch = true;
//        cancelProgressTimer();
        ViewParent vpdown = getParent();
        while (vpdown != null) {
            vpdown.requestDisallowInterceptTouchEvent(true);
            vpdown = vpdown.getParent();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.i(TAG, "bottomProgress onStopTrackingTouch [" + this.hashCode() + "] ");
        mSeekBarTouch = false;
        onEvent(JCBuriedPoint.ON_SEEK_POSITION);
//        startProgressTimer();
        ViewParent vpup = getParent();
        while (vpup != null) {
            vpup.requestDisallowInterceptTouchEvent(false);
            vpup = vpup.getParent();
        }
        if (currentState != CURRENT_STATE_PLAYING && currentState != CURRENT_STATE_PAUSE &&
                currentState != CURRENT_STATE_PLAYING_BUFFERING_START) {
            return;
        }
        int time = seekBar.getProgress() * getDuration() / 100;
        JCMediaManager.instance().mediaPlayer.seekTo(time);
        Log.i(TAG, "seekTo " + time + " [" + this.hashCode() + "] ");
    }

    public static boolean backPress() {
        Log.i(TAG, "backPress");
        JCMediaPlayerListener listener = JCVideoPlayerManager.listener();
        if (listener != null) {
            return listener.goToOtherListener();
        }
        return false;
    }

    public void startWindowFullscreen() {
        Log.i(TAG, "startWindowFullscreen " + " [" + this.hashCode() + "] ");

        hideSupportActionBar(getContext());

        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext())).findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(R.id.FULLSCREEN_ID);
        if (old != null) {
            vp.removeView(old);
        }
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        try {
            Constructor<JCVideoPlayer> constructor = (Constructor<JCVideoPlayer>) JCVideoPlayer.this.getClass().getConstructor(Context.class);
            JCVideoPlayer jcVideoPlayer = constructor.newInstance(getContext());
            jcVideoPlayer.setId(R.id.FULLSCREEN_ID);
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            int w = wm.getDefaultDisplay().getWidth();
            int h = wm.getDefaultDisplay().getHeight();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h, w);
            lp.setMargins((w - h) / 2, -(w - h) / 2, 0, 0);
            vp.addView(jcVideoPlayer, lp);
            jcVideoPlayer.setUp(url, JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN, objects);
            jcVideoPlayer.setUiWitStateAndScreen(currentState);
            jcVideoPlayer.setTextAndProgress(JCMediaManager.instance().bufferPercent);
            jcVideoPlayer.addTextureView();
            jcVideoPlayer.setRotation(90);

//            final Animation ra = AnimationUtils.loadAnimation(getContext(), R.anim.start_fullscreen);
//            jcVideoPlayer.setAnimation(ra);

            JCVideoPlayerManager.setLastListener(this);
            JCVideoPlayerManager.setListener(jcVideoPlayer);


        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startWindowTiny() {
        Log.i(TAG, "startWindowTiny " + " [" + this.hashCode() + "] ");
        onEvent(JCBuriedPoint.ON_ENTER_TINYSCREEN);

        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext())).findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(R.id.TINY_ID);
        if (old != null) {
            vp.removeView(old);
        }
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        try {
            Constructor<JCVideoPlayer> constructor = (Constructor<JCVideoPlayer>) JCVideoPlayer.this.getClass().getConstructor(Context.class);
            JCVideoPlayer mJcVideoPlayer = constructor.newInstance(getContext());
            mJcVideoPlayer.setId(R.id.TINY_ID);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(400, 400);
            lp.gravity = Gravity.END | Gravity.BOTTOM;
            vp.addView(mJcVideoPlayer, lp);
            mJcVideoPlayer.setUp(url, JCVideoPlayerStandard.SCREEN_WINDOW_TINY, objects);
            mJcVideoPlayer.setUiWitStateAndScreen(currentState);
            mJcVideoPlayer.addTextureView();
            JCVideoPlayerManager.setLastListener(this);
            JCVideoPlayerManager.setListener(mJcVideoPlayer);

        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    static public void onOrientationConfigurationChanged(int orientation) {
        if (JCVideoPlayerManager.lastListener() != null &&
                JCVideoPlayerManager.listener() != null) {
            JCVideoPlayer player = (JCVideoPlayer) JCVideoPlayerManager.listener();
            if (player != null) {
                player.orientationChange(orientation);
            }
        }
    }

    public void orientationChange(int orientation) {

    }

    public class ProgressTimerTask extends TimerTask {
        @Override
        public void run() {
            if (currentState == CURRENT_STATE_PLAYING || currentState == CURRENT_STATE_PAUSE ||
                    currentState == CURRENT_STATE_PLAYING_BUFFERING_START) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setTextAndProgress(JCMediaManager.instance().bufferPercent);
                        if (mUpdateTimes > -1) {
                            mUpdateTimes++;
                        }
                    }
                });
            }
        }
    }

    /**
     * mPauseBeforePosition 在点击视频暂停前一刻取的值
     * mUpdateTimes 在暂停后点击播放开始记录Timer的启动次数
     * 基本原理是：将当前的currentPosition 与 mPauseBeforePosition + mUpdateTimes*350 进行比较，
     * 如果差值小于100，则说明当前的mediaPlayer的currentPosition是正常的。
     *
     * @return
     */

    public int getCurrentPositionWhenPlaying() {
        int currentPosition = 0;
        if (currentState == CURRENT_STATE_PLAYING || currentState == CURRENT_STATE_PAUSE ||
                currentState == CURRENT_STATE_PLAYING_BUFFERING_START) {
            currentPosition = getCurrentPosition();
//            Log.v(TAG, "ShengJun -----------------------------\n");
//            Log.v(TAG, "ShengJun currentPosition " + currentPosition + " [" + this.hashCode() + "] ");
//            Log.v(TAG, "ShengJun mPauseBeforePosition " + mPauseBeforePosition + " [" + this.hashCode() + "] ");
//            Log.v(TAG, "ShengJun mUpdateTimes " + mUpdateTimes + " [" + this.hashCode() + "] ");
            if (mPauseBeforePosition != 0 && mUpdateTimes != -1) {
                int currentPausePosition = mPauseBeforePosition + mUpdateTimes * 320; // timer 300ms启动一次，handler有延迟
//                Log.v(TAG, "ShengJun currentPausePosition " + currentPausePosition + " [" + this.hashCode() + "] ");
//                Log.v(TAG, "ShengJun Math.abs(currentPosition - currentPausePosition)  " + Math.abs(currentPosition - currentPausePosition) + " [" + this.hashCode() + "] ");
                if (Math.abs(currentPosition - currentPausePosition) < 100) {
                    clearPositionHelper();
                } else {
                    // 如果currentPosition已经小于currentPausePosition, 则认为正常
                    if (currentPosition < currentPausePosition) {
                        clearPositionHelper();
                    } else {
                        currentPosition = currentPausePosition;
                    }
                }
            }
        }
//        Log.v(TAG, "ShengJun return currentPosition " + currentPosition + " [" + this.hashCode() + "] ");
//        Log.v(TAG, "ShengJun -----------------------------\n");
        return currentPosition;
    }

    private void clearPositionHelper() {
        mPauseBeforePosition = 0;
        mUpdateTimes = -1;
    }

    public int getCurrentPosition() {
        int position = 0;
        try {
            position = (int) JCMediaManager.instance().mediaPlayer.getCurrentPosition();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return position;
        }
        return position;
    }

    public int getDuration() {
        int duration = 0;
        try {
            duration = (int) JCMediaManager.instance().mediaPlayer.getDuration();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return duration;
        }
        return duration;
    }

    public void setTextAndProgress(int secProgress) {
        int position = getCurrentPositionWhenPlaying();
        int duration = getDuration();
        Log.v(TAG, "setTextAndProgress " + position + "/" + duration + " [" + this.hashCode() + "] ");
        int progress = position * 100 / (duration == 0 ? 1 : duration);
        setProgressAndTime(progress, secProgress, position, duration);
    }

    public void setProgressAndTime(int progress, int secProgress, int currentTime, int totalTime) {
        if (!mTouchingProgressBar && !mSeekBarTouch) {
            if (progress != 0) progressBar.setProgress(progress);
        }
        if (!mSeekBarTouch) {
            if (currentTime != 0) currentTimeTextView.setText(JCUtils.stringForTime(currentTime));
        }
        totalTimeTextView.setText(JCUtils.stringForTime(totalTime));
    }

    public void resetProgressAndTime() {
        progressBar.setProgress(0);
//        progressBar.setSecondaryProgress(0);
        currentTimeTextView.setText(JCUtils.stringForTime(0));
        totalTimeTextView.setText(JCUtils.stringForTime(0));
    }

    public static AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    releaseAllVideos();
                    Log.d(TAG, "AUDIOFOCUS_LOSS [" + this.hashCode() + "]");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (JCMediaManager.instance().mediaPlayer.isPlaying()) {
                        JCMediaManager.instance().mediaPlayer.pause();
                        if (JCVideoPlayerManager.listener() == null) { //
                            JCVideoPlayer player = (JCVideoPlayer) JCVideoPlayerManager.listener();
                            if (player != null) {
                                player.setUiWitStateAndScreen(CURRENT_STATE_PAUSE);
                            }
                        }
                    }
                    Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT [" + this.hashCode() + "]");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    break;
            }
        }
    };

    public void release() {
        if (isCurrentMediaListener() &&
                (System.currentTimeMillis() - CLICK_QUIT_FULLSCREEN_TIME) > FULL_SCREEN_NORMAL_DELAY) {
            Log.d(TAG, "release [" + this.hashCode() + "]");
            releaseAllVideos();
        }
    }

    public boolean isCurrentMediaListener() {
        return JCVideoPlayerManager.listener() != null
                && JCVideoPlayerManager.listener() == this;
    }

    public static void releaseAllVideos() {
        Log.d(TAG, "releaseAllVideos");
        JCMediaPlayerListener listener = JCVideoPlayerManager.listener();
        if (listener != null) {
            listener.onCompletion();
        }
        JCMediaPlayerListener lastLisenter = JCVideoPlayerManager.lastListener();
        if (lastLisenter != null) {
            lastLisenter.onCompletion();
        }
        JCMediaManager.instance().releaseMediaPlayer();
        JCMediaManager.textureView = null;
    }

    public static void setJcBuriedPoint(JCBuriedPoint jcBuriedPoint) {
        JC_BURIED_POINT = jcBuriedPoint;
    }

    public void onEvent(int type) {
        if (JC_BURIED_POINT != null && isCurrentMediaListener()) {
            JC_BURIED_POINT.onEvent(type, url, currentScreen, objects);
        }
    }

    public static void startFullscreen(Context context, Class _class, String url, Object... objects) {

        hideSupportActionBar(context);
        ViewGroup vp = (ViewGroup) (JCUtils.getAppCompActivity(context)).findViewById(Window.ID_ANDROID_CONTENT);
        View old = null;
        if (vp != null) {
            old = vp.findViewById(R.id.FULLSCREEN_ID);
        }
        if (old != null) {
            vp.removeView(old);
        }
        try {
            Constructor<JCVideoPlayer> constructor = _class.getConstructor(Context.class);
            JCVideoPlayer jcVideoPlayer = constructor.newInstance(context);
            jcVideoPlayer.setId(R.id.FULLSCREEN_ID);
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            int w = wm.getDefaultDisplay().getWidth();
            int h = wm.getDefaultDisplay().getHeight();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h, w);
            lp.setMargins((w - h) / 2, -(w - h) / 2, 0, 0);
            if (vp != null) {
                vp.addView(jcVideoPlayer, lp);
            }

//            final Animation ra = AnimationUtils.loadAnimation(context, R.anim.start_fullscreen);
//            jcVideoPlayer.setAnimation(ra);

            jcVideoPlayer.setUp(url, JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN, objects);
            jcVideoPlayer.addTextureView();
            jcVideoPlayer.setRotation(90);

            jcVideoPlayer.startButton.performClick();

        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void hideSupportActionBar(Context context) {
        if (ACTION_BAR_EXIST) {
            ActionBar ab = JCUtils.getAppCompActivity(context).getSupportActionBar();
            if (ab != null) {
                ab.setShowHideAnimationEnabled(false);
                ab.hide();
            }
        }
        if (TOOL_BAR_EXIST) {
            JCUtils.getAppCompActivity(context).getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    public void showSupportActionBar(Context context) {
        if (ACTION_BAR_EXIST) {
            ActionBar ab = JCUtils.getAppCompActivity(context).getSupportActionBar();
            if (ab != null) {
                ab.setShowHideAnimationEnabled(false);
                ab.show();
            }
        }
        if (TOOL_BAR_EXIST) {
            JCUtils.getAppCompActivity(context).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    public static class JCAutoFullscreenListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {//可以得到传感器实时测量出来的变化值
            float x = event.values[SensorManager.DATA_X];
            float y = event.values[SensorManager.DATA_Y];
            if (x > 10) {
                //direction left
                JCMediaPlayerListener listener = JCVideoPlayerManager.listener();
                if (listener != null) {
                    listener.autoFullscreenLeft();
                }
            } else if (y > 9.5) {
                JCMediaPlayerListener listener = JCVideoPlayerManager.listener();
                if (listener != null) {
                    listener.autoQuitFullscreen();
                }
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }

    public void showWifiDialog() {
    }

    public void showProgressDialog(float deltaX,
                                   String seekTime, int seekTimePosition,
                                   String totalTime, int totalTimeDuration) {
    }

    public void dismissProgressDialog() {

    }

    public void showVolumeDialog(float deltaY, int volumePercent) {

    }

    public void dismissVolumeDialog() {

    }


    public abstract int getLayoutId();


}
