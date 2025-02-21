package com.renard.ocr.thu;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.renard.ocr.R;
import com.renard.ocr.base.PermissionGrantedEvent;
import com.renard.ocr.documents.creation.MemoryWarningDialog;
import com.renard.ocr.documents.creation.NewDocumentActivity;
import com.renard.ocr.documents.viewing.grid.DocumentGridActivity;
import com.renard.ocr.install.InstallActivity;
import com.renard.ocr.language.OcrLanguage;
import com.renard.ocr.language.OcrLanguageDataStore;

import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * 应用首页,控制中心,核心功能入口
 */
public class MainActivity extends NewDocumentActivity {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_CODE_INSTALL = 234;
    private boolean mBusIsRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.thu_activity_main);

        initToolbar();

        //隐藏功能
        findViewById(R.id.start_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkRam(MemoryWarningDialog.DoAfter.START_CAMERA);
            }
        });
        //隐藏功能
        findViewById(R.id.start_gallary).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkRam(MemoryWarningDialog.DoAfter.START_GALLERY);
            }
        });

        //选择图片
        findViewById(R.id.start_batch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkRam(MemoryWarningDialog.DoAfter.START_MIP);
            }
        });

        //处理记录
        findViewById(R.id.document_grid).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DocumentGridActivity.class));
            }
        });

    }

    @Override
    protected int getParentId() {
        return -1;
    }//NewDocumentActivity 需要实现的抽象方法

    @Override
    protected void onResume() {
        super.onResume();
        if (!mBusIsRegistered) {
            EventBus.getDefault().register(this);
            mBusIsRegistered = true;
        }
        //声明需要访问thuocr文件夹
        ensurePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, R.string.permission_explanation);
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(final PermissionGrantedEvent event) {
        Log.i(LOG_TAG, "Permission Granted");
        startInstallActivityIfNeeded();
    }

    /**
     * 如果应用启动之后发现没有安装任何语言，这个时候就会去将assets目录下的tessdata.zip复制到sd卡中，并安装这些默认的语言包
     * Start the InstallActivity if possible and needed.
     */
    private void startInstallActivityIfNeeded() {
        final List<OcrLanguage> installedOCRLanguages = OcrLanguageDataStore.getInstalledOCRLanguages(this);
        final String state = Environment.getExternalStorageState();

        if (state.equals(Environment.MEDIA_MOUNTED)) {//sd卡存在
            if (installedOCRLanguages.isEmpty()) {//只有在安装语言为空的时候才会去安装,以前不论安装了哪些语言都不再重新安装
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setClassName(this, InstallActivity.class.getName());
                startActivityForResult(intent, REQUEST_CODE_INSTALL);//进入安装语言包 for result
            }
        } else {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setMessage(getString(R.string.no_sd_card));
            alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            alert.show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.thu_menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.item_settings) {
            SettingsDialog.newInstance().show(getSupportFragmentManager(), SettingsDialog.TAG);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_INSTALL) {//安装默认语言包
            if (RESULT_OK != resultCode) {//安装失败，立即退出
                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
        mBusIsRegistered = false;
    }

}
