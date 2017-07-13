package fm.jiecao.jcvideoplayer_lib.xclub;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.os.Build;
import android.text.Html;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

import fm.jiecao.jcvideoplayer_lib.JCBuriedPoint;
import fm.jiecao.jcvideoplayer_lib.JCBuriedPointStandard;
import fm.jiecao.jcvideoplayer_lib.JCMediaManager;
import fm.jiecao.jcvideoplayer_lib.JCResizeImageView;
import fm.jiecao.jcvideoplayer_lib.JCUtils;
import fm.jiecao.jcvideoplayer_lib.JCVideoPlayer;
import fm.jiecao.jcvideoplayer_lib.JCVideoPlayerManager;
import fm.jiecao.jcvideoplayer_lib.JCVideoPlayerStandard;
import fm.jiecao.jcvideoplayer_lib.R;
import tv.danmaku.ijk.media.player.IMediaPlayer;

public class XClubVideoPlayer extends JCVideoPlayer {
    private static final String TAG = "JieCaoVideoPlayerXClub";

    protected static Timer DISMISS_CONTROL_VIEW_TIMER;

    public ImageView backButton;
    public ProgressBar bottomProgressBar, loadingProgressBar;
    public TextView titleTextView;
    public LinearLayout titleLinearLayout;
    public ImageView thumbImageView;
    public JCResizeImageView coverImageView;
    public ImageView tinyBackImageView;
    public TextView errorTextView;

    protected static Bitmap pauseSwitchCoverBitmap = null;
    protected boolean textureUpdated;
    protected boolean textureSizeChanged;

    protected DismissControlViewTimerTask mDismissControlViewTimerTask;

    private RelativeLayout centerController;
    private PlayCallBack callback;
    /**
     * 音量、亮度调节
     */
    private float mY;           //滑动Y坐标
    private float mX;
    private float mDistanceY;   //滑动距离Y
    private float mDistanceX;
    boolean isVolume = true;    //亮度或者音量
    boolean mAdjust = false;
    boolean mAdjustScope = false;
    private FrameLayout layout_volume;                    //音量布局
    private FrameLayout layout_brightness;                //亮度布局
    private CircleProgressBar progress_volume;
    private CircleProgressBar progress_brightness;
    private ImageView img_volume;                         //音量图标
    private ImageView img_volume_ban;                     //禁音图标
    private int adjustVolume = 0;

    private static int mLastListState = -1;     //切换到全屏时，小屏的播放状态
//    private int mPauseTime = 0;
//    private int mVirtualTime = 0;

    public XClubVideoPlayer(Context context) {
        super(context);
    }

    public XClubVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void init(Context context) {
        super.init(context);
        bottomProgressBar = (ProgressBar) findViewById(R.id.bottom_progressbar);
        titleTextView = (TextView) findViewById(R.id.title);
        titleLinearLayout = (LinearLayout) findViewById(R.id.layout_video_title);
        backButton = (ImageView) findViewById(R.id.back);
        thumbImageView = (ImageView) findViewById(R.id.thumb);
        coverImageView = (JCResizeImageView) findViewById(R.id.cover);
        loadingProgressBar = (ProgressBar) findViewById(R.id.loading);
        tinyBackImageView = (ImageView) findViewById(R.id.back_tiny);
        errorTextView = (TextView) findViewById(R.id.error);
        centerController = (RelativeLayout) findViewById(R.id.center_controller);

        //音量、亮度
        layout_volume = (FrameLayout) findViewById(R.id.volume);
        layout_brightness = (FrameLayout) findViewById(R.id.brightness);
        img_volume = (ImageView) findViewById(R.id.img_volume);
        img_volume_ban = (ImageView) findViewById(R.id.img_volume_silence);
        progress_volume = (CircleProgressBar) findViewById(R.id.progressBar_volume);
        progress_brightness = (CircleProgressBar) findViewById(R.id.progressBar_brightness);

        thumbImageView.setOnClickListener(this);
        backButton.setOnClickListener(this);
        tinyBackImageView.setOnClickListener(this);
        loadingProgressBar.setOnClickListener(this);
    }

    // 如果视频已下架，请调用此函数
    public void setVideoInvalid() {
        tinyBackImageView.setVisibility(View.INVISIBLE);
        setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                View.INVISIBLE, View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                View.VISIBLE);
        errorTextView.setText(R.string.tips_video_invalid);
    }

    public void setPlayListener(PlayCallBack listener) {
        callback = listener;
    }

    @Override
    public boolean setUp(String url, int screen, Object... objects) {
        initAdjustGuide(screen);
        if (objects.length == 0) return false;
        if (super.setUp(url, screen, objects)) {
            titleTextView.setText(objects[0].toString());
            if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
                fullscreenButton.setImageResource(R.drawable.biz_shrink);
                adjustLoadingProgressBar(true);
                topContainer.setVisibility(View.VISIBLE);
                tinyBackImageView.setVisibility(View.INVISIBLE);
            } else if (currentScreen == SCREEN_LAYOUT_LIST) {
                fullscreenButton.setImageResource(R.drawable.biz_enlarge);
                adjustLoadingProgressBar(false);
                topContainer.setVisibility(View.INVISIBLE);
                tinyBackImageView.setVisibility(View.INVISIBLE);
            } else if (currentScreen == SCREEN_WINDOW_TINY) {
                tinyBackImageView.setVisibility(View.VISIBLE);
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE);
            }
            updateStartImage();
            return true;
        }
        return false;
    }

    private int getDimensionPixelSize(int size) {
        return getResources().getDimensionPixelSize(size);
    }

    private void adjustLoadingProgressBar(boolean isFullScreen) {
        ViewGroup.LayoutParams layoutParams = loadingProgressBar.getLayoutParams();
        int size = getDimensionPixelSize(isFullScreen ? R.dimen.loading_size_fullscreen : R.dimen.loading_size_small);
        layoutParams.width = size;
        layoutParams.height = size;
        loadingProgressBar.setLayoutParams(layoutParams);
    }

    @Override
    public int getLayoutId() {
        return R.layout.biz_layout_xclub;
    }

    @Override
    public void setUiWitStateAndScreen(int state) {
        super.setUiWitStateAndScreen(state);
        if (currentState == CURRENT_STATE_PREPARING) {
            progressBar.setEnabled(false);
        } else {
            progressBar.setEnabled(true);
        }
        switch (currentState) {
            case CURRENT_STATE_NORMAL:
                changeUiToNormal();
                break;
            case CURRENT_STATE_PREPARING:
                if (uiContainerShow()) {
                    changeUiToPreparingShow();
                } else {
                    changeUiToPreparingClear();
                }
                startDismissControlViewTimer();
                break;
            case CURRENT_STATE_PLAYING:
                if (uiContainerShow()) {
                    changeUiToPlayingShow();
                } else {
                    changeUiToPlayingClear();
                }
                startDismissControlViewTimer();
                break;
            case CURRENT_STATE_PAUSE:
                if (uiContainerShow()) {
                    changeUiToPauseShow();
                } else {
                    changeUiToPauseClear();
                }
                cancelDismissControlViewTimer();
                break;
            case CURRENT_STATE_ERROR:
                changeUiToError();
                break;
            case CURRENT_STATE_AUTO_COMPLETE:
                if (uiContainerShow()) {
                    changeUiToCompleteShow();
                } else {
                    changeUiToCompleteClear();
                }
                ;
                cancelDismissControlViewTimer();
                bottomProgressBar.setProgress(100);
                break;
            case CURRENT_STATE_PLAYING_BUFFERING_START:
                if (uiContainerShow()) {
                    changeUiToPlayingBufferingShow();
                } else {
                    changeUiToPlayingBufferingClear();
                }
                break;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (adjustVolumeBrightness(v, event)) {     //如果是调节音量和亮度，则不响应控制栏的点击事件
            return false;
        }
        int id = v.getId();
        if (id == R.id.surface_container) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_MOVE:
                    break;
                case MotionEvent.ACTION_UP:
                    startDismissControlViewTimer();
                    if (mChangePosition) {
                        int duration = getDuration();
                        int progress = mSeekTimePosition * 100 / (duration == 0 ? 1 : duration);
                        bottomProgressBar.setProgress(progress);
                    }
                    if (!mChangePosition && !mChangeVolume) {
                        onEvent(JCBuriedPointStandard.ON_CLICK_BLANK);
                        onClickUiToggle();
                    }
                    break;
            }
        } else if (id == R.id.progress) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    cancelDismissControlViewTimer();
                    break;
                case MotionEvent.ACTION_UP:
                    startDismissControlViewTimer();
                    break;
            }
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (currentScreen == SCREEN_LAYOUT_LIST && i == R.id.fullscreen) {
            hideSoftInput(v);
            saveLastListState();
        }
        // 需要在super.onClick(v)前拦截对应的点击事件
        if (i == R.id.start) {
            if (callback != null) {
                callback.callback();
            }
            if (mPlayerBuffering) {
                if (currentState == CURRENT_STATE_PAUSE) {
                    Log.d(TAG, "VIDEO BUFFERING, switch to CURRENT_STATE_PLAYING");
                    JCMediaManager.instance().backUpBufferState = CURRENT_STATE_PLAYING;
                    setUiWitStateAndScreen(CURRENT_STATE_PLAYING_BUFFERING_START);
                    return;
                }
            }
        }
        super.onClick(v);
        if (i == R.id.thumb) {
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(getContext(), getResources().getString(R.string.no_url), Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentState == CURRENT_STATE_NORMAL) {
                if (!url.startsWith("file") && JCUtils.isMobileConnected(getContext()) && !WIFI_TIP_DIALOG_SHOWED) {
                    showWifiDialog();
                    return;
                }
                startPlayLogic();
            } else if (currentState == CURRENT_STATE_AUTO_COMPLETE) {
                onClickUiToggle();
            }
        } else if (i == R.id.surface_container) {
            startDismissControlViewTimer();
        } else if (i == R.id.back) {
            backPress();
        } else if (i == R.id.back_tiny) {
            backPress();
        } else if (i == R.id.loading) {
            if (mPlayerBuffering) {
                if (!uiContainerShow()) {
                    onClickUiToggle();
                }
                Log.d(TAG, "VIDEO BUFFERING, switch to CURRENT_STATE_PAUSE");
                JCMediaManager.instance().backUpBufferState = CURRENT_STATE_PAUSE;
                setUiWitStateAndScreen(CURRENT_STATE_PAUSE);
            }
        }
    }

    private void obtainCover() {
        if (currentState != CURRENT_STATE_ERROR && currentState != CURRENT_STATE_PREPARING) {
            Point videoSize = JCMediaManager.instance().getVideoSize();
            if (videoSize != null) {
                Bitmap bitmap = JCMediaManager.textureView.getBitmap(videoSize.x, videoSize.y);
                if (bitmap != null) {
                    pauseSwitchCoverBitmap = bitmap;
                }
            }
        }
    }

    public void refreshCover() {
        if (currentState != CURRENT_STATE_ERROR && currentState != CURRENT_STATE_PREPARING) {
            if (pauseSwitchCoverBitmap != null) {
                XClubVideoPlayer jcVideoPlayer = ((XClubVideoPlayer) JCVideoPlayerManager.listener());
                if (jcVideoPlayer != null) {
                    jcVideoPlayer.coverImageView.setBackgroundColor(Color.parseColor("#00000000"));
                    jcVideoPlayer.coverImageView.setImageBitmap(pauseSwitchCoverBitmap);
                    jcVideoPlayer.coverImageView.setVisibility(VISIBLE);
                }
            }
        }
    }

    @Override
    public void onInfo(int what, int extra) {
        super.onInfo(what, extra);
        if (what == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
            coverImageView.setRotation(JCMediaManager.instance().videoRotation);
        }
    }

    @Override
    public void onVideoSizeChanged() {
        super.onVideoSizeChanged();
        coverImageView.setVideoSize(JCMediaManager.instance().getVideoSize());
    }

    // onSurfaceTextureSizeChanged 在 onSurfaceTextureUpdated 之前调用

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        super.onSurfaceTextureAvailable(surface, width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        super.onSurfaceTextureSizeChanged(surface, width, height);

        // 如果SurfaceTexture还没有更新Image，则记录SizeChanged事件，否则忽略
        if (!textureUpdated) {
            textureSizeChanged = true;
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return super.onSurfaceTextureDestroyed(surface);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        super.onSurfaceTextureUpdated(surface);

        // 如果textureSizeChanged=true，则说明此次Updated事件不是Image更新引起的
        if (!textureSizeChanged) {
            coverImageView.setVisibility(INVISIBLE);
            JCMediaManager.textureView.setHasUpdated();
        } else {
            textureSizeChanged = false;
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        super.onProgressChanged(seekBar, progress, fromUser);
        if (fromUser) {
            bottomProgressBar.setProgress(progress);
        }
    }

    @Override
    public void showWifiDialog() {
        super.showWifiDialog();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(getResources().getString(R.string.tips_not_wifi));
        builder.setPositiveButton(getResources().getString(R.string.tips_not_wifi_confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                startPlayLogic();
                WIFI_TIP_DIALOG_SHOWED = true;
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.tips_not_wifi_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        super.onStartTrackingTouch(seekBar);
        cancelDismissControlViewTimer();
    }


    @Override
    public void addTextureView() {
        super.addTextureView();
        coverImageView.setVideoSize(JCMediaManager.instance().getVideoSize());
        coverImageView.setRotation(JCMediaManager.instance().videoRotation);
    }

    @Override
    public void startWindowFullscreen() {
        obtainCover();
        Log.i(TAG, "startWindowFullscreen " + " [" + this.hashCode() + "] ");
        if (!XClubJCUtils.haveNavigationBar()) {
            hideSupportActionBar(getContext());
        } else {
            XClubJCUtils.enterFullScreen((Activity) getContext());
        }

        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext())).findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(R.id.FULLSCREEN_ID);
        if (old != null) {
            vp.removeView(old);
        }
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        try {
            XClubVideoPlayer jcVideoPlayer = new XClubVideoPlayer(getContext());
            jcVideoPlayer.setId(R.id.FULLSCREEN_ID);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            vp.addView(jcVideoPlayer, lp);
            jcVideoPlayer.setUp(url, JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN, objects);
            jcVideoPlayer.setUiWitStateAndScreen(currentState);
            jcVideoPlayer.setTextAndProgress(JCMediaManager.instance().bufferPercent);
            jcVideoPlayer.addTextureView();

//            final Animation ra = AnimationUtils.loadAnimation(getContext(), R.anim.start_fullscreen);
//            jcVideoPlayer.setAnimation(ra);

            JCVideoPlayerManager.setLastListener(this);
            JCVideoPlayerManager.setListener(jcVideoPlayer);

        } catch (Exception e) {
            e.printStackTrace();
        }
        JCUtils.switchToFullOrientation(getContext());
        refreshCover();
    }

    @Override
    public boolean goToOtherListener() {
        obtainCover();
        boolean b = super.goToOtherListener();
        JCUtils.restoreScreenOrientation(getContext());
        refreshCover();
        return b;
    }

    @Override
    public void onCompletion() {
        clearCoverBitmap();
        super.onCompletion();
        JCUtils.restoreScreenOrientation(getContext());
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        super.onStopTrackingTouch(seekBar);
        startDismissControlViewTimer();
    }

    public void startPlayLogic() {
        prepareVideo();
        startDismissControlViewTimer();
        onEvent(JCBuriedPointStandard.ON_CLICK_START_THUMB);
    }

    public void onClickUiToggle() {
        if (currentState == CURRENT_STATE_PREPARING) {
            if (uiContainerShow()) {
                changeUiToPreparingClear();
            } else {
                changeUiToPreparingShow();
            }
        } else if (currentState == CURRENT_STATE_PLAYING) {
            if (uiContainerShow()) {
                changeUiToPlayingClear();
            } else {
                changeUiToPlayingShow();
            }
        } else if (currentState == CURRENT_STATE_PAUSE) {
            if (uiContainerShow()) {
                changeUiToPauseClear();
            } else {
                changeUiToPauseShow();
            }
        } else if (currentState == CURRENT_STATE_AUTO_COMPLETE) {
            if (uiContainerShow()) {
                changeUiToCompleteClear();
            } else {
                changeUiToCompleteShow();
            }
        } else if (currentState == CURRENT_STATE_PLAYING_BUFFERING_START) {
            if (uiContainerShow()) {
                changeUiToPlayingBufferingClear();
            } else {
                changeUiToPlayingBufferingShow();
            }
        }
    }

    private boolean uiContainerShow() {
        return bottomContainer.getVisibility() == View.VISIBLE;
    }

    @Override
    public void setProgressAndTime(int progress, int secProgress, int currentTime, int totalTime) {
        if (currentTime >= totalTime) {
            currentTime = totalTime;    //当前时间有可能大于总时长
        }
        super.setProgressAndTime(progress, secProgress, currentTime, totalTime);
        if (progress != 0) bottomProgressBar.setProgress(progress);
//        if (secProgress != 0) bottomProgressBar.setSecondaryProgress(secProgress);
    }

    @Override
    public void resetProgressAndTime() {
        super.resetProgressAndTime();
        bottomProgressBar.setProgress(0);
//        bottomProgressBar.setSecondaryProgress(0);
    }

    //Unified management Ui
    public void changeUiToNormal() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.VISIBLE, View.VISIBLE, View.INVISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.VISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.VISIBLE, View.VISIBLE, View.INVISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }
    }

    public void changeUiToPreparingShow() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.VISIBLE, View.INVISIBLE,
                        View.VISIBLE, View.INVISIBLE, View.VISIBLE, View.INVISIBLE,
                        View.INVISIBLE);

                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.VISIBLE, View.VISIBLE, View.INVISIBLE,
                        View.VISIBLE, View.INVISIBLE, View.VISIBLE, View.INVISIBLE,
                        View.INVISIBLE);
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }

    }

    public void changeUiToPreparingClear() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.VISIBLE, View.INVISIBLE, View.VISIBLE, View.INVISIBLE,
                        View.INVISIBLE);
                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.VISIBLE, View.INVISIBLE, View.VISIBLE, View.INVISIBLE,
                        View.INVISIBLE);
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }

    }

    public void changeUiToPlayingShow() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.VISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.INVISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.VISIBLE, View.VISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.INVISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }

    }

    public void changeUiToPlayingClear() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.VISIBLE,
                        View.INVISIBLE);
                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.VISIBLE,
                        View.INVISIBLE);
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }

    }

    public void changeUiToPauseShow() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.VISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.INVISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.VISIBLE, View.VISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.INVISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }

    }

    public void changeUiToPauseClear() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.INVISIBLE,
                        View.INVISIBLE);
                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.INVISIBLE,
                        View.INVISIBLE);
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }

    }

    public void changeUiToPlayingBufferingShow() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.VISIBLE, View.INVISIBLE,
                        View.VISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.INVISIBLE,
                        View.INVISIBLE);
                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.VISIBLE, View.VISIBLE, View.INVISIBLE,
                        View.VISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.INVISIBLE,
                        View.INVISIBLE);
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }

    }

    public void changeUiToPlayingBufferingClear() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.VISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.VISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.VISIBLE, View.INVISIBLE, coverImageView.getVisibility(), View.VISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }

    }

    public void changeUiToCompleteShow() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.VISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.VISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.VISIBLE, View.VISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.VISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }

    }

    public void changeUiToCompleteClear() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.VISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.VISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }

    }

    public void changeUiToError() {
        switch (currentScreen) {
            case SCREEN_LAYOUT_LIST:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.VISIBLE);
                updateErrorInfo();
                updateStartImage();
                break;
            case SCREEN_WINDOW_FULLSCREEN:
                setAllControlsVisible(View.INVISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.VISIBLE);
                updateErrorInfo();
                updateStartImage();
                break;
            case SCREEN_WINDOW_TINY:
                break;
        }

    }

    public void setAllControlsVisible(int topCon, int bottomCon, int startBtn, int loadingPro,
                                      int thumbImg, int coverImg, int bottomPro, int errTxt) {
        topContainer.setVisibility(topCon);
        bottomContainer.setVisibility(bottomCon);
        startButton.setVisibility(startBtn);
        loadingProgressBar.setVisibility(loadingPro);
        thumbImageView.setVisibility(thumbImg);
        coverImageView.setVisibility(coverImg);
        bottomProgressBar.setVisibility(bottomPro);
        errorTextView.setVisibility(errTxt);
    }

    public void updateStartImage() {
        if (currentState == CURRENT_STATE_PLAYING) {
            if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
                startButton.setImageResource(R.drawable.biz_click_pause_fullscreen_selector);
            } else {
                startButton.setImageResource(R.drawable.biz_click_pause_selector);
            }
        } else if (currentState == CURRENT_STATE_ERROR) {
            startButton.setImageBitmap(null);
        } else {
            if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
                startButton.setImageResource(R.drawable.biz_click_play_fullscreen_selector);
            } else {
                startButton.setImageResource(R.drawable.biz_click_play_selector);
            }
        }
    }

    public void updateErrorInfo() {
        String info;
        if (XClubJCUtils.isNetworkConnected(getContext())) {
            info = getContext().getString(R.string.tips_load_failed);
        } else {
            info = getContext().getString(R.string.tips_not_network);
        }
        errorTextView.setText(Html.fromHtml(info));
    }

    protected Dialog mProgressDialog;
    protected ProgressBar mDialogProgressBar;
    protected TextView mDialogSeekTime;
    protected TextView mDialogTotalTime;
    protected ImageView mDialogIcon;

    @Override
    public void showProgressDialog(float deltaX, String seekTime, int seekTimePosition, String totalTime, int totalTimeDuration) {
        super.showProgressDialog(deltaX, seekTime, seekTimePosition, totalTime, totalTimeDuration);
        if (mProgressDialog == null) {
            View localView = LayoutInflater.from(getContext()).inflate(R.layout.jc_progress_dialog, null);
            View content = localView.findViewById(R.id.content);
            content.setRotation(90);
            mDialogProgressBar = ((ProgressBar) localView.findViewById(R.id.duration_progressbar));
            mDialogSeekTime = ((TextView) localView.findViewById(R.id.tv_current));
            mDialogTotalTime = ((TextView) localView.findViewById(R.id.tv_duration));
            mDialogIcon = ((ImageView) localView.findViewById(R.id.duration_image_tip));
            mProgressDialog = new Dialog(getContext(), R.style.jc_style_dialog_progress);
            mProgressDialog.setContentView(localView);
            Window window = mProgressDialog.getWindow();
            if (window != null) {
                window.addFlags(Window.FEATURE_ACTION_BAR);
                window.addFlags(32);
                window.addFlags(16);
                window.setLayout(-2, -2);
                WindowManager.LayoutParams localLayoutParams = window.getAttributes();
                localLayoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                localLayoutParams.x = getResources().getDimensionPixelOffset(R.dimen.jc_progress_dialog_margin_top) / 2;
                window.setAttributes(localLayoutParams);
            }
        }
        if (!mProgressDialog.isShowing()) {
            mProgressDialog.show();
        }

        mDialogSeekTime.setText(seekTime);
        mDialogTotalTime.setText(" / " + totalTime);
        mDialogProgressBar.setProgress(totalTimeDuration <= 0 ? 0 : (seekTimePosition * 100 / totalTimeDuration));
        if (deltaX > 0) {
            mDialogIcon.setBackgroundResource(R.drawable.jc_forward_icon);
        } else {
            mDialogIcon.setBackgroundResource(R.drawable.jc_backward_icon);
        }

    }

    @Override
    public void dismissProgressDialog() {
        super.dismissProgressDialog();
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }


    protected Dialog mVolumeDialog;
    protected ProgressBar mDialogVolumeProgressBar;

    @Override
    public void showVolumeDialog(float deltaY, int volumePercent) {
        super.showVolumeDialog(deltaY, volumePercent);
        if (mVolumeDialog == null) {
            View localView = LayoutInflater.from(getContext()).inflate(R.layout.jc_volume_dialog, null);
            View content = localView.findViewById(R.id.content);
            content.setRotation(90);
            mDialogVolumeProgressBar = ((ProgressBar) localView.findViewById(R.id.volume_progressbar));
            mVolumeDialog = new Dialog(getContext(), R.style.jc_style_dialog_progress);
            mVolumeDialog.setContentView(localView);
            Window window = mVolumeDialog.getWindow();
            if (window != null) {
                window.addFlags(8);
                window.addFlags(32);
                window.addFlags(16);
                window.setLayout(-2, -2);
                WindowManager.LayoutParams localLayoutParams = window.getAttributes();
                localLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
//            localLayoutParams.y = getContext().getResources().getDimensionPixelOffset(R.dimen.jc_volume_dialog_margin_left);
                window.setAttributes(localLayoutParams);
            }
        }
        if (!mVolumeDialog.isShowing()) {
            mVolumeDialog.show();
        }

        mDialogVolumeProgressBar.setProgress(volumePercent);
    }

    @Override
    public void dismissVolumeDialog() {
        super.dismissVolumeDialog();
        if (mVolumeDialog != null) {
            mVolumeDialog.dismiss();
        }
    }

    public void startDismissControlViewTimer() {
        cancelDismissControlViewTimer();
        DISMISS_CONTROL_VIEW_TIMER = new Timer();
        mDismissControlViewTimerTask = new DismissControlViewTimerTask();
        DISMISS_CONTROL_VIEW_TIMER.schedule(mDismissControlViewTimerTask, 2500);
    }

    public void cancelDismissControlViewTimer() {
        if (DISMISS_CONTROL_VIEW_TIMER != null) {
            DISMISS_CONTROL_VIEW_TIMER.cancel();
        }
        if (mDismissControlViewTimerTask != null) {
            mDismissControlViewTimerTask.cancel();
        }

    }

    public class DismissControlViewTimerTask extends TimerTask {

        @Override
        public void run() {
            if (currentState != CURRENT_STATE_NORMAL
                    && currentState != CURRENT_STATE_ERROR
                    && currentState != CURRENT_STATE_AUTO_COMPLETE
                    && currentState != CURRENT_STATE_PLAYING_BUFFERING_START) {
                if (getContext() != null && getContext() instanceof Activity) {
                    ((Activity) getContext()).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            bottomContainer.setVisibility(View.INVISIBLE);
                            topContainer.setVisibility(View.INVISIBLE);
                            startButton.setVisibility(View.INVISIBLE);
                            if (currentScreen != SCREEN_WINDOW_TINY) {
                                bottomProgressBar.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }
            }
        }
    }

    /**
     * 调节音量和屏幕亮度
     *
     * @param v
     * @param event
     * @return false
     */
    private boolean adjustVolumeBrightness(View v, MotionEvent event) {
        if (currentState != CURRENT_STATE_PLAYING
                && currentState != CURRENT_STATE_PAUSE
                && currentState != CURRENT_STATE_PLAYING_BUFFERING_START) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mAdjust = false;
                mY = event.getY();
                mX = event.getX();
                mDistanceY = 0;
                mDistanceX = 0;
                float x = event.getX();    //相对容器x坐标
                if (x <= getWidth() / 2) {
                    //调节亮度
                    isVolume = false;
                } else {
                    //调节音量
                    isVolume = true;
                }
                float y = event.getY();
                if (y <= getHeight() / 4 * 3 && y >= getHeight() / 4) { //是否在调节范围内
                    mAdjustScope = true;
                } else {
                    mAdjustScope = false;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mAdjustScope) {
                    break;
                }
                if (currentScreen != SCREEN_WINDOW_FULLSCREEN) {      //非大屏禁止调节
                    break;
                }
                mDistanceY += event.getY() - mY;
                mDistanceX += event.getX() - mX;
                mY = event.getY();
                mX = event.getX();
                if (Math.abs(mDistanceX) > Math.abs(mDistanceY) / 2) {
                    mDistanceY = 0;
                    mDistanceX = 0;
                    break;
                }
                if (mDistanceY <= -8) {     //以10为最小单位距离来改变亮度（音量）值
                    mDistanceY = 0;
                    mDistanceX = 0;
                    if (!isVolume) {
                        //提高亮度
                        updateBrightness(true);
                    } else {
                        //提高音量
                        updateVolume(AudioManager.ADJUST_RAISE, getContext());
                    }
                    centerController.setVisibility(GONE);
                    mAdjust = true;
                } else if (mDistanceY >= 8) {
                    mDistanceY = 0;
                    mDistanceX = 0;
                    if (!isVolume) {
                        //降低亮度
                        updateBrightness(false);
                    } else {
                        //降低音量
                        updateVolume(AudioManager.ADJUST_LOWER, getContext());
                    }
                    centerController.setVisibility(GONE);
                    mAdjust = true;
                }
                break;
            case MotionEvent.ACTION_UP:
                layout_brightness.setVisibility(GONE);
                layout_volume.setVisibility(GONE);
                centerController.setVisibility(VISIBLE);
                break;
        }
        return mAdjust;    //是否消费事件
    }

    /**
     * 更改当前屏幕亮度，每次调节±5
     *
     * @param isRaise boolean
     */
    private void updateBrightness(boolean isRaise) {
        Activity activity = (Activity) getContext();
        int screenBrightness;
        if (layout_brightness.getVisibility() == GONE) {
            layout_brightness.setVisibility(VISIBLE);
        }
        if (isRaise) {
            screenBrightness = XClubJCUtils.getScreenBrightness(activity, 255 / 2) + 5;
        } else {
            screenBrightness = XClubJCUtils.getScreenBrightness(activity, 255 / 2) - 5;
        }
        XClubJCUtils.setScreenBrightness(activity, screenBrightness);
        if (screenBrightness < 0) screenBrightness = 0;
        if (screenBrightness > 255) screenBrightness = 255;
        progress_brightness.setProgress(screenBrightness / 255.0f);
    }

    /**
     * 调节音量
     *
     * @param direction int
     * @param context   Context
     */
    private void updateVolume(int direction, Context context) {
        float progress = progress_volume.getProgress();
        if (progress == -1) {
            progress = XClubJCUtils.getCurVolume(context) * 1f / XClubJCUtils.getMaxVolume(context);
        }
        switch (direction) {
            case AudioManager.ADJUST_LOWER:
                if (adjustVolume > 0) adjustVolume = 0;
                adjustVolume--;
                progress -= 1f / (XClubJCUtils.getMaxVolume(context) * 4);
                break;
            case AudioManager.ADJUST_RAISE:
                if (adjustVolume < 0) adjustVolume = 0;
                adjustVolume++;
                progress += 1f / (XClubJCUtils.getMaxVolume(context) * 4);
                break;
        }
        if (progress > 1) progress = 1;
        if (progress < 0) progress = 0;
        progress_volume.setProgress(progress);
        if (adjustVolume == 4 || adjustVolume == -4) {
            adjustVolume = 0;
            XClubJCUtils.setVolume(context, direction);
        }
        if (layout_volume.getVisibility() == View.GONE) {
            layout_volume.setVisibility(VISIBLE);
        }
        img_volume.setVisibility(progress != 0 ? VISIBLE : GONE);
        img_volume_ban.setVisibility(progress == 0 ? VISIBLE : GONE);
    }

    /**
     * 视频引导页
     *
     * @param screen int
     */
    private void initAdjustGuide(int screen) {
        if (screen == SCREEN_WINDOW_FULLSCREEN) {
            if (XClubJCUtils.needShowGuide(getContext())) {
                JCMediaManager.instance().mediaPlayer.pause();
                final View view = findViewById(R.id.relativeLayout_adjust);
                view.setVisibility(View.VISIBLE);
                findViewById(R.id.adjust_i_know).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        view.setVisibility(View.GONE);
                        XClubJCUtils.noLongerShowGuide(getContext());
                        if (mLastListState != CURRENT_STATE_PAUSE) {
                            onEvent(JCBuriedPoint.ON_CLICK_RESUME);
                            JCMediaManager.instance().mediaPlayer.start();
                            setUiWitStateAndScreen(CURRENT_STATE_PLAYING);
                        }
                    }
                });
            }
        }
    }

    /**
     * 保存小屏的播放状态（切换到全屏时）
     */
    private void saveLastListState() {
        if (XClubJCUtils.needShowGuide(getContext())) {
            mLastListState = currentState;
            currentState = CURRENT_STATE_PAUSE;
        }
    }

    /**
     * 重写onPrepared是为了优化引导页显示后无法暂停问题
     */
    @Override
    public void onPrepared() {
        super.onPrepared();
        if (currentScreen == SCREEN_WINDOW_FULLSCREEN && XClubJCUtils.needShowGuide(getContext())) {
            JCMediaManager.instance().mediaPlayer.pause();
            setUiWitStateAndScreen(CURRENT_STATE_PAUSE);
        }
    }

    /**
     * 切换到全屏时隐藏输入法
     *
     * @param view View
     */
    private void hideSoftInput(View view) {
        InputMethodManager im = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        im.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @Override
    public void showSupportActionBar(Context context) {
        if (XClubJCUtils.haveNavigationBar()) {
            try {
                XClubJCUtils.exitFullScreen((Activity) context);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else {
            super.showSupportActionBar(context);
        }
    }

    /**
     * 采用另一种规避方案，此方法暂时注释掉
     * <p>
     * “播放-暂停-播放”的状态下，JCMediaManager.instance().mediaPlayer.getCurrentPosition()返回的值存在问题，
     * 重写此方法是为了规避此问题.重写{@link #onSeekComplete()}使拖动进度条不受此方法的影响.
     * </p>
     * <p>
     * 1.暂停时，记录下当前值，这个值是固定的.
     * 2.点击继续播放时，数值有可能虚高(判断>300,这里判断不严谨，有可能造成bug)，摒弃这个值，用替代.
     * 3.数值下降恢复正常时，直接返回正常值.
     * </p>
     *
     * @return int
     */
//    @Override
//    public int getCurrentPositionWhenPlaying() {
//        int curTime = super.getCurrentPositionWhenPlaying();
//        if (currentState == CURRENT_STATE_PAUSE) {
//            mPauseTime = curTime;
//        } else {
//            if (curTime - mPauseTime > 300 && mPauseTime != 0) {       //暂停，继续点击播放后数值虚高，（偶发，视频格式决定）
//                if (mVirtualTime != 0 && mVirtualTime - curTime > 0) {
//                    mPauseTime = mVirtualTime = 0;                     //当前播放时间陡增几次后下降回归正常值
//                } else {
//                    mVirtualTime = curTime;                            //数值虚高时用暂停时的值替代
//                    curTime = mPauseTime;
//                }
//            } else {
//                mPauseTime = mVirtualTime = 0;
//            }
//        }
//        return curTime;
//    }
    @Override
    public void onSeekComplete() {
        super.onSeekComplete();
//        mPauseTime = mVirtualTime = 0;
    }

    @Override
    public void onError(int what, int extra) {
        if (what != 38 && what != -38) {
            clearCoverBitmap();
        }
        super.onError(what, extra);
    }

    private void clearCoverBitmap() {
        pauseSwitchCoverBitmap = null;
        coverImageView.setImageBitmap(null);
        coverImageView.setVisibility(INVISIBLE);
    }

    @Override
    public void orientationChange(int orientation) {
        super.orientationChange(orientation);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) titleLinearLayout.getLayoutParams();
        int dimen = getResources().getDimensionPixelSize(R.dimen.video_title_margin_right);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            params.setMarginEnd(dimen);
        }
        params.rightMargin = dimen;
        titleLinearLayout.setLayoutParams(params);
    }
}
