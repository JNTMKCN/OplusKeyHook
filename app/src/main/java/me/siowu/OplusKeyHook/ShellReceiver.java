package me.siowu.OplusKeyHook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class ShellReceiver extends BroadcastReceiver {

    private static final String TAG = "ShellReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String cmd = intent.getStringExtra("cmd");
        if (cmd == null || cmd.trim().isEmpty()) {
            Log.i(TAG, "收到空命令，忽略");
            return;
        }else{
            Log.i(TAG, "收到命令: " + cmd);
        }

        new Thread(() -> execShell(cmd)).start();
    }

    private void execShell(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");

            OutputStreamWriter os = new OutputStreamWriter(p.getOutputStream());
            BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader es = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            // 写入命令
            os.write(cmd + "\n");
            os.write("exit\n");
            os.flush();

            // 读取输出
            String line;
            StringBuilder out = new StringBuilder();
            while ((line = is.readLine()) != null) out.append(line).append("\n");

            StringBuilder err = new StringBuilder();
            while ((line = es.readLine()) != null) err.append(line).append("\n");

            int exit = p.waitFor();

            Log.i(TAG, "命令执行结束 exit=" + exit);
            if (out.length() > 0) Log.i(TAG, "输出:\n" + out);
            if (err.length() > 0) Log.e(TAG, "错误:\n" + err);

        } catch (Exception e) {
            Log.e(TAG, "Shell 执行异常: " + e);
        } finally {
            if (p != null) p.destroy();
        }
    }
}
