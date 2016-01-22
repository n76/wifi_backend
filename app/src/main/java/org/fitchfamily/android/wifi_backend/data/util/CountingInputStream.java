package org.fitchfamily.android.wifi_backend.data.util;

/*
 *  WiFi Backend for Unified Network Location
 *  Copyright (C) 2014,2015  Tod Fitch
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.support.annotation.NonNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CountingInputStream extends FilterInputStream {
    private long bytesRead = 0;

    public CountingInputStream(InputStream inputStream) {
        super(inputStream);
    }

    @Override
    public int read() throws IOException {
        final int result = super.read();

        if(result != -1) {
            bytesRead++;
        }

        return result;
    }

    @Override
    public int read(@NonNull byte[] buffer) throws IOException {
        final int result = super.read(buffer);

        if(result != -1) {
            bytesRead += result;
        }

        return result;
    }

    @Override
    public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
        final int result = super.read(buffer, byteOffset, byteCount);

        if(result != -1) {
            bytesRead += result;
        }

        return result;
    }

    public long getBytesRead() {
        return bytesRead;
    }
}
