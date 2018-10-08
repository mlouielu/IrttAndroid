package lu.louie.irttandroid;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity {
    String TAG = "HERE";
    boolean THREAD_RUNNING = false;
    Future irttFuture = null;

    public String ipInt2Str(int ip)
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(String.valueOf(ip&0xFF)).append(".");
        sb.append(String.valueOf((ip&0xFFFF) >>>8 )).append(".");
        sb.append(String.valueOf((ip&0xFFFFFF) >>> 16)).append(".");
        sb.append(String.valueOf(ip>>>24));
        return sb.toString();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String appFileDirectory = getFilesDir().getPath();
        String executableFilePath = appFileDirectory + "/irtt";

        copyAssets(appFileDirectory, "irtt");
        File execFile = new File(executableFilePath);
        execFile.setExecutable(true);

        // Setup SSID and IP for user
        TextView textView = findViewById(R.id.textView2);
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifiManager.getConnectionInfo();
        String ssid = info.getSSID();
        String ipaddr = ipInt2Str(info.getIpAddress());
        textView.setText("Info: " + ipaddr + ", " + ssid);

        // Setup textView
        TextView output = findViewById(R.id.textView);
        output.setMovementMethod(new ScrollingMovementMethod());

        // Bind onClick to switch
        Switch sw = findViewById(R.id.switch2);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                onStartClick(compoundButton);
            }
        });

    }

    private static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;

        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private void copyAssets(String appFileDirectory, String filename) {
        AssetManager assetManager = getAssets();

        InputStream in = null;
        OutputStream out = null;

        try {
            in = assetManager.open(filename);
            File outFile = new File(appFileDirectory, filename);
            out = new FileOutputStream(outFile);

            copyFile(in, out);
            in.close();
            out.flush();
            out.close();
        } catch (IOException e) {

        }
    }

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Runnable irttRunnable = new Runnable() {
        public void run() {
            String appFileDirectory = getFilesDir().getPath();
            String executableFilePath = appFileDirectory + "/irtt";
            String cmd[] = {executableFilePath, "server"};
            Process process = null;
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                try {
                    process = pb.start();
                } catch (Exception e) {
                    Log.d(TAG, "ERROR: " + e);
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                int read;
                char[] buffer = new char[4096];
                StringBuffer output = new StringBuffer();
                try {
                    while ((read = reader.read(buffer)) > 0 && THREAD_RUNNING) {
                        output.append(buffer, 0, read);
                        final String text = output.toString();
                        output.setLength(0);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                TextView textView = findViewById(R.id.textView);
                                textView.append(text);
                                while (textView.canScrollVertically(1))
                                    textView.scrollBy(0, 10);
                            }
                        });
                    }
                } catch (IOException e) {

                }

                Log.d(TAG, "END: " + output.toString());
                try {
                    reader.close();
                    process.waitFor();
                } catch (Exception e) {
                    Log.d(TAG, "Some problem: " + e);
                    process.destroy();
                }
            } catch (Exception e) {
                Log.d(TAG, "INTERRUPTED");
                process.destroy();
            }
        }};

    public void onStartClick(View v) {
        if (!THREAD_RUNNING) {
            THREAD_RUNNING = true;
            irttFuture = executorService.submit(irttRunnable);
        } else if (THREAD_RUNNING) {
            THREAD_RUNNING = false;
            irttFuture.cancel(true);
            Log.d(TAG, "Cancel");
        }
    }
}
