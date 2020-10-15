package com.bzc.cameraapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button mStartBtn;
    private static final int TAKE_PHONE = 20;//启动相机码
    private static final int CHOOSE_PHOTO = 201;//选择相册
    private File mOutputImage;
    private Uri mImageUri;
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initEvent();
    }

    private void initEvent() {
        mStartBtn.setOnClickListener(this);
    }

    private void initView() {
        mStartBtn = findViewById(R.id.start);
        mImageView = findViewById(R.id.showImage);
    }

    @Override
    public void onClick(View view) {
        if(view.getId() == R.id.start){
            showSelectDialog();
        }
    }

    private void showSelectDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        builder.setTitle("请选择").setItems(new String[]{"拍照", "相册", "录制视频"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (i == 0){
                            takePhone();
                        }else if(i == 1){
                            openAlbum();
                        }else if(i == 2){
                            Log.e("xxx", "录制视频");
                        }
                    }
                }).setNegativeButton("确定", null);
        builder.create().show();
    }

    private void openAlbum() {
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        int permission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    MainActivity.this,
                    PERMISSIONS_STORAGE,
                    1
            );
        }else{
            chooseImage();
        }
    }

    private void chooseImage() {
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        startActivityForResult(intent, CHOOSE_PHOTO);
    }

    private void takePhone() {
        //创建一个File对象用于存储拍照后的照片
        mOutputImage = new File(getExternalCacheDir(),getImagePath());
        try{
            if(mOutputImage.exists()){
                mOutputImage.delete();
            }
            mOutputImage.createNewFile();
        }catch (Exception e){
            e.printStackTrace();
        }

        //判断Android版本是否是Android7.0以上
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            mImageUri = FileProvider.getUriForFile(MainActivity.this,"com.example.administrator.myapplication.fileprovider", mOutputImage);
            //AndroidMainfest中authorities一定要跟第二个参数一样！
        }else{
            mImageUri =Uri.fromFile(mOutputImage);
        }

        //启动相机程序
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
        startActivityForResult(intent,TAKE_PHONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == TAKE_PHONE){
            if(resultCode == RESULT_OK){
                try {

                    Bitmap bitmap  = BitmapFactory.decodeStream(getContentResolver().openInputStream(mImageUri));
                    mImageView.setImageBitmap(bitmap);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }else{
                Toast.makeText(this, "拍照失败", Toast.LENGTH_LONG).show();
            }
        }else if(requestCode == CHOOSE_PHOTO){
            if(resultCode == RESULT_OK){
                //获取成功
                //判断手机版本号
                if(Build.VERSION.SDK_INT >= 19){
                    //4.4以上的版本系统使用这个方法
                    handleImageOfKitKat(data);
                }else{
                    //4.4以下的版本使用这个方法
                    handleImageBeforeKitKat(data);
                }
            }else{
                Toast.makeText(MainActivity.this, "照片获取失败", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * API 大于等于19的时候需要对返回的数据进行解析
     *
     * @param data
     */

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void handleImageOfKitKat(Intent data) {
        String imageUrl = null;
        Uri uri = data.getData();
        if (DocumentsContract.isDocumentUri(this, uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.media.documnets".equals(uri.getAuthority())) {//判断uri是不是media格式
                String id = docId.split(":")[1];//是media格式的话将uri进行二次解析取出id
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imageUrl = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(docId));
                imageUrl = getImagePath(contentUri, null);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            imageUrl = getImagePath(uri, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            imageUrl = uri.getPath();
        }
        displayImage(imageUrl);
    }

    /**
     * API小于19的时候返回的就是图片的Url
     *
     * @param data
     */
    private void handleImageBeforeKitKat(Intent data) {
        Uri imageUrl = data.getData();
        String imagePath = imageUrl.getPath();
        displayImage(imagePath);

    }

    private void displayImage(String imagePath) {

        Bitmap bitmap  = BitmapFactory.decodeFile(imagePath);
        mImageView.setImageBitmap(bitmap);
        saveBitmapFile(bitmap, imagePath);

    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private String getImagePath(Uri externalContentUri, String selection) {
        String path = null;
        Cursor cursor = getContentResolver().query(externalContentUri, null, selection, null, null, null);
        if (cursor == null) {
            return path;
        }
        while (cursor.moveToNext()) {
            path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
        }
        cursor.close();
        return path;
    }

    /**
     * 用来生成一个图片的文件名，避免重复
     */
    private String getImagePath(){
        String filePath = "";
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat("'IMG'_yyyyMMdd_HHmmss");
        filePath = format.format(date) + ".png";
        return filePath;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 1){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                chooseImage();
            }else{
                Toast.makeText(this, "权限不足，操作失败", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveBitmapFile(Bitmap bitmap, String path) {

        File file = new File(path);//将要保存图片的路径
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();

            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri uri = Uri.fromFile(file);
            intent.setData(uri);
            this.sendBroadcast(intent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}