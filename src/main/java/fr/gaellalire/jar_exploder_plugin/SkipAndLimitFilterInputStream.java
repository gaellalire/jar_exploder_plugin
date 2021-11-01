/*
 * Copyright 2021 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.gaellalire.jar_exploder_plugin;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Gael Lalire
 */
public class SkipAndLimitFilterInputStream extends FilterInputStream {

    private long size;

    public SkipAndLimitFilterInputStream(final InputStream in, final long skip, final long size) throws IOException {
        super(in);
        in.skip(skip);
        this.size = size;
    }

    @Override
    public int read() throws IOException {
        if (size == 0) {
            return -1;
        }
        int count = in.read();
        if (count > 0 && size > 0) {
            size--;
        }
        return count;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (size == 0) {
            return -1;
        }
        int rlen = len;
        if (size > 0) {
            if (len > size) {
                rlen = (int) size;
            }
        }
        int count = in.read(b, off, rlen);
        if (count > 0 && size > 0) {
            size -= count;
        }
        return count;
    }

}
