package cn.ca.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.security.KeyChain;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * @author sanbo
 */
public class MainActivity extends Activity {

    private TextView mTVStatus;
    private static Context mContext;
    private String URL_BASE = "http://mitm.it/";
    private String URL_DOWMLOAD = "http://mitm.it/cert/pem";
    private String DOWN_FILE_NAME = "mitmproxy.pem";
    private String INSTALL_CA_NAME = "mitmproxy";
    final int INSTALL_CA_REQUEST_CODE = 0x99;
    private MyHandler mMessageThreadHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mContext = this;
        if (VERSION.SDK_INT > 23) {
            PermissionUtils.requestPermission(this, PermissionUtils.CODE_WRITE_EXTERNAL_STORAGE, mPermissionGrant);
            PermissionUtils.requestPermission(this, PermissionUtils.CODE_READ_PHONE_STATE, mPermissionGrant);
            PermissionUtils.requestPermission(this, PermissionUtils.CODE_ACCESS_COARSE_LOCATION, mPermissionGrant);
        }
        registerHandler();
        mTVStatus = (TextView)findViewById(R.id.status);
        Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/Helvetica.ttf");
        TextView a = (TextView)findViewById(R.id.installCA);
        a.setTypeface(tf);
        mTVStatus.setTypeface(tf);

        TextView readme = (TextView)findViewById(R.id.readme);
        readme.setTypeface(tf);

        findViewById(R.id.ac_main_base).setOnClickListener(listener);
    }

    /**
     * 三击
     */
    private long[] mHits = new long[3];

    public final OnClickListener listener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
            mHits[mHits.length - 1] = SystemClock.uptimeMillis();//获取手机开机时间
            if (mHits[mHits.length - 1] - mHits[0] < 500) {
                /**双击的业务逻辑*/
                L.e("三击");
                //installNativeCA();
                mMessageThreadHandler.sendMessage(mMessageThreadHandler.obtainMessage(0));
            }
        }
    };

    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.installCA:
                mMessageThreadHandler.sendMessage(mMessageThreadHandler.obtainMessage(0));
                break;
            case R.id.status:
                mMessageThreadHandler.sendMessage(mMessageThreadHandler.obtainMessage(1));
                break;
            default:
                break;
        }
    }

    private void registerHandler() {

        final HandlerThread thread = new HandlerThread(MainActivity.class.getCanonicalName());
        thread.setPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mMessageThreadHandler = new MyHandler(this, thread.getLooper());
    }

    private class MyHandler extends Handler {

        public MyHandler(Context context, Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            try {

                final int what = msg.what;
                switch (what) {
                    case 0:

                        try {
                            Document doc = Jsoup.connect(URL_BASE).get();
                            String s = doc.toString();
                            if (s.contains(
                                "If you can see this, traffic is not passing through mitmproxy.")) {
                                Toast.makeText(MainActivity.this, "请检查代理网络", Toast.LENGTH_LONG).show();
                                //need running on UIThread
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mTVStatus == null) {
                                            mTVStatus = (TextView)findViewById(R.id.status);
                                        }
                                        mTVStatus.setTextColor(Color.parseColor("#FF0000"));
                                        mTVStatus.setText(getResources().getString(R.string.check_network));
                                    }
                                });
                            } else {
                                L.d("will down load");
                                jsoupDownFile();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case 1:
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); //直接进入手机中的wifi网络设置界面
                        break;
                    default:
                        break;
                }
            } finally {}
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    private void jsoupDownFile() {
        FileOutputStream out = null;
        try {
            if (PermissionUtils.checkPermission(mContext, permission.WRITE_EXTERNAL_STORAGE)) {
                L.i("有权限下载.........");
                Response resultImageResponse = Jsoup.connect(URL_DOWMLOAD).ignoreContentType(true).ignoreHttpErrors(
                    true)
                    .execute();
                String desFileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
                    + "Download";
                File dir = new File(desFileDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File saveFile = new java.io.File(dir, DOWN_FILE_NAME);
                out = (new FileOutputStream(saveFile));
                out.write(resultImageResponse.bodyAsBytes());
                L.i("下载成功,.........");
                installCA(saveFile);
            } else {
                L.e("没有权限下载");
                Toast.makeText(mContext, "没有权限下载!", Toast.LENGTH_LONG).show();
            }

        } catch (Throwable e) {} finally {

            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void installCA(File file) {
        try {
            byte[] keychainBytes;
            InputStream bis = new FileInputStream(file);
            keychainBytes = new byte[bis.available()];
            bis.read(keychainBytes);
            Intent intent = KeyChain.createInstallIntent();
            intent.putExtra(KeyChain.EXTRA_CERTIFICATE, keychainBytes);
            intent.putExtra(KeyChain.EXTRA_NAME, INSTALL_CA_NAME);
            startActivityForResult(intent, INSTALL_CA_REQUEST_CODE);
            mTVStatus.setTextColor(Color.parseColor("#00FF00"));
            getResources().getString(R.string.install_over);
        } catch (Exception e) {
            L.e(e);
        }
    }

    private void installNativeCA() {
        InputStream is = null;
        try {

            /**
             * 每个人的证书都不一样,这个是根据mitm编译各个环境运行时候产生的,一般在~/.mitmproxy的目录里
             * <pre>
             * [~/.mitmproxy]$ ls
             *  mitmproxy-ca-cert.cer mitmproxy-ca-cert.p12 mitmproxy-ca-cert.pem mitmproxy-ca.pem mitmproxy-dhparam.pem
             * </pre>
             */
            is = getAssets().open("mitmproxy-ca-cert.pem");
            int lenght = is.available();
            byte[] bytes = new byte[lenght];
            is.read(bytes);

            //byte[] keychainBytes;
            //InputStream bis = new FileInputStream(file);
            //keychainBytes = new byte[bis.available()];
            //bis.read(keychainBytes);
            Intent intent = KeyChain.createInstallIntent();
            intent.putExtra(KeyChain.EXTRA_CERTIFICATE, bytes);
            intent.putExtra(KeyChain.EXTRA_NAME, INSTALL_CA_NAME);
            startActivityForResult(intent, INSTALL_CA_REQUEST_CODE);
            mTVStatus.setTextColor(Color.parseColor("#00FF00"));
            getResources().getString(R.string.install_over);
        } catch (Exception e) {
            L.e(e);
        }
    }

    /**
     * 用于回显的展示类
     */
    private PermissionUtils.PermissionGrant mPermissionGrant = new PermissionUtils.PermissionGrant() {

        @Override
        public void onPermissionGranted(int requestCode) {
            switch (requestCode) {
                case PermissionUtils.CODE_RECORD_AUDIO:
                    Toast.makeText(mContext, "Result Permission Grant CODE_RECORD_AUDIO", Toast.LENGTH_SHORT)
                        .show();
                    break;
                case PermissionUtils.CODE_GET_ACCOUNTS:
                    Toast.makeText(mContext, "Result Permission Grant CODE_GET_ACCOUNTS", Toast.LENGTH_SHORT)
                        .show();
                    break;
                case PermissionUtils.CODE_READ_PHONE_STATE:
                    Toast.makeText(mContext, "Result Permission Grant CODE_READ_PHONE_STATE", Toast.LENGTH_SHORT)
                        .show();
                    break;
                case PermissionUtils.CODE_CALL_PHONE:
                    Toast.makeText(mContext, "Result Permission Grant CODE_CALL_PHONE", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_CAMERA:
                    Toast.makeText(mContext, "Result Permission Grant CODE_CAMERA", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_ACCESS_FINE_LOCATION:
                    Toast.makeText(mContext, "Result Permission Grant CODE_ACCESS_FINE_LOCATION",
                        Toast.LENGTH_SHORT)
                        .show();
                    break;
                case PermissionUtils.CODE_ACCESS_COARSE_LOCATION:
                    Toast.makeText(mContext, "Result Permission Grant CODE_ACCESS_COARSE_LOCATION",
                        Toast.LENGTH_SHORT)
                        .show();
                    break;
                case PermissionUtils.CODE_READ_EXTERNAL_STORAGE:
                    Toast.makeText(mContext, "Result Permission Grant CODE_READ_EXTERNAL_STORAGE",
                        Toast.LENGTH_SHORT)
                        .show();
                    break;
                case PermissionUtils.CODE_WRITE_EXTERNAL_STORAGE:
                    Toast.makeText(mContext, "Result Permission Grant CODE_WRITE_EXTERNAL_STORAGE",
                        Toast.LENGTH_SHORT)
                        .show();
                    break;
                default:
                    break;
            }
        }
    };
}
