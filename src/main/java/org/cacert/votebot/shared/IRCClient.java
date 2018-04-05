/*
 * Copyright (c) 2015  Felix Doerre
 * Copyright (c) 2015  Benny Baumann
 * Copyright (c) 2016, 2018  Jan Dittberner
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

import org.apache.commons.cli.*;
import org.cacert.votebot.shared.exceptions.IRCClientException;
import org.cacert.votebot.shared.exceptions.InvalidChannelName;
import org.cacert.votebot.shared.exceptions.InvalidNickName;
import org.cacert.votebot.shared.exceptions.NoBotAssigned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * This class encapsulates the communication with the IRC server.
 *
 * @author Felix Doerre
 * @author Jan Dittberner
 */
@SuppressWarnings({"unused", "WeakerAccess"})
@Component
public class IRCClient {
    /**
     * Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IRCClient.class);
    /**
     * Regular expression to validate IRC nick names.
     */
    private static final Pattern NICK_RE = Pattern.compile("[a-zA-Z0-9_-]+");
    /**
     * Regular expression to validate IRC channel names.
     */
    private static final Pattern CHANNEL_RE = Pattern.compile("[a-zA-Z0-9_-]+");

    private final Semaphore loggedin = new Semaphore(1);
    private PrintWriter out;
    private final Set<String> joinedChannels = new HashSet<>();
    private IRCBot targetBot;

    /**
     * Initialize the IRC client based on command line arguments.
     *
     * @param args command line arguments
     * @return the instance itself
     * @throws IOException          in case of network IO problems
     * @throws InterruptedException in case of thread interruption
     * @throws ParseException       in case of problems parsing the command line arguments
     * @throws IRCClientException   in case of syntactic errors related to the IRC protocol
     */
    public IRCClient initializeFromArgs(final String... args)
    throws IOException, InterruptedException, ParseException, IRCClientException {
        final Options opts = new Options();
        opts.addOption(
                Option.builder("u").longOpt("no-ssl")
                      .desc("disable SSL").build());
        opts.addOption(
                Option.builder("h").longOpt("host").hasArg(true).required()
                      .desc("hostname of the IRC server").build());
        opts.addOption(
                Option.builder("p").longOpt("port").hasArg(true)
                      .desc("tcp port of the IRC server").type(Integer.class).build());
        opts.addOption(
                Option.builder("n").longOpt("nick").hasArg(true).argName("nick").required()
                      .desc("IRC nick name").build());

        final CommandLineParser commandLineParser = new DefaultParser();
        try {
            final CommandLine commandLine = commandLineParser.parse(opts, args);
            initialize(
                    commandLine.getOptionValue("nick"),
                    commandLine.getOptionValue("host"),
                    Integer.parseInt(commandLine.getOptionValue("port", "7000")),
                    !commandLine.hasOption("no-ssl"));
        } catch (final ParseException pe) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("votebot", opts);
            throw pe;
        }
        return this;
    }

    private void initialize(final String nick, final String server, final int port, final boolean ssl)
    throws IOException,
            InterruptedException, IRCClientException {
        if (!NICK_RE.matcher(nick).matches()) {
            throw new IRCClientException(String.format("malformed nickname %s", nick));
        }

        final Socket socket;
        if (ssl) {
            socket = SSLSocketFactory.getDefault().createSocket(server, port); //default-ssl = 7000
        } else {
            socket = new Socket(server, port); // default-plain = 6667
        }

        out = new CRLFPrintWriter(socket.getOutputStream(), true);
        final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        new ServerReader(in);

        out.println("NICK " + nick);
        out.println("USER " + nick + " 0 * :CAcert Votebot");

        loggedin.acquire();
    }

    /**
     * Check whether preconditions for a channel command are met.
     *
     * @param channel channel name
     * @throws NoBotAssigned      when no bot is associated with this client
     * @throws InvalidChannelName when the channel name is invalid
     */
    private void checkChannelPreconditions(final String channel) throws NoBotAssigned, InvalidChannelName {
        if (targetBot == null) {
            throw new NoBotAssigned();
        }

        if (!CHANNEL_RE.matcher(channel).matches()) {
            throw new InvalidChannelName(channel);
        }
    }

    /**
     * Check whether preconditions for a private message command are met.
     *
     * @param nick nick name
     * @throws NoBotAssigned   when no bot is associated with this client
     * @throws InvalidNickName when the nick name is invalid
     */
    private void checkPrivateMessagePreconditions(final String nick) throws NoBotAssigned, InvalidNickName {
        if (targetBot == null) {
            throw new NoBotAssigned();
        }

        if (!NICK_RE.matcher(nick).matches()) {
            throw new InvalidNickName(nick);
        }
    }

    /**
     * Let the associated bot join the given channel.
     *
     * @param channel channel name
     * @throws IRCClientException for IRC client issue
     */
    public void join(final String channel) throws IRCClientException {
        checkChannelPreconditions(channel);

        if (joinedChannels.add(channel)) {
            out.println("JOIN #" + channel);
        }
    }

    /**
     * Let the associated bot leave the given channel.
     *
     * @param channel channel name
     * @throws IRCClientException for IRC client issues
     */
    public void leave(final String channel) throws IRCClientException {
        checkChannelPreconditions(channel);

        if (joinedChannels.remove(channel)) {
            out.println("PART #" + channel);
        }
    }

    @PreDestroy
    public void leaveAll() {
        List<String> channels = new ArrayList<>(joinedChannels);
        for (String channel : channels) {
            try {
                leave(channel);
            } catch (IRCClientException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Send a message to the given channel.
     *
     * @param msg     message
     * @param channel channel name
     * @throws IRCClientException for IRC client issues
     */
    public void send(final String msg, final String channel) throws IRCClientException {
        checkChannelPreconditions(channel);

        for (String line : msg.split("\n")) {
            if (line.length() == 0) {
                line = " ";
            }
            out.println(String.format("PRIVMSG #%s :%s", channel, line));
        }
    }

    /**
     * Send a private message to the given nick name.
     *
     * @param msg message
     * @param to  nickname
     * @throws IRCClientException for IRC client issues
     */
    public void sendPrivate(final String msg, final String to) throws IRCClientException {
        checkPrivateMessagePreconditions(to);

        for (String line : msg.split("\n")) {
            if (line.length() == 0) {
                line = " ";
            }
            out.println(String.format("PRIVMSG %s :%s", to, line));
        }
    }

    /**
     * Assign a bot to this client.
     *
     * @param bot IRC bot instance
     */
    public void assignBot(final IRCBot bot) {
        targetBot = bot;
    }


    /**
     * Quit the IRC session.
     */
    public void quit() {
        out.println("QUIT");
    }

    /**
     * Reader thread for handling the IRC connection.
     */
    private class ServerReader implements Runnable {
        private final BufferedReader bufferedReader;
        private final Map<String, PrintWriter> logs = new HashMap<>();

        ServerReader(final BufferedReader bufferedReader) {
            this.bufferedReader = bufferedReader;

            Thread serverReader = new Thread(this);
            serverReader.setName("irc-client-thread");
            serverReader.start();
        }

        @Override
        public void run() {
            String line;

            try {
                while ((line = bufferedReader.readLine()) != null) {
                    final String fullLine = line;

                    if (line.startsWith("PING ")) {
                        handleIrcPing(line);
                        continue;
                    }

                    String referent = "";

                    if (line.startsWith(":")) {
                        final String[] parts = line.split(" ", 2);
                        referent = parts[0];
                        line = parts[1];
                    }

                    final String[] command = line.split(" ", 3);

                    if (command[0].equals("001")) {
                        loggedin.release();
                    }

                    switch (command[0]) {
                        case "PRIVMSG":
                            final String msg = command[2].substring(1);
                            final String chnl = command[1];

                            if (chnl.startsWith("#")) {
                                handleMsg(referent, chnl, msg);
                            } else {
                                handlePrivMsg(referent, msg);
                            }

                            log(chnl, fullLine);
                            break;
                        case "JOIN": {
                            final String channel = command[1].substring(1);
                            targetBot.join(cleanReferent(referent), channel.substring(1));
                            log(channel, fullLine);
                            break;
                        }
                        case "PART":
                            final String channel = command[1];
                            targetBot.part(cleanReferent(referent), channel);
                            log(channel, fullLine);
                            break;
                        default:
                            LOGGER.info("unknown line: {}", line);
                            break;
                    }
                }
            } catch (final IOException | IRCClientException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        private void handleIrcPing(final String line) {
            LOGGER.debug("PONG");
            out.println("PONG " + line.substring("PING ".length()));
        }

        private String cleanReferent(final String referent) {
            final String[] parts = referent.split("!");

            if (!parts[0].startsWith(":")) {
                LOGGER.error("invalid public message");
                return "unknown";
            }

            return parts[0];
        }

        private void log(final String channel, final String logline) {
            PrintWriter log = logs.get(channel);

            if (log == null) {
                final Path dirPath = Paths.get("irc");
                if (!Files.exists(dirPath)) {
                    final Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxr-x---");
                    final FileAttribute fileAttributes = PosixFilePermissions.asFileAttribute(permissions);
                    try {
                        Files.createDirectory(dirPath, fileAttributes);
                    } catch (final IOException e) {
                        LOGGER.error("error creating directory 'irc': {}", e.getMessage());
                        return;
                    }
                }
                final Path filePath = dirPath.resolve(String.format("log_%s", channel));
                try {
                    log = new PrintWriter(Files
                            .newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.APPEND,
                                    StandardOpenOption.CREATE, StandardOpenOption.WRITE));
                } catch (final IOException e) {
                    LOGGER.error("error opening log file '{}' for writing: {}", filePath, e.getMessage());
                    return;
                }
                logs.put(channel, log);
            }

            log.println(logline);
            log.flush();
        }

        @SuppressWarnings("unused")
        @PreDestroy
        private void closeLogs() {
            for (final PrintWriter pwr : logs.values()) {
                pwr.flush();
                pwr.close();
            }
            logs.clear();
        }

        private void handlePrivMsg(final String referent, final String msg) throws IRCClientException {
            if (targetBot == null) {
                throw new NoBotAssigned();
            }

            final String[] parts = referent.split("!");

            if (!parts[0].startsWith(":")) {
                LOGGER.warn("invalid private message: {}", msg);
                return;
            }

            targetBot.privateMessage(parts[0].substring(1), msg);
        }

        private void handleMsg(final String referent, final String chnl, final String msg) throws IRCClientException {
            if (targetBot == null) {
                throw new NoBotAssigned();
            }

            final String[] parts = referent.split("!");

            if (!parts[0].startsWith(":")) {
                LOGGER.warn("invalid public message");
                return;
            }

            if (!chnl.startsWith("#")) {
                LOGGER.warn("invalid public message (chnl)");
                return;
            }

            targetBot.publicMessage(parts[0].substring(1), chnl.substring(1), msg);
        }

    }
}
