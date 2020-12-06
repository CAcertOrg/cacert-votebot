/*
 * Copyright (c) 2018-2020  Jan Dittberner
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

import org.cacert.votebot.shared.exceptions.IRCClientException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.SocketUtils;

import javax.net.ServerSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;


public class IRCClientTest {
    private static IRCRequestHandler handler;
    private static IRCClient client;
    private static Thread serverThread;
    private static PrintWriter mockMe;

    interface IRCRequestHandler {
        void handle();

        void sendCommand(String command);

        void setSocket(Socket socket) throws IOException;
    }

    static class MockIRCRequestHandler implements IRCRequestHandler {
        private BufferedReader reader;
        private PrintWriter writer;
        private Socket socket;
        private final PrintWriter testWriter;

        MockIRCRequestHandler(PrintWriter testWriter) {
            this.testWriter = testWriter;
        }


        @Override
        public void handle() {
            try {

                String nick = null;
                Map<String, Set<String>> channels = new HashMap<>();

                for (; ; ) {
                    String inputLine = reader.readLine();
                    this.testWriter.println(inputLine);
                    String[] parts = inputLine.split(" ");
                    switch (parts[0].toUpperCase()) {
                        case "NICK":
                            nick = parts[1];
                            break;
                        case "USER":
                            if (nick != null) {
                                writer.println("Hello " + nick);
                            }
                            break;
                        case "QUIT":
                            socket.close();
                            return;
                        case "JOIN":
                            String channel = parts[1];
                            if (!channels.containsKey(channel)) {
                                channels.put(channel, new HashSet<>());
                            }
                            channels.get(channel).add(nick);
                            break;
                        default:
                            writer.println("001 Unknown command");
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void sendCommand(String command) {
            this.writer.println(command);
        }

        @Override
        public void setSocket(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(socket.getOutputStream(), true);
        }
    }

    static class MockIrcServer implements Runnable {
        private final IRCRequestHandler handler;
        private final ServerSocket serverSocket;
        private final Logger log = LoggerFactory.getLogger(MockIrcServer.class);

        MockIrcServer(IRCRequestHandler handler) throws IOException {
            this.serverSocket = ServerSocketFactory.getDefault().createServerSocket(SocketUtils.findAvailableTcpPort());
            this.handler = handler;
        }

        int getServerPort() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void run() {
            new Thread(() -> {
                if (serverSocket != null) {
                    try {
                        Socket socket = serverSocket.accept();
                        handler.setSocket(socket);
                        handler.handle();
                    } catch (Exception e) {
                        log.error("Exception while handling socket I/O", e);
                    }
                }
            }).start();
        }
    }

    @BeforeAll
    public static void initializeClient() throws Exception {
        mockMe = Mockito.mock(PrintWriter.class);
        handler = new MockIRCRequestHandler(mockMe);
        MockIrcServer server = new MockIrcServer(handler);
        serverThread = new Thread(server);
        serverThread.setName("mock-server");
        serverThread.start();
        client = new IRCClient();
        String testPort = Integer.toString(server.getServerPort());
        client.initializeFromArgs("-h", "localhost", "-p", testPort, "-n", "testbot", "--no-ssl");
        client.assignBot(Mockito.mock(IRCBot.class));
        verify(mockMe, after(100)).println("NICK testbot");
        verify(mockMe, after(100)).println(ArgumentMatchers.startsWith("USER testbot"));
    }

    @AfterAll
    public static void shutdownClient() {
        client.quit();
        serverThread.interrupt();
    }

    @BeforeEach
    public void clearInvocations() {
        Mockito.clearInvocations(mockMe);
    }

    @Test
    public void testPingHandler() {
        handler.sendCommand("PING me");
        verify(mockMe, after(100)).println("PONG me");
    }

    @Test
    public void testPrivMessageHandler() {
        handler.sendCommand("PRIVMSG #meeting Hello");
    }

    @Test
    public void testJoinLeave() throws IRCClientException {
        client.join("test");
        verify(mockMe, after(100)).println("JOIN #test");
        client.leave("test");
        verify(mockMe, after(100)).println("PART #test");
    }

    @Test
    public void testJoinLeaveAll() throws IRCClientException {
        client.join("vote");
        client.join("meeting");
        client.leaveAll();

        verify(mockMe, after(100)).println("JOIN #vote");
        verify(mockMe).println("JOIN #meeting");
        verify(mockMe).println("PART #vote");
        verify(mockMe).println("PART #meeting");
        verifyNoMoreInteractions(mockMe);
    }

    @Test
    public void testSend() throws Exception {
        client.join("meeting");
        client.send("Test message", "meeting");
        client.leaveAll();

        verify(mockMe, after(100)).println("JOIN #meeting");
        verify(mockMe).println("PRIVMSG #meeting :Test message");
        verify(mockMe).println("PART #meeting");
        verifyNoMoreInteractions(mockMe);
    }

    @Test
    public void testSendMultiline() throws Exception {
        client.join("meeting");
        client.send("Test message\nMeeting in 10 minutes", "meeting");
        client.leaveAll();

        verify(mockMe, after(100)).println("JOIN #meeting");
        verify(mockMe).println("PRIVMSG #meeting :Test message");
        verify(mockMe).println("PRIVMSG #meeting :Meeting in 10 minutes");
        verify(mockMe).println("PART #meeting");
        verifyNoMoreInteractions(mockMe);
    }

    @Test
    public void testSendPrivateMessage() throws Exception {
        client.sendPrivate("Test message", "otherguy");
        verify(mockMe, after(100)).println("PRIVMSG otherguy :Test message");
        verifyNoMoreInteractions(mockMe);
    }

    @Test
    public void testSendPrivateMessageMultiline() throws Exception {
        client.sendPrivate("Test message\nMeeting in 10 minutes", "otherguy");
        verify(mockMe, after(100)).println("PRIVMSG otherguy :Test message");
        verify(mockMe, after(100)).println("PRIVMSG otherguy :Meeting in 10 minutes");
        verifyNoMoreInteractions(mockMe);
    }

    @Test
    public void testFailPrivateMessageToSelf() {
        try {
            client.sendPrivate("Test message", "test/nick");
            fail("Expected IRC client exception for private message to invalid nick not thrown.");
        } catch (IRCClientException e) {
            assertThat(e.getMessage(), containsString("test/nick"));
        }
        verifyNoInteractions(mockMe);
    }
}
