package com.chenjishi.u148.activity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.RelativeLayout;
import com.chenjishi.u148.R;
import com.chenjishi.u148.base.FileCache;
import com.chenjishi.u148.sina.RequestListener;
import com.chenjishi.u148.util.Utils;
import com.chenjishi.u148.util.Constants;
import com.chenjishi.u148.util.HttpUtils;
import com.chenjishi.u148.util.ShareUtils;
import com.chenjishi.u148.util.Utils;
import com.chenjishi.u148.view.ShareDialog;
import com.chenjishi.u148.view.TouchImageView;
import com.chenjishi.u148.volley.VolleyError;
import com.chenjishi.u148.volley.toolbox.ImageLoader;
import com.flurry.android.FlurryAgent;
import com.sina.weibo.sdk.exception.WeiboException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: chenjishi
 * Date: 13-6-23
 * Time: 上午11:30
 * To change this template use File | Settings | File Templates.
 */
public class ImageActivity extends BaseActivity implements GestureDetector.OnGestureListener,
        ViewPager.OnPageChangeListener, ShareDialog.OnShareListener {
    private ArrayList<String> mImageList = new ArrayList<String>();

    private int mCurrentIndex;
    private GestureDetector mGestureDetector;

    private ViewPager mViewPager;
    private RelativeLayout mToolBar;

    private ImageLoader imageLoader;

    private boolean showToolBar = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int sdk_int = Build.VERSION.SDK_INT;
        if (sdk_int < 16) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            View decorView = getWindow().getDecorView();

            if (sdk_int >= 19) {
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE);
            } else {
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
            }
        }

        setContentView(R.layout.photo_layout, true);

        Bundle bundle = getIntent().getExtras();
        if (null == bundle) return;

        mImageList = bundle.getStringArrayList("images");
        String currentUrl = bundle.getString("imgsrc");

        for (int i = 0; i < mImageList.size(); i++) {
            if (mImageList.get(i).equals(currentUrl)) {
                mCurrentIndex = i;
                break;
            }
        }

        mGestureDetector = new GestureDetector(this, this);
        imageLoader = HttpUtils.getImageLoader();

        mToolBar = (RelativeLayout) findViewById(R.id.tool_bar);
        mViewPager = (ViewPager) findViewById(R.id.pager_photo);
        mViewPager.setAdapter(new PhotoPagerAdapter(this));
        mViewPager.setOnPageChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mViewPager.setCurrentItem(mCurrentIndex);
    }

    public void onCloseButtonClicked(View v) {
        finish();
    }

    public void onDownloadButtonClicked(View v) {
        saveImage();
    }

    private ShareDialog shareDialog;

    public void onShareButtonClicked(View v) {
        if (null == shareDialog) {
            shareDialog = new ShareDialog(this, this);
        }

        shareDialog.show();
    }

    @Override
    public void onShare(final int type) {
        final String url = null != mImageList ? mImageList.get(mCurrentIndex) : "";

        HashMap<String, String> params = new HashMap<String, String>();
        params.put(Constants.PARAM_URL, url);
        FlurryAgent.logEvent(Constants.EVENT_IMAGE_SHARE, params);

        if (!TextUtils.isEmpty(url)) {
            imageLoader.get(url, new ImageLoader.ImageListener() {
                @Override
                public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                    Bitmap bitmap = response.getBitmap();
                    if (null != bitmap) {
                        String path = FileCache.getTempCacheDir();
                        if (!new File(path).exists()) FileCache.mkDirs(path);

                        try {
                            File imageFile = new File(path, "temp");
                            imageFile.createNewFile();

                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);

                            byte[] bitmapdata = bos.toByteArray();
                            FileOutputStream fos = new FileOutputStream(imageFile);
                            fos.write(bitmapdata);
                        } catch (IOException e) {
                        }

                        if (type == ShareUtils.SHARE_WEIBO) {
                            String filePath = path + "temp";
                            shareToWeibo(filePath);
                        } else {
                            ShareUtils.shareImage(ImageActivity.this, url, type, bitmap);
                        }
                    } else {
                        Utils.showToast(R.string.share_error);
                    }
                }

                @Override
                public void onErrorResponse(VolleyError error) {
                    Utils.showToast(R.string.share_error);
                }
            });
        } else {
            Utils.showToast(R.string.share_error);
        }

        shareDialog.dismiss();
    }

    private void shareToWeibo(String imagePath) {
        ShareUtils.shareToWeibo(this, getString(R.string.share_image_tip), imagePath, null, new RequestListener() {
            @Override
            public void onComplete(String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Utils.showToast(R.string.share_success);
                    }
                });
            }

            @Override
            public void onComplete4binary(ByteArrayOutputStream responseOS) {

            }

            @Override
            public void onIOException(IOException e) {

            }

            @Override
            public void onError(WeiboException e) {

            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        super.dispatchTouchEvent(ev);
        return mGestureDetector.onTouchEvent(ev);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        showToolBar = !showToolBar;
        mToolBar.setVisibility(showToolBar ? View.VISIBLE : View.GONE);
        float height = getResources().getDimension(R.dimen.action_bar_height);
        float startY = showToolBar ? 0 : height;
        float endY = showToolBar ? height : 0;
        Animation animation = new TranslateAnimation(0, 0, startY, endY);
        animation.setDuration(400);
        animation.setFillAfter(true);
        mToolBar.clearAnimation();
        mToolBar.startAnimation(animation);
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public void onPageScrolled(int i, float v, int i2) {
    }

    @Override
    public void onPageSelected(int i) {
        mCurrentIndex = i;
    }

    @Override
    public void onPageScrollStateChanged(int i) {
    }

    private void saveImage() {
        final String imageUrl = mImageList.get(mCurrentIndex);
        if (TextUtils.isEmpty(imageUrl)) return;

        imageLoader.get(imageUrl, new ImageLoader.ImageListener() {
            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                String picUrl = null;
                Bitmap bitmap = response.getBitmap();

                if (null != bitmap) {
                    String name = System.currentTimeMillis() + ".jpg";
                    ContentResolver cr = ImageActivity.this.getContentResolver();
                    picUrl = MediaStore.Images.Media.insertImage(cr, bitmap, name, "Image Saved From U148");

                    if (!TextUtils.isEmpty(picUrl)) {
                        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        String imagePath = getFilePathByContentResolver(Uri.parse(picUrl));
                        Uri uri = Uri.fromFile(new File(imagePath));
                        intent.setData(uri);
                        ImageActivity.this.sendBroadcast(intent);
                    }
                }

                Utils.showToast(getString(TextUtils.isEmpty(picUrl) ?
                        R.string.image_save_fail : R.string.image_save_success));
            }

            @Override
            public void onErrorResponse(VolleyError error) {
                Utils.showToast(getString(R.string.image_save_fail));
            }
        });
    }

    private String getFilePathByContentResolver(Uri uri) {
        if (null == uri) return null;

        Cursor c = getContentResolver().query(uri, null, null, null, null);
        String filePath = null;
        if (null == c) {
            throw new IllegalArgumentException(
                    "Query on " + uri + " returns null result.");
        }
        try {
            if ((c.getCount() != 1) || !c.moveToFirst()) {
            } else {
                filePath = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA));
            }
        } finally {
            c.close();
        }
        return filePath;
    }

    class PhotoPagerAdapter extends PagerAdapter {
        private LayoutInflater inflater;

        public PhotoPagerAdapter(Context context) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return mImageList.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View pageView = inflater.inflate(R.layout.photo_item, null);

            final TouchImageView imageView = (TouchImageView) pageView.findViewById(R.id.img_photo);

            imageLoader.get(mImageList.get(position),
                    ImageLoader.getImageListener(imageView, R.drawable.gray, R.drawable.gray));

            container.addView(pageView);
            return pageView;
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return view == o;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }
    }
}
