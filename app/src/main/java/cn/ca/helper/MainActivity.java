package cn.ca.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.security.KeyChain;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.socks.okhttp.plus.OkHttpProxy;
import com.socks.okhttp.plus.callback.OkCallback;
import com.socks.okhttp.plus.listener.DownloadListener;
import com.socks.okhttp.plus.model.Progress;
import com.socks.okhttp.plus.parser.OkTextParser;

/**
 * @author sanbo
 */
public class MainActivity extends Activity {

    private TextView mTVStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        registerHandler();
        mTVStatus = (TextView)findViewById(R.id.status);
        Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/Helvetica.ttf");
        TextView a = (TextView)findViewById(R.id.installCA);
        a.setTypeface(tf);
        mTVStatus.setTypeface(tf);

        TextView readme = (TextView)findViewById(R.id.readme);
        readme.setTypeface(tf);
    }

    private String URL_BASE = "http://mitm.it/";
    private String URL_DOWMLOAD = "http://mitm.it/cert/pem";
    private String DOWN_FILE_NAME = "mitmproxy.pem";
    private String INSTALL_CA_NAME = "mitmproxy";

    final int INSTALL_CA_REQUEST_CODE = 0x99;

    private MyHandler mMessageThreadHandler;

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

                        OkHttpProxy.get()
                            .url(URL_BASE)
                            .tag(this)
                            .enqueue(new OkCallback<String>(new OkTextParser()) {
                                @Override
                                public void onSuccess(int code, String s) {
                                    L.i("onSuccess  " + code + "==" + s);
                                    if (s.contains("If you can see this, traffic is not passing through mitmproxy.")) {
                                        Toast.makeText(MainActivity.this, "请检查代理网络", Toast.LENGTH_LONG).show();
                                        mTVStatus.setTextColor(Color.parseColor("#FF0000"));
                                        mTVStatus.setText(getResources().getString(R.string.check_network));
                                    } else {
                                        downFile();
                                    }
                                }

                                @Override
                                public void onFailure(Throwable e) {
                                    L.e("onFailure");
                                }
                            });

                        break;
                    case 1:
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); //直接进入手机中的wifi网络设置界面
                        break;
                    default:
                        break;
                }
            } finally {
            }
        }
    }

    private void downFile() {
        String desFileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
            + "Download";
        OkHttpProxy.download(URL_DOWMLOAD, new DownloadListener(desFileDir, DOWN_FILE_NAME) {

            @Override
            public void onUIProgress(Progress progress) {

                double pro = progress.getCurrentBytes() / (double)progress.getTotalBytes() * 100;
                L.d(
                    "onUIProgress() " + progress.getCurrentBytes() + " / "
                        + progress.getTotalBytes() + " = " + String.format("%.2f", pro));
            }

            @Override
            public void onSuccess(File file) {
                L.d("onSuccess==>" + file.getAbsolutePath());

                if (file != null) {
                    installCA(file);
                }
            }

            @Override
            public void onFailure(Exception e) {
                L.d("onFailure==>" + e.getMessage());

            }
        });

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
}
