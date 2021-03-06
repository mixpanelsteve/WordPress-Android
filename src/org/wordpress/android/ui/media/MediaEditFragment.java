package org.wordpress.android.ui.media;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.models.Blog;
import org.wordpress.android.util.ImageHelper.BitmapWorkerCallback;
import org.wordpress.android.util.ImageHelper.BitmapWorkerTask;
import org.wordpress.android.util.MediaUtils;
import org.xmlrpc.android.ApiHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A fragment for editing media on the Media tab
 */
public class MediaEditFragment extends SherlockFragment {

    private static final String ARGS_MEDIA_ID = "media_id";
    public static final String TAG = "MediaEditFragment"; // also appears in the layouts, from the strings.xml

    private NetworkImageView mNetworkImageView;
    private ImageView mLocalImageView;
    private EditText mTitleView;
    private EditText mCaptionView;
    private EditText mDescriptionView;
    private Button mSaveButton;

    private MediaEditFragmentCallback mCallback;

    private boolean mIsMediaUpdating = false;

    private String mMediaId;
    private ScrollView mScrollView;
    private View mLinearLayout;
    private ImageLoader mImageLoader;

    public interface MediaEditFragmentCallback {
        public void onResume(Fragment fragment);
        public void onPause(Fragment fragment);
        public void onSavedEdit(String mediaId, boolean result);
    }

    public static MediaEditFragment newInstance(String mediaId) {
        MediaEditFragment fragment = new MediaEditFragment();

        Bundle args = new Bundle();
        args.putString(ARGS_MEDIA_ID, mediaId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mImageLoader = MediaImageLoader.getInstance();

        // retain this fragment across configuration changes
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mCallback = (MediaEditFragmentCallback) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement " + MediaEditFragmentCallback.class.getSimpleName());
        }
    }


    @Override
    public void onDetach() {
        super.onDetach();
        // set callback to null so we don't accidentally leak the activity instance
        mCallback = null;
    }

    private boolean hasCallback() {
        return (mCallback != null);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasCallback())
            mCallback.onResume(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (hasCallback())
            mCallback.onPause(this);
    }

    public String getMediaId() {
        if (mMediaId != null) {
            return mMediaId;
        } else if (getArguments() != null) {
            mMediaId = getArguments().getString(ARGS_MEDIA_ID);
            return mMediaId;
        } else {
            return null;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mScrollView = (ScrollView) inflater.inflate(R.layout.media_edit_fragment, container, false);

        mLinearLayout = mScrollView.findViewById(R.id.media_edit_linear_layout);
        mTitleView = (EditText) mScrollView.findViewById(R.id.media_edit_fragment_title);
        mCaptionView = (EditText) mScrollView.findViewById(R.id.media_edit_fragment_caption);
        mDescriptionView = (EditText) mScrollView.findViewById(R.id.media_edit_fragment_description);
        mLocalImageView = (ImageView) mScrollView.findViewById(R.id.media_edit_fragment_image_local);
        mNetworkImageView = (NetworkImageView) mScrollView.findViewById(R.id.media_edit_fragment_image_network);
        mSaveButton = (Button) mScrollView.findViewById(R.id.media_edit_save_button);
        mSaveButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                editMedia();
            }
        });

        disableEditingOnOldVersion();

        loadMedia(getMediaId());

        return mScrollView;
    }

    private void disableEditingOnOldVersion() {

        if( MediaUtils.isWordPressVersionWithMediaEditingCapabilities() )
            return;

        mSaveButton.setEnabled(false);
        mTitleView.setEnabled(false);
        mCaptionView.setEnabled(false);
        mDescriptionView.setEnabled(false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
     }

    public void loadMedia(String mediaId) {
        mMediaId = mediaId;
        Blog blog = WordPress.getCurrentBlog();

        if (blog != null && getActivity() != null) {
            String blogId = String.valueOf(blog.getLocalTableBlogId());

            if (mMediaId != null) {
                Cursor cursor = WordPress.wpDB.getMediaFile(blogId, mMediaId);
                refreshViews(cursor);
                cursor.close();
            } else {
                refreshViews(null);
            }

        }
    }

    void hideKeyboard() {
        if (getActivity() != null) {
            InputMethodManager inputManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    void editMedia() {
        hideKeyboard();

        final String mediaId = this.getMediaId();
        final String title = mTitleView.getText().toString();
        final String description = mDescriptionView.getText().toString();
        final Blog currentBlog = WordPress.getCurrentBlog();
        final String caption = mCaptionView.getText().toString();

        ApiHelper.EditMediaItemTask task = new ApiHelper.EditMediaItemTask(mediaId, title,
                description, caption,
                new ApiHelper.GenericCallback() {
                    @Override
                    public void onSuccess() {
                        String blogId = String.valueOf(currentBlog.getLocalTableBlogId());
                        WordPress.wpDB.updateMediaFile(blogId, mediaId, title, description,
                                caption);
                        if (getActivity() != null) {
                            Toast.makeText(getActivity(), R.string.media_edit_success, Toast.LENGTH_LONG).show();
                        }
                        setMediaUpdating(false);
                        if (hasCallback()) {
                            mCallback.onSavedEdit(mediaId, true);
                        }
                    }

                    @Override
                    public void onFailure(ApiHelper.ErrorType errorType, String errorMessage, Throwable throwable) {
                        if (getActivity() != null) {
                            Toast.makeText(getActivity(), R.string.media_edit_failure,
                                    Toast.LENGTH_LONG).show();
                        }
                        setMediaUpdating(false);
                        getSherlockActivity().invalidateOptionsMenu();
                        if (hasCallback()) {
                            mCallback.onSavedEdit(mediaId, false);
                        }
                    }
                });

        List<Object> apiArgs = new ArrayList<Object>();
        apiArgs.add(currentBlog);

        if (!isMediaUpdating()) {
            setMediaUpdating(true);
            task.execute(apiArgs);
        }

    }

    private void setMediaUpdating(boolean isUpdating) {
        mIsMediaUpdating = isUpdating;
        mSaveButton.setEnabled(!isUpdating);

        if (isUpdating) {
            mSaveButton.setText("Saving..");
        } else {
            mSaveButton.setText(R.string.save);
        }
    }

    private boolean isMediaUpdating() {
        return mIsMediaUpdating;
    }

    private void refreshViews(Cursor cursor) {
        if (cursor == null || !cursor.moveToFirst() || cursor.getCount() == 0) {
            mLinearLayout.setVisibility(View.GONE);
            return;
        }

        mLinearLayout.setVisibility(View.VISIBLE);

        mScrollView.scrollTo(0, 0);

        String state = cursor.getString(cursor.getColumnIndex("uploadState"));
        boolean isLocal = MediaUtils.isLocalFile(state);
        if (isLocal) {
            mNetworkImageView.setVisibility(View.GONE);
            mLocalImageView.setVisibility(View.VISIBLE);
        } else {
            mNetworkImageView.setVisibility(View.VISIBLE);
            mLocalImageView.setVisibility(View.GONE);
        }

        // user can't edit local files
        mSaveButton.setEnabled(!isLocal);
        mTitleView.setEnabled(!isLocal);
        mCaptionView.setEnabled(!isLocal);
        mDescriptionView.setEnabled(!isLocal);

        mMediaId = cursor.getString(cursor.getColumnIndex("mediaId"));
        mTitleView.setText(cursor.getString(cursor.getColumnIndex("title")));
        mTitleView.requestFocus();
        mTitleView.setSelection(mTitleView.getText().length());
        mCaptionView.setText(cursor.getString(cursor.getColumnIndex("caption")));
        mDescriptionView.setText(cursor.getString(cursor.getColumnIndex("description")));

        final String imageUri;
        if (isLocal) {
            imageUri = cursor.getString(cursor.getColumnIndex("filePath"));
        } else {
            imageUri = cursor.getString(cursor.getColumnIndex("fileURL"));
        }
        if (MediaUtils.isValidImage(imageUri)) {

            int width = cursor.getInt(cursor.getColumnIndex("width"));
            int height = cursor.getInt(cursor.getColumnIndex("height"));

            // differentiating between tablet and phone
            float screenWidth;
            if (this.isInLayout()) {
                screenWidth = mLinearLayout.getMeasuredWidth();
            } else {
                screenWidth = getActivity().getResources().getDisplayMetrics().widthPixels;
            }
            float screenHeight = getActivity().getResources().getDisplayMetrics().heightPixels;


            if (width > screenWidth) {
                height = (int) (height / (width / screenWidth));
            } else if (height > screenHeight) {
                width = (int) (width / (height / screenHeight));
            }

            if (isLocal) {
                loadLocalImage(mLocalImageView, imageUri, width, height);
                mLocalImageView.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, height));
            } else {
                mNetworkImageView.setImageUrl(imageUri + "?w=" + screenWidth, mImageLoader);
                mNetworkImageView.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, height));
            }

        } else {
            mNetworkImageView.setVisibility(View.GONE);
            mLocalImageView.setVisibility(View.GONE);
        }

        disableEditingOnOldVersion();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!isInLayout()) {
            inflater.inflate(R.menu.media_edit, menu);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (!isInLayout()) {
            menu.findItem(R.id.menu_new_media).setVisible(false);
            menu.findItem(R.id.menu_search).setVisible(false);

            if (!MediaUtils.isWordPressVersionWithMediaEditingCapabilities())
                menu.findItem(R.id.menu_save_media).setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_save_media) {
            item.setActionView(R.layout.progressbar);
            editMedia();

        }
        return super.onOptionsItemSelected(item);
    }

    private synchronized void loadLocalImage(ImageView imageView, String filePath, int width, int height) {

        if (MediaUtils.isValidImage(filePath)) {
            imageView.setTag(filePath);

            Bitmap bitmap = WordPress.getBitmapCache().get(filePath);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                BitmapWorkerTask task = new BitmapWorkerTask(imageView, width, height, new BitmapWorkerCallback() {

                    @Override
                    public void onBitmapReady(String path, ImageView imageView, Bitmap bitmap) {
                        if (imageView != null) {
                            imageView.setImageBitmap(bitmap);
                        }
                        WordPress.getBitmapCache().put(path, bitmap);
                    }
                });
                task.execute(filePath);
            }
        }
    }
}
