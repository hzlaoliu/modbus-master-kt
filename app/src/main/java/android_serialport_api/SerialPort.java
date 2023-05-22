/*
 * Copyright 2009 Cedric Priscal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android_serialport_api;

import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.NonNull;

public final class SerialPort {

    private static final String TAG = "SerialPort";

    private final FileDescriptor mFd;

    private final FileInputStream mFileInputStream;

    private final FileOutputStream mFileOutputStream;

    public SerialPort(@NonNull File path, int baudRate, int stopBits, int dataBits, int parity, int flowCon, int flags) throws SecurityException, IOException {

        if (!path.canRead() || !path.canWrite()) {
            try {
                // 获取ROOT权限
                Process su = Runtime.getRuntime().exec("/system/bin/su");
                String cmd = "chmod 666 " + path.getAbsolutePath() + "\n" + "exit\n";
                su.getOutputStream().write(cmd.getBytes());
                if ((su.waitFor() != 0) || !path.canRead() || !path.canWrite()) {
                    Log.e(TAG, "获取指定串口的读写权限异常");
                    throw new SecurityException();
                }
            } catch (Exception e) {
                throw new SecurityException();
            }
        }

        mFd = open(path.getAbsolutePath(), baudRate, stopBits, dataBits, parity, flowCon, flags);
        if (null == mFd) {
            Log.e(TAG, "native open returns null");
            throw new IOException();
        }
        mFileInputStream = new FileInputStream(mFd);
        mFileOutputStream = new FileOutputStream(mFd);
    }

    public InputStream getInputStream() {
        return mFileInputStream;
    }


    public OutputStream getOutputStream() {
        return mFileOutputStream;
    }


    private native static FileDescriptor open(@NonNull String path, int baudRate, int stopBits,
                                              int dataBits, int parity, int flowCon, int flags);

    public native void close();

    static {
        System.loadLibrary("serial_port");
    }
}