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

/**
 * @author Gael Lalire
 */
public class MetaAndSha512 {

    private String sha512;

    private int position;

    private String name;

    private long size;

    private long crc32;

    private long time;

    private long deflatedSize;

    private String url;

    public MetaAndSha512(final String sha512, final int position, final String name, final long size, final long crc32, final long time, final long deflatedSize,
            final String url) {
        this.sha512 = sha512;
        this.position = position;
        this.name = name;
        this.size = size;
        this.crc32 = crc32;
        this.time = time;
        this.deflatedSize = deflatedSize;
        this.url = url;
    }

    @Override
    public String toString() {
        return "MetaAndSha512 [sha512=" + sha512 + ", position=" + position + ", url=" + url + ", name=" + name + ", size=" + size + ", crc32=" + crc32 + ", time=" + time
                + ", deflatedSize=" + deflatedSize + "]";
    }

    public String getSha512() {
        return sha512;
    }

    public int getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public long getCrc32() {
        return crc32;
    }

    public long getTime() {
        return time;
    }

    public long getDeflatedSize() {
        return deflatedSize;
    }

    public String getUrl() {
        return url;
    }

}
