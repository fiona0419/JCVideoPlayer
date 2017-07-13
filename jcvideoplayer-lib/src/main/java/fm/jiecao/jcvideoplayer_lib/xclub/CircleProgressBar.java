package fm.jiecao.jcvideoplayer_lib.xclub;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import fm.jiecao.jcvideoplayer_lib.R;

/**
 * <p>
 * the progressBar of adjust volume or brightness.
 * </p>
 * Created by fuqiang.zhong on 2016/7/19.
 */
public class CircleProgressBar extends View {

    private Paint p;
    private float r = 0;
    private float w = 0;
    private float h = 0;
    private final static float END_ANGLE = 270;     //进度条终点，以270°为起点（固定）
    private float angle = 270;                      //进度条长度
    private int color;                              //进度条颜色
    private float width;                            //进度条宽度

    private float mProgress = -1;

    public CircleProgressBar(Context context) {
        this(context, null);
    }

    public CircleProgressBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.CircleProgressBar);
        color = typedArray.getColor(R.styleable.CircleProgressBar_progress_color, 0x0EC066);
        width = typedArray.getDimension(R.styleable.CircleProgressBar_progress_width, 4.0f);
        Drawable drawable = typedArray.getDrawable(R.styleable.CircleProgressBar_progress_src);
        typedArray.recycle();

        if (drawable != null) {
            h = drawable.getIntrinsicHeight();
            w = drawable.getIntrinsicWidth();
        }

        p = new Paint();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (r == 0) {
            r = h == 0 && w == 0 ? getHeight() > getWidth() ? getWidth() / 2 : getHeight() / 2 : h > w ? w / 2 : h / 2;
        }
        canvas.drawArc(width / 2, width / 2, 2 * r - width / 2, 2 * r - width / 2, END_ANGLE - angle, angle, false, p);
    }

    /**
     * 固定大小
     *
     * @param widthMeasureSpec  w
     * @param heightMeasureSpec h
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (h != 0) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec((int) w, MeasureSpec.EXACTLY);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec((int) h, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * 设置进度
     *
     * @param progress 0.0~1.0 （0%~100%）
     */
    public void setProgress(float progress) {
        this.mProgress = progress;
        if (mProgress >= 0 && mProgress <= 1) {
            this.angle = 360 * mProgress;
        } else {
            throw new IllegalArgumentException("the scope of progress need 0~1");
        }
        invalidate();
    }

    /**
     * 获取当前进度
     *
     * @return float
     */
    public float getProgress() {
        return mProgress;
    }
}
