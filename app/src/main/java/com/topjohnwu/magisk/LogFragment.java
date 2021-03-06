package com.topjohnwu.magisk;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.topjohnwu.magisk.utils.Shell;
import com.topjohnwu.magisk.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;

import butterknife.BindView;
import butterknife.ButterKnife;

public class LogFragment extends Fragment {

    private static final String MAGISK_LOG = "/cache/magisk.log";

    @BindView(R.id.txtLog) TextView txtLog;
    @BindView(R.id.svLog) ScrollView svLog;
    @BindView(R.id.hsvLog) HorizontalScrollView hsvLog;

    @BindView(R.id.progressBar) ProgressBar progressBar;

    private MenuItem mClickedMenuItem;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.log_fragment, container, false);
        ButterKnife.bind(this, view);

        txtLog.setTextIsSelectable(true);

        reloadErrorLog();

        setHasOptionsMenu(true);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_log, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        mClickedMenuItem = item;
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                reloadErrorLog();
                return true;
            case R.id.menu_send:
                try {
                    send();
                } catch (NullPointerException ignored) {
                }
                return true;
            case R.id.menu_save:
                save();
                return true;
            case R.id.menu_clear:
                clear();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reloadErrorLog() {
        new LogsManager(true).execute();
        svLog.post(() -> svLog.scrollTo(0, txtLog.getHeight()));
        hsvLog.post(() -> hsvLog.scrollTo(0, 0));
    }

    private void clear() {
        new LogsManager(false).execute();
        reloadErrorLog();
    }

    private void send() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(save()));
        sendIntent.setType("application/html");
        startActivity(Intent.createChooser(sendIntent, getResources().getString(R.string.menuSend)));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mClickedMenuItem != null) {
                    new Handler().postDelayed(() -> onOptionsItemSelected(mClickedMenuItem), 500);
                }
            } else {
                Snackbar.make(txtLog, R.string.permissionNotGranted, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private File save() {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
            return null;
        }

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Snackbar.make(txtLog, R.string.sdcard_not_writable, Snackbar.LENGTH_LONG).show();
            return null;
        }

        Calendar now = Calendar.getInstance();
        String filename = String.format(
                "magisk_%s_%04d%02d%02d_%02d%02d%02d.log", "error",
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE), now.get(Calendar.SECOND));

        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Magisk/");
        dir.mkdir();

        File targetFile = new File(dir, filename);
        List<String> in = Utils.readFile(MAGISK_LOG);

        try {
            FileWriter out = new FileWriter(targetFile);
            for (String line : in) {
                out.write(line + "\n");
            }
            out.close();

            Toast.makeText(getActivity(), targetFile.toString(), Toast.LENGTH_LONG).show();
            return targetFile;
        } catch (IOException e) {
            Toast.makeText(getActivity(), getResources().getString(R.string.logs_save_failed) + "\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private class LogsManager extends AsyncTask<Void, Integer, String> {

        private boolean readLog;

        public LogsManager(boolean read) {
            readLog = read;
        }

        @Override
        protected void onPreExecute() {
            txtLog.setText("");
        }

        @Override
        protected String doInBackground(Void... voids) {
            if (readLog) {
                List<String> logList = Utils.readFile(MAGISK_LOG);

                StringBuilder llog = new StringBuilder(15 * 10 * 1024);
                for (String s : logList) {
                    llog.append(s).append("\n");
                }

                return llog.toString();
            } else {
                if (Utils.removeFile(MAGISK_LOG)) {
                    Snackbar.make(txtLog, R.string.logs_cleared, Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(txtLog, R.string.logs_clear_failed, Snackbar.LENGTH_SHORT).show();
                }
                return "";
            }
        }

        @Override
        protected void onPostExecute(String llog) {
            progressBar.setVisibility(View.GONE);
            txtLog.setText(llog);

            if (llog.length() == 0)
                txtLog.setText(R.string.log_is_empty);
        }

    }

}
