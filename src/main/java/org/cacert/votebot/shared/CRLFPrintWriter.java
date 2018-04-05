/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This file is part of CAcert VoteBot.
 *
 * CAcert VoteBot is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * CAcert VoteBot is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * CAcert VoteBot.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.cacert.votebot.shared;

import java.io.*;

/**
 * Based on http://www.java2s.com/Tutorial/Java/0180__File/APrintWriterthatendslineswithacarriagereturnlinefeedCRLF.htm
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 */
public class CRLFPrintWriter extends PrintWriter {
    private boolean autoFlush;

    /**
     * @param outputStream wrapped output stream
     * @param autoFlush    whether to autoFlush output immediately
     */
    CRLFPrintWriter(OutputStream outputStream, boolean autoFlush) {
        super(outputStream, autoFlush);
        this.autoFlush = autoFlush;
    }

    private void ensureOpen() throws IOException {
        if (out == null) throw new IOException("Stream closed");
    }

    @Override
    public void println() {
        try {
            //noinspection SynchronizeOnNonFinalField
            synchronized (lock) {
                ensureOpen();

                out.write("\r\n");

                if (autoFlush) {
                    out.flush();
                }
            }
        } catch (InterruptedIOException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ioe) {
            setError();
        }
    }
}
