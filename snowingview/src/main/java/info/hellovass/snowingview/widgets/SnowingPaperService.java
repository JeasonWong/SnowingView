package info.hellovass.snowingview.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.util.concurrent.TimeUnit;

import info.hellovass.snowingview.R;
import info.hellovass.snowingview.utils.DensityUtil;
import info.hellovass.snowingview.utils.RandomUtil;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.subscriptions.Subscriptions;

/**
 * 作者： 巴掌 on 16/9/30 13:41
 * Github: https://github.com/JeasonWong
 */

public class SnowingPaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new SnowingEngine();
    }

    public class SnowingEngine extends Engine implements SensorEventListener {

        Subscription mLooper = Subscriptions.empty();

        private boolean isVisible;

        private final static long INVALID_TIME = -1;

        private final static int DEFAULT_SNOWFLAKE_COUNT = 10;

        private final static int MSG_CALCULATE = 233;

        private final static int LOW_VELOCITY_Y = 150;

        private final static int HIGH_VELOCITY_Y = 2 * LOW_VELOCITY_Y;

        private final static float GRAVITATIONAL_ACCELERATION = 9.81F;

        private final static float MIN_OFFSET_X = 15.0F;

        private final static float MAX_OFFSET_X = 20.0F;

        private Context mContext;

        private int mWidth;

        private int mHeight;

        private HandlerThread mCalculatePositionThread;

        private Handler mCalculateHandler;

        private float mSnowFlakeBitmapPivotX;

        private float mSnowFlakeBitmapPivotY;

        private Bitmap mSnowFlakeBitmap;

        private long mLastTimeMillis = INVALID_TIME;

        private Matrix mSnowFlakeMatrix;

        private Paint mSnowFlakePaint;

        private SnowFlake[] mSnowFlakes;

        private SensorManager mSensorManager;

        private Sensor mAccelerometerSensor;

        private float mAccelerationXPercentage;

        private int mLowVelocityY;
        private int mHighVelocityY;
        private int mMinOffsetY;
        private int mMaxOffsetY;

        private SurfaceHolder mHolder;

        public SnowingEngine() {
            super();
            mContext = getApplicationContext();
            mHolder = getSurfaceHolder();
            mSnowFlakeBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_snowflake);
            mSnowFlakeBitmapPivotX = mSnowFlakeBitmap.getWidth() / 2.0F;
            mSnowFlakeBitmapPivotY = mSnowFlakeBitmap.getHeight() / 2.0F;
            mLowVelocityY = DensityUtil.dip2px(mContext, LOW_VELOCITY_Y);
            mHighVelocityY = DensityUtil.dip2px(mContext, HIGH_VELOCITY_Y);
            mMinOffsetY = DensityUtil.dip2px(mContext, MIN_OFFSET_X);
            mMaxOffsetY = DensityUtil.dip2px(mContext, MAX_OFFSET_X);
            initSensorManager();
            initCalculateThread();
            initCalculateHandler();
            initSnowFlakeMatrix();
            initSnowFlakePaint();

        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            createSnowFlakes();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            isVisible = visible;
            if (visible) {
                startFall();
            } else {
                stopFall();
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            stopFall();
        }

        /**
         * 初始化传感器
         */
        private void initSensorManager() {
            mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        /**
         * 初始化雪花矩阵
         */
        private void initSnowFlakeMatrix() {
            mSnowFlakeMatrix = new Matrix();
        }

        /**
         * 初始化雪花画笔
         */
        private void initSnowFlakePaint() {
            mSnowFlakePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }

        /**
         * 初始化工作线程
         */
        private void initCalculateThread() {
            mCalculatePositionThread = new HandlerThread("calculate_thread");
            mCalculatePositionThread.start();
        }

        /**
         * 初始化Handler
         */
        private void initCalculateHandler() {

            mCalculateHandler = new Handler(mCalculatePositionThread.getLooper()) {

                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);

                    long currentTimeMillis = System.currentTimeMillis();

                    if (mLastTimeMillis != INVALID_TIME) {

                        float deltaTime = (currentTimeMillis - mLastTimeMillis) / 1000.0F;

                        for (SnowFlake snowFlake : mSnowFlakes) {

                            float x = snowFlake.getPositionX() + randomOffsetX();
                            float y = snowFlake.getPositionY() + snowFlake.getVelocityY() * deltaTime;

                            if (outOfRange(x, y)) {
                                snowFlake.setPositionX(randomPositionX());
                                snowFlake.setPositionY(resetPositionY());
                            } else {
                                snowFlake.setPositionX(x);
                                snowFlake.setPositionY(y);
                            }
                        }
                    }

                    mLastTimeMillis = currentTimeMillis;
                }
            };
        }

        /**
         * 创建雪花数组
         */
        private void createSnowFlakes() {

            mSnowFlakes = new SnowFlake[DEFAULT_SNOWFLAKE_COUNT];

            for (int index = 0; index < mSnowFlakes.length; index++) {

                SnowFlake snowFlake = new SnowFlake.Builder().setPositionX(randomPositionX())
                        .setPositionY(randomPositionY())
                        .setVelocityY(randomVelocityY())
                        .setTransparency(randomTransparency())
                        .setScale(randomScale())
                        .create();

                mSnowFlakes[index] = snowFlake;
            }
        }

        /**
         * 随机可能的X坐标
         *
         * @return 雪花的X坐标
         */
        private float randomPositionX() {
            return RandomUtil.nextFloat(mWidth + 2 * mSnowFlakeBitmap.getWidth())
                    - mSnowFlakeBitmap.getWidth();
        }

        /**
         * 随机可能的Y坐标
         *
         * @return 雪花的Y坐标
         */
        private float randomPositionY() {
            return RandomUtil.nextFloat(mHeight + 2 * mSnowFlakeBitmap.getHeight())
                    - mSnowFlakeBitmap.getHeight();
        }

        /**
         * 将雪花的Y坐标重置
         *
         * @return 雪花的Y坐标
         */
        private float resetPositionY() {
            return -mSnowFlakeBitmap.getHeight();
        }

        /**
         * 随机雪花在Y轴方向上的速度(2dp/s-4dp/s)
         *
         * @return y轴方向上的速度
         */
        private float randomVelocityY() {

            return RandomUtil.nextFloat(mLowVelocityY, mHighVelocityY);
        }

        /**
         * 随机雪花的透明度
         *
         * @return 雪花的透明度
         */
        private int randomTransparency() {
            return RandomUtil.nextInt(10, 255) << 24;
        }

        /**
         * 随机雪花的缩放比例
         *
         * @return 雪花的缩放比
         */
        private float randomScale() {
            return RandomUtil.nextFloat(0.5F, 2.0F);
        }

        /**
         * 随机X轴的偏移量
         *
         * @return x轴上的偏移量
         */
        private float randomOffsetX() {
            return RandomUtil.nextFloat(mMinOffsetY, mMaxOffsetY) * -mAccelerationXPercentage;
        }

        /**
         * 是否超出View的范围
         *
         * @return true表示超出范围
         */
        private boolean outOfRange(float x, float y) {

            if (x < -mSnowFlakeBitmap.getWidth() || x > mWidth + mSnowFlakeBitmap.getWidth()) {
                return true;
            }

            if (y > mHeight + mSnowFlakeBitmap.getHeight()) {
                return true;
            }

            return false;
        }

        private void draw() {

            Canvas canvas = null;
            try {
                canvas = mHolder.lockCanvas();
                if (canvas != null) {

                    canvas.drawColor(Color.BLACK);

                    for (SnowFlake snowFlake : mSnowFlakes) {
                        mSnowFlakeMatrix.setTranslate(0, 0);
                        mSnowFlakeMatrix.postScale(snowFlake.getScale(), snowFlake.getScale(), mSnowFlakeBitmapPivotX,
                                mSnowFlakeBitmapPivotY);
                        mSnowFlakeMatrix.postTranslate(snowFlake.getPositionX(), snowFlake.getPositionY());
                        mSnowFlakePaint.setColor(snowFlake.getTransparency());
                        canvas.drawBitmap(mSnowFlakeBitmap, mSnowFlakeMatrix, mSnowFlakePaint);
                    }

                    mCalculateHandler.sendEmptyMessage(MSG_CALCULATE);
                }
            } finally {
                if (canvas != null) {
                    mHolder.unlockCanvasAndPost(canvas);
                }
            }

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            float accelerationX = event.values[SensorManager.DATA_X];
            mAccelerationXPercentage = accelerationX / GRAVITATIONAL_ACCELERATION;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        /**
         * 通知HandlerThread停止执行
         */
        private void notifyCalculateThreadStop() {
            mCalculateHandler.removeMessages(MSG_CALCULATE);
        }

        private void startLooper() {
            mLooper.unsubscribe();
            mLooper = Observable.interval(12, TimeUnit.MILLISECONDS)
                    .subscribe(new Action1<Long>() {
                        @Override
                        public void call(Long aLong) {
                            if (isVisible) {
                                draw();
                            }
                        }
                    });
        }

        private void stopLooper() {
            mLooper.unsubscribe();
        }

        /**
         * 开始下雪动画
         */
        public void startFall() {
            mSensorManager.registerListener(this, mAccelerometerSensor, SensorManager.SENSOR_DELAY_GAME);
            startLooper();
        }

        /**
         * 停止下雪动画
         */
        public void stopFall() {
            mSensorManager.unregisterListener(this);
            notifyCalculateThreadStop();
            stopLooper();
        }

    }

}
