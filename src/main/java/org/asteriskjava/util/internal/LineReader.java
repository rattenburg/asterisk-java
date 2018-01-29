package org.asteriskjava.util.internal;

import java.io.IOException;
import java.io.Reader;

public class LineReader extends Reader
{
    private Reader in;
    private String lineSplitPattern;
    private char[] cbuf;
    private int off, len;

    public LineReader(Reader in, String lineSplitPattern)
    {
        if (lineSplitPattern.isEmpty())
        {
            throw new IllegalArgumentException("pattern must not be empty");
        }
        this.in = in;
        this.lineSplitPattern = lineSplitPattern;
        cbuf = new char[8192 + lineSplitPattern.length()];
    }

    @Override
    public void close() throws IOException
    {
        synchronized (lock)
        {
            if (in == null)
            {
                return;
            }
            try
            {
                in.close();
            }
            finally
            {
                // free memory
                in = null;
                lineSplitPattern = null;
                cbuf = null;

                // disable reading
                len = -1;
            }
        }
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException
    {
        if (len <= 0)
        {
            return 0;
        }
        if (off < 0 || off + len >= cbuf.length)
        {
            throw new ArrayIndexOutOfBoundsException();
        }
        synchronized (lock)
        {
            // fill buffer from stream
            if (this.len == 0)
            {
                this.off = 0;
                this.len = in.read(this.cbuf, 0, this.cbuf.length);
            }

            // end of stream?
            if (this.len < 0)
            {
                // free memory
                lineSplitPattern = null;
                this.cbuf = null;
                return -1;
            }

            // copy buffer to result
            len = Math.min(len, this.len);
            System.arraycopy(this.cbuf, this.off, cbuf, off, len);
            this.off += len;
            this.len -= len;
            return len;
        }
    }

    public String readLine() throws IOException
    {
        synchronized (lock)
        {
            if (len < 0)
            {
                return null;
            }

            StringBuilder result = new StringBuilder();
            while (true)
            {
                // search pattern in buffer
                int matches = 0;
                for (int size = 0; size < len; )
                {
                    if (cbuf[off + size++] == lineSplitPattern.charAt(matches))
                    {
                        matches++;
                        if (matches == lineSplitPattern.length())
                        {
                            result.append(cbuf, off, size - matches);
                            off += size;
                            len -= size;
                            return result.toString();
                        }
                    }
                    else
                    {
                        matches = 0;
                    }
                }

                // copy buffer to result except for matched suffix
                result.append(cbuf, off, len - matches);

                // fill buffer from stream
                // (leaving some space to prepend matched suffix later)
                len = in.read(cbuf, matches, cbuf.length - matches);

                // end of stream?
                if (len < 0)
                {
                    // append matched suffix
                    result.append(lineSplitPattern, 0, matches);
                    // free memory
                    lineSplitPattern = null;
                    cbuf = null;
                    return result.toString();
                }
                assert len > 0;

                // prepend previously matched suffix as new prefix (for search during next iteration)
                lineSplitPattern.getChars(0, matches, cbuf, 0);
                off = 0;
                len += matches; // chars from stream + previously matched suffix
            }
        }
    }
}
