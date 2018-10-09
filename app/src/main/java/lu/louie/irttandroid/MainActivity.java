package lu.louie.irttandroid;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.llollox.androidtoggleswitch.widgets.ToggleSwitch;
import com.vistrav.ask.Ask;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_clean_output:
                TextView textView = findViewById(R.id.irttStdout);
                textView.setText("");
                break;
            case R.id.action_help:
                new MaterialStyledDialog.Builder(this)
                        .setTitle("Help")
                        .setDescription("You can use the '-h' option to show help message of IRTT\n\n" +
                                "If you have any bug report or feature request, please send an email to <git@louie.lu>\n\n" +
                                "Also, the source code of this APP can be found on <github.com/mlouielu/irttandroid>"
                        )
                        .setStyle(Style.HEADER_WITH_TITLE)
                        .setPositiveText("OK")
                        .show();
                break;
        }
        return true;
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

        // Check the external directory is create or not
        // If not, create it
        Ask.on(this)
                .forPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withRationales("We need WRITE_EXTERNAL_STORAGE permission for irtt client output options," +
                        "otherwise it will not work.")
                .go();
        File appExternalDirectory = getExternalFilesDir(null);
        Log.d(TAG, "external directory: " + appExternalDirectory);
        if (!appExternalDirectory.isDirectory() || !appExternalDirectory.exists())
            Log.d(TAG, "MKDIRS: " + appExternalDirectory.mkdirs());

        // Setup SSID and IP for user
        TextView textView = findViewById(R.id.wifiInfo);
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifiManager.getConnectionInfo();
        String ssid = info.getSSID();
        String ipaddr = ipInt2Str(info.getIpAddress());
        textView.setText("Info: " + ipaddr + ", " + ssid);

        // Setup textView
        TextView output = findViewById(R.id.irttStdout);
        output.setMovementMethod(new ScrollingMovementMethod());

        // Bind onClick to switch
        Switch sw = findViewById(R.id.switch2);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                onStartClick(compoundButton);
            }
        });

        // Setup Server/Client toggleSwitch
        ToggleSwitch ts = findViewById(R.id.irttServerClientSwitch);
        ts.setCheckedPosition(0);
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

    public <T> T[] combineArrays(T[] a, T[] b) {
        int aLen = a.length;
        int bLen = b.length;

        @SuppressWarnings("unchecked")
        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);

        return c;
    }


    ExecutorService executorService = null;
    Runnable irttRunnable = new Runnable() {
        public void run() {
            EditText irttOptions = findViewById(R.id.irttOptions);
            ToggleSwitch irttServerClientSwitch = findViewById(R.id.irttServerClientSwitch);
            String appFileDirectory = getFilesDir().getPath();
            String executableFilePath = appFileDirectory + "/irtt";
            String argv[] = {
                    executableFilePath, irttServerClientSwitch.getCheckedPosition() == 1 ? "client" : "server"};
            String options[] = irttOptions.getText().toString().split(" ");

            // Check options, if we get -o, then patch the path to /sdcard/Android/data
            // We don't care if the next option is a path or not, just patch it.
            // User will found that error, maybe :(
            for (int i = 0; i < options.length; ++i) {
                if (options[i].equals("-o")) {
                    options[i + 1] = getExternalFilesDir(null) + "/" + options[i + 1];
                }
            }

            String cmd[] = combineArrays(argv, options);
            Log.d(TAG, "CMD: " + Arrays.toString(cmd));

            Process process = null;
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                try {
                    process = pb.start();
                } catch (Exception e) {
                    Log.d(TAG, "ERROR: " + e);
                }

                int read;
                char[] buffer = new char[4096];
                StringBuffer output = new StringBuffer();
                try {
                    InputStream is = process.getInputStream();
                    InputStream es = process.getErrorStream();
                    BufferedReader ireader = new BufferedReader(new InputStreamReader(is));
                    BufferedReader ereader = new BufferedReader(new InputStreamReader(es));
                    while (!Thread.currentThread().isInterrupted()) {
                        while (!Thread.currentThread().isInterrupted() && is.available() > 0 && (read = ireader.read(buffer)) > 0) {
                            output.append(buffer, 0, read);
                            final String text = output.toString();
                            output.setLength(0);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    TextView textView = findViewById(R.id.irttStdout);
                                    textView.append(text);
                                    while (textView.canScrollVertically(1))
                                        textView.scrollBy(0, 10);
                                }
                            });
                        }
                        while (!Thread.currentThread().isInterrupted() && es.available() > 0 && (read = ereader.read(buffer)) > 0) {
                            output.append(buffer, 0, read);
                            final String text = output.toString();
                            output.setLength(0);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    TextView textView = findViewById(R.id.irttStdout);
                                    textView.append(text);
                                    while (textView.canScrollVertically(1))
                                        textView.scrollBy(0, 10);
                                }
                            });
                        }

                    }
                    ireader.close();
                    ereader.close();
                } catch (IOException e) {

                }

                Log.d(TAG, "END: " + output.toString());
                try {
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
            executorService = Executors.newSingleThreadExecutor();
            irttFuture = executorService.submit(irttRunnable);
            Log.d(TAG, "Start");
        } else if (THREAD_RUNNING) {
            THREAD_RUNNING = false;
            irttFuture.cancel(true);
            executorService.shutdown();
            Log.d(TAG, "Cancel");
        }
    }
}
