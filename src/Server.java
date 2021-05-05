import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.*;
import java.util.Iterator;
import java.util.Set;

public class Server {
    private static ByteBuffer buffer;
    private static byte[] hostName = null;
    private static byte [] crnlMsg = null;
    // RESPONSE CODES
    private static byte[] connectionResponse = null;
    private static byte[] okayResponse = null;
    private static byte[] dataResponse = null;
    private static byte[] quitResponse = null;
    // HELP RESPONSES
    private static byte[] helpResponseHelo = null;
    private static byte[] helpResponseMail = null;
    private static byte[] helpResponseRcpt = null;
    private static byte[] helpResponseNone = null;
    // CONSTANTS
    private static final int BUFFER_SIZE = 8192;
    private static final int CRLF_LEN = 2; // Size of \r\n
    private static final int CRLF_DOT_LEN = 5; // Size of \r\n.\r\n
    private static final int MAX_MESSAGE_ID = 10000;
    private static final int MAIL_CMD_LEN = 11; // Corresponds to "MAIL FROM:"
    private static final int RCPT_CMD_LEN = 9; // Corresponds to "RCPT TO:"
    private static final String CRLF = "\r\n";
    private static final Charset MESSAGE_CHARSET = StandardCharsets.US_ASCII;

    // Initialize response messages
    public static void initResponses() {
        connectionResponse = "220 ".getBytes(MESSAGE_CHARSET);
        okayResponse = "250 OK".getBytes(MESSAGE_CHARSET);
        dataResponse = "354 Please send the message".getBytes(MESSAGE_CHARSET);
        quitResponse = "221 Bye".getBytes(MESSAGE_CHARSET);
        crnlMsg = CRLF.getBytes(MESSAGE_CHARSET);
        helpResponseHelo = "214 Lest das RFC: MAIL".getBytes(MESSAGE_CHARSET);
        helpResponseMail = "214 Lest das RFC: RCPT".getBytes(MESSAGE_CHARSET);
        helpResponseRcpt = "214 Lest das RFC: DATA".getBytes(MESSAGE_CHARSET);
        helpResponseNone = "214 Lest das RFC: QUIT oder MAIL".getBytes(MESSAGE_CHARSET);
    }

    // Read 4 first bytes of buffer and return corresponding command
    public static Command getCommand(SocketChannel channel, ByteBuffer buffer, ClientState state) throws IOException {
        buffer.clear();
        int pos = buffer.position();
        channel.read(buffer);

        byte[] command = new byte[4];
        command[0] = buffer.get(pos);
        command[1] = buffer.get(pos + 1);
        command[2] = buffer.get(pos + 2);
        command[3] = buffer.get(pos + 3);

        switch (new String(command, MESSAGE_CHARSET)) {
            // In case that the last state was DATA, treat HELP as plaintext
            case "HELP":
                if(state != null && state.getLastState() != null && state.getLastState() == Command.DATA){
                    return Command.NONE;
                }else{
                    return Command.HELP;
                }
            case "HELO":
                return Command.HELO;
            case "MAIL":
                return Command.MAIL;
            case "RCPT":
                return Command.RCPT;
            case "DATA":
                return Command.DATA;
            case "QUIT":
                return Command.QUIT;
            default:
                return Command.NONE;
        }
    }

    // Read buffer and return in string form
    private static String readMessage(ByteBuffer buffer, int startPos) {
        for (int i = startPos; i < buffer.position(); i++) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n') {
                buffer.flip();
                break;
            }
        }

        byte[] message = new byte[buffer.limit() - startPos];

        for (int i = startPos; i < buffer.limit(); i++) {
            message[i - startPos] = buffer.get(i);
        }

        return new String(message, MESSAGE_CHARSET);
    }

    // Default signature, starts at index 0
    private static String readMessage(ByteBuffer buffer) {
        return readMessage(buffer, 0);
    }

    // Send response to channel with option to add hostname
    public static void sendResponse(SocketChannel channel, ByteBuffer buffer, byte[] response, boolean appendHostName) throws IOException {
        buffer.clear();

        buffer.put(response);
        if (appendHostName) {
            buffer.put(hostName);
        }
        buffer.put(crnlMsg);
        buffer.flip();

        channel.write(buffer);

        buffer.clear();
    }

    // Default signature, without hostname
    public static void sendResponse(SocketChannel channel, ByteBuffer buffer, byte[] response) throws IOException {
        sendResponse(channel, buffer, response, false);
    }

    public static void main(String[] args) throws IOException {
        // Selector: multiplexor of SelectableChannel objects
        Selector selector = null; // selector is open here
        try {
            selector = Selector.open();
        } catch (IOException e1) {
            e1.printStackTrace();
            System.exit(1);
        }

        // ServerSocketChannel: selectable channel for stream-oriented listening sockets
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", 1111);

        // Binds the channel's serverChannel to a local address and configures the serverChannel to listen for connections
        serverChannel.bind(serverAddress);

        // Adjusts this channel's blocking mode.
        serverChannel.configureBlocking(false);

        int ops = serverChannel.validOps();
        SelectionKey selectKey = serverChannel.register(selector, ops, null);

        try {
            hostName = java.net.InetAddress.getLocalHost().getHostName().getBytes(MESSAGE_CHARSET);
        } catch (UnknownHostException e) {
            System.err.println("Cannot determine name of host. Exiting...");
            System.exit(1);
        }

        initResponses();
        buffer = ByteBuffer.allocate(BUFFER_SIZE);

        // Keep server running
        while (true) {
            // Selects a set of keys whose corresponding channels are ready for I/O operations
            try {
                if(selector.select() == 0)
                    continue;
            } catch (IOException e) {
                e.printStackTrace();
            }

            // token representing the registration of a SelectableChannel with a Selector
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> keysIterator = keys.iterator();

            while (keysIterator.hasNext()) {
                SelectionKey key = keysIterator.next();

                // Tests whether this key's channel is ready to accept a new serverChannel connection
                if (key.isAcceptable()) {
                    SocketChannel smtpClient = serverChannel.accept();

                    // Adjusts this channel's blocking mode to false
                    smtpClient.configureBlocking(false);

                    // Operation-set bit for read operations
                    smtpClient.register(selector, SelectionKey.OP_READ);

                    sendResponse(smtpClient, buffer, connectionResponse, true);
                    // Tests whether this key's channel is ready for reading
                } else if (key.isReadable()) {
                    SocketChannel smtpClient = (SocketChannel) key.channel();
                    ClientState state = (ClientState) key.attachment();

                    switch (getCommand(smtpClient, buffer, state)) {
                        case HELP:
                            switch (state.getLastState()) { // Send help response according to last client state
                                case HELO -> sendResponse(smtpClient, buffer, helpResponseHelo);
                                case MAIL -> sendResponse(smtpClient, buffer, helpResponseMail);
                                case RCPT -> sendResponse(smtpClient, buffer, helpResponseRcpt);
                                case NONE -> sendResponse(smtpClient, buffer, helpResponseNone);
                            }
                            break;
                        case HELO: // Attach new client state with random messageId
                            state = new ClientState();
                            state.setMessage_id((int) (Math.random() * MAX_MESSAGE_ID));
                            state.setLastState(Command.HELO);
                            key.attach(state);
                            sendResponse(smtpClient, buffer, okayResponse);
                            break;
                        case MAIL: // Update sender address
                            String senderAddress = readMessage(buffer, MAIL_CMD_LEN);
                            state.setSender(senderAddress.substring(0, senderAddress.length() - CRLF_LEN));
                            state.setLastState(Command.MAIL);
                            sendResponse(smtpClient, buffer, okayResponse);
                            break;
                        case RCPT: // Update receiver address
                            String receiverAddress = readMessage(buffer, RCPT_CMD_LEN);
                            state.setReceiver(receiverAddress.substring(0, receiverAddress.length() - CRLF_LEN));
                            state.setLastState(Command.RCPT);
                            sendResponse(smtpClient, buffer, okayResponse);
                            break;
                        case DATA: // Set state to DATA
                            state.setLastState(Command.DATA);
                            sendResponse(smtpClient, buffer, dataResponse);
                            break;
                        case NONE: // Read and set message chunks, if ends with \r\n.\r\n create email file and reset state
                            String currentMessage = readMessage(buffer);

                            if (currentMessage.endsWith("." + CRLF)) {
                                if (currentMessage.endsWith(CRLF + "." + CRLF)) {
                                    String trimmed = currentMessage.substring(0, currentMessage.length() - CRLF_DOT_LEN);
                                    state.setMessage(state.getMessage() + trimmed);
                                }
                                IOUtils.createEmailFile(state);
                                state.clearStateContent();
                                state.setLastState(Command.NONE);
                                state.setMessage_id((int) (Math.random() * MAX_MESSAGE_ID));
                                sendResponse(smtpClient, buffer, okayResponse);
                            } else {
                                state.setMessage(state.getMessage() + currentMessage);
                            }
                            break;
                        case QUIT: // Send response and close channel
                            sendResponse(smtpClient, buffer, quitResponse);
                            key.cancel();
                            key.channel().close();
                            break;
                    }
                }
                keysIterator.remove();
            }
        }
    }
}
